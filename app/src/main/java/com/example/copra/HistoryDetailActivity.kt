package com.example.copra

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class HistoryDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
        private const val TAG = "HistoryDetailActivity"
        private const val PAGE_SIZE = 6
    }

    private lateinit var historyRepository: AnalysisHistoryRepository
    private lateinit var imageView: ImageView
    private lateinit var overlayView: BoundingBoxView
    private lateinit var btnViewResults: MaterialButton
    private lateinit var btnDeleteHistory: MaterialButton
    private lateinit var txtSource: TextView
    private lateinit var txtDate: TextView
    private lateinit var txtModel: TextView
    private lateinit var txtCountSummary: TextView
    private lateinit var txtDetectionCount: TextView
    private lateinit var txtComputedPricing: TextView
    private lateinit var txtPricingMeta: TextView
    private lateinit var txtPricingProportions: TextView
    private lateinit var txtLatency: TextView
    private lateinit var txtLatencyMeta: TextView
    private lateinit var loadingView: View

    private val decodeExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var currentSession: AnalysisHistorySession? = null
    private var displayItems: List<CapturedDetection> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history_detail)

        historyRepository = AnalysisHistoryRepository.getInstance(applicationContext)
        imageView = findViewById(R.id.imgHistoryDetail)
        overlayView = findViewById(R.id.historyBoundingOverlay)
        btnViewResults = findViewById(R.id.btnHistoryViewResults)
        btnDeleteHistory = findViewById(R.id.btnDeleteHistory)
        txtSource = findViewById(R.id.tvHistorySource)
        txtDate = findViewById(R.id.tvHistoryDate)
        txtModel = findViewById(R.id.tvHistoryModel)
        txtCountSummary = findViewById(R.id.tvHistorySummary)
        txtDetectionCount = findViewById(R.id.tvHistoryDetectionCount)
        txtComputedPricing = findViewById(R.id.tvHistoryComputedPricing)
        txtPricingMeta = findViewById(R.id.tvHistoryPricingMeta)
        txtPricingProportions = findViewById(R.id.tvHistoryPricingProportions)
        txtLatency = findViewById(R.id.tvHistoryLatency)
        txtLatencyMeta = findViewById(R.id.tvHistoryLatencyMeta)
        loadingView = findViewById(R.id.historyLoadingView)
        btnViewResults.isEnabled = false
        btnDeleteHistory.isEnabled = false

        findViewById<View>(R.id.btnHistoryBack).setOnClickListener { finish() }
        btnViewResults.setOnClickListener { showResultsModal() }
        btnDeleteHistory.setOnClickListener { confirmDeleteCurrentHistory() }

        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        if (sessionId <= 0L) {
            Toast.makeText(this, "Unable to open saved result.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadSession(sessionId)
    }

    override fun onDestroy() {
        super.onDestroy()
        decodeExecutor.shutdownNow()
    }

    private fun loadSession(sessionId: Long) {
        loadingView.visibility = View.VISIBLE
        historyRepository.loadSession(
            sessionId = sessionId,
            onComplete = { session ->
                if (session == null) {
                    Toast.makeText(this, "Saved analysis was not found.", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    currentSession = session
                    bindSessionHeader(session)
                    decodeAssets(session)
                }
            },
            onError = { throwable ->
                Log.e(TAG, "Failed to load session $sessionId", throwable)
                Toast.makeText(this, "Unable to load saved analysis.", Toast.LENGTH_SHORT).show()
                finish()
            }
        )
    }

    private fun bindSessionHeader(session: AnalysisHistorySession) {
        txtSource.text = if (session.sourceType == AnalysisSourceType.SCAN) "Scan History" else "Upload History"
        txtDate.text = historyRepository.formatDate(session.createdAt)
        txtModel.text = "Model: ${session.classificationModelName ?: "Model not recorded"}"
        txtCountSummary.text = historyRepository.buildCountSummary(session)
        txtDetectionCount.text = "${session.detectionCount} detected copra"
        txtComputedPricing.text = "Estimated price per kg: ${PricingFormatter.formatBatchPrice(session.pricing)}"
        txtPricingMeta.text = if (session.pricing != null) {
            "Effective date: ${PricingFormatter.formatEffectiveDate(session.pricing.effectiveDate)}"
        } else {
            "Latest pricing has not been saved on this device yet."
        }
        txtPricingProportions.text = PricingFormatter.buildProportionSummary(
            grade1Count = session.grade1Count,
            grade2Count = session.grade2Count,
            grade3Count = session.grade3Count
        )
        txtLatency.text = ClassificationLatencyFormatter.formatHeadline(session.latency)
        txtLatencyMeta.text = ClassificationLatencyFormatter.formatMeta(session.latency)
    }

    private fun decodeAssets(session: AnalysisHistorySession) {
        decodeExecutor.execute {
            val fullBitmap = historyRepository.decodeBitmap(
                session.fullImagePath,
                resources.displayMetrics.widthPixels,
                (resources.displayMetrics.heightPixels * 0.5f).toInt()
            )
            val items = session.items.map { item ->
                val cropBitmap = historyRepository.decodeBitmap(item.cropImagePath, 256, 256)
                    ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

                CapturedDetection(
                    crop = cropBitmap,
                    sourceRect = RectF(item.sourceRect),
                    previewRect = RectF(),
                    label = "Copra",
                    score = 1f,
                    classificationLabel = item.classificationLabel,
                    classificationConfidence = item.classificationConfidence,
                    classificationStatus = item.classificationStatus,
                    classificationMs = item.classificationMs,
                    classificationModelKey = item.classificationModelKey,
                    classificationModelName = item.classificationModelName
                )
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                loadingView.visibility = View.GONE
                displayItems = items
                if (fullBitmap == null) {
                    Toast.makeText(this, "Saved image is no longer available.", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }

                imageView.setImageBitmap(fullBitmap)
                imageView.post {
                    renderOverlay(session, fullBitmap)
                }
                btnViewResults.isEnabled = items.isNotEmpty()
                btnDeleteHistory.isEnabled = true
            }
        }
    }

    private fun renderOverlay(session: AnalysisHistorySession, bitmap: Bitmap) {
        val previewWidth = imageView.width
        val previewHeight = imageView.height
        if (previewWidth <= 0 || previewHeight <= 0) {
            return
        }

        val boxes = session.items.map { item ->
            BoundingBoxView.Box(
                rect = mapFrameRectToPreview(
                    item.sourceRect,
                    bitmap.width,
                    bitmap.height,
                    previewWidth,
                    previewHeight
                ),
                label = when (item.classificationStatus) {
                    ClassificationStatus.READY -> item.classificationLabel ?: "Unknown"
                    ClassificationStatus.FAILED -> "Unclassified"
                    ClassificationStatus.PENDING -> "Classifying..."
                },
                color = colorForStatus(item.classificationLabel, item.classificationStatus),
                score = item.classificationConfidence ?: 0f
            )
        }
        overlayView.setBoxes(boxes)
        overlayView.visibility = View.VISIBLE
    }

    private fun mapFrameRectToPreview(
        rectInFrame: RectF,
        frameWidth: Int,
        frameHeight: Int,
        previewWidth: Int,
        previewHeight: Int
    ): RectF {
        val previewW = previewWidth.toFloat()
        val previewH = previewHeight.toFloat()
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

    private fun colorForStatus(label: String?, status: ClassificationStatus): Int {
        return when (status) {
            ClassificationStatus.PENDING -> Color.parseColor("#757575")
            ClassificationStatus.FAILED -> Color.parseColor("#9E9E9E")
            ClassificationStatus.READY -> when (historyRepository.gradeBucket(label)) {
                1 -> Color.parseColor("#2E7D32")
                2 -> Color.parseColor("#F9A825")
                3 -> Color.parseColor("#C62828")
                else -> Color.parseColor("#455A64")
            }
        }
    }

    private fun showResultsModal() {
        val session = currentSession ?: return
        val allItems = displayItems

        val dialog = BottomSheetDialog(this, R.style.BottomSheetStyle)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_results, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.tvTitle).text = "Saved Analysis Results"
        view.findViewById<ImageView>(R.id.btnClose).setOnClickListener { dialog.dismiss() }

        val summarySection = view.findViewById<View>(R.id.layoutSummarySection)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerImages)
        val emptyState = view.findViewById<TextView>(R.id.tvEmptyState)
        val btnPrev = view.findViewById<MaterialButton>(R.id.btnPrev)
        val btnNext = view.findViewById<MaterialButton>(R.id.btnNext)
        val pageInput = view.findViewById<TextInputEditText>(R.id.etPageNumber)
        val totalPagesText = view.findViewById<TextView>(R.id.tvTotalPages)
        val grade1Text = view.findViewById<TextView>(R.id.tvGrade1Count)
        val grade2Text = view.findViewById<TextView>(R.id.tvGrade2Count)
        val grade3Text = view.findViewById<TextView>(R.id.tvGrade3Count)
        val pricingCard = view.findViewById<View>(R.id.cardPricingSummary)
        val batchPriceValue = view.findViewById<TextView>(R.id.tvBatchPriceValue)
        val batchPriceCaption = view.findViewById<TextView>(R.id.tvBatchPriceCaption)
        val batchPriceMeta = view.findViewById<TextView>(R.id.tvBatchPriceMeta)
        val batchPriceProportions = view.findViewById<TextView>(R.id.tvBatchPriceProportions)
        val latencyValue = view.findViewById<TextView>(R.id.tvLatencyValue)
        val latencyMeta = view.findViewById<TextView>(R.id.tvLatencyMeta)

        recycler.layoutManager = GridLayoutManager(this, 3)
        recycler.isNestedScrollingEnabled = false
        val adapter = CapturedImageAdapter(emptyList())
        recycler.adapter = adapter

        grade1Text.text = session.grade1Count.toString()
        grade2Text.text = session.grade2Count.toString()
        grade3Text.text = session.grade3Count.toString()
        bindPricingSummary(
            pricing = session.pricing,
            grade1Count = session.grade1Count,
            grade2Count = session.grade2Count,
            grade3Count = session.grade3Count,
            pricingCard = pricingCard,
            batchPriceValue = batchPriceValue,
            batchPriceCaption = batchPriceCaption,
            batchPriceMeta = batchPriceMeta,
            batchPriceProportions = batchPriceProportions,
            latency = session.latency,
            latencyValue = latencyValue,
            latencyMeta = latencyMeta
        )

        if (allItems.isEmpty()) {
            summarySection.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
            recycler.visibility = View.GONE
            btnPrev.isEnabled = false
            btnNext.isEnabled = false
            pageInput.setText("1")
            totalPagesText.text = "of 1"
        } else {
            summarySection.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            recycler.visibility = View.VISIBLE

            var currentPage = 1
            val totalPages = max(1, (allItems.size + PAGE_SIZE - 1) / PAGE_SIZE)

            fun renderPage(page: Int) {
                val start = (page - 1) * PAGE_SIZE
                val end = min(start + PAGE_SIZE, allItems.size)
                adapter.updateData(allItems.subList(start, end))
                pageInput.setText(page.toString())
                totalPagesText.text = "of $totalPages"
                btnPrev.isEnabled = page > 1
                btnNext.isEnabled = page < totalPages
            }

            renderPage(currentPage)

            btnPrev.setOnClickListener {
                if (currentPage > 1) {
                    currentPage -= 1
                    renderPage(currentPage)
                }
            }

            btnNext.setOnClickListener {
                if (currentPage < totalPages) {
                    currentPage += 1
                    renderPage(currentPage)
                }
            }
        }

        dialog.show()
        val behavior = dialog.behavior
        behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.5).toInt()
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun bindPricingSummary(
        pricing: AnalysisPricing?,
        grade1Count: Int,
        grade2Count: Int,
        grade3Count: Int,
        pricingCard: View,
        batchPriceValue: TextView,
        batchPriceCaption: TextView,
        batchPriceMeta: TextView,
        batchPriceProportions: TextView,
        latency: ClassificationLatencySummary?,
        latencyValue: TextView,
        latencyMeta: TextView
    ) {
        pricingCard.visibility = View.VISIBLE
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
        latencyValue.text = ClassificationLatencyFormatter.formatDetail(latency)
        latencyMeta.text = ClassificationLatencyFormatter.formatMeta(latency)
    }

    private fun confirmDeleteCurrentHistory() {
        val sessionId = currentSession?.id ?: return

        AlertDialog.Builder(this)
            .setTitle("Delete this history?")
            .setMessage("This saved analysis will be permanently removed from this device.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                btnDeleteHistory.isEnabled = false
                btnViewResults.isEnabled = false
                historyRepository.deleteSession(
                    sessionId = sessionId,
                    onComplete = {
                        Toast.makeText(this, "History deleted.", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onError = { throwable ->
                        Log.e(TAG, "Failed to delete history session $sessionId", throwable)
                        btnDeleteHistory.isEnabled = true
                        btnViewResults.isEnabled = displayItems.isNotEmpty()
                        Toast.makeText(this, "Unable to delete this history.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .show()
    }
}
