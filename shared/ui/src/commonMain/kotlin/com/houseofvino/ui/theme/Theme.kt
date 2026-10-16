package com.houseofvino.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

enum class ThemeMode { SYSTEM, DARK, LIGHT }

object Hov {
    val colors: HovColors
        @Composable @ReadOnlyComposable get() = LocalHovColors.current
    val type: HovType
        @Composable @ReadOnlyComposable get() = LocalHovType.current
}

/** Spacing and sizes on the 4 dp grid. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val gutter = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val vintageColumn = 64.dp
    val touch = 48.dp
}

@Composable
fun HouseOfVinoTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colors = if (dark) HovColors.Dark else HovColors.Light
    val type = hovType()
    CompositionLocalProvider(
        LocalHovColors provides colors,
        LocalHovType provides type,
        LocalTextSelectionColors provides TextSelectionColors(colors.burgundyBright, colors.burgundy.copy(alpha = 0.35f)),
    ) {
        MaterialTheme(
            colorScheme = materialScheme(colors),
            typography = Typography(
                bodyLarge = type.body,
                bodyMedium = type.detail,
                bodySmall = type.caption,
                labelLarge = type.label,
                labelMedium = type.label,
                labelSmall = type.caption,
                titleLarge = type.title,
                titleMedium = type.heading,
                titleSmall = type.labelStrong,
                headlineSmall = type.title,
            ),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(2.dp),
                small = RoundedCornerShape(4.dp),
                medium = RoundedCornerShape(8.dp),
                large = RoundedCornerShape(12.dp),
                extraLarge = RoundedCornerShape(16.dp),
            ),
            content = content,
        )
    }
}

private fun materialScheme(c: HovColors): ColorScheme =
    if (c.isDark) {
        darkColorScheme(
            primary = c.burgundy, onPrimary = c.onBurgundy, primaryContainer = c.burgundy, onPrimaryContainer = c.onBurgundy,
            secondary = c.burgundyBright, onSecondary = c.canvas, background = c.canvas, onBackground = c.ink,
            surface = c.canvas, onSurface = c.ink, surfaceVariant = c.surface, onSurfaceVariant = c.inkMuted,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceRaised, surfaceContainerHighest = c.surfaceRaised,
            surfaceContainerLow = c.surface, surfaceContainerLowest = c.canvas, outline = c.hairline, outlineVariant = c.hairline,
            error = c.danger, onError = c.canvas, scrim = c.scrim,
        )
    } else {
        lightColorScheme(
            primary = c.burgundy, onPrimary = c.onBurgundy, primaryContainer = c.burgundy, onPrimaryContainer = c.onBurgundy,
            secondary = c.burgundy, onSecondary = c.onBurgundy, background = c.canvas, onBackground = c.ink,
            surface = c.canvas, onSurface = c.ink, surfaceVariant = c.surface, onSurfaceVariant = c.inkMuted,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceRaised, surfaceContainerHighest = c.surfaceRaised,
            surfaceContainerLow = c.surface, surfaceContainerLowest = c.surfaceRaised, outline = c.hairline, outlineVariant = c.hairline,
            error = c.danger, onError = c.surfaceRaised, scrim = c.scrim,
        )
    }
