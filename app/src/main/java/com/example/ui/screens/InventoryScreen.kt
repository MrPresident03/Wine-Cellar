package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.TimeFilter
import com.example.WineCellarViewModel
import com.example.data.WineBottle
import java.io.File

/**
 * Premium glass bottle vector design, responsive and customizable.
 * Colors adapt beautifully based on selected flavor profile.
 */
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
        // Aesthetic bottle glass reflections
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            val w = size.width
            val h = size.height

            // High-end vertical reflection gloss
            drawRect(
                color = Color.White.copy(alpha = 0.08f),
                topLeft = Offset(w * 0.42f, 0f),
                size = androidx.compose.ui.geometry.Size(w * 0.16f, h * 0.3f)
            )

            // Body contour highlight
            drawRect(
                color = Color.White.copy(alpha = 0.12f),
                topLeft = Offset(w * 0.15f, h * 0.45f),
                size = androidx.compose.ui.geometry.Size(w * 0.1f, h * 0.4f)
            )
        }

        // Estate custom label overlay
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

/**
 * Helper to save snapped bitmap safely in system cache directory
 */
fun saveBitmapToCache(context: Context, bitmap: Bitmap): String? {
    return try {
        val file = File(context.cacheDir, "wine_captured_${System.currentTimeMillis()}.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        file.absolutePath
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InventoryScreen(
    viewModel: WineCellarViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bottles by viewModel.filteredBottlesState.collectAsState()
    val allBottles by viewModel.bottlesState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    // Climate and history states for integrated dashboard readings
    val currentClimate by viewModel.currentClimateState.collectAsState()
    val historyPoints by viewModel.filteredHistoryState.collectAsState()
    val activeFilter by viewModel.timeFilter.collectAsState()
    val syncing by viewModel.syncing.collectAsState()

    val activeAgingFilter by viewModel.agingFilter.collectAsState()
    val activeVarietalFilter by viewModel.varietalFilter.collectAsState()
    val activeSortBy by viewModel.sortBy.collectAsState()

    var showFilterMenu by remember { mutableStateOf(false) }
    val standardVarietals = remember {
        listOf(
            "Cabernet Sauvignon",
            "Pinot Noir",
            "Chardonnay",
            "Shiraz / Syrah",
            "Sauvignon Blanc",
            "Merlot",
            "Pinot Grigio",
            "Riesling",
            "Champagne / Sparkling",
            "Rosé",
            "Other"
        )
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var selectedBottleForDetail by remember { mutableStateOf<WineBottle?>(null) }

    // Prefilled grid row/col
    var prefilledRow by remember { mutableStateOf(1) }
    var prefilledCol by remember { mutableStateOf(1) }

    val sortedBottlesByVarietal = bottles

    val totalRows = 18
    val totalCols = 10

    // Check alert status
    val isAlertActive = currentClimate != null && currentClimate!!.temperature > viewModel.alertTempThreshold.collectAsState().value

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("inventory_screen")
    ) {
        // PREMIUM CELLAR HEADER AND COMPACT CLIMATE CAPSULE
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
                Column {
                    Text(
                        text = "My Cellar",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = Color.White
                    )
                    if (isAlertActive) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        color = Color(0xFFEF4444),
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = "ATTENTION: FLUID LIMITS EXCEEDED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = Color(0xFFEF4444)
                            )
                        }
                    }
                }

                // Add Bottle Trigger button
                Button(
                    onClick = {
                        prefilledRow = 1
                        prefilledCol = 1
                        showAddDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("add_bottle_trigger")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add Bottle",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Store",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // DYNAMIC CLIMATE MONITORING PILL CAPSULE (Interactive)
            val currentTemp = currentClimate?.temperature ?: 13.2f
            val currentHumid = currentClimate?.humidity ?: 68.0f

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .border(
                        width = 1.dp,
                        color = if (isAlertActive) Color(0xFFEF4444).copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .background(
                        if (isAlertActive) Color(0xFF7F1D1D).copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    )
                    .clickable { showHistoryDialog = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Pulse live syncer point icon
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (isAlertActive) Color(0xFFEF4444) else Color(0xFF10B981),
                                shape = CircleShape
                            )
                    )

                    Text(
                        text = "CURRENT CLIMATE:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )

                    Text(
                        text = "${String.format("%.1f", currentTemp)}°C",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = if (isAlertActive) Color(0xFFFCA5A5) else MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "•",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )

                    Text(
                        text = "${String.format("%.0f", currentHumid)}% RH",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Color.White
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.TrendingUp,
                    contentDescription = "View History Graph",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // MAIN SEARCH BAR
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = {
                    Text(
                        "Search vault varietals, estates, years...",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wine_search_bar")
            )

            // DYNAMIC REFINE INVENTORY / FILTER PANEL
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showFilterMenu = !showFilterMenu }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filters",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Refine Inventory",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.2.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val activeFiltersCount = (if (activeAgingFilter != "All") 1 else 0) +
                                                 (if (activeVarietalFilter != "All") 1 else 0) +
                                                 (if (activeSortBy != "Varietal (A-Z)") 1 else 0)
                        if (activeFiltersCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$activeFiltersCount",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (showFilterMenu) "Collapse" else "Filter & Sort",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                        )
                        Icon(
                            imageVector = if (showFilterMenu) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand Filters",
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showFilterMenu,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. AGING FILTER
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Aging Status",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("All", "Aging Only", "Ready (Not Aging)").forEach { opt ->
                                    val isSelected = activeAgingFilter == opt
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setAgingFilter(opt) },
                                        label = { Text(opt, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }
                        }

                        // 2. VARIETAL FILTER
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Varietal Selection",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val filterCategories = listOf("All") + standardVarietals.filter { it != "Other" }
                                filterCategories.forEach { vOpt ->
                                    val isSelected = activeVarietalFilter == vOpt
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setVarietalFilter(vOpt) },
                                        label = { Text(vOpt, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }
                        }

                        // 3. SORTING OPTIONS
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Sort Inventory By",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(
                                    "Varietal (A-Z)",
                                    "Winery (A-Z)",
                                    "Vintage (Newest)",
                                    "Vintage (Oldest)",
                                    "Price (Highest)",
                                    "Price (Lowest)"
                                ).forEach { sortOpt ->
                                    val isSelected = activeSortBy == sortOpt
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setSortBy(sortOpt) },
                                        label = { Text(sortOpt, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // SCROLLABLE BODY (GRID & COLLECTION)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // THE 18x10 LUXE WOODEN SHELF MAP SECTION
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    // Deluxe Shelf Box
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val bottleMap = remember(allBottles) { allBottles.associateBy { it.gridRow to it.gridCol } }
                            val matchedIds = remember(bottles) { bottles.map { it.id }.toSet() }

                            for (r in 1..totalRows) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    for (c in 1..totalCols) {
                                        key(r, c) {
                                            val existingBottle = bottleMap[r to c]
                                            val matchesSearch = existingBottle != null && matchedIds.contains(existingBottle.id)

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(0.85f)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(
                                                        if (existingBottle != null) Color.Transparent else MaterialTheme.colorScheme.background.copy(alpha = 0.15f)
                                                    )
                                                    .border(
                                                        width = if (existingBottle != null && matchesSearch) 1.5.dp else 0.5.dp,
                                                        color = when {
                                                            existingBottle != null && matchesSearch -> MaterialTheme.colorScheme.primary
                                                            existingBottle != null -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                                                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                                                        },
                                                        shape = RoundedCornerShape(3.dp)
                                                    )
                                                    .clickable {
                                                        if (existingBottle != null) {
                                                            selectedBottleForDetail = existingBottle
                                                            showDetailDialog = true
                                                        } else {
                                                            prefilledRow = r
                                                            prefilledCol = c
                                                            showAddDialog = true
                                                        }
                                                    }
                                                    .testTag("grid_cell_${r}_${c}"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (existingBottle != null) {
                                                    val bitmap = remember(existingBottle.photoUri) {
                                                        if (existingBottle.photoUri != null && !existingBottle.photoUri.startsWith("preset_")) {
                                                            try {
                                                                BitmapFactory.decodeFile(existingBottle.photoUri)?.asImageBitmap()
                                                            } catch (e: Exception) {
                                                                null
                                                            }
                                                        } else {
                                                            null
                                                        }
                                                    }

                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (bitmap != null) {
                                                            Image(
                                                                bitmap = bitmap,
                                                                contentDescription = "Stored wine bottle picture",
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        } else {
                                                            val preset = existingBottle.photoUri ?: when {
                                                                existingBottle.varietal.contains("Cabernet", ignoreCase = true) || existingBottle.varietal.contains("Margaux", ignoreCase = true) || existingBottle.varietal.contains("Shiraz", ignoreCase = true) -> "preset_cabernet"
                                                                existingBottle.varietal.contains("Chardonnay", ignoreCase = true) || existingBottle.varietal.contains("Sauvignon", ignoreCase = true) || existingBottle.varietal.contains("White", ignoreCase = true) -> "preset_chardonnay"
                                                                existingBottle.varietal.contains("Rose", ignoreCase = true) -> "preset_rose"
                                                                existingBottle.varietal.contains("Champagne", ignoreCase = true) -> "preset_champagne"
                                                                else -> "preset_cabernet"
                                                            }
                                                            WineBottleVector(styleKey = preset)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Wooden Base Bar Illustration
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                                                MaterialTheme.colorScheme.background
                                            )
                                        ),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            ) {}

                            Text(
                                text = "💡 Tip: Tap an empty cell to store a new bottle at that exact coordinate.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // COLLECTION ALPHABETICAL VARIABLE LIST HEADER
            if (sortedBottlesByVarietal.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Layers,
                                contentDescription = "None",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matches found." else "The cellar is empty. Add some bottles!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(sortedBottlesByVarietal, key = { it.id }) { bottle ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .clickable {
                                selectedBottleForDetail = bottle
                                showDetailDialog = true
                            }
                            .testTag("bottle_item_${bottle.id}"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                              ) {
                                // Real photo or custom style vector in circular card thumbnails
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = 1.dp,
                                            color = Color.White.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val listBitmap = remember(bottle.photoUri) {
                                        if (bottle.photoUri != null && !bottle.photoUri.startsWith("preset_")) {
                                            try {
                                                BitmapFactory.decodeFile(bottle.photoUri)?.asImageBitmap()
                                            } catch (e: Exception) {
                                                null
                                            }
                                        } else {
                                            null
                                        }
                                    }

                                    if (listBitmap != null) {
                                        Image(
                                            bitmap = listBitmap,
                                            contentDescription = "Wine picture",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        val preset = bottle.photoUri ?: when {
                                            bottle.varietal.contains("Cabernet", ignoreCase = true) || bottle.varietal.contains("Margaux", ignoreCase = true) || bottle.varietal.contains("Shiraz", ignoreCase = true) -> "preset_cabernet"
                                            bottle.varietal.contains("Chardonnay", ignoreCase = true) || bottle.varietal.contains("Sauvignon", ignoreCase = true) || bottle.varietal.contains("White", ignoreCase = true) -> "preset_chardonnay"
                                            bottle.varietal.contains("Rose", ignoreCase = true) -> "preset_rose"
                                            bottle.varietal.contains("Champagne", ignoreCase = true) -> "preset_champagne"
                                            else -> "preset_cabernet"
                                        }
                                        WineBottleVector(styleKey = preset)
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = bottle.varietal,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = Color.White
                                        )
                                        if (bottle.isAging) {
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        shape = RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "AGING",
                                                    fontSize = 7.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "${bottle.wineryName} • ${bottle.vintage}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                        if (bottle.price != null && bottle.price > 0.0) {
                                            Text(
                                                text = "• $${String.format("%.2f", bottle.price)}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "R${bottle.gridRow} : C${String.format("%02d", bottle.gridCol)}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = "Details",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // DIALOG 0: CLIMATE MONITOR HISTORY CHART popup
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Climate Observatory",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { showHistoryDialog = false }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Quick Stats row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("TEMPERATURE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                                Text("${String.format("%.1f", currentClimate?.temperature ?: 13.2f)}°C", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("HUMIDITY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                                Text("${String.format("%.0f", currentClimate?.humidity ?: 68.0f)}% RH", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                            }
                        }
                    }

                    // Chart header and filters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Historical Plots",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TimeFilter.values().forEach { filter ->
                                val active = filter == activeFilter
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .border(
                                            width = 1.dp,
                                            color = if (active) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { viewModel.setTimeFilter(filter) }
                                        .padding(vertical = 4.dp, horizontal = 10.dp)
                                ) {
                                    Text(
                                        text = when (filter) {
                                            TimeFilter.DAY -> "Day"
                                            TimeFilter.WEEK -> "Week"
                                            TimeFilter.MONTH -> "Month"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    // Line Chart rendering
                    if (historyPoints.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        // Directly load standard compiled canvas chart from package!
                        CellarLineChart(
                            records = historyPoints,
                            filter = activeFilter,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )
                    }

                    // Synchronization footer and controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Wi-Fi Sensor Link Status", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.secondary)
                                Text("Last checked: Active live sync", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            }

                            Button(
                                onClick = { viewModel.triggerWifiSync() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                if (syncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.dp, color = Color.White)
                                } else {
                                    Icon(Icons.Rounded.Refresh, contentDescription = "Sync", modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sync", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text("Close Details", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // DIALOG 1: ADD BOTTLE DIALOG FORM
    if (showAddDialog) {
        var wineryInput by remember { mutableStateOf("") }
        var varietalInput by remember { mutableStateOf("") }
        var varietalDropdownSelection by remember { mutableStateOf(standardVarietals[0]) }
        var expandedVarietalDropdown by remember { mutableStateOf(false) }
        var vintageInput by remember { mutableStateOf("") }
        var rowInput by remember { mutableStateOf(prefilledRow.toString()) }
        var colInput by remember { mutableStateOf(prefilledCol.toString()) }
        var priceInput by remember { mutableStateOf("") }
        var isAgingInput by remember { mutableStateOf(false) }

        // Camera states
        var capturedPhotoUri by remember { mutableStateOf<String?>(null) }
        var showCameraViewfinder by remember { mutableStateOf(false) }
        var selectedPresetStyle by remember { mutableStateOf("preset_cabernet") }

        var validationError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    "Store New Bottle",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 4.dp)
                ) {
                    // Winery Input
                    OutlinedTextField(
                        value = wineryInput,
                        onValueChange = { wineryInput = it },
                        label = { Text("Winery Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_winery_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    // Varietal Input Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = varietalDropdownSelection,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Choose Varietal") },
                            trailingIcon = {
                                IconButton(onClick = { expandedVarietalDropdown = true }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Expand Varietals"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedVarietalDropdown = true }
                                .testTag("add_varietal_dropdown"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        DropdownMenu(
                            expanded = expandedVarietalDropdown,
                            onDismissRequest = { expandedVarietalDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            standardVarietals.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        varietalDropdownSelection = item
                                        expandedVarietalDropdown = false
                                        // Auto preset style selection
                                        selectedPresetStyle = when {
                                            item.contains("Cabernet", ignoreCase = true) || item.contains("Shiraz", ignoreCase = true) -> "preset_cabernet"
                                            item.contains("Chardonnay", ignoreCase = true) || item.contains("Sauvignon", ignoreCase = true) -> "preset_chardonnay"
                                            item.contains("Rose", ignoreCase = true) -> "preset_rose"
                                            item.contains("Champagne", ignoreCase = true) -> "preset_champagne"
                                            else -> "preset_cabernet"
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Custom input field displays only if "Other" is selected in dropdown
                    if (varietalDropdownSelection == "Other") {
                        OutlinedTextField(
                            value = varietalInput,
                            onValueChange = { varietalInput = it },
                            label = { Text("Specify Custom Varietal") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("add_varietal_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }

                    // Vintage Input
                    OutlinedTextField(
                        value = vintageInput,
                        onValueChange = { vintageInput = it },
                        label = { Text("Vintage Year") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_vintage_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    // Grid Layout row-col coordinate
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = rowInput,
                            onValueChange = { rowInput = it },
                            label = { Text("Row (1-$totalRows)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("add_row_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        OutlinedTextField(
                            value = colInput,
                            onValueChange = { colInput = it },
                            label = { Text("Col (1-$totalCols)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("add_col_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }

                    // Price Input Field (Optional)
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        label = { Text("Price ($/Bottle, Optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_price_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    // Aging Toggle Selection Group
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .clickable { isAgingInput = !isAgingInput }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Update,
                                contentDescription = "Aging Flag Icon",
                                tint = if (isAgingInput) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Column {
                                Text(
                                    text = "Aging in Cellar",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isAgingInput) "Currently aging" else "Ready to drink / enjoy",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        Switch(
                            checked = isAgingInput,
                            onCheckedChange = { isAgingInput = it },
                            modifier = Modifier.testTag("add_aging_switch")
                        )
                    }

                    // CAMERA AND DESIGN CUSTOMIZER CONTAINER BLOCK
                    Text(
                        text = "BOTTLE PHOTOGRAPH",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Thumbnail Preview
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val thumbBitmap = remember(capturedPhotoUri) {
                                if (capturedPhotoUri != null && !capturedPhotoUri!!.startsWith("preset_")) {
                                    try {
                                        BitmapFactory.decodeFile(capturedPhotoUri)?.asImageBitmap()
                                    } catch (e: Exception) {
                                        null
                                    }
                                } else {
                                    null
                                }
                            }

                            if (thumbBitmap != null) {
                                Image(
                                    bitmap = thumbBitmap,
                                    contentDescription = "Capture preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                WineBottleVector(styleKey = capturedPhotoUri ?: selectedPresetStyle)
                            }
                        }

                        // Physical Camera Launcher / Virtual DSLR Viewfinder Selector
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            val cameraLauncher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.TakePicturePreview()
                            ) { bitmap: Bitmap? ->
                                if (bitmap != null) {
                                    val savedPath = saveBitmapToCache(context, bitmap)
                                    if (savedPath != null) {
                                        capturedPhotoUri = savedPath
                                    }
                                }
                            }

                            // True hardware camera option
                            Button(
                                onClick = {
                                    try {
                                        cameraLauncher.launch(null)
                                    } catch (e: Exception) {
                                        showCameraViewfinder = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                    contentColor = MaterialTheme.colorScheme.secondary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.fillMaxWidth().height(28.dp)
                            ) {
                                Icon(Icons.Rounded.PhotoCamera, contentDescription = "Camera", modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Real Camera Photo", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                            }

                            // Dynamic Shutter/Color customizer
                            OutlinedButton(
                                onClick = { showCameraViewfinder = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth().height(28.dp)
                            ) {
                                Icon(Icons.Rounded.Brush, contentDescription = "Design", modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Aesthetic DSLR Studio", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                            }
                        }
                    }

                    validationError?.let { err ->
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val rowParsed = rowInput.toIntOrNull()
                        val colParsed = colInput.toIntOrNull()
                        val actualVarietalToSave = if (varietalDropdownSelection == "Other") varietalInput else varietalDropdownSelection

                        when {
                            wineryInput.isBlank() -> {
                                validationError = "Winery name cannot be blank."
                            }
                            actualVarietalToSave.isBlank() -> {
                                validationError = "Varietal cannot be blank."
                            }
                            vintageInput.isBlank() -> {
                                validationError = "Vintage year cannot be blank."
                            }
                            rowParsed == null || rowParsed !in 1..totalRows -> {
                                validationError = "Row coordinate must be a valid number between 1 and $totalRows."
                            }
                            colParsed == null || colParsed !in 1..totalCols -> {
                                validationError = "Column coordinate must be a valid number between 1 and $totalCols."
                            }
                            else -> {
                                val parsedPrice = priceInput.toDoubleOrNull()
                                viewModel.addBottle(
                                    winery = wineryInput,
                                    varietal = actualVarietalToSave,
                                    vintage = vintageInput,
                                    row = rowParsed,
                                    col = colParsed,
                                    photoUri = capturedPhotoUri ?: selectedPresetStyle,
                                    price = parsedPrice,
                                    isAging = isAgingInput
                                )
                                showAddDialog = false
                            }
                        }
                    },
                    modifier = Modifier.testTag("add_bottle_save")
                ) {
                    Text("Store Bottle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )

        // DIALOG 1.1: VIRTUAL DSLR VIEW-FINDER SIMULATOR overlay
        if (showCameraViewfinder) {
            AlertDialog(
                onDismissRequest = { showCameraViewfinder = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Viewfinder Shutter", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        IconButton(onClick = { showCameraViewfinder = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.5f))
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Shutter screen simulator
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black)
                                .border(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            // Focus scanning grids lines drawn dynamically
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height

                                // Draw vertical/horizontal grid helper lines
                                drawLine(
                                    color = Color.White.copy(alpha = 0.12f),
                                    start = Offset(w / 3, 0f),
                                    end = Offset(w / 3, h),
                                    strokeWidth = 1.dp.toPx()
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.12f),
                                    start = Offset(2 * w / 3, 0f),
                                    end = Offset(2 * w / 3, h),
                                    strokeWidth = 1.dp.toPx()
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.12f),
                                    start = Offset(0f, h / 3),
                                    end = Offset(w, h / 3),
                                    strokeWidth = 1.dp.toPx()
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.12f),
                                    start = Offset(0f, 2 * h / 3),
                                    end = Offset(w, 2 * h / 3),
                                    strokeWidth = 1.dp.toPx()
                                )

                                // Auto focusing green brackets in center
                                val pad = 24.dp.toPx()
                                drawLine(
                                    color = Color(0xFF10B981),
                                    start = Offset(w / 2 - pad, h / 2 - pad),
                                    end = Offset(w / 2 - pad + 12.dp.toPx(), h / 2 - pad),
                                    strokeWidth = 2.dp.toPx()
                                )
                                drawLine(
                                    color = Color(0xFF10B981),
                                    start = Offset(w / 2 - pad, h / 2 - pad),
                                    end = Offset(w / 2 - pad, h / 2 - pad + 12.dp.toPx()),
                                    strokeWidth = 2.dp.toPx()
                                )

                                drawLine(
                                    color = Color(0xFF10B981),
                                    start = Offset(w / 2 + pad, h / 2 + pad),
                                    end = Offset(w / 2 + pad - 12.dp.toPx(), h / 2 + pad),
                                    strokeWidth = 2.dp.toPx()
                                )
                                drawLine(
                                    color = Color(0xFF10B981),
                                    start = Offset(w / 2 + pad, h / 2 + pad),
                                    end = Offset(w / 2 + pad, h / 2 + pad - 12.dp.toPx()),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }

                            // Central dynamic bottle preview
                            Box(
                                modifier = Modifier
                                    .size(width = 44.dp, height = 110.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                WineBottleVector(styleKey = selectedPresetStyle)
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(Color.Red.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("LIVE STABLE VIEW", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }

                        // Presets switcher row label
                        Text(
                            text = "SELECT LIQUID VARIANT FLAVOR PROFILE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                            color = MaterialTheme.colorScheme.secondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "preset_cabernet" to "Red",
                                "preset_chardonnay" to "White",
                                "preset_rose" to "Rosé",
                                "preset_champagne" to "Amber"
                            ).forEach { (key, label) ->
                                val active = selectedPresetStyle == key
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.background)
                                        .border(
                                            width = if (active) 1.5.dp else 1.dp,
                                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { selectedPresetStyle = key }
                                        .padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 14.dp, height = 32.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                    ) {
                                        WineBottleVector(styleKey = key)
                                    }
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            capturedPhotoUri = selectedPresetStyle
                            showCameraViewfinder = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(56.dp)
                            .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        // Elegant single big shutter center
                    }
                },
                containerColor = Color(0xFF141414),
                shape = RoundedCornerShape(24.dp)
            )
        }
    }

    // DIALOG 2: BOTTLE DETAIL SCREEN & CONTAINER
    if (showDetailDialog && selectedBottleForDetail != null) {
        val bottle = selectedBottleForDetail!!
        AlertDialog(
            onDismissRequest = { showDetailDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(width = 70.dp, height = 150.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val detailBitmap = remember(bottle.photoUri) {
                        if (bottle.photoUri != null && !bottle.photoUri.startsWith("preset_")) {
                            try {
                                BitmapFactory.decodeFile(bottle.photoUri)?.asImageBitmap()
                            } catch (e: Exception) {
                                null
                            }
                        } else {
                            null
                        }
                    }

                    if (detailBitmap != null) {
                        Image(
                            bitmap = detailBitmap,
                            contentDescription = "Stored wine bottle picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val preset = bottle.photoUri ?: when {
                            bottle.varietal.contains("Cabernet", ignoreCase = true) || bottle.varietal.contains("Margaux", ignoreCase = true) || bottle.varietal.contains("Shiraz", ignoreCase = true) -> "preset_cabernet"
                            bottle.varietal.contains("Chardonnay", ignoreCase = true) || bottle.varietal.contains("Sauvignon", ignoreCase = true) || bottle.varietal.contains("White", ignoreCase = true) -> "preset_chardonnay"
                            bottle.varietal.contains("Rose", ignoreCase = true) -> "preset_rose"
                            bottle.varietal.contains("Champagne", ignoreCase = true) -> "preset_champagne"
                            else -> "preset_cabernet"
                        }
                        WineBottleVector(styleKey = preset)
                    }
                }
            },
            title = {
                Text(
                    text = bottle.wineryName,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${bottle.vintage}  •  ${bottle.varietal}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cellar Rack Coordinate:", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                        Text(
                            "Row ${bottle.gridRow}  |  Col ${bottle.gridCol}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Shelf Cell ID:", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                        Text(
                            text = "R${bottle.gridRow}C${bottle.gridCol}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Price per Bottle:", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                        Text(
                            text = if (bottle.price != null && bottle.price > 0.0) "$${String.format("%.2f", bottle.price)}" else "Not Recorded",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Aging Status:", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                        Text(
                            text = if (bottle.isAging) "Aging in Cellar" else "Ready to drink",
                            fontWeight = FontWeight.Bold,
                            color = if (bottle.isAging) MaterialTheme.colorScheme.primary else Color(0xFF10B981)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteBottle(bottle)
                        showDetailDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    modifier = Modifier.testTag("delete_bottle_button")
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Drink")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Drink / Remove Bottle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDetailDialog = false }) {
                    Text("Back to Cellar", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
