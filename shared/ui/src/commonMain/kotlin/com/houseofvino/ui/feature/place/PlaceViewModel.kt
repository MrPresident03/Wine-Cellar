package com.houseofvino.ui.feature.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.houseofvino.core.model.Vintage
import com.houseofvino.core.model.WineStyle
import com.houseofvino.core.search.SearchFilters
import com.houseofvino.core.search.SearchQuery
import com.houseofvino.core.search.SearchSort
import com.houseofvino.data.repo.InvalidInputException
import com.houseofvino.data.repo.LocationDetails
import com.houseofvino.data.repo.LocationNotEmptyException
import com.houseofvino.data.repo.Slot
import com.houseofvino.data.repo.SlotTakenException
import com.houseofvino.data.search.CellarRow
import com.houseofvino.ui.app.AppGraph
import com.houseofvino.ui.feature.wine.UiEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GridCell(val positionId: String, val lotId: String, val vintageId: String, val vintage: Vintage, val producer: String, val name: String, val style: WineStyle?)

data class Unplaced(val lotId: String, val vintageId: String, val vintage: Vintage, val producer: String, val name: String, val unplaced: Int)

data class PlacePage(
    val place: LocationDetails,
    val path: List<LocationDetails>,
    val children: List<Pair<LocationDetails, Int>>,
    val wines: List<CellarRow>,
    val totalBottles: Int,
    val grid: Map<Pair<Int, Int>, GridCell>,
    val unplaced: List<Unplaced>,
    val currentYear: Int,
)

sealed interface PlaceUi {
    data object Loading : PlaceUi
    data object Gone : PlaceUi
    data class Ready(val page: PlacePage) : PlaceUi
    data class Failed(val message: String) : PlaceUi
}

class PlaceViewModel(private val graph: AppGraph, private val placeId: String) : ViewModel() {
    val state: StateFlow<PlaceUi> = graph.observe(*AppGraph.CELLAR_TABLES) { session ->
        val place = graph.locations.location(placeId) ?: return@observe PlaceUi.Gone
        val db = graph.store.db
        val counts = graph.locations.tree(session)
        fun find(nodes: List<com.houseofvino.data.repo.LocationNode>): com.houseofvino.data.repo.LocationNode? =
            nodes.firstNotNullOfOrNull { if (it.location.id == placeId) it else find(it.children) }
        val node = find(counts)
        val search = graph.search.searchCellar(session, SearchQuery(filters = SearchFilters(locationIds = setOf(placeId)), sort = SearchSort.PRODUCER), limit = 500)
        val grid = if (place.hasGrid) {
            db.positionQueries.inLocationWithWine(placeId).executeAsList().associate {
                (it.row_index.toInt() to it.col_index.toInt()) to GridCell(it.id, it.lot_id, it.vintage_id, Vintage.fromStorage(it.vintage_kind, it.vintage_year), it.producer, it.name, WineStyle.fromCode(it.style))
            }
        } else emptyMap()
        val unplaced = if (place.hasGrid) {
            db.stockQueries.contentsWithWine(placeId).executeAsList().mapNotNull {
                val n = it.qty.toInt() - it.placed.toInt()
                if (n <= 0) null else Unplaced(it.lot_id, it.vintage_id, Vintage.fromStorage(it.vintage_kind, it.vintage_year), it.producer, it.name, n)
            }
        } else emptyList()
        PlaceUi.Ready(
            PlacePage(
                place = place,
                path = graph.locations.path(placeId),
                children = node?.children?.map { it.location to it.bottlesIncludingChildren }.orEmpty(),
                wines = search.items,
                totalBottles = node?.bottlesIncludingChildren ?: search.bottles,
                grid = grid,
                unplaced = unplaced,
                currentYear = graph.clock.currentYear(),
            ),
        )
    }
        .catch { emit(PlaceUi.Failed(it.message ?: "Couldn't load this place")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaceUi.Loading)

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<UiEvent> = _events

    fun placeAt(lotId: String, slot: Slot) {
        viewModelScope.launch {
            try {
                graph.db { s -> graph.inventory.place(s, lotId, placeId, listOf(slot)) }
            } catch (e: SlotTakenException) {
                _events.emit(UiEvent(e.message ?: "That slot is taken"))
            } catch (e: InvalidInputException) {
                _events.emit(UiEvent(e.message ?: "Couldn't place the bottle"))
            }
        }
    }

    fun moveInGrid(positionId: String, to: Slot) {
        viewModelScope.launch {
            try {
                graph.db { s -> graph.inventory.moveWithinGrid(s, positionId, to) }
            } catch (e: SlotTakenException) {
                _events.emit(UiEvent(e.message ?: "That slot is taken"))
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                graph.db { s -> graph.locations.delete(s, placeId) }
                onDeleted()
            } catch (e: LocationNotEmptyException) {
                _events.emit(UiEvent(e.message ?: "Not empty"))
            } catch (e: InvalidInputException) {
                _events.emit(UiEvent(e.message ?: "Couldn't remove"))
            }
        }
    }
}
