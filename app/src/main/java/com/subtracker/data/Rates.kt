package com.subtracker.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlin.math.pow

/**
 * Exchange rates from Norges Bank's open data API (no key required).
 *
 * The CSV is semicolon-separated:
 *   FREQ;Frequency;BASE_CUR;Base Currency;QUOTE_CUR;Quote Currency;TENOR;Tenor;
 *   DECIMALS;CALCULATED;UNIT_MULT;Unit Multiplier;COLLECTION;Collection Indicator;
 *   TIME_PERIOD;OBS_VALUE
 *
 * UNIT_MULT matters: SEK, DKK and JPY are quoted per 100 units, so the raw value
 * is divided by 10^UNIT_MULT to get "NOK per 1 unit". Values can also carry
 * thousands separators (e.g. 1,145.56 for CHF), which are stripped.
 */
object Rates {

    /** Currencies offered in the app. NOK is the base and always 1.0. */
    val currencies = listOf("NOK", "USD", "EUR", "GBP", "SEK", "DKK", "CHF", "PLN", "JPY", "CAD", "AUD")

    private const val PREFS = "rates"
    private const val KEY_DATA = "data"
    private const val KEY_DATE = "date"

    var rates by mutableStateOf(mapOf("NOK" to 1.0))
        private set
    var lastUpdated by mutableStateOf<LocalDate?>(null)
        private set

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun url(): String {
        val quoted = currencies.filter { it != "NOK" }.joinToString("+")
        return "https://data.norges-bank.no/api/data/EXR/B.$quoted.NOK.SP" +
            "?format=csv&lastNObservations=1&locale=en"
    }

    fun load(context: Context) {
        val p = prefs(context)
        rates = decode(p.getString(KEY_DATA, null))
        lastUpdated = p.getString(KEY_DATE, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    /** Rates as stored on disk — for the widget, which has no Compose state. */
    fun ratesOf(context: Context): Map<String, Double> =
        decode(prefs(context).getString(KEY_DATA, null))

    /** Fetches only once per day; returns true if rates are usable. */
    suspend fun refreshIfStale(context: Context): Boolean {
        if (lastUpdated == LocalDate.now() && rates.size > 1) return true
        return refresh(context)
    }

    suspend fun refresh(context: Context): Boolean = withContext(Dispatchers.IO) {
        val csv = runCatching { download(url()) }.getOrNull() ?: return@withContext false
        val parsed = parse(csv)
        if (parsed.size <= 1) return@withContext false
        prefs(context).edit()
            .putString(KEY_DATA, encode(parsed))
            .putString(KEY_DATE, LocalDate.now().toString())
            .apply()
        rates = parsed
        lastUpdated = LocalDate.now()
        true
    }

    private fun download(from: String): String {
        val connection = (URL(from).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
        }
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(csv: String): Map<String, Double> {
        val out = mutableMapOf("NOK" to 1.0)
        csv.lineSequence().drop(1).forEach { line ->
            val cols = line.split(';')
            if (cols.size < 16) return@forEach
            val code = cols[2].trim()
            val unitMult = cols[10].trim().toIntOrNull() ?: 0
            val value = cols[15].trim().replace(",", "").toDoubleOrNull() ?: return@forEach
            if (code in currencies) out[code] = value / 10.0.pow(unitMult)
        }
        return out
    }

    private fun encode(map: Map<String, Double>) =
        map.entries.joinToString("|") { "${it.key}=${it.value}" }

    private fun decode(raw: String?): Map<String, Double> {
        val out = mutableMapOf("NOK" to 1.0)
        raw?.split('|')?.forEach { entry ->
            val (code, value) = entry.split('=').takeIf { it.size == 2 } ?: return@forEach
            value.toDoubleOrNull()?.let { out[code] = it }
        }
        return out
    }
}
