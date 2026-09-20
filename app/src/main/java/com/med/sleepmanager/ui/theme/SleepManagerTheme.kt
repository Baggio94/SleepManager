package com.med.sleepmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightPalette = lightColorScheme(
    primary = Color(0xFF5B72E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF101A46),
    secondary = Color(0xFF3E8FB4),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2F0FF),
    onSecondaryContainer = Color(0xFF093449),
    tertiary = Color(0xFFC47F18),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE2AF),
    onTertiaryContainer = Color(0xFF402A00),
    background = Color(0xFFEAF2FF),
    onBackground = Color(0xFF172033),
    surface = Color(0xFFF7FAFF),
    onSurface = Color(0xFF172033),
    surfaceVariant = Color(0xFFE1E9F8),
    onSurfaceVariant = Color(0xFF5E6B87),
    outline = Color(0xFF7A87A2),
    outlineVariant = Color(0xFFC7D1E5),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkPalette = darkColorScheme(
    primary = Color(0xFF88A8FF),
    onPrimary = Color(0xFF10214F),
    primaryContainer = Color(0xFF233B78),
    onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = Color(0xFF73D8FF),
    onSecondary = Color(0xFF003548),
    secondaryContainer = Color(0xFF174A60),
    onSecondaryContainer = Color(0xFFC7F0FF),
    tertiary = Color(0xFFF7C66A),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5D450F),
    onTertiaryContainer = Color(0xFFFFE2A8),
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFF3F6FF),
    surface = Color(0xFF11192C),
    onSurface = Color(0xFFF3F6FF),
    surfaceVariant = Color(0xFF1A243B),
    onSurfaceVariant = Color(0xFFB8C2E0),
    outline = Color(0xFF8590AB),
    outlineVariant = Color(0xFF35405A),
    error = Color(0xFFFF7B7B),
    onError = Color(0xFF4A0003),
    errorContainer = Color(0xFF762126),
    onErrorContainer = Color(0xFFFFDAD8)
)

@Composable
fun SleepManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkPalette else LightPalette,
        content = content
    )
}
