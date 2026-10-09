package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.WineCellarViewModel
import com.example.data.CellarLayout
import com.example.data.WineBottle
import java.util.Locale

@Composable
fun BottleDetailDialog(
    viewModel: WineCellarViewModel,
    bottle: WineBottle,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    var showMove by remember(bottle.id) { mutableStateOf(false) }
    var rowText by remember(bottle.id) { mutableStateOf(bottle.gridRow.toString()) }
    var colText by remember(bottle.id) { mutableStateOf(bottle.gridCol.toString()) }
    var moveError by remember(bottle.id) { mutableStateOf<String?>(null) }
    var confirmDelete by remember(bottle.id) { mutableStateOf(false) }

    // Full-size photo is loaded on demand (the list only carries a small thumbnail).
    var fullPhoto by remember(bottle.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(bottle.id, bottle.thumb) {
        fullPhoto = if (bottle.hasPhoto) viewModel.loadFullPhoto(bottle.id) else null
    }

    val targetRow = rowText.toIntOrNull()
    val targetCol = colText.toIntOrNull()
    val occupant = if (targetRow != null && targetCol != null) viewModel.bottleAt(targetRow, targetCol, exceptId = bottle.id) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(width = 90.dp, height = 150.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                val photo = fullPhoto
                if (photo != null) {
                    Image(
                        bitmap = photo,
                        contentDescription = "Photo of ${bottle.wineryName}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    BottleImage(bottle = bottle)
                }
            }
        },
        title = {
            Text(
                text = bottle.wineryName,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${bottle.vintage.ifBlank { "NV" }}  •  ${bottle.varietal}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                bottle.classification?.takeIf { it.isNotBlank() }?.let { DetailRow("Classification", it) }
                DetailRow("Rack position", "Row ${bottle.gridRow}  |  Col ${bottle.gridCol}")
                DetailRow(
                    "Price per bottle",
                    bottle.price?.takeIf { it > 0.0 }?.let { "$" + String.format(Locale.getDefault(), "%.2f", it) } ?: "Not recorded"
                )
                DetailRow(
                    "Status",
                    if (bottle.isAging) "Aging in cellar" else "Ready to drink",
                    valueColor = if (bottle.isAging) MaterialTheme.colorScheme.primary else Color(0xFF10B981)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                Button(
                    onClick = onEdit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("detail_edit"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit wine details")
                }
                Button(
                    onClick = {
                        viewModel.duplicateToNextFreeSlot(bottle)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add another of these (next free slot)")
                }

                // Relocate / swap
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showMove = !showMove }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                            Text(
                                "Relocate / swap slot",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Icon(
                            if (showMove) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = if (showMove) "Hide" else "Show"
                        )
                    }
                    if (showMove) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = rowText,
                                onValueChange = {
                                    rowText = it.filter { c -> c.isDigit() }.take(2)
                                    moveError = null
                                },
                                label = { Text("Row") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = colText,
                                onValueChange = {
                                    colText = it.filter { c -> c.isDigit() }.take(2)
                                    moveError = null
                                },
                                label = { Text("Col") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (occupant != null) {
                            Text(
                                "That slot holds ${occupant.wineryName} ${occupant.vintage}. They'll swap places.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Button(
                            onClick = {
                                val r = targetRow
                                val c = targetCol
                                if (r == null || c == null || r !in 1..CellarLayout.ROWS || c !in 1..CellarLayout.COLS) {
                                    moveError = "Row must be 1-${CellarLayout.ROWS} and column 1-${CellarLayout.COLS}."
                                } else {
                                    viewModel.moveBottle(bottle, r, c)
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        ) {
                            Text(if (occupant != null) "Swap bottles" else "Move here")
                        }
                        moveError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                modifier = Modifier.testTag("detail_delete")
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Drink / Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp)
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove this bottle?") },
            text = {
                Text(
                    "${bottle.wineryName} ${bottle.vintage} will be removed from the cellar on every phone. " +
                        "This can't be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteBottle(bottle)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep it") }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("$label:", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        Spacer(Modifier.width(12.dp))
        Text(value, fontWeight = FontWeight.Bold, color = valueColor, textAlign = TextAlign.End)
    }
}
