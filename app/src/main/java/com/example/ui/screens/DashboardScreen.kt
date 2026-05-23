package com.example.ui.screens

import android.text.format.DateUtils
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.TimeFilter
import com.example.WineCellarViewModel
import com.example.data.TemperatureRecord
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    viewModel: WineCellarViewModel,
    modifier: Modifier = Modifier
) {
    val currentClimate by viewModel.currentClimateState.collectAsState()
    val historyPoints by viewModel.filteredHistoryState.collectAsState()
    val activeFilter by viewModel.timeFilter.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val lastSyncTime by viewModel.lastSyncTimestamp.collectAsState()
    val alertThreshold by viewModel.alertTempThreshold.collectAsState()

    val scrollState = rememberScrollState()

    // Determine if warning is active based on latest temperature vs threshold
    val currentTemp = currentClimate?.temperature ?: 13.5f
    val isAlertActive = currentTemp >= alertThreshold

    // Calculate dynamic time elapsed string
    var timeElapsedStr by remember { mutableStateOf("Just now") }
    LaunchedEffect(lastSyncTime, currentClimate) {
        // Run a periodic update every 10 seconds to refresh the sync elapsed time friendly string
        while(true) {
            val now = System.currentTimeMillis()
            val diff = now - lastSyncTime
            timeElapsedStr = when {
                diff < 60000 -> "Just now"
                diff < 3600000 -> "${diff / 60000} mins ago"
                else -> "${diff / 3600000} hours ago"
            }
            kotlinx.coroutines.delay(10000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .testTag("dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome and Status Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Reserve Cellar",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color.White
                )
                // Pulse Animation for Live Sync Active
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val alphaPulse by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse_alpha"
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = alphaPulse),
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = "LIVE SYNC ACTIVE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        ),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    )
                }
            }

            // Sync Button & Last Updated display
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "LAST UPDATED",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = timeElapsedStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )

                    IconButton(
                        onClick = { viewModel.triggerWifiSync() },
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = if (syncing) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                                shape = CircleShape
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                            .testTag("dashboard_sync_button")
                    ) {
                        if (syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Sync Now",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // WI-FI Sync status indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Wifi,
                contentDescription = "Wi-Fi Icon",
                tint = if (viewModel.isMicrocontrollerConnected.collectAsState().value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Hardware Controller: Mega-01 Active",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        // EMERGENCY OVER-TEMP WARNING
        AnimatedVisibility(
            visible = isAlertActive,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE53935), RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2C1414)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Alert Temperature",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = "CRITICAL TEMPERATURE WARNING!",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFFEF5350),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Current vault reading is ${String.format("%.1f", currentTemp)}°C, exceeding safety limit of ${alertThreshold}°C. Cork damage risk!",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFCDD2)
                        )
                    }
                }
            }
        }

        // Integrated Climate Dashboard Card (Rounded 32px, unified metrics)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (isAlertActive) Color(0xFFE53935).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(32.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Temperature Column
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "MAIN ALCOVE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            ),
                            color = if (isAlertActive) Color(0xFFEF5350) else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = String.format("%.1f", currentTemp),
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Light,
                                    fontSize = 48.sp,
                                    letterSpacing = (-1.5).sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "°C",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }

                    // Humidity Column
                    val currentHumidity = currentClimate?.humidity ?: 68.0f
                    val isHumidityAestheticOk = currentHumidity in 60.0f..75.0f
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "HUMIDITY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = String.format("%.0f", currentHumidity),
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Light,
                                    fontSize = 36.sp,
                                    letterSpacing = (-1).sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "%",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Text(
                            text = if (isHumidityAestheticOk) "Ideal" else "Fluctuating",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = if (isHumidityAestheticOk) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Interactive Temperature History Chart Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Chart Title and Filters Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Climate History",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Medium
                    )

                    // Time filter chips container
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimeFilter.values().forEach { filter ->
                            val active = filter == activeFilter
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (active) MaterialTheme.colorScheme.primary else Color.Transparent
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (active) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.setTimeFilter(filter) }
                                    .padding(vertical = 6.dp, horizontal = 12.dp)
                            ) {
                                Text(
                                    text = when (filter) {
                                        TimeFilter.DAY -> "Day"
                                        TimeFilter.WEEK -> "Week"
                                        TimeFilter.MONTH -> "Month"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Line Chart Area
                if (historyPoints.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    CellarLineChart(
                        records = historyPoints,
                        filter = activeFilter,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Chart Legend / Info Notes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp, 4.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        )
                        Text(
                            text = "Temperature (°C)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Text(
                        text = "*Tap chart to view reading details",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

/**
 * Custom line chart drawn to precise specification.
 * Solid Canvas structure + click detector for tooltip overlay details.
 */
@Composable
fun CellarLineChart(
    records: List<TemperatureRecord>,
    filter: TimeFilter,
    modifier: Modifier = Modifier
) {
    val primaryAmber = MaterialTheme.colorScheme.primary
    val secondaryWood = MaterialTheme.colorScheme.secondary
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)

    val density = androidx.compose.ui.platform.LocalDensity.current
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
