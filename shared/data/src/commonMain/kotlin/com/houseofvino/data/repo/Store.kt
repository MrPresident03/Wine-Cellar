package com.houseofvino.data.repo

import app.cash.sqldelight.Query
import com.houseofvino.data.AppClock
import com.houseofvino.data.db.BundledSqlDriver
import com.houseofvino.data.db.HouseOfVinoDatabase
import com.houseofvino.data.search.SearchIndexer
import com.houseofvino.data.sync.Outbox
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Shared plumbing for repositories: one database, one clock, the outbox and the search index. */
class Store(
    val driver: BundledSqlDriver,
    val db: HouseOfVinoDatabase,
    val clock: AppClock,
) {
    val outbox = Outbox(driver, db, clock)
    val indexer = SearchIndexer(db)

    /** Runs [block] in one transaction: either every change (data, index, outbox) is saved, or none. */
    fun <T> write(block: () -> T): T = db.transactionWithResult { block() }

    fun now(): Long = clock.nowMillis()

    /**
     * Re-runs [block] whenever any of [tables] changes (through SQLDelight-generated writes) and
     * emits the result. Bursts of changes are conflated into one re-query.
     */
    fun <T> observe(vararg tables: String, dispatcher: CoroutineDispatcher, block: () -> T): Flow<T> =
        callbackFlow {
            val listener = object : Query.Listener {
                override fun queryResultsChanged() {
                    trySend(Unit)
                }
            }
            driver.addListener(*tables, listener = listener)
            send(Unit)
            awaitClose { driver.removeListener(*tables, listener = listener) }
        }
            .conflate()
            .map { block() }
            .flowOn(dispatcher)
}

/** Validation failures meant to be shown to the user next to the field they concern. */
class InvalidInputException(val field: String, message: String) : IllegalArgumentException(message)

internal fun requireInput(condition: Boolean, field: String, message: () -> String) {
    if (!condition) throw InvalidInputException(field, message())
}

internal fun String?.cleaned(maxLength: Int = 500): String? {
    val t = this?.trim()?.replace(Regex("""\s+"""), " ")
    return if (t.isNullOrEmpty()) null else t.take(maxLength)
}

internal fun String?.cleanedMultiline(maxLength: Int = 20_000): String? {
    val t = this?.trim()
    return if (t.isNullOrEmpty()) null else t.take(maxLength)
}
