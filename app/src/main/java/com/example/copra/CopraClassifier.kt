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
import kotlin.math.max

data class ClassificationResult(
    val gradeLabel: String,
    val confidence: Float,
    val probabilities: List<Float>,
    val inferenceMs: Long
)

class CopraClassifier(
    private val context: Context
) {
    companion object {
        private const val TAG = "CopraClassifier"
        private const val MODEL_FILE = "mobilenetv2_baseline_phase2_float32_metadata.tflite"
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

    @Synchronized
    fun initialize(): Boolean {
        if (initialized && interpreter != null) return true

        return try {
            val modelBuffer = loadModelFile(context, MODEL_FILE)
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }

            interpreter = Interpreter(modelBuffer, options)
            labels = readLabelsFromMetadata(modelBuffer) ?: DEFAULT_LABELS
            initialized = true
            Log.d(TAG, "Initialized classifier with labels=$labels")
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

        val inputBuffer = preprocess(cropBitmap)
        val output = Array(1) { FloatArray(3) }

        val startNs = SystemClock.elapsedRealtimeNanos()
        localInterpreter.run(inputBuffer, output)
        val inferenceMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000L

        val probabilities = normalizeProbabilities(output[0])
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
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
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

    private fun normalizeProbabilities(raw: FloatArray): List<Float> {
        val clipped = raw.map { value ->
            when {
                value.isNaN() || value.isInfinite() -> 0f
                else -> max(0f, value)
            }
        }

        val sum = clipped.sum()
        return if (sum <= 0f) {
            List(clipped.size) { 0f }
        } else {
            clipped.map { it / sum }
        }
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
