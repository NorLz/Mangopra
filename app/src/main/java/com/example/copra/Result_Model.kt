package com.example.copra

data class ResultModel(
    val sessionId: Long,
    val imagePath: String,
    val sourceLabel: String,
    val status: String,
    val gradeSummary: String,
    val pricingSummary: String,
    val latencySummary: String,
    val date: String,
    val grade1Count: Int,
    val grade2Count: Int,
    val grade3Count: Int
)
