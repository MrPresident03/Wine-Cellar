package com.houseofvino.ui.feature.wine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.houseofvino.core.model.BottleFormat
import com.houseofvino.core.model.WineStyle
import com.houseofvino.core.money.Currency
import com.houseofvino.ui.LocalSnackbar
import com.houseofvino.ui.app.LocalGraph
import com.houseofvino.ui.components.Chip
import com.houseofvino.ui.components.ChoiceChips
import com.houseofvino.ui.components.HovTextField
import com.houseofvino.ui.components.PickerField
import com.houseofvino.ui.components.PrimaryButton
import com.houseofvino.ui.components.QuantityStepper
import com.houseofvino.ui.components.ScreenHeader
import com.houseofvino.ui.components.SecondaryButton
import com.houseofvino.ui.components.TextAction
import com.houseofvino.ui.format.Fmt
import com.houseofvino.ui.nav.AddWinePrefill
import com.houseofvino.ui.nav.LocalNavigator
import com.houseofvino.ui.nav.Route
import com.houseofvino.ui.theme.Hov
import com.houseofvino.ui.theme.Space
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Fast entry: everything on one screen, sensible defaults (one standard bottle, today, the last
 * place used), producer autocomplete from the user's own cellar, and "Save and add another".
 */
@Composable
fun AddWineScreen(prefill: AddWinePrefill?) {
    val graph = LocalGraph.current
    val nav = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val vm = viewModel { AddWineViewModel(graph, prefill) }
    val state by vm.state.collectAsState()
    val f = state.form
    val e = state.errors

    LaunchedEffect(state.savedVintageId) { state.savedVintageId?.let { nav.replace(Route.Vintage(it)) } }
    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let {
            vm.messageShown()
            snackbar.showSnackbar(it)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Add wine", onBack = { nav.pop() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.ime)
                .padding(horizontal = Space.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            // ---------------------------------------------------------------- identity
            HovTextField(
                label = "Producer",
                value = f.producer,
                onValueChange = vm::editProducer,
                placeholder = "e.g. Felton Road",
                error = e["producer"],
                capitalization = KeyboardCapitalization.Words,
            )
            if (state.producerSuggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    state.producerSuggestions.forEach { p -> Chip(p, selected = false, onClick = { vm.chooseProducer(p) }) }
                }
            }
            if (state.winesOfProducer.isNotEmpty()) {
                Column {
                    Text("Already in your cellar", style = Hov.type.label, color = Hov.colors.inkMuted)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        state.winesOfProducer.forEach { w -> Chip(w.name.ifBlank { w.producer }, selected = false, onClick = { vm.chooseWine(w) }) }
                    }
                }
            }
            if (f.wineId != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Adding to ${f.producer}${if (f.name.isNotBlank()) " ${f.name}" else ""}", style = Hov.type.body, color = Hov.colors.ink, modifier = Modifier.weight(1f))
                    TextAction("New wine", vm::clearChosenWine)
                }
            } else {
                HovTextField(label = "Wine name, optional", value = f.name, onValueChange = { v -> vm.edit { it.copy(name = v) } }, placeholder = "e.g. Block 5 Pinot Noir", capitalization = KeyboardCapitalization.Words)
            }

            HovTextField(
                label = "Vintage",
                value = f.vintage,
                onValueChange = { v -> vm.edit { it.copy(vintage = v.take(10)) } },
                placeholder = "Year, NV, or leave empty",
                error = e["vintage"],
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Characters,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                listOf("NV", (state.currentYear - 2).toString(), (state.currentYear - 1).toString()).forEach { v ->
                    Chip(v, selected = f.vintage.equals(v, ignoreCase = true), onClick = { vm.edit { it.copy(vintage = v) } })
                }
            }

            if (f.wineId == null) {
                Column {
                    Text("Style", style = Hov.type.label, color = Hov.colors.inkMuted)
                    Spacer(Modifier.height(6.dp))
                    ChoiceChips(WineStyle.entries, f.style, { it.label }, { s -> vm.edit { it.copy(style = if (it.style == s) null else s) } })
                }
                HovTextField(label = "Grapes, optional", value = f.grapes, onValueChange = { v -> vm.edit { it.copy(grapes = v) } }, placeholder = "e.g. Cabernet Sauvignon 60%, Merlot", capitalization = KeyboardCapitalization.Words)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    HovTextField(label = "Country", value = f.country, onValueChange = { v -> vm.edit { it.copy(country = v) } }, modifier = Modifier.weight(1f), capitalization = KeyboardCapitalization.Words)
                    HovTextField(label = "Region", value = f.region, onValueChange = { v -> vm.edit { it.copy(region = v) } }, modifier = Modifier.weight(1f), capitalization = KeyboardCapitalization.Words)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    HovTextField(label = "Appellation", value = f.appellation, onValueChange = { v -> vm.edit { it.copy(appellation = v) } }, modifier = Modifier.weight(1f), capitalization = KeyboardCapitalization.Words)
                    HovTextField(label = "Classification", value = f.classification, onValueChange = { v -> vm.edit { it.copy(classification = v) } }, modifier = Modifier.weight(1f), capitalization = KeyboardCapitalization.Words)
                }
            }

            // ---------------------------------------------------------------- bottles
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bottles", style = Hov.type.label, color = Hov.colors.inkMuted)
                    Spacer(Modifier.height(6.dp))
                    QuantityStepper(f.quantity, { q -> vm.edit { it.copy(quantity = q) } })
                }
            }
            Column {
                Text("Size", style = Hov.type.label, color = if (e["format"] != null) Hov.colors.danger else Hov.colors.inkMuted)
                Spacer(Modifier.height(6.dp))
                ChoiceChips(listOf(375, 750, 1500, 3000), if (f.otherFormat.isBlank()) f.formatMl else null, { BottleFormat.label(it) }, { ml -> vm.edit { it.copy(formatMl = ml, otherFormat = "") } })
                Spacer(Modifier.height(Space.s))
                HovTextField(label = "Other size", value = f.otherFormat, onValueChange = { v -> vm.edit { it.copy(otherFormat = v) } }, placeholder = "e.g. 500 ml", error = e["format"])
            }
            PlacePicker(state, f.locationId, e["location"]) { id -> vm.edit { it.copy(locationId = id) } }

            // ---------------------------------------------------------------- purchase
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m), verticalAlignment = Alignment.Bottom) {
                HovTextField(
                    label = "Price per bottle, optional",
                    value = f.price,
                    onValueChange = { v -> vm.edit { it.copy(price = v) } },
                    keyboardType = KeyboardType.Decimal,
                    error = e["price"],
                    modifier = Modifier.weight(1f),
                )
                CurrencyPicker(f.currency) { c -> vm.edit { it.copy(currency = c) } }
            }
            DateField("Bought on", f.acquiredOn) { d -> vm.edit { it.copy(acquiredOn = d) } }
            HovTextField(label = "Bought from, optional", value = f.source, onValueChange = { v -> vm.edit { it.copy(source = v) } }, placeholder = "Merchant, auction, gift…", capitalization = KeyboardCapitalization.Words)

            // ---------------------------------------------------------------- drinking window
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                HovTextField(label = "Drink from", value = f.drinkFrom, onValueChange = { v -> vm.edit { it.copy(drinkFrom = v.filter(Char::isDigit).take(4)) } }, keyboardType = KeyboardType.Number, error = e["drinkFrom"], modifier = Modifier.weight(1f))
                HovTextField(label = "Drink by", value = f.drinkTo, onValueChange = { v -> vm.edit { it.copy(drinkTo = v.filter(Char::isDigit).take(4)) } }, keyboardType = KeyboardType.Number, error = e["drinkTo"] ?: e["window"], modifier = Modifier.weight(1f))
            }

            e["form"]?.let { Text(it, style = Hov.type.body, color = Hov.colors.danger) }
            Spacer(Modifier.height(Space.s))
        }
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = Space.gutter, vertical = Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            SecondaryButton("Save and add another", { vm.save(addAnother = true) }, enabled = !state.saving, modifier = Modifier.weight(1f))
            PrimaryButton("Save", { vm.save(addAnother = false) }, enabled = !state.saving, modifier = Modifier.weight(1f))
        }
    }

    if (state.duplicates.isNotEmpty()) {
        val first = state.duplicates.first()
        AlertDialog(
            onDismissRequest = vm::dismissDuplicates,
            containerColor = Hov.colors.surfaceRaised,
            title = { Text("${first.producer}${if (first.name.isNotBlank()) " ${first.name}" else ""} is already in your cellar", style = Hov.type.heading, color = Hov.colors.ink) },
            text = { Text("Add these bottles to it, or keep them as a separate wine?", style = Hov.type.body, color = Hov.colors.inkMuted) },
            confirmButton = { TextAction("Add to it", { vm.save(addAnother = false, useExistingWineId = first.id) }) },
            dismissButton = { TextAction("Separate wine", { vm.save(addAnother = false, forceNewWine = true) }, color = Hov.colors.ink) },
        )
    }
}

@Composable
fun PlacePicker(state: AddWineState, selected: String?, error: String?, onSelect: (String) -> Unit) {
    PlaceChips(state.places, selected, error, onSelect)
}

@Composable
fun PlaceChips(places: List<com.houseofvino.data.repo.LocationDetails>, selected: String?, error: String?, onSelect: (String) -> Unit) {
    Column {
        Text("Where", style = Hov.type.label, color = if (error != null) Hov.colors.danger else Hov.colors.inkMuted)
        Spacer(Modifier.height(6.dp))
        val byId = places.associateBy { it.id }
        fun path(id: String): String {
            val names = mutableListOf<String>()
            var cur = byId[id]
            var guard = 0
            while (cur != null && guard++ < 16) {
                names += cur.name
                cur = cur.parentId?.let(byId::get)
            }
            return names.reversed().joinToString(" › ")
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            places.sortedBy { path(it.id).lowercase() }.forEach { p -> Chip(path(p.id), selected = p.id == selected, onClick = { onSelect(p.id) }) }
        }
        if (error != null) Text(error, style = Hov.type.caption, color = Hov.colors.danger, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun CurrencyPicker(currency: Currency, onSelect: (Currency) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Chip(currency.code, selected = false, onClick = { open = true })
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = Hov.colors.surfaceRaised) {
            Currency.COMMON.forEach { c ->
                DropdownMenuItem(text = { Text(c.code, style = Hov.type.body, color = Hov.colors.ink) }, onClick = { open = false; onSelect(c) })
            }
        }
    }
}

@Composable
fun DateField(label: String, date: LocalDate?, onDate: (LocalDate?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    PickerField(label, date?.let { Fmt.date(it) }, "Not set", onClick = { open = true })
    if (open) {
        val initial = date?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds()
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextAction("Done", {
                    open = false
                    onDate(picker.selectedDateMillis?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date })
                })
            },
            dismissButton = { TextAction("Clear", { open = false; onDate(null) }, color = Hov.colors.ink) },
        ) {
            DatePicker(picker)
        }
    }
}
