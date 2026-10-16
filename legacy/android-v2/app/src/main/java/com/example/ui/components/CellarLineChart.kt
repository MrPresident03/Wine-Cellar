package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ClimateReading
import com.example.data.TimeFilter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Temperature line chart. Tap a point to see the reading.
 * (Moved here from the old DashboardScreen, now fed by real sensor readings.)
 */
@Composable
fun CellarLineChart(
    records: List<ClimateReading>,
    filter: TimeFilter,
    modifier: Modifier = Modifier
) {
    val primaryAmber = MaterialTheme.colorScheme.primary
    val secondaryWood = MaterialTheme.colorScheme.secondary
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)

    val density = LocalDensity.current
    var touchX by remember { mutableStateOf(-1f) }
    var containerWidth by remember { mutableStateOf(0) }

    val selectedRecordIndex = remember(touchX, records, containerWidth) {
        if (touchX < 0 || records.isEmpty() || containerWidth <= 0) {
            -1
        } else {
            val marginX = with(density) { 48.dp.toPx() }
            val rightPadding = with(density) { 12.dp.toPx() }
            val chartWidth = containerWidth - marginX

            val timestamps = records.map { it.timestamp }
            val minTime = timestamps.minOrNull() ?: 0L
            val maxTime = timestamps.maxOrNull() ?: 1L
            val timeRange = (maxTime - minTime).coerceAtLeast(1L)

            var closestIndex = -1
            var smallestDistance = Float.MAX_VALUE

            for (i in records.indices) {
                val ratio = (records[i].timestamp - minTime).toFloat() / timeRange
                val x = marginX + (ratio * (chartWidth - rightPadding))
                val dist = Math.abs(x - touchX)
                if (dist < smallestDistance) {
                    smallestDistance = dist
                    closestIndex = i
                }
            }

            // Limit selection sensitivity within 55dp of the point
            if (smallestDistance < with(density) { 55.dp.toPx() }) {
                closestIndex
            } else {
                -1
            }
        }
    }

    val formatter = remember(filter) {
        when (filter) {
            TimeFilter.DAY -> SimpleDateFormat("HH:mm", Locale.getDefault())
            TimeFilter.WEEK -> SimpleDateFormat("EEE d", Locale.getDefault())
            TimeFilter.MONTH -> SimpleDateFormat("MMM d", Locale.getDefault())
        }
    }

    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier
            .onSizeChanged { size ->
                containerWidth = size.width
            }
            .pointerInput(records) {
                detectTapGestures(
                    onPress = { offset ->
                        touchX = offset.x
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Calculate margins for x and y labels
            val marginY = 32.dp.toPx()
            val marginX = 48.dp.toPx()

            val chartWidth = canvasWidth - marginX
            val chartHeight = canvasHeight - marginY

            if (records.isEmpty()) return@Canvas

            // Extract values
            val timestamps = records.map { it.timestamp }
            val temperatures = records.map { it.temperature }

            val minTime = timestamps.minOrNull() ?: 0L
            val maxTime = timestamps.maxOrNull() ?: 1L
            val timeRange = (maxTime - minTime).coerceAtLeast(1L)

            // Dynamic temperature bounding range spanning safe wine zone
            val minTempMeasured = temperatures.minOrNull() ?: 10.0f
            val maxTempMeasured = temperatures.maxOrNull() ?: 18.0f
            val displayMinTemp = (minTempMeasured - 0.5f).coerceAtMost(11.0f)
            val displayMaxTemp = (maxTempMeasured + 0.5f).coerceAtLeast(16.0f)
            val tempRange = (displayMaxTemp - displayMinTemp).coerceAtLeast(1.0f)

            // 1. Draw horizontal grid lines and temperatures labels
            val gridCount = 4
            for (i in 0..gridCount) {
                val fraction = i.toFloat() / gridCount
                val y = chartHeight - (fraction * chartHeight)

                // Grid line
                drawLine(
                    color = gridColor,
                    start = Offset(marginX, y),
                    end = Offset(canvasWidth, y),
                    strokeWidth = 1.dp.toPx()
                )

                // Degree label
                val tempLabelText = String.format("%.1f°C", displayMinTemp + (fraction * tempRange))
                drawText(
                    textMeasurer = textMeasurer,
                    text = tempLabelText,
                    topLeft = Offset(4.dp.toPx(), y - 8.dp.toPx()),
                    style = TextStyle(
                        color = labelColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }

            // Translate coordinates functions
            fun getX(timestamp: Long): Float {
                val ratio = (timestamp - minTime).toFloat() / timeRange
                return marginX + (ratio * (chartWidth - 12.dp.toPx()))
            }

            fun getY(temp: Float): Float {
                val ratio = (temp - displayMinTemp) / tempRange
                return chartHeight - (ratio * chartHeight)
            }

            // Save calculated coordinates for lines and points
            val points = records.map { r ->
                Offset(getX(r.timestamp), getY(r.temperature))
            }

            // Draw line curve
            if (points.size > 1) {
                val path = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) {
                        val pPrev = points[i - 1]
                        val pCurr = points[i]
                        // Standard cubic bezier pathing for high-end organic flow curves (anti-ai-slop quality)
                        val conX1 = (pPrev.x + pCurr.x) / 2
                        val conY1 = pPrev.y
                        val conX2 = (pPrev.x + pCurr.x) / 2
                        val conY2 = pCurr.y
                        cubicTo(conX1, conY1, conX2, conY2, pCurr.x, pCurr.y)
                    }
                }

                // Transparent fade fill beneath path curve
                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(points.last().x, chartHeight)
                    lineTo(points.first().x, chartHeight)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryAmber.copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = chartHeight
                    )
                )

                drawPath(
                    path = path,
                    color = primaryAmber,
                    style = Stroke(
                        width = 3.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            }

            // Draw X Axis dates/time ticks
            val labelCount = when (filter) {
                TimeFilter.DAY -> 4
                TimeFilter.WEEK -> 5
                TimeFilter.MONTH -> 4
            }

            val recordsIndicesToLabel = if (records.size <= labelCount) {
                records.indices.toList()
            } else {
                val step = records.size / (labelCount - 1)
                (0 until labelCount).map { (it * step).coerceAtMost(records.size - 1) }
            }

            recordsIndicesToLabel.forEach { index ->
                val record = records[index]
                val x = getX(record.timestamp)
                val labelText = formatter.format(Date(record.timestamp))

                drawText(
                    textMeasurer = textMeasurer,
                    text = labelText,
                    topLeft = Offset(x - 20.dp.toPx(), chartHeight + 6.dp.toPx()),
                    style = TextStyle(
                        color = labelColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                )

                // Subtle tiny tick line
                drawLine(
                    color = gridColor,
                    start = Offset(x, chartHeight),
                    end = Offset(x, chartHeight + 4.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Tooltip Highlight Dot & vertical line drawing using precalculated selectedRecordIndex
            if (selectedRecordIndex != -1 && selectedRecordIndex < points.size) {
                val highlightPoint = points[selectedRecordIndex]

                // Draw thin warm line
                drawLine(
                    color = secondaryWood.copy(alpha = 0.5f),
                    start = Offset(highlightPoint.x, 0f),
                    end = Offset(highlightPoint.x, chartHeight),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )

                // Highlight coordinate bubble
                drawCircle(
                    color = primaryAmber,
                    radius = 6.dp.toPx(),
                    center = highlightPoint
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = highlightPoint
                )
            }
        }

        // TOOLTIP POPUP OVERLAY UI using precalculated selectedRecordIndex
        if (selectedRecordIndex != -1 && selectedRecordIndex < records.size) {
            val record = records[selectedRecordIndex]
            val dateStr = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(record.timestamp))

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${String.format("%.1f", record.temperature)}°C  •  ${String.format("%.1f", record.humidity)}% RH",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
