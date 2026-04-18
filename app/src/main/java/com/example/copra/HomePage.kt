package com.example.copra

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.util.Calendar

class HomePage : AppCompatActivity() {

    companion object {
        private const val TAG = "HomePage"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var clearAllButton: MaterialButton
    private lateinit var adapter: ResultAdapter
    private lateinit var historyRepository: AnalysisHistoryRepository

    private val fullList = mutableListOf<ResultModel>()
    private var currentPage = 0
    private val pageSize = 6

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_page)

        recyclerView = findViewById(R.id.recyclerViewCards)
        emptyStateText = findViewById(R.id.tvHistoryEmptyState)
        clearAllButton = findViewById(R.id.btnClearAllHistory)
        historyRepository = AnalysisHistoryRepository.getInstance(applicationContext)

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

        val calendarButton: ImageButton = findViewById(R.id.imageButton)
        calendarButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    Toast.makeText(
                        this,
                        "Selected: ${month + 1}/$day/$year",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadHistorySessions()
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
}
