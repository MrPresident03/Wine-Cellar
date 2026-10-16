package com.houseofvino.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * House of Vino's own line icons: one 1.75 px stroke weight on a 24 px grid, round caps. Drawn
 * here rather than pulled from an icon pack so the set stays small, consistent and ours.
 */
object HovIcons {
    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    private fun PathBuilder.line(x1: Float, y1: Float, x2: Float, y2: Float) {
        moveTo(x1, y1)
        lineTo(x2, y2)
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx + r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
        close()
    }

    val Search by lazy { icon("search") { circle(10.5f, 10.5f, 6.25f); line(15.2f, 15.2f, 20f, 20f) } }
    val Add by lazy { icon("add") { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) } }
    val Close by lazy { icon("close") { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) } }
    val Back by lazy { icon("back") { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) } }
    val ChevronRight by lazy { icon("chevronRight") { moveTo(9.5f, 6f); lineTo(15.5f, 12f); lineTo(9.5f, 18f) } }
    val ChevronDown by lazy { icon("chevronDown") { moveTo(6f, 9.5f); lineTo(12f, 15.5f); lineTo(18f, 9.5f) } }
    val Filter by lazy { icon("filter") { line(4f, 7f, 20f, 7f); line(7f, 12f, 17f, 12f); line(10f, 17f, 14f, 17f) } }
    val Sort by lazy {
        icon("sort") {
            line(8f, 5f, 8f, 19f); moveTo(5f, 16f); lineTo(8f, 19f); lineTo(11f, 16f)
            line(16f, 19f, 16f, 5f); moveTo(13f, 8f); lineTo(16f, 5f); lineTo(19f, 8f)
        }
    }
    val Bottle by lazy {
        icon("bottle") {
            moveTo(10.5f, 2.5f); lineTo(13.5f, 2.5f); lineTo(13.5f, 7.5f)
            quadTo(16.5f, 8.5f, 16.5f, 11.5f); lineTo(16.5f, 21f); lineTo(7.5f, 21f); lineTo(7.5f, 11.5f)
            quadTo(7.5f, 8.5f, 10.5f, 7.5f); close()
            line(7.5f, 14f, 16.5f, 14f); line(7.5f, 18f, 16.5f, 18f)
        }
    }
    val Glass by lazy {
        icon("glass") {
            moveTo(7f, 3f); lineTo(17f, 3f); cubicTo(17.2f, 9f, 15f, 12.5f, 12f, 12.5f); cubicTo(9f, 12.5f, 6.8f, 9f, 7f, 3f); close()
            line(7.4f, 7f, 16.6f, 7f)
            line(12f, 12.5f, 12f, 20.5f); line(8.5f, 20.5f, 15.5f, 20.5f)
        }
    }
    val Bookmark by lazy { icon("bookmark") { moveTo(7f, 3.5f); lineTo(17f, 3.5f); lineTo(17f, 20.5f); lineTo(12f, 16.5f); lineTo(7f, 20.5f); close() } }
    val Chart by lazy { icon("chart") { line(4f, 20f, 20f, 20f); line(6.5f, 16.5f, 6.5f, 12f); line(11f, 16.5f, 11f, 7f); line(15.5f, 16.5f, 15.5f, 10f); line(19.5f, 16.5f, 19.5f, 4.5f) } }
    val Place by lazy {
        icon("place") {
            moveTo(12f, 21.5f); cubicTo(12f, 21.5f, 5f, 15f, 5f, 9.75f)
            arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 19f, y1 = 9.75f)
            cubicTo(19f, 15f, 12f, 21.5f, 12f, 21.5f); close()
            circle(12f, 9.75f, 2.5f)
        }
    }
    val Grid by lazy {
        icon("grid") {
            listOf(4f to 4f, 13.5f to 4f, 4f to 13.5f, 13.5f to 13.5f).forEach { (x, y) ->
                moveTo(x, y); lineTo(x + 6.5f, y); lineTo(x + 6.5f, y + 6.5f); lineTo(x, y + 6.5f); close()
            }
        }
    }
    val ListLines by lazy { icon("list") { line(9f, 6.5f, 20f, 6.5f); line(9f, 12f, 20f, 12f); line(9f, 17.5f, 20f, 17.5f); line(4.5f, 6.5f, 4.6f, 6.5f); line(4.5f, 12f, 4.6f, 12f); line(4.5f, 17.5f, 4.6f, 17.5f) } }
    val Settings by lazy { icon("settings") { line(4f, 7f, 7f, 7f); line(11f, 7f, 20f, 7f); circle(9f, 7f, 2f); line(4f, 17f, 13f, 17f); line(17f, 17f, 20f, 17f); circle(15f, 17f, 2f) } }
    val More by lazy { icon("more") { line(5.5f, 12f, 5.6f, 12f); line(12f, 12f, 12.1f, 12f); line(18.5f, 12f, 18.6f, 12f) } }
    val Edit by lazy { icon("edit") { moveTo(15f, 4.5f); lineTo(19.5f, 9f); lineTo(9f, 19.5f); lineTo(4.5f, 19.5f); lineTo(4.5f, 15f); close(); line(12.5f, 7f, 17f, 11.5f) } }
    val Camera by lazy {
        icon("camera") {
            moveTo(3.5f, 8f); lineTo(8f, 8f); lineTo(9.5f, 5f); lineTo(14.5f, 5f); lineTo(16f, 8f); lineTo(20.5f, 8f)
            lineTo(20.5f, 19f); lineTo(3.5f, 19f); close()
            circle(12f, 13.25f, 3.5f)
        }
    }
    val Barcode by lazy { icon("barcode") { listOf(4f, 6.5f, 9.5f, 11.5f, 14.5f, 17f, 20f).forEach { line(it, 5f, it, 19f) } } }
    val Check by lazy { icon("check") { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) } }
    val Offline by lazy {
        icon("offline") {
            moveTo(7.5f, 18.5f); lineTo(17.5f, 18.5f)
            arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 17f, y1 = 10.5f)
            arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 6.5f, y1 = 11.5f)
            arcTo(3.5f, 3.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 7.5f, y1 = 18.5f)
            line(4f, 4f, 20f, 20f)
        }
    }
    val Alert by lazy { icon("alert") { moveTo(12f, 4f); lineTo(21f, 19.5f); lineTo(3f, 19.5f); close(); line(12f, 10f, 12f, 14f); line(12f, 16.9f, 12f, 17f) } }
    val Undo by lazy {
        icon("undo") {
            moveTo(9f, 14f); lineTo(4f, 9f); lineTo(9f, 4f)
            moveTo(4f, 9f); lineTo(14f, 9f)
            arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 14f, y1 = 20f)
            lineTo(10f, 20f)
        }
    }
    val Move by lazy {
        icon("move") {
            line(4f, 8f, 18f, 8f); moveTo(14f, 4f); lineTo(18f, 8f); lineTo(14f, 12f)
            line(20f, 16f, 6f, 16f); moveTo(10f, 12f); lineTo(6f, 16f); lineTo(10f, 20f)
        }
    }
    val Tag by lazy { icon("tag") { moveTo(3.5f, 12.5f); lineTo(11.5f, 4.5f); lineTo(19.5f, 4.5f); lineTo(19.5f, 12.5f); lineTo(11.5f, 20.5f); close(); circle(15.5f, 8.5f, 1.25f) } }
    val Download by lazy { icon("download") { line(12f, 4f, 12f, 15f); moveTo(7.5f, 10.5f); lineTo(12f, 15f); lineTo(16.5f, 10.5f); line(5f, 19.5f, 19f, 19.5f) } }
    val Upload by lazy { icon("upload") { line(12f, 15f, 12f, 4f); moveTo(7.5f, 8.5f); lineTo(12f, 4f); lineTo(16.5f, 8.5f); line(5f, 19.5f, 19f, 19.5f) } }
    val Trash by lazy {
        icon("trash") {
            line(5f, 7f, 19f, 7f); moveTo(10f, 7f); lineTo(10f, 4.5f); lineTo(14f, 4.5f); lineTo(14f, 7f)
            moveTo(6.5f, 7f); lineTo(7.5f, 20f); lineTo(16.5f, 20f); lineTo(17.5f, 7f)
        }
    }
    val Person by lazy { icon("person") { circle(12f, 8.5f, 3.5f); moveTo(5f, 20f); cubicTo(5f, 15.5f, 8f, 13.5f, 12f, 13.5f); cubicTo(16f, 13.5f, 19f, 15.5f, 19f, 20f) } }
    val Sync by lazy {
        icon("sync") {
            moveTo(19f, 12f); arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 16.95f, y1 = 7.05f)
            moveTo(17f, 3.5f); lineTo(17f, 7.1f); lineTo(13.4f, 7.1f)
        }
    }
    val Minus by lazy { icon("minus") { line(5f, 12f, 19f, 12f) } }
}
