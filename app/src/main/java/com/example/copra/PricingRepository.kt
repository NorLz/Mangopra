package com.example.copra

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PricingRepository private constructor(context: Context) {

    data class RefreshResult(
        val pricing: LatestPricing,
        val hasChanges: Boolean
    )

    companion object {
        private const val TAG = "PricingRepository"
        private const val PREFS_NAME = "pricing_cache"
        private const val KEY_PRICING_JSON = "latest_pricing_json"

        @Volatile
        private var INSTANCE: PricingRepository? = null

        fun getInstance(context: Context): PricingRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PricingRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refreshLock = Any()
    @Volatile
    private var refreshInProgress = false

    fun isConfigured(): Boolean {
        return !BuildConfig.PRICING_API_BASE_URL.contains("replace-me", ignoreCase = true)
    }

    fun isRefreshInProgress(): Boolean = refreshInProgress

    fun shouldRefresh(maxAgeMillis: Long): Boolean {
        val cachedPricing = getCachedPricing() ?: return true
        if (maxAgeMillis <= 0L) return true
        return System.currentTimeMillis() - cachedPricing.syncedAtMillis >= maxAgeMillis
    }

    fun getCachedPricing(): LatestPricing? {
        val rawJson = prefs.getString(KEY_PRICING_JSON, null) ?: return null
        return runCatching { parsePricingJson(JSONObject(rawJson)) }
            .onFailure { Log.e(TAG, "Failed to decode cached pricing", it) }
            .getOrNull()
    }

    fun refreshLatestPricing(
        onComplete: (RefreshResult) -> Unit,
        onError: ((Throwable, LatestPricing?) -> Unit)? = null,
        onSkipped: (() -> Unit)? = null
    ) {
        synchronized(refreshLock) {
            if (refreshInProgress) {
                mainHandler.post { onSkipped?.invoke() }
                return
            }
            refreshInProgress = true
        }

        executor.execute {
            val cached = getCachedPricing()
            try {
                if (!isConfigured()) {
                    throw IllegalStateException(
                        "Pricing endpoint is not configured. Set PRICING_API_BASE_URL for this build."
                    )
                }

                val connection = (URL("${BuildConfig.PRICING_API_BASE_URL}pricing/latest")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/json")
                }

                connection.use { http ->
                    val statusCode = http.responseCode
                    val inputStream = if (statusCode in 200..299) {
                        http.inputStream
                    } else {
                        http.errorStream ?: http.inputStream
                    }

                    val responseBody = inputStream.bufferedReader().use { it.readText() }
                    if (statusCode !in 200..299) {
                        throw IllegalStateException("Pricing service returned HTTP $statusCode: $responseBody")
                    }

                    val fetchedPricing = parsePricingJson(JSONObject(responseBody), System.currentTimeMillis())
                    val hasChanges = cached?.hasSamePricingValues(fetchedPricing) != true
                    val pricingToUse = if (!hasChanges) {
                        cached
                    } else {
                        cachePricing(fetchedPricing)
                        fetchedPricing
                    }
                    mainHandler.post {
                        onComplete(
                            RefreshResult(
                                pricing = pricingToUse,
                                hasChanges = hasChanges
                            )
                        )
                    }
                }
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to refresh latest pricing", throwable)
                mainHandler.post { onError?.invoke(throwable, cached) }
            } finally {
                synchronized(refreshLock) {
                    refreshInProgress = false
                }
            }
        }
    }

    private fun cachePricing(pricing: LatestPricing) {
        prefs.edit()
            .putString(KEY_PRICING_JSON, serializePricing(pricing).toString())
            .apply()
    }

    private fun LatestPricing.hasSamePricingValues(other: LatestPricing): Boolean {
        return commodityCode == other.commodityCode &&
            commodityName == other.commodityName &&
            grade1PricePerKg == other.grade1PricePerKg &&
            grade2PricePerKg == other.grade2PricePerKg &&
            grade3PricePerKg == other.grade3PricePerKg &&
            unit == other.unit
    }

    private fun serializePricing(pricing: LatestPricing): JSONObject {
        return JSONObject()
            .put("commodity_code", pricing.commodityCode)
            .put("commodity_name", pricing.commodityName)
            .put("grade_1_price_per_kg", pricing.grade1PricePerKg)
            .put("grade_2_price_per_kg", pricing.grade2PricePerKg)
            .put("grade_3_price_per_kg", pricing.grade3PricePerKg)
            .put("unit", pricing.unit)
            .put("effective_date", pricing.effectiveDate)
            .put("source_label", pricing.sourceLabel)
            .put("notes", pricing.notes)
            .put("recorded_at", pricing.recordedAt)
            .put("synced_at_millis", pricing.syncedAtMillis)
    }

    private fun parsePricingJson(
        payload: JSONObject,
        fallbackSyncedAtMillis: Long = payload.optLong("synced_at_millis", System.currentTimeMillis())
    ): LatestPricing {
        return LatestPricing(
            commodityCode = payload.optString("commodity_code", "copra"),
            commodityName = payload.optString("commodity_name", "Copra"),
            grade1PricePerKg = payload.optNullableDouble("grade_1_price_per_kg"),
            grade2PricePerKg = payload.optNullableDouble("grade_2_price_per_kg"),
            grade3PricePerKg = payload.optNullableDouble("grade_3_price_per_kg"),
            unit = payload.optString("unit", "PHP/kg"),
            effectiveDate = payload.optStringOrNull("effective_date"),
            sourceLabel = payload.optStringOrNull("source_label"),
            notes = payload.optStringOrNull("notes"),
            recordedAt = payload.optStringOrNull("recorded_at"),
            syncedAtMillis = payload.optLong("synced_at_millis", fallbackSyncedAtMillis)
        )
    }

    private fun JSONObject.optNullableDouble(name: String): Double? {
        return if (isNull(name)) null else optDouble(name)
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        if (isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() }
    }

    private inline fun <T : HttpURLConnection, R> T.use(block: (T) -> R): R {
        try {
            return block(this)
        } finally {
            disconnect()
        }
    }
}
