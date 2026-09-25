package com.subtracker.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.subtracker.data.Subscription
import org.json.JSONArray

/**
 * Category tags: the built-in suggestions plus any the user has created.
 * Custom tags are kept in SharedPreferences so they survive even when no
 * subscription uses them yet. Tags compare case-insensitively.
 */
object Tags {
    private const val PREFS = "tags"
    private const val KEY_CUSTOM = "custom"

    val defaults = listOf(
        "Streaming", "Music", "Gaming", "Software", "Cloud storage",
        "Lotteri/spill", "News", "Fitness", "Phone & internet", "Insurance", "Other",
    )

    var custom by mutableStateOf(emptyList<String>())
        private set

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        val raw = prefs(context).getString(KEY_CUSTOM, null) ?: return
        custom = runCatching {
            val array = JSONArray(raw)
            List(array.length()) { array.getString(it) }
        }.getOrDefault(emptyList())
    }

    private fun persist(context: Context) {
        prefs(context).edit().putString(KEY_CUSTOM, JSONArray(custom).toString()).apply()
    }

    /** Every tag to offer: built-ins, custom tags, then any category only found on a subscription. */
    fun all(subs: List<Subscription>): List<String> =
        (defaults + custom + subs.map { it.category.trim() })
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

    /** The existing spelling of [name] if it matches a known tag, else [name] trimmed. */
    fun canonical(name: String, known: List<String>): String {
        val trimmed = name.trim()
        return known.firstOrNull { it.equals(trimmed, ignoreCase = true) } ?: trimmed
    }

    fun isCustom(name: String): Boolean = custom.any { it.equals(name, ignoreCase = true) }

    /** Saves [name] as a custom tag unless it's blank or already known. */
    fun add(context: Context, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        if ((defaults + custom).any { it.equals(trimmed, ignoreCase = true) }) return
        custom = custom + trimmed
        persist(context)
    }

    fun remove(context: Context, name: String) {
        custom = custom.filterNot { it.equals(name, ignoreCase = true) }
        persist(context)
    }
}