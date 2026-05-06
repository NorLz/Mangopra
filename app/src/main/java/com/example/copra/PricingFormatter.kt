package com.example.copra

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object PricingFormatter {
    fun formatPricePerKg(value: Double?, unit: String?): String {
        if (value == null) return "Unavailable"

        val normalizedUnit = unit?.trim().orEmpty().ifBlank { "PHP/kg" }
        return if (normalizedUnit.equals("PHP/kg", ignoreCase = true)) {
            "PHP ${"%.2f".format(Locale.getDefault(), value)}/kg"
        } else {
            "${"%.2f".format(Locale.getDefault(), value)} $normalizedUnit"
        }
    }

    fun formatBatchPrice(pricing: AnalysisPricing?): String {
        return pricing?.computedPricePerKg?.let { formatPricePerKg(it, pricing.unit) }
            ?: "Pricing unavailable offline"
    }

    fun formatEffectiveDate(rawDate: String?): String {
        if (rawDate.isNullOrBlank()) return "Not provided"
        return runCatching {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            parser.parse(rawDate)?.let { formatter.format(it) } ?: rawDate
        }.getOrElse { rawDate }
    }

    fun formatRecordedAt(rawTimestamp: String?): String {
        if (rawTimestamp.isNullOrBlank()) return "Not provided"
        val parserPatterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX"
        )
        val formatter = SimpleDateFormat("MMM d, yyyy hh:mm a", Locale.getDefault())

        parserPatterns.forEach { pattern ->
            runCatching {
                val parser = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = parser.parse(rawTimestamp)
                if (parsed != null) {
                    return formatter.format(parsed)
                }
            }
        }
        return rawTimestamp
    }

    fun formatSyncedAt(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0L) return "Not synced on this device yet"
        val formatter = SimpleDateFormat("MM/dd/yy hh:mm a", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }

    fun formatRelativeSyncAge(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0L) return "Not synced yet"

        val diffMillis = System.currentTimeMillis() - timestamp
        val totalMinutes = abs(diffMillis) / 60_000L
        return when {
            totalMinutes < 1L -> "Updated just now"
            totalMinutes == 1L -> "Updated 1 minute ago"
            totalMinutes < 60L -> "Updated $totalMinutes minutes ago"
            totalMinutes < 120L -> "Updated 1 hour ago"
            totalMinutes < 24L * 60L -> "Updated ${totalMinutes / 60L} hours ago"
            totalMinutes < 48L * 60L -> "Updated 1 day ago"
            else -> "Updated ${totalMinutes / (24L * 60L)} days ago"
        }
    }

    fun buildBatchPriceCaption(pricing: AnalysisPricing?): String {
        if (pricing?.computedPricePerKg == null) {
            return "Latest pricing has not been downloaded on this device yet."
        }

        return "Weighted using the detected share of Grade I, II, and III copra."
    }

    fun buildProportionSummary(
        grade1Count: Int,
        grade2Count: Int,
        grade3Count: Int
    ): String {
        val total = grade1Count + grade2Count + grade3Count
        if (total <= 0) {
            return "No classified copra available yet."
        }

        fun ratioLabel(count: Int): String {
            val percent = (count.toDouble() / total.toDouble()) * 100.0
            return "${count}/${total} (${String.format(Locale.getDefault(), "%.0f", percent)}%)"
        }

        return "Proportion  I: ${ratioLabel(grade1Count)}  II: ${ratioLabel(grade2Count)}  III: ${ratioLabel(grade3Count)}"
    }
}
