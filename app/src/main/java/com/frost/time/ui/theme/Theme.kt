package com.frost.time.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Expressive Palette Fallback (when dynamic wallpaper color is not available)
private val FrostDarkColorScheme = darkColorScheme(
    primary = Color(0xFF64D2FF),
    onPrimary = Color(0xFF003549),
    primaryContainer = Color(0xFF004D68),
    onPrimaryContainer = Color(0xFFBDE9FF),
    secondary = Color(0xFF70D6BC),
    onSecondary = Color(0xFF00382C),
    secondaryContainer = Color(0xFF005141),
    onSecondaryContainer = Color(0xFF8CF2D8),
    tertiary = Color(0xFFFFB59D),
    onTertiary = Color(0xFF5B1B06),
    tertiaryContainer = Color(0xFF7C311A),
    onTertiaryContainer = Color(0xFFFFDBD1),
    background = Color(0xFF0E1418),
    onBackground = Color(0xFFDEE3E8),
    surface = Color(0xFF0E1418),
    onSurface = Color(0xFFDEE3E8),
    surfaceVariant = Color(0xFF40484D),
    onSurfaceVariant = Color(0xFFBFC8CE),
    surfaceContainer = Color(0xFF161C21),
    surfaceContainerHigh = Color(0xFF20262B),
    surfaceContainerHighest = Color(0xFF2B3136),
    outline = Color(0xFF899298),
    outlineVariant = Color(0xFF40484D)
)

private val FrostLightColorScheme = lightColorScheme(
    primary = Color(0xFF00678A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC3E8FF),
    onPrimaryContainer = Color(0xFF001E2C),
    secondary = Color(0xFF006B56),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF8DF3D8),
    onSecondaryContainer = Color(0xFF002018),
    tertiary = Color(0xFF9E4229),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBD1),
    onTertiaryContainer = Color(0xFF3B0900),
    background = Color(0xFFF6FAFE),
    onBackground = Color(0xFF171C20),
    surface = Color(0xFFF6FAFE),
    onSurface = Color(0xFF171C20),
    surfaceVariant = Color(0xFFDCE4E9),
    onSurfaceVariant = Color(0xFF40484D),
    surfaceContainer = Color(0xFFEAEFF3),
    surfaceContainerHigh = Color(0xFFE4E9ED),
    surfaceContainerHighest = Color(0xFFDEE3E8),
    outline = Color(0xFF70787D),
    outlineVariant = Color(0xFFBFC8CE)
)

// Vibrant colors for the multi-segment screen time ring
val RingPalette = listOf(
    Color(0xFF64D2FF), // Sky Blue
    Color(0xFF30D158), // Emerald Green
    Color(0xFFFF9F0A), // Warm Amber
    Color(0xFFBF5AF2), // Electric Purple
    Color(0xFFFF375F), // Coral Red
    Color(0xFFFFD60A), // Bright Yellow
    Color(0xFF5E5CE6)  // Indigo
)

@Composable
fun FrostTimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> FrostDarkColorScheme
        else -> FrostLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FrostTypography,
        content = content
    )
}
