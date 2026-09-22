package com.med.sleepmanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

private val SleepManagerLight = lightColorScheme(
    primary = Color(0xFF2A4174),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF5E73A9),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF596078),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF6E7488),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF4C5F88),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE4FF),
    onTertiaryContainer = Color(0xFF101B33),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF40434B),
    outline = Color(0xFF5D5F67),
    outlineVariant = Color(0xFF797A83),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    inversePrimary = Color(0xFFB7CAFF),
    surfaceTint = Color(0xFF2A4174),
    surfaceBright = Color(0xFFFAF8FF),
    surfaceDim = Color(0xFFDAD8E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F3FA),
    surfaceContainer = Color(0xFFEEEDF4),
    surfaceContainerHigh = Color(0xFFEAE9F0),
    surfaceContainerHighest = Color(0xFFE2E2E9)
)

private val SleepManagerDark = darkColorScheme(
    primary = Color(0xFFB7CAFF),
    onPrimary = Color(0xFF223961),
    primaryContainer = Color(0xFF7A90C8),
    onPrimaryContainer = Color(0xFF10182D),
    secondary = Color(0xFFC6CAE0),
    onSecondary = Color(0xFF2E3244),
    secondaryContainer = Color(0xFF8A90A5),
    onSecondaryContainer = Color(0xFF000000),
    tertiary = Color(0xFFB8CAF7),
    onTertiary = Color(0xFF20365C),
    tertiaryContainer = Color(0xFF354B74),
    onTertiaryContainer = Color(0xFFDCE4FF),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF444A5E),
    onSurfaceVariant = Color(0xFFC9CAD4),
    outline = Color(0xFF8A90A5),
    outlineVariant = Color(0xFF44464F),
    inverseSurface = Color(0xFFE4E1E9),
    inverseOnSurface = Color(0xFF303036),
    inversePrimary = Color(0xFF2A4174),
    surfaceTint = Color(0xFFB7CAFF),
    surfaceBright = Color(0xFF393A40),
    surfaceDim = Color(0xFF121318),
    surfaceContainerLowest = Color(0xFF0D0E12),
    surfaceContainerLow = Color(0xFF1A1B20),
    surfaceContainer = Color(0xFF1E1F24),
    surfaceContainerHigh = Color(0xFF28292F),
    surfaceContainerHighest = Color(0xFF33343A)
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
)

@Composable
fun SleepManagerTheme(
    useSystemColors: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()

    val colors = when {
        useSystemColors && Build.VERSION.SDK_INT >= 31 && dark ->
            dynamicDarkColorScheme(context)
        useSystemColors && Build.VERSION.SDK_INT >= 31 && !dark ->
            dynamicLightColorScheme(context)
        dark -> SleepManagerDark
        else -> SleepManagerLight
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
