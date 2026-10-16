package com.houseofvino.data.importexport

import com.houseofvino.core.csv.Csv
import com.houseofvino.core.model.BottleFormat
import com.houseofvino.core.model.Grapes
import com.houseofvino.core.model.MovementKind
import com.houseofvino.core.model.Vintage
import com.houseofvino.core.money.Currency
import com.houseofvino.core.money.Money
import com.houseofvino.data.Session
import com.houseofvino.data.repo.Store
import com.houseofvino.data.repo.ValuationRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * CSV exports that open cleanly in Excel, Numbers and Google Sheets. Amounts are written as plain
 * decimals with their currency in a separate column, never pre-formatted or converted.
 */
class CsvExporter(private val store: Store, private val valuations: ValuationRepository) {
    private val db get() = store.db

    fun inventory(session: Session): String {
        val values = valuations.latestValues(session).associateBy { it.vintageId to it.formatMl }
        val locations = db.locationQueries.all(session.householdId).executeAsList().associateBy { it.id }
        fun path(id: String): String {
            val names = mutableListOf<String>()
            var cur = locations[id]
            var guard = 0
            while (cur != null && guard++ < 16) {
                names += cur.name
                cur = cur.parent_id?.let(locations::get)
            }
            return names.reversed().joinToString(" > ")
        }
        val rows = db.exportQueries.inventory(session.householdId).executeAsList().map { r ->
            val value = values[r.vintage_id to r.format_ml.toInt()]
            listOf(
                r.producer, r.name, vintageText(r.vintage_kind, r.vintage_year), r.style, r.country, r.region, r.sub_region, r.appellation,
                r.classification, Grapes.display(Grapes.decode(r.grapes)), BottleFormat.label(r.format_ml.toInt()), r.qty.toString(),
                path(r.location_id), r.unit_price_minor?.let { minor(it, r.price_currency) }, r.price_currency, r.acquired_on, r.source,
                r.drink_from?.toString(), r.drink_to?.toString(), value?.amount?.toPlainString(), value?.amount?.currency?.code,
                value?.source, value?.asOf?.toString(), r.vintage_id, r.lot_id,
            )
        }
        return Csv.write(
            listOf(
                "Producer", "Wine", "Vintage", "Style", "Country", "Region", "Sub-region", "Appellation", "Classification", "Grapes",
                "Size", "Quantity", "Location", "Price per bottle", "Currency", "Purchase date", "Bought from", "Drink from", "Drink by",
                "Estimated value per bottle", "Value currency", "Valued by", "Value date", "Vintage ID", "Purchase ID",
            ),
            rows,
        )
    }

    fun tastings(session: Session): String {
        val rows = db.exportQueries.tastings(session.householdId).executeAsList().map { n ->
            listOf(
                n.tasted_on, n.producer, n.name, vintageText(n.vintage_kind, n.vintage_year), n.rating_value, n.rating_scale,
                n.appearance, n.nose, n.palate, n.finish, n.conclusion, n.notes, n.occasion, n.companions, n.food_pairing, n.place,
                n.would_buy_again?.let { if (it == 1L) "yes" else "no" }, n.id,
            )
        }
        return Csv.write(
            listOf("Date", "Producer", "Wine", "Vintage", "Rating", "Out of", "Appearance", "Nose", "Palate", "Finish", "Conclusion", "Notes", "Occasion", "With", "Food", "Place", "Would buy again", "Note ID"),
            rows,
        )
    }

    /** Purchases, sales and other stock movements with their money, for records and tax. */
    fun financial(session: Session): String {
        val zone = store.clock.timeZone()
        val rows = db.exportQueries.financial(session.householdId).executeAsList().map { m ->
            val kind = MovementKind.fromCode(m.kind)
            val unit = when (kind) {
                MovementKind.PURCHASE -> m.unit_price_minor?.let { minor(it, m.lot_currency) }
                else -> m.price_minor?.let { minor(it, m.price_currency) }
            }
            val currency = if (kind == MovementKind.PURCHASE) m.lot_currency else m.price_currency
            val total = when (kind) {
                MovementKind.PURCHASE -> m.unit_price_minor?.let { unitMinor ->
                    m.lot_currency?.let { c ->
                        val cur = Currency(c)
                        (Money(unitMinor, cur) * m.quantity + Money(m.extra_costs_minor ?: 0L, cur)).toPlainString()
                    }
                }
                else -> m.price_minor?.let { p -> m.price_currency?.let { c -> (Money(p, Currency(c)) * m.quantity).toPlainString() } }
            }
            listOf(
                Instant.fromEpochMilliseconds(m.occurred_at).toLocalDateTime(zone).date.toString(), kind.label, m.producer, m.name,
                vintageText(m.vintage_kind, m.vintage_year), BottleFormat.label(m.format_ml.toInt()), m.quantity.toString(), unit, currency,
                total, if (kind == MovementKind.PURCHASE) m.source else m.counterparty, m.reverses_id?.let { "reverses $it" } ?: m.note,
                m.sync_state, m.id,
            )
        }
        return Csv.write(
            listOf("Date", "What", "Producer", "Wine", "Vintage", "Size", "Bottles", "Price per bottle", "Currency", "Total", "Counterparty", "Note", "Sync state", "Movement ID"),
            rows,
        )
    }

    private fun vintageText(kind: String, year: Long?) = Vintage.fromStorage(kind, year).toString()

    private fun minor(amount: Long, currency: String?): String? = currency?.let { Money(amount, Currency(it)).toPlainString() }
}
