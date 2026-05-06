package com.example.copra

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.copra.ml.BestFloat32Metadata
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class UploadActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "UploadActivity"
        private const val CONFIDENCE_THRESHOLD = 0.50f
        private const val PAGE_SIZE = 6
    }

    private var selectedImageUri: Uri? = null
    private var selectedBitmap: Bitmap? = null
    private var selectedFileName: String = "uploaded-image"
    private var isAnalyzed = false
    private var analysisSessionId = 0
    private var loggedOutputShape = false

    private lateinit var imgPreview: ImageView
    private lateinit var overlay: BoundingBoxView
    private lateinit var analyzeButton: MaterialButton
    private lateinit var loadingContainer: MaterialCardView
    private lateinit var uploadCard: MaterialCardView
    private lateinit var uploadContent: View
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var checkIcon: ImageView

    private lateinit var analysisExecutor: ExecutorService
    private lateinit var historyRepository: AnalysisHistoryRepository
    private lateinit var pricingRepository: PricingRepository
    private lateinit var classificationModelStore: ClassificationModelStore
    private var detectorModel: BestFloat32Metadata? = null
    private var classifier: CopraClassifier? = null
    private var selectedClassificationModel: ClassificationModelOption = ClassificationModels.default
    private var uploadedDetections: MutableList<CapturedDetection> = mutableListOf()
    private var lastPersistedAnalysisSessionId = -1

    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { pickedUri ->
                val bitmap = loadBitmapFromUri(pickedUri)
                if (bitmap == null) {
                    Toast.makeText(this, "Unable to load selected image.", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }

                selectedImageUri = pickedUri
                selectedBitmap = bitmap
                selectedFileName = resolveFileName(pickedUri)
                prepareForNewImage()
                showSelectedFile(pickedUri, bitmap)
                simulateUpload()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload)

        imgPreview = findViewById(R.id.imgPreview)
        overlay = findViewById(R.id.boundingOverlay)
        analyzeButton = findViewById(R.id.btnAnalyzeQuality)
        loadingContainer = findViewById(R.id.loadingContainer)
        uploadCard = findViewById(R.id.uploadProgressCard)
        uploadContent = findViewById(R.id.uploadContent)
        progressBar = findViewById(R.id.uploadProgressBar)
        statusText = findViewById(R.id.txtUploadStatus)
        checkIcon = findViewById(R.id.imgCheck)

        analysisExecutor = Executors.newSingleThreadExecutor()
        historyRepository = AnalysisHistoryRepository.getInstance(applicationContext)
        pricingRepository = PricingRepository.getInstance(applicationContext)
        classificationModelStore = ClassificationModelStore(applicationContext)
        selectedClassificationModel = classificationModelStore.getSelectedModel()

        initializeModels()

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener {
            val intent = Intent(this, HomePage::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        findViewById<MaterialButton>(R.id.btnSelectImage).setOnClickListener {
            imagePicker.launch("image/*")
        }

        findViewById<MaterialButton>(R.id.btnRemove).setOnClickListener {
            resetUploadUI()
        }

        analyzeButton.setOnClickListener {
            if (!isAnalyzed) {
                startImageAnalysis()
            } else {
                showResultsModal()
            }
        }

        val btnGallery = findViewById<MaterialButton>(R.id.btnGallery2)
        val btnScan = findViewById<MaterialButton>(R.id.btnScan2)
        val btnConfirm = findViewById<MaterialButton>(R.id.btnConfirm2)

        setActiveControl(btnGallery)

        btnGallery.setOnClickListener {
            setActiveControl(btnGallery)
        }

        btnScan.setOnClickListener {
            setActiveControl(btnScan)
            startActivity(Intent(this, ScanActivity::class.java))
        }

        btnConfirm.setOnClickListener {
            setActiveControl(btnConfirm)
            if (uploadedDetections.isNotEmpty() || isAnalyzed) {
                showResultsModal()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisSessionId += 1
        detectorModel?.close()
        detectorModel = null
        classifier?.close()
        classifier = null
        analysisExecutor.shutdownNow()
    }

    override fun onResume() {
        super.onResume()
        refreshSelectedClassificationModel()
    }

    private fun initializeModels() {
        if (detectorModel == null) {
            try {
                detectorModel = BestFloat32Metadata.newInstance(this)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize detection model", e)
                Toast.makeText(this, "Failed to initialize detection model.", Toast.LENGTH_SHORT).show()
            }
        }

        classifier?.close()
        classifier = CopraClassifier(applicationContext, selectedClassificationModel)
        if (classifier?.initialize() != true) {
            Log.w(TAG, "Classifier initialization failed for upload flow")
        }
    }

    private fun refreshSelectedClassificationModel() {
        val latestSelection = classificationModelStore.getSelectedModel()
        if (classifier == null || latestSelection.key != selectedClassificationModel.key) {
            selectedClassificationModel = latestSelection
            initializeModels()
        }
    }

    private fun prepareForNewImage() {
        analysisSessionId += 1
        isAnalyzed = false
        uploadedDetections.clear()
        overlay.clearBoxes()
        overlay.visibility = View.GONE
        analyzeButton.text = "Analyze Quality"
        analyzeButton.visibility = View.GONE
        loadingContainer.visibility = View.GONE
    }

    private fun startImageAnalysis() {
        val bitmap = selectedBitmap
        if (bitmap == null) {
            Toast.makeText(this, "Please select an image first.", Toast.LENGTH_SHORT).show()
            return
        }

        if (imgPreview.width == 0 || imgPreview.height == 0) {
            imgPreview.post { startImageAnalysis() }
            return
        }

        val sessionId = ++analysisSessionId
        lastPersistedAnalysisSessionId = -1
        isAnalyzed = false
        uploadedDetections.clear()
        overlay.clearBoxes()
        overlay.visibility = View.GONE
        analyzeButton.visibility = View.GONE
        loadingContainer.visibility = View.VISIBLE

        val previewWidth = imgPreview.width
        val previewHeight = imgPreview.height

        analysisExecutor.execute {
            val startMs = SystemClock.elapsedRealtime()
            val detections = runDetection(bitmap, previewWidth, previewHeight)
            val results = detections.sortedByDescending { it.score }.map { detection ->
                createUploadedDetection(bitmap, detection)
            }.toMutableList()

            val localClassifier = classifier
            val classifierReady = localClassifier?.initialize() == true
            if (!classifierReady) {
                results.indices.forEach { index ->
                    val item = results[index]
                    if (item.classificationStatus == ClassificationStatus.PENDING) {
                        results[index] = item.copy(classificationStatus = ClassificationStatus.FAILED)
                    }
                }
            } else {
                results.indices.forEach { index ->
                    val current = results[index]
                    if (current.classificationStatus != ClassificationStatus.PENDING) return@forEach

                    val classification = try {
                        localClassifier.classify(current.crop)
                    } catch (e: Exception) {
                        Log.e(TAG, "Upload classification failed at index=$index", e)
                        null
                    }

                    results[index] = if (classification == null) {
                        current.copy(
                            classificationStatus = ClassificationStatus.FAILED,
                            classificationLabel = null,
                            classificationConfidence = null,
                            classificationMs = null
                        )
                    } else {
                        Log.d(
                            TAG,
                            "Upload classification index=$index label=${classification.gradeLabel} confidence=${"%.3f".format(classification.confidence)} inferenceMs=${classification.inferenceMs}"
                        )
                        current.copy(
                            classificationLabel = classification.gradeLabel,
                            classificationConfidence = classification.confidence,
                            classificationStatus = ClassificationStatus.READY,
                            classificationMs = classification.inferenceMs
                        )
                    }
                }
            }

            val totalMs = SystemClock.elapsedRealtime() - startMs
            Log.d(TAG, "Upload analysis completed detections=${results.size} totalMs=$totalMs")

            runOnUiThread {
                if (sessionId != analysisSessionId || isFinishing || isDestroyed) {
                    return@runOnUiThread
                }

                uploadedDetections = results
                renderDetectedOverlay(results)

                loadingContainer.visibility = View.GONE
                analyzeButton.visibility = View.VISIBLE
                analyzeButton.text = "View Results"
                isAnalyzed = true
                persistUploadSessionIfNeeded(sessionId, bitmap, results)

                if (results.isEmpty()) {
                    Toast.makeText(this, "No copra detected in the uploaded image.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun createUploadedDetection(bitmap: Bitmap, detection: FrameDetection): CapturedDetection {
        val expandedRect = expandRectByPercent(detection.rectInFrame, 0.08f)
        val cropRect = clampRectForBitmap(expandedRect, bitmap.width, bitmap.height)
        val activeModel = selectedClassificationModel

        if (cropRect == null) {
            return CapturedDetection(
                crop = bitmap,
                sourceRect = RectF(),
                previewRect = RectF(detection.rectInPreview),
                label = detection.label,
                score = detection.score,
                classificationStatus = ClassificationStatus.FAILED,
                classificationModelKey = activeModel.key,
                classificationModelName = activeModel.displayName
            )
        }

        val crop = Bitmap.createBitmap(
            bitmap,
            cropRect.left,
            cropRect.top,
            cropRect.width(),
            cropRect.height()
        )

        val tooSmall = cropRect.width() < 32 || cropRect.height() < 32
        return CapturedDetection(
            crop = crop,
            sourceRect = RectF(cropRect),
            previewRect = RectF(detection.rectInPreview),
            label = detection.label,
            score = detection.score,
            classificationLabel = if (tooSmall) "Too small" else null,
            classificationStatus = if (tooSmall) ClassificationStatus.FAILED else ClassificationStatus.PENDING,
            classificationModelKey = activeModel.key,
            classificationModelName = activeModel.displayName
        )
    }

    private fun renderDetectedOverlay(items: List<CapturedDetection>) {
        val boxes = items.map { item ->
            val label = when (item.classificationStatus) {
                ClassificationStatus.READY -> item.classificationLabel ?: "Unknown"
                ClassificationStatus.FAILED -> "Unclassified"
                ClassificationStatus.PENDING -> "Classifying..."
            }
            val score = item.classificationConfidence ?: 0f
            BoundingBoxView.Box(
                rect = RectF(item.previewRect),
                label = label,
                color = colorForGrade(item.classificationLabel),
                score = score
            )
        }

        overlay.setBoxes(boxes)
        overlay.visibility = View.VISIBLE
    }

    private fun showResultsModal() {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetStyle)
        val view = layoutInflater.inflate(R.layout.results_modal, null)
        dialog.setContentView(view)

        view.findViewById<ImageView>(R.id.btnCloseResults).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<TextView>(R.id.tvFileName).text = selectedFileName

        val grade1 = view.findViewById<TextView>(R.id.tvGrade1Total)
        val grade2 = view.findViewById<TextView>(R.id.tvGrade2Total)
        val grade3 = view.findViewById<TextView>(R.id.tvGrade3Total)
        val recycler = view.findViewById<RecyclerView>(R.id.rvDetectedImages)
        val emptyState = view.findViewById<TextView>(R.id.tvEmptyStateUpload)
        val btnNext = view.findViewById<MaterialButton>(R.id.btnNext)
        val btnPrev = view.findViewById<MaterialButton>(R.id.btnPrev)
        val etPageNumber = view.findViewById<TextInputEditText>(R.id.etPageNumber)
        val tvTotalPages = view.findViewById<TextView>(R.id.tvTotalPages)
        val batchPriceValue = view.findViewById<TextView>(R.id.tvBatchPriceValue)
        val batchPriceCaption = view.findViewById<TextView>(R.id.tvBatchPriceCaption)
        val batchPriceMeta = view.findViewById<TextView>(R.id.tvBatchPriceMeta)
        val batchPriceProportions = view.findViewById<TextView>(R.id.tvBatchPriceProportions)
        val latencyValue = view.findViewById<TextView>(R.id.tvLatencyValue)
        val latencyMeta = view.findViewById<TextView>(R.id.tvLatencyMeta)

        recycler.layoutManager = GridLayoutManager(this, 3)
        recycler.isNestedScrollingEnabled = false

        val allDetected = uploadedDetections.toList()
        val readyDetections = allDetected.filter { it.classificationStatus == ClassificationStatus.READY }
        val grade1Count = readyDetections.count { gradeBucket(it.classificationLabel) == 1 }
        val grade2Count = readyDetections.count { gradeBucket(it.classificationLabel) == 2 }
        val grade3Count = readyDetections.count { gradeBucket(it.classificationLabel) == 3 }
        grade1.text = grade1Count.toString()
        grade2.text = grade2Count.toString()
        grade3.text = grade3Count.toString()
        val pricing = PricingCalculator.compute(
            grade1Count = grade1Count,
            grade2Count = grade2Count,
            grade3Count = grade3Count,
            latestPricing = pricingRepository.getCachedPricing()
        )
        batchPriceValue.text = PricingFormatter.formatBatchPrice(pricing)
        batchPriceCaption.text = PricingFormatter.buildBatchPriceCaption(pricing)
        batchPriceMeta.text = if (pricing != null) {
            "Effective date: ${PricingFormatter.formatEffectiveDate(pricing.effectiveDate)}\n" +
                "Saved on device: ${PricingFormatter.formatSyncedAt(pricing.syncedAtMillis)}"
        } else {
            "Open Home while online to download the latest pricing for offline use."
        }
        batchPriceProportions.text = PricingFormatter.buildProportionSummary(
            grade1Count = grade1Count,
            grade2Count = grade2Count,
            grade3Count = grade3Count
        )
        val latency = ClassificationLatency.fromCapturedItems(allDetected)
        latencyValue.text = ClassificationLatencyFormatter.formatDetail(latency)
        latencyMeta.text = ClassificationLatencyFormatter.formatMeta(latency)

        val adapter = CapturedImageAdapter(emptyList())
        recycler.adapter = adapter

        if (allDetected.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            recycler.visibility = View.GONE
            btnPrev.isEnabled = false
            btnNext.isEnabled = false
            etPageNumber.setText("1")
            tvTotalPages.text = "of 1"
        } else {
            emptyState.visibility = View.GONE
            recycler.visibility = View.VISIBLE

            var currentPage = 1
            val totalPages = max(1, (allDetected.size + PAGE_SIZE - 1) / PAGE_SIZE)

            fun updatePage(page: Int) {
                val start = (page - 1) * PAGE_SIZE
                val end = min(start + PAGE_SIZE, allDetected.size)
                adapter.updateData(allDetected.subList(start, end))
                etPageNumber.setText(page.toString())
                tvTotalPages.text = "of $totalPages"
                btnPrev.isEnabled = page > 1
                btnNext.isEnabled = page < totalPages
            }

            updatePage(currentPage)

            btnNext.setOnClickListener {
                if (currentPage < totalPages) {
                    currentPage += 1
                    updatePage(currentPage)
                }
            }

            btnPrev.setOnClickListener {
                if (currentPage > 1) {
                    currentPage -= 1
                    updatePage(currentPage)
                }
            }
        }

        dialog.show()
        val behavior = dialog.behavior
        behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.5).toInt()
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun setActiveControl(activeButton: MaterialButton) {
        val btnGallery = findViewById<MaterialButton>(R.id.btnGallery2)
        val btnScan = findViewById<MaterialButton>(R.id.btnScan2)
        val btnConfirm = findViewById<MaterialButton>(R.id.btnConfirm2)

        btnGallery.setIconResource(R.drawable.upload__1_)
        btnScan.setIconResource(R.drawable.scan)
        btnConfirm.setIconResource(R.drawable.check)

        when (activeButton.id) {
            R.id.btnGallery2 -> btnGallery.setIconResource(R.drawable.upload)
            R.id.btnScan2 -> btnScan.setIconResource(R.drawable.scan__1_)
            R.id.btnConfirm2 -> btnConfirm.setIconResource(R.drawable.check)
        }
    }

    private fun showSelectedFile(uri: Uri, bitmap: Bitmap) {
        findViewById<TextView>(R.id.txtFileName).text = resolveFileName(uri)

        uploadCard.visibility = View.VISIBLE
        imgPreview.setImageBitmap(bitmap)
        imgPreview.visibility = View.VISIBLE
        uploadContent.visibility = View.GONE
        overlay.clearBoxes()
        overlay.visibility = View.GONE
    }

    private fun simulateUpload() {
        analyzeButton.visibility = View.GONE
        checkIcon.visibility = View.GONE
        progressBar.progress = 100
        statusText.text = "Upload Complete"
        statusText.setTextColor(Color.parseColor("#4CAF50"))
        checkIcon.visibility = View.VISIBLE
        analyzeButton.visibility = View.VISIBLE
    }

    private fun resetUploadUI() {
        analysisSessionId += 1
        selectedImageUri = null
        selectedBitmap = null
        selectedFileName = "uploaded-image"
        uploadedDetections.clear()
        lastPersistedAnalysisSessionId = -1
        isAnalyzed = false

        uploadCard.visibility = View.GONE
        analyzeButton.visibility = View.GONE
        analyzeButton.text = "Analyze Quality"
        loadingContainer.visibility = View.GONE

        imgPreview.setImageDrawable(null)
        imgPreview.visibility = View.GONE
        uploadContent.visibility = View.VISIBLE
        overlay.clearBoxes()
        overlay.visibility = View.GONE

        progressBar.progress = 0
        statusText.text = "Uploading..."
        statusText.setTextColor(Color.parseColor("#666666"))
        checkIcon.visibility = View.GONE
    }

    private fun persistUploadSessionIfNeeded(
        sessionId: Int,
        fullImage: Bitmap,
        results: List<CapturedDetection>
    ) {
        if (sessionId != analysisSessionId) return
        if (lastPersistedAnalysisSessionId == sessionId) return
        if (results.isEmpty()) return

        lastPersistedAnalysisSessionId = sessionId
        historyRepository.saveSession(
            sourceType = AnalysisSourceType.UPLOAD,
            modelOption = selectedClassificationModel,
            fullImage = fullImage,
            items = results,
            onComplete = { savedSessionId ->
                Log.d(TAG, "Saved upload history sessionId=$savedSessionId analysisSession=$sessionId")
            },
            onError = { throwable ->
                if (analysisSessionId == sessionId) {
                    lastPersistedAnalysisSessionId = -1
                }
                Log.e(TAG, "Failed to save upload history for analysisSession=$sessionId", throwable)
            }
        )
    }

    private fun resolveFileName(uri: Uri): String {
        return uri.lastPathSegment?.substringAfterLast("/") ?: "selected_image.jpg"
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode bitmap from uri", e)
            null
        }
    }

    private fun runDetection(
        bitmap: Bitmap,
        previewWidth: Int,
        previewHeight: Int
    ): List<FrameDetection> {
        val model = detectorModel ?: return emptyList()
        val image = TensorImage.fromBitmap(bitmap)
        val outputs = model.process(image)

        parseDetectionsFromTensor(outputs.locationAsTensorBuffer, bitmap.width, bitmap.height, previewWidth, previewHeight)?.let {
            return it
        }

        val rawDetections = try {
            outputs.detectionResultList
        } catch (e: Exception) {
            Log.e(TAG, "Unable to parse upload detection results", e)
            return emptyList()
        }

        return rawDetections.mapNotNull { detectionResult ->
            val rawRect = extractRect(detectionResult) ?: return@mapNotNull null
            val score = extractScore(detectionResult)
            if (score < CONFIDENCE_THRESHOLD) return@mapNotNull null

            val frameRect = normalizeAndClampRect(rawRect, bitmap.width.toFloat(), bitmap.height.toFloat())
                ?: return@mapNotNull null
            val previewRect = mapFrameRectToUploadPreview(frameRect, bitmap.width, bitmap.height, previewWidth, previewHeight)

            FrameDetection(
                rectInFrame = frameRect,
                rectInPreview = previewRect,
                label = extractLabel(detectionResult),
                score = score
            )
        }
    }

    private fun parseDetectionsFromTensor(
        tensorBuffer: TensorBuffer,
        frameWidth: Int,
        frameHeight: Int,
        previewWidth: Int,
        previewHeight: Int
    ): List<FrameDetection>? {
        val values = tensorBuffer.floatArray
        if (values.isEmpty()) return emptyList()

        val shape = tensorBuffer.shape
        val featureCount = inferFeatureCount(shape, values.size) ?: return null
        if (featureCount < 4) return null

        if (!loggedOutputShape) {
            Log.d(
                TAG,
                "Upload model output shape=${shape.joinToString(prefix = "[", postfix = "]")} featureCount=$featureCount"
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
            val previewRect = mapFrameRectToUploadPreview(frameRect, frameWidth, frameHeight, previewWidth, previewHeight)

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
            ?: 1f
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

            when (val value = method.invoke(target)) {
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

            (method.invoke(target) as? String)?.takeIf { it.isNotBlank() }
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

    private fun mapFrameRectToUploadPreview(
        rectInFrame: RectF,
        frameWidth: Int,
        frameHeight: Int,
        previewWidth: Int,
        previewHeight: Int
    ): RectF {
        val previewW = previewWidth.toFloat()
        val previewH = previewHeight.toFloat()
        if (previewW <= 0f || previewH <= 0f) {
            return RectF(rectInFrame)
        }

        val scale = min(previewW / frameWidth.toFloat(), previewH / frameHeight.toFloat())
        val scaledWidth = frameWidth * scale
        val scaledHeight = frameHeight * scale
        val dx = (previewW - scaledWidth) / 2f
        val dy = (previewH - scaledHeight) / 2f

        return RectF(
            rectInFrame.left * scale + dx,
            rectInFrame.top * scale + dy,
            rectInFrame.right * scale + dx,
            rectInFrame.bottom * scale + dy
        )
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

    private fun colorForGrade(label: String?): Int {
        return when (gradeBucket(label)) {
            1 -> Color.parseColor("#2E7D32")
            2 -> Color.parseColor("#F9A825")
            3 -> Color.parseColor("#C62828")
            else -> Color.parseColor("#455A64")
        }
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

    private fun Float.isFiniteValue(): Boolean = !isNaN() && !isInfinite()
}
