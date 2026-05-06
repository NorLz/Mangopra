package com.example.copra

data class ClassificationLatencySummary(
    val averageMs: Double,
    val minMs: Long,
    val maxMs: Long,
    val sampleCount: Int
)

object ClassificationLatency {
    fun fromCapturedItems(items: List<CapturedDetection>): ClassificationLatencySummary? {
        val timings = items.mapNotNull {
            if (it.classificationStatus == ClassificationStatus.READY) it.classificationMs else null
        }
        return fromTimings(timings)
    }

    fun fromHistoryItems(items: List<AnalysisHistoryItem>): ClassificationLatencySummary? {
        val timings = items.mapNotNull {
            if (it.classificationStatus == ClassificationStatus.READY) it.classificationMs else null
        }
        return fromTimings(timings)
    }

    fun fromPersisted(
        averageMs: Double?,
        minMs: Long?,
        maxMs: Long?,
        sampleCount: Int?
    ): ClassificationLatencySummary? {
        if (averageMs == null || minMs == null || maxMs == null || sampleCount == null || sampleCount <= 0) {
            return null
        }
        return ClassificationLatencySummary(
            averageMs = averageMs,
            minMs = minMs,
            maxMs = maxMs,
            sampleCount = sampleCount
        )
    }

    private fun fromTimings(timings: List<Long>): ClassificationLatencySummary? {
        if (timings.isEmpty()) return null
        return ClassificationLatencySummary(
            averageMs = timings.average(),
            minMs = timings.minOrNull() ?: 0L,
            maxMs = timings.maxOrNull() ?: 0L,
            sampleCount = timings.size
        )
    }
}

object ClassificationLatencyFormatter {
    fun formatHeadline(summary: ClassificationLatencySummary?): String {
        if (summary == null) return "Classifier latency unavailable"
        return "Classifier latency: ${formatMs(summary.averageMs)} ms/copra avg"
    }

    fun formatDetail(summary: ClassificationLatencySummary?): String {
        if (summary == null) {
            return "Classifier latency could not be measured for this analysis."
        }
        return "Average ${formatMs(summary.averageMs)} ms per classified copra"
    }

    fun formatMeta(summary: ClassificationLatencySummary?): String {
        if (summary == null) {
            return "No successful classification timings were recorded."
        }
        return "Based on ${summary.sampleCount} classified copra\nRange: ${summary.minMs}-${summary.maxMs} ms"
    }

    private fun formatMs(value: Double): String {
        return if (value >= 100) {
            String.format(java.util.Locale.getDefault(), "%.0f", value)
        } else {
            String.format(java.util.Locale.getDefault(), "%.1f", value)
        }
    }
}
