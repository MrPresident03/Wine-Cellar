package com.houseofvino.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

/** WCAG 2.x contrast for every text/mark colour on every surface it can sit on. */
class ThemeContrastTest {
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return (maxOf(la, lb) / minOf(la, lb)).toDouble()
    }

    @Test
    fun textMeetsAaOnAllSurfaces() {
        for (c in listOf(HovColors.Dark, HovColors.Light)) {
            val surfaces = listOf("canvas" to c.canvas, "surface" to c.surface, "surfaceRaised" to c.surfaceRaised)
            val text = listOf(
                "ink" to c.ink, "inkMuted" to c.inkMuted, "inkFaint" to c.inkFaint, "burgundyBright" to c.burgundyBright,
                "hold" to c.hold, "ready" to c.ready, "drinkSoon" to c.drinkSoon, "past" to c.past, "danger" to c.danger,
            )
            for ((sn, s) in surfaces) for ((tn, t) in text) {
                val r = contrast(t, s)
                assertTrue(r >= 4.5, "${if (c.isDark) "dark" else "light"}: $tn on $sn is ${(r * 100).toInt() / 100.0}:1")
            }
            assertTrue(contrast(c.onBurgundy, c.burgundy) >= 4.5, "text on burgundy buttons")
            assertTrue(contrast(c.burgundy, c.canvas) >= 3.0 || contrast(c.burgundyBright, c.canvas) >= 3.0, "burgundy marks visible")
        }
    }
}
