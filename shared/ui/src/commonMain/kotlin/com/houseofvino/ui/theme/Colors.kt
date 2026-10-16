package com.houseofvino.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.houseofvino.core.model.DrinkStatus

/** Colour tokens from docs/design-system.md. Contrast pairs are checked in ThemeContrastTest. */
@Immutable
data class HovColors(
    val isDark: Boolean,
    val canvas: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val hairline: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val burgundy: Color,
    val burgundyBright: Color,
    val onBurgundy: Color,
    val hold: Color,
    val ready: Color,
    val drinkSoon: Color,
    val past: Color,
    val danger: Color,
    val scrim: Color,
) {
    fun status(s: DrinkStatus): Color = when (s) {
        DrinkStatus.TOO_YOUNG -> hold
        DrinkStatus.READY -> ready
        DrinkStatus.DRINK_SOON -> drinkSoon
        DrinkStatus.PAST_PEAK -> past
        DrinkStatus.UNKNOWN -> inkFaint
    }

    companion object {
        val Dark = HovColors(
            isDark = true,
            canvas = Color(0xFF1B1918),
            surface = Color(0xFF242120),
            surfaceRaised = Color(0xFF2E2A28),
            hairline = Color(0xFF3A3533),
            ink = Color(0xFFEEE7DC),
            inkMuted = Color(0xFFB0A699),
            inkFaint = Color(0xFF9A9085),
            burgundy = Color(0xFF8E2C3B),
            burgundyBright = Color(0xFFD27A86),
            onBurgundy = Color(0xFFF8EFEF),
            hold = Color(0xFF8D9BB0),
            ready = Color(0xFF9DB38A),
            drinkSoon = Color(0xFFD2A55C),
            past = Color(0xFFB98A80),
            danger = Color(0xFFE08A7E),
            scrim = Color(0x99000000),
        )

        val Light = HovColors(
            isDark = false,
            canvas = Color(0xFFF5EFE4),
            surface = Color(0xFFFBF7F0),
            surfaceRaised = Color(0xFFFFFFFF),
            hairline = Color(0xFFE2D9CB),
            ink = Color(0xFF241E1B),
            inkMuted = Color(0xFF5A5049),
            inkFaint = Color(0xFF72675E),
            burgundy = Color(0xFF7A2333),
            burgundyBright = Color(0xFF7A2333),
            onBurgundy = Color(0xFFFFF8F5),
            hold = Color(0xFF4D5E78),
            ready = Color(0xFF4C6B3A),
            drinkSoon = Color(0xFF805C1A),
            past = Color(0xFF8A4E42),
            danger = Color(0xFFA23B2E),
            scrim = Color(0x66000000),
        )
    }
}

val LocalHovColors = staticCompositionLocalOf { HovColors.Dark }
