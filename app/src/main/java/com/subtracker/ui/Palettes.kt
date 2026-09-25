package com.subtracker.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Hand-picked Material 3 tonal palettes. Each palette supplies the key tones
 * (10/20/30/40/80/90 for the primary family plus a tertiary accent); the rest of
 * the colour roles fall back to the Material defaults.
 */
data class Palette(
    val id: String,
    val label: String,
    val p10: Long, val p20: Long, val p30: Long, val p40: Long, val p80: Long, val p90: Long,
    val t10: Long, val t30: Long, val t40: Long, val t80: Long, val t90: Long,
) {
    /** Swatch shown in the picker. */
    val swatch: Color get() = Color(p40)
}

val palettes = listOf(
    Palette(
        "blue", "Blue",
        0xFF001B3E, 0xFF002F68, 0xFF004494, 0xFF0B57D0, 0xFFACC7FF, 0xFFD8E2FF,
        0xFF001F25, 0xFF004E5A, 0xFF006878, 0xFF62D4EC, 0xFFB0EBFF,
    ),
    Palette(
        "green", "Green",
        0xFF002204, 0xFF00390A, 0xFF005313, 0xFF1B6C2A, 0xFF8FD98C, 0xFFAAF5A6,
        0xFF001F26, 0xFF004E59, 0xFF006876, 0xFF62D3E8, 0xFFAFECFF,
    ),
    Palette(
        "purple", "Purple",
        0xFF21005D, 0xFF381E72, 0xFF4F378B, 0xFF6750A4, 0xFFD0BCFF, 0xFFEADDFF,
        0xFF31111D, 0xFF633B48, 0xFF7D5260, 0xFFEFB8C8, 0xFFFFD8E4,
    ),
    Palette(
        "orange", "Orange",
        0xFF360F00, 0xFF561E00, 0xFF792F00, 0xFF9C4200, 0xFFFFB68F, 0xFFFFDBCA,
        0xFF231B00, 0xFF4B4400, 0xFF665E00, 0xFFD6C80A, 0xFFF3E48A,
    ),
    Palette(
        "teal", "Teal",
        0xFF00201E, 0xFF003734, 0xFF00504B, 0xFF006A64, 0xFF54DBD1, 0xFF9FF2E9,
        0xFF0C1D33, 0xFF2C4257, 0xFF425A72, 0xFFAAC7E5, 0xFFC9E2FF,
    ),
    Palette(
        "pink", "Pink",
        0xFF3E001D, 0xFF5E1133, 0xFF7B2949, 0xFF9B4060, 0xFFFFB1C6, 0xFFFFD9E2,
        0xFF2E1500, 0xFF5B3D1F, 0xFF75552F, 0xFFE6BE8F, 0xFFFFDDB9,
    ),
    Palette(
        "graphite", "Graphite",
        0xFF191C1E, 0xFF2E3133, 0xFF44474A, 0xFF5C5F62, 0xFFC5C7CA, 0xFFE1E3E6,
        0xFF1A1C22, 0xFF3F4250, 0xFF565A6B, 0xFFC0C4D6, 0xFFDDE1F4,
    ),
)

fun Palette.scheme(dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = Color(p80),
        onPrimary = Color(p20),
        primaryContainer = Color(p30),
        onPrimaryContainer = Color(p90),
        secondary = Color(p80),
        onSecondary = Color(p20),
        secondaryContainer = Color(p30),
        onSecondaryContainer = Color(p90),
        tertiary = Color(t80),
        onTertiary = Color(t10),
        tertiaryContainer = Color(t30),
        onTertiaryContainer = Color(t90),
    )
} else {
    lightColorScheme(
        primary = Color(p40),
        onPrimary = Color.White,
        primaryContainer = Color(p90),
        onPrimaryContainer = Color(p10),
        secondary = Color(p40),
        onSecondary = Color.White,
        secondaryContainer = Color(p90),
        onSecondaryContainer = Color(p10),
        tertiary = Color(t40),
        onTertiary = Color.White,
        tertiaryContainer = Color(t90),
        onTertiaryContainer = Color(t10),
    )
}