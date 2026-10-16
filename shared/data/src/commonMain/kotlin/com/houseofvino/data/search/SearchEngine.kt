package com.houseofvino.data.search

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlPreparedStatement
import com.houseofvino.core.model.DrinkStatus
import com.houseofvino.core.model.Vintage
import com.houseofvino.core.model.WineStyle
import com.houseofvino.core.search.FtsExpression
import com.houseofvino.core.search.QueryParser
import com.houseofvino.core.search.SearchFilters
import com.houseofvino.core.search.SearchQuery
import com.houseofvino.core.search.SearchScope
import com.houseofvino.core.search.SearchSort
import com.houseofvino.core.search.TypoCorrector
import com.houseofvino.data.Session
import com.houseofvino.data.repo.Store

data class FormatCount(val ml: Int, val count: Int)

/** One wine vintage in the cellar list or cellar search results. */
data class CellarRow(
    val vintageId: String,
    val wineId: String,
    val producer: String,
    val name: String,
    val classification: String?,
    val style: WineStyle?,
    val country: String?,
    val region: String?,
    val appellation: String?,
    val vintage: Vintage,
    val drinkFrom: Int?,
    val drinkTo: Int?,
    val bottles: Int,
    val formats: List<FormatCount>,
    val bestRating100: Int?,
)

data class TastingRow(
    val noteId: String,
    val vintageId: String,
    val producer: String,
    val name: String,
    val vintage: Vintage,
    val tastedOn: String,
    val rating100: Int?,
    val ratingValue: String?,
    val ratingScale: String?,
    val summary: String?,
)

data class WishlistRow(
    val itemId: String,
    val title: String,
    val producer: String?,
    val status: String,
    val priority: Int,
    val desiredQuantity: Int,
    val vintageFrom: Int?,
    val vintageTo: Int?,
)

data class SearchPage<T>(
    val items: List<T>,
    /** Matches across all pages. */
    val total: Int,
    /** Bottles across all matching wines (cellar scope only). */
    val bottles: Int = 0,
    /** Terms that matched nothing and the words searched instead ("barollo" -> ["barolo"]). */
    val corrections: Map<String, List<String>> = emptyMap(),
    val offset: Int = 0,
) {
    val hasMore: Boolean get() = offset + items.size < total
}

/**
 * Full-text + structured search over the local database. SQL is assembled here from fixed
 * fragments; every user-supplied value is a bound parameter. Results are paged.
 */
class SearchEngine(private val store: Store) {

    private val driver get() = store.driver

    fun searchCellar(session: Session, query: SearchQuery, offset: Int = 0, limit: Int = 50, currentYear: Int = store.clock.currentYear()): SearchPage<CellarRow> {
        val parsed = QueryParser.parse(query.text)
        val filters = query.filters.let { f ->
            if (parsed.vintageFrom != null) f.copy(vintageFrom = parsed.vintageFrom, vintageTo = parsed.vintageTo) else f
        }
        var corrections = emptyMap<String, List<String>>()
        var expr = FtsExpression.build(parsed.terms)
        var page = runCellar(session, expr, filters, query.sort, offset, limit, currentYear)
        if (page.total == 0 && expr != null) {
            corrections = corrections(parsed.terms)
            if (corrections.isNotEmpty()) {
                expr = FtsExpression.build(parsed.terms, corrections)
                page = runCellar(session, expr, filters, query.sort, offset, limit, currentYear)
            }
        }
        return page.copy(corrections = if (page.total > 0) corrections else emptyMap())
    }

    fun searchTastings(session: Session, query: SearchQuery, offset: Int = 0, limit: Int = 50): SearchPage<TastingRow> {
        val parsed = QueryParser.parse(query.text)
        var expr = FtsExpression.build(parsed.terms)
        var corrections = emptyMap<String, List<String>>()
        var page = runTastings(session, expr, query.filters, query.sort, offset, limit, parsed.vintageFrom, parsed.vintageTo)
        if (page.total == 0 && expr != null) {
            corrections = corrections(parsed.terms)
            if (corrections.isNotEmpty()) {
                expr = FtsExpression.build(parsed.terms, corrections)
                page = runTastings(session, expr, query.filters, query.sort, offset, limit, parsed.vintageFrom, parsed.vintageTo)
            }
        }
        return page.copy(corrections = if (page.total > 0) corrections else emptyMap())
    }

    fun searchWishlist(session: Session, query: SearchQuery, offset: Int = 0, limit: Int = 50, includeClosed: Boolean = false): SearchPage<WishlistRow> {
        val parsed = QueryParser.parse(query.text)
        var expr = FtsExpression.build(parsed.terms)
        var corrections = emptyMap<String, List<String>>()
        var page = runWishlist(session, expr, query.filters, offset, limit, includeClosed)
        if (page.total == 0 && expr != null) {
            corrections = corrections(parsed.terms)
            if (corrections.isNotEmpty()) {
                expr = FtsExpression.build(parsed.terms, corrections)
                page = runWishlist(session, expr, query.filters, offset, limit, includeClosed)
            }
        }
        return page.copy(corrections = if (page.total > 0) corrections else emptyMap())
    }

    /** Scope dispatcher used by the Search screen. DISCOVER is handled by external catalogues, not here. */
    fun scopeOf(query: SearchQuery): SearchScope = query.scope

    // ------------------------------------------------------------ typo tolerance

    private fun ensureVocabTable() {
        driver.execute(null, "CREATE VIRTUAL TABLE IF NOT EXISTS temp.search_vocab USING fts5vocab(main, search_fts, 'row')", 0)
    }

    /** For each term with no prefix match in the index, the closest words that do exist. */
    fun corrections(terms: List<String>): Map<String, List<String>> {
        if (terms.isEmpty()) return emptyMap()
        ensureVocabTable()
        val result = mutableMapOf<String, List<String>>()
        for (term in terms) {
            val hasHit = queryList("SELECT 1 FROM search_fts WHERE search_fts MATCH ? LIMIT 1", listOf(FtsExpression.quote(term) + "*")) { it.getLong(0) }.isNotEmpty()
            if (hasHit) continue
            val minLen = (term.length - 2).coerceAtLeast(1).toLong()
            val maxLen = (term.length + 2).toLong()
            val vocab = queryList("SELECT term, doc FROM temp.search_vocab WHERE length(term) BETWEEN ? AND ?", listOf(minLen, maxLen)) {
                it.getString(0)!! to (it.getLong(1) ?: 0L)
            }
            val suggestions = TypoCorrector.suggest(term, vocab.asSequence())
            if (suggestions.isNotEmpty()) result[term] = suggestions
        }
        return result
    }

    // ------------------------------------------------------------ cellar

    private fun runCellar(
        session: Session,
        expr: String?,
        f: SearchFilters,
        sort: SearchSort,
        offset: Int,
        limit: Int,
        currentYear: Int,
    ): SearchPage<CellarRow> {
        val args = Args()
        val locationIds = f.locationIds.flatMap { store.db.locationQueries.subtreeIds(it).executeAsList() }.distinct()
        val sql = StringBuilder()
        sql.append(
            """
            WITH holdings AS (
                SELECT l.vintage_id AS vintage_id, l.format_ml AS format_ml, SUM(s.confirmed_qty + s.pending_delta) AS qty
                FROM stock s JOIN lot l ON l.id = s.lot_id
                WHERE s.household_id = ${args.add(session.householdId)}
            """.trimIndent(),
        )
        if (locationIds.isNotEmpty()) sql.append(" AND s.location_id IN ${args.list(locationIds)}")
        sql.append(
            """
                GROUP BY l.vintage_id, l.format_ml
            ),
            pv AS (
                SELECT vintage_id, SUM(qty) AS bottles, GROUP_CONCAT(format_ml || ':' || qty, ',') AS formats
                FROM holdings WHERE qty > 0 GROUP BY vintage_id
            )
            """.trimIndent(),
        )
        if (expr != null) {
            sql.append(
                """,
                m AS (
                    SELECT d.doc_id AS vid, bm25(search_fts, 10.0, 8.0, 4.0, 3.0, 3.0, 2.0, 1.0) AS score
                    FROM search_fts JOIN search_doc d ON d.id = search_fts.rowid
                    WHERE search_fts MATCH ${args.add(expr)} AND d.doc_type = 'vintage' AND d.household_id = ${args.add(session.householdId)}
                )
                """.trimIndent(),
            )
        }
        val from = StringBuilder()
        from.append(" FROM wine_vintage v JOIN wine w ON w.id = v.wine_id LEFT JOIN pv ON pv.vintage_id = v.id")
        if (expr != null) from.append(" JOIN m ON m.vid = v.id")
        from.append(" WHERE v.household_id = ${args.add(session.householdId)} AND v.deleted_at IS NULL AND w.deleted_at IS NULL")
        if (f.includeFormerlyOwned && locationIds.isEmpty()) {
            from.append(" AND (pv.bottles > 0 OR EXISTS (SELECT 1 FROM lot lo WHERE lo.vintage_id = v.id))")
        } else {
            from.append(" AND pv.bottles > 0")
        }
        appendWineFilters(from, args, f, currentYear)
        if (f.formatsMl.isNotEmpty()) {
            from.append(" AND EXISTS (SELECT 1 FROM holdings h WHERE h.vintage_id = v.id AND h.qty > 0 AND h.format_ml IN ${args.list(f.formatsMl.map { it.toLong() })})")
        }

        // Count first (same CTEs and WHERE), then the page.
        val countArgs = args.copy()
        val countSql = "$sql SELECT COUNT(*), COALESCE(SUM(pv.bottles), 0) $from"
        val (total, bottles) = queryList(countSql, countArgs.values) { (it.getLong(0) ?: 0L) to (it.getLong(1) ?: 0L) }.first()

        val select = StringBuilder()
        select.append(
            """
             SELECT v.id, w.id, w.producer, w.name, w.classification, w.style, w.country, w.region, w.appellation,
                    v.vintage_kind, v.vintage_year, v.drink_from, v.drink_to, COALESCE(pv.bottles, 0), pv.formats,
                    (SELECT MAX(n.rating_100) FROM tasting_note n WHERE n.vintage_id = v.id AND n.deleted_at IS NULL) AS best_rating
            """.trimIndent(),
        )
        select.append(from)
        select.append(" ORDER BY ").append(orderBy(sort, expr != null))
        select.append(" LIMIT ${args.add(limit.toLong())} OFFSET ${args.add(offset.toLong())}")
        val rows = queryList("$sql$select", args.values) { c ->
            CellarRow(
                vintageId = c.getString(0)!!,
                wineId = c.getString(1)!!,
                producer = c.getString(2)!!,
                name = c.getString(3)!!,
                classification = c.getString(4),
                style = WineStyle.fromCode(c.getString(5)),
                country = c.getString(6),
                region = c.getString(7),
                appellation = c.getString(8),
                vintage = Vintage.fromStorage(c.getString(9)!!, c.getLong(10)),
                drinkFrom = c.getLong(11)?.toInt(),
                drinkTo = c.getLong(12)?.toInt(),
                bottles = (c.getLong(13) ?: 0L).toInt(),
                formats = parseFormats(c.getString(14)),
                bestRating100 = c.getLong(15)?.toInt(),
            )
        }
        return SearchPage(rows, total.toInt(), bottles.toInt(), offset = offset)
    }

    private fun appendWineFilters(sql: StringBuilder, args: Args, f: SearchFilters, currentYear: Int) {
        if (f.styles.isNotEmpty()) sql.append(" AND w.style IN ${args.list(f.styles.map { it.code })}")
        if (f.countries.isNotEmpty()) sql.append(" AND w.country COLLATE NOCASE IN ${args.list(f.countries.toList())}")
        if (f.regions.isNotEmpty()) {
            val r = f.regions.toList()
            sql.append(" AND (w.region COLLATE NOCASE IN ${args.list(r)} OR w.sub_region COLLATE NOCASE IN ${args.list(r)} OR w.appellation COLLATE NOCASE IN ${args.list(r)})")
        }
        if (f.grapes.isNotEmpty()) {
            sql.append(" AND EXISTS (SELECT 1 FROM json_each(w.grapes) g WHERE json_extract(g.value, '$.name') COLLATE NOCASE IN ${args.list(f.grapes.toList())})")
        }
        if (f.vintageFrom != null || f.vintageTo != null) {
            val lo = (f.vintageFrom ?: Vintage.MIN_YEAR).toLong()
            val hi = (f.vintageTo ?: Vintage.MAX_YEAR).toLong()
            sql.append(" AND ((v.vintage_kind = 'year' AND v.vintage_year BETWEEN ${args.add(lo)} AND ${args.add(hi)})")
            if (f.includeNonVintage) sql.append(" OR v.vintage_kind = 'nv'")
            sql.append(")")
        }
        if (f.tagIds.isNotEmpty()) {
            sql.append(
                " AND EXISTS (SELECT 1 FROM tag_link tl WHERE tl.deleted_at IS NULL AND tl.tag_id IN ${args.list(f.tagIds.toList())}" +
                    " AND ((tl.target_type = 'wine' AND tl.target_id = w.id) OR (tl.target_type = 'vintage' AND tl.target_id = v.id)" +
                    " OR (tl.target_type = 'lot' AND tl.target_id IN (SELECT lt.id FROM lot lt WHERE lt.vintage_id = v.id))))",
            )
        }
        if (f.drinkStatuses.isNotEmpty()) {
            sql.append(" AND ${drinkStatusSql(args, currentYear)} IN ${args.list(f.drinkStatuses.map { it.name })}")
        }
        f.minRating100?.let { min ->
            sql.append(" AND (SELECT MAX(n2.rating_100) FROM tasting_note n2 WHERE n2.vintage_id = v.id AND n2.deleted_at IS NULL) >= ${args.add(min.toLong())}")
        }
    }

    private fun orderBy(sort: SearchSort, hasFts: Boolean): String {
        val vintageAsc = "CASE WHEN v.vintage_kind = 'year' THEN v.vintage_year ELSE 99999 END"
        val name = "w.producer COLLATE NOCASE, w.name COLLATE NOCASE"
        val tail = ", v.id"
        return when (sort) {
            SearchSort.RELEVANCE -> if (hasFts) "m.score, $name, $vintageAsc$tail" else "$name, $vintageAsc$tail"
            SearchSort.PRODUCER -> "$name, $vintageAsc$tail"
            SearchSort.VINTAGE_OLDEST -> "$vintageAsc, $name$tail"
            SearchSort.VINTAGE_NEWEST -> "(v.vintage_kind <> 'year'), v.vintage_year DESC, $name$tail"
            SearchSort.QUANTITY -> "COALESCE(pv.bottles, 0) DESC, $name$tail"
            SearchSort.RECENTLY_ADDED -> "COALESCE((SELECT MAX(lr.created_at) FROM lot lr WHERE lr.vintage_id = v.id), v.created_at) DESC$tail"
            SearchSort.DRINK_BY -> "(v.drink_to IS NULL), v.drink_to, (v.drink_from IS NULL), v.drink_from, $name$tail"
            SearchSort.RATING -> "(best_rating IS NULL), best_rating DESC, $name$tail"
        }
    }

    // ------------------------------------------------------------ tasting notes

    private fun runTastings(
        session: Session,
        expr: String?,
        f: SearchFilters,
        sort: SearchSort,
        offset: Int,
        limit: Int,
        inlineFrom: Int?,
        inlineTo: Int?,
    ): SearchPage<TastingRow> {
        val args = Args()
        val sql = StringBuilder()
        if (expr != null) {
            sql.append(
                "WITH m AS (SELECT d.doc_id AS nid, bm25(search_fts, 6.0, 6.0, 3.0, 2.0, 2.0, 2.0, 1.0) AS score " +
                    "FROM search_fts JOIN search_doc d ON d.id = search_fts.rowid " +
                    "WHERE search_fts MATCH ${args.add(expr)} AND d.doc_type = 'tasting' AND d.household_id = ${args.add(session.householdId)}) ",
            )
        }
        val from = StringBuilder(" FROM tasting_note n JOIN wine_vintage v ON v.id = n.vintage_id JOIN wine w ON w.id = v.wine_id")
        if (expr != null) from.append(" JOIN m ON m.nid = n.id")
        from.append(" WHERE n.household_id = ${args.add(session.householdId)} AND n.deleted_at IS NULL")
        val filters = if (inlineFrom != null) f.copy(vintageFrom = inlineFrom, vintageTo = inlineTo) else f
        if (filters.styles.isNotEmpty()) from.append(" AND w.style IN ${args.list(filters.styles.map { it.code })}")
        if (filters.countries.isNotEmpty()) from.append(" AND w.country COLLATE NOCASE IN ${args.list(filters.countries.toList())}")
        if (filters.vintageFrom != null || filters.vintageTo != null) {
            from.append(" AND v.vintage_kind = 'year' AND v.vintage_year BETWEEN ${args.add((filters.vintageFrom ?: Vintage.MIN_YEAR).toLong())} AND ${args.add((filters.vintageTo ?: Vintage.MAX_YEAR).toLong())}")
        }
        filters.minRating100?.let { min -> from.append(" AND n.rating_100 >= ${args.add(min.toLong())}") }
        if (filters.tagIds.isNotEmpty()) {
            from.append(" AND EXISTS (SELECT 1 FROM tag_link tl WHERE tl.deleted_at IS NULL AND tl.target_type = 'tasting' AND tl.target_id = n.id AND tl.tag_id IN ${args.list(filters.tagIds.toList())})")
        }
        val countArgs = args.copy()
        val total = queryList("${sql}SELECT COUNT(*)$from", countArgs.values) { it.getLong(0) ?: 0L }.first()
        val order = when (sort) {
            SearchSort.RELEVANCE -> if (expr != null) "m.score, n.tasted_on DESC" else "n.tasted_on DESC, n.created_at DESC"
            SearchSort.RATING -> "(n.rating_100 IS NULL), n.rating_100 DESC, n.tasted_on DESC"
            SearchSort.PRODUCER -> "w.producer COLLATE NOCASE, n.tasted_on DESC"
            SearchSort.VINTAGE_OLDEST -> "v.vintage_year, n.tasted_on DESC"
            SearchSort.VINTAGE_NEWEST -> "v.vintage_year DESC, n.tasted_on DESC"
            else -> "n.tasted_on DESC, n.created_at DESC"
        }
        val rows = queryList(
            "${sql}SELECT n.id, v.id, w.producer, w.name, v.vintage_kind, v.vintage_year, n.tasted_on, n.rating_100, n.rating_value, n.rating_scale, " +
                "COALESCE(n.conclusion, n.notes, n.palate, n.nose)$from ORDER BY $order, n.id LIMIT ${args.add(limit.toLong())} OFFSET ${args.add(offset.toLong())}",
            args.values,
        ) { c ->
            TastingRow(
                noteId = c.getString(0)!!, vintageId = c.getString(1)!!, producer = c.getString(2)!!, name = c.getString(3)!!,
                vintage = Vintage.fromStorage(c.getString(4)!!, c.getLong(5)), tastedOn = c.getString(6)!!,
                rating100 = c.getLong(7)?.toInt(), ratingValue = c.getString(8), ratingScale = c.getString(9),
                summary = c.getString(10)?.take(240),
            )
        }
        return SearchPage(rows, total.toInt(), offset = offset)
    }

    // ------------------------------------------------------------ wishlist

    private fun runWishlist(session: Session, expr: String?, f: SearchFilters, offset: Int, limit: Int, includeClosed: Boolean): SearchPage<WishlistRow> {
        val args = Args()
        val sql = StringBuilder()
        if (expr != null) {
            sql.append(
                "WITH m AS (SELECT d.doc_id AS iid, bm25(search_fts, 8.0, 8.0, 3.0, 3.0, 2.0, 2.0, 1.0) AS score " +
                    "FROM search_fts JOIN search_doc d ON d.id = search_fts.rowid " +
                    "WHERE search_fts MATCH ${args.add(expr)} AND d.doc_type = 'wishlist' AND d.household_id = ${args.add(session.householdId)}) ",
            )
        }
        val from = StringBuilder(" FROM wishlist_item i LEFT JOIN wine w ON w.id = i.wine_id")
        if (expr != null) from.append(" JOIN m ON m.iid = i.id")
        from.append(" WHERE i.household_id = ${args.add(session.householdId)} AND i.deleted_at IS NULL")
        if (!includeClosed) from.append(" AND i.status IN ('wanted', 'watching', 'ordered')")
        if (f.styles.isNotEmpty()) from.append(" AND COALESCE(w.style, i.style) IN ${args.list(f.styles.map { it.code })}")
        val countArgs = args.copy()
        val total = queryList("${sql}SELECT COUNT(*)$from", countArgs.values) { it.getLong(0) ?: 0L }.first()
        val order = if (expr != null) "m.score, i.priority, i.created_at DESC" else "i.priority, i.created_at DESC"
        val rows = queryList(
            "${sql}SELECT i.id, i.title, COALESCE(w.producer, i.producer), i.status, i.priority, i.desired_quantity, i.vintage_from, i.vintage_to" +
                "$from ORDER BY $order, i.id LIMIT ${args.add(limit.toLong())} OFFSET ${args.add(offset.toLong())}",
            args.values,
        ) { c ->
            WishlistRow(
                itemId = c.getString(0)!!, title = c.getString(1)!!, producer = c.getString(2), status = c.getString(3)!!,
                priority = (c.getLong(4) ?: 2L).toInt(), desiredQuantity = (c.getLong(5) ?: 1L).toInt(),
                vintageFrom = c.getLong(6)?.toInt(), vintageTo = c.getLong(7)?.toInt(),
            )
        }
        return SearchPage(rows, total.toInt(), offset = offset)
    }

    // ------------------------------------------------------------ plumbing

    /** Bound parameters in order; [add] returns the placeholder to splice into SQL. */
    private class Args(val values: MutableList<Any?> = mutableListOf()) {
        fun add(v: Any?): String {
            values += v
            return "?"
        }

        fun list(vs: Collection<Any?>): String {
            require(vs.isNotEmpty())
            vs.forEach { values += it }
            return vs.joinToString(", ", "(", ")") { "?" }
        }

        fun copy() = Args(values.toMutableList())
    }

    private fun <T> queryList(sql: String, params: List<Any?>, map: (SqlCursor) -> T): List<T> =
        driver.executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                val out = mutableListOf<T>()
                while (cursor.next().value) out += map(cursor)
                QueryResult.Value(out)
            },
            parameters = params.size,
            binders = { bindAll(params) },
        ).value

    private fun SqlPreparedStatement.bindAll(params: List<Any?>) {
        params.forEachIndexed { i, v ->
            when (v) {
                null -> bindString(i, null)
                is String -> bindString(i, v)
                is Long -> bindLong(i, v)
                is Int -> bindLong(i, v.toLong())
                is Double -> bindDouble(i, v)
                is Boolean -> bindLong(i, if (v) 1 else 0)
                else -> error("Unsupported parameter type ${v::class}")
            }
        }
    }

    companion object {
        /** SQL mirror of [com.houseofvino.core.model.DrinkingWindow.status]; a test keeps them equal. */
        internal fun drinkStatusSql(args: Any, currentYear: Int): String {
            val y = currentYear.toString() // an Int from the clock, not user input
            return "(CASE" +
                " WHEN v.drink_from IS NULL AND v.drink_to IS NULL THEN '${DrinkStatus.UNKNOWN.name}'" +
                " WHEN v.drink_from IS NOT NULL AND $y < v.drink_from THEN '${DrinkStatus.TOO_YOUNG.name}'" +
                " WHEN v.drink_to IS NOT NULL AND $y > v.drink_to THEN '${DrinkStatus.PAST_PEAK.name}'" +
                " WHEN v.drink_to IS NOT NULL AND $y >= v.drink_to - 1 THEN '${DrinkStatus.DRINK_SOON.name}'" +
                " ELSE '${DrinkStatus.READY.name}' END)"
        }

        internal fun parseFormats(s: String?): List<FormatCount> =
            s?.split(',')?.mapNotNull { part ->
                val (ml, n) = part.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                FormatCount(ml.toIntOrNull() ?: return@mapNotNull null, n.toIntOrNull() ?: return@mapNotNull null)
            }?.sortedByDescending { it.count }.orEmpty()
    }
}
