package com.subtracker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.padding
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.subtracker.widget.SubWidget
import com.subtracker.widget.WidgetSettings
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val vm: SubViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeState.load(this)
        WidgetSettings.load(this)
        Tags.load(this)
        setContent { SubTheme { App(vm) } }
    }

    override fun onResume() {
        super.onResume()
        // Keeps the widget's "in N days" labels fresh whenever the app is opened.
        lifecycleScope.launch { SubWidget().updateAll(applicationContext) }
    }
}

/** Editor state: null = closed, 0 = new subscription, otherwise the id being edited. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(vm: SubViewModel) {
    val subs by vm.subs.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editorId by rememberSaveable { mutableStateOf<Long?>(null) }
    val today = LocalDate.now()

    val openId = editorId
    if (openId != null) {
        BackHandler { editorId = null }
        EditScreen(
            id = openId,
            initial = subs.find { it.id == openId },
            tags = Tags.all(subs),
            onSave = { vm.save(it); editorId = null },
            onDelete = { vm.delete(it); editorId = null },
            onClose = { editorId = null },
        )
    } else {
        MainScaffold(subs, today, tab, onTab = { tab = it }, onOpen = { editorId = it })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    subs: List<com.subtracker.data.Subscription>,
    today: LocalDate,
    tab: Int,
    onTab: (Int) -> Unit,
    onOpen: (Long) -> Unit,
) {
    val context = LocalContext.current
    var showTheme by rememberSaveable { mutableStateOf(false) }

    // Keep the widget in step with the chosen theme and widget settings.
    LaunchedEffect(ThemeState.paletteId, ThemeState.appearance, WidgetSettings.total, WidgetSettings.payday) {
        SubWidget().updateAll(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (tab) {
                            0 -> "Subscriptions"
                            1 -> "Calendar"
                            else -> "Categories"
                        },
                    )
                },
                actions = {
                    IconButton(onClick = { showTheme = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpen(0L) }) {
                Icon(Icons.Filled.Add, contentDescription = "Add subscription")
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { onTab(0) },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    label = { Text("Upcoming") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { onTab(1) },
                    icon = { Icon(Icons.Filled.DateRange, null) },
                    label = { Text("Calendar") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { onTab(2) },
                    icon = { Icon(TagIcon, null) },
                    label = { Text("Categories") },
                )
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            0 -> UpcomingScreen(subs, today, onOpen = { onOpen(it.id) }, modifier = modifier)
            1 -> CalendarScreen(subs, today, onOpen = { onOpen(it.id) }, modifier = modifier)
            else -> CategoriesScreen(subs, onOpen = { onOpen(it.id) }, modifier = modifier)
        }
    }

    if (showTheme) ThemePicker(onDismiss = { showTheme = false })
}