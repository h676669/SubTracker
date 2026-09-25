package com.subtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.subtracker.data.Rates
import com.subtracker.data.Status
import com.subtracker.data.Subscription
import com.subtracker.data.isActive
import com.subtracker.data.missingRate
import com.subtracker.data.monthlyCostNok

private const val UNCATEGORISED = "Uncategorised"

/** Material "label" glyph; material-icons-core doesn't ship it. */
val TagIcon: ImageVector = ImageVector.Builder(
    name = "Tag",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = addPathNodes(
        "M17.63,5.84C17.27,5.33 16.67,5 16,5L5,5.01C3.9,5.01 3,5.9 3,7v10c0,1.1 0.9,1.99 2,1.99" +
                "L16,19c0.67,0 1.27,-0.33 1.63,-0.84L22,12l-4.37,-6.16z",
    ),
    fill = SolidColor(Color.Black),
).build()

/** One category and what it costs; [monthly] counts active subscriptions only. */
private class Group(val name: String, val subs: List<Subscription>, val monthly: Double) {
    val activeCount get() = subs.count { it.isActive }
}

@Composable
fun CategoriesScreen(
    subs: List<Subscription>,
    onOpen: (Subscription) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val rates = Rates.rates
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }
    var creating by rememberSaveable { mutableStateOf(false) }

    val known = Tags.all(subs)
    val groups = subs
        .groupBy { s -> s.category.trim().ifBlank { UNCATEGORISED }.let { Tags.canonical(it, known) } }
        .map { (name, list) ->
            Group(
                name = name,
                subs = list.sortedWith(compareBy({ !it.isActive }, { -it.monthlyCostNok(rates) })),
                monthly = list.filter { it.isActive }.sumOf { it.monthlyCostNok(rates) },
            )
        }
        .sortedWith(compareBy({ -it.monthly }, { it.name.lowercase() }))
    // Custom tags nothing uses yet, so they can be seen (and removed).
    val unused = Tags.custom.filter { tag -> groups.none { it.name.equals(tag, ignoreCase = true) } }
    val total = groups.sumOf { it.monthly }

    LazyColumn(
        modifier = modifier,
        // Bottom padding keeps the last row clear of the add button.
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Per month, by category", style = MaterialTheme.typography.labelLarge)
                    Text(
                        kr(total),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "${groups.count { it.monthly > 0 }} categories · tap one to see its subscriptions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (subs.missingRate(rates)) {
                        Text(
                            UNCONVERTED_WARNING,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        if (subs.isEmpty()) {
            item {
                Text(
                    "No subscriptions yet. Tap + to add your first one.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
        items(groups, key = { "group:" + it.name.lowercase() }) { group ->
            val key = group.name.lowercase()
            GroupCard(
                group = group,
                share = if (total > 0) (group.monthly / total).toFloat() else 0f,
                expanded = key in expanded,
                onToggle = { expanded = if (key in expanded) expanded - key else expanded + key },
                onOpen = onOpen,
            )
        }
        items(unused, key = { "unused:" + it.lowercase() }) { tag ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(TagIcon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tag, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "No subscriptions yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { Tags.remove(context, tag) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove tag $tag")
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { creating = true }, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("New tag")
            }
        }
    }

    if (creating) {
        NewTagDialog(
            known = known,
            onCreate = { Tags.add(context, it); creating = false },
            onDismiss = { creating = false },
        )
    }
}
@Composable
private fun GroupCard(
    group: Group,
    share: Float,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: (Subscription) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.clickable(onClick = onToggle).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(TagIcon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        group.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val inactive = group.subs.size - group.activeCount
                    Text(
                        buildString {
                            append("${group.activeCount} active")
                            if (inactive > 0) append(" · $inactive paused/cancelled")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${kr(group.monthly)}/mo", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${kr(group.monthly * 12)}/yr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { share }, modifier = Modifier.fillMaxWidth())

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                group.subs.forEach { s ->
                    HorizontalDivider()
                    MemberRow(s, onOpen)
                }
            }
        }
    }
}

@Composable
private fun MemberRow(s: Subscription, onOpen: (Subscription) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(s) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(s.color)))
        Spacer(Modifier.width(10.dp))
        Text(
            s.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (s.status == Status.CANCELLED) TextDecoration.LineThrough else null,
            color = if (s.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (s.isActive) "${kr(s.monthlyCostNok(Rates.rates))}/mo" else s.status.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NewTagDialog(known: List<String>, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val exists = known.any { it.equals(name.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New tag") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name, e.g. Patreon") },
                singleLine = true,
                isError = exists,
                supportingText = { if (exists) Text("That tag already exists") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank() && !exists) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}