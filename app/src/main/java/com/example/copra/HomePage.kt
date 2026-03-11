package com.example.copra

import android.os.Bundle
import android.widget.ImageButton
import android.app.DatePickerDialog
import android.widget.Toast
import java.util.Calendar
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
import com.example.copra.ResultAdapter.ItemViewHolder
import com.example.copra.ResultAdapter.FooterViewHolder
import android.content.Intent

class HomePage : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ResultAdapter

    private val fullList = mutableListOf<ResultModel>()
    private var currentPage = 0
    private val pageSize = 6

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_page)

        recyclerView = findViewById(R.id.recyclerViewCards)
        val gridLayoutManager = GridLayoutManager(this, 2)

        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (position == adapter.itemCount - 1) {
                    2 // Footer takes full width (both columns)
                } else {
                    1 // Normal items take 1 column
                }
            }
        }

        recyclerView.layoutManager = gridLayoutManager

        // ✅ Generate dummy data for testing pagination
        generateFullData()

        // Show the first page
        showPage(0)

        // Bottom Navigation Buttons
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
        scanBtn.setOnClickListener { setActive(scanBtn) }
        uploadBtn.setOnClickListener { setActive(uploadBtn) }

        homeBtn.post {
            setActive(homeBtn)
        }


        scanBtn.setOnClickListener {
            setActive(scanBtn)
            startActivity(Intent(this, ScanActivity::class.java))
            finish() // closes HomePage so it doesn't stack
        }

        uploadBtn.setOnClickListener {
            setActive(uploadBtn)
            startActivity(Intent(this, UploadActivity::class.java))
            finish() // closes HomePage
        }

        // Calendar Button
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

    // Generate sample data
    private fun generateFullData() {
        fullList.clear()
        for (i in 1..30) { // 30 items → multiple pages
            val grade = when ((1..3).random()) {
                1 -> "I"
                2 -> "II"
                else -> "III"
            }
            val confidence = "${(70..99).random()}%"
            val day = (1..28).random()
            val date = "01/$day/25"

            fullList.add(
                ResultModel(
                    imageRes = R.drawable.sample_image_23,
                    grade = grade,
                    confidence = confidence,
                    date = date
                )
            )
        }
    }

    // Show selected page
    private fun showPage(page: Int) {

        currentPage = page  // 0-based

        val fromIndex = page * pageSize
        val toIndex = minOf(fromIndex + pageSize, fullList.size)
        val pageList = fullList.subList(fromIndex, toIndex)

        val totalPages = (fullList.size + pageSize - 1) / pageSize

        adapter = ResultAdapter(
            list = pageList.toMutableList(),
            currentPage = currentPage,  // 0-based
            totalPages = totalPages,
            onPageClick = { showPage(it) },
            onPrevClick = { if (currentPage > 0) showPage(currentPage - 1) },
            onNextClick = { if (currentPage < totalPages - 1) showPage(currentPage + 1) }
        )

        recyclerView.adapter = adapter

        val gridLayoutManager = recyclerView.layoutManager as GridLayoutManager
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (position == adapter.itemCount - 1) 2 else 1
            }
        }
    }

    fun moveIndicator(button: ImageButton, indicator: View) {
        indicator.animate()
            .x(button.x + button.width / 2 - indicator.width / 2)
            .setDuration(200)
            .start()
    }
}