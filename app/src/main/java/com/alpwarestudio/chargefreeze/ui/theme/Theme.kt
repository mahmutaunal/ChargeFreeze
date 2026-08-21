package com.alpwarestudio.chargefreeze.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.alpwarestudio.chargefreeze.data.ThemeMode

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF20C7AE),
    onPrimary = Color(0xFF00201B),
    primaryContainer = Color(0xFF0D443C),
    onPrimaryContainer = Color(0xFF9CF2DF),
    secondary = Color(0xFF9CD5CA),
    background = Color(0xFF07141B),
    onBackground = Color(0xFFE7F2F5),
    surface = Color(0xFF0D1C24),
    onSurface = Color(0xFFE7F2F5),
    surfaceVariant = Color(0xFF132630),
    onSurfaceVariant = Color(0xFFB8C7CC),
    outline = Color(0xFF324852),
    error = Color(0xFFFF5257),
    onError = Color.White
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1C9A39),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F8DD),
    onPrimaryContainer = Color(0xFF052B0C),
    secondary = Color(0xFF4E6354),
    background = Color(0xFFF8FAF8),
    onBackground = Color(0xFF171D18),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171D18),
    surfaceVariant = Color(0xFFF1F4F1),
    onSurfaceVariant = Color(0xFF5D665F),
    outline = Color(0xFFD5DCD6),
    error = Color(0xFFD62F35),
    onError = Color.White
)

@Composable
fun ChargeFreezeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}
