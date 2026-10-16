package com.houseofvino.data

import app.cash.sqldelight.db.QueryResult
import com.houseofvino.core.model.LocationKind
import com.houseofvino.core.model.Vintage
import com.houseofvino.core.model.WineStyle
import com.houseofvino.core.money.Currency
import com.houseofvino.core.money.Money
import com.houseofvino.data.db.DatabaseFactory
import com.houseofvino.data.repo.HouseholdRepository
import com.houseofvino.data.repo.InventoryRepository
import com.houseofvino.data.repo.LocationInput
import com.houseofvino.data.repo.LocationRepository
import com.houseofvino.data.repo.PurchaseInput
import com.houseofvino.data.repo.Store
import com.houseofvino.data.repo.TagRepository
import com.houseofvino.data.repo.VintageInput
import com.houseofvino.data.repo.WineInput
import com.houseofvino.data.repo.WineRepository
import com.houseofvino.data.search.SearchEngine

/** A fresh in-memory database with every repository, a fixed clock and a local household. */
class TestEnv(startMillis: Long = 1_790_000_000_000L) {
    val clock = FixedClock(startMillis)
    private val opened = DatabaseFactory.open(":memory:")
    val driver = opened.driver
    val db = opened.database
    val store = Store(driver, db, clock)
    val households = HouseholdRepository(store)
    val wines = WineRepository(store)
    val inventory = InventoryRepository(store)
    val locations = LocationRepository(store)
    val tags = TagRepository(store)
    val search = SearchEngine(store)
    val session: Session = households.startSession(Currency("NZD"))
    val cellarId: String = locations.all(session).single().id
    val nzd = Currency("NZD")

    fun close() = driver.close()

    fun wine(producer: String, name: String = "", style: WineStyle? = WineStyle.RED, region: String? = null, country: String? = null) =
        wines.createWine(session, WineInput(producer = producer, name = name, style = style, region = region, country = country), allowDuplicate = true)

    fun vintage(wineId: String, year: Int?, drinkFrom: Int? = null, drinkTo: Int? = null): String =
        wines.addVintage(session, wineId, VintageInput(if (year == null) Vintage.NonVintage else Vintage.Year(year), drinkFrom = drinkFrom, drinkTo = drinkTo))

    fun buy(vintageId: String, qty: Int, at: String = cellarId, priceCents: Long? = null, formatMl: Int = 750): String =
        inventory.addPurchase(session, PurchaseInput(vintageId, qty, at, formatMl = formatMl, unitPrice = priceCents?.let { Money(it, nzd) }))

    fun place(name: String, parent: String? = cellarId, rows: Int? = null, cols: Int? = null, kind: LocationKind = LocationKind.RACK) =
        locations.create(session, LocationInput(name, kind, parent, rows, cols))

    fun scalarLong(sql: String): Long =
        driver.executeQuery(null, sql, { c -> QueryResult.Value(if (c.next().value) c.getLong(0) ?: 0L else 0L) }, 0).value

    fun outboxCount(): Long = scalarLong("SELECT count(*) FROM sync_outbox")

    /** The trigger-maintained per-vintage totals must equal the sum of stock rows. */
    fun assertHoldingsConsistent() {
        val drift = scalarLong(
            """
            SELECT count(*) FROM (
                SELECT l.vintage_id, l.format_ml, sum(s.confirmed_qty + s.pending_delta) AS q
                FROM stock s JOIN lot l ON l.id = s.lot_id GROUP BY l.vintage_id, l.format_ml
            ) a
            LEFT JOIN vintage_holding h ON h.vintage_id = a.vintage_id AND h.format_ml = a.format_ml
            WHERE coalesce(h.qty, 0) <> a.q
            """.trimIndent(),
        ) + scalarLong(
            """
            SELECT count(*) FROM vintage_holding h WHERE h.qty <> 0 AND NOT EXISTS (
                SELECT 1 FROM stock s JOIN lot l ON l.id = s.lot_id WHERE l.vintage_id = h.vintage_id AND l.format_ml = h.format_ml
            )
            """.trimIndent(),
        )
        kotlin.test.assertEquals(0L, drift, "vintage_holding drifted from stock")
    }
    fun movementCount(): Long = scalarLong("SELECT count(*) FROM inventory_movement")
}

/** Test bodies must return Unit (JUnit and Kotlin/Native test runners require it). */
inline fun withEnv(block: TestEnv.() -> Unit) {
    val env = TestEnv()
    try {
        env.block()
    } finally {
        env.close()
    }
}
