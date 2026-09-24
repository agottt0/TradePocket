package com.tradelog.data.fx

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * One fetch of exchange rates, quoted against USD (the upstream API's base).
 *
 * [rates] maps ISO-4217 code to "units of that currency per 1 USD", so USD itself is 1.0.
 * Any cross rate falls out of two entries; nothing in the app assumes a particular base.
 */
data class FxSnapshot(
    val rates: Map<String, Double>,
    val fetchedAt: Long,
) {
    /** How many units of [to] one unit of [from] buys. Null when either side is unknown. */
    fun rate(from: String, to: String): Double? {
        val fromPerUsd = rates[from] ?: return null
        val toPerUsd = rates[to] ?: return null
        if (fromPerUsd == 0.0) return null
        return toPerUsd / fromPerUsd
    }
}

/**
 * Exchange rates from open.er-api.com — free, keyless, refreshed daily upstream.
 *
 * The last good snapshot is cached in plain SharedPreferences (rates are public data, unlike
 * the IMAP password) so the app can show something offline, stamped with when it was fetched.
 * Refreshes are throttled: the upstream updates once a day, so hammering it buys nothing.
 */
class FxRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("fx_cache", Context.MODE_PRIVATE)

    private val _snapshot = MutableStateFlow(readCache())
    val snapshot: StateFlow<FxSnapshot?> = _snapshot.asStateFlow()

    /**
     * Fetches fresh rates, or returns the cached snapshot when it is recent enough.
     *
     * [force] skips the throttle for the card's manual refresh button. Returns null only when
     * the fetch fails *and* there is nothing cached; a failure with a cache keeps the stale
     * snapshot, which the UI dates rather than hides.
     */
    suspend fun refresh(force: Boolean = false): FxSnapshot? = withContext(Dispatchers.IO) {
        val cached = _snapshot.value
        if (!force && cached != null &&
            System.currentTimeMillis() - cached.fetchedAt < REFRESH_INTERVAL_MS
        ) {
            return@withContext cached
        }

        val fetched = try {
            fetch()
        } catch (_: Exception) {
            null
        }

        if (fetched != null) {
            writeCache(fetched)
            _snapshot.value = fetched
        }
        fetched ?: cached
    }

    private fun fetch(): FxSnapshot? {
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (json.optString("result") != "success") return null

            val rawRates = json.getJSONObject("rates")
            val rates = buildMap {
                for (code in rawRates.keys()) {
                    put(code, rawRates.getDouble(code))
                }
            }
            if (rates.isEmpty()) null else FxSnapshot(rates, System.currentTimeMillis())
        } finally {
            connection.disconnect()
        }
    }

    private fun readCache(): FxSnapshot? {
        val body = prefs.getString(KEY_RATES, null) ?: return null
        val fetchedAt = prefs.getLong(KEY_FETCHED_AT, 0L)
        if (fetchedAt <= 0L) return null
        return try {
            val json = JSONObject(body)
            val rates = buildMap {
                for (code in json.keys()) {
                    put(code, json.getDouble(code))
                }
            }
            if (rates.isEmpty()) null else FxSnapshot(rates, fetchedAt)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCache(snapshot: FxSnapshot) {
        val json = JSONObject()
        for ((code, rate) in snapshot.rates) json.put(code, rate)
        prefs.edit()
            .putString(KEY_RATES, json.toString())
            .putLong(KEY_FETCHED_AT, snapshot.fetchedAt)
            .apply()
    }

    companion object {
        private const val ENDPOINT = "https://open.er-api.com/v6/latest/USD"
        private const val TIMEOUT_MS = 15_000
        private const val REFRESH_INTERVAL_MS = 60L * 60 * 1000
        private const val KEY_RATES = "rates_usd_base"
        private const val KEY_FETCHED_AT = "fetched_at"

        @Volatile
        private var instance: FxRepository? = null

        fun get(context: Context): FxRepository =
            instance ?: synchronized(this) {
                instance ?: FxRepository(context).also { instance = it }
            }
    }
}
