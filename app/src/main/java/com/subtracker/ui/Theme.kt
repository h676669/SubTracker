package com.subtracker.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

val supportsWallpaperColors: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

fun schemeFor(context: Context, paletteId: String, dark: Boolean): ColorScheme =
    if (paletteId == ThemeState.WALLPAPER && supportsWallpaperColors) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val palette = palettes.firstOrNull { it.id == paletteId }
            ?: palettes.first { it.id == ThemeState.DEFAULT_PALETTE }
        palette.scheme(dark)
    }

fun isDark(appearance: Appearance, systemDark: Boolean): Boolean = when (appearance) {
    Appearance.SYSTEM -> systemDark
    Appearance.LIGHT -> false
    Appearance.DARK -> true
}

@Composable
fun SubTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isDark(ThemeState.appearance, isSystemInDarkTheme())
    MaterialTheme(colorScheme = schemeFor(context, ThemeState.paletteId, dark), content = content)
}