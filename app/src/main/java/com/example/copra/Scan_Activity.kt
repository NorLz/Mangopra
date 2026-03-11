package com.example.copra

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.yourpackage.OverlayView

class ScanActivity : AppCompatActivity() {

    private lateinit var cameraPreview: PreviewView
    private lateinit var overlay: OverlayView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        cameraPreview = findViewById(R.id.cameraPreview)
        overlay = findViewById(R.id.overlayView)

        // --------------------------------
        // Camera Permission
        // --------------------------------

        if (checkSelfPermission(Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 100)
        } else {
            startCamera()
        }

        // --------------------------------
        // Upload Button
        // --------------------------------

        val btnUpload = findViewById<MaterialButton>(R.id.btnGallery)

        btnUpload.setOnClickListener {
            val intent = Intent(this, UploadActivity::class.java)
            startActivity(intent)
        }

        // --------------------------------
        // Confirm Button (Bottom Sheet)
        // --------------------------------

        val btnConfirm = findViewById<MaterialButton>(R.id.btnConfirm)

        btnConfirm.setOnClickListener {

            val dialog = BottomSheetDialog(this, R.style.BottomSheetStyle)
            val view = layoutInflater.inflate(R.layout.bottom_sheet_results, null)
            dialog.setContentView(view)

            val recycler = view.findViewById<RecyclerView>(R.id.recyclerImages)
            recycler.layoutManager = GridLayoutManager(this, 3)

            val allImages = listOf(
                R.drawable.sample_image_20,
                R.drawable.sample_image_23,
                R.drawable.sample_image_23,
                R.drawable.sample_image_20,
                R.drawable.sample_image_23,
                R.drawable.sample_image_20,
                R.drawable.sample_image_23,
                R.drawable.sample_image_23,
                R.drawable.sample_image_20
            )

            val itemsPerPage = 6
            var currentPage = 1
            val totalPages = (allImages.size + itemsPerPage - 1) / itemsPerPage

            recycler.adapter = ImageAdapter(emptyList())

            fun loadPage(page: Int) {
                val start = (page - 1) * itemsPerPage
                val end = minOf(start + itemsPerPage, allImages.size)

                val pageList = allImages.subList(start, end)
                recycler.adapter = ImageAdapter(pageList)

                view.findViewById<TextInputEditText>(R.id.etPageNumber)
                    .setText(page.toString())

                view.findViewById<TextView>(R.id.tvTotalPages)
                    .text = "of $totalPages"
            }

            loadPage(currentPage)

            val btnPrev = view.findViewById<MaterialButton>(R.id.btnPrev)
            val btnNext = view.findViewById<MaterialButton>(R.id.btnNext)

            btnPrev.setOnClickListener {
                if (currentPage > 1) {
                    currentPage--
                    loadPage(currentPage)
                }
            }

            btnNext.setOnClickListener {
                if (currentPage < totalPages) {
                    currentPage++
                    loadPage(currentPage)
                }
            }

            view.findViewById<ImageView>(R.id.btnClose)
                .setOnClickListener { dialog.dismiss() }

            dialog.show()

            val bottomSheet =
                dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.5).toInt()
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }

        // --------------------------------
        // Scan Button Animation
        // --------------------------------

        val btnScan = findViewById<MaterialButton>(R.id.btnScan)
        var isActive = false

        btnScan.setOnClickListener {

            isActive = !isActive

            if (isActive) {

                btnScan.setIconResource(R.drawable.scan__1_)

                btnScan.animate()
                    .scaleX(1.4f)
                    .scaleY(1.4f)
                    .setDuration(200)
                    .start()

                // 🔥 SHOW DETECTIONS
                overlay.detections = listOf(
                    OverlayView.Detection(
                        RectF(100f, 200f, 400f, 500f),
                        "Grade I",
                        Color.GREEN
                    ),
                    OverlayView.Detection(
                        RectF(500f, 300f, 800f, 600f),
                        "Grade II",
                        Color.YELLOW
                    ),
                    OverlayView.Detection(
                        RectF(300f, 700f, 600f, 1000f),
                        "Grade III",
                        Color.RED
                    )
                )

                overlay.invalidate()

            } else {

                btnScan.setIconResource(R.drawable.scan)

                btnScan.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .start()

                // ❌ HIDE DETECTIONS
                overlay.detections = emptyList()
                overlay.invalidate()
            }
        }

        // --------------------------------
        // Back Button
        // --------------------------------

        val btnBack = findViewById<MaterialButton>(R.id.btnBack)

        btnBack.setOnClickListener {
            val intent = Intent(this, HomePage::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        // --------------------------------
        // Example Detection Boxes
        // --------------------------------
    }

    // --------------------------------
    // CameraX Start
    // --------------------------------

    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()

            preview.setSurfaceProvider(cameraPreview.surfaceProvider)

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // --------------------------------
    // Permission Result
    // --------------------------------

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        }
    }
}