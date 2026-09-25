package com.subtracker.widget

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.YearMonth

/** What the widget's headline amount shows. */
enum class WidgetTotal(val label: String) {
    MONTHLY("Monthly total"),
    MONTH_END("Left to pay this month"),
    PAYDAY("Left to pay before payday"),
}

/** Widget choice, kept in SharedPreferences so the app and the widget agree. */
object WidgetSettings {
    private const val PREFS = "widget"
    private const val KEY_TOTAL = "total"
    private const val KEY_PAYDAY = "payday"
    private const val KEY_COUNT = "count"
    private const val DEFAULT_PAYDAY = 15
    private const val DEFAULT_COUNT = 6

    /**
     * Glance allows 10 children per Column and the widget spends 3 on the headline,
     * the rate caveat and a spacer, so 7 charge rows is the ceiling. Going over it
     * throws at runtime, not compile time.
     */
    const val MAX_COUNT = 7

    var total by mutableStateOf(WidgetTotal.MONTHLY)
        private set
    /** Day of month (1-31); clamped to the last day in short months. */
    var payday by mutableIntStateOf(DEFAULT_PAYDAY)
        private set
    /** How many upcoming charges the widget lists (1..[MAX_COUNT]). */
    var count by mutableIntStateOf(DEFAULT_COUNT)
        private set

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        total = totalOf(context)
        payday = paydayOf(context)
        count = countOf(context)
    }

    fun setTotal(context: Context, value: WidgetTotal) {
        total = value
        prefs(context).edit().putString(KEY_TOTAL, value.name).apply()
    }

    fun setPayday(context: Context, day: Int) {
        payday = day.coerceIn(1, 31)
        prefs(context).edit().putInt(KEY_PAYDAY, payday).apply()
    }

    fun setCount(context: Context, value: Int) {
        count = value.coerceIn(1, MAX_COUNT)
        prefs(context).edit().putInt(KEY_COUNT, count).apply()
    }

    /** Read straight from disk — used by the widget, which has no Compose state. */
    fun totalOf(context: Context): WidgetTotal =
        runCatching { WidgetTotal.valueOf(prefs(context).getString(KEY_TOTAL, "MONTHLY")!!) }
            .getOrDefault(WidgetTotal.MONTHLY)

    fun paydayOf(context: Context): Int =
        prefs(context).getInt(KEY_PAYDAY, DEFAULT_PAYDAY).coerceIn(1, 31)

    fun countOf(context: Context): Int =
        prefs(context).getInt(KEY_COUNT, DEFAULT_COUNT).coerceIn(1, MAX_COUNT)
}

/** [day] of the given month, or its last day if the month is shorter. */
private fun YearMonth.clampedDay(day: Int): LocalDate = atDay(minOf(day, lengthOfMonth()))

/**
 * First payday strictly after [today]. On payday itself the new period has
 * already started, so the next one is a month away.
 */
fun nextPayday(today: LocalDate, day: Int): LocalDate {
    val month = YearMonth.from(today)
    val thisMonth = month.clampedDay(day)
    return if (thisMonth.isAfter(today)) thisMonth else month.plusMonths(1).clampedDay(day)
}

/**
 * Last day (inclusive) of the period the "left to pay" amount covers, starting today.
 * Charges falling on payday itself are paid from the new salary, so they're excluded.
 */
fun periodEnd(total: WidgetTotal, today: LocalDate, payday: Int): LocalDate = when (total) {
    WidgetTotal.PAYDAY -> nextPayday(today, payday).minusDays(1)
    else -> YearMonth.from(today).atEndOfMonth()
}