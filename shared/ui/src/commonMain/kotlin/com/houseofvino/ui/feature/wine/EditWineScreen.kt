package com.houseofvino.ui.feature.wine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.houseofvino.core.model.Grapes
import com.houseofvino.core.model.Vintage
import com.houseofvino.core.model.WindowSource
import com.houseofvino.core.model.WineStyle
import com.houseofvino.core.money.Decimal
import com.houseofvino.core.money.DecimalParser
import com.houseofvino.data.repo.DuplicateVintageException
import com.houseofvino.data.repo.InvalidInputException
import com.houseofvino.data.repo.VintageInput
import com.houseofvino.data.repo.WineInput
import com.houseofvino.ui.app.AppGraph
import com.houseofvino.ui.app.LocalGraph
import com.houseofvino.ui.components.ChoiceChips
import com.houseofvino.ui.components.HovTextField
import com.houseofvino.ui.components.LoadingRows
import com.houseofvino.ui.components.PrimaryButton
import com.houseofvino.ui.components.ScreenHeader
import com.houseofvino.ui.components.SectionHeading
import com.houseofvino.ui.nav.LocalNavigator
import com.houseofvino.ui.theme.Hov
import com.houseofvino.ui.theme.Space
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditWineForm(
    val wineId: String = "",
    val producer: String = "",
    val name: String = "",
    val style: WineStyle? = null,
    val country: String = "",
    val region: String = "",
    val subRegion: String = "",
    val appellation: String = "",
    val classification: String = "",
    val grapes: String = "",
    val wineNotes: String = "",
    val vintage: String = "",
    val alcohol: String = "",
    val drinkFrom: String = "",
    val drinkTo: String = "",
    val drinkSource: WindowSource = WindowSource.USER,
    val drinkNote: String = "",
    val vintageNotes: String = "",
    val otherVintages: Int = 0,
)

data class EditWineState(val loaded: Boolean = false, val form: EditWineForm = EditWineForm(), val errors: Map<String, String> = emptyMap(), val saved: Boolean = false, val saving: Boolean = false)

class EditWineViewModel(private val graph: AppGraph, private val vintageId: String) : ViewModel() {
    private val _state = MutableStateFlow(EditWineState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val form = graph.db {
                val v = graph.wines.vintage(vintageId)!!
                val w = graph.wines.wine(v.wineId)!!
                EditWineForm(
                    wineId = w.id, producer = w.producer, name = w.name, style = w.style, country = w.country.orEmpty(),
                    region = w.region.orEmpty(), subRegion = w.subRegion.orEmpty(), appellation = w.appellation.orEmpty(),
                    classification = w.classification.orEmpty(), grapes = Grapes.display(w.grapes), wineNotes = w.notes.orEmpty(),
                    vintage = when (v.vintage) { Vintage.Unknown -> ""; else -> v.vintage.toString() },
                    alcohol = v.alcoholPct?.toString().orEmpty(), drinkFrom = v.window?.fromYear?.toString().orEmpty(),
                    drinkTo = v.window?.toYear?.toString().orEmpty(), drinkSource = v.window?.source ?: WindowSource.USER,
                    drinkNote = v.window?.note.orEmpty(), vintageNotes = v.notes.orEmpty(),
                    otherVintages = graph.wines.vintagesOf(w.id).size - 1,
                )
            }
            _state.update { it.copy(loaded = true, form = form) }
        }
    }

    fun edit(change: (EditWineForm) -> EditWineForm) = _state.update { it.copy(form = change(it.form), errors = emptyMap()) }

    fun save() {
        val f = _state.value.form
        val year = graph.clock.currentYear()
        val errors = buildMap {
            if (f.producer.isBlank()) put("producer", "Enter the producer")
            if (Vintage.parse(f.vintage, year + 1) == null) put("vintage", "Enter a year like 2016, NV, or leave it empty if unknown")
            if (f.alcohol.isNotBlank() && DecimalParser.normalise(f.alcohol)?.let(Decimal::parseOrNull) == null) put("alcohol", "Enter a number like 13.5")
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                graph.db { s ->
                    graph.store.write {
                        graph.wines.updateWine(
                            s, f.wineId,
                            WineInput(
                                producer = f.producer, name = f.name, style = f.style, country = f.country, region = f.region,
                                subRegion = f.subRegion, appellation = f.appellation, classification = f.classification,
                                grapes = Grapes.parse(f.grapes), notes = f.wineNotes,
                            ),
                        )
                        graph.wines.updateVintage(
                            s, vintageId,
                            VintageInput(
                                vintage = Vintage.parse(f.vintage, year + 1)!!,
                                alcoholPct = f.alcohol.takeIf { it.isNotBlank() }?.let { DecimalParser.normalise(it)?.let(Decimal::parseOrNull) },
                                drinkFrom = f.drinkFrom.toIntOrNull(), drinkTo = f.drinkTo.toIntOrNull(),
                                drinkSource = f.drinkSource, drinkNote = f.drinkNote, notes = f.vintageNotes,
                            ),
                        )
                    }
                }
                _state.update { it.copy(saved = true, saving = false) }
            } catch (e: DuplicateVintageException) {
                _state.update { it.copy(saving = false, errors = mapOf("vintage" to "This wine already has an entry for that vintage. Open that one from the wine page instead.")) }
            } catch (e: InvalidInputException) {
                _state.update { it.copy(saving = false, errors = mapOf(e.field to (e.message ?: "Check this"))) }
            }
        }
    }
}

@Composable
fun EditWineScreen(vintageId: String) {
    val graph = LocalGraph.current
    val nav = LocalNavigator.current
    val vm = viewModel(key = "edit-$vintageId") { EditWineViewModel(graph, vintageId) }
    val state by vm.state.collectAsState()
    LaunchedEffect(state.saved) { if (state.saved) nav.pop() }
    val f = state.form
    val e = state.errors
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Edit wine", onBack = { nav.pop() })
        if (!state.loaded) {
            LoadingRows(3)
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.ime).padding(horizontal = Space.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            Text(
                if (f.otherVintages > 0) "These details are shared by all ${f.otherVintages + 1} vintages of this wine." else "These details describe the wine across all its vintages.",
                style = Hov.type.detail, color = Hov.colors.inkMuted,
            )
            HovTextField("Producer", f.producer, { v -> vm.edit { it.copy(producer = v) } }, error = e["producer"], capitalization = KeyboardCapitalization.Words)
            HovTextField("Wine name", f.name, { v -> vm.edit { it.copy(name = v) } }, capitalization = KeyboardCapitalization.Words)
            Column {
                Text("Style", style = Hov.type.label, color = Hov.colors.inkMuted)
                Spacer(Modifier.height(6.dp))
                ChoiceChips(WineStyle.entries, f.style, { it.label }, { s -> vm.edit { it.copy(style = if (it.style == s) null else s) } })
            }
            HovTextField("Grapes", f.grapes, { v -> vm.edit { it.copy(grapes = v) } }, placeholder = "e.g. Cabernet Sauvignon 60%, Merlot", capitalization = KeyboardCapitalization.Words)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                HovTextField("Country", f.country, { v -> vm.edit { it.copy(country = v) } }, Modifier.weight(1f), capitalization = KeyboardCapitalization.Words)
                HovTextField("Region", f.region, { v -> vm.edit { it.copy(region = v) } }, Modifier.weight(1f), capitalization = KeyboardCapitalization.Words)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                HovTextField("Sub-region", f.subRegion, { v -> vm.edit { it.copy(subRegion = v) } }, Modifier.weight(1f), capitalization = KeyboardCapitalization.Words)
                HovTextField("Appellation", f.appellation, { v -> vm.edit { it.copy(appellation = v) } }, Modifier.weight(1f), capitalization = KeyboardCapitalization.Words)
            }
            HovTextField("Classification", f.classification, { v -> vm.edit { it.copy(classification = v) } }, capitalization = KeyboardCapitalization.Words)
            HovTextField("Notes about the wine", f.wineNotes, { v -> vm.edit { it.copy(wineNotes = v) } }, singleLine = false, minLines = 3)

            SectionHeading("This vintage", Modifier.padding(start = 0.dp))
            HovTextField("Vintage", f.vintage, { v -> vm.edit { it.copy(vintage = v.take(10)) } }, error = e["vintage"], placeholder = "Year, NV, or empty if unknown", capitalization = KeyboardCapitalization.Characters)
            HovTextField("Alcohol %", f.alcohol, { v -> vm.edit { it.copy(alcohol = v) } }, keyboardType = KeyboardType.Decimal, error = e["alcohol"])
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                HovTextField("Drink from", f.drinkFrom, { v -> vm.edit { it.copy(drinkFrom = v.filter(Char::isDigit).take(4)) } }, Modifier.weight(1f), keyboardType = KeyboardType.Number, error = e["window"])
                HovTextField("Drink by", f.drinkTo, { v -> vm.edit { it.copy(drinkTo = v.filter(Char::isDigit).take(4)) } }, Modifier.weight(1f), keyboardType = KeyboardType.Number)
            }
            Column {
                Text("Window source", style = Hov.type.label, color = Hov.colors.inkMuted)
                Spacer(Modifier.height(6.dp))
                ChoiceChips(WindowSource.entries, f.drinkSource, { it.label }, { s -> vm.edit { it.copy(drinkSource = s) } })
            }
            HovTextField("Window note", f.drinkNote, { v -> vm.edit { it.copy(drinkNote = v) } }, placeholder = "e.g. Jancis Robinson, March 2026")
            HovTextField("Notes about this vintage", f.vintageNotes, { v -> vm.edit { it.copy(vintageNotes = v) } }, singleLine = false, minLines = 3)
            e["form"]?.let { Text(it, style = Hov.type.body, color = Hov.colors.danger) }
            Spacer(Modifier.height(Space.s))
        }
        PrimaryButton(
            "Save changes", vm::save, enabled = !state.saving,
            modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = Space.gutter, vertical = Space.m),
        )
    }
}
