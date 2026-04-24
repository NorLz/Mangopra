package com.example.copra

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class ClassificationResult(
    val gradeLabel: String,
    val confidence: Float,
    val probabilities: List<Float>,
    val inferenceMs: Long
)

class CopraClassifier(
    private val context: Context,
    val modelOption: ClassificationModelOption
) {
    companion object {
        private const val TAG = "CopraClassifier"
        private const val INPUT_WIDTH = 224
        private const val INPUT_HEIGHT = 224
        private const val INPUT_CHANNELS = 3
        private val DEFAULT_LABELS = listOf("Grade I", "Grade II", "Grade III")

        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }

    private var interpreter: Interpreter? = null
    private var labels: List<String> = DEFAULT_LABELS
    private var initialized = false
    private var outputClassCount = DEFAULT_LABELS.size
    private var imageInputIndex = 0
    private var glcmInputIndex: Int? = null
    private var requiresGlcm = false
    private val glcmBridge = GlcmPythonBridge(context)

    @Synchronized
    fun initialize(): Boolean {
        if (initialized && interpreter != null) return true

        return try {
            val modelBuffer = loadModelFile(context, modelOption.assetFileName)
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }

            interpreter = Interpreter(modelBuffer, options)
            labels = readLabelsFromMetadata(modelBuffer) ?: DEFAULT_LABELS
            validateModel(ioInterpreter = interpreter!!)
            initialized = true
            Log.d(TAG, "Initialized classifier model=${modelOption.displayName} labels=$labels")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize classifier", e)
            initialized = false
            interpreter?.close()
            interpreter = null
            false
        }
    }

    @Synchronized
    fun classify(cropBitmap: Bitmap): ClassificationResult {
        val localInterpreter = interpreter
            ?: throw IllegalStateException("CopraClassifier is not initialized")

        val startNs = SystemClock.elapsedRealtimeNanos()

        val imageBuffer = preprocessImageInput(cropBitmap)
        val output = Array(1) { FloatArray(outputClassCount) }
        val inputs = arrayOfNulls<Any>(localInterpreter.inputTensorCount)
        inputs[imageInputIndex] = imageBuffer
        glcmInputIndex?.let { index ->
            inputs[index] = preprocessGlcmInput(cropBitmap)
        }

        val outputs = mutableMapOf<Int, Any>(0 to output)
        localInterpreter.runForMultipleInputsOutputs(
            inputs.requireNoNulls(),
            outputs
        )
        val inferenceMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000L

        val probabilities = softmax(output[0])
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
        val rawLabel = labels.getOrNull(maxIndex) ?: "Unknown"
        val label = toDisplayGradeLabel(rawLabel)
        val confidence = probabilities.getOrNull(maxIndex) ?: 0f

        return ClassificationResult(
            gradeLabel = label,
            confidence = confidence,
            probabilities = probabilities,
            inferenceMs = inferenceMs
        )
    }

    @Synchronized
    fun close() {
        interpreter?.close()
        interpreter = null
        initialized = false
        requiresGlcm = false
        imageInputIndex = 0
        glcmInputIndex = null
    }

    private fun preprocessImageInput(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_WIDTH, INPUT_HEIGHT, true)
        val inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_WIDTH * INPUT_HEIGHT * INPUT_CHANNELS)
            .order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_WIDTH * INPUT_HEIGHT)
        resized.getPixels(pixels, 0, INPUT_WIDTH, 0, 0, INPUT_WIDTH, INPUT_HEIGHT)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            inputBuffer.putFloat((r - MEAN[0]) / STD[0])
            inputBuffer.putFloat((g - MEAN[1]) / STD[1])
            inputBuffer.putFloat((b - MEAN[2]) / STD[2])
        }

        inputBuffer.rewind()
        return inputBuffer
    }

    private fun preprocessGlcmInput(bitmap: Bitmap): ByteBuffer {
        val rgbBytes = extractRgbBytes(bitmap)
        val glcmFeatures = glcmBridge.extractFeaturesFromRgb(rgbBytes, bitmap.width, bitmap.height)

        val glcmBuffer = ByteBuffer.allocateDirect(4 * glcmFeatures.size)
            .order(ByteOrder.nativeOrder())
        for (feature in glcmFeatures) {
            glcmBuffer.putFloat(feature)
        }
        glcmBuffer.rewind()
        return glcmBuffer
    }

    private fun extractRgbBytes(bitmap: Bitmap): ByteArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val rgb = ByteArray(pixels.size * 3)
        var offset = 0
        for (pixel in pixels) {
            rgb[offset++] = ((pixel shr 16) and 0xFF).toByte()
            rgb[offset++] = ((pixel shr 8) and 0xFF).toByte()
            rgb[offset++] = (pixel and 0xFF).toByte()
        }
        return rgb
    }

    private fun softmax(raw: FloatArray): List<Float> {
        if (raw.isEmpty()) return emptyList()

        val maxLogit = raw
            .filter { it.isFinite() }
            .maxOrNull()
            ?: return List(raw.size) { 0f }

        val exps = raw.map { value ->
            if (value.isNaN() || value.isInfinite()) {
                0f
            } else {
                kotlin.math.exp(value - maxLogit).toFloat()
            }
        }
        val sum = exps.sum()
        return if (sum <= 0f) List(exps.size) { 0f } else exps.map { it / sum }
    }

    private fun validateModel(ioInterpreter: Interpreter) {
        require(ioInterpreter.inputTensorCount in 1..2) {
            "Expected 1 or 2 model inputs, found ${ioInterpreter.inputTensorCount}"
        }
        require(ioInterpreter.outputTensorCount >= 1) {
            "Expected at least 1 model output, found ${ioInterpreter.outputTensorCount}"
        }

        val tensorShapes = (0 until ioInterpreter.inputTensorCount).associateWith { index ->
            ioInterpreter.getInputTensor(index).shape()
        }
        val imageTensorEntry = tensorShapes.entries.firstOrNull { (_, shape) ->
            shape.size == 4 &&
                shape[1] == INPUT_HEIGHT &&
                shape[2] == INPUT_WIDTH &&
                shape[3] == INPUT_CHANNELS
        } ?: throw IllegalArgumentException("Unable to find image input tensor in selected model")
        val glcmTensorEntry = tensorShapes.entries.firstOrNull { (_, shape) ->
            shape.size >= 2 && shape.last() == 5
        }

        imageInputIndex = imageTensorEntry.key
        glcmInputIndex = glcmTensorEntry?.key
        requiresGlcm = glcmTensorEntry != null

        val imageShape = imageTensorEntry.value
        val outputShape = ioInterpreter.getOutputTensor(0).shape()

        require(imageShape.contentEquals(intArrayOf(1, INPUT_HEIGHT, INPUT_WIDTH, INPUT_CHANNELS))) {
            "Unexpected image input shape: ${imageShape.joinToString(prefix = "[", postfix = "]")}"
        }
        if (ioInterpreter.inputTensorCount == 2) {
            require(glcmTensorEntry != null) {
                "Expected a GLCM input tensor in the selected model"
            }
        }
        require(outputShape.size >= 2 && outputShape.last() > 0) {
            "Unexpected output shape: ${outputShape.joinToString(prefix = "[", postfix = "]")}"
        }

        outputClassCount = outputShape.last()
        Log.d(
            TAG,
            "Validated model=${modelOption.displayName} inputs=${ioInterpreter.inputTensorCount} " +
                "imageInputIndex=$imageInputIndex glcmInputIndex=${glcmInputIndex ?: -1} requiresGlcm=$requiresGlcm"
        )
    }

    private fun toDisplayGradeLabel(rawLabel: String): String {
        return when (gradeBucket(rawLabel)) {
            1 -> "Grade I"
            2 -> "Grade II"
            3 -> "Grade III"
            else -> rawLabel
        }
    }

    private fun gradeBucket(label: String): Int {
        val compact = label.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        return when {
            compact.contains("gradeiii") || compact == "iii" ||
                compact.contains("gradec") || compact == "c" -> 3

            compact.contains("gradeii") || compact == "ii" ||
                compact.contains("gradeb") || compact == "b" -> 2

            compact.contains("gradei") || compact == "i" ||
                compact.contains("gradea") || compact == "a" -> 1

            else -> 0
        }
    }

    private fun readLabelsFromMetadata(modelBuffer: ByteBuffer): List<String>? {
        return try {
            val metadataBuffer = modelBuffer.duplicate().apply { rewind() }
            val extractorClass = Class.forName("org.tensorflow.lite.support.metadata.MetadataExtractor")
            val constructor = extractorClass.getConstructor(ByteBuffer::class.java)
            val extractor = constructor.newInstance(metadataBuffer)

            val fileNamesMethod = extractorClass.methods.firstOrNull {
                it.name == "getAssociatedFileNames" && it.parameterCount == 0
            } ?: return null

            val associatedFileNames = when (val namesValue = fileNamesMethod.invoke(extractor)) {
                is List<*> -> namesValue.mapNotNull { it as? String }
                is Array<*> -> namesValue.mapNotNull { it as? String }
                else -> emptyList()
            }

            val labelFile = associatedFileNames.firstOrNull { name ->
                name.contains("label", ignoreCase = true) || name.endsWith(".txt", ignoreCase = true)
            } ?: return null

            val associatedFileMethod = extractorClass.methods.firstOrNull {
                it.name == "getAssociatedFile" && it.parameterCount == 1
            } ?: return null

            val labelStream = associatedFileMethod.invoke(extractor, labelFile) as? InputStream
                ?: return null

            labelStream.bufferedReader().useLines { lines ->
                lines.map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toList()
                    .takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load labels from metadata, using defaults", e)
            null
        }
    }

    private fun loadModelFile(context: Context, fileName: String): MappedByteBuffer {
        context.assets.openFd(fileName).use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                val fileChannel = inputStream.channel
                return fileChannel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fileDescriptor.startOffset,
                    fileDescriptor.declaredLength
                )
            }
        }
    }
}
