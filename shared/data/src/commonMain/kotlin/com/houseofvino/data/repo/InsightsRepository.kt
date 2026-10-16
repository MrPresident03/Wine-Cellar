package com.houseofvino.data.repo

import com.houseofvino.core.model.BottleFormat
import com.houseofvino.core.model.DrinkStatus
import com.houseofvino.core.model.DrinkingWindow
import com.houseofvino.core.model.Grapes
import com.houseofvino.core.model.MovementKind
import com.houseofvino.core.model.Vintage
import com.houseofvino.core.model.WineStyle
import com.houseofvino.core.money.Currency
import com.houseofvino.core.money.Money
import com.houseofvino.core.money.MoneyTotals
import com.houseofvino.data.Session
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

data class Breakdown(val label: String, val bottles: Int)

data class WindowWine(val vintageId: String, val producer: String, val name: String, val vintage: Vintage, val bottles: Int, val drinkTo: Int?)

data class MonthCount(val year: Int, val month: Int, val opened: Int, val sold: Int, val other: Int)

data class ValueSummary(
    /** Latest estimate × bottles held, per currency, for bottles that have an estimate. */
    val totals: List<Money>,
    val converted: MoneyTotals.Converted?,
    val valuedBottles: Int,
    val unvaluedBottles: Int,
    /** Where the estimates came from, with the date range of the newest value per wine. */
    val sources: List<Triple<String, LocalDate, LocalDate>>,
)

data class Insights(
    val bottles: Int,
    val wines: Int,
    val vintages: Int,
    /** Volume in standard 750 ml bottles, to one decimal. */
    val standardBottlesTenths: Int,
    val homeCurrency: Currency,
    /** What the bottles still held cost, per currency, and in the home currency where rates allow. */
    val costHeld: List<Money>,
    val costHeldConverted: MoneyTotals.Converted?,
    val unpricedBottles: Int,
    /** Everything ever bought (including extra costs), per currency. */
    val spentAllTime: List<Money>,
    val value: ValueSummary,
    val windows: Map<DrinkStatus, Int>,
    val drinkSoon: List<WindowWine>,
    val pastWindow: List<WindowWine>,
    val months: List<MonthCount>,
    val byStyle: List<Breakdown>,
    val byCountry: List<Breakdown>,
    val byRegion: List<Breakdown>,
    val byGrape: List<Breakdown>,
    val byDecade: List<Breakdown>,
    val byFormat: List<Breakdown>,
    val byLocation: List<Breakdown>,
)

/** Collection insights computed from the ledger-derived balances, never from guesses. */
class InsightsRepository(private val store: Store, private val valuations: ValuationRepository, private val locations: LocationRepository) {
    private val db get() = store.db

    fun compute(session: Session, homeCurrency: Currency): Insights {
        val today = store.clock.today()
        val year = today.year
        val rows = db.insightsQueries.holdings(session.householdId).executeAsList()
        val bottles = rows.sumOf { it.qty.toInt() }
        val rates = valuations.rates(session).map { it.rate }

        // Cost of what's held.
        val cost = MoneyTotals()
        var unpriced = 0
        rows.forEach { r ->
            if (r.unit_price_minor != null && r.price_currency != null) cost.add(Money(r.unit_price_minor, Currency(r.price_currency)) * r.qty)
            else unpriced += r.qty.toInt()
        }
        val spent = MoneyTotals()
        db.insightsQueries.spending(session.householdId).executeAsList().forEach { l ->
            val c = Currency(l.price_currency!!)
            l.unit_price_minor?.let { spent.add(Money(it, c) * l.quantity_acquired) }
            l.extra_costs_minor?.let { spent.add(Money(it, c)) }
        }

        // Estimated value from the newest estimate per vintage and size.
        val latest = valuations.latestValues(session).associateBy { it.vintageId to it.formatMl }
        val value = MoneyTotals()
        var valued = 0
        val usedValues = mutableListOf<MarketValue>()
        rows.forEach { r ->
            val mv = latest[r.vintage_id to r.format_ml.toInt()]
            if (mv != null) {
                value.add(mv.amount * r.qty)
                valued += r.qty.toInt()
                usedValues += mv
            }
        }
        val sources = usedValues.distinctBy { it.id }.groupBy { it.source }.map { (src, vs) -> Triple(src, vs.minOf { it.asOf }, vs.maxOf { it.asOf }) }
            .sortedByDescending { it.third }

        // Drinking windows, by bottles.
        val byVintage = rows.groupBy { it.vintage_id }
        val windows = DrinkStatus.entries.associateWith { 0 }.toMutableMap()
        val soon = mutableListOf<WindowWine>()
        val past = mutableListOf<WindowWine>()
        byVintage.forEach { (vid, rs) ->
            val r = rs.first()
            val n = rs.sumOf { it.qty.toInt() }
            val status = DrinkingWindow.statusOf(r.drink_from?.toInt(), r.drink_to?.toInt(), year)
            windows[status] = windows.getValue(status) + n
            val ww = WindowWine(vid, r.producer, r.name, Vintage.fromStorage(r.vintage_kind, r.vintage_year), n, r.drink_to?.toInt())
            if (status == DrinkStatus.DRINK_SOON) soon += ww
            if (status == DrinkStatus.PAST_PEAK) past += ww
        }

        // Last 12 months of bottles leaving the cellar.
        val since = today.minus(DatePeriod(months = 11)).let { LocalDate(it.year, it.month, 1) }
        val sinceMillis = since.atStartOfDayIn(store.clock.timeZone()).toEpochMilliseconds()
        val months = (0 until 12).map { i ->
            val d = LocalDate(since.year, since.month, 1).let { base -> base.plusMonths(i) }
            MonthCount(d.year, d.month.ordinal + 1, 0, 0, 0)
        }.toMutableList()
        db.insightsQueries.outgoing(session.householdId, sinceMillis).executeAsList().forEach { m ->
            val d = Instant.fromEpochMilliseconds(m.occurred_at).toLocalDateTime(store.clock.timeZone()).date
            val idx = months.indexOfFirst { it.year == d.year && it.month == d.month.ordinal + 1 }
            if (idx >= 0) {
                val q = m.quantity.toInt()
                months[idx] = when (MovementKind.fromCode(m.kind)) {
                    MovementKind.CONSUME -> months[idx].copy(opened = months[idx].opened + q)
                    MovementKind.SELL -> months[idx].copy(sold = months[idx].sold + q)
                    else -> months[idx].copy(other = months[idx].other + q)
                }
            }
        }

        fun breakdown(key: (com.houseofvino.data.db.Holdings) -> String?): List<Breakdown> =
            rows.groupBy { key(it) ?: "Not recorded" }.map { (k, rs) -> Breakdown(k, rs.sumOf { it.qty.toInt() }) }.sortedByDescending { it.bottles }

        val locs = locations.all(session).associateBy { it.id }
        fun topLevel(id: String): String {
            var cur = locs[id]
            var guard = 0
            while (cur?.parentId != null && guard++ < 16) cur = locs[cur.parentId]
            return cur?.name ?: "Removed place"
        }

        return Insights(
            bottles = bottles,
            wines = rows.map { it.wine_id }.distinct().size,
            vintages = byVintage.size,
            standardBottlesTenths = rows.sumOf { BottleFormat.standardEquivalentTenths(it.format_ml.toInt()) * it.qty.toInt() },
            homeCurrency = homeCurrency,
            costHeld = cost.toList(),
            costHeldConverted = if (cost.isEmpty) null else cost.convertTo(homeCurrency, rates),
            unpricedBottles = unpriced,
            spentAllTime = spent.toList(),
            value = ValueSummary(value.toList(), if (value.isEmpty) null else value.convertTo(homeCurrency, rates), valued, bottles - valued, sources),
            windows = windows,
            drinkSoon = soon.sortedBy { it.drinkTo ?: Int.MAX_VALUE },
            pastWindow = past.sortedBy { it.drinkTo ?: Int.MAX_VALUE },
            months = months,
            byStyle = breakdown { WineStyle.fromCode(it.style)?.label },
            byCountry = breakdown { it.country },
            byRegion = breakdown { it.region },
            byGrape = rows.flatMap { r -> Grapes.decode(r.grapes).ifEmpty { listOf(null) }.map { g -> (g?.name ?: "Not recorded") to r.qty.toInt() } }
                .groupBy({ it.first }, { it.second }).map { (k, v) -> Breakdown(k, v.sum()) }.sortedByDescending { it.bottles },
            byDecade = breakdown { r ->
                when (val v = Vintage.fromStorage(r.vintage_kind, r.vintage_year)) {
                    is Vintage.Year -> "${v.year / 10 * 10}s"
                    Vintage.NonVintage -> "Non-vintage"
                    Vintage.Unknown -> null
                }
            },
            byFormat = breakdown { BottleFormat.label(it.format_ml.toInt()) },
            byLocation = breakdown { topLevel(it.location_id) },
        )
    }
}

private fun LocalDate.plusMonths(n: Int): LocalDate {
    val total = year * 12 + (month.ordinal) + n
    return LocalDate(total / 12, total % 12 + 1, 1)
}
