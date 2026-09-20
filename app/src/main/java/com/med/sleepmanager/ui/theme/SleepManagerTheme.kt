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

private val LightFallback = lightColorScheme(
    primary = Color(0xFF1F5F8B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE7FF),
    onPrimaryContainer = Color(0xFF001D32),
    secondary = Color(0xFF52606C),
    secondaryContainer = Color(0xFFD6E4F0),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFF7F9FC)
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

private val DarkFallback = darkColorScheme(
    primary = Color(0xFF96CCF7),
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF114B70),
    onPrimaryContainer = Color(0xFFCDE7FF),
    secondary = Color(0xFFBAC8D5),
    secondaryContainer = Color(0xFF344956),
    background = Color(0xFF0D141A),
    surface = Color(0xFF0D141A)
)

@Composable
fun SleepManagerTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()

    val colors = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 && !dark -> dynamicLightColorScheme(context)
        dark -> DarkFallback
        else -> LightFallback
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
