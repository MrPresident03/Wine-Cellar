package com.houseofvino.data.repo

import com.houseofvino.core.model.BottleFormat
import com.houseofvino.core.model.Ids
import com.houseofvino.core.money.Currency
import com.houseofvino.core.money.Decimal
import com.houseofvino.core.money.ExchangeRate
import com.houseofvino.core.money.Money
import com.houseofvino.data.NotFoundException
import com.houseofvino.data.Session
import com.houseofvino.data.sync.SyncTables
import kotlinx.datetime.LocalDate

/** An estimated value: always with where it came from and when. */
data class MarketValue(val id: String, val vintageId: String, val formatMl: Int, val amount: Money, val source: String, val sourceRef: String?, val asOf: LocalDate, val note: String?)

data class StoredRate(val id: String, val rate: ExchangeRate)

/**
 * Market values and exchange rates. The app never generates either; they come from the user, an
 * import file, or (later) a provider the user turned on — and the source is always recorded.
 */
class ValuationRepository(private val store: Store) {
    private val db get() = store.db

    fun valuesFor(vintageId: String): List<MarketValue> = db.valuationQueries.valuesFor(vintageId).executeAsList().map {
        MarketValue(it.id, it.vintage_id, it.format_ml.toInt(), Money(it.amount_minor, Currency(it.currency)), it.source, it.source_ref, LocalDate.parse(it.as_of), it.note)
    }

    /** Newest value per vintage and bottle size. */
    fun latestValues(session: Session): List<MarketValue> = db.valuationQueries.latestValues(session.householdId).executeAsList().map {
        MarketValue(it.id, it.vintage_id, it.format_ml.toInt(), Money(it.amount_minor, Currency(it.currency)), it.source, it.source_ref, LocalDate.parse(it.as_of), it.note)
    }

    fun addValue(session: Session, vintageId: String, formatMl: Int, amount: Money, source: String, asOf: LocalDate, sourceRef: String? = null, note: String? = null): String = store.write {
        session.requireWrite()
        val v = db.vintageQueries.byId(vintageId).executeAsOneOrNull()
        if (v == null || v.household_id != session.householdId) throw NotFoundException("Vintage")
        requireInput(!amount.isNegative, "amount") { "A value can't be negative" }
        requireInput(BottleFormat.isValid(formatMl), "format") { "Bottle size must be between 50 ml and 30 L" }
        val src = source.cleaned(150)
        requireInput(src != null, "source") { "Say where this value comes from" }
        requireInput(asOf <= store.clock.today(), "asOf") { "The date can't be in the future" }
        val id = Ids.new()
        val now = store.now()
        db.valuationQueries.insertValue(id, session.householdId, vintageId, formatMl.toLong(), amount.minor, amount.currency.code, src!!, sourceRef.cleaned(500), asOf.toString(), note.cleaned(300), now, now, session.userId)
        store.outbox.afterInsert(SyncTables.MARKET_VALUE, id, session.householdId)
        id
    }

    fun deleteValue(session: Session, id: String) = store.write {
        session.requireWrite()
        store.outbox.beforeUpdate(SyncTables.MARKET_VALUE, id)
        db.valuationQueries.softDeleteValue(store.now(), session.userId, id)
    }

    fun rates(session: Session): List<StoredRate> = db.valuationQueries.rates(session.householdId).executeAsList().mapNotNull {
        val r = Decimal.parseOrNull(it.rate) ?: return@mapNotNull null
        runCatching { StoredRate(it.id, ExchangeRate(Currency(it.from_currency), Currency(it.to_currency), r, LocalDate.parse(it.as_of), it.source)) }.getOrNull()
    }

    fun addRate(session: Session, rate: ExchangeRate): String = store.write {
        session.requireWrite()
        requireInput(rate.from != rate.to, "rate") { "Choose two different currencies" }
        val id = Ids.new()
        val now = store.now()
        db.valuationQueries.insertRate(id, session.householdId, rate.from.code, rate.to.code, rate.rate.toString(), rate.asOf.toString(), rate.source.take(100), now, now, session.userId)
        store.outbox.afterInsert(SyncTables.FX_RATE, id, session.householdId)
        id
    }

    fun deleteRate(session: Session, id: String) = store.write {
        session.requireWrite()
        store.outbox.beforeUpdate(SyncTables.FX_RATE, id)
        db.valuationQueries.softDeleteRate(store.now(), session.userId, id)
    }
}
