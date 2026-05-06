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
import android.os.SystemClock
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
import java.util.concurrent.atomic.AtomicInteger
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
    private lateinit var classificationExecutor: ExecutorService
    private lateinit var historyRepository: AnalysisHistoryRepository
    private lateinit var pricingRepository: PricingRepository
    private lateinit var classificationModelStore: ClassificationModelStore
    private var detectorModel: BestFloat32Metadata? = null
    private var classifier: CopraClassifier? = null
    private var selectedClassificationModel: ClassificationModelOption = ClassificationModels.default
    private var loggedOutputShape = false

    private val frameLock = Any()
    private var latestFrameBitmap: Bitmap? = null
    private var capturedFrameBitmap: Bitmap? = null
    private var latestFrameDetections: List<FrameDetection> = emptyList()
    private var capturedDetections: MutableList<CapturedDetection> = mutableListOf()
    private var lastPersistedCaptureSessionId: Int = -1
    @Volatile
    private var captureSessionId: Int = 0
    @Volatile
    private var isActivityDestroyed = false
    @Volatile
    private var acceptClassificationCallbacks = false

    private var resultsDialog: BottomSheetDialog? = null
    private var resultsSummarySection: View? = null
    private var resultsRecycler: RecyclerView? = null
    private var resultsEmptyState: TextView? = null
    private var resultsBtnPrev: MaterialButton? = null
    private var resultsBtnNext: MaterialButton? = null
    private var resultsPageInput: TextInputEditText? = null
    private var resultsTotalPagesText: TextView? = null
    private var resultsGrade1Count: TextView? = null
    private var resultsGrade2Count: TextView? = null
    private var resultsGrade3Count: TextView? = null
    private var resultsAdapter: CapturedImageAdapter? = null
    private var resultsCurrentPage = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        cameraPreview = findViewById(R.id.cameraPreview)
        overlay = findViewById(R.id.overlayView)
        frozenFrameView = findViewById(R.id.frozenFrameView)
        btnScan = findViewById(R.id.btnScan)
        analysisExecutor = Executors.newSingleThreadExecutor()
        classificationExecutor = Executors.newSingleThreadExecutor()
        historyRepository = AnalysisHistoryRepository.getInstance(applicationContext)
        pricingRepository = PricingRepository.getInstance(applicationContext)
        classificationModelStore = ClassificationModelStore(applicationContext)
        selectedClassificationModel = classificationModelStore.getSelectedModel()

        try {
            detectorModel = BestFloat32Metadata.newInstance(this)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to initialize detection model.", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Model initialization failed", e)
        }
        initializeClassifier()

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
                    val sessionId = captureCurrentDetections()
                    enqueueClassificationJobs(sessionId)
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

    override fun onStart() {
        super.onStart()
        refreshSelectedClassificationModel()
        acceptClassificationCallbacks = true
    }

    override fun onStop() {
        super.onStop()
        acceptClassificationCallbacks = false
        captureSessionId += 1
        if (state == ScanState.SCANNING) {
            stopLiveDetection(freeze = false)
            state = ScanState.IDLE
            setScanButtonActive(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isActivityDestroyed = true
        acceptClassificationCallbacks = false
        captureSessionId += 1
        dismissResultsBottomSheet()
        stopLiveDetection(freeze = false)
        detectorModel?.close()
        detectorModel = null
        classifier?.close()
        classifier = null
        analysisExecutor.shutdown()
        classificationExecutor.shutdownNow()
        synchronized(frameLock) {
            latestFrameBitmap = null
            latestFrameDetections = emptyList()
            capturedDetections.clear()
        }
        capturedFrameBitmap = null
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

    private fun initializeClassifier() {
        classifier?.close()
        classifier = CopraClassifier(applicationContext, selectedClassificationModel)

        val initialized = classifier?.initialize() == true
        if (!initialized) {
            Log.w(TAG, "Classifier initialization failed. Capture classification will be unavailable.")
        }
    }

    private fun refreshSelectedClassificationModel() {
        val latestSelection = classificationModelStore.getSelectedModel()
        if (classifier == null || latestSelection.key != selectedClassificationModel.key) {
            selectedClassificationModel = latestSelection
            initializeClassifier()
        }
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

    private fun captureCurrentDetections(): Int {
        val sessionId = nextCaptureSessionId()
        val frameBitmap = synchronized(frameLock) { latestFrameBitmap }
        val detections = synchronized(frameLock) { latestFrameDetections.toList() }
        lastPersistedCaptureSessionId = -1

        if (frameBitmap == null || detections.isEmpty()) {
            capturedDetections.clear()
            capturedFrameBitmap = null
            resultsCurrentPage = 1
            renderResultsSheetIfVisible()
            return sessionId
        }

        capturedFrameBitmap = frameBitmap.copy(frameBitmap.config ?: Bitmap.Config.ARGB_8888, false)

        val crops = mutableListOf<CapturedDetection>()
        val activeModel = selectedClassificationModel
        detections.forEach { detection ->
            val expandedRect = expandRectByPercent(detection.rectInFrame, 0.08f)
            val cropRect = clampRectForBitmap(expandedRect, frameBitmap.width, frameBitmap.height)
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
                        previewRect = RectF(detection.rectInPreview),
                        label = detection.label,
                        score = detection.score,
                        classificationStatus = if (cropRect.width() < 32 || cropRect.height() < 32) {
                            ClassificationStatus.FAILED
                        } else {
                            ClassificationStatus.PENDING
                        },
                        classificationLabel = if (cropRect.width() < 32 || cropRect.height() < 32) {
                            "Too small"
                        } else {
                            null
                        },
                        classificationModelKey = activeModel.key,
                        classificationModelName = activeModel.displayName
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Skipping invalid crop rect: $cropRect", e)
            }
        }

        capturedDetections = crops
        refreshCapturedOverlay()
        resultsCurrentPage = 1
        renderResultsSheetIfVisible()
        return sessionId
    }

    private fun expandRectByPercent(rect: RectF, expansionRatio: Float): RectF {
        val dx = rect.width() * expansionRatio
        val dy = rect.height() * expansionRatio
        return RectF(
            rect.left - dx,
            rect.top - dy,
            rect.right + dx,
            rect.bottom + dy
        )
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

    private fun enqueueClassificationJobs(sessionId: Int) {
        val localClassifier = classifier
        if (localClassifier == null || !localClassifier.initialize()) {
            markPendingClassificationsAsFailed(sessionId, "Classifier unavailable")
            return
        }

        val jobs = capturedDetections
            .withIndex()
            .filter { it.value.classificationStatus == ClassificationStatus.PENDING }
            .sortedByDescending { it.value.score }

        if (jobs.isEmpty()) {
            maybePersistCapturedSession(sessionId)
            renderResultsSheetIfVisible()
            return
        }

        val batchStartMs = SystemClock.elapsedRealtime()
        val completedCount = AtomicInteger(0)

        jobs.forEach { indexedItem ->
            classificationExecutor.execute {
                val result = try {
                    localClassifier.classify(indexedItem.value.crop)
                } catch (e: Exception) {
                    Log.e(TAG, "Classification failed for index=${indexedItem.index}", e)
                    null
                }

                val finished = completedCount.incrementAndGet()
                runOnUiThread {
                    if (isActivityDestroyed || sessionId != captureSessionId) {
                        return@runOnUiThread
                    }

                    val itemIndex = indexedItem.index
                    if (itemIndex !in capturedDetections.indices) return@runOnUiThread

                    if (result == null) {
                        applyClassificationFailure(itemIndex)
                    } else {
                        applyClassificationSuccess(itemIndex, result)
                        Log.d(
                            TAG,
                            "Classification index=$itemIndex label=${result.gradeLabel} confidence=${"%.3f".format(result.confidence)} inferenceMs=${result.inferenceMs}"
                        )
                    }

                    if (acceptClassificationCallbacks) {
                        renderResultsSheetIfVisible()
                    }

                    if (finished == jobs.size) {
                        val totalMs = SystemClock.elapsedRealtime() - batchStartMs
                        Log.d(
                            TAG,
                            "Classification batch completed session=$sessionId jobs=${jobs.size} totalMs=$totalMs"
                        )
                        maybePersistCapturedSession(sessionId)
                    }
                }
            }
        }
    }

    private fun applyClassificationSuccess(index: Int, result: ClassificationResult) {
        val current = capturedDetections[index]
        capturedDetections[index] = current.copy(
            classificationLabel = result.gradeLabel,
            classificationConfidence = result.confidence,
            classificationStatus = ClassificationStatus.READY,
            classificationMs = result.inferenceMs
        )
        refreshCapturedOverlay()
    }

    private fun applyClassificationFailure(index: Int) {
        val current = capturedDetections[index]
        capturedDetections[index] = current.copy(
            classificationLabel = null,
            classificationConfidence = null,
            classificationStatus = ClassificationStatus.FAILED,
            classificationMs = null
        )
        refreshCapturedOverlay()
    }

    private fun markPendingClassificationsAsFailed(sessionId: Int, reason: String) {
        runOnUiThread {
            if (isActivityDestroyed || sessionId != captureSessionId) return@runOnUiThread

            capturedDetections = capturedDetections.map { item ->
                if (item.classificationStatus == ClassificationStatus.PENDING) {
                    item.copy(
                        classificationStatus = ClassificationStatus.FAILED,
                        classificationLabel = null,
                        classificationConfidence = null,
                        classificationMs = null
                    )
                } else {
                    item
                }
            }.toMutableList()
            refreshCapturedOverlay()

            if (acceptClassificationCallbacks) {
                renderResultsSheetIfVisible()
                Toast.makeText(this, reason, Toast.LENGTH_SHORT).show()
            }

            maybePersistCapturedSession(sessionId)
        }
    }

    private fun nextCaptureSessionId(): Int {
        captureSessionId += 1
        return captureSessionId
    }

    private fun clearCapturedSession() {
        captureSessionId += 1
        capturedDetections.clear()
        capturedFrameBitmap = null
        resultsCurrentPage = 1
        dismissResultsBottomSheet()

        overlay.detections = emptyList()
        overlay.invalidate()
        frozenFrameView.setImageDrawable(null)
        frozenFrameView.visibility = View.GONE
        synchronized(frameLock) {
            latestFrameDetections = emptyList()
        }
    }

    private fun maybePersistCapturedSession(sessionId: Int) {
        if (sessionId != captureSessionId) return
        if (lastPersistedCaptureSessionId == sessionId) return
        if (capturedDetections.isEmpty()) return
        if (capturedDetections.any { it.classificationStatus == ClassificationStatus.PENDING }) return

        val frameBitmap = capturedFrameBitmap ?: return
        val snapshot = capturedDetections.toList()
        lastPersistedCaptureSessionId = sessionId

        historyRepository.saveSession(
            sourceType = AnalysisSourceType.SCAN,
            modelOption = selectedClassificationModel,
            fullImage = frameBitmap,
            items = snapshot,
            onComplete = { savedSessionId ->
                Log.d(TAG, "Saved scan history sessionId=$savedSessionId captureSession=$sessionId")
            },
            onError = { throwable ->
                if (captureSessionId == sessionId) {
                    lastPersistedCaptureSessionId = -1
                }
                Log.e(TAG, "Failed to save scan history for captureSession=$sessionId", throwable)
            }
        )
    }

    private fun refreshCapturedOverlay() {
        if (state != ScanState.CAPTURED && state != ScanState.SCANNING) return
        if (capturedDetections.isEmpty()) return

        overlay.detections = capturedDetections.map { captured ->
            val (label, score, color) = when (captured.classificationStatus) {
                ClassificationStatus.PENDING -> Triple(
                    "Classifying...",
                    0f,
                    Color.parseColor("#757575")
                )

                ClassificationStatus.FAILED -> Triple(
                    "Unclassified",
                    0f,
                    Color.parseColor("#9E9E9E")
                )

                ClassificationStatus.READY -> {
                    val grade = captured.classificationLabel ?: "Unknown"
                    val confidence = captured.classificationConfidence ?: 0f
                    Triple(grade, confidence, colorForGrade(grade))
                }
            }

            OverlayView.Detection(
                rectPreview = RectF(captured.previewRect),
                label = label,
                score = score,
                color = color
            )
        }
        overlay.invalidate()
    }

    private fun colorForGrade(gradeLabel: String): Int {
        return when (gradeBucket(gradeLabel)) {
            1 -> Color.parseColor("#2E7D32")
            2 -> Color.parseColor("#F9A825")
            3 -> Color.parseColor("#C62828")
            else -> Color.parseColor("#455A64")
        }
    }

    private fun dismissResultsBottomSheet() {
        resultsDialog?.setOnDismissListener(null)
        resultsDialog?.dismiss()
        clearResultsBottomSheetRefs()
    }

    private fun clearResultsBottomSheetRefs() {
        resultsDialog = null
        resultsSummarySection = null
        resultsRecycler = null
        resultsEmptyState = null
        resultsBtnPrev = null
        resultsBtnNext = null
        resultsPageInput = null
        resultsTotalPagesText = null
        resultsGrade1Count = null
        resultsGrade2Count = null
        resultsGrade3Count = null
        resultsAdapter = null
    }

    private fun showCapturedResultsBottomSheet() {
        if (resultsDialog?.isShowing == true) {
            renderResultsSheet()
            return
        }

        val dialog = BottomSheetDialog(this, R.style.BottomSheetStyle)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_results, null)
        dialog.setContentView(view)
        resultsDialog = dialog

        resultsSummarySection = view.findViewById(R.id.layoutSummarySection)
        resultsRecycler = view.findViewById<RecyclerView>(R.id.recyclerImages).apply {
            layoutManager = GridLayoutManager(this@ScanActivity, 3)
            isNestedScrollingEnabled = false
        }
        resultsEmptyState = view.findViewById(R.id.tvEmptyState)
        resultsBtnPrev = view.findViewById(R.id.btnPrev)
        resultsBtnNext = view.findViewById(R.id.btnNext)
        resultsPageInput = view.findViewById(R.id.etPageNumber)
        resultsTotalPagesText = view.findViewById(R.id.tvTotalPages)
        resultsGrade1Count = view.findViewById(R.id.tvGrade1Count)
        resultsGrade2Count = view.findViewById(R.id.tvGrade2Count)
        resultsGrade3Count = view.findViewById(R.id.tvGrade3Count)
        resultsAdapter = CapturedImageAdapter(emptyList())
        resultsRecycler?.adapter = resultsAdapter

        resultsBtnPrev?.setOnClickListener {
            if (resultsCurrentPage > 1) {
                resultsCurrentPage -= 1
                renderResultsSheet()
            }
        }

        view.findViewById<ImageView>(R.id.btnClose).setOnClickListener {
            dialog.dismiss()
        }
        resultsBtnNext?.setOnClickListener {
            val totalPages = max(1, (capturedDetections.size + PAGE_SIZE - 1) / PAGE_SIZE)
            if (resultsCurrentPage < totalPages) {
                resultsCurrentPage += 1
                renderResultsSheet()
            }
        }

        dialog.setOnDismissListener { clearResultsBottomSheetRefs() }
        dialog.show()
        val behavior = dialog.behavior
        behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.5).toInt()
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED

        renderResultsSheet()
    }

    private fun renderResultsSheetIfVisible() {
        if (resultsDialog?.isShowing == true) {
            renderResultsSheet()
        }
    }

    private fun renderResultsSheet() {
        val summarySection = resultsSummarySection ?: return
        val recycler = resultsRecycler ?: return
        val emptyState = resultsEmptyState ?: return
        val btnPrev = resultsBtnPrev ?: return
        val btnNext = resultsBtnNext ?: return
        val pageInput = resultsPageInput ?: return
        val totalPagesText = resultsTotalPagesText ?: return
        val adapter = resultsAdapter ?: return

        val allItems = capturedDetections.toList()
        val readyItems = allItems.filter { it.classificationStatus == ClassificationStatus.READY }
        val grade1Count = readyItems.count { gradeBucket(it.classificationLabel) == 1 }
        val grade2Count = readyItems.count { gradeBucket(it.classificationLabel) == 2 }
        val grade3Count = readyItems.count { gradeBucket(it.classificationLabel) == 3 }
        if (allItems.isEmpty()) {
            summarySection.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
            recycler.visibility = View.GONE
            btnPrev.isEnabled = false
            btnNext.isEnabled = false
            pageInput.setText("1")
            totalPagesText.text = "of 1"
            adapter.updateData(emptyList())
            return
        }

        summarySection.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        recycler.visibility = View.VISIBLE
        updateSummaryCounts(allItems)
        bindPricingCard(
            view = resultsDialog?.findViewById(R.id.bottomSheetRoot),
            pricing = PricingCalculator.compute(
                grade1Count = grade1Count,
                grade2Count = grade2Count,
                grade3Count = grade3Count,
                latestPricing = pricingRepository.getCachedPricing()
            ),
            grade1Count = grade1Count,
            grade2Count = grade2Count,
            grade3Count = grade3Count
        )

        val totalPages = max(1, (allItems.size + PAGE_SIZE - 1) / PAGE_SIZE)
        if (resultsCurrentPage > totalPages) {
            resultsCurrentPage = totalPages
        }
        if (resultsCurrentPage < 1) {
            resultsCurrentPage = 1
        }

        val start = (resultsCurrentPage - 1) * PAGE_SIZE
        val end = min(start + PAGE_SIZE, allItems.size)
        val pageItems = allItems.subList(start, end)

        adapter.updateData(pageItems)
        pageInput.setText(resultsCurrentPage.toString())
        totalPagesText.text = "of $totalPages"
        btnPrev.isEnabled = resultsCurrentPage > 1
        btnNext.isEnabled = resultsCurrentPage < totalPages
    }

    private fun updateSummaryCounts(items: List<CapturedDetection>) {
        val readyItems = items.filter { it.classificationStatus == ClassificationStatus.READY }
        val grade1 = readyItems.count { gradeBucket(it.classificationLabel) == 1 }
        val grade2 = readyItems.count { gradeBucket(it.classificationLabel) == 2 }
        val grade3 = readyItems.count { gradeBucket(it.classificationLabel) == 3 }

        resultsGrade1Count?.text = grade1.toString()
        resultsGrade2Count?.text = grade2.toString()
        resultsGrade3Count?.text = grade3.toString()
    }

    private fun bindPricingCard(
        view: View?,
        pricing: AnalysisPricing?,
        grade1Count: Int,
        grade2Count: Int,
        grade3Count: Int
    ) {
        if (view == null) return

        view.findViewById<TextView>(R.id.tvBatchPriceValue)?.text =
            PricingFormatter.formatBatchPrice(pricing)
        view.findViewById<TextView>(R.id.tvBatchPriceCaption)?.text =
            PricingFormatter.buildBatchPriceCaption(pricing)
        view.findViewById<TextView>(R.id.tvBatchPriceMeta)?.text = if (pricing != null) {
            "Effective date: ${PricingFormatter.formatEffectiveDate(pricing.effectiveDate)}\n" +
                "Saved on device: ${PricingFormatter.formatSyncedAt(pricing.syncedAtMillis)}"
        } else {
            "Open Home while online to download the latest pricing for offline use."
        }
        view.findViewById<TextView>(R.id.tvBatchPriceProportions)?.text =
            PricingFormatter.buildProportionSummary(
                grade1Count = grade1Count,
                grade2Count = grade2Count,
                grade3Count = grade3Count
            )
        val latency = ClassificationLatency.fromCapturedItems(capturedDetections)
        view.findViewById<TextView>(R.id.tvLatencyValue)?.text =
            ClassificationLatencyFormatter.formatDetail(latency)
        view.findViewById<TextView>(R.id.tvLatencyMeta)?.text =
            ClassificationLatencyFormatter.formatMeta(latency)
    }

    private fun gradeBucket(label: String?): Int {
        if (label.isNullOrBlank()) return 0
        val normalized = label.trim().lowercase()
        val compact = normalized.replace(Regex("[^a-z0-9]"), "")
        return when {
            normalized.contains("grade iii") || compact.contains("gradeiii") ||
                compact.contains("gradec") || compact == "c" -> 3

            normalized.contains("grade ii") || compact.contains("gradeii") ||
                compact.contains("gradeb") || compact == "b" -> 2

            normalized.contains("grade i") || compact.contains("gradei") ||
                compact.contains("gradea") || compact == "a" -> 1

            else -> 0
        }
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
