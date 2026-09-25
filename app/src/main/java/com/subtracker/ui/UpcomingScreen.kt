@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.subtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.subtracker.data.Rates
import com.subtracker.data.Status
import com.subtracker.data.Subscription
import com.subtracker.data.costBetweenNok
import com.subtracker.data.isActive
import com.subtracker.data.missingRate
import com.subtracker.data.monthlyCostNok
import com.subtracker.data.priceNok
import com.subtracker.data.nextCharge
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Shown wherever a NOK total silently leaves out unconverted foreign prices. */
const val UNCONVERTED_WARNING =
    "No exchange rates yet — foreign prices count as 1:1, so totals are understated"

@Composable
fun UpcomingScreen(
    subs: List<Subscription>,
    today: LocalDate,
    onOpen: (Subscription) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = subs.filter { it.isActive }
    val sorted = active.sortedBy { it.nextCharge(today) } +
        subs.filterNot { it.isActive }.sortedBy { it.status.ordinal }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SummaryCard(active, today) }
        if (subs.isEmpty()) {
            item {
                Text(
                    "No subscriptions yet. Tap + to add your first one.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
        items(sorted, key = { it.id }) { SubRow(it, today, onOpen) }
    }
}

@Composable
private fun SummaryCard(active: List<Subscription>, today: LocalDate) {
    val rates = Rates.rates
    val monthly = active.sumOf { it.monthlyCostNok(rates) }
    // One calendar month, not 30 days: a fixed day count is longer than a short
    // month and would charge a monthly subscription twice (Feb 1 + 30d = Mar 2).
    val comingMonth = active.sumOf { s ->
        s.costBetweenNok(today, today.plusMonths(1).minusDays(1), rates)
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Per month", style = MaterialTheme.typography.labelLarge)
            Text(
                kr(monthly),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.padding(4.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Stat("Per year", kr(monthly * 12))
                Stat("Coming month", kr(comingMonth))
                Stat("Active", active.size.toString())
            }
            val updated = Rates.lastUpdated
            val unconverted = active.missingRate(rates)
            if (active.any { it.currency != "NOK" }) {
                Spacer(Modifier.padding(4.dp))
                Text(
                    when {
                        unconverted -> UNCONVERTED_WARNING
                        updated != null -> "Converted with Norges Bank rates from ${updated.format(shortDate)}"
                        else -> "Converted with Norges Bank rates"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (unconverted) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SubRow(s: Subscription, today: LocalDate, onOpen: (Subscription) -> Unit) {
    val next = s.nextCharge(today)
    Card(onClick = { onOpen(s) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(s.color)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    s.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (s.status == Status.CANCELLED) TextDecoration.LineThrough else null,
                )
                val extra = if (s.category.isNotBlank()) " · ${s.category}" else ""
                val converted =
                    if (s.currency != "NOK") " (≈ ${kr(s.priceNok(Rates.rates))})" else ""
                Text(
                    "${money(s.price, s.currency)}$converted ${s.cycle.label.lowercase()}$extra",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (next != null) {
                    val soon = ChronoUnit.DAYS.between(today, next) <= 3
                    Text(
                        daysLabel(next, today),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (soon) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    Text(next.format(shortDate), style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        s.status.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
