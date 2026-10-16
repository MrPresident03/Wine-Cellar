package com.houseofvino.ui.feature.wine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.houseofvino.core.model.MovementDraft
import com.houseofvino.core.model.MovementError
import com.houseofvino.core.model.MovementKind
import com.houseofvino.data.NotFoundException
import com.houseofvino.data.repo.Holding
import com.houseofvino.data.repo.InvalidInputException
import com.houseofvino.data.repo.LocationDetails
import com.houseofvino.data.repo.LotDetails
import com.houseofvino.data.repo.MovementDetails
import com.houseofvino.data.repo.MovementRecord
import com.houseofvino.data.repo.StockStillHeldException
import com.houseofvino.data.repo.VintageDetails
import com.houseofvino.data.repo.WineDetails
import com.houseofvino.ui.app.AppGraph
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VintagePage(
    val wine: WineDetails,
    val vintage: VintageDetails,
    val holdings: List<Holding>,
    val lots: List<LotDetails>,
    val history: List<MovementRecord>,
    val otherVintages: List<Pair<VintageDetails, Int>>,
    val locations: Map<String, LocationDetails>,
    val reversed: Set<String>,
    val currentYear: Int,
) {
    val bottles: Int get() = holdings.sumOf { it.quantity }
}

sealed interface VintageUi {
    data object Loading : VintageUi
    data object Gone : VintageUi
    data class Ready(val page: VintagePage) : VintageUi
    data class Failed(val message: String) : VintageUi
}

/** One-off messages for the snackbar; [undoMovementId] offers "Undo". */
data class UiEvent(val message: String, val undoMovementId: String? = null)

class VintageViewModel(private val graph: AppGraph, private val vintageId: String) : ViewModel() {

    val state: StateFlow<VintageUi> = graph.observe(*AppGraph.CELLAR_TABLES) { session ->
        val vintage = graph.wines.vintage(vintageId) ?: return@observe VintageUi.Gone
        val wine = graph.wines.wine(vintage.wineId) ?: return@observe VintageUi.Gone
        if (vintage.deleted) return@observe VintageUi.Gone
        val history = graph.inventory.history(vintageId)
        val reversed = history.mapNotNull { it.reversesId }.toSet()
        VintageUi.Ready(
            VintagePage(
                wine = wine,
                vintage = vintage,
                holdings = graph.inventory.holdings(vintageId),
                lots = graph.inventory.lotsOf(vintageId),
                history = history,
                otherVintages = graph.wines.vintagesOf(wine.id).filter { it.id != vintageId }.map { v -> v to graph.inventory.holdings(v.id).sumOf { it.quantity } },
                locations = graph.locations.all(session).associateBy { it.id },
                reversed = reversed,
                currentYear = graph.clock.currentYear(),
            ),
        )
    }
        .catch { emit(VintageUi.Failed(it.message ?: "Couldn't load this wine")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VintageUi.Loading)

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<UiEvent> = _events

    /** Records a movement and reports the outcome in the user's words. */
    fun record(draft: MovementDraft, details: MovementDetails, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val result = graph.db { s -> graph.inventory.record(s, draft, details) }
                val verb = when (draft.kind) {
                    MovementKind.CONSUME -> "Opened"
                    MovementKind.TRANSFER -> "Moved"
                    MovementKind.SELL -> "Sold"
                    MovementKind.GIFT_OUT -> "Gave away"
                    MovementKind.LOSS -> "Recorded"
                    else -> "Recorded"
                }
                val what = "${draft.quantity} bottle${if (draft.quantity == 1) "" else "s"}"
                val extra = if (result.positionsCleared > 0) " ${result.positionsCleared} rack slot${if (result.positionsCleared == 1) " was" else "s were"} cleared." else ""
                _events.emit(UiEvent(if (draft.kind == MovementKind.LOSS) "Recorded $what as broken or spoiled.$extra" else "$verb $what.$extra", result.movementId))
                onDone()
            } catch (e: MovementError) {
                _events.emit(UiEvent(e.message ?: "That couldn't be recorded"))
            } catch (e: InvalidInputException) {
                _events.emit(UiEvent(e.message ?: "Check the details"))
            } catch (e: NotFoundException) {
                _events.emit(UiEvent(e.message ?: "Not found"))
            }
        }
    }

    fun correctCount(lotId: String, locationId: String, actual: Int, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val result = graph.db { s -> graph.inventory.correctCount(s, lotId, locationId, actual) }
                _events.emit(UiEvent(if (result == null) "Count unchanged." else "Count corrected to $actual.", result?.movementId))
                onDone()
            } catch (e: IllegalArgumentException) {
                _events.emit(UiEvent(e.message ?: "Check the count"))
            }
        }
    }

    fun undo(movementId: String) {
        viewModelScope.launch {
            try {
                graph.db { s -> graph.inventory.reverse(s, movementId, note = "Undone") }
                _events.emit(UiEvent("Undone."))
            } catch (e: MovementError) {
                _events.emit(UiEvent(e.message ?: "That can't be undone"))
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                graph.db { s -> graph.wines.deleteVintage(s, vintageId) }
                onDeleted()
            } catch (e: StockStillHeldException) {
                _events.emit(UiEvent(e.message ?: "Bottles remain"))
            }
        }
    }
}
