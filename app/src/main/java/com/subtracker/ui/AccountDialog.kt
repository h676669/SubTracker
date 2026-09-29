package com.subtracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.subtracker.sync.DriveStore
import com.subtracker.sync.Sync
import com.subtracker.sync.SyncMode
import com.subtracker.sync.SyncResult
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val syncedAt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")

@Composable
fun AccountDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var diverged by remember { mutableStateOf<SyncResult.Diverged?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }

    // The operation waiting on the Drive consent screen, resumed when it returns.
    var pending by remember { mutableStateOf<(suspend (String) -> SyncResult)?>(null) }

    fun report(result: SyncResult) {
        if (result is SyncResult.Diverged) {
            diverged = result
            return
        }
        message = when (result) {
            is SyncResult.Pushed -> "Backed up ${result.subscriptions} subscriptions."
            is SyncResult.Pulled -> with(result.restored) {
                buildString {
                    append("Restored ")
                    append(subscriptions)
                    append(" subscriptions")
                    if (tags > 0) append(" and $tags tags")
                    if (skipped > 0) append(". $skipped entries were unreadable and were skipped")
                    append(".")
                }
            }
            SyncResult.NothingRemote -> "There is no backup in Drive yet."
            is SyncResult.Failed -> result.message
            is SyncResult.Diverged -> null
        }
    }

    val launcher = rememberLauncherForActivityResult(StartIntentSenderForResult()) { activityResult ->
        val action = pending
        scope.launch {
            val auth = runCatching { DriveStore.authFromIntent(context, activityResult.data) }.getOrNull()
            val token = (auth as? DriveStore.Auth.Granted)?.token
            when {
                token == null -> message = "Drive access was not granted."
                action != null -> report(action(token))
            }
        }
    }

    /** Authorises, then either runs [action] or sends the user to the consent screen. */
    fun start(action: suspend (String) -> SyncResult) {
        pending = action
        message = null
        scope.launch {
            val auth = runCatching { DriveStore.authorize(context) }.getOrElse { error ->
                message = error.message ?: "Could not reach Google Play services."
                return@launch
            }
            when (auth) {
                is DriveStore.Auth.Granted -> report(action(auth.token))
                is DriveStore.Auth.NeedsConsent ->
                    launcher.launch(IntentSenderRequest.Builder(auth.intentSender).build())
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Account & sync") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            Sync.mode.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            when (Sync.mode) {
                                SyncMode.LOCAL ->
                                    "Nothing leaves this phone. Connect Drive to carry your data to a new one."
                                SyncMode.GOOGLE -> Sync.account ?: "Signed in"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (Sync.mode == SyncMode.GOOGLE) {
                            Text(
                                if (Sync.lastSyncedAt > 0L) {
                                    "Last synced " + Instant.ofEpochMilli(Sync.lastSyncedAt)
                                        .atZone(ZoneId.systemDefault()).format(syncedAt)
                                } else {
                                    "Not synced yet"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (Sync.mode == SyncMode.LOCAL) {
                    Button(
                        onClick = { start { Sync.connect(context, it) } },
                        enabled = !Sync.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Connect Google Drive") }
                    Text(
                        "Kept in a private app folder in your Drive: hidden from your files, " +
                            "unreadable by other apps, and only your subscription list.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    Button(
                        onClick = { start { Sync.push(context, it) } },
                        enabled = !Sync.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Back up now") }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { confirmRestore = true },
                        enabled = !Sync.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Restore from Drive") }
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = { confirmDisconnect = true },
                        enabled = !Sync.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Disconnect") }
                    Text(
                        "Changes upload on their own. Restoring is manual because it replaces " +
                            "what is on this phone.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (Sync.busy) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Talking to Drive…", style = MaterialTheme.typography.bodySmall)
                    }
                }

                message?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    )

    diverged?.let { clash ->
        AlertDialog(
            onDismissRequest = { diverged = null },
            title = { Text("Two different copies") },
            text = {
                Text(
                    "Drive holds a backup from ${clash.remoteDevice} with " +
                        "${clash.remoteSubscriptions} subscriptions, saved since this phone last " +
                        "synced. Only one of them can be kept.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    diverged = null
                    start { Sync.pull(context, it) }
                }) { Text("Keep Drive's") }
            },
            dismissButton = {
                TextButton(onClick = {
                    diverged = null
                    start { Sync.push(context, it, force = true) }
                }) { Text("Keep this phone's") }
            },
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Replace this phone's data?") },
            text = {
                Text(
                    "Every subscription on this phone is replaced by the copy in Drive. " +
                        "This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    start { Sync.pull(context, it) }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Disconnect Drive?") },
            text = {
                Text("This phone goes back to local-only. The backup stays in Drive unless you delete it.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    scope.launch { Sync.disconnect(context, token = null, forget = false) }
                }) { Text("Disconnect") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    message = null
                    scope.launch {
                        val auth = runCatching { DriveStore.authorize(context) }.getOrNull()
                        val token = (auth as? DriveStore.Auth.Granted)?.token
                        Sync.disconnect(context, token, forget = true)
                        message = if (token == null) {
                            "Disconnected, but the Drive copy could not be deleted."
                        } else {
                            "Disconnected, and the Drive copy was deleted."
                        }
                    }
                }) { Text("Disconnect & delete") }
            },
        )
    }
}
