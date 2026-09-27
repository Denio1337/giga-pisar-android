package ru.gigapisar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
internal fun GigaPisarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            darkColorScheme(
                primary = Color(0xFF4CAF50),
                secondary = Color(0xFF81C784),
            ),
        content = content,
    )
}
