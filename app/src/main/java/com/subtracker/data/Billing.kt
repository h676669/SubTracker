package com.subtracker.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Charge dates are always computed from the anchor date by index
 * (anchor + n weeks / anchor + n*k months), never by stepping from the
 * previous result, so a subscription that starts on the 31st stays on
 * the last day of short months instead of drifting to the 28th.
 */

private val Subscription.anchor: LocalDate get() = LocalDate.ofEpochDay(anchorEpochDay)

val Subscription.isActive: Boolean
    get() = status == Status.ACTIVE || status == Status.TRIAL

val Subscription.monthlyCost: Double
    get() = if (cycle == Cycle.WEEKLY) price * 52.0 / 12.0 else price / cycle.months

fun Subscription.occurrence(n: Long): LocalDate =
    if (cycle == Cycle.WEEKLY) anchor.plusWeeks(n) else anchor.plusMonths(n * cycle.months)

/** Index of the first charge falling on or after [date]. */
private fun Subscription.firstIndexOnOrAfter(date: LocalDate): Long {
    if (!anchor.isBefore(date)) return 0
    var n = if (cycle == Cycle.WEEKLY) {
        ChronoUnit.DAYS.between(anchor, date) / 7
    } else {
        ChronoUnit.MONTHS.between(anchor, date) / cycle.months
    }
    while (occurrence(n).isBefore(date)) n++
    return n
}

/** Next charge on or after [today], or null if paused/cancelled. */
fun Subscription.nextCharge(today: LocalDate): LocalDate? =
    if (!isActive) null else occurrence(firstIndexOnOrAfter(today))

/** All charge dates in [from]..[to] (inclusive). Empty if paused/cancelled. */
fun Subscription.chargesBetween(from: LocalDate, to: LocalDate): List<LocalDate> {
    if (!isActive || to.isBefore(from)) return emptyList()
    val out = mutableListOf<LocalDate>()
    var n = firstIndexOnOrAfter(from)
    while (true) {
        val d = occurrence(n)
        if (d.isAfter(to)) break
        out += d
        n++
    }
    return out
}

/** NOK per one unit of this subscription's currency (1.0 if unknown). */
fun Subscription.rate(rates: Map<String, Double>): Double = rates[currency] ?: 1.0

fun Subscription.priceNok(rates: Map<String, Double>): Double = price * rate(rates)

fun Subscription.monthlyCostNok(rates: Map<String, Double>): Double = monthlyCost * rate(rates)
