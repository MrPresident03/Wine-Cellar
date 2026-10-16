package com.houseofvino.data.repo

import com.houseofvino.core.model.BottleFormat
import com.houseofvino.core.model.Ids
import com.houseofvino.core.model.Ledger
import com.houseofvino.core.model.MovementDraft
import com.houseofvino.core.model.MovementError
import com.houseofvino.core.model.MovementKind
import com.houseofvino.core.money.Money
import com.houseofvino.data.NotFoundException
import com.houseofvino.data.Session
import com.houseofvino.data.sync.SyncTables

/** Extra facts recorded with a movement. */
data class MovementDetails(
    /** When it physically happened; defaults to now. */
    val occurredAt: Long? = null,
    /** Sale price per bottle (SELL) — never required. */
    val unitPrice: Money? = null,
    val counterparty: String? = null,
    val note: String? = null,
    /** Exact bottles to take out of a gridded source location. */
    val takeFromPositionIds: List<String> = emptyList(),
    /** Slots to put bottles into in a gridded destination. */
    val placeInSlots: List<Slot> = emptyList(),
)

data class MovementResult(
    val movementId: String,
    /** Positions cleared because fewer bottles remain than were placed (most recently placed first). */
    val positionsCleared: Int,
)

class SlotTakenException(val slot: Slot) : IllegalStateException("Slot ${slot.row + 1}-${slot.col + 1} is already taken")

/**
 * Stock changes. Every change is one ledger row plus balance updates, in one transaction, and is
 * checked so balances never go negative. Nothing here edits or deletes ledger rows.
 */
class InventoryRepository(private val store: Store) {
    private val db get() = store.db

    // ------------------------------------------------------------ reads

    fun lot(id: String): LotDetails? = db.lotQueries.byId(id).executeAsOneOrNull()?.takeIf { it.deleted_at == null }?.toDetails()

    fun lotsOf(vintageId: String): List<LotDetails> = db.lotQueries.forVintage(vintageId).executeAsList().map { it.toDetails() }

    fun holdings(vintageId: String): List<Holding> = db.stockQueries.holdingsForVintage(vintageId).executeAsList()
        .filter { it.qty > 0 }
        .map { Holding(it.lot_id, it.location_id, it.location_name, it.format_ml.toInt(), it.qty.toInt(), it.grid_rows != null) }

    /** Bottles of [lotId] at [locationId] as this device sees them (accepted + not yet synced). */
    fun available(lotId: String, locationId: String): Int =
        db.stockQueries.get(lotId, locationId).executeAsOneOrNull()?.let { (it.confirmed_qty + it.pending_delta).toInt() } ?: 0

    fun history(vintageId: String): List<MovementRecord> = db.movementQueries.forVintage(vintageId).executeAsList().map {
        MovementRecord(
            id = it.id, lotId = it.lot_id, kind = MovementKind.fromCode(it.kind), quantity = it.quantity.toInt(),
            fromLocationId = it.from_location_id, toLocationId = it.to_location_id, occurredAt = it.occurred_at,
            recordedAt = it.recorded_at, recordedBy = it.recorded_by, price = money(it.price_minor, it.price_currency),
            counterparty = it.counterparty, note = it.note, reversesId = it.reverses_id, syncState = it.sync_state,
            rejectReason = it.reject_reason,
        )
    }

    fun movement(id: String): MovementRecord? = db.movementQueries.byId(id).executeAsOneOrNull()?.toRecord()

    fun isReversed(movementId: String): Boolean = db.movementQueries.reversalsOf(movementId).executeAsList().isNotEmpty()

    // ------------------------------------------------------------ writes

    /** Records bottles arriving: creates the purchase (lot) and its PURCHASE/RECEIVE movement. */
    fun addPurchase(session: Session, input: PurchaseInput, details: MovementDetails = MovementDetails()): String = store.write {
        session.requireWrite()
        requireInput(input.quantity in 1..10_000, "quantity") { "Quantity must be between 1 and 10,000" }
        requireInput(BottleFormat.isValid(input.formatMl), "format") { "Bottle size must be between 50 ml and 30 L" }
        requireInput(input.kind.direction == MovementKind.Direction.IN, "kind") { "Use a purchase or receipt for new bottles" }
        val currency = input.unitPrice?.currency ?: input.extraCosts?.currency
        if (input.unitPrice != null && input.extraCosts != null) {
            requireInput(input.unitPrice.currency == input.extraCosts.currency, "price") { "Price and extra costs must be in the same currency" }
        }
        input.unitPrice?.let { requireInput(!it.isNegative, "price") { "Price can't be negative" } }
        input.extraCosts?.let { requireInput(!it.isNegative, "extraCosts") { "Extra costs can't be negative" } }
        input.caseSize?.let { requireInput(it in 1..1000, "caseSize") { "Case size looks wrong" } }
        val vintage = db.vintageQueries.byId(input.vintageId).executeAsOneOrNull()
        if (vintage == null || vintage.household_id != session.householdId || vintage.deleted_at != null) throw NotFoundException("Vintage")

        val lotId = Ids.new()
        val now = store.now()
        db.lotQueries.insert(
            lotId, session.householdId, input.vintageId, input.formatMl.toLong(), input.quantity.toLong(),
            input.unitPrice?.minor, currency?.code, input.extraCosts?.minor, input.acquiredOn?.toString(),
            input.source.cleaned(150), input.sourceRef.cleaned(150), input.caseType.code, input.caseSize?.toLong(),
            input.wishlistItemId, input.notes.cleanedMultiline(), now, now, session.userId,
        )
        store.outbox.afterInsert(SyncTables.LOT, lotId, session.householdId)
        record(
            session,
            MovementDraft(input.kind, lotId, input.quantity, fromLocationId = null, toLocationId = input.locationId),
            details.copy(placeInSlots = details.placeInSlots.ifEmpty { input.slots }),
        )
        store.indexer.reindexVintage(input.vintageId)
        lotId
    }

    /**
     * Records any stock movement. Throws [MovementError] for impossible movements (including
     * taking more bottles than are there) and leaves the database untouched in that case.
     */
    fun record(session: Session, draft: MovementDraft, details: MovementDetails = MovementDetails()): MovementResult = store.write {
        session.requireWrite()
        Ledger.validate(draft)
        val lot = db.lotQueries.byId(draft.lotId).executeAsOneOrNull()
        if (lot == null || lot.household_id != session.householdId) throw NotFoundException("Purchase")
        val from = draft.fromLocationId?.let { requireLocation(session, it) }
        val to = draft.toLocationId?.let { requireLocation(session, it) }

        if (draft.kind == MovementKind.REVERSAL) checkReversal(draft)
        if (draft.kind == MovementKind.SELL || details.unitPrice != null) {
            requireInput(draft.kind == MovementKind.SELL, "price") { "Only sales record a price" }
            details.unitPrice?.let { requireInput(!it.isNegative, "price") { "Price can't be negative" } }
        }

        // Balance check at the source.
        if (from != null) {
            val available = available(draft.lotId, from.id)
            if (available < draft.quantity) throw MovementError.InsufficientStock(available, draft.quantity)
        }
        if (to?.hasGrid == true) {
            requireInput(details.placeInSlots.size <= draft.quantity, "slots") { "More slots chosen than bottles" }
            details.placeInSlots.forEach { s ->
                requireInput(s.row in 0 until to.gridRows!! && s.col in 0 until to.gridCols!!, "slots") { "That slot isn't in ${to.name}" }
            }
            requireInput(details.placeInSlots.distinct().size == details.placeInSlots.size, "slots") { "The same slot was chosen twice" }
        } else {
            requireInput(details.placeInSlots.isEmpty(), "slots") { "This place has no grid" }
        }

        val id = Ids.new()
        val now = store.now()
        db.movementQueries.insert(
            id, session.householdId, draft.lotId, draft.kind.code, draft.quantity.toLong(), draft.fromLocationId, draft.toLocationId,
            details.occurredAt ?: now, now, session.userId, session.deviceId, details.unitPrice?.minor, details.unitPrice?.currency?.code,
            details.counterparty.cleaned(150), details.note.cleanedMultiline(2000), draft.reversesMovementId, "pending", 0,
        )
        if (from != null) {
            db.stockQueries.ensure(draft.lotId, from.id, session.householdId)
            db.stockQueries.addPending(-draft.quantity.toLong(), draft.lotId, from.id)
        }
        if (to != null) {
            db.stockQueries.ensure(draft.lotId, to.id, session.householdId)
            db.stockQueries.addPending(draft.quantity.toLong(), draft.lotId, to.id)
        }
        store.outbox.movement(session.householdId, id)

        // Positions: take the chosen bottles out of the source grid, place into the destination grid.
        var cleared = 0
        if (from != null) {
            details.takeFromPositionIds.forEach { pid ->
                val p = db.positionQueries.byId(pid).executeAsOneOrNull()
                requireInput(p != null && p.deleted_at == null && p.lot_id == draft.lotId && p.location_id == from.id, "positions") {
                    "That bottle position doesn't match this wine and place"
                }
                removePosition(session, pid)
            }
            cleared = trimPositions(session, draft.lotId, from.id)
        }
        if (to != null) {
            details.placeInSlots.forEach { slot -> placeAt(session, draft.lotId, to.id, slot) }
        }
        MovementResult(id, cleared)
    }

    /** Undo a movement by recording its exact opposite. */
    fun reverse(session: Session, movementId: String, note: String? = null): MovementResult = store.write {
        val original = db.movementQueries.byId(movementId).executeAsOneOrNull() ?: throw NotFoundException("Movement")
        if (original.household_id != session.householdId) throw NotFoundException("Movement")
        val draft = Ledger.reversalOf(
            movementId,
            MovementDraft(MovementKind.fromCode(original.kind), original.lot_id, original.quantity.toInt(), original.from_location_id, original.to_location_id),
        )
        record(session, draft, MovementDetails(note = note))
    }

    /** Stock count: sets the balance at a place to [actual] with a correction movement. */
    fun correctCount(session: Session, lotId: String, locationId: String, actual: Int, note: String? = null): MovementResult? = store.write {
        requireInput(actual in 0..10_000, "quantity") { "Count must be between 0 and 10,000" }
        val current = available(lotId, locationId)
        when {
            actual > current -> record(session, MovementDraft(MovementKind.ADJUST_IN, lotId, actual - current, null, locationId), MovementDetails(note = note))
            actual < current -> record(session, MovementDraft(MovementKind.ADJUST_OUT, lotId, current - actual, locationId, null), MovementDetails(note = note))
            else -> null
        }
    }

    /** Moves an already placed bottle to an empty slot (same lot, any gridded place it is stocked in). */
    fun moveWithinGrid(session: Session, positionId: String, to: Slot) = store.write {
        session.requireWrite()
        val p = db.positionQueries.byId(positionId).executeAsOneOrNull() ?: throw NotFoundException("Bottle position")
        if (p.household_id != session.householdId || p.deleted_at != null) throw NotFoundException("Bottle position")
        removePosition(session, positionId)
        placeAt(session, p.lot_id, p.location_id, to)
    }

    /** Places bottles already at a gridded place into slots (without moving stock). */
    fun place(session: Session, lotId: String, locationId: String, slots: List<Slot>) = store.write {
        session.requireWrite()
        val loc = requireLocation(session, locationId)
        requireInput(loc.hasGrid, "slots") { "This place has no grid" }
        val placed = db.positionQueries.countForLotAt(lotId, locationId).executeAsOne().toInt()
        requireInput(placed + slots.size <= available(lotId, locationId), "slots") { "There aren't that many unplaced bottles here" }
        slots.forEach {
            requireInput(it.row in 0 until loc.gridRows!! && it.col in 0 until loc.gridCols!!, "slots") { "That slot isn't in ${loc.name}" }
            placeAt(session, lotId, locationId, it)
        }
    }

    // ------------------------------------------------------------ helpers

    private fun checkReversal(draft: MovementDraft) {
        val targetId = draft.reversesMovementId ?: throw MovementError.MissingReversalTarget()
        val target = db.movementQueries.byId(targetId).executeAsOneOrNull() ?: throw NotFoundException("Movement")
        if (target.kind == MovementKind.REVERSAL.code) throw MovementError.CannotReverseReversal()
        if (target.sync_state == "rejected") throw MovementError.AlreadyReversed()
        if (isReversed(targetId)) throw MovementError.AlreadyReversed()
        requireInput(
            target.lot_id == draft.lotId && target.quantity.toInt() == draft.quantity &&
                target.from_location_id == draft.toLocationId && target.to_location_id == draft.fromLocationId,
            "reversal",
        ) { "A reversal must mirror the movement it undoes" }
    }

    private fun requireLocation(session: Session, id: String): LocationDetails {
        val row = db.locationQueries.byId(id).executeAsOneOrNull()
        if (row == null || row.household_id != session.householdId || row.deleted_at != null) throw NotFoundException("Place")
        return row.toDetails()
    }

    private fun placeAt(session: Session, lotId: String, locationId: String, slot: Slot) {
        val occupant = db.positionQueries.occupant(locationId, slot.row.toLong(), slot.col.toLong()).executeAsOneOrNull()
        if (occupant != null) throw SlotTakenException(slot)
        val id = Ids.new()
        val now = store.now()
        db.positionQueries.insert(id, session.householdId, locationId, lotId, slot.row.toLong(), slot.col.toLong(), now, now, session.userId)
        store.outbox.afterInsert(SyncTables.POSITION, id, session.householdId)
    }

    private fun removePosition(session: Session, positionId: String) {
        store.outbox.beforeUpdate(SyncTables.POSITION, positionId)
        db.positionQueries.softDelete(store.now(), session.userId, positionId)
    }

    /** Positions can't outnumber bottles; clear the most recently placed extras. */
    private fun trimPositions(session: Session, lotId: String, locationId: String): Int {
        val remaining = available(lotId, locationId).coerceAtLeast(0)
        val positions = db.positionQueries.forLotAt(lotId, locationId).executeAsList()
        val extra = positions.size - remaining
        if (extra <= 0) return 0
        positions.take(extra).forEach { removePosition(session, it.id) }
        return extra
    }
}
