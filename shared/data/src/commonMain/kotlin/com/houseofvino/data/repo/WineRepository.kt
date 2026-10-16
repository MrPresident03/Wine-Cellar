package com.houseofvino.data.repo

import com.houseofvino.core.model.Grapes
import com.houseofvino.core.model.Ids
import com.houseofvino.core.model.Vintage
import com.houseofvino.core.money.Decimal
import com.houseofvino.core.text.TextNormalizer
import com.houseofvino.data.NotFoundException
import com.houseofvino.data.Session
import com.houseofvino.data.sync.SyncTables

class DuplicateWineException(val candidates: List<WineDetails>) :
    IllegalStateException("A wine with this producer and name already exists")

class DuplicateVintageException(val existingVintageId: String) :
    IllegalStateException("This vintage of the wine already exists")

class VintageMismatchException(val keep: Vintage, val merge: Vintage) :
    IllegalStateException("These are different vintages ($keep and $merge). Confirm to merge them anyway.")

class StockStillHeldException(val bottles: Int) :
    IllegalStateException("$bottles bottle${if (bottles == 1) " is" else "s are"} still in the cellar. Record what happened to them first.")

/**
 * Wine identities and their vintages. Wines and vintages are only ever soft-deleted, and merges
 * happen only when asked for explicitly.
 */
class WineRepository(private val store: Store) {
    private val db get() = store.db

    // ------------------------------------------------------------ reads

    fun wine(id: String): WineDetails? = db.wineQueries.byId(id).executeAsOneOrNull()?.toDetails()

    fun vintage(id: String): VintageDetails? = db.vintageQueries.byId(id).executeAsOneOrNull()?.toDetails()

    fun vintagesOf(wineId: String): List<VintageDetails> = db.vintageQueries.forWine(wineId).executeAsList().map { it.toDetails() }

    fun findDuplicateWines(session: Session, input: WineInput): List<WineDetails> =
        db.wineQueries.byIdentityKey(session.householdId, identityKey(input.producer, input.name)).executeAsList().map { it.toDetails() }

    fun findVintage(wineId: String, vintage: Vintage): VintageDetails? =
        db.vintageQueries.matching(wineId, vintage.kind, vintage.yearOrNull?.toLong()).executeAsList().firstOrNull()?.toDetails()

    fun producerSuggestions(session: Session, prefix: String, limit: Int = 8): List<String> {
        val escaped = prefix.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return db.wineQueries.producerSuggestions(session.householdId, escaped, limit.toLong()).executeAsList().map { it.producer }
    }

    // ------------------------------------------------------------ writes

    /** Creates a wine identity. If one with the same producer and name exists, asks first. */
    fun createWine(session: Session, input: WineInput, allowDuplicate: Boolean = false): String = store.write {
        session.requireWrite()
        val clean = validate(input)
        if (!allowDuplicate) {
            val dupes = findDuplicateWines(session, clean)
            if (dupes.isNotEmpty()) throw DuplicateWineException(dupes)
        }
        val id = Ids.new()
        val now = store.now()
        db.wineQueries.insert(
            id, session.householdId, clean.producer, clean.name, clean.style?.code, clean.country, clean.region, clean.subRegion,
            clean.appellation, clean.classification, Grapes.encode(clean.grapes), clean.notes,
            identityKey(clean.producer, clean.name), now, now, session.userId,
        )
        store.outbox.afterInsert(SyncTables.WINE, id, session.householdId)
        id
    }

    /** Edits the identity; this changes every vintage of the wine. */
    fun updateWine(session: Session, id: String, input: WineInput) = store.write {
        session.requireWrite()
        requireOwned(session, id)
        val clean = validate(input)
        store.outbox.beforeUpdate(SyncTables.WINE, id)
        db.wineQueries.update(
            producer = clean.producer, name = clean.name, style = clean.style?.code, country = clean.country, region = clean.region,
            subRegion = clean.subRegion, appellation = clean.appellation, classification = clean.classification,
            grapes = Grapes.encode(clean.grapes), notes = clean.notes, identityKey = identityKey(clean.producer, clean.name),
            now = store.now(), by = session.userId, id = id,
        )
        store.indexer.reindexWine(id)
    }

    fun addVintage(session: Session, wineId: String, input: VintageInput): String = store.write {
        session.requireWrite()
        requireOwned(session, wineId)
        val clean = validate(input)
        findVintage(wineId, clean.vintage)?.let { throw DuplicateVintageException(it.id) }
        val id = Ids.new()
        val now = store.now()
        db.vintageQueries.insert(
            id, session.householdId, wineId, clean.vintage.kind, clean.vintage.yearOrNull?.toLong(), clean.alcoholPct?.toString(),
            clean.drinkFrom?.toLong(), clean.drinkTo?.toLong(), clean.drinkSource?.code, clean.drinkNote, clean.notes, now, now,
            session.userId,
        )
        store.outbox.afterInsert(SyncTables.VINTAGE, id, session.householdId)
        store.indexer.reindexVintage(id)
        id
    }

    /** Returns the existing vintage of [wineId] matching [input], or creates it. */
    fun vintageFor(session: Session, wineId: String, input: VintageInput): String = store.write {
        findVintage(wineId, input.vintage)?.id ?: addVintage(session, wineId, input)
    }

    fun updateVintage(session: Session, id: String, input: VintageInput) = store.write {
        session.requireWrite()
        val current = vintage(id) ?: throw NotFoundException("Vintage")
        requireOwned(session, current.wineId)
        val clean = validate(input)
        if (clean.vintage != current.vintage) {
            findVintage(current.wineId, clean.vintage)?.let { if (it.id != id) throw DuplicateVintageException(it.id) }
        }
        store.outbox.beforeUpdate(SyncTables.VINTAGE, id)
        db.vintageQueries.update(
            kind = clean.vintage.kind, year = clean.vintage.yearOrNull?.toLong(), alcoholPct = clean.alcoholPct?.toString(),
            drinkFrom = clean.drinkFrom?.toLong(), drinkTo = clean.drinkTo?.toLong(), drinkSource = clean.drinkSource?.code,
            drinkNote = clean.drinkNote, notes = clean.notes, now = store.now(), by = session.userId, id = id,
        )
        store.indexer.reindexVintage(id)
    }

    /** Removes a vintage from the cellar views. Its tasting notes and history are kept. */
    fun deleteVintage(session: Session, id: String) = store.write {
        session.requireWrite()
        val v = vintage(id) ?: throw NotFoundException("Vintage")
        requireOwned(session, v.wineId)
        val held = bottlesHeld(id)
        if (held != 0) throw StockStillHeldException(held)
        store.outbox.beforeUpdate(SyncTables.VINTAGE, id)
        db.vintageQueries.softDelete(store.now(), session.userId, id)
        store.indexer.reindexVintage(id)
    }

    fun deleteWine(session: Session, id: String) = store.write {
        session.requireWrite()
        requireOwned(session, id)
        val vintages = db.vintageQueries.idsForWine(id).executeAsList()
        val held = vintages.sumOf { bottlesHeld(it) }
        if (held != 0) throw StockStillHeldException(held)
        vintages.forEach { vid ->
            if (vintage(vid)?.deleted == false) {
                store.outbox.beforeUpdate(SyncTables.VINTAGE, vid)
                db.vintageQueries.softDelete(store.now(), session.userId, vid)
            }
        }
        store.outbox.beforeUpdate(SyncTables.WINE, id)
        db.wineQueries.softDelete(store.now(), session.userId, id)
        store.indexer.reindexWine(id)
    }

    /**
     * Folds [mergeId] into [keepId]: purchases, tasting notes, values, wishlist entries, barcodes,
     * photos and tags move across, then [mergeId] is removed. Refuses different vintages unless
     * [confirmDifferentVintages] is set by an explicit user confirmation.
     */
    fun mergeVintages(session: Session, keepId: String, mergeId: String, confirmDifferentVintages: Boolean = false) = store.write {
        session.requireWrite()
        require(keepId != mergeId) { "Can't merge a vintage into itself" }
        val keep = vintage(keepId) ?: throw NotFoundException("Vintage")
        val merge = vintage(mergeId) ?: throw NotFoundException("Vintage")
        requireOwned(session, keep.wineId)
        requireOwned(session, merge.wineId)
        if (keep.vintage != merge.vintage && !confirmDifferentVintages) throw VintageMismatchException(keep.vintage, merge.vintage)
        val now = store.now()
        val by = session.userId

        db.lotQueries.idsForVintage(mergeId).executeAsList().forEach { lotId ->
            store.outbox.beforeUpdate(SyncTables.LOT, lotId)
            db.lotQueries.reassignVintage(keepId, now, by, lotId)
        }
        db.tastingQueries.idsForVintage(mergeId).executeAsList().forEach { store.outbox.beforeUpdate(SyncTables.TASTING, it) }
        db.tastingQueries.reassignVintage(keepId, now, by, mergeId)
        db.valuationQueries.valueIdsForVintage(mergeId).executeAsList().forEach { store.outbox.beforeUpdate(SyncTables.MARKET_VALUE, it) }
        db.valuationQueries.reassignValues(keepId, now, by, mergeId)
        db.wishlistQueries.idsForVintage(mergeId).executeAsList().forEach { store.outbox.beforeUpdate(SyncTables.WISHLIST, it) }
        db.wishlistQueries.reassignVintage(keepId, keep.wineId, now, by, mergeId)
        db.barcodeQueries.idsForVintage(mergeId).executeAsList().forEach { store.outbox.beforeUpdate(SyncTables.BARCODE, it) }
        db.barcodeQueries.reassignVintage(keepId, keep.wineId, now, by, mergeId)
        db.photoQueries.idsForOwner("vintage", mergeId).executeAsList().forEach { store.outbox.beforeUpdate(SyncTables.PHOTO, it) }
        db.photoQueries.reassignOwner(keepId, now, by, "vintage", mergeId)
        db.tagQueries.linkIdsForTarget("vintage", mergeId).executeAsList().forEach { store.outbox.beforeUpdate(SyncTables.TAG_LINK, it) }
        db.tagQueries.reassignTarget(keepId, now, by, "vintage", mergeId)

        store.outbox.beforeUpdate(SyncTables.VINTAGE, mergeId)
        db.vintageQueries.softDelete(now, by, mergeId)
        db.tastingQueries.idsForVintage(keepId).executeAsList().forEach(store.indexer::reindexTasting)
        db.wishlistQueries.idsForVintage(keepId).executeAsList().forEach(store.indexer::reindexWishlist)
        store.indexer.reindexVintage(mergeId)
        store.indexer.reindexVintage(keepId)
    }

    /**
     * Folds wine [mergeId] into [keepId]. Vintages that exist in both are merged (same vintage only);
     * the rest move across unchanged.
     */
    fun mergeWines(session: Session, keepId: String, mergeId: String) = store.write {
        session.requireWrite()
        require(keepId != mergeId) { "Can't merge a wine into itself" }
        requireOwned(session, keepId)
        requireOwned(session, mergeId)
        val now = store.now()
        val by = session.userId
        for (v in vintagesOf(mergeId)) {
            val twin = findVintage(keepId, v.vintage)
            if (twin != null) {
                mergeVintages(session, twin.id, v.id)
            } else {
                store.outbox.beforeUpdate(SyncTables.VINTAGE, v.id)
                db.vintageQueries.reassignWine(keepId, now, by, v.id)
            }
        }
        db.wishlistQueries.idsForWine(mergeId).executeAsList().forEach { store.outbox.beforeUpdate(SyncTables.WISHLIST, it) }
        db.wishlistQueries.reassignWine(keepId, now, by, mergeId)
        db.barcodeQueries.idsForWine(mergeId).executeAsList().forEach { store.outbox.beforeUpdate(SyncTables.BARCODE, it) }
        db.barcodeQueries.reassignWine(keepId, now, by, mergeId)
        db.photoQueries.idsForOwner("wine", mergeId).executeAsList().forEach { store.outbox.beforeUpdate(SyncTables.PHOTO, it) }
        db.photoQueries.reassignOwner(keepId, now, by, "wine", mergeId)
        db.tagQueries.linkIdsForTarget("wine", mergeId).executeAsList().forEach { store.outbox.beforeUpdate(SyncTables.TAG_LINK, it) }
        db.tagQueries.reassignTarget(keepId, now, by, "wine", mergeId)
        store.outbox.beforeUpdate(SyncTables.WINE, mergeId)
        db.wineQueries.softDelete(now, by, mergeId)
        store.indexer.reindexWine(mergeId)
        store.indexer.reindexWine(keepId)
    }

    // ------------------------------------------------------------ helpers

    private fun bottlesHeld(vintageId: String): Int =
        db.stockQueries.holdingsForVintage(vintageId).executeAsList().sumOf { it.qty.toInt() }

    private fun requireOwned(session: Session, wineId: String) {
        val w = db.wineQueries.byId(wineId).executeAsOneOrNull() ?: throw NotFoundException("Wine")
        if (w.household_id != session.householdId) throw NotFoundException("Wine")
    }

    companion object {
        fun identityKey(producer: String, name: String): String =
            TextNormalizer.identityKey(producer) + "|" + TextNormalizer.identityKey(name)

        fun validate(input: WineInput): WineInput {
            val producer = input.producer.cleaned(200)
            requireInput(producer != null, "producer") { "Enter the producer" }
            return input.copy(
                producer = producer!!,
                name = input.name.cleaned(200).orEmpty(),
                country = input.country.cleaned(100),
                region = input.region.cleaned(100),
                subRegion = input.subRegion.cleaned(100),
                appellation = input.appellation.cleaned(150),
                classification = input.classification.cleaned(100),
                grapes = input.grapes.distinctBy { TextNormalizer.fold(it.name) }.take(30),
                notes = input.notes.cleanedMultiline(),
            )
        }

        fun validate(input: VintageInput): VintageInput {
            input.alcoholPct?.let { requireInput(!it.isNegative && it <= Decimal.of(100), "alcohol") { "Alcohol must be between 0 and 100%" } }
            val from = input.drinkFrom
            val to = input.drinkTo
            listOfNotNull(from, to).forEach { requireInput(it in Vintage.MIN_YEAR..Vintage.MAX_YEAR, "window") { "Drinking window years look wrong" } }
            if (from != null && to != null) requireInput(from <= to, "window") { "The drinking window starts after it ends" }
            return input.copy(
                drinkNote = input.drinkNote.cleaned(300),
                notes = input.notes.cleanedMultiline(),
                drinkSource = if (from == null && to == null) null else input.drinkSource,
            )
        }
    }
}
