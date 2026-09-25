package com.subtracker.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val norwegian: Locale = Locale.forLanguageTag("nb-NO")

fun kr(value: Double): String =
    if (value % 1.0 == 0.0) String.format(norwegian, "%,.0f kr", value)
    else String.format(norwegian, "%,.2f kr", value)

/** Formats an amount in its own currency. */
fun money(value: Double, currency: String): String {
    val n = if (value % 1.0 == 0.0) String.format(norwegian, "%,.0f", value)
    else String.format(norwegian, "%,.2f", value)
    return when (currency) {
        "NOK" -> "$n kr"
        "USD" -> "$$n"
        "EUR" -> "€$n"
        "GBP" -> "£$n"
        "JPY" -> "¥$n"
        else -> "$n $currency"
    }
}

val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
val longDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH)
val monthTitle: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

fun daysLabel(date: LocalDate, today: LocalDate): String {
    val n = ChronoUnit.DAYS.between(today, date)
    return when (n) {
        0L -> "Today"
        1L -> "Tomorrow"
        else -> "in $n days"
    }
}

val palette: List<Long> = listOf(
    0xFF1DB954, // green
    0xFFE50914, // red
    0xFF4285F4, // blue
    0xFFFF9800, // orange
    0xFF9C27B0, // purple
    0xFF00ACC1, // teal
    0xFFFFC107, // amber
    0xFF795548, // brown
)

val categorySuggestions = listOf(
    "Streaming", "Music", "Gaming", "Software", "Cloud storage",
    "Lotteri/spill", "News", "Fitness", "Phone & internet", "Insurance", "Other",
)
