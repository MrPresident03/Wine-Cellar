package com.houseofvino.data.repo

import com.houseofvino.core.model.Grapes
import com.houseofvino.core.model.Ids
import com.houseofvino.core.search.SearchQuery
import com.houseofvino.core.search.SearchScope
import com.houseofvino.data.Session
import com.houseofvino.data.sync.SyncTables
import kotlinx.serialization.json.Json

data class SavedSearch(val id: String, val name: String, val query: SearchQuery)

/** Facet values that exist in the user's own cellar, for filter pickers. */
data class FilterOptions(
    val countries: List<String>,
    val regions: List<String>,
    val grapes: List<String>,
    val formatsMl: List<Int>,
    val vintageRange: IntRange?,
)

class SavedSearchRepository(private val store: Store) {
    private val db get() = store.db
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun saved(session: Session): List<SavedSearch> = db.searchQueries.saved(session.householdId).executeAsList().mapNotNull {
        runCatching { SavedSearch(it.id, it.name, json.decodeFromString(SearchQuery.serializer(), it.query_json)) }.getOrNull()
    }

    fun save(session: Session, name: String, query: SearchQuery): String = store.write {
        session.requireWrite()
        val clean = name.cleaned(60)
        requireInput(clean != null, "name") { "Name this search" }
        val id = Ids.new()
        val now = store.now()
        db.searchQueries.insertSaved(id, session.householdId, session.userId, clean!!, json.encodeToString(SearchQuery.serializer(), query), 0, now, now, session.userId)
        store.outbox.afterInsert(SyncTables.SAVED_SEARCH, id, session.householdId)
        id
    }

    fun delete(session: Session, id: String) = store.write {
        session.requireWrite()
        store.outbox.beforeUpdate(SyncTables.SAVED_SEARCH, id)
        db.searchQueries.softDeleteSaved(store.now(), session.userId, id)
    }

    /** Remembers a query on this device only (recent searches are not synced). */
    fun recordRecent(session: Session, scope: SearchScope, text: String) = store.write {
        val t = text.trim()
        if (t.length < 2) return@write
        db.searchQueries.touchRecent(session.householdId, scope.name, t.take(100), store.now())
        db.searchQueries.trimRecent(session.householdId, scope.name, 12)
    }

    fun recent(session: Session, scope: SearchScope, limit: Int = 8): List<String> =
        db.searchQueries.recent(session.householdId, scope.name, limit.toLong()).executeAsList().map { it.query_text }

    fun clearRecent(session: Session, scope: SearchScope) = store.write { db.searchQueries.clearRecent(session.householdId, scope.name) }

    fun filterOptions(session: Session): FilterOptions {
        val rows = db.wineQueries.facetSource(session.householdId).executeAsList()
        val countries = rows.mapNotNull { it.country?.trim()?.takeIf(String::isNotEmpty) }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
        val regions = rows.flatMap { listOfNotNull(it.region, it.sub_region, it.appellation) }.map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
        val grapes = rows.flatMap { Grapes.decode(it.grapes).map { g -> g.name } }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
        val formats = db.lotQueries.formatsInStock(session.householdId).executeAsList().map { it.toInt() }.sorted()
        val years = db.vintageQueries.yearRange(session.householdId).executeAsOne()
        val range = if (years.lo != null && years.hi != null) years.lo.toInt()..years.hi.toInt() else null
        return FilterOptions(countries, regions, grapes, formats, range)
    }
}
