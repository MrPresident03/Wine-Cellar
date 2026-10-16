package com.houseofvino.ui

import com.houseofvino.core.model.GrapeShare
import com.houseofvino.core.model.LocationKind
import com.houseofvino.core.model.MovementDraft
import com.houseofvino.core.model.MovementKind
import com.houseofvino.core.model.Vintage
import com.houseofvino.core.model.WindowSource
import com.houseofvino.core.model.WineStyle
import com.houseofvino.core.money.Currency
import com.houseofvino.core.money.Money
import com.houseofvino.data.FixedClock
import com.houseofvino.data.repo.LocationInput
import com.houseofvino.data.repo.PurchaseInput
import com.houseofvino.data.repo.Slot
import com.houseofvino.data.repo.VintageInput
import com.houseofvino.data.repo.WineInput
import com.houseofvino.ui.app.AppGraph
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/** A small, realistic cellar for UI tests and screenshots. The data is invented test data. */
object TestCellar {
    val today = LocalDate(2026, 10, 17)
    private val nzd = Currency("NZD")

    class Seeded(val graph: AppGraph, val ids: Map<String, String>)

    fun empty(): AppGraph {
        val g = AppGraph(":memory:", FixedClock(today.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() + 10 * 3600_000L), nzd)
        runBlocking { g.start() }
        return g
    }

    fun seeded(): Seeded {
        val g = empty()
        val s = g.requireSession()
        val ids = mutableMapOf<String, String>()
        val cellar = g.locations.all(s).single().id
        val rack = g.locations.create(s, LocationInput("Rack A", LocationKind.RACK, cellar, gridRows = 6, gridCols = 8))
        val fridge = g.locations.create(s, LocationInput("Kitchen fridge", LocationKind.FRIDGE, null))
        ids["cellar"] = cellar
        ids["rack"] = rack
        ids["fridge"] = fridge

        fun add(key: String, w: WineInput, vintage: Vintage, qty: Int, at: String, ml: Int = 750, price: Long? = null, from: Int? = null, to: Int? = null, source: String? = null, slots: List<Slot> = emptyList()) {
            val wineId = g.wines.createWine(s, w, allowDuplicate = true)
            val v = g.wines.addVintage(s, wineId, VintageInput(vintage, drinkFrom = from, drinkTo = to, drinkSource = if (from != null || to != null) WindowSource.CRITIC else null))
            g.inventory.addPurchase(s, PurchaseInput(v, qty, at, formatMl = ml, unitPrice = price?.let { Money(it, nzd) }, acquiredOn = LocalDate(2024, 3, 12), source = source, slots = slots))
            ids[key] = v
        }

        add("felton", WineInput("Felton Road", "Block 5 Pinot Noir", WineStyle.RED, "New Zealand", "Central Otago", appellation = "Bannockburn", grapes = listOf(GrapeShare("Pinot Noir"))), Vintage.Year(2019), 6, rack, price = 95_00, from = 2024, to = 2034, source = "Regional Wines", slots = listOf(Slot(0, 0), Slot(0, 1), Slot(0, 2)))
        add("barton", WineInput("Château Léoville Barton", "", WineStyle.RED, "France", "Bordeaux", appellation = "Saint-Julien", classification = "2ème Cru Classé", grapes = listOf(GrapeShare("Cabernet Sauvignon"), GrapeShare("Merlot"))), Vintage.Year(2010), 6, cellar, price = 185_00, from = 2025, to = 2050)
        add("ponsot", WineInput("Domaine Ponsot", "Clos de la Roche", WineStyle.RED, "France", "Burgundy", appellation = "Morey-Saint-Denis", classification = "Grand Cru", grapes = listOf(GrapeShare("Pinot Noir"))), Vintage.Year(2015), 2, cellar, ml = 1500, from = 2025, to = 2045)
        add("krug", WineInput("Krug", "Grande Cuvée", WineStyle.SPARKLING, "France", "Champagne"), Vintage.NonVintage, 3, fridge, price = 420_00)
        add("giacosa", WineInput("Bruno Giacosa", "Falletto", WineStyle.RED, "Italy", "Piedmont", appellation = "Barolo", classification = "Riserva", grapes = listOf(GrapeShare("Nebbiolo"))), Vintage.Year(2016), 12, cellar, price = 310_00, from = 2026, to = 2045, source = "Auction")
        add("coleraine", WineInput("Te Mata", "Coleraine", WineStyle.RED, "New Zealand", "Hawke's Bay"), Vintage.Year(2016), 4, rack, from = 2022, to = 2036, slots = listOf(Slot(1, 0), Slot(1, 1)))
        add("yquem", WineInput("Château d'Yquem", "", WineStyle.SWEET, "France", "Bordeaux", appellation = "Sauternes", grapes = listOf(GrapeShare("Sémillon"), GrapeShare("Sauvignon Blanc"))), Vintage.Year(2001), 3, cellar, ml = 375, from = 2010, to = 2027)
        add("grange", WineInput("Penfolds", "Grange", WineStyle.RED, "Australia", "South Australia", appellation = "Barossa Valley", grapes = listOf(GrapeShare("Shiraz"))), Vintage.Year(2008), 1, cellar, from = 2020, to = 2050)
        add("tekoko", WineInput("Cloudy Bay", "Te Koko", WineStyle.WHITE, "New Zealand", "Marlborough", grapes = listOf(GrapeShare("Sauvignon Blanc"))), Vintage.Year(2019), 2, fridge, from = 2021, to = 2027)
        add("muller", WineInput("Egon Müller", "Scharzhofberger Spätlese", WineStyle.WHITE, "Germany", "Mosel", grapes = listOf(GrapeShare("Riesling"))), Vintage.Year(2017), 3, rack, from = 2025, to = 2055, slots = listOf(Slot(2, 4)))

        val feltonLot = g.inventory.lotsOf(ids.getValue("felton")).single().id
        g.inventory.record(s, MovementDraft(MovementKind.CONSUME, feltonLot, 1, rack, null))
        val giacosaLot = g.inventory.lotsOf(ids.getValue("giacosa")).single().id
        g.inventory.record(s, MovementDraft(MovementKind.TRANSFER, giacosaLot, 2, cellar, fridge))
        return Seeded(g, ids)
    }
}
