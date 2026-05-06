package com.example.copra

object PricingCalculator {

    fun compute(
        grade1Count: Int,
        grade2Count: Int,
        grade3Count: Int,
        latestPricing: LatestPricing?
    ): AnalysisPricing? {
        if (latestPricing == null) return null

        val computation = computeBatchPricePerKg(
            grade1Count = grade1Count,
            grade2Count = grade2Count,
            grade3Count = grade3Count,
            grade1PricePerKg = latestPricing.grade1PricePerKg,
            grade2PricePerKg = latestPricing.grade2PricePerKg,
            grade3PricePerKg = latestPricing.grade3PricePerKg
        )

        return AnalysisPricing(
            grade1PricePerKg = latestPricing.grade1PricePerKg,
            grade2PricePerKg = latestPricing.grade2PricePerKg,
            grade3PricePerKg = latestPricing.grade3PricePerKg,
            computedPricePerKg = computation?.computedPricePerKg,
            unit = latestPricing.unit,
            effectiveDate = latestPricing.effectiveDate,
            sourceLabel = latestPricing.sourceLabel,
            recordedAt = latestPricing.recordedAt,
            syncedAtMillis = latestPricing.syncedAtMillis,
            classifiedCopraCount = computation?.classifiedCopraCount ?: (grade1Count + grade2Count + grade3Count)
        )
    }

    fun computeBatchPricePerKg(
        grade1Count: Int,
        grade2Count: Int,
        grade3Count: Int,
        grade1PricePerKg: Double?,
        grade2PricePerKg: Double?,
        grade3PricePerKg: Double?
    ): PricingComputation? {
        val totalClassifiedCount = grade1Count + grade2Count + grade3Count
        if (totalClassifiedCount <= 0) return null
        if (grade1PricePerKg == null || grade2PricePerKg == null || grade3PricePerKg == null) {
            return null
        }

        val total = totalClassifiedCount.toDouble()
        val grade1Ratio = grade1Count / total
        val grade2Ratio = grade2Count / total
        val grade3Ratio = grade3Count / total

        val computedPricePerKg =
            (grade1PricePerKg * grade1Ratio) +
                (grade2PricePerKg * grade2Ratio) +
                (grade3PricePerKg * grade3Ratio)

        return PricingComputation(
            computedPricePerKg = computedPricePerKg,
            classifiedCopraCount = totalClassifiedCount,
            grade1Ratio = grade1Ratio,
            grade2Ratio = grade2Ratio,
            grade3Ratio = grade3Ratio
        )
    }
}
