package com.houseofvino.core.search

import com.houseofvino.core.model.DrinkStatus
import com.houseofvino.core.model.WineStyle
import com.houseofvino.core.text.EditDistance
import com.houseofvino.core.text.TextNormalizer
import kotlinx.serialization.Serializable

/** Where a search looks. Each scope has its own index rows and result type. */
@Serializable
enum class SearchScope(val label: String) {
    /** Wines with bottles in the collection now (optionally including ones no longer held). */
    CELLAR("Cellar"),
    WISHLIST("Wishlist"),
    TASTINGS("Tasting notes"),

    /** An external catalogue, when one is configured. Never mixed with the user's own data. */
    DISCOVER("Discover"),
}

@Serializable
enum class SearchSort(val label: String) {
    RELEVANCE("Best match"),
    PRODUCER("Producer A–Z"),
    VINTAGE_OLDEST("Vintage, oldest first"),
    VINTAGE_NEWEST("Vintage, newest first"),
    QUANTITY("Most bottles"),
    RECENTLY_ADDED("Recently added"),
    DRINK_BY("Drink-by year"),
    RATING("My rating"),
}

@Serializable
data class SearchFilters(
    val styles: Set<WineStyle> = emptySet(),
    val countries: Set<String> = emptySet(),
    val regions: Set<String> = emptySet(),
    val grapes: Set<String> = emptySet(),
    val vintageFrom: Int? = null,
    val vintageTo: Int? = null,
    /** When a vintage range is set, also include non-vintage wines. */
    val includeNonVintage: Boolean = false,
    val formatsMl: Set<Int> = emptySet(),
    /** Matches the location or anything inside it. */
    val locationIds: Set<String> = emptySet(),
    val tagIds: Set<String> = emptySet(),
    val drinkStatuses: Set<DrinkStatus> = emptySet(),
    val minRating100: Int? = null,
    /** CELLAR scope only: also show wines that have been fully consumed or sold. */
    val includeFormerlyOwned: Boolean = false,
) {
    val isEmpty: Boolean
        get() = activeCount == 0

    val activeCount: Int
        get() = listOf(
            styles.isNotEmpty(), countries.isNotEmpty(), regions.isNotEmpty(), grapes.isNotEmpty(),
            vintageFrom != null || vintageTo != null, formatsMl.isNotEmpty(), locationIds.isNotEmpty(),
            tagIds.isNotEmpty(), drinkStatuses.isNotEmpty(), minRating100 != null, includeFormerlyOwned,
        ).count { it }
}

@Serializable
data class SearchQuery(
    val text: String = "",
    val scope: SearchScope = SearchScope.CELLAR,
    val filters: SearchFilters = SearchFilters(),
    val sort: SearchSort = SearchSort.RELEVANCE,
)

/** A user's typed text broken into searchable terms plus any inline vintage range ("2010-2015"). */
data class ParsedText(val terms: List<String>, val vintageFrom: Int?, val vintageTo: Int?)

object QueryParser {
    private val RANGE = Regex("""\b(1[7-9]\d\d|2[01]\d\d)\s*(?:-|–|\.\.|to)\s*(1[7-9]\d\d|2[01]\d\d)\b""")

    fun parse(text: String): ParsedText {
        var rest = text
        var from: Int? = null
        var to: Int? = null
        RANGE.find(TextNormalizer.foldCase(text))?.let { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            from = minOf(a, b)
            to = maxOf(a, b)
            rest = TextNormalizer.foldCase(text).removeRange(m.range)
        }
        val terms = TextNormalizer.tokens(rest).distinct().take(MAX_TERMS)
        return ParsedText(terms, from, to)
    }

    const val MAX_TERMS = 12
}

/**
 * Builds an FTS5 MATCH expression. Every term must match (AND), each as a prefix so "chat"
 * finds "Château". Terms that look like typos are widened with close vocabulary words:
 * "barollo" -> ("barollo"* OR "barolo"*). Returns null when there is nothing to match.
 */
object FtsExpression {

    fun build(terms: List<String>, corrections: Map<String, List<String>> = emptyMap()): String? {
        if (terms.isEmpty()) return null
        return terms.joinToString(" AND ") { term ->
            val alternatives = (listOf(term) + corrections[term].orEmpty()).distinct()
            if (alternatives.size == 1) quote(term) + "*" else alternatives.joinToString(" OR ", "(", ")") { quote(it) + "*" }
        }
    }

    /** FTS5 string literal; terms are already folded to letters and digits but quote defensively. */
    fun quote(term: String): String = "\"" + term.replace("\"", "\"\"") + "\""
}

/**
 * Picks likely intended words for terms that matched nothing. [vocabulary] is the set of terms in
 * the index (FTS5 vocab table), so suggestions only ever point at words that exist in the user's
 * own data.
 */
object TypoCorrector {
    fun suggest(term: String, vocabulary: Sequence<Pair<String, Long>>, limit: Int = 3): List<String> {
        val allowance = EditDistance.allowance(term.length)
        if (allowance == 0) return emptyList()
        return vocabulary
            .filter { (word, _) -> word != term && kotlin.math.abs(word.length - term.length) <= allowance }
            .map { (word, count) -> Triple(word, EditDistance.osa(term, word, allowance), count) }
            .filter { it.second <= allowance }
            .sortedWith(compareBy<Triple<String, Int, Long>> { it.second }.thenByDescending { it.third })
            .take(limit)
            .map { it.first }
            .toList()
    }
}
