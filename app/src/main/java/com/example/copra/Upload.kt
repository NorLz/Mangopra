package com.example.copra

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.animation.ValueAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class UploadActivity : AppCompatActivity() {

    private var selectedImageUri: Uri? = null

    // Modern Image Picker
    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                selectedImageUri = it
                showSelectedFile(it)
                simulateUpload()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload)

        val btnBack = findViewById<MaterialButton>(R.id.btnBack)

        btnBack.setOnClickListener {
            val intent = Intent(this, HomePage::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        // Upload UI
        val selectBtn = findViewById<MaterialButton>(R.id.btnSelectImage)
        val removeBtn = findViewById<MaterialButton>(R.id.btnRemove)
        val analyzeButton = findViewById<MaterialButton>(R.id.btnAnalyzeQuality)

/*        // Bounding Overlay
        val overlay = findViewById<BoundingBoxView>(R.id.boundingOverlay)
        val imgPreview = findViewById<ImageView>(R.id.imgPreview)*/

        // Bottom Control Buttons
        val btnGallery = findViewById<MaterialButton>(R.id.btnGallery2)
        val btnScan = findViewById<MaterialButton>(R.id.btnScan2)
        val btnConfirm = findViewById<MaterialButton>(R.id.btnConfirm2)

        setActiveControl(btnGallery)

        // Select Image
        selectBtn.setOnClickListener {
            imagePicker.launch("image/*")
        }

        // Remove Image
        removeBtn.setOnClickListener {
            resetUploadUI()
        }

        // Analyze Button Click

        var isAnalyzed = false

        analyzeButton.setOnClickListener {

            val loadingContainer = findViewById<MaterialCardView>(R.id.loadingContainer)
            val overlay = findViewById<BoundingBoxView>(R.id.boundingOverlay)

            if (!isAnalyzed) {

                // FIRST CLICK → ANALYZE
                analyzeButton.visibility = View.GONE
                loadingContainer.visibility = View.VISIBLE

                Handler(Looper.getMainLooper()).postDelayed({

                    loadingContainer.visibility = View.GONE
                    overlay.visibility = View.VISIBLE

                    overlay.post {
                        overlay.generateFakeResults(overlay.width, overlay.height)
                    }

                    analyzeButton.visibility = View.VISIBLE
                    analyzeButton.text = "View Results"

                    isAnalyzed = true

                }, 2000)

            } else {
                // SECOND CLICK → SHOW RESULTS
                showResultsModal()
            }
        }

        // Bottom Navigation
        btnGallery.setOnClickListener {
            setActiveControl(btnGallery)
        }

        btnScan.setOnClickListener {
            setActiveControl(btnScan)
            startActivity(Intent(this, ScanActivity::class.java))
        }

        btnConfirm.setOnClickListener {
            setActiveControl(btnConfirm)
        }

    }

    private fun showResultsModal() {

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.results_modal, null)
        dialog.setContentView(view)

        val btnClose = view.findViewById<ImageView>(R.id.btnCloseResults)
        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // ===== SUMMARY COUNTS =====
        val grade1 = view.findViewById<TextView>(R.id.tvGrade1Total)
        val grade2 = view.findViewById<TextView>(R.id.tvGrade2Total)
        val grade3 = view.findViewById<TextView>(R.id.tvGrade3Total)

        // ===== RECYCLER VIEW =====
        val recycler = view.findViewById<RecyclerView>(R.id.rvDetectedImages)
        recycler.layoutManager = GridLayoutManager(this, 3)

        // 🔥 Simulated Generated Results
        val allDetected = listOf(
            "Grade I", "Grade II", "Grade III",
            "Grade I", "Grade II", "Grade III",
            "Grade I", "Grade II",
            "Grade III", "Grade I",
            "Grade II", "Grade III"
        )

        // ===== DYNAMIC GRADE COUNT =====
        grade1.text = allDetected.count { it == "Grade I" }.toString()
        grade2.text = allDetected.count { it == "Grade II" }.toString()
        grade3.text = allDetected.count { it == "Grade III" }.toString()

        // ===== PAGINATION VARIABLES =====
        val pageSize = 6
        var currentPage = 0
        val totalPages = if (allDetected.isEmpty()) {
            1
        } else {
            (allDetected.size + pageSize - 1) / pageSize
        }

        // ===== ADAPTER =====
        val adapter = DetectedAdapter(emptyList())
        recycler.adapter = adapter

        // ===== PAGINATION VIEWS =====
        val btnNext = view.findViewById<MaterialButton>(R.id.btnNext)
        val btnPrev = view.findViewById<MaterialButton>(R.id.btnPrev)
        val etPageNumber = view.findViewById<TextView>(R.id.etPageNumber)
        val tvTotalPages = view.findViewById<TextView>(R.id.tvTotalPages)

        fun updatePage() {

            val start = currentPage * pageSize
            val end = minOf(start + pageSize, allDetected.size)

            val pageItems = if (start < end) {
                allDetected.subList(start, end)
            } else {
                emptyList()
            }

            adapter.updateData(pageItems)

            // Update UI
            etPageNumber.text = (currentPage + 1).toString()
            tvTotalPages.text = "of $totalPages"

            btnPrev.isEnabled = currentPage > 0
            btnNext.isEnabled = currentPage < totalPages - 1
        }

        // ===== BUTTON ACTIONS =====
        btnNext.setOnClickListener {
            if (currentPage < totalPages - 1) {
                currentPage++
                updatePage()
            }
        }

        btnPrev.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                updatePage()
            }
        }

        // First Load
        updatePage()

        dialog.show()
    }
    // Change Active Bottom Button Icon
    private fun setActiveControl(activeButton: MaterialButton) {

        val btnGallery = findViewById<MaterialButton>(R.id.btnGallery2)
        val btnScan = findViewById<MaterialButton>(R.id.btnScan2)
        val btnConfirm = findViewById<MaterialButton>(R.id.btnConfirm2)

        btnGallery.setIconResource(R.drawable.upload__1_)
        btnScan.setIconResource(R.drawable.scan)
        btnConfirm.setIconResource(R.drawable.check)

        when (activeButton.id) {
            R.id.btnGallery2 ->
                btnGallery.setIconResource(R.drawable.upload)

            R.id.btnScan2 ->
                btnScan.setIconResource(R.drawable.scan__1_)

            R.id.btnConfirm2 ->
                btnConfirm.setIconResource(R.drawable.check)
        }
    }

    // Show Selected File + Preview
    private fun showSelectedFile(uri: Uri) {

        val txtFileName = findViewById<TextView>(R.id.txtFileName)
        val uploadCard = findViewById<MaterialCardView>(R.id.uploadProgressCard)
        val imgPreview = findViewById<ImageView>(R.id.imgPreview)
        val uploadContent = findViewById<View>(R.id.uploadContent)
        val overlay = findViewById<BoundingBoxView>(R.id.boundingOverlay)

        val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "selected_image.jpg"

        txtFileName.text = fileName
        uploadCard.visibility = View.VISIBLE

        imgPreview.setImageURI(uri)
        imgPreview.visibility = View.VISIBLE

        uploadContent.visibility = View.GONE
        overlay.visibility = View.GONE
    }

    // Simulated Upload Progress
    private fun simulateUpload() {

        val progressBar = findViewById<ProgressBar>(R.id.uploadProgressBar)
        val statusText = findViewById<TextView>(R.id.txtUploadStatus)
        val checkIcon = findViewById<ImageView>(R.id.imgCheck)
        val analyzeButton = findViewById<MaterialButton>(R.id.btnAnalyzeQuality)

        analyzeButton.visibility = View.GONE
        checkIcon.visibility = View.GONE

        progressBar.progress = 0
        statusText.text = "Uploading..."
        statusText.setTextColor(Color.parseColor("#666666"))

        val handler = Handler(Looper.getMainLooper())
        var progress = 0

        val runnable = object : Runnable {
            override fun run() {
                if (progress < 100) {
                    progress += 5
                    progressBar.progress = progress
                    statusText.text = "$progress% - Uploading..."
                    handler.postDelayed(this, 100)
                } else {
                    statusText.text = "Upload Complete"
                    statusText.setTextColor(Color.parseColor("#4CAF50"))
                    checkIcon.visibility = View.VISIBLE
                    analyzeButton.visibility = View.VISIBLE
                }
            }
        }

        handler.post(runnable)
    }

    // Reset Upload UI
    private fun resetUploadUI() {

        val uploadCard = findViewById<MaterialCardView>(R.id.uploadProgressCard)
        val analyzeButton = findViewById<MaterialButton>(R.id.btnAnalyzeQuality)
        val imgPreview = findViewById<ImageView>(R.id.imgPreview)
        val uploadContent = findViewById<View>(R.id.uploadContent)
        val overlay = findViewById<BoundingBoxView>(R.id.boundingOverlay)

        selectedImageUri = null
        uploadCard.visibility = View.GONE
        analyzeButton.visibility = View.GONE

        imgPreview.setImageDrawable(null)
        imgPreview.visibility = View.GONE
        uploadContent.visibility = View.VISIBLE
        overlay.visibility = View.GONE
    }
}