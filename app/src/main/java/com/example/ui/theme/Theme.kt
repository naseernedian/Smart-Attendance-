package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val WhiteLightGreenColorScheme = lightColorScheme(
    primary = LightGreenPrimary,
    onPrimary = LightGreenOnPrimary,
    primaryContainer = LightGreenContainer,
    onPrimaryContainer = LightGreenOnContainer,
    secondary = LightGreenSecondary,
    onSecondary = LightGreenOnSecondary,
    secondaryContainer = LightGreenSecondaryContainer,
    onSecondaryContainer = LightGreenOnSecondaryContainer,
    tertiary = SoftSageTertiary,
    onTertiary = SoftSageOnTertiary,
    tertiaryContainer = SoftSageContainer,
    onTertiaryContainer = SoftSageOnTertiaryContainer,
    background = PureWhiteBackground,
    onBackground = TextPrimary,
    surface = PureWhiteSurface,
    onSurface = TextPrimary,
    surfaceVariant = SoftMintSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = LightGreenOutline,
    error = AbsentRed,
    onError = Color.White,
    errorContainer = AbsentRedBg,
    onErrorContainer = Color(0xFF6B151D)
)

val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)

@Composable
fun SmartAttendanceTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WhiteLightGreenColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
