package com.example.copra

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.app.DatePickerDialog
import android.widget.Toast
import java.util.Calendar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private lateinit var pricingRepository: PricingRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        pricingRepository = PricingRepository.getInstance(applicationContext)
        refreshPricingOnLaunch()

        // Handle Edge-to-Edge padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Next button
        val nextButton: Button = findViewById(R.id.button)
        nextButton.setOnClickListener {
            val intent = Intent(this, HomePage::class.java)
            startActivity(intent)
        }
    }

    private fun refreshPricingOnLaunch() {
        if (!pricingRepository.isConfigured()) return

        pricingRepository.refreshLatestPricing(
            onComplete = { },
            onError = { _, _ -> },
            onSkipped = { }
        )
    }
}


// Calendar button
