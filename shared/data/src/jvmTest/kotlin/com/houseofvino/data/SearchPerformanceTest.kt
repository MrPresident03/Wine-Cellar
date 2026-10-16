package com.houseofvino.data

import com.houseofvino.core.model.DrinkStatus
import com.houseofvino.core.model.GrapeShare
import com.houseofvino.core.model.Vintage
import com.houseofvino.core.model.WineStyle
import com.houseofvino.core.search.SearchFilters
import com.houseofvino.core.search.SearchQuery
import com.houseofvino.core.search.SearchSort
import com.houseofvino.data.repo.PurchaseInput
import com.houseofvino.data.repo.VintageInput
import com.houseofvino.data.repo.WineInput
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * A synthetic collection far larger than the 5,000-bottle target (20,000 vintages, ~60,000
 * bottles), searched the way the app does. The data is random but shaped like a real cellar.
 */
class SearchPerformanceTest {

    private val producers = listOf(
        "Château", "Domaine", "Bodegas", "Tenuta", "Weingut", "Maison", "Quinta", "Cantina", "Clos", "Estate",
    )
    private val names = listOf(
        "Margaux", "Latour", "Ponsot", "Leflaive", "Roumier", "Giacosa", "Vega Sicilia", "Egon Müller", "Krug", "Penfolds",
        "Felton Road", "Cloudy Bay", "Ridge", "Dönnhoff", "Trimbach", "Guigal", "Chave", "Rayas", "Sassicaia", "Ornellaia",
    )
    private val regions = listOf("Bordeaux", "Burgundy", "Piedmont", "Rioja", "Mosel", "Champagne", "Barossa", "Central Otago", "Napa", "Rhône")
    private val grapes = listOf("Pinot Noir", "Cabernet Sauvignon", "Nebbiolo", "Riesling", "Chardonnay", "Syrah", "Tempranillo", "Merlot", "Grenache", "Sémillon")

    @Test
    fun searchStaysFastOnALargeCollection() = withEnv {
        val r = Random(5)
        val generation = measureTime {
            store.write {
                var vintagesMade = 0
                var wineIndex = 0
                while (vintagesMade < 20_000) {
                    val producer = "${producers.random(r)} ${names.random(r)} ${wineIndex % 997}"
                    val wine = wines.createWine(
                        session,
                        WineInput(
                            producer = producer,
                            name = "Cuvée ${wineIndex % 53}",
                            style = WineStyle.entries.random(r),
                            country = "Country ${wineIndex % 12}",
                            region = regions.random(r),
                            grapes = listOf(GrapeShare(grapes.random(r))),
                        ),
                        allowDuplicate = true,
                    )
                    wineIndex++
                    val years = (1960..2023).shuffled(r).take(r.nextInt(1, 6))
                    for (y in years) {
                        val v = wines.addVintage(session, wine, VintageInput(Vintage.Year(y), drinkFrom = y + 5, drinkTo = y + 25))
                        inventory.addPurchase(session, PurchaseInput(v, r.nextInt(1, 6), cellarId))
                        vintagesMade++
                    }
                }
            }
        }
        println("Generated ${scalarLong("SELECT count(*) FROM wine_vintage")} vintages / ${scalarLong("SELECT sum(confirmed_qty + pending_delta) FROM stock")} bottles in $generation")

        val queries = listOf(
            "first page, no text" to SearchQuery(sort = SearchSort.PRODUCER),
            "prefix 'chat'" to SearchQuery("chat"),
            "two terms" to SearchQuery("domaine pinot"),
            "term + year" to SearchQuery("margaux 2010"),
            "typo 'barossaa'" to SearchQuery("barossaa"),
            "accent 'donnhoff'" to SearchQuery("donnhoff"),
            "filters" to SearchQuery(filters = SearchFilters(styles = setOf(WineStyle.RED), vintageFrom = 1990, vintageTo = 2005, drinkStatuses = setOf(DrinkStatus.READY))),
            "text + filters + sort" to SearchQuery("riesling", filters = SearchFilters(regions = setOf("Mosel")), sort = SearchSort.VINTAGE_OLDEST),
            "sort by drink-by" to SearchQuery(sort = SearchSort.DRINK_BY),
        )
        val report = StringBuilder("Search timings on 20,000 vintages (median of 9 runs, first 50 results + count):\n")
        var worst = 0.0
        for ((label, q) in queries) {
            search.searchCellar(session, q) // warm up
            val times = (1..9).map { measureTime { search.searchCellar(session, q, limit = 50) }.inWholeMicroseconds / 1000.0 }.sorted()
            val median = times[4]
            val total = search.searchCellar(session, q).total
            worst = maxOf(worst, median)
            report.append("  %-24s %7.1f ms  (%d matches)\n".format(label, median, total))
        }
        println(report)
        System.getProperty("houseofvino.reports")?.let { File(it).apply { mkdirs() }.resolve("search-performance.txt").writeText(report.toString()) }
        // CI machines are slower than phones' SQLite on small queries but much faster at scale; keep a generous ceiling.
        assertTrue(worst < 250.0, "slowest median query took $worst ms")
    }
}
