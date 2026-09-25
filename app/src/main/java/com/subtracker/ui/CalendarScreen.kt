package com.subtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.subtracker.data.Rates
import com.subtracker.data.Subscription
import com.subtracker.data.priceNok
import com.subtracker.data.chargesBetween
import java.time.LocalDate
import java.time.YearMonth

private val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@Composable
fun CalendarScreen(
    subs: List<Subscription>,
    today: LocalDate,
    onOpen: (Subscription) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Month stored as a single index (year * 12 + month0) so it survives rotation.
    var monthIndex by rememberSaveable { mutableIntStateOf(today.year * 12 + today.monthValue - 1) }
    var selectedDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val ym = YearMonth.of(monthIndex / 12, monthIndex % 12 + 1)
    val first = ym.atDay(1)
    val last = ym.atEndOfMonth()
    // The detail list has to describe a day that's actually in the grid, so a
    // selection left behind by paging months falls back into the visible one.
    val selected = LocalDate.ofEpochDay(selectedDay).takeIf { YearMonth.from(it) == ym }
        ?: today.takeIf { YearMonth.from(it) == ym }
        ?: first

    val byDay: Map<LocalDate, List<Subscription>> = remember(subs, ym) {
        subs.flatMap { s -> s.chargesBetween(first, last).map { it to s } }
            .groupBy({ it.first }, { it.second })
    }
    val rates = Rates.rates
    val monthTotal = byDay.values.sumOf { list -> list.sumOf { it.priceNok(rates) } }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { monthIndex-- }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month")
            }
            Text(
                ym.format(monthTitle),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { monthIndex++ }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
            }
        }
        Text(
            "${kr(monthTotal)} charged this month",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        Row {
            weekdays.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val offset = first.dayOfWeek.value - 1 // Monday-first
        val days = ym.lengthOfMonth()
        val rows = (offset + days + 6) / 7
        for (r in 0 until rows) {
            Row {
                for (c in 0..6) {
                    val dayNum = r * 7 + c - offset + 1
                    if (dayNum in 1..days) {
                        val date = ym.atDay(dayNum)
                        DayCell(
                            date = date,
                            charges = byDay[date].orEmpty(),
                            isToday = date == today,
                            isSelected = date == selected,
                            onClick = { selectedDay = date.toEpochDay() },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        val sel = selected
        val charges = subs.filter { it.chargesBetween(sel, sel).isNotEmpty() }
        Text(sel.format(longDate), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (charges.isEmpty()) {
            Text(
                "No charges this day",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            charges.forEach { s ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpen(s) },
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(s.color)))
                        Spacer(Modifier.width(12.dp))
                        Text(s.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(money(s.price, s.currency), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        Spacer(Modifier.height(80.dp)) // room for the FAB
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    charges: List<Subscription>,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(shape)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .then(if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
            charges.take(3).forEach {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Color(it.color)))
            }
        }
    }
}
