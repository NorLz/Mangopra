package com.example.copra

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.copra.ml.BestFloat32Metadata
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ScanActivity : AppCompatActivity() {

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
        private const val CONFIDENCE_THRESHOLD = 0.50f
        private const val PAGE_SIZE = 6
        private const val TAG = "ScanActivity"
    }

    private lateinit var cameraPreview: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var frozenFrameView: ImageView
    private lateinit var btnScan: MaterialButton

    private var state: ScanState = ScanState.IDLE
    private var startRequestedAfterPermission = false

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private lateinit var analysisExecutor: ExecutorService
    private var detectorModel: BestFloat32Metadata? = null
    private var loggedOutputShape = false

    private val frameLock = Any()
    private var latestFrameBitmap: Bitmap? = null
    private var latestFrameDetections: List<FrameDetection> = emptyList()
    private var capturedDetections: MutableList<CapturedDetection> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        cameraPreview = findViewById(R.id.cameraPreview)
        overlay = findViewById(R.id.overlayView)
        frozenFrameView = findViewById(R.id.frozenFrameView)
        btnScan = findViewById(R.id.btnScan)
        analysisExecutor = Executors.newSingleThreadExecutor()

        try {
            detectorModel = BestFloat32Metadata.newInstance(this)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to initialize detection model.", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Model initialization failed", e)
        }

        val btnUpload = findViewById<MaterialButton>(R.id.btnGallery)
        btnUpload.setOnClickListener {
            val intent = Intent(this, UploadActivity::class.java)
            startActivity(intent)
        }

        val btnConfirm = findViewById<MaterialButton>(R.id.btnConfirm)
        btnConfirm.setOnClickListener {
            showCapturedResultsBottomSheet()
        }

        btnScan.setOnClickListener {
            when (state) {
                ScanState.IDLE -> {
                    startLiveDetection()
                }

                ScanState.SCANNING -> {
                    captureCurrentDetections()
                    stopLiveDetection(freeze = true)
                    state = ScanState.CAPTURED
                    setScanButtonActive(false)
                    Toast.makeText(
                        this,
                        "Captured ${capturedDetections.size} detection(s). Tap Confirm to review.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                ScanState.CAPTURED -> {
                    clearCapturedSession()
                    startLiveDetection()
                }
            }
        }

        val btnBack = findViewById<MaterialButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            val intent = Intent(this, HomePage::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        if (state == ScanState.SCANNING) {
            stopLiveDetection(freeze = false)
            state = ScanState.IDLE
            setScanButtonActive(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLiveDetection(freeze = false)
        detectorModel?.close()
        detectorModel = null
        analysisExecutor.shutdown()
        synchronized(frameLock) {
            latestFrameBitmap = null
            latestFrameDetections = emptyList()
            capturedDetections.clear()
        }
    }

    private fun startLiveDetection() {
        if (!hasCameraPermission()) {
            startRequestedAfterPermission = true
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
            return
        }

        if (detectorModel == null) {
            try {
                detectorModel = BestFloat32Metadata.newInstance(this)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to initialize detection model.", Toast.LENGTH_SHORT)
                    .show()
                Log.e(TAG, "Model initialization failed on start", e)
                return
            }
        }

        state = ScanState.SCANNING
        setScanButtonActive(true)

        frozenFrameView.setImageDrawable(null)
        frozenFrameView.visibility = View.GONE

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(cameraPreview.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysisUseCase ->
                        analysisUseCase.setAnalyzer(analysisExecutor) { imageProxy ->
                            analyzeFrame(imageProxy)
                        }
                    }
                imageAnalysis = analysis

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind camera use cases", e)
                Toast.makeText(this, "Unable to start camera detection.", Toast.LENGTH_SHORT).show()
                state = ScanState.IDLE
                setScanButtonActive(false)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopLiveDetection(freeze: Boolean) {
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        cameraProvider?.unbindAll()

        if (freeze) {
            val frozenBitmap = synchronized(frameLock) { latestFrameBitmap }
            if (frozenBitmap != null) {
                runOnUiThread {
                    frozenFrameView.setImageBitmap(frozenBitmap)
                    frozenFrameView.visibility = View.VISIBLE
                }
            }
        } else {
            runOnUiThread {
                frozenFrameView.setImageDrawable(null)
                frozenFrameView.visibility = View.GONE
                overlay.detections = emptyList()
                overlay.invalidate()
            }
        }
    }

    private fun analyzeFrame(imageProxy: ImageProxy) {
        try {
            if (state != ScanState.SCANNING) return

            val bitmap = imageProxyToBitmap(imageProxy) ?: return
            val detections = runDetection(bitmap)

            synchronized(frameLock) {
                latestFrameBitmap = bitmap
                latestFrameDetections = detections
            }

            runOnUiThread {
                if (state != ScanState.SCANNING) return@runOnUiThread

                overlay.detections = detections.map {
                    OverlayView.Detection(
                        rectPreview = it.rectInPreview,
                        label = it.label,
                        score = it.score,
                        color = colorForScore(it.score)
                    )
                }
                overlay.invalidate()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Analyzer error", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun runDetection(bitmap: Bitmap): List<FrameDetection> {
        val model = detectorModel ?: return emptyList()
        val image = TensorImage.fromBitmap(bitmap)
        val outputs = model.process(image)

        parseDetectionsFromTensor(outputs.locationAsTensorBuffer, bitmap.width, bitmap.height)?.let {
            return it
        }

        val rawDetections = try {
            outputs.detectionResultList
        } catch (e: Exception) {
            Log.e(TAG, "Unable to parse model output as detectionResultList", e)
            return emptyList()
        }

        return rawDetections.mapNotNull { detectionResult ->
            val rawRect = extractRect(detectionResult) ?: return@mapNotNull null
            val score = extractScore(detectionResult)
            if (score < CONFIDENCE_THRESHOLD) return@mapNotNull null

            val label = extractLabel(detectionResult)
            val frameRect = normalizeAndClampRect(rawRect, bitmap.width.toFloat(), bitmap.height.toFloat())
                ?: return@mapNotNull null

            val previewRect = mapFrameRectToPreview(frameRect, bitmap.width, bitmap.height)

            FrameDetection(
                rectInFrame = frameRect,
                rectInPreview = previewRect,
                label = label,
                score = score
            )
        }
    }

    private fun parseDetectionsFromTensor(
        tensorBuffer: TensorBuffer,
        frameWidth: Int,
        frameHeight: Int
    ): List<FrameDetection>? {
        val values = tensorBuffer.floatArray
        if (values.isEmpty()) return emptyList()

        val shape = tensorBuffer.shape
        val featureCount = inferFeatureCount(shape, values.size) ?: return null
        if (featureCount < 4) return null

        if (!loggedOutputShape) {
            Log.d(
                TAG,
                "Model output shape=${shape.joinToString(prefix = "[", postfix = "]")} featureCount=$featureCount"
            )
            loggedOutputShape = true
        }

        val detections = mutableListOf<FrameDetection>()
        val boxCount = values.size / featureCount

        for (index in 0 until boxCount) {
            val offset = index * featureCount
            val x0 = values[offset]
            val y0 = values[offset + 1]
            val x1 = values[offset + 2]
            val y1 = values[offset + 3]

            if (!x0.isFiniteValue() || !y0.isFiniteValue() || !x1.isFiniteValue() || !y1.isFiniteValue()) {
                continue
            }

            val rawRect = if (x1 > x0 && y1 > y0) {
                RectF(x0, y0, x1, y1)
            } else {
                // Fallback for center-width-height outputs.
                val width = abs(x1)
                val height = abs(y1)
                RectF(
                    x0 - width / 2f,
                    y0 - height / 2f,
                    x0 + width / 2f,
                    y0 + height / 2f
                )
            }

            val score = extractTensorScore(values, offset, featureCount)
            if (score < CONFIDENCE_THRESHOLD) continue

            val frameRect = normalizeAndClampRect(rawRect, frameWidth.toFloat(), frameHeight.toFloat())
                ?: continue
            val previewRect = mapFrameRectToPreview(frameRect, frameWidth, frameHeight)

            detections.add(
                FrameDetection(
                    rectInFrame = frameRect,
                    rectInPreview = previewRect,
                    label = "Copra",
                    score = score
                )
            )
        }

        return detections
    }

    private fun inferFeatureCount(shape: IntArray, valueCount: Int): Int? {
        val lastDim = shape.lastOrNull()
        if (lastDim != null && lastDim >= 4 && valueCount % lastDim == 0) {
            return lastDim
        }

        return when {
            valueCount % 6 == 0 -> 6
            valueCount % 5 == 0 -> 5
            valueCount % 4 == 0 -> 4
            else -> null
        }
    }

    private fun extractTensorScore(values: FloatArray, offset: Int, featureCount: Int): Float {
        if (featureCount >= 6) {
            val score4 = values[offset + 4]
            val score5 = values[offset + 5]
            return when {
                score4 in 0f..1f -> score4
                score5 in 0f..1f -> score5
                else -> 0f
            }
        }

        if (featureCount == 5) {
            val score = values[offset + 4]
            return if (score in 0f..1f) score else 0f
        }

        return 1f
    }

    private fun Float.isFiniteValue(): Boolean = !isNaN() && !isInfinite()

    private fun extractRect(detectionResult: Any): RectF? {
        val method = detectionResult.javaClass.methods.firstOrNull {
            it.name == "getLocationAsRectF" && it.parameterCount == 0
        } ?: return null

        val value = method.invoke(detectionResult)
        return if (value is RectF) RectF(value) else null
    }

    private fun extractScore(detectionResult: Any): Float {
        return callFloatMethod(detectionResult, "getScoreAsFloat")
            ?: callFloatMethod(detectionResult, "getScore")
            ?: 1.0f
    }

    private fun extractLabel(detectionResult: Any): String {
        return callStringMethod(detectionResult, "getCategoryAsString")
            ?: callStringMethod(detectionResult, "getLabelAsString")
            ?: callStringMethod(detectionResult, "getCategory")
            ?: "Copra"
    }

    private fun callFloatMethod(target: Any, methodName: String): Float? {
        return try {
            val method = target.javaClass.methods.firstOrNull {
                it.name == methodName && it.parameterCount == 0
            } ?: return null

            val value = method.invoke(target)
            when (value) {
                is Float -> value
                is Double -> value.toFloat()
                is Number -> value.toFloat()
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun callStringMethod(target: Any, methodName: String): String? {
        return try {
            val method = target.javaClass.methods.firstOrNull {
                it.name == methodName && it.parameterCount == 0
            } ?: return null

            val value = method.invoke(target) as? String
            value?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeAndClampRect(rawRect: RectF, frameWidth: Float, frameHeight: Float): RectF? {
        val sortedRect = RectF(
            min(rawRect.left, rawRect.right),
            min(rawRect.top, rawRect.bottom),
            max(rawRect.left, rawRect.right),
            max(rawRect.top, rawRect.bottom)
        )

        val looksNormalized = sortedRect.left >= -0.1f &&
            sortedRect.top >= -0.1f &&
            sortedRect.right <= 1.1f &&
            sortedRect.bottom <= 1.1f

        val frameRect = if (looksNormalized) {
            RectF(
                sortedRect.left * frameWidth,
                sortedRect.top * frameHeight,
                sortedRect.right * frameWidth,
                sortedRect.bottom * frameHeight
            )
        } else {
            sortedRect
        }

        val clamped = RectF(
            frameRect.left.coerceIn(0f, frameWidth),
            frameRect.top.coerceIn(0f, frameHeight),
            frameRect.right.coerceIn(0f, frameWidth),
            frameRect.bottom.coerceIn(0f, frameHeight)
        )

        return if (clamped.width() >= 2f && clamped.height() >= 2f) clamped else null
    }

    private fun mapFrameRectToPreview(rectInFrame: RectF, frameWidth: Int, frameHeight: Int): RectF {
        val previewWidth = cameraPreview.width.toFloat()
        val previewHeight = cameraPreview.height.toFloat()

        if (previewWidth <= 0f || previewHeight <= 0f) {
            return RectF(rectInFrame)
        }

        val scale = max(previewWidth / frameWidth.toFloat(), previewHeight / frameHeight.toFloat())
        val scaledWidth = frameWidth * scale
        val scaledHeight = frameHeight * scale
        val dx = (scaledWidth - previewWidth) / 2f
        val dy = (scaledHeight - previewHeight) / 2f

        val mapped = RectF(
            rectInFrame.left * scale - dx,
            rectInFrame.top * scale - dy,
            rectInFrame.right * scale - dx,
            rectInFrame.bottom * scale - dy
        )

        return RectF(
            mapped.left.coerceIn(0f, previewWidth),
            mapped.top.coerceIn(0f, previewHeight),
            mapped.right.coerceIn(0f, previewWidth),
            mapped.bottom.coerceIn(0f, previewHeight)
        )
    }

    private fun captureCurrentDetections() {
        val frameBitmap = synchronized(frameLock) { latestFrameBitmap }
        val detections = synchronized(frameLock) { latestFrameDetections.toList() }

        if (frameBitmap == null || detections.isEmpty()) {
            capturedDetections.clear()
            return
        }

        val crops = mutableListOf<CapturedDetection>()
        detections.forEach { detection ->
            val cropRect = clampRectForBitmap(detection.rectInFrame, frameBitmap.width, frameBitmap.height)
                ?: return@forEach

            try {
                val crop = Bitmap.createBitmap(
                    frameBitmap,
                    cropRect.left,
                    cropRect.top,
                    cropRect.width(),
                    cropRect.height()
                )

                crops.add(
                    CapturedDetection(
                        crop = crop,
                        sourceRect = RectF(cropRect),
                        label = detection.label,
                        score = detection.score
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Skipping invalid crop rect: $cropRect", e)
            }
        }

        capturedDetections = crops
    }

    private fun clampRectForBitmap(rect: RectF, bitmapWidth: Int, bitmapHeight: Int): Rect? {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) return null

        val left = rect.left.toInt().coerceIn(0, bitmapWidth - 1)
        val top = rect.top.toInt().coerceIn(0, bitmapHeight - 1)
        val right = rect.right.toInt().coerceIn(left + 1, bitmapWidth)
        val bottom = rect.bottom.toInt().coerceIn(top + 1, bitmapHeight)

        if (right - left < 2 || bottom - top < 2) return null
        return Rect(left, top, right, bottom)
    }

    private fun clearCapturedSession() {
        capturedDetections.clear()
        overlay.detections = emptyList()
        overlay.invalidate()
        frozenFrameView.setImageDrawable(null)
        frozenFrameView.visibility = View.GONE
        synchronized(frameLock) {
            latestFrameDetections = emptyList()
        }
    }

    private fun showCapturedResultsBottomSheet() {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetStyle)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_results, null)
        dialog.setContentView(view)

        val summarySection = view.findViewById<View>(R.id.layoutSummarySection)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerImages)
        val emptyState = view.findViewById<TextView>(R.id.tvEmptyState)
        val btnPrev = view.findViewById<MaterialButton>(R.id.btnPrev)
        val btnNext = view.findViewById<MaterialButton>(R.id.btnNext)
        val pageInput = view.findViewById<TextInputEditText>(R.id.etPageNumber)
        val totalPagesText = view.findViewById<TextView>(R.id.tvTotalPages)

        summarySection.visibility = View.GONE
        recycler.layoutManager = GridLayoutManager(this, 3)

        val allItems = capturedDetections.toList()
        val adapter = CapturedImageAdapter(emptyList())
        recycler.adapter = adapter

        if (allItems.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            recycler.visibility = View.GONE
            btnPrev.isEnabled = false
            btnNext.isEnabled = false
            pageInput.setText("1")
            totalPagesText.text = "of 1"
        } else {
            emptyState.visibility = View.GONE
            recycler.visibility = View.VISIBLE

            var currentPage = 1
            val totalPages = (allItems.size + PAGE_SIZE - 1) / PAGE_SIZE

            fun loadPage(page: Int) {
                val start = (page - 1) * PAGE_SIZE
                val end = min(start + PAGE_SIZE, allItems.size)
                val pageItems = allItems.subList(start, end)

                adapter.updateData(pageItems)
                pageInput.setText(page.toString())
                totalPagesText.text = "of $totalPages"
                btnPrev.isEnabled = page > 1
                btnNext.isEnabled = page < totalPages
            }

            loadPage(currentPage)

            btnPrev.setOnClickListener {
                if (currentPage > 1) {
                    currentPage -= 1
                    loadPage(currentPage)
                }
            }

            btnNext.setOnClickListener {
                if (currentPage < totalPages) {
                    currentPage += 1
                    loadPage(currentPage)
                }
            }
        }

        view.findViewById<ImageView>(R.id.btnClose).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        val behavior = dialog.behavior
        behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.5).toInt()
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    @OptIn(ExperimentalGetImage::class)
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val mediaImage = imageProxy.image ?: return null
        val nv21 = yuv420888ToNv21(mediaImage)
        val yuvImage = android.graphics.YuvImage(
            nv21,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )

        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 95, out)
        val imageBytes = out.toByteArray()
        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        return bitmap
    }

    private fun yuv420888ToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4
        val nv21 = ByteArray(ySize + uvSize * 2)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        var position = 0

        val yBuffer = yPlane.buffer
        for (row in 0 until height) {
            val rowStart = row * yPlane.rowStride
            for (col in 0 until width) {
                nv21[position++] = yBuffer.get(rowStart + col * yPlane.pixelStride)
            }
        }

        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        for (row in 0 until height / 2) {
            val rowStartU = row * uPlane.rowStride
            val rowStartV = row * vPlane.rowStride
            for (col in 0 until width / 2) {
                val uIndex = rowStartU + col * uPlane.pixelStride
                val vIndex = rowStartV + col * vPlane.pixelStride
                nv21[position++] = vBuffer.get(vIndex)
                nv21[position++] = uBuffer.get(uIndex)
            }
        }

        return nv21
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun setScanButtonActive(isActive: Boolean) {
        if (isActive) {
            btnScan.setIconResource(R.drawable.scan__1_)
            btnScan.animate()
                .scaleX(1.4f)
                .scaleY(1.4f)
                .setDuration(200)
                .start()
        } else {
            btnScan.setIconResource(R.drawable.scan)
            btnScan.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .start()
        }
    }

    private fun colorForScore(score: Float): Int {
        return when {
            score >= 0.80f -> Color.parseColor("#2E7D32")
            score >= 0.50f -> Color.parseColor("#F9A825")
            else -> Color.parseColor("#C62828")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            if (startRequestedAfterPermission) {
                startRequestedAfterPermission = false
                startLiveDetection()
            }
        } else if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            startRequestedAfterPermission = false
            Toast.makeText(this, "Camera permission is required for scanning.", Toast.LENGTH_SHORT)
                .show()
        }
    }
}
