package com.example.copra

data class LatestPricing(
    val commodityCode: String,
    val commodityName: String,
    val grade1PricePerKg: Double?,
    val grade2PricePerKg: Double?,
    val grade3PricePerKg: Double?,
    val unit: String,
    val effectiveDate: String?,
    val sourceLabel: String?,
    val notes: String?,
    val recordedAt: String?,
    val syncedAtMillis: Long
)

data class AnalysisPricing(
    val grade1PricePerKg: Double?,
    val grade2PricePerKg: Double?,
    val grade3PricePerKg: Double?,
    val computedPricePerKg: Double?,
    val unit: String?,
    val effectiveDate: String?,
    val sourceLabel: String?,
    val recordedAt: String?,
    val syncedAtMillis: Long?,
    val classifiedCopraCount: Int
)

data class PricingComputation(
    val computedPricePerKg: Double,
    val classifiedCopraCount: Int,
    val grade1Ratio: Double,
    val grade2Ratio: Double,
    val grade3Ratio: Double
)
