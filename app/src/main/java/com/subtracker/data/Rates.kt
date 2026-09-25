package com.subtracker.data

import android.content.Context
import android.content.SharedPreferences
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
 *
 * TIME_PERIOD is the business day the rates are quoted for, kept separate from
 * the day we fetched them: Norges Bank publishes on business days only, so over
 * a weekend a fresh fetch still returns Friday's rates.
 */
object Rates {

    /** Currencies offered in the app. NOK is the base and always 1.0. */
    val currencies = listOf("NOK", "USD", "EUR", "GBP", "SEK", "DKK", "CHF", "PLN", "JPY", "CAD", "AUD")

    private const val PREFS = "rates"
    private const val KEY_DATA = "data"
    private const val KEY_DATE = "date"
    private const val KEY_OBSERVED = "observed"

    var rates by mutableStateOf(mapOf("NOK" to 1.0))
        private set

    /** Business day the current rates are quoted for, not the day they were downloaded. */
    var lastUpdated by mutableStateOf<LocalDate?>(null)
        private set

    /** When the API was last called successfully; drives the once-a-day throttle. */
    private var lastFetched: LocalDate? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun SharedPreferences.date(key: String): LocalDate? =
        getString(key, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun url(): String {
        val quoted = currencies.filter { it != "NOK" }.joinToString("+")
        return "https://data.norges-bank.no/api/data/EXR/B.$quoted.NOK.SP" +
            "?format=csv&lastNObservations=1&locale=en"
    }

    fun load(context: Context) {
        val p = prefs(context)
        rates = decode(p.getString(KEY_DATA, null))
        lastFetched = p.date(KEY_DATE)
        // Installs from before TIME_PERIOD was stored fall back to the fetch date.
        lastUpdated = p.date(KEY_OBSERVED) ?: lastFetched
    }

    /** Rates as stored on disk — for the widget, which has no Compose state. */
    fun ratesOf(context: Context): Map<String, Double> =
        decode(prefs(context).getString(KEY_DATA, null))

    /** Fetches only once per day; returns true if rates are usable. */
    suspend fun refreshIfStale(context: Context): Boolean {
        if (lastFetched == LocalDate.now()) return true
        return refresh(context)
    }

    suspend fun refresh(context: Context): Boolean {
        val csv = withContext(Dispatchers.IO) { runCatching { download(url()) }.getOrNull() }
            ?: return false
        val snapshot = parse(csv)
        if (snapshot.rates.size <= 1) return false
        val fetched = LocalDate.now()
        val observed = snapshot.date ?: fetched
        prefs(context).edit()
            .putString(KEY_DATA, encode(snapshot.rates))
            .putString(KEY_DATE, fetched.toString())
            .putString(KEY_OBSERVED, observed.toString())
            .apply()
        // Compose state must be written from the main thread, whatever dispatcher called us.
        withContext(Dispatchers.Main) {
            rates = snapshot.rates
            lastUpdated = observed
        }
        lastFetched = fetched
        return true
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

    /** Rates plus the business day they are quoted for ([date] is null if unparseable). */
    internal data class Snapshot(val rates: Map<String, Double>, val date: LocalDate?)

    internal fun parse(csv: String): Snapshot {
        val out = mutableMapOf("NOK" to 1.0)
        var observed: LocalDate? = null
        csv.lineSequence().drop(1).forEach { line ->
            val cols = line.split(';')
            if (cols.size < 16) return@forEach
            val code = cols[2].trim()
            val unitMult = cols[10].trim().toIntOrNull() ?: 0
            val value = cols[15].trim().replace(",", "").toDoubleOrNull() ?: return@forEach
            if (code !in currencies) return@forEach
            out[code] = value / 10.0.pow(unitMult)
            // Every currency is quoted for the same day; keep the latest in case they differ.
            val day = runCatching { LocalDate.parse(cols[14].trim()) }.getOrNull()
            val seen = observed
            if (day != null && (seen == null || day.isAfter(seen))) observed = day
        }
        return Snapshot(out, observed)
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
