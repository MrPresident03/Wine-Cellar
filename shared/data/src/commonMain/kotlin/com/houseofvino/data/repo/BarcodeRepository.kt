package com.houseofvino.data.repo

import com.houseofvino.core.model.Ids
import com.houseofvino.core.model.Vintage
import com.houseofvino.data.Session
import com.houseofvino.data.sync.SyncTables

data class BarcodeCandidate(val wineId: String, val vintageId: String?, val producer: String, val name: String, val vintage: Vintage?, val formatMl: Int?)

/** Barcodes the user has seen on their own bottles. A code can point at several wines or vintages. */
class BarcodeRepository(private val store: Store) {
    private val db get() = store.db

    fun candidates(session: Session, gtin: String): List<BarcodeCandidate> =
        db.barcodeQueries.forGtin(session.householdId, gtin).executeAsList().map {
            BarcodeCandidate(it.wine_id, it.vintage_id, it.producer, it.name, it.vintage_kind?.let { k -> Vintage.fromStorage(k, it.vintage_year) }, it.format_ml?.toInt())
        }

    fun remember(session: Session, gtin: String, wineId: String, vintageId: String?, formatMl: Int?) = store.write {
        session.requireWrite()
        if (db.barcodeQueries.existing(session.householdId, gtin, wineId, vintageId).executeAsOneOrNull() == null) {
            val id = Ids.new()
            val now = store.now()
            db.barcodeQueries.insert(id, session.householdId, gtin, wineId, vintageId, formatMl?.toLong(), now, now, session.userId)
            store.outbox.afterInsert(SyncTables.BARCODE, id, session.householdId)
        }
    }
}
