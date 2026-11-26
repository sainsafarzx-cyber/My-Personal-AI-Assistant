package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LeonColorScheme = darkColorScheme(
    primary = ArcCyan,
    onPrimary = DeepObsidian,
    primaryContainer = HudSurfaceElevated,
    onPrimaryContainer = ArcCyanSoft,
    secondary = NeuralViolet,
    onSecondary = IceTextPrimary,
    secondaryContainer = ColorTokens.VioletContainer,
    onSecondaryContainer = NeuralVioletSoft,
    tertiary = EmeraldTelemetry,
    onTertiary = DeepObsidian,
    background = DeepObsidian,
    onBackground = IceTextPrimary,
    surface = MidnightSlate,
    onSurface = IceTextPrimary,
    surfaceVariant = HudSurface,
    onSurfaceVariant = SlateTextSecondary,
    outline = HudBorder,
    error = CrimsonEmergency,
    onError = IceTextPrimary
)

private object ColorTokens {
    val VioletContainer = androidx.compose.ui.graphics.Color(0xFF281D4E)
}

private val LeonShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun LeonAiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LeonColorScheme,
        typography = Typography,
        shapes = LeonShapes,
        content = content
    )
}
