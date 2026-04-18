package com.example.copra

import android.graphics.RectF

data class AnalysisHistorySession(
    val id: Long,
    val createdAt: Long,
    val sourceType: String,
    val fullImagePath: String,
    val grade1Count: Int,
    val grade2Count: Int,
    val grade3Count: Int,
    val detectionCount: Int,
    val items: List<AnalysisHistoryItem> = emptyList()
)

data class AnalysisHistoryItem(
    val id: Long,
    val sessionId: Long,
    val cropImagePath: String,
    val sourceRect: RectF,
    val classificationLabel: String?,
    val classificationConfidence: Float?,
    val classificationStatus: ClassificationStatus,
    val classificationMs: Long?,
    val displayOrder: Int
)

object AnalysisSourceType {
    const val SCAN = "SCAN"
    const val UPLOAD = "UPLOAD"
}
