package com.example.copra

import android.graphics.Bitmap
import android.graphics.RectF

enum class ScanState {
    IDLE,
    SCANNING,
    CAPTURED
}

data class FrameDetection(
    val rectInFrame: RectF,
    val rectInPreview: RectF,
    val label: String,
    val score: Float
)

data class CapturedDetection(
    val crop: Bitmap,
    val sourceRect: RectF,
    val label: String,
    val score: Float
)
