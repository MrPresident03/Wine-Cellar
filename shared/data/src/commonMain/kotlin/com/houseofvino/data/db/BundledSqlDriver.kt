package com.houseofvino.data.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

/**
 * A SQLDelight driver on top of AndroidX's bundled SQLite. The same SQLite build (with FTS5 and
 * JSON1) ships inside the app on Android and iOS and is used by the JVM tests, so full-text search
 * behaves identically everywhere instead of depending on each phone's system SQLite.
 *
 * One connection is shared and guarded by a re-entrant lock. A transaction holds the lock from
 * BEGIN to COMMIT/ROLLBACK, so other threads wait instead of interleaving statements.
 */
class BundledSqlDriver(
    fileName: String,
    private val statementCacheSize: Int = 64,
) : SqlDriver {

    private val lock: ReentrantLock = reentrantLock()
    private val connection: SQLiteConnection = BundledSQLiteDriver().open(fileName)
    private val statements = LinkedHashMap<Int, SQLiteStatement>()
    private val listeners = mutableMapOf<String, MutableSet<Query.Listener>>()
    private var transaction: Transaction? = null
    private var closed = false

    init {
        lock.withLock {
            // WAL keeps reads working during writes and survives crashes better; the pragmas return rows.
            if (fileName != ":memory:") query("PRAGMA journal_mode = WAL")
            connection.execute("PRAGMA foreign_keys = ON")
            connection.execute("PRAGMA busy_timeout = 5000")
            connection.execute("PRAGMA synchronous = NORMAL")
        }
    }

    // ------------------------------------------------------------------ SqlDriver

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> = lock.withLock {
        checkOpen()
        withStatement(identifier, sql) { stmt ->
            if (binders != null) Binder(stmt).binders()
            while (stmt.step()) { /* drain RETURNING rows, if any */ }
        }
        QueryResult.Value(changes())
    }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> = lock.withLock {
        checkOpen()
        withStatement(identifier, sql) { stmt ->
            if (binders != null) Binder(stmt).binders()
            mapper(Cursor(stmt))
        }
    }

    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        lock.lock()
        try {
            checkOpen()
            val enclosing = transaction
            if (enclosing == null) connection.execute("BEGIN IMMEDIATE")
            val t = Transaction(enclosing)
            transaction = t
            return QueryResult.Value(t)
        } catch (e: Throwable) {
            lock.unlock()
            throw e
        }
    }

    override fun currentTransaction(): Transacter.Transaction? = lock.withLock { transaction }

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
        lock.withLock { queryKeys.forEach { listeners.getOrPut(it) { linkedSetOf() }.add(listener) } }
    }

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
        lock.withLock { queryKeys.forEach { listeners[it]?.remove(listener) } }
    }

    override fun notifyListeners(vararg queryKeys: String) {
        val toNotify = lock.withLock { queryKeys.flatMapTo(linkedSetOf()) { listeners[it].orEmpty() } }
        toNotify.forEach { it.queryResultsChanged() }
    }

    override fun close() {
        lock.withLock {
            if (closed) return
            statements.values.forEach { runCatching { it.close() } }
            statements.clear()
            connection.close()
            closed = true
        }
    }

    // ------------------------------------------------------------------ raw helpers (not part of SqlDriver)

    /** Runs a statement that may return rows (e.g. PRAGMA) and discards them. */
    fun query(sql: String) = lock.withLock {
        connection.prepare(sql).use { while (it.step()) Unit }
    }

    /** Reads `PRAGMA user_version`, which this app uses as the schema version. */
    fun userVersion(): Long = lock.withLock {
        connection.prepare("PRAGMA user_version").use { if (it.step()) it.getLong(0) else 0L }
    }

    fun setUserVersion(version: Long) = lock.withLock { connection.execute("PRAGMA user_version = $version") }

    // ------------------------------------------------------------------ internals

    private inner class Transaction(override val enclosingTransaction: Transacter.Transaction?) : Transacter.Transaction() {
        override fun endTransaction(successful: Boolean): QueryResult<Unit> {
            try {
                if (enclosingTransaction == null) {
                    connection.execute(if (successful) "COMMIT" else "ROLLBACK")
                }
                transaction = enclosingTransaction as Transaction?
            } finally {
                lock.unlock()
            }
            return QueryResult.Unit
        }
    }

    private inline fun <T> withStatement(identifier: Int?, sql: String, block: (SQLiteStatement) -> T): T {
        if (identifier == null) {
            return connection.prepare(sql).use(block)
        }
        val stmt = statements.remove(identifier) ?: connection.prepare(sql)
        val result = try {
            block(stmt)
        } catch (e: Throwable) {
            // Don't put a statement in an unknown state back in the cache.
            runCatching { stmt.close() }
            throw e
        }
        stmt.reset()
        stmt.clearBindings()
        // LRU: most recently used goes to the end; evict the oldest.
        statements[identifier] = stmt
        while (statements.size > statementCacheSize) {
            val eldest = statements.keys.first()
            statements.remove(eldest)?.close()
        }
        return result
    }

    private fun changes(): Long = connection.prepare("SELECT changes()").use { if (it.step()) it.getLong(0) else 0L }

    private fun checkOpen() = check(!closed) { "Database is closed" }

    private class Binder(private val stmt: SQLiteStatement) : SqlPreparedStatement {
        override fun bindBytes(index: Int, bytes: ByteArray?) = if (bytes == null) stmt.bindNull(index + 1) else stmt.bindBlob(index + 1, bytes)
        override fun bindLong(index: Int, long: Long?) = if (long == null) stmt.bindNull(index + 1) else stmt.bindLong(index + 1, long)
        override fun bindDouble(index: Int, double: Double?) = if (double == null) stmt.bindNull(index + 1) else stmt.bindDouble(index + 1, double)
        override fun bindString(index: Int, string: String?) = if (string == null) stmt.bindNull(index + 1) else stmt.bindText(index + 1, string)
        override fun bindBoolean(index: Int, boolean: Boolean?) =
            if (boolean == null) stmt.bindNull(index + 1) else stmt.bindLong(index + 1, if (boolean) 1L else 0L)
    }

    private class Cursor(private val stmt: SQLiteStatement) : SqlCursor {
        override fun next(): QueryResult<Boolean> = QueryResult.Value(stmt.step())
        override fun getString(index: Int): String? = if (stmt.isNull(index)) null else stmt.getText(index)
        override fun getLong(index: Int): Long? = if (stmt.isNull(index)) null else stmt.getLong(index)
        override fun getBytes(index: Int): ByteArray? = if (stmt.isNull(index)) null else stmt.getBlob(index)
        override fun getDouble(index: Int): Double? = if (stmt.isNull(index)) null else stmt.getDouble(index)
        override fun getBoolean(index: Int): Boolean? = if (stmt.isNull(index)) null else stmt.getLong(index) != 0L
    }
}

private fun SQLiteConnection.execute(sql: String) = prepare(sql).use { it.step() }
