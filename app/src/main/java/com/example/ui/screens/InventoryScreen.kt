package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.WineCellarViewModel
import com.example.data.CellarLayout
import com.example.data.STANDARD_VARIETALS
import com.example.data.WineBottle
import com.example.ui.components.BottleDetailDialog
import com.example.ui.components.BottleFormDialog
import com.example.ui.components.BottleImage
import java.util.Locale

private val AlertRed = Color(0xFFEF4444)

@Composable
fun InventoryScreen(viewModel: WineCellarViewModel, modifier: Modifier = Modifier) {
    val cellar by viewModel.cellar.collectAsState()
    val filtered by viewModel.filteredBottles.collectAsState()
    val allBottles by viewModel.bottles.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val latest by viewModel.latestReading.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val agingFilter by viewModel.agingFilter.collectAsState()
    val varietalFilter by viewModel.varietalFilter.collectAsState()
    val sortBy by viewModel.sortBy.collectAsState()

    var showFilters by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    // Dialog state. Bottles are tracked by id and looked up live, so a dialog never shows stale data
    // after a change from the other phone.
    var formOpen by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var prefillRow by remember { mutableStateOf(1) }
    var prefillCol by remember { mutableStateOf(1) }
    var detailId by remember { mutableStateOf<String?>(null) }

    val threshold = cellar?.alertThreshold ?: 18.0
    val reading = latest
    val readingFresh = reading != null && System.currentTimeMillis() - reading.timestamp < STALE_AFTER_MS
    val isAlert = reading != null && readingFresh && reading.temperature > threshold

    Column(modifier = modifier.fillMaxSize().testTag("inventory_screen")) {
        // ---------------------------------------------------------------- header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable {
                                    renameText = cellar?.name ?: ""
                                    showRename = true
                                }
                        ) {
                            Text(
                                text = cellar?.name ?: "My Cellar",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(Icons.Rounded.Edit, contentDescription = "Rename cellar", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        ) {
                            Text(
                                text = if (filtered.size != allBottles.size) "${filtered.size} of ${allBottles.size}" else "${allBottles.size} wines",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                maxLines = 1
                            )
                        }
                    }
                    if (isAlert) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Box(modifier = Modifier.size(6.dp).background(AlertRed, CircleShape))
                            Text(
                                text = "CELLAR ABOVE ${formatTemp(threshold)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                                color = AlertRed
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        val firstFree = firstFreeSlot(allBottles)
                        prefillRow = firstFree.first
                        prefillCol = firstFree.second
                        editingId = null
                        formOpen = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("add_bottle_trigger")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Store", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }

            if (importing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Copying your bottles from the previous version…", style = MaterialTheme.typography.bodySmall)
                }
            }

            // Climate pill: real sensor data, tap for the Climate tab
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .border(
                        1.dp,
                        if (isAlert) AlertRed.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        RoundedCornerShape(32.dp)
                    )
                    .background(if (isAlert) Color(0xFF7F1D1D).copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                    .clickable { viewModel.selectTab(1) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier.size(8.dp).background(
                            when {
                                reading == null || !readingFresh -> Color.Gray
                                isAlert -> AlertRed
                                else -> Color(0xFF10B981)
                            },
                            CircleShape
                        )
                    )
                    if (reading == null) {
                        Text(
                            "No sensor readings yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        Text(
                            text = formatTemp(reading.temperature.toDouble()),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                            color = if (isAlert) Color(0xFFFCA5A5) else MaterialTheme.colorScheme.primary
                        )
                        Text("•", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        Text(
                            text = String.format(Locale.getDefault(), "%.0f%% RH", reading.humidity),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                            color = Color.White
                        )
                        if (!readingFresh) {
                            Text(
                                "(sensor offline)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                Icon(Icons.Rounded.Thermostat, contentDescription = "Climate details", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search wineries, varietals, years…", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth().testTag("wine_search_bar")
            )

            // ---------------------------------------------------------------- filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showFilters = !showFilters }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.FilterList, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text("Filter & sort", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                    val active = (if (agingFilter != "All") 1 else 0) +
                        (if (varietalFilter != "All") 1 else 0) +
                        (if (sortBy != WineCellarViewModel.DEFAULT_SORT) 1 else 0)
                    if (active > 0) {
                        Box(
                            modifier = Modifier.size(16.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$active", color = MaterialTheme.colorScheme.onPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Icon(
                    if (showFilters) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (showFilters) "Hide filters" else "Show filters",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            AnimatedVisibility(visible = showFilters, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChipRow("Aging status", listOf("All", "Aging Only", "Ready (Not Aging)"), agingFilter) { viewModel.setAgingFilter(it) }
                    ChipRow("Varietal", listOf("All") + STANDARD_VARIETALS.filter { it != "Other" }, varietalFilter) { viewModel.setVarietalFilter(it) }
                    ChipRow("Sort by", WineCellarViewModel.SORT_OPTIONS, sortBy) { viewModel.setSortBy(it) }
                }
            }
        }

        // ---------------------------------------------------------------- rack + list
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                RackGrid(
                    allBottles = allBottles,
                    matchedIds = remember(filtered) { filtered.map { it.id }.toSet() },
                    onBottleClick = { detailId = it.id },
                    onEmptyClick = { r, c ->
                        prefillRow = r
                        prefillCol = c
                        editingId = null
                        formOpen = true
                    }
                )
            }
            if (filtered.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = if (allBottles.isNotEmpty()) "No bottles match your search or filters." else "The cellar is empty. Tap Store to add a bottle.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(filtered, key = { it.id }) { bottle ->
                    BottleListItem(bottle = bottle, onClick = { detailId = bottle.id })
                }
            }
        }
    }

    // ---------------------------------------------------------------- dialogs
    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename cellar") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Cellar name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameCellar(renameText)
                    showRename = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } }
        )
    }

    // If the bottle is removed (possibly from the other phone) while open, the dialog simply closes.
    val detailBottle = detailId?.let { id -> allBottles.firstOrNull { it.id == id } }
    if (detailBottle != null) {
        BottleDetailDialog(
            viewModel = viewModel,
            bottle = detailBottle,
            onEdit = {
                editingId = detailBottle.id
                detailId = null
                formOpen = true
            },
            onDismiss = { detailId = null }
        )
    }

    if (formOpen) {
        val editing = editingId?.let { id -> allBottles.firstOrNull { it.id == id } }
        key(editingId, prefillRow, prefillCol) {
            BottleFormDialog(
                viewModel = viewModel,
                initial = editing,
                prefillRow = prefillRow,
                prefillCol = prefillCol,
                onDismiss = {
                    formOpen = false
                    editingId = null
                }
            )
        }
    }
}

@Composable
private fun ChipRow(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = { Text(option, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }
}

@Composable
private fun RackGrid(
    allBottles: List<WineBottle>,
    matchedIds: Set<String>,
    onBottleClick: (WineBottle) -> Unit,
    onEmptyClick: (Int, Int) -> Unit
) {
    val bySlot = remember(allBottles) { allBottles.groupBy { Pair(it.gridRow, it.gridCol) } }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 1..CellarLayout.ROWS) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 1..CellarLayout.COLS) {
                        key(r, c) {
                            val inSlot = bySlot[Pair(r, c)].orEmpty()
                            val bottle = inSlot.firstOrNull()
                            val highlighted = bottle != null && bottle.id in matchedIds
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(0.85f)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (bottle != null) Color.Transparent else MaterialTheme.colorScheme.background.copy(alpha = 0.15f))
                                    .border(
                                        width = if (highlighted) 1.5.dp else 0.5.dp,
                                        color = when {
                                            highlighted -> MaterialTheme.colorScheme.primary
                                            bottle != null -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                                        },
                                        shape = RoundedCornerShape(3.dp)
                                    )
                                    .clickable {
                                        if (bottle != null) onBottleClick(bottle) else onEmptyClick(r, c)
                                    }
                                    .testTag("grid_cell_${r}_$c"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (bottle != null) {
                                    BottleImage(bottle = bottle)
                                    if (inSlot.size > 1) {
                                        // Left over from version 1, where two bottles could share a slot.
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(12.dp)
                                                .background(AlertRed, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("${inSlot.size}", fontSize = 7.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
            )
            Text(
                text = "Tap an empty slot to store a bottle there.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BottleListItem(bottle: WineBottle, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .testTag("bottle_item_${bottle.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    BottleImage(bottle = bottle)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = bottle.vintage.ifBlank { "NV" },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                        if (bottle.isAging) {
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("AGING", fontSize = 7.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                    Text(
                        text = bottle.wineryName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val classification = bottle.classification
                    if (!classification.isNullOrBlank()) {
                        Text(
                            text = classification,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = bottle.varietal,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "R${bottle.gridRow} : C" + String.format(Locale.US, "%02d", bottle.gridCol),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), modifier = Modifier.size(16.dp))
                }
                val price = bottle.price
                if (price != null && price > 0.0) {
                    Text(
                        text = "$" + String.format(Locale.getDefault(), "%.2f", price),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(end = 4.dp, top = 2.dp)
                    )
                }
            }
        }
    }
}

private const val STALE_AFTER_MS = 60L * 60 * 1000

private fun formatTemp(value: Double): String = String.format(Locale.getDefault(), "%.1f°C", value)

private fun firstFreeSlot(bottles: List<WineBottle>): Pair<Int, Int> {
    val taken = bottles.map { Pair(it.gridRow, it.gridCol) }.toSet()
    for (r in 1..CellarLayout.ROWS) {
        for (c in 1..CellarLayout.COLS) {
            if (Pair(r, c) !in taken) return Pair(r, c)
        }
    }
    return Pair(1, 1)
}
