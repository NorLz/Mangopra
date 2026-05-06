package com.example.copra

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

object PricingFormatter {
    private val localSyncFormatter = SimpleDateFormat("MM/dd/yy hh:mm a", Locale.getDefault())

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
            LocalDate.parse(rawDate).format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            )
        }.getOrElse { rawDate }
    }

    fun formatRecordedAt(rawTimestamp: String?): String {
        if (rawTimestamp.isNullOrBlank()) return "Not provided"
        return runCatching {
            OffsetDateTime.parse(rawTimestamp).format(
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            )
        }.getOrElse { rawTimestamp }
    }

    fun formatSyncedAt(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0L) return "Not synced on this device yet"
        return localSyncFormatter.format(Date(timestamp))
    }

    fun buildBatchPriceCaption(pricing: AnalysisPricing?): String {
        if (pricing?.computedPricePerKg == null) {
            return "Latest pricing has not been downloaded on this device yet."
        }

        return "Weighted average based on the detected Grade I, II, and III counts."
    }
}
