@file:OptIn(ExperimentalMaterial3Api::class)

package com.subtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.subtracker.data.Cycle
import com.subtracker.data.Rates
import com.subtracker.data.Status
import com.subtracker.data.Subscription
import com.subtracker.data.nextCharge
import com.subtracker.data.priceNok
import java.time.LocalDate

private const val DAY_MS = 86_400_000L

private fun plain(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

/**
 * @param id 0 for a new subscription, otherwise the id being edited (kept even if
 *           [initial] hasn't loaded yet, so saving never creates a duplicate).
 * @param tags category tags to offer; a new one typed here is saved as a tag on save.
 */
@Composable
fun EditScreen(
    id: Long,
    initial: Subscription?,
    tags: List<String>,
    onSave: (Subscription) -> Unit,
    onDelete: (Subscription) -> Unit,
    onClose: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var price by rememberSaveable { mutableStateOf(initial?.price?.let(::plain) ?: "") }
    var currency by rememberSaveable { mutableStateOf(initial?.currency ?: "NOK") }
    var cycle by rememberSaveable { mutableStateOf(initial?.cycle ?: Cycle.MONTHLY) }
    var anchor by rememberSaveable { mutableLongStateOf(initial?.anchorEpochDay ?: LocalDate.now().toEpochDay()) }
    var category by rememberSaveable { mutableStateOf(initial?.category ?: "") }
    var status by rememberSaveable { mutableStateOf(initial?.status ?: Status.ACTIVE) }
    var color by rememberSaveable { mutableLongStateOf(initial?.color ?: palette.first()) }
    var notes by rememberSaveable { mutableStateOf(initial?.notes ?: "") }
    var showPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val priceValue = price.trim().replace(',', '.').toDoubleOrNull()
    val valid = name.isNotBlank() && priceValue != null && priceValue >= 0

    fun build() = Subscription(
        id = id,
        name = name.trim(),
        category = Tags.canonical(category, tags),
        price = priceValue ?: 0.0,
        currency = currency,
        cycle = cycle,
        anchorEpochDay = anchor,
        status = status,
        color = color,
        notes = notes.trim(),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == 0L) "New subscription" else "Edit subscription") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (initial != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = price, onValueChange = { price = it },
                label = { Text(if (cycle == Cycle.WEEKLY) "Price per week" else "Price per charge") },
                singleLine = true,
                isError = price.isNotBlank() && priceValue == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Label("Currency")
            ChipRow(Rates.currencies, currency, { it }) { currency = it }
            if (currency != "NOK" && priceValue != null) {
                val nok = build().priceNok(Rates.rates)
                Text(
                    if (Rates.rates.containsKey(currency)) "≈ ${kr(nok)} per charge"
                    else "No exchange rate yet — connect to the internet to convert",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Label("Billing cycle")
            ChipRow(Cycle.entries, cycle, { it.label }) { cycle = it }

            Label("Charge date")
            Text(
                "Any date you've been (or will be) charged. The next charge rolls forward automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { showPicker = true }) {
                Icon(Icons.Filled.DateRange, null)
                Spacer(Modifier.width(8.dp))
                Text(LocalDate.ofEpochDay(anchor).format(longDate))
            }
            val preview = build().nextCharge(LocalDate.now())
            if (preview != null) {
                Text(
                    "Next charge: ${preview.format(longDate)} (${daysLabel(preview, LocalDate.now())})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            OutlinedTextField(
                value = category, onValueChange = { category = it },
                label = { Text("Category") }, singleLine = true,
                supportingText = {
                    if (category.isNotBlank() && tags.none { it.equals(category.trim(), ignoreCase = true) }) {
                        Text("New tag — it'll be saved for next time")
                    }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            ChipRow(tags, Tags.canonical(category, tags), { it }) {
                category = if (category.trim().equals(it, ignoreCase = true)) "" else it
            }

            Label("Status")
            ChipRow(Status.entries, status, { it.label }) { status = it }

            Label("Colour")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                palette.forEach { c ->
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .then(
                                if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier,
                            )
                            .clickable { color = c },
                    )
                }
            }

            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notes") }, minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = { onSave(build()) }, enabled = valid, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = anchor * DAY_MS)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { anchor = Math.floorDiv(it, DAY_MS) }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${initial.name}?") },
            text = { Text("This removes it from the tracker. To keep it for your savings history, set the status to Cancelled instead.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(initial) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}
