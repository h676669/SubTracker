package com.subtracker.sync

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.updateAll
import com.subtracker.data.AppDatabase
import com.subtracker.widget.SubWidget
import org.json.JSONObject

enum class SyncMode(val label: String) {
    LOCAL("This phone only"),
    GOOGLE("Google Drive"),
}

/** What an operation did, for the UI to report precisely. */
sealed interface SyncResult {
    data class Pushed(val subscriptions: Int) : SyncResult
    data class Pulled(val restored: Backup.Restored) : SyncResult
    data object NothingRemote : SyncResult

    /**
     * The remote document changed since this phone last synced, so a push would
     * throw those changes away. The user picks a side; nothing is written yet.
     */
    data class Diverged(val remoteDevice: String, val remoteSubscriptions: Int) : SyncResult
    data class Failed(val message: String) : SyncResult
}

/**
 * Whole-blob last-write-wins sync against [DriveStore].
 *
 * Deliberately asymmetric: pushes happen automatically after a change, pulls only when
 * the user asks. A silent pull would be the one operation able to destroy local data
 * the user can't get back, so it always needs a tap — and a push that would overwrite a
 * newer remote copy stops and reports [SyncResult.Diverged] instead.
 *
 * All the state here is Compose state. Every suspend function below is called from the
 * main dispatcher and [DriveStore] does its own IO internally, so these writes stay on
 * the main thread.
 */
object Sync {

    private const val PREFS = "sync"
    private const val KEY_MODE = "mode"
    private const val KEY_ACCOUNT = "account"
    private const val KEY_LAST_SYNCED = "lastSynced"
    private const val KEY_REMOTE_REVISION = "remoteRevision"

    var mode by mutableStateOf(SyncMode.LOCAL)
        private set
    /** Email of the connected account, shown so the user knows which one holds the data. */
    var account by mutableStateOf<String?>(null)
        private set
    var lastSyncedAt by mutableLongStateOf(0L)
        private set
    var busy by mutableStateOf(false)
        private set

    /** Revision of the remote document as of our last sync; detects changes elsewhere. */
    private var knownRemoteRevision = 0L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        val p = prefs(context)
        mode = runCatching { SyncMode.valueOf(p.getString(KEY_MODE, "LOCAL")!!) }
            .getOrDefault(SyncMode.LOCAL)
        account = p.getString(KEY_ACCOUNT, null)
        lastSyncedAt = p.getLong(KEY_LAST_SYNCED, 0L)
        knownRemoteRevision = p.getLong(KEY_REMOTE_REVISION, 0L)
    }

    /** Switches to local-only. Remote data is left alone unless [forget] is asked for. */
    suspend fun disconnect(context: Context, token: String?, forget: Boolean) {
        if (forget && token != null) runCatching { DriveStore.deleteRemote(token) }
        mode = SyncMode.LOCAL
        account = null
        lastSyncedAt = 0L
        knownRemoteRevision = 0L
        prefs(context).edit()
            .putString(KEY_MODE, SyncMode.LOCAL.name)
            .remove(KEY_ACCOUNT)
            .remove(KEY_LAST_SYNCED)
            .remove(KEY_REMOTE_REVISION)
            .apply()
    }

    /**
     * First connect on this phone. Pulls when Drive already holds a backup and this
     * phone is empty — the new-phone case — and otherwise reports what it found so the
     * user chooses, rather than guessing which side to keep.
     */
    suspend fun connect(context: Context, token: String): SyncResult = guard {
        val email = DriveStore.accountEmail(token)
        val remote = DriveStore.download(token)
        setMode(context, email)

        if (remote == null) return@guard push(context, token, force = true)

        val localCount = AppDatabase.get(context).dao().getAll().size
        if (localCount == 0) return@guard applyRemote(context, remote)

        SyncResult.Diverged(deviceOf(remote), countOf(remote))
    }

    /** Uploads the local dataset. [force] overwrites a remote copy we haven't seen. */
    suspend fun push(context: Context, token: String, force: Boolean = false): SyncResult = guard {
        val remote = DriveStore.download(token)
        if (!force && remote != null && Backup.revisionOf(remote) > knownRemoteRevision) {
            return@guard SyncResult.Diverged(deviceOf(remote), countOf(remote))
        }
        val revision = System.currentTimeMillis()
        val json = Backup.export(context, revision)
        DriveStore.upload(token, json)
        remember(context, revision)
        SyncResult.Pushed(countOf(json))
    }

    /** Replaces local data with the remote copy. Caller confirms with the user first. */
    suspend fun pull(context: Context, token: String): SyncResult = guard {
        val remote = DriveStore.download(token) ?: return@guard SyncResult.NothingRemote
        applyRemote(context, remote)
    }

    /**
     * Pushes after a local change, when connected. Silent by design: it reports nothing
     * and swallows a missing-consent result, because an edit screen is the wrong place
     * to raise a sync problem. The Account screen is where the real state is shown.
     */
    suspend fun pushQuietly(context: Context) {
        if (mode != SyncMode.GOOGLE || busy) return
        val auth = runCatching { DriveStore.authorize(context) }.getOrNull() ?: return
        val token = (auth as? DriveStore.Auth.Granted)?.token ?: return
        runCatching { push(context, token) }
    }

    private suspend fun applyRemote(context: Context, remote: String): SyncResult {
        val restored = Backup.restore(context, remote)
        remember(context, Backup.revisionOf(remote))
        SubWidget().updateAll(context)
        return SyncResult.Pulled(restored)
    }

    private fun setMode(context: Context, email: String?) {
        mode = SyncMode.GOOGLE
        account = email
        prefs(context).edit()
            .putString(KEY_MODE, SyncMode.GOOGLE.name)
            .apply { if (email != null) putString(KEY_ACCOUNT, email) }
            .apply()
    }

    private fun remember(context: Context, revision: Long) {
        knownRemoteRevision = revision
        lastSyncedAt = System.currentTimeMillis()
        prefs(context).edit()
            .putLong(KEY_REMOTE_REVISION, revision)
            .putLong(KEY_LAST_SYNCED, lastSyncedAt)
            .apply()
    }

    /** One busy flag and one place that turns a thrown error into a reportable result. */
    private suspend fun guard(block: suspend () -> SyncResult): SyncResult {
        busy = true
        return try {
            block()
        } catch (e: Exception) {
            SyncResult.Failed(e.message ?: e::class.simpleName ?: "Sync failed")
        } finally {
            busy = false
        }
    }

    private fun deviceOf(json: String): String =
        runCatching { JSONObject(json).optString("device") }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "another phone"

    private fun countOf(json: String): Int =
        runCatching { JSONObject(json).optJSONArray("subscriptions")?.length() ?: 0 }
            .getOrDefault(0)
}
