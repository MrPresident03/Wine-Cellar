package com.example.ui.components

import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PhotoUtils
import com.example.data.WineBottle
import com.example.data.presetForVarietal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decoded thumbnails, so scrolling the grid doesn't decode the same image again and again. */
object ThumbnailCache {
    private val cache = LruCache<String, ImageBitmap>(400)

    private fun key(id: String, base64: String) = id + ":" + base64.hashCode()

    fun peek(id: String, base64: String?): ImageBitmap? =
        if (base64 == null) null else cache.get(key(id, base64))

    fun decode(id: String, base64: String): ImageBitmap? {
        val k = key(id, base64)
        cache.get(k)?.let { return it }
        val bitmap = PhotoUtils.decode(base64)?.asImageBitmap() ?: return null
        cache.put(k, bitmap)
        return bitmap
    }
}

fun WineBottle.styleKey(): String = preset ?: presetForVarietal(varietal)

/** Shows the bottle's photo thumbnail, or the coloured bottle drawing when there's no photo. */
@Composable
fun BottleImage(bottle: WineBottle, modifier: Modifier = Modifier) {
    val thumb = bottle.thumb
    val bitmap by produceState(ThumbnailCache.peek(bottle.id, thumb), bottle.id, thumb) {
        if (value == null && thumb != null) {
            value = withContext(Dispatchers.Default) { ThumbnailCache.decode(bottle.id, thumb) }
        }
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = "Photo of ${bottle.wineryName}",
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize()
        )
    } else {
        WineBottleVector(styleKey = bottle.styleKey(), modifier = modifier)
    }
}

/** Stylised glass bottle, coloured by wine style. */
@Composable
fun WineBottleVector(
    styleKey: String?,
    modifier: Modifier = Modifier
) {
    val gradientColors = when (styleKey) {
        "preset_cabernet" -> listOf(Color(0xFF6B0E23), Color(0xFF2C0109))
        "preset_chardonnay" -> listOf(Color(0xFFE5B54F), Color(0xFF4C360C))
        "preset_rose" -> listOf(Color(0xFFE98895), Color(0xFF5E1724))
        "preset_champagne" -> listOf(Color(0xFFCF9E53), Color(0xFF422C0A))
        else -> listOf(Color(0xFF8B1229), Color(0xFF32020B))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            val w = size.width
            val h = size.height
            drawRect(
                color = Color.White.copy(alpha = 0.08f),
                topLeft = Offset(w * 0.42f, 0f),
                size = Size(w * 0.16f, h * 0.3f)
            )
            drawRect(
                color = Color.White.copy(alpha = 0.12f),
                topLeft = Offset(w * 0.15f, h * 0.45f),
                size = Size(w * 0.1f, h * 0.4f)
            )
        }

        val labelChar = when (styleKey) {
            "preset_cabernet" -> "C"
            "preset_chardonnay" -> "W"
            "preset_rose" -> "R"
            "preset_champagne" -> "S"
            else -> "V"
        }
        Box(
            modifier = Modifier
                .size(width = 24.dp, height = 32.dp)
                .background(Color(0xE8141211), RoundedCornerShape(2.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = labelChar,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = when (styleKey) {
                    "preset_cabernet" -> Color(0xFFF59E0B)
                    "preset_chardonnay" -> Color(0xFFE5B54F)
                    "preset_rose" -> Color(0xFFE98895)
                    "preset_champagne" -> Color(0xFFCF9E53)
                    else -> Color.White
                }
            )
        }
    }
}
