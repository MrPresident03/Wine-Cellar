package com.houseofvino.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.houseofvino.ui.app.AppGraph
import com.houseofvino.ui.nav.Navigator
import com.houseofvino.ui.theme.ThemeMode
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File

/** Renders the real app off-screen at phone size and writes PNGs for review. */
@OptIn(ExperimentalTestApi::class)
object Screenshots {
    private val outDir: File? = System.getProperty("houseofvino.screenshots")?.let { File(it, "desktop").apply { mkdirs() } }

    @Composable
    fun Host(content: @Composable () -> Unit) = content()

    /** Renders [navigator]'s current screen and saves it; returns the file (null when no output dir is set). */
    fun capture(name: String, graph: AppGraph, navigator: Navigator, theme: ThemeMode = ThemeMode.DARK, widthDp: Int = 393, heightDp: Int = 852): File? {
        val density = 2f
        var out: File? = null
        runSkikoComposeUiTest(size = Size(widthDp * density, heightDp * density), density = Density(density)) {
            setContent { HouseOfVinoApp(graph, theme, navigator) }
            // Let the database load and the screen settle.
            waitUntil(timeoutMillis = 10_000) { onAllNodes(hasContentDescription("Loading")).fetchSemanticsNodes().isEmpty() }
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            val bitmap = onRoot().captureToImage().asSkiaBitmap()
            val bytes = Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)?.bytes
            val dir = outDir
            if (bytes != null && dir != null) out = File(dir, "$name.png").apply { writeBytes(bytes) }
        }
        return out
    }
}
