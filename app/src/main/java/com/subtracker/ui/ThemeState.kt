package com.subtracker.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Appearance(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** Theme choice, kept in SharedPreferences so the app and the widget agree. */
object ThemeState {
    private const val PREFS = "theme"
    private const val KEY_PALETTE = "palette"
    private const val KEY_APPEARANCE = "appearance"

    /** "wallpaper" = Material You colours from the wallpaper (Android 12+). */
    var paletteId by mutableStateOf(DEFAULT_PALETTE)
        private set
    var appearance by mutableStateOf(Appearance.SYSTEM)
        private set

    const val DEFAULT_PALETTE = "blue"
    const val WALLPAPER = "wallpaper"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        val p = prefs(context)
        paletteId = p.getString(KEY_PALETTE, DEFAULT_PALETTE) ?: DEFAULT_PALETTE
        appearance = runCatching { Appearance.valueOf(p.getString(KEY_APPEARANCE, "SYSTEM")!!) }
            .getOrDefault(Appearance.SYSTEM)
    }

    fun setPalette(context: Context, id: String) {
        paletteId = id
        prefs(context).edit().putString(KEY_PALETTE, id).apply()
    }

    fun setAppearance(context: Context, value: Appearance) {
        appearance = value
        prefs(context).edit().putString(KEY_APPEARANCE, value.name).apply()
    }

    /** Read straight from disk — used by the widget, which has no Compose state. */
    fun paletteIdOf(context: Context): String =
        prefs(context).getString(KEY_PALETTE, DEFAULT_PALETTE) ?: DEFAULT_PALETTE

    fun appearanceOf(context: Context): Appearance =
        runCatching { Appearance.valueOf(prefs(context).getString(KEY_APPEARANCE, "SYSTEM")!!) }
            .getOrDefault(Appearance.SYSTEM)
}
