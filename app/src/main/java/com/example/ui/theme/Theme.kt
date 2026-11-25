package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ColorTokensOutlineSubtle = androidx.compose.ui.graphics.Color(0xFF1F2D40)

private val StudioDarkColorScheme = darkColorScheme(
    primary = ChampagneGold,
    onPrimary = DeepBronze,
    primaryContainer = WarmGoldContainer,
    onPrimaryContainer = GoldTint,
    secondary = StudioSage,
    onSecondary = DeepSage,
    secondaryContainer = SageContainer,
    onSecondaryContainer = StudioSage,
    tertiary = LiveCoral,
    onTertiary = DeepCoral,
    tertiaryContainer = CoralContainer,
    onTertiaryContainer = LiveCoral,
    background = ObsidianCanvas,
    onBackground = CrispParchment,
    surface = MidnightSurface,
    onSurface = CrispParchment,
    surfaceVariant = SlateCard,
    onSurfaceVariant = MutedSilver,
    surfaceContainerLowest = ObsidianCanvas,
    surfaceContainerLow = MidnightSurface,
    surfaceContainer = SlateCard,
    surfaceContainerHigh = SlateElevated,
    surfaceContainerHighest = HairlineBorder,
    outline = HairlineBorder,
    outlineVariant = ColorTokensOutlineSubtle,
    error = LiveCoral,
    onError = DeepCoral,
    errorContainer = CoralContainer,
    onErrorContainer = CrispParchment
)

private val StudioLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = GoldTint,
    onPrimaryContainer = DeepBronze,
    secondary = DeepSage,
    onSecondary = LightSurface,
    background = LightCanvas,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightCard,
    onSurfaceVariant = LightMuted,
    outline = SoftSteel,
    error = LiveCoral,
    onError = LightSurface
)

val ClarionShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun ClarionTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) StudioDarkColorScheme else StudioLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ClarionTypography,
        shapes = ClarionShapes,
        content = content
    )
}
