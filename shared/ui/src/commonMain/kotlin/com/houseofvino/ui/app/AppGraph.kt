package com.houseofvino.ui.app

import androidx.compose.runtime.staticCompositionLocalOf
import com.houseofvino.core.money.Currency
import com.houseofvino.data.AppClock
import com.houseofvino.data.Session
import com.houseofvino.data.SystemClock
import com.houseofvino.data.db.DatabaseFactory
import com.houseofvino.data.repo.HouseholdRepository
import com.houseofvino.data.repo.InventoryRepository
import com.houseofvino.data.repo.LocationRepository
import com.houseofvino.data.repo.SavedSearchRepository
import com.houseofvino.data.repo.Store
import com.houseofvino.data.repo.TagRepository
import com.houseofvino.data.repo.WineRepository
import com.houseofvino.data.search.SearchEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Everything the screens need, wired by hand. One per process; the database opens lazily on the
 * database dispatcher so app start never blocks the main thread.
 */
class AppGraph(
    private val databasePath: String,
    val clock: AppClock = SystemClock,
    val defaultCurrency: Currency = Currency("NZD"),
) {
    /** All database work runs here, one statement at a time (SQLite has one connection). */
    val dbDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val opened by lazy { DatabaseFactory.open(databasePath) }
    val store by lazy { Store(opened.driver, opened.database, clock) }
    val households by lazy { HouseholdRepository(store) }
    val wines by lazy { WineRepository(store) }
    val inventory by lazy { InventoryRepository(store) }
    val locations by lazy { LocationRepository(store) }
    val tags by lazy { TagRepository(store) }
    val search by lazy { SearchEngine(store) }
    val savedSearches by lazy { SavedSearchRepository(store) }

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    /** Opens the database (creating or migrating it) and starts a session. Safe to call repeatedly. */
    suspend fun start(): Session = withContext(dbDispatcher) {
        _session.value ?: households.startSession(defaultCurrency).also { _session.value = it }
    }

    fun requireSession(): Session = checkNotNull(_session.value) { "App not started" }

    /** Runs a blocking repository call on the database dispatcher. */
    suspend fun <T> db(block: (Session) -> T): T = withContext(dbDispatcher) { block(requireSession()) }

    /** Re-runs [block] when any of [tables] change. */
    fun <T> observe(vararg tables: String, block: (Session) -> T): Flow<T> =
        store.observe(*tables, dispatcher = dbDispatcher) { block(requireSession()) }

    companion object {
        /** Tables whose changes affect what the cellar shows. */
        val CELLAR_TABLES = arrayOf("wine", "wine_vintage", "lot", "stock", "inventory_movement", "location", "tag_link", "tag", "tasting_note", "search_fts", "bottle_position")
    }
}

val LocalGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph not provided") }
