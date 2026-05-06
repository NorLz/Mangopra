package com.example.copra

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton

class HomePage : AppCompatActivity() {

    companion object {
        private const val TAG = "HomePage"
        private const val HOME_REFRESH_STALE_MS = 15 * 60 * 1000L
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var clearAllButton: MaterialButton
    private lateinit var modelSelectorButton: MaterialButton
    private lateinit var adapter: ResultAdapter
    private lateinit var historyRepository: AnalysisHistoryRepository
    private lateinit var pricingRepository: PricingRepository
    private lateinit var classificationModelStore: ClassificationModelStore
    private lateinit var pricingFab: FloatingActionButton

    private val fullList = mutableListOf<ResultModel>()
    private var currentPage = 0
    private val pageSize = 6
    private var latestPricing: LatestPricing? = null
    private var latestPricingNotice: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_page)

        recyclerView = findViewById(R.id.recyclerViewCards)
        emptyStateText = findViewById(R.id.tvHistoryEmptyState)
        clearAllButton = findViewById(R.id.btnClearAllHistory)
        modelSelectorButton = findViewById(R.id.button2)
        historyRepository = AnalysisHistoryRepository.getInstance(applicationContext)
        pricingRepository = PricingRepository.getInstance(applicationContext)
        classificationModelStore = ClassificationModelStore(applicationContext)
        pricingFab = findViewById(R.id.fabLatestPricing)
        updateModelSelectorText()
        latestPricing = pricingRepository.getCachedPricing()
        updatePricingFabState()

        val gridLayoutManager = GridLayoutManager(this, 1)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return 1
            }
        }
        recyclerView.layoutManager = gridLayoutManager

        val homeBtn = findViewById<ImageButton>(R.id.imageButton15)
        val scanBtn = findViewById<ImageButton>(R.id.imageButton13)
        val uploadBtn = findViewById<ImageButton>(R.id.imageButton16)
        val indicator = findViewById<View>(R.id.view5)

        fun setActive(button: ImageButton) {
            homeBtn.setImageResource(R.drawable.iconly_icon_export_1773057824)
            scanBtn.setImageResource(R.drawable.scan)
            uploadBtn.setImageResource(R.drawable.upload__1_)

            when (button.id) {
                R.id.imageButton15 -> {
                    homeBtn.setImageResource(R.drawable.iconly_icon_export_1773064234)
                    moveIndicator(homeBtn, indicator)
                }

                R.id.imageButton13 -> {
                    scanBtn.setImageResource(R.drawable.scan__1_)
                    moveIndicator(scanBtn, indicator)
                }

                R.id.imageButton16 -> {
                    uploadBtn.setImageResource(R.drawable.upload)
                    moveIndicator(uploadBtn, indicator)
                }
            }
        }

        homeBtn.setOnClickListener { setActive(homeBtn) }
        scanBtn.setOnClickListener {
            setActive(scanBtn)
            startActivity(Intent(this, ScanActivity::class.java))
            finish()
        }
        uploadBtn.setOnClickListener {
            setActive(uploadBtn)
            startActivity(Intent(this, UploadActivity::class.java))
            finish()
        }

        homeBtn.post { setActive(homeBtn) }

        clearAllButton.setOnClickListener {
            confirmClearAllHistory()
        }

        val modelSelectorArrow = findViewById<ImageView>(R.id.imageView19)
        val openModelSelector = View.OnClickListener { showModelSelectionDialog() }
        modelSelectorButton.setOnClickListener(openModelSelector)
        modelSelectorArrow.setOnClickListener(openModelSelector)
        pricingFab.setOnClickListener { showLatestPricingSheet() }
    }

    override fun onResume() {
        super.onResume()
        updateModelSelectorText()
        loadHistorySessions()
        refreshLatestPricing(force = false)
    }

    private fun updateModelSelectorText() {
        val selectedModel = classificationModelStore.getSelectedModel()
        modelSelectorButton.text = "Model: ${selectedModel.displayName}"
    }

    private fun showModelSelectionDialog() {
        val currentModel = classificationModelStore.getSelectedModel()
        val modelNames = ClassificationModels.all.map { it.displayName }.toTypedArray()
        val checkedIndex = ClassificationModels.all.indexOfFirst { it.key == currentModel.key }

        AlertDialog.Builder(this)
            .setTitle("Choose classification model")
            .setSingleChoiceItems(modelNames, checkedIndex) { dialog, which ->
                val selectedModel = ClassificationModels.all[which]
                classificationModelStore.setSelectedModel(selectedModel)
                updateModelSelectorText()
                Toast.makeText(
                    this,
                    "${selectedModel.displayName} is now the default model.",
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadHistorySessions() {
        historyRepository.loadRecentSessions(
            onComplete = { sessions ->
                fullList.clear()
                fullList.addAll(
                    sessions.map { session ->
                        ResultModel(
                            sessionId = session.id,
                            imagePath = session.fullImagePath,
                            sourceLabel = if (session.sourceType == AnalysisSourceType.SCAN) "Scan" else "Upload",
                            status = "${session.detectionCount} copra analyzed",
                            gradeSummary = "Grades  I:${session.grade1Count}  II:${session.grade2Count}  III:${session.grade3Count}",
                            pricingSummary = "Estimated price per kg: ${PricingFormatter.formatBatchPrice(session.pricing)}",
                            date = historyRepository.formatDate(session.createdAt),
                            grade1Count = session.grade1Count,
                            grade2Count = session.grade2Count,
                            grade3Count = session.grade3Count
                        )
                    }
                )

                if (fullList.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    emptyStateText.visibility = View.VISIBLE
                    clearAllButton.visibility = View.GONE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    emptyStateText.visibility = View.GONE
                    clearAllButton.visibility = View.VISIBLE
                    showPage(0)
                }
            },
            onError = { throwable ->
                Log.e(TAG, "Failed to load history sessions", throwable)
                recyclerView.visibility = View.GONE
                emptyStateText.visibility = View.VISIBLE
                clearAllButton.visibility = View.GONE
                Toast.makeText(this, "Unable to load history yet.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showPage(page: Int) {
        if (fullList.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
            return
        }

        currentPage = page
        val fromIndex = page * pageSize
        val toIndex = minOf(fromIndex + pageSize, fullList.size)
        val pageList = fullList.subList(fromIndex, toIndex)
        val totalPages = (fullList.size + pageSize - 1) / pageSize

        adapter = ResultAdapter(
            list = pageList.toMutableList(),
            currentPage = currentPage,
            totalPages = totalPages,
            onItemClick = { item ->
                startActivity(
                    Intent(this, HistoryDetailActivity::class.java).apply {
                        putExtra(HistoryDetailActivity.EXTRA_SESSION_ID, item.sessionId)
                    }
                )
            },
            onPageClick = { showPage(it) },
            onPrevClick = { if (currentPage > 0) showPage(currentPage - 1) },
            onNextClick = { if (currentPage < totalPages - 1) showPage(currentPage + 1) }
        )

        recyclerView.adapter = adapter

        val layoutManager = recyclerView.layoutManager as GridLayoutManager
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return 1
            }
        }
    }

    fun moveIndicator(button: ImageButton, indicator: View) {
        indicator.animate()
            .x(button.x + button.width / 2 - indicator.width / 2)
            .setDuration(200)
            .start()
    }

    private fun confirmClearAllHistory() {
        if (fullList.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle("Delete all history?")
            .setMessage("This will permanently remove all saved scan and upload history from this device.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete All") { _, _ ->
                clearAllButton.isEnabled = false
                historyRepository.deleteAllSessions(
                    onComplete = {
                        clearAllButton.isEnabled = true
                        Toast.makeText(this, "All history deleted.", Toast.LENGTH_SHORT).show()
                        loadHistorySessions()
                    },
                    onError = { throwable ->
                        clearAllButton.isEnabled = true
                        Log.e(TAG, "Failed to delete all history", throwable)
                        Toast.makeText(this, "Unable to delete all history.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .show()
    }

    private fun refreshLatestPricing(force: Boolean) {
        latestPricing = pricingRepository.getCachedPricing()
        updatePricingFabState()
        if (!pricingRepository.isConfigured()) return
        if (!force && !pricingRepository.shouldRefresh(HOME_REFRESH_STALE_MS)) return

        pricingRepository.refreshLatestPricing(
            onComplete = { result ->
                latestPricing = result.pricing
                latestPricingNotice = if (result.hasChanges) {
                    "Latest pricing updated and saved on this device for offline use."
                } else {
                    "No new pricing changes were found. Your phone is still using the latest saved pricing."
                }
                updatePricingFabState()
                if (result.hasChanges) {
                    loadHistorySessions()
                }
            },
            onError = { throwable, cached ->
                latestPricing = cached ?: latestPricing
                latestPricingNotice = if (latestPricing != null) {
                    "We could not reach the server, so the app is showing your last saved pricing."
                } else {
                    "We could not reach the server and there is no saved pricing yet."
                }
                updatePricingFabState()
                Log.w(TAG, "Pricing refresh failed; using cached pricing if available", throwable)
            },
            onSkipped = { }
        )
    }

    private fun updatePricingFabState() {
        pricingFab.alpha = if (latestPricing != null || pricingRepository.isConfigured()) 1f else 0.65f
    }

    private fun showLatestPricingSheet() {
        val cachedPricing = latestPricing ?: pricingRepository.getCachedPricing()
        if (cachedPricing == null && !pricingRepository.isConfigured()) {
            Toast.makeText(
                this,
                "Set PRICING_API_BASE_URL for this build before fetching pricing.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val dialog = BottomSheetDialog(this, R.style.BottomSheetStyle)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_latest_pricing, null)
        dialog.setContentView(view)

        view.findViewById<ImageView>(R.id.btnClosePricingSheet).setOnClickListener { dialog.dismiss() }
        val refreshButton = view.findViewById<MaterialButton>(R.id.btnRefreshPricing)
        val refreshHint = view.findViewById<TextView>(R.id.tvPricingRefreshHint)
        val refreshProgress = view.findViewById<ProgressBar>(R.id.progressRefreshPricing)
        val refreshIcon = AppCompatResources.getDrawable(this, R.drawable.system_update_alt)

        fun bindPricingSheet(pricing: LatestPricing?) {
            view.findViewById<TextView>(R.id.tvPricingStatus).text = if (pricing != null) {
                PricingFormatter.formatRelativeSyncAge(pricing.syncedAtMillis)
            } else {
                "No pricing saved on this device yet"
            }
            view.findViewById<TextView>(R.id.tvLatestGrade1Price).text =
                PricingFormatter.formatPricePerKg(pricing?.grade1PricePerKg, pricing?.unit)
            view.findViewById<TextView>(R.id.tvLatestGrade2Price).text =
                PricingFormatter.formatPricePerKg(pricing?.grade2PricePerKg, pricing?.unit)
            view.findViewById<TextView>(R.id.tvLatestGrade3Price).text =
                PricingFormatter.formatPricePerKg(pricing?.grade3PricePerKg, pricing?.unit)
            view.findViewById<TextView>(R.id.tvPricingCommodity).text =
                pricing?.commodityName ?: "Latest copra pricing unavailable"
            view.findViewById<TextView>(R.id.tvPricingEffectiveDate).text =
                "Price effective from: ${PricingFormatter.formatEffectiveDate(pricing?.effectiveDate)}"
            view.findViewById<TextView>(R.id.tvPricingRecordedAt).text =
                "Saved on server: ${PricingFormatter.formatRecordedAt(pricing?.recordedAt)}"
            view.findViewById<TextView>(R.id.tvPricingSyncedAt).text =
                "Saved on device: ${PricingFormatter.formatSyncedAt(pricing?.syncedAtMillis)}"
            view.findViewById<TextView>(R.id.tvPricingSource).text =
                "Source: ${pricing?.sourceLabel ?: "Not provided"}"
            view.findViewById<TextView>(R.id.tvPricingNotes).text =
                pricing?.notes?.takeIf { it.isNotBlank() }
                    ?: "This latest pricing snapshot will be reused when the app is offline."
        }
        fun updateRefreshUi(
            isRefreshing: Boolean,
            helperText: String,
            buttonLabel: String
        ) {
            refreshButton.isEnabled = !isRefreshing
            refreshButton.alpha = if (isRefreshing) 0.55f else 1f
            refreshButton.text = buttonLabel
            refreshButton.icon = if (isRefreshing) null else refreshIcon
            refreshHint.text = helperText
            refreshProgress.visibility = if (isRefreshing) View.VISIBLE else View.GONE
        }

        bindPricingSheet(cachedPricing)
        updateRefreshUi(
            isRefreshing = pricingRepository.isRefreshInProgress(),
            helperText = if (pricingRepository.isRefreshInProgress()) {
                "We are already checking the latest backend pricing. The button will re-enable when it finishes."
            } else {
                if (!latestPricingNotice.isNullOrBlank()) {
                    latestPricingNotice!!
                } else if (cachedPricing != null) {
                    "You can keep using this saved pricing offline, or refresh now to check for a newer update."
                } else {
                    "Download the latest pricing once so it stays available offline."
                }
            },
            buttonLabel = if (pricingRepository.isRefreshInProgress()) "Please wait" else "Refresh Latest Pricing"
        )

        refreshButton.setOnClickListener {
            if (!pricingRepository.isConfigured()) {
                updateRefreshUi(
                    isRefreshing = false,
                    helperText = "Pricing endpoint is not configured for this build.",
                    buttonLabel = "Refresh Latest Pricing"
                )
                return@setOnClickListener
            }

            updateRefreshUi(
                isRefreshing = true,
                helperText = "Checking the server for a newer pricing update...",
                buttonLabel = "Please wait"
            )
            pricingRepository.refreshLatestPricing(
                onComplete = { result ->
                    latestPricing = result.pricing
                    latestPricingNotice = if (result.hasChanges) {
                        "Latest pricing updated and saved on this device for offline use."
                    } else {
                        "No new pricing changes were found. Your phone is still using the latest saved pricing."
                    }
                    bindPricingSheet(result.pricing)
                    updateRefreshUi(
                        isRefreshing = false,
                        helperText = latestPricingNotice!!,
                        buttonLabel = "Refresh Latest Pricing"
                    )
                    if (result.hasChanges) {
                        loadHistorySessions()
                    }
                },
                onError = { _, fallback ->
                    latestPricing = fallback ?: latestPricing
                    latestPricingNotice = if (latestPricing != null) {
                        "We could not reach the server, so the app is showing your last saved pricing."
                    } else {
                        "We could not reach the server and there is no saved pricing yet."
                    }
                    bindPricingSheet(latestPricing)
                    updateRefreshUi(
                        isRefreshing = false,
                        helperText = latestPricingNotice!!,
                        buttonLabel = "Refresh Latest Pricing"
                    )
                },
                onSkipped = {
                    updateRefreshUi(
                        isRefreshing = true,
                        helperText = "A refresh is already in progress. Please wait for it to finish.",
                        buttonLabel = "Please wait"
                    )
                }
            )
        }

        dialog.show()
    }
}
