package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme =
  darkColorScheme(
    primary = PrimaryAmber,
    secondary = SecondaryWood,
    tertiary = TertiaryCream,
    background = BackgroundCharcoal,
    surface = SurfaceCharcoal,
    onPrimary = BackgroundCharcoal,
    onSecondary = OnBackgroundLight,
    onBackground = OnBackgroundLight,
    onSurface = OnSurfaceLight,
    outline = CardBorder
  )

@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = DarkColorScheme,
    typography = Typography,
    content = content
  )
}
