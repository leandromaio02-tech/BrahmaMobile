package com.brahma.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val scheme = darkColorScheme(
    primary = Color(0xFFA48BFF),
    secondary = Color(0xFF7EF9FF),
    background = Color(0xFF0B0D17),
    surface = Color(0xFF151A2C),
    onPrimary = Color(0xFF0B0D17),
    onBackground = Color(0xFFE8ECFF),
    onSurface = Color(0xFFE8ECFF)
)

@Composable
fun BrahmaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
