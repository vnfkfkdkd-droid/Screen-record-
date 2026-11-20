package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val D7nDarkColorScheme = darkColorScheme(
    primary = D7nCrimson,
    onPrimary = Color.White,
    primaryContainer = D7nCrimsonContainer,
    onPrimaryContainer = D7nCrimsonGlow,
    secondary = InternalAudioCyan,
    onSecondary = StudioObsidian,
    secondaryContainer = InternalAudioCyanContainer,
    onSecondaryContainer = InternalAudioCyan,
    tertiary = StudioAmber,
    onTertiary = StudioObsidian,
    tertiaryContainer = StudioAmberContainer,
    onTertiaryContainer = StudioAmber,
    background = StudioObsidian,
    onBackground = StudioTextPrimary,
    surface = StudioSurface,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = StudioTextSecondary,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceElevated,
    surfaceContainerHigh = StudioSurfaceHighlight,
    outline = StudioBorder,
    outlineVariant = StudioBorder.copy(alpha = 0.5f),
    error = D7nCrimson,
    onError = Color.White
)

private val D7nLightColorScheme = lightColorScheme(
    primary = D7nCrimson,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE5E9),
    onPrimaryContainer = Color(0xFF8E001A),
    secondary = Color(0xFF0097B2),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8F7FD),
    onSecondaryContainer = Color(0xFF004E5C),
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = Color(0xFFD0D7DE)
)

val D7nShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun D7nTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) D7nDarkColorScheme else D7nLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = D7nShapes,
        content = content
    )
}
