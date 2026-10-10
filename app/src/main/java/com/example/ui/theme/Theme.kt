package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkGlassColorScheme = darkColorScheme(
    primary = IcyBluePrimary,
    onPrimary = CharcoalDeep,
    primaryContainer = CharcoalElevated,
    onPrimaryContainer = IcyBlueLight,
    secondary = SilverLuminous,
    onSecondary = CharcoalDeep,
    secondaryContainer = GlassBackground,
    onSecondaryContainer = SilverLuminous,
    tertiary = IcyBlueGlow,
    onTertiary = CharcoalDeep,
    background = CharcoalDeep,
    onBackground = SilverLuminous,
    surface = CharcoalSurface,
    onSurface = SilverLuminous,
    surfaceVariant = CharcoalElevated,
    onSurfaceVariant = SilverMuted,
    outline = SilverBorder,
    outlineVariant = Color(0x1AFFFFFF)
)

@Composable
fun LivingPhoneTheme(
    content: @Composable () -> Unit
) {
    // Living Phone always presents its signature deep obsidian glass aesthetic
    MaterialTheme(
        colorScheme = DarkGlassColorScheme,
        typography = Typography,
        content = content
    )
}
