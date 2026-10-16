package com.houseofvino.ui.feature.place

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
import com.houseofvino.core.model.LocationKind
import com.houseofvino.data.repo.InvalidInputException
import com.houseofvino.data.repo.LocationCycleException
import com.houseofvino.data.repo.LocationDetails
import com.houseofvino.data.repo.LocationInput
import com.houseofvino.ui.app.AppGraph
import com.houseofvino.ui.app.LocalGraph
import com.houseofvino.ui.components.Chip
import com.houseofvino.ui.components.ChoiceChips
import com.houseofvino.ui.components.HovTextField
import com.houseofvino.ui.components.LoadingRows
import com.houseofvino.ui.components.PrimaryButton
import com.houseofvino.ui.components.ScreenHeader
import com.houseofvino.ui.feature.wine.PlaceChips
import com.houseofvino.ui.nav.LocalNavigator
import com.houseofvino.ui.nav.Route
import com.houseofvino.ui.theme.Hov
import com.houseofvino.ui.theme.Space
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditPlaceState(
    val loaded: Boolean = false,
    val name: String = "",
    val kind: LocationKind = LocationKind.RACK,
    val parentId: String? = null,
    val hasGrid: Boolean = false,
    val rows: String = "",
    val cols: String = "",
    val notes: String = "",
    val places: List<LocationDetails> = emptyList(),
    val errors: Map<String, String> = emptyMap(),
    val savedId: String? = null,
)

class EditPlaceViewModel(private val graph: AppGraph, private val id: String?, parentId: String?) : ViewModel() {
    private val _state = MutableStateFlow(EditPlaceState(parentId = parentId))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val (places, current) = graph.db { s -> graph.locations.all(s) to id?.let { graph.locations.location(it) } }
            _state.update {
                if (current == null) {
                    it.copy(loaded = true, places = places, kind = if (parentId == null) LocationKind.SITE else LocationKind.RACK)
                } else {
                    it.copy(
                        loaded = true, places = places.filter { p -> p.id != current.id }, name = current.name, kind = current.kind,
                        parentId = current.parentId, hasGrid = current.hasGrid, rows = current.gridRows?.toString().orEmpty(),
                        cols = current.gridCols?.toString().orEmpty(), notes = current.notes.orEmpty(),
                    )
                }
            }
        }
    }

    fun edit(change: (EditPlaceState) -> EditPlaceState) = _state.update { change(it).copy(errors = emptyMap()) }

    fun save() {
        val s = _state.value
        val rows = s.rows.toIntOrNull()
        val cols = s.cols.toIntOrNull()
        if (s.hasGrid && (rows == null || cols == null)) {
            _state.update { it.copy(errors = mapOf("grid" to "Enter rows and columns")) }
            return
        }
        val input = LocationInput(s.name, s.kind, s.parentId, if (s.hasGrid) rows else null, if (s.hasGrid) cols else null, s.notes)
        viewModelScope.launch {
            try {
                val saved = graph.db { session ->
                    if (id == null) graph.locations.create(session, input) else id.also { graph.locations.update(session, it, input) }
                }
                _state.update { it.copy(savedId = saved) }
            } catch (e: InvalidInputException) {
                _state.update { it.copy(errors = mapOf(e.field to (e.message ?: "Check this"))) }
            } catch (e: LocationCycleException) {
                _state.update { it.copy(errors = mapOf("parent" to (e.message ?: "Choose another place"))) }
            }
        }
    }
}

@Composable
fun EditPlaceScreen(id: String?, parentId: String?) {
    val graph = LocalGraph.current
    val nav = LocalNavigator.current
    val vm = viewModel(key = "edit-place-$id-$parentId") { EditPlaceViewModel(graph, id, parentId) }
    val s by vm.state.collectAsState()
    LaunchedEffect(s.savedId) {
        s.savedId?.let { if (id == null) nav.replace(Route.Place(it)) else nav.pop() }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (id == null) "New place" else "Edit place", onBack = { nav.pop() })
        if (!s.loaded) {
            LoadingRows(3)
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.ime).padding(horizontal = Space.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            HovTextField("Name", s.name, { v -> vm.edit { it.copy(name = v) } }, placeholder = "e.g. Rack A, Kitchen fridge", error = s.errors["name"], capitalization = KeyboardCapitalization.Words)
            Column {
                Text("Kind", style = Hov.type.label, color = Hov.colors.inkMuted)
                Spacer(Modifier.height(6.dp))
                ChoiceChips(LocationKind.entries, s.kind, { it.label }, { k -> vm.edit { it.copy(kind = k) } })
            }
            Column {
                Row { Chip("Top level", selected = s.parentId == null, onClick = { vm.edit { it.copy(parentId = null) } }) }
                Spacer(Modifier.height(Space.s))
                PlaceChips(s.places, s.parentId, s.errors["parent"]) { p -> vm.edit { it.copy(parentId = p) } }
            }
            Column {
                Text("Exact bottle positions", style = Hov.type.label, color = Hov.colors.inkMuted)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    Chip("No grid", selected = !s.hasGrid, onClick = { vm.edit { it.copy(hasGrid = false) } })
                    Chip("Grid of slots", selected = s.hasGrid, onClick = { vm.edit { it.copy(hasGrid = true) } })
                }
            }
            if (s.hasGrid) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    HovTextField("Rows", s.rows, { v -> vm.edit { it.copy(rows = v.filter(Char::isDigit).take(3)) } }, Modifier.weight(1f), keyboardType = KeyboardType.Number, error = s.errors["grid"])
                    HovTextField("Slots per row", s.cols, { v -> vm.edit { it.copy(cols = v.filter(Char::isDigit).take(3)) } }, Modifier.weight(1f), keyboardType = KeyboardType.Number)
                }
            }
            HovTextField("Notes", s.notes, { v -> vm.edit { it.copy(notes = v) } }, singleLine = false, minLines = 2)
        }
        PrimaryButton(
            if (id == null) "Add place" else "Save changes", vm::save,
            modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = Space.gutter, vertical = Space.m),
        )
    }
}
