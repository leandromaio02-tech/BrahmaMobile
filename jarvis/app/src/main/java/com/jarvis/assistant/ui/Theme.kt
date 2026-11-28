package com.jarvis.assistant.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val JarvisBlack = Color(0xFF02050A)
val JarvisNavy = Color(0xFF071421)
val JarvisPanel = Color(0xFF0B1D2B)
val JarvisCyan = Color(0xFF38E1FF)
val JarvisCyanDim = Color(0xFF1596B3)
val JarvisText = Color(0xFFDDF7FF)
val JarvisMuted = Color(0xFF7FA8B8)
val JarvisGold = Color(0xFFFFC66B)
val JarvisRed = Color(0xFFFF6B6B)
val JarvisGreen = Color(0xFF6CF0A8)

private val Dark = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = JarvisBlack,
    secondary = JarvisCyanDim,
    onSecondary = JarvisText,
    background = JarvisBlack,
    onBackground = JarvisText,
    surface = JarvisNavy,
    onSurface = JarvisText,
    surfaceVariant = JarvisPanel,
    onSurfaceVariant = JarvisMuted,
    error = JarvisRed,
    onError = JarvisBlack
)

private val Light = lightColorScheme(
    primary = Color(0xFF007A96),
    onPrimary = Color.White,
    secondary = Color(0xFF00657D),
    onSecondary = Color.White,
    background = Color(0xFFF2F8FA),
    onBackground = Color(0xFF071421),
    surface = Color.White,
    onSurface = Color(0xFF071421),
    surfaceVariant = Color(0xFFE2F0F5),
    onSurfaceVariant = Color(0xFF4B6875),
    error = Color(0xFFB3261E),
    onError = Color.White
)

@Composable
fun JarvisTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
