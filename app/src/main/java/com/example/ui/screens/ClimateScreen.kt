package com.example.ui.screens

import android.Manifest
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.WineCellarViewModel
import com.example.alerts.Notifications
import com.example.data.TimeFilter
import com.example.ui.components.CellarLineChart
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun ClimateScreen(viewModel: WineCellarViewModel) {
    val context = LocalContext.current
    val cellar by viewModel.cellar.collectAsState()
    val latest by viewModel.latestReading.collectAsState()
    val history by viewModel.history.collectAsState()
    val filter by viewModel.timeFilter.collectAsState()
    val alertsEnabled by viewModel.alertsEnabled.collectAsState()

    // Ticks once a minute so "updated 5 min ago" stays current.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }

    val threshold = cellar?.alertThreshold ?: 18.0
    var sliderValue by remember(threshold) { mutableFloatStateOf(threshold.toFloat()) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setAlertsEnabled(granted)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Cellar Climate", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.White)

        val reading = latest
        val ageMs = if (reading != null) now - reading.timestamp else Long.MAX_VALUE
        val online = reading != null && ageMs < 60L * 60 * 1000
        val alert = reading != null && online && reading.temperature > threshold

        // ---------------------------------------------------------------- current reading
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard(
                label = "TEMPERATURE",
                value = reading?.let { String.format(Locale.getDefault(), "%.1f°C", it.temperature) } ?: "--",
                valueColor = if (alert) Color(0xFFFCA5A5) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "HUMIDITY",
                value = reading?.let { String.format(Locale.getDefault(), "%.0f%% RH", it.humidity) } ?: "--",
                valueColor = Color.White,
                modifier = Modifier.weight(1f)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (online) Color(0xFF10B981) else Color.Gray, CircleShape)
            )
            Text(
                text = when {
                    reading == null -> "  No readings yet. Set up the sensor from Settings → Temperature sensor."
                    online -> "  Sensor online · updated " +
                        DateUtils.getRelativeTimeSpanString(reading.timestamp, now, DateUtils.MINUTE_IN_MILLIS)
                    else -> "  Sensor offline · last reading " +
                        DateUtils.getRelativeTimeSpanString(reading.timestamp, now, DateUtils.MINUTE_IN_MILLIS)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        // ---------------------------------------------------------------- history chart
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("History", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TimeFilter.values().forEach { option ->
                            val active = option == filter
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (active) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { viewModel.setTimeFilter(option) }
                                    .padding(vertical = 4.dp, horizontal = 10.dp)
                            ) {
                                Text(
                                    option.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
                if (history.size < 2) {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Not enough readings in this period yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    CellarLineChart(records = history, filter = filter, modifier = Modifier.fillMaxWidth().height(180.dp))
                    val temps = history.map { it.temperature }
                    Text(
                        String.format(
                            Locale.getDefault(),
                            "Low %.1f°C · High %.1f°C · Avg %.1f°C",
                            temps.minOrNull() ?: 0f,
                            temps.maxOrNull() ?: 0f,
                            temps.average()
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // ---------------------------------------------------------------- alerts
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Temperature alert", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                    String.format(Locale.getDefault(), "Warn when the cellar goes above %.1f°C", sliderValue),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { viewModel.setAlertThreshold(sliderValue.toDouble()) },
                    valueRange = 8f..24f,
                    steps = 31
                )
                Text(
                    "Shared with everyone in this cellar.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Notify this phone", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text(
                            "Checks about every 15 minutes, even when the app is closed.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = alertsEnabled,
                        onCheckedChange = { enable ->
                            if (enable && Build.VERSION.SDK_INT >= 33 && !Notifications.canNotify(context)) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.setAlertsEnabled(enable)
                            }
                        }
                    )
                }
                if (alertsEnabled && !Notifications.canNotify(context)) {
                    Text(
                        "Notifications are turned off for this app in your phone's settings, so alerts can't be shown.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
            Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = valueColor)
        }
    }
}
