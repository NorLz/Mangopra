package com.example.copra

import android.graphics.Bitmap
import android.graphics.RectF

enum class ScanState {
    IDLE,
    SCANNING,
    CAPTURED
}

enum class ClassificationStatus {
    PENDING,
    READY,
    FAILED
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
    val previewRect: RectF,
    val label: String,
    val score: Float,
    val classificationLabel: String? = null,
    val classificationConfidence: Float? = null,
    val classificationStatus: ClassificationStatus = ClassificationStatus.PENDING,
    val classificationMs: Long? = null
)
