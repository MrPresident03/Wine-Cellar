package com.houseofvino.ui.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.houseofvino.core.search.SearchFilters
import com.houseofvino.core.search.SearchQuery
import com.houseofvino.core.search.SearchScope
import com.houseofvino.core.search.SearchSort
import com.houseofvino.data.repo.FilterOptions
import com.houseofvino.data.repo.LocationDetails
import com.houseofvino.data.repo.SavedSearch
import com.houseofvino.data.repo.TagDetails
import com.houseofvino.data.search.CellarRow
import com.houseofvino.data.search.SearchPage
import com.houseofvino.data.search.TastingRow
import com.houseofvino.data.search.WishlistRow
import com.houseofvino.ui.app.AppGraph
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SearchResults {
    data class Cellar(val page: SearchPage<CellarRow>) : SearchResults
    data class Tastings(val page: SearchPage<TastingRow>) : SearchResults
    data class Wishlist(val page: SearchPage<WishlistRow>) : SearchResults
    data object NoCatalogue : SearchResults
}

data class SearchUi(
    val query: SearchQuery = SearchQuery(),
    val results: SearchResults? = null,
    val recent: List<String> = emptyList(),
    val saved: List<SavedSearch> = emptyList(),
    val grid: Boolean = false,
    val error: String? = null,
    val currentYear: Int = 2026,
)

data class FilterChoices(
    val options: FilterOptions? = null,
    val places: List<LocationDetails> = emptyList(),
    val tags: List<TagDetails> = emptyList(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val graph: AppGraph) : ViewModel() {
    private val query = MutableStateFlow(SearchQuery(sort = SearchSort.RELEVANCE))
    private val limit = MutableStateFlow(PAGE)
    private val grid = MutableStateFlow(false)

    private val results = combine(query.debounce(120), limit) { q, l -> q to l }.flatMapLatest { (q, l) ->
        graph.observe(*AppGraph.CELLAR_TABLES, "wishlist_item", "saved_search", "recent_query") { s ->
            val res: SearchResults = when (q.scope) {
                SearchScope.CELLAR -> SearchResults.Cellar(graph.search.searchCellar(s, q, limit = l))
                SearchScope.TASTINGS -> SearchResults.Tastings(graph.search.searchTastings(s, q, limit = l))
                SearchScope.WISHLIST -> SearchResults.Wishlist(graph.search.searchWishlist(s, q, limit = l))
                SearchScope.DISCOVER -> SearchResults.NoCatalogue
            }
            Triple(res, graph.savedSearches.recent(s, q.scope), graph.savedSearches.saved(s))
        }
    }

    val state: StateFlow<SearchUi> = combine(query, results, grid) { q, (res, recent, saved), g ->
        SearchUi(query = q, results = res, recent = recent, saved = saved, grid = g, currentYear = graph.clock.currentYear())
    }
        .catch { emit(SearchUi(error = it.message ?: "Search failed")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUi())

    private val _choices = MutableStateFlow(FilterChoices())
    val choices: StateFlow<FilterChoices> = _choices

    fun loadChoices() {
        viewModelScope.launch {
            val c = graph.db { s -> FilterChoices(graph.savedSearches.filterOptions(s), graph.locations.all(s), graph.tags.all(s)) }
            _choices.value = c
        }
    }

    fun setText(t: String) = update { it.copy(text = t) }
    fun setScope(s: SearchScope) = update { it.copy(scope = s) }
    fun setSort(s: SearchSort) = update { it.copy(sort = s) }
    fun setFilters(f: SearchFilters) = update { it.copy(filters = f) }
    fun clearFilters() = update { it.copy(filters = SearchFilters()) }
    fun toggleGrid() = grid.update { !it }
    fun apply(q: SearchQuery) = update { q }

    fun loadMore() {
        limit.update { it + PAGE }
    }

    /** Called when the user commits a search (keyboard search action or opening a result). */
    fun remember() {
        val q = query.value
        if (q.text.isBlank()) return
        viewModelScope.launch { graph.db { s -> graph.savedSearches.recordRecent(s, q.scope, q.text) } }
    }

    fun save(name: String) {
        val q = query.value
        viewModelScope.launch { graph.db { s -> graph.savedSearches.save(s, name, q) } }
    }

    fun deleteSaved(id: String) {
        viewModelScope.launch { graph.db { s -> graph.savedSearches.delete(s, id) } }
    }

    fun clearRecent() {
        val scope = query.value.scope
        viewModelScope.launch { graph.db { s -> graph.savedSearches.clearRecent(s, scope) } }
    }

    private fun update(change: (SearchQuery) -> SearchQuery) {
        query.update(change)
        limit.value = PAGE
    }

    companion object {
        const val PAGE = 50
    }
}
