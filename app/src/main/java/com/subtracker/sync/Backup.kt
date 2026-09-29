package com.subtracker.sync

import android.content.Context
import android.os.Build
import androidx.room.withTransaction
import com.subtracker.data.AppDatabase
import com.subtracker.data.Cycle
import com.subtracker.data.Status
import com.subtracker.data.Subscription
import com.subtracker.ui.Appearance
import com.subtracker.ui.Tags
import com.subtracker.ui.ThemeState
import com.subtracker.ui.palette
import com.subtracker.ui.palettes
import com.subtracker.widget.WidgetSettings
import com.subtracker.widget.WidgetTotal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * The whole dataset as one JSON document.
 *
 * Sync is whole-blob last-write-wins: `revision` (epoch millis) orders two copies of
 * the document and there are no per-record revisions, so a push replaces the remote
 * copy and a pull replaces the local database. Nothing in here merges.
 *
 * A restored document is untrusted input — it comes back over the network and could
 * be truncated or hand-edited in a Drive client — so every record is validated and a
 * bad one is skipped and counted rather than written.
 */
object Backup {

    /** Bumped when the document shape changes. A newer one is refused, not half-applied. */
    const val FORMAT = 1

    fun export(subs: List<Subscription>, tags: List<String>, settings: JSONObject, revision: Long): String =
        JSONObject()
            .put("format", FORMAT)
            .put("revision", revision)
            .put("device", Build.MODEL ?: "")
            .put("subscriptions", JSONArray(subs.map { it.toJson() }))
            .put("tags", JSONArray(tags))
            .put("settings", settings)
            .toString(2)

    /** Reads the current state straight from disk and serialises it. */
    suspend fun export(context: Context, revision: Long = System.currentTimeMillis()): String =
        export(
            subs = AppDatabase.get(context).dao().getAll(),
            tags = Tags.customOf(context),
            settings = settingsJson(context),
            revision = revision,
        )

    /** What a restore actually wrote, so the UI can be specific about it. */
    data class Restored(val subscriptions: Int, val skipped: Int, val tags: Int)

    /** Orders two documents. 0 for anything unreadable, which always loses. */
    fun revisionOf(json: String): Long =
        runCatching { JSONObject(json).optLong("revision", 0L) }.getOrDefault(0L)

    /**
     * Replaces the local database and settings with [json].
     * Throws if the document is unreadable or was written by a newer app version.
     */
    suspend fun restore(context: Context, json: String): Restored {
        val doc = JSONObject(json)
        val format = doc.optInt("format", 0)
        if (format !in 1..FORMAT) {
            error("Backup format $format isn't supported by this version of SubTracker.")
        }

        val array = doc.optJSONArray("subscriptions") ?: JSONArray()
        val subs = mutableListOf<Subscription>()
        var skipped = 0
        for (i in 0 until array.length()) {
            val parsed = array.optJSONObject(i)?.toSubscription()
            if (parsed == null) skipped++ else subs += parsed
        }

        val db = AppDatabase.get(context)
        db.withTransaction {
            db.dao().clear()
            db.dao().upsertAll(subs)
        }

        val tagArray = doc.optJSONArray("tags") ?: JSONArray()
        val tags = List(tagArray.length()) { tagArray.optString(it).trim() }
            .filter { it.isNotBlank() && it.length <= 60 }
        // Tags and settings are Compose state, so they are written on the main thread.
        withContext(Dispatchers.Main) {
            tags.forEach { Tags.add(context, it) }
            doc.optJSONObject("settings")?.let { applySettings(context, it) }
        }
        return Restored(subs.size, skipped, tags.size)
    }

    private fun settingsJson(context: Context) = JSONObject()
        .put("palette", ThemeState.paletteIdOf(context))
        .put("appearance", ThemeState.appearanceOf(context).name)
        .put("widgetTotal", WidgetSettings.totalOf(context).name)
        .put("payday", WidgetSettings.paydayOf(context))
        .put("widgetCount", WidgetSettings.countOf(context))

    private fun applySettings(context: Context, s: JSONObject) {
        // An unknown palette id would render as the default anyway; don't persist it.
        s.optString("palette").takeIf { id -> palettes.any { it.id == id } || id == ThemeState.WALLPAPER }
            ?.let { ThemeState.setPalette(context, it) }
        s.optString("appearance").takeIf { it.isNotBlank() }
            ?.let { ThemeState.setAppearance(context, enumOr(it, Appearance.SYSTEM)) }
        s.optString("widgetTotal").takeIf { it.isNotBlank() }
            ?.let { WidgetSettings.setTotal(context, enumOr(it, WidgetTotal.MONTHLY)) }
        // Both setters clamp, so an out-of-range value can't get through.
        if (s.has("payday")) WidgetSettings.setPayday(context, s.optInt("payday", 15))
        if (s.has("widgetCount")) WidgetSettings.setCount(context, s.optInt("widgetCount", 6))
    }
}

private fun Subscription.toJson() = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("category", category)
    .put("price", price)
    .put("currency", currency)
    .put("cycle", cycle.name)
    .put("anchorEpochDay", anchorEpochDay)
    .put("status", status.name)
    .put("color", color)
    .put("notes", notes)

/** Null if the entry can't be trusted; the caller counts it as skipped. */
private fun JSONObject.toSubscription(): Subscription? {
    val name = optString("name").trim()
    val price = optDouble("price", Double.NaN)
    if (name.isBlank() || !price.isFinite() || price < 0) return null
    val anchor = optLong("anchorEpochDay", Long.MIN_VALUE)
        .takeIf { it != Long.MIN_VALUE && it in MIN_EPOCH_DAY..MAX_EPOCH_DAY }
        ?: LocalDate.now().toEpochDay()
    return Subscription(
        id = optLong("id", 0L).coerceAtLeast(0L),
        name = name.take(200),
        category = optString("category").trim().take(60),
        price = price,
        currency = optString("currency").trim().ifBlank { "NOK" }.take(8).uppercase(),
        cycle = enumOr(optString("cycle"), Cycle.MONTHLY),
        anchorEpochDay = anchor,
        status = enumOr(optString("status"), Status.ACTIVE),
        color = optLong("color", palette.first()),
        notes = optString("notes").take(2000),
    )
}

// A charge date outside these bounds would make LocalDate.ofEpochDay throw in the UI.
private val MIN_EPOCH_DAY = LocalDate.of(1900, 1, 1).toEpochDay()
private val MAX_EPOCH_DAY = LocalDate.of(2200, 1, 1).toEpochDay()

private inline fun <reified T : Enum<T>> enumOr(name: String, fallback: T): T =
    runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
