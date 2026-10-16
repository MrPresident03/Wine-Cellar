package com.houseofvino.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.houseofvino.core.model.BottleFormat
import com.houseofvino.core.model.DrinkStatus
import com.houseofvino.core.model.DrinkingWindow
import com.houseofvino.core.model.Vintage
import com.houseofvino.core.model.WineStyle
import com.houseofvino.data.search.CellarRow
import com.houseofvino.data.search.FormatCount
import com.houseofvino.ui.theme.Hov
import com.houseofvino.ui.theme.Space

/**
 * The cellar-book line: vintage large in the left column, producer and cuvée beside it, place and
 * quantities quiet on the right. Read by screen readers as one sentence.
 */
@Composable
fun VintageRow(row: CellarRow, currentYear: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val status = DrinkingWindow.statusOf(row.drinkFrom, row.drinkTo, currentYear)
    val cuvee = listOfNotNull(row.name.ifBlank { null }, row.classification).joinToString(" ")
    val place = listOfNotNull(row.appellation ?: row.region, row.country).distinct().joinToString(", ")
    val quantity = formatsLabel(row.formats)
    val spoken = buildString {
        append(row.producer)
        if (cuvee.isNotEmpty()) append(", ").append(cuvee)
        append(", ").append(spokenVintage(row.vintage))
        append(", ").append(spokenFormats(row.formats))
        if (status != DrinkStatus.UNKNOWN) append(", ").append(status.label.lowercase())
    }
    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Row(Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.gutter, top = 12.dp, bottom = 12.dp)) {
            // Vintage, producer and quantity share a baseline, as the lines of a cellar book do.
            VintageFigure(row.vintage, Modifier.width(Space.vintageColumn).alignByBaseline())
            Column(Modifier.weight(1f).alignByBaseline()) {
                Text(row.producer, style = Hov.type.heading, color = Hov.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (cuvee.isNotEmpty()) {
                    Text(cuvee, style = Hov.type.cuvee, color = Hov.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (place.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(place, style = Hov.type.detail, color = Hov.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(Space.m))
            Column(Modifier.alignByBaseline(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(quantity, style = Hov.type.figure, color = if (row.bottles > 0) Hov.colors.ink else Hov.colors.inkFaint, textAlign = TextAlign.End)
                StatusMark(status)
            }
        }
        Hairline(inset = Space.gutter)
    }
}

/** Large old-style vintage numerals; "NV" for non-vintage and an em dash when unknown. */
@Composable
fun VintageFigure(vintage: Vintage, modifier: Modifier = Modifier, small: Boolean = false) {
    Text(
        text = when (vintage) {
            is Vintage.Year -> vintage.year.toString()
            Vintage.NonVintage -> "NV"
            Vintage.Unknown -> "—"
        },
        style = if (small) Hov.type.vintageSmall else Hov.type.vintage,
        color = if (vintage is Vintage.Unknown) Hov.colors.inkFaint else Hov.colors.ink,
        modifier = modifier,
        maxLines = 1,
    )
}

fun formatsLabel(formats: List<FormatCount>): String = when {
    formats.isEmpty() -> "None left"
    formats.size == 1 -> "${formats[0].count} × ${BottleFormat.label(formats[0].ml)}"
    else -> formats.joinToString("\n") { "${it.count} × ${BottleFormat.label(it.ml)}" }
}

private fun spokenFormats(formats: List<FormatCount>): String = if (formats.isEmpty()) {
    "none left"
} else {
    formats.joinToString(" and ") { f ->
        val name = BottleFormat.NAMED.firstOrNull { it.ml == f.ml && it.ml != BottleFormat.STANDARD_ML }?.name?.lowercase()
        when {
            name != null -> "${f.count} ${name}${if (f.count == 1) "" else "s"}"
            f.ml == BottleFormat.STANDARD_ML -> "${f.count} bottle${if (f.count == 1) "" else "s"}"
            else -> "${f.count} × ${BottleFormat.label(f.ml)}"
        }
    }
}

private fun spokenVintage(v: Vintage) = when (v) {
    is Vintage.Year -> v.year.toString()
    Vintage.NonVintage -> "non-vintage"
    Vintage.Unknown -> "vintage unknown"
}

/** The quiet stand-in when a wine has no photo: a bottle outline in its style's colour. */
@Composable
fun BottleSilhouette(style: WineStyle?, modifier: Modifier = Modifier, height: Dp = 120.dp) {
    val fill = styleColor(style)
    val outline = Hov.colors.hairline
    Canvas(modifier.size(width = height * 0.32f, height = height).semantics { }) {
        val w = size.width
        val h = size.height
        val neckW = w * 0.32f
        val neckTop = h * 0.02f
        val shoulder = h * 0.32f
        val p = Path().apply {
            moveTo((w - neckW) / 2, neckTop)
            lineTo((w + neckW) / 2, neckTop)
            lineTo((w + neckW) / 2, h * 0.2f)
            quadraticTo(w * 0.98f, h * 0.24f, w * 0.98f, shoulder)
            lineTo(w * 0.98f, h * 0.97f)
            quadraticTo(w * 0.98f, h, w * 0.9f, h)
            lineTo(w * 0.1f, h)
            quadraticTo(w * 0.02f, h, w * 0.02f, h * 0.97f)
            lineTo(w * 0.02f, shoulder)
            quadraticTo(w * 0.02f, h * 0.24f, (w - neckW) / 2, h * 0.2f)
            close()
        }
        drawPath(p, fill)
        drawPath(p, outline, style = Stroke(width = 1.dp.toPx()))
        // Label.
        drawLine(outline, Offset(w * 0.12f, h * 0.55f), Offset(w * 0.88f, h * 0.55f), strokeWidth = 1.dp.toPx())
        drawLine(outline, Offset(w * 0.12f, h * 0.78f), Offset(w * 0.88f, h * 0.78f), strokeWidth = 1.dp.toPx())
    }
}

@Composable
fun styleColor(style: WineStyle?): Color {
    val dark = Hov.colors.isDark
    return when (style) {
        WineStyle.RED, null -> if (dark) Color(0xFF4A1E26) else Color(0xFF6B2532)
        WineStyle.WHITE -> if (dark) Color(0xFF8C8255) else Color(0xFFD8C98A)
        WineStyle.ROSE -> if (dark) Color(0xFF9A5A5E) else Color(0xFFE2A9A6)
        WineStyle.SPARKLING -> if (dark) Color(0xFF7A7650) else Color(0xFFCFC796)
        WineStyle.SWEET -> if (dark) Color(0xFF9A6A2A) else Color(0xFFD3A04F)
        WineStyle.FORTIFIED -> if (dark) Color(0xFF5A2A1E) else Color(0xFF7E3B26)
        WineStyle.ORANGE -> if (dark) Color(0xFF9A5A2A) else Color(0xFFD08A4A)
        WineStyle.OTHER -> if (dark) Color(0xFF4E4844) else Color(0xFFB5ABA0)
    }
}
