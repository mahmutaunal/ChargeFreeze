package com.alpwarestudio.chargefreeze.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.alpwarestudio.chargefreeze.data.ThemeMode

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF24D1B5),
    onPrimary = Color(0xFF00251F),
    primaryContainer = Color(0xFF0C433A),
    onPrimaryContainer = Color(0xFFB1F5E8),
    secondary = Color(0xFF55D86B),
    onSecondary = Color(0xFF06250C),
    secondaryContainer = Color(0xFF173D20),
    onSecondaryContainer = Color(0xFFC0F4C7),
    background = Color(0xFF071117),
    onBackground = Color(0xFFF3F7F8),
    surface = Color(0xFF0D1A22),
    onSurface = Color(0xFFF3F7F8),
    surfaceVariant = Color(0xFF12232C),
    onSurfaceVariant = Color(0xFFAFBEC5),
    outline = Color(0xFF2B3E48),
    outlineVariant = Color(0xFF1D3039),
    error = Color(0xFFFF4D58),
    onError = Color.White,
    errorContainer = Color(0xFF4C1E24),
    onErrorContainer = Color(0xFFFFDADD)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF159A3B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F5DD),
    onPrimaryContainer = Color(0xFF092E11),
    secondary = Color(0xFF1AB795),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0F5EC),
    onSecondaryContainer = Color(0xFF07382E),
    background = Color(0xFFF7F8F6),
    onBackground = Color(0xFF151B17),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF151B17),
    surfaceVariant = Color(0xFFF0F3F0),
    onSurfaceVariant = Color(0xFF606A63),
    outline = Color(0xFFD3DAD4),
    outlineVariant = Color(0xFFE5E9E5),
    error = Color(0xFFD92F3B),
    onError = Color.White,
    errorContainer = Color(0xFFFFDADC),
    onErrorContainer = Color(0xFF5A1119)
)

private val ChargeFreezeTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Medium
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    )
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
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = ChargeFreezeTypography,
        content = content
    )
}
