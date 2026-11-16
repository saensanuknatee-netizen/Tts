package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = SiamGold,
    onPrimary = Color(0xFF1A1100),
    primaryContainer = SiamGoldContainer,
    onPrimaryContainer = OnSiamGoldContainer,
    secondary = EmeraldTeal,
    onSecondary = Color(0xFF00201D),
    secondaryContainer = EmeraldTealContainer,
    onSecondaryContainer = OnEmeraldTealContainer,
    tertiary = IndigoAccent,
    onTertiary = Color.White,
    background = ObsidianBackground,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    error = CoralError,
    errorContainer = CoralErrorContainer,
    onError = Color.White,
    onErrorContainer = Color(0xFFFFE4E6),
    outline = OutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = SiamGoldDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF451A03),
    secondary = EmeraldTealDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF042F2E),
    tertiary = IndigoAccent,
    onTertiary = Color.White,
    background = IvoryBackground,
    onBackground = TextPrimaryLight,
    surface = IvorySurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = IvorySurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    error = CoralError,
    onError = Color.White,
    outline = OutlineLight
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
