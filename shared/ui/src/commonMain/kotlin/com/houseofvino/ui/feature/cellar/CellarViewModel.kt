package com.houseofvino.ui.feature.cellar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.houseofvino.core.search.SearchQuery
import com.houseofvino.core.search.SearchSort
import com.houseofvino.data.repo.LocationNode
import com.houseofvino.data.search.CellarRow
import com.houseofvino.data.search.SearchPage
import com.houseofvino.ui.app.AppGraph
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class CellarView { WINES, PLACES }

data class CellarState(
    val loading: Boolean = true,
    val page: SearchPage<CellarRow>? = null,
    val places: List<LocationNode> = emptyList(),
    val view: CellarView = CellarView.WINES,
    val sort: SearchSort = SearchSort.PRODUCER,
    val error: String? = null,
    val currentYear: Int = 2026,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CellarViewModel(private val graph: AppGraph) : ViewModel() {
    private val view = MutableStateFlow(CellarView.WINES)
    private val sort = MutableStateFlow(SearchSort.PRODUCER)
    private val limit = MutableStateFlow(PAGE)

    private val rows = combine(sort, limit) { s, l -> s to l }.flatMapLatest { (s, l) ->
        graph.observe(*AppGraph.CELLAR_TABLES) { session -> graph.search.searchCellar(session, SearchQuery(sort = s), limit = l) }
    }

    private val places = graph.observe("location", "stock") { session -> graph.locations.tree(session) }

    val state: StateFlow<CellarState> = combine(rows, places, view, sort) { page, tree, v, s ->
        CellarState(loading = false, page = page, places = tree, view = v, sort = s, currentYear = graph.clock.currentYear())
    }
        .catch { emit(CellarState(loading = false, error = it.message ?: "Couldn't read the cellar")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CellarState())

    fun setView(v: CellarView) {
        view.value = v
    }

    fun setSort(s: SearchSort) {
        sort.value = s
        limit.value = PAGE
    }

    fun loadMore() {
        val page = state.value.page ?: return
        if (page.hasMore && limit.value <= page.items.size) limit.value += PAGE
    }

    companion object {
        const val PAGE = 60
    }
}
