package com.houseofvino.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.houseofvino.ui.app.AppGraph
import com.houseofvino.ui.nav.Navigator
import com.houseofvino.ui.theme.ThemeMode
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/** Renders the real app off-screen at phone size and writes PNGs for review. */
object Screenshots {
    private val outDir: File? = System.getProperty("houseofvino.screenshots")?.let { File(it, "desktop").apply { mkdirs() } }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    @Composable
    fun Host(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalLifecycleOwner provides TestLifecycleOwner(), content = content)
    }

    /** Renders [navigator]'s current screen; returns the PNG file (or null when no output dir is set). */
    fun capture(name: String, graph: AppGraph, navigator: Navigator, theme: ThemeMode = ThemeMode.DARK, widthDp: Int = 393, heightDp: Int = 852, settleMs: Long = 1500): File? {
        val density = 3f
        val scene = ImageComposeScene((widthDp * density).toInt(), (heightDp * density).toInt(), Density(density)) {
            Host { HouseOfVinoApp(graph, theme, navigator) }
        }
        try {
            var t = 0L
            val deadline = System.currentTimeMillis() + settleMs
            while (System.currentTimeMillis() < deadline) {
                scene.render(t)
                t += 16_000_000L
                Thread.sleep(16)
            }
            val image = scene.render(t)
            val dir = outDir ?: return null
            val bytes = image.encodeToData(EncodedImageFormat.PNG)?.bytes ?: return null
            return File(dir, "$name.png").apply { writeBytes(bytes) }
        } finally {
            scene.close()
        }
    }
}
