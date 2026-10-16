package com.houseofvino.data

import com.houseofvino.core.model.DrinkStatus
import com.houseofvino.core.model.DrinkingWindow
import com.houseofvino.core.model.GrapeShare
import com.houseofvino.core.model.MovementDraft
import com.houseofvino.core.model.MovementKind
import com.houseofvino.core.model.WineStyle
import com.houseofvino.core.search.SearchFilters
import com.houseofvino.core.search.SearchQuery
import com.houseofvino.core.search.SearchSort
import com.houseofvino.data.repo.TagTarget
import com.houseofvino.data.repo.WineInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchEngineTest {

    private fun TestEnv.seed(): Map<String, String> {
        val lafite = wines.createWine(session, WineInput("Château Lafite Rothschild", "", WineStyle.RED, "France", "Bordeaux", appellation = "Pauillac", grapes = listOf(GrapeShare("Cabernet Sauvignon"), GrapeShare("Merlot"))))
        val yquem = wines.createWine(session, WineInput("Château d'Yquem", "", WineStyle.SWEET, "France", "Bordeaux", appellation = "Sauternes", grapes = listOf(GrapeShare("Sémillon"))))
        val giacosa = wines.createWine(session, WineInput("Bruno Giacosa", "Falletto", WineStyle.RED, "Italy", "Piedmont", appellation = "Barolo", classification = "Riserva", grapes = listOf(GrapeShare("Nebbiolo"))))
        val krug = wines.createWine(session, WineInput("Krug", "Grande Cuvée", WineStyle.SPARKLING, "France", "Champagne", grapes = listOf(GrapeShare("Pinot Noir"), GrapeShare("Chardonnay"))))
        val ids = mapOf(
            "lafite2010" to vintage(lafite, 2010, drinkFrom = 2030, drinkTo = 2070),
            "lafite2005" to vintage(lafite, 2005, drinkFrom = 2020, drinkTo = 2050),
            "yquem2001" to vintage(yquem, 2001, drinkFrom = 2010, drinkTo = 2027),
            "giacosa2016" to vintage(giacosa, 2016, drinkFrom = 2026, drinkTo = 2045),
            "krugNV" to vintage(krug, null),
        )
        buy(ids.getValue("lafite2010"), 6)
        buy(ids.getValue("lafite2005"), 2, formatMl = 1500)
        buy(ids.getValue("yquem2001"), 3, formatMl = 375)
        buy(ids.getValue("giacosa2016"), 12)
        buy(ids.getValue("krugNV"), 3)
        return ids
    }

    private fun TestEnv.cellar(text: String = "", filters: SearchFilters = SearchFilters(), sort: SearchSort = SearchSort.RELEVANCE) =
        search.searchCellar(session, SearchQuery(text, filters = filters, sort = sort), currentYear = 2026)

    @Test
    fun emptyQueryListsTheCellar() = withEnv {
        seed()
        val page = cellar(sort = SearchSort.PRODUCER)
        assertEquals(5, page.total)
        assertEquals(26, page.bottles)
        assertEquals(listOf("Bruno Giacosa", "Château d'Yquem", "Château Lafite Rothschild", "Château Lafite Rothschild", "Krug"), page.items.map { it.producer })
    }

    @Test
    fun accentAndCaseInsensitivePrefixMatching() = withEnv {
        val ids = seed()
        assertEquals(setOf(ids["lafite2010"], ids["lafite2005"], ids["yquem2001"]), cellar("chateau").items.map { it.vintageId }.toSet())
        assertEquals(setOf(ids["lafite2010"], ids["lafite2005"]), cellar("laf").items.map { it.vintageId }.toSet())
        assertEquals(listOf(ids["yquem2001"]), cellar("yquem").items.map { it.vintageId })
        assertEquals(listOf(ids["yquem2001"]), cellar("dyquem").items.map { it.vintageId })
        assertEquals(listOf(ids["yquem2001"]), cellar("semillon").items.map { it.vintageId })
        assertEquals(listOf(ids["giacosa2016"]), cellar("barolo riserva").items.map { it.vintageId })
        assertEquals(listOf(ids["lafite2010"]), cellar("lafite 2010").items.map { it.vintageId })
        assertEquals(listOf(ids["krugNV"]), cellar("krug nv").items.map { it.vintageId })
    }

    @Test
    fun typosAreForgivenUsingWordsFromTheCollection() = withEnv {
        val ids = seed()
        val page = cellar("giacossa barollo")
        assertEquals(listOf(ids["giacosa2016"]), page.items.map { it.vintageId })
        assertEquals(listOf("barolo"), page.corrections["barollo"])
        // Nothing invented: a nonsense word finds nothing.
        assertEquals(0, cellar("zzzzzzzz").total)
    }

    @Test
    fun structuredFilters() = withEnv {
        val ids = seed()
        assertEquals(setOf(ids["krugNV"]), cellar(filters = SearchFilters(styles = setOf(WineStyle.SPARKLING))).items.map { it.vintageId }.toSet())
        assertEquals(setOf(ids["giacosa2016"]), cellar(filters = SearchFilters(countries = setOf("italy"))).items.map { it.vintageId }.toSet())
        assertEquals(4, cellar(filters = SearchFilters(countries = setOf("FRANCE"))).total)
        assertEquals(setOf(ids["lafite2010"], ids["lafite2005"]), cellar(filters = SearchFilters(regions = setOf("pauillac"))).items.map { it.vintageId }.toSet())
        assertEquals(setOf(ids["giacosa2016"]), cellar(filters = SearchFilters(grapes = setOf("nebbiolo"))).items.map { it.vintageId }.toSet())
        assertEquals(setOf(ids["lafite2005"], ids["lafite2010"]), cellar(filters = SearchFilters(vintageFrom = 2005, vintageTo = 2010)).items.map { it.vintageId }.toSet())
        assertEquals(3, cellar(filters = SearchFilters(vintageFrom = 2005, vintageTo = 2010, includeNonVintage = true)).total)
        assertEquals(setOf(ids["lafite2005"]), cellar(filters = SearchFilters(formatsMl = setOf(1500))).items.map { it.vintageId }.toSet())
        // Inline vintage range in the text.
        assertEquals(setOf(ids["lafite2005"], ids["lafite2010"]), cellar("lafite 2004-2011").items.map { it.vintageId }.toSet())
    }

    @Test
    fun drinkingWindowFilterMatchesTheDomainRule() = withEnv {
        val ids = seed()
        // 2026: Lafite 2010 hold, Lafite 2005 ready, Yquem 2001 drink soon (ends 2027), Giacosa ready, Krug unknown.
        fun statuses(s: DrinkStatus) = cellar(filters = SearchFilters(drinkStatuses = setOf(s))).items.map { it.vintageId }.toSet()
        assertEquals(setOf(ids["lafite2010"]), statuses(DrinkStatus.TOO_YOUNG))
        assertEquals(setOf(ids["lafite2005"], ids["giacosa2016"]), statuses(DrinkStatus.READY))
        assertEquals(setOf(ids["yquem2001"]), statuses(DrinkStatus.DRINK_SOON))
        assertEquals(setOf(ids["krugNV"]), statuses(DrinkStatus.UNKNOWN))
        // The SQL and the Kotlin rule agree for every year and window shape.
        val rows = cellar().items
        for (year in 2000..2080) {
            for (row in rows) {
                val expected = DrinkingWindow.statusOf(row.drinkFrom, row.drinkTo, year)
                val got = search.searchCellar(session, SearchQuery(filters = SearchFilters(drinkStatuses = setOf(expected))), currentYear = year)
                    .items.any { it.vintageId == row.vintageId }
                assertTrue(got, "year $year ${row.producer} ${row.vintage} expected $expected")
            }
        }
    }

    @Test
    fun locationFilterIncludesSubPlaces() = withEnv {
        val ids = seed()
        val house = place("House", parent = null)
        val fridge = place("Fridge", parent = house)
        val lot = inventory.lotsOf(ids.getValue("giacosa2016")).single().id
        inventory.record(session, MovementDraft(MovementKind.TRANSFER, lot, 2, cellarId, fridge))
        val page = cellar(filters = SearchFilters(locationIds = setOf(house)))
        assertEquals(listOf(ids["giacosa2016"]), page.items.map { it.vintageId })
        assertEquals(2, page.items.single().bottles)
    }

    @Test
    fun tagsAreSearchableAndFilterable() = withEnv {
        val ids = seed()
        val tag = tags.obtain(session, "Birth year")
        tags.attach(session, tag, TagTarget.VINTAGE, ids.getValue("giacosa2016"))
        assertEquals(listOf(ids["giacosa2016"]), cellar("birth").items.map { it.vintageId })
        assertEquals(listOf(ids["giacosa2016"]), cellar(filters = SearchFilters(tagIds = setOf(tag))).items.map { it.vintageId })
        tags.detach(session, tag, TagTarget.VINTAGE, ids.getValue("giacosa2016"))
        assertEquals(0, cellar("birth").total)
    }

    @Test
    fun consumedWinesLeaveTheCellarButCanBeIncluded() = withEnv {
        val ids = seed()
        val lot = inventory.lotsOf(ids.getValue("krugNV")).single().id
        inventory.record(session, MovementDraft(MovementKind.CONSUME, lot, 3, cellarId, null))
        assertEquals(0, cellar("krug").total)
        assertEquals(1, cellar("krug", SearchFilters(includeFormerlyOwned = true)).total)
    }

    @Test
    fun sortingAndPaging() = withEnv {
        seed()
        val byQty = cellar(sort = SearchSort.QUANTITY).items.map { it.bottles }
        assertEquals(byQty.sortedDescending(), byQty)
        val newest = cellar(sort = SearchSort.VINTAGE_NEWEST).items.map { it.vintage.toString() }
        assertEquals(listOf("2016", "2010", "2005", "2001", "NV"), newest)
        val first = search.searchCellar(session, SearchQuery(sort = SearchSort.PRODUCER), offset = 0, limit = 2)
        val second = search.searchCellar(session, SearchQuery(sort = SearchSort.PRODUCER), offset = 2, limit = 2)
        assertTrue(first.hasMore)
        assertEquals(4, (first.items + second.items).map { it.vintageId }.distinct().size)
    }

    @Test
    fun indexStaysConsistentWithTheData() = withEnv {
        seed()
        val wine = cellar("giacosa").items.single().wineId
        wines.updateWine(session, wine, WineInput("Bruno Giacosa", "Rocche del Falletto", WineStyle.RED, "Italy", "Piedmont", appellation = "Barolo"))
        assertEquals(1, cellar("rocche").total)
        val incremental = store.indexer.snapshot(session.householdId)
        store.write { store.indexer.rebuild(session.householdId) }
        assertEquals(store.indexer.snapshot(session.householdId), incremental)
    }
}
