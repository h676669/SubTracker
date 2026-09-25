@file:OptIn(ExperimentalMaterial3Api::class)

package com.subtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.subtracker.widget.WidgetSettings
import com.subtracker.widget.WidgetTotal
import com.subtracker.widget.nextPayday
import com.subtracker.widget.periodEnd
import java.time.LocalDate

@Composable
fun ThemePicker(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Appearance", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Appearance.entries.forEach { option ->
                        FilterChip(
                            selected = ThemeState.appearance == option,
                            onClick = { ThemeState.setAppearance(context, option) },
                            label = { Text(option.label) },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("Colour", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(12.dp))

                val options = buildList {
                    if (supportsWallpaperColors) add(ThemeState.WALLPAPER to "Wallpaper")
                    palettes.forEach { add(it.id to it.label) }
                }
                options.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { (id, label) ->
                            Swatch(
                                id = id,
                                label = label,
                                selected = ThemeState.paletteId == id,
                                onClick = { ThemeState.setPalette(context, id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                Spacer(Modifier.height(8.dp))
                WidgetSection()
            }
        },
    )
}

@Composable
private fun Swatch(
    id: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val color = if (id == ThemeState.WALLPAPER) {
        schemeFor(context, ThemeState.WALLPAPER, dark = false).primary
    } else {
        palettes.first { it.id == id }.swatch
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    else Modifier,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, null, tint = Color.White)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun WidgetSection() {
    val context = LocalContext.current
    Text("Widget shows", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(4.dp))
    WidgetTotal.entries.forEach { option ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = WidgetSettings.total == option,
                    onClick = { WidgetSettings.setTotal(context, option) },
                    role = Role.RadioButton,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = WidgetSettings.total == option, onClick = null)
            Spacer(Modifier.width(8.dp))
            Text(option.label, style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (WidgetSettings.total == WidgetTotal.PAYDAY) {
        Spacer(Modifier.height(8.dp))
        Text("Payday (day of month)", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = (WidgetSettings.payday - 3).coerceAtLeast(0))
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items((1..31).toList()) { day ->
                FilterChip(
                    selected = WidgetSettings.payday == day,
                    onClick = { WidgetSettings.setPayday(context, day) },
                    label = { Text(day.toString()) },
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    Text("Upcoming charges listed", style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(4.dp))
    // Scrolls because seven chips are wider than a dialog on a narrow phone.
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items((1..WidgetSettings.MAX_COUNT).toList()) { n ->
            FilterChip(
                selected = WidgetSettings.count == n,
                onClick = { WidgetSettings.setCount(context, n) },
                label = { Text(n.toString()) },
            )
        }
    }
    Text(
        "Resize the widget on the home screen if the list doesn't fit.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (WidgetSettings.total != WidgetTotal.MONTHLY) {
        val today = LocalDate.now()
        val end = periodEnd(WidgetSettings.total, today, WidgetSettings.payday)
        Spacer(Modifier.height(4.dp))
        Text(
            buildString {
                append("Counts charges from today through ${end.format(shortDate)}")
                if (WidgetSettings.total == WidgetTotal.PAYDAY) {
                    append(". Next payday: ${nextPayday(today, WidgetSettings.payday).format(shortDate)}")
                }
                append(".")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}