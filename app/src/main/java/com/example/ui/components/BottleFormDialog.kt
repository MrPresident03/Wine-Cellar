package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.WineCellarViewModel
import com.example.data.BottleDraft
import com.example.data.CellarLayout
import com.example.data.PRESET_STYLES
import com.example.data.PhotoChange
import com.example.data.PhotoUtils
import com.example.data.STANDARD_VARIETALS
import com.example.data.WineBottle
import com.example.data.presetForVarietal
import kotlinx.coroutines.launch
import java.io.File

/**
 * One form for both adding and editing a bottle (version 1 had two near-identical copies).
 * [initial] = null means "add a new bottle".
 */
@Composable
fun BottleFormDialog(
    viewModel: WineCellarViewModel,
    initial: WineBottle?,
    prefillRow: Int,
    prefillCol: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEdit = initial != null

    val initialVarietalKnown = initial != null && STANDARD_VARIETALS.contains(initial.varietal)
    var winery by remember { mutableStateOf(initial?.wineryName ?: "") }
    var classification by remember { mutableStateOf(initial?.classification ?: "") }
    var varietalChoice by remember {
        mutableStateOf(
            when {
                initial == null -> STANDARD_VARIETALS[0]
                initialVarietalKnown -> initial.varietal
                else -> "Other"
            }
        )
    }
    var customVarietal by remember { mutableStateOf(if (initial != null && !initialVarietalKnown) initial.varietal else "") }
    var varietalMenuOpen by remember { mutableStateOf(false) }
    var vintage by remember { mutableStateOf(initial?.vintage ?: "") }
    var rowText by remember { mutableStateOf((initial?.gridRow ?: prefillRow).toString()) }
    var colText by remember { mutableStateOf((initial?.gridCol ?: prefillCol).toString()) }
    var priceText by remember { mutableStateOf(initial?.price?.let { formatPrice(it) } ?: "") }
    var isAging by remember { mutableStateOf(initial?.isAging ?: false) }
    var preset by remember { mutableStateOf(initial?.styleKey() ?: presetForVarietal(STANDARD_VARIETALS[0])) }
    var presetTouched by remember { mutableStateOf(initial?.preset != null) }

    // Photo state
    var photoChange by remember { mutableStateOf<PhotoChange>(PhotoChange.Keep) }
    var preview by remember {
        mutableStateOf<ImageBitmap?>(
            initial?.let { b -> b.thumb?.let { t -> ThumbnailCache.decode(b.id, t) } }
        )
    }
    var processing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun handlePicked(uri: Uri) {
        processing = true
        error = null
        scope.launch {
            val data = viewModel.processPhoto(uri)
            processing = false
            if (data == null) {
                error = "Couldn't read that photo. Try another one."
            } else {
                photoChange = PhotoChange.Replace(data)
                preview = PhotoUtils.decode(data.thumb)?.asImageBitmap()
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingCameraUri
        if (saved && uri != null) handlePicked(uri)
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) handlePicked(uri)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isEdit) "Edit Wine Details" else "Store New Bottle",
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
                OutlinedTextField(
                    value = winery,
                    onValueChange = { winery = it },
                    label = { Text("Winery Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("form_winery")
                )
                OutlinedTextField(
                    value = classification,
                    onValueChange = { classification = it },
                    label = { Text("Classification (optional, e.g. Reserve)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Varietal picker
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = varietalChoice,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Varietal") },
                        trailingIcon = {
                            IconButton(onClick = { varietalMenuOpen = true }) {
                                Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Choose varietal")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Transparent overlay so tapping anywhere on the field opens the menu
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { varietalMenuOpen = true }
                    )
                    DropdownMenu(
                        expanded = varietalMenuOpen,
                        onDismissRequest = { varietalMenuOpen = false },
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        STANDARD_VARIETALS.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    varietalChoice = item
                                    varietalMenuOpen = false
                                    if (!presetTouched && item != "Other") preset = presetForVarietal(item)
                                }
                            )
                        }
                    }
                }
                if (varietalChoice == "Other") {
                    OutlinedTextField(
                        value = customVarietal,
                        onValueChange = {
                            customVarietal = it
                            if (!presetTouched) preset = presetForVarietal(it)
                        },
                        label = { Text("Type the varietal") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = vintage,
                    onValueChange = { vintage = it },
                    label = { Text("Vintage year (or NV)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = rowText,
                        onValueChange = { rowText = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Row (1-${CellarLayout.ROWS})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = colText,
                        onValueChange = { colText = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Col (1-${CellarLayout.COLS})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Price per bottle (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .clickable { isAging = !isAging }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            Icons.Rounded.Update,
                            contentDescription = null,
                            tint = if (isAging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Column {
                            Text("Aging in cellar", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                if (isAging) "Leave it to age" else "Ready to drink",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Switch(checked = isAging, onCheckedChange = { isAging = it })
                }

                // ---------------------------------------------------------- photo
                Text(
                    "BOTTLE PHOTO",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
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
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 80.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val shown = preview
                        when {
                            processing -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            shown != null -> Image(
                                bitmap = shown,
                                contentDescription = "Bottle photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            else -> WineBottleVector(styleKey = preset)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val uri = newCameraUri(context)
                                    pendingCameraUri = uri
                                    cameraLauncher.launch(uri)
                                } catch (e: ActivityNotFoundException) {
                                    error = "No camera app found on this phone."
                                } catch (e: Exception) {
                                    error = "Couldn't open the camera."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Take photo", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = {
                                try {
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                } catch (e: Exception) {
                                    error = "Couldn't open your photos."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Choose photo", style = MaterialTheme.typography.labelMedium)
                        }
                        if (preview != null) {
                            TextButton(
                                onClick = {
                                    preview = null
                                    photoChange = if (initial?.hasPhoto == true || initial?.thumb != null) PhotoChange.Remove else PhotoChange.Keep
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Remove photo", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                // Bottle colour used when there's no photo
                Text(
                    "Bottle colour (shown when there's no photo)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PRESET_STYLES.forEach { (key, label) ->
                        val active = preset == key
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (active) 1.5.dp else 1.dp,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    preset = key
                                    presetTouched = true
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(modifier = Modifier.size(width = 14.dp, height = 32.dp).clip(RoundedCornerShape(3.dp))) {
                                WineBottleVector(styleKey = key)
                            }
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !processing,
                onClick = {
                    val row = rowText.toIntOrNull()
                    val col = colText.toIntOrNull()
                    val varietal = (if (varietalChoice == "Other") customVarietal else varietalChoice).trim()
                    val priceClean = priceText.trim().replace(",", ".").removePrefix("$")
                    val price = if (priceClean.isEmpty()) null else priceClean.toDoubleOrNull()
                    val occupant = if (row != null && col != null) viewModel.bottleAt(row, col, exceptId = initial?.id) else null
                    when {
                        winery.isBlank() -> error = "Enter the winery name."
                        varietal.isBlank() -> error = "Choose or type a varietal."
                        vintage.isBlank() -> error = "Enter the vintage year (or NV)."
                        row == null || row !in 1..CellarLayout.ROWS -> error = "Row must be between 1 and ${CellarLayout.ROWS}."
                        col == null || col !in 1..CellarLayout.COLS -> error = "Column must be between 1 and ${CellarLayout.COLS}."
                        priceClean.isNotEmpty() && price == null -> error = "Price must be a number, e.g. 34.99"
                        occupant != null -> error =
                            "Row $row, column $col already holds ${occupant.wineryName} ${occupant.vintage}. " +
                                "Pick an empty slot, or open that bottle and use Relocate to swap them."
                        else -> {
                            val draft = BottleDraft(
                                wineryName = winery.trim(),
                                classification = classification.trim().ifBlank { null },
                                varietal = varietal,
                                vintage = vintage.trim(),
                                gridRow = row,
                                gridCol = col,
                                price = price,
                                isAging = isAging,
                                preset = preset
                            )
                            val change = photoChange
                            if (initial == null) {
                                viewModel.addBottle(draft, (change as? PhotoChange.Replace)?.photo)
                            } else {
                                viewModel.updateBottle(initial.id, draft, change)
                            }
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.testTag("form_save")
            ) {
                Text(if (isEdit) "Save Changes" else "Store Bottle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

private fun formatPrice(value: Double): String =
    if (value == Math.floor(value)) value.toLong().toString() else String.format(java.util.Locale.US, "%.2f", value)

/** A file in the app's private folder for the camera app to save into (shared via FileProvider). */
private fun newCameraUri(context: Context): Uri {
    val dir = File(context.filesDir, "camera").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    // Keep the folder from growing: remove older captures.
    dir.listFiles()?.filter { it.name != file.name }?.forEach { it.delete() }
    return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
}
