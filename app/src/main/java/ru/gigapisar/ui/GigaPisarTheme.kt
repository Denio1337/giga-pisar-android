package ru.gigapisar.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Tonal palette built from the site green. Every role is set, so no stock Material
 * purple leaks in. Light or dark follows the phone.
 */
private val LightColors =
    lightColorScheme(
        primary = Color(0xFF2E6B30),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFB1F1A9),
        onPrimaryContainer = Color(0xFF002204),
        secondary = Color(0xFF52634F),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD5E8CF),
        onSecondaryContainer = Color(0xFF101F10),
        tertiary = Color(0xFF38656A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFBCEBF0),
        onTertiaryContainer = Color(0xFF002023),
        background = Color(0xFFF7FBF1),
        onBackground = Color(0xFF181D17),
        surface = Color(0xFFF7FBF1),
        onSurface = Color(0xFF181D17),
        surfaceVariant = Color(0xFFDEE5D8),
        onSurfaceVariant = Color(0xFF424940),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF1F5EC),
        surfaceContainer = Color(0xFFEBEFE6),
        surfaceContainerHigh = Color(0xFFE6E9E0),
        surfaceContainerHighest = Color(0xFFE0E4DA),
        outline = Color(0xFF72796F),
        outlineVariant = Color(0xFFC2C9BD),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF96D78F),
        onPrimary = Color(0xFF003A08),
        primaryContainer = Color(0xFF145219),
        onPrimaryContainer = Color(0xFFB1F1A9),
        secondary = Color(0xFFB9CCB4),
        onSecondary = Color(0xFF253423),
        secondaryContainer = Color(0xFF3B4B38),
        onSecondaryContainer = Color(0xFFD5E8CF),
        tertiary = Color(0xFFA0CFD4),
        onTertiary = Color(0xFF00363B),
        tertiaryContainer = Color(0xFF1F4D52),
        onTertiaryContainer = Color(0xFFBCEBF0),
        background = Color(0xFF101510),
        onBackground = Color(0xFFE0E4DA),
        surface = Color(0xFF101510),
        onSurface = Color(0xFFE0E4DA),
        surfaceVariant = Color(0xFF424940),
        onSurfaceVariant = Color(0xFFC2C9BD),
        surfaceContainerLowest = Color(0xFF0B0F0A),
        surfaceContainerLow = Color(0xFF181D17),
        surfaceContainer = Color(0xFF1C211B),
        surfaceContainerHigh = Color(0xFF262B25),
        surfaceContainerHighest = Color(0xFF313630),
        outline = Color(0xFF8C9388),
        outlineVariant = Color(0xFF424940),
    )

@Composable
internal fun GigaPisarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
