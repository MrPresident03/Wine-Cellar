package com.houseofvino.data

import com.houseofvino.core.model.MovementDraft
import com.houseofvino.core.model.MovementError
import com.houseofvino.core.model.MovementKind
import com.houseofvino.core.money.Money
import com.houseofvino.data.repo.InvalidInputException
import com.houseofvino.data.repo.MovementDetails
import com.houseofvino.data.repo.PurchaseInput
import com.houseofvino.data.repo.Slot
import com.houseofvino.data.repo.SlotTakenException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InventoryRepositoryTest {

    @Test
    fun purchaseCreatesLotLedgerAndBalance() = withEnv {
        val v = vintage(wine("Bollinger", "Special Cuvée", region = "Champagne"), null)
        val lot = inventory.addPurchase(session, PurchaseInput(v, 6, cellarId, unitPrice = Money(89_99, nzd), source = "Glengarry"))
        assertEquals(6, inventory.available(lot, cellarId))
        val history = inventory.history(v)
        assertEquals(listOf(MovementKind.PURCHASE), history.map { it.kind })
        assertEquals("pending", history.single().syncState)
        assertEquals(Money(89_99, nzd), inventory.lot(lot)!!.unitPrice)
    }

    @Test
    fun consumingMoreThanAvailableChangesNothing() = withEnv {
        val v = vintage(wine("Cloudy Bay", "Te Koko", style = null), 2019)
        val lot = buy(v, 2)
        val movementsBefore = movementCount()
        val outboxBefore = outboxCount()
        val e = assertFailsWith<MovementError.InsufficientStock> {
            inventory.record(session, MovementDraft(MovementKind.CONSUME, lot, 3, cellarId, null))
        }
        assertEquals(2, e.available)
        assertEquals(2, inventory.available(lot, cellarId))
        assertEquals(movementsBefore, movementCount())
        assertEquals(outboxBefore, outboxCount())
    }

    @Test
    fun transferAndConsume() = withEnv {
        val fridge = place("Kitchen fridge")
        val v = vintage(wine("Felton Road", "Block 5"), 2020)
        val lot = buy(v, 6)
        inventory.record(session, MovementDraft(MovementKind.TRANSFER, lot, 2, cellarId, fridge))
        inventory.record(session, MovementDraft(MovementKind.CONSUME, lot, 1, fridge, null))
        assertEquals(4, inventory.available(lot, cellarId))
        assertEquals(1, inventory.available(lot, fridge))
        assertFailsWith<MovementError.SameLocation> { inventory.record(session, MovementDraft(MovementKind.TRANSFER, lot, 1, fridge, fridge)) }
    }

    @Test
    fun reversalRestoresAndCanOnlyHappenOnce() = withEnv {
        val v = vintage(wine("Te Mata", "Coleraine"), 2016)
        val lot = buy(v, 3)
        val opened = inventory.record(session, MovementDraft(MovementKind.CONSUME, lot, 1, cellarId, null)).movementId
        assertEquals(2, inventory.available(lot, cellarId))
        val reversal = inventory.reverse(session, opened).movementId
        assertEquals(3, inventory.available(lot, cellarId))
        assertTrue(inventory.isReversed(opened))
        assertFailsWith<MovementError.AlreadyReversed> { inventory.reverse(session, opened) }
        assertFailsWith<MovementError.CannotReverseReversal> { inventory.reverse(session, reversal) }
        // History keeps all three entries.
        assertEquals(3, inventory.history(v).size)
    }

    @Test
    fun reversingAPurchaseNeedsTheBottlesToStillBeThere() = withEnv {
        val v = vintage(wine("Mount Edward"), 2021)
        val lot = buy(v, 1)
        val purchase = inventory.history(v).single().id
        inventory.record(session, MovementDraft(MovementKind.CONSUME, lot, 1, cellarId, null))
        assertFailsWith<MovementError.InsufficientStock> { inventory.reverse(session, purchase) }
    }

    @Test
    fun stockCountCorrections() = withEnv {
        val v = vintage(wine("Ata Rangi"), 2018)
        val lot = buy(v, 6)
        assertEquals(MovementKind.ADJUST_OUT, inventory.correctCount(session, lot, cellarId, 5)!!.let { inventory.movement(it.movementId)!!.kind })
        assertEquals(MovementKind.ADJUST_IN, inventory.correctCount(session, lot, cellarId, 7)!!.let { inventory.movement(it.movementId)!!.kind })
        assertNull(inventory.correctCount(session, lot, cellarId, 7))
        assertEquals(7, inventory.available(lot, cellarId))
    }

    @Test
    fun salesMayRecordAPriceOtherMovementsMayNot() = withEnv {
        val v = vintage(wine("Henschke", "Hill of Grace"), 2012)
        val lot = buy(v, 2)
        inventory.record(session, MovementDraft(MovementKind.SELL, lot, 1, cellarId, null), MovementDetails(unitPrice = Money(950_00, nzd), counterparty = "Auction"))
        assertEquals(Money(950_00, nzd), inventory.history(v).first { it.kind == MovementKind.SELL }.price)
        assertFailsWith<InvalidInputException> {
            inventory.record(session, MovementDraft(MovementKind.CONSUME, lot, 1, cellarId, null), MovementDetails(unitPrice = Money(1, nzd)))
        }
    }

    @Test
    fun gridPositions() = withEnv {
        val rack = place("Rack A", rows = 3, cols = 4)
        val v = vintage(wine("Craggy Range", "Le Sol"), 2019)
        val lot = inventory.addPurchase(session, PurchaseInput(v, 3, rack, slots = listOf(Slot(0, 0), Slot(0, 1))))
        assertEquals(2, db.positionQueries.inLocation(rack).executeAsList().size)
        // A slot can hold one bottle.
        val other = vintage(wine("Craggy Range", "Sophia"), 2019)
        assertFailsWith<SlotTakenException> { inventory.addPurchase(session, PurchaseInput(other, 1, rack, slots = listOf(Slot(0, 1)))) }
        // Slots outside the grid are refused.
        assertFailsWith<InvalidInputException> { inventory.place(session, lot, rack, listOf(Slot(5, 5))) }
        // Opening a specific placed bottle frees its slot.
        val p = db.positionQueries.occupant(rack, 0, 0).executeAsOne()
        inventory.record(session, MovementDraft(MovementKind.CONSUME, lot, 1, rack, null), MovementDetails(takeFromPositionIds = listOf(p.id)))
        assertNull(db.positionQueries.occupant(rack, 0, 0).executeAsOneOrNull())
        // Positions never exceed bottles: opening the last two clears the remaining position.
        val result = inventory.record(session, MovementDraft(MovementKind.CONSUME, lot, 2, rack, null))
        assertEquals(1, result.positionsCleared)
        assertTrue(db.positionQueries.inLocation(rack).executeAsList().isEmpty())
    }

    @Test
    fun ledgerIsTheSourceOfTruthAfterRandomOperations() = withEnv {
        val places = listOf(cellarId, place("Fridge"), place("Rack", rows = 10, cols = 10), place("Off-site", parent = null))
        val vintages = (1..6).map { vintage(wine("Producer $it"), 2000 + it) }
        val lots = mutableListOf<String>()
        val r = Random(42)
        var rejected = 0
        repeat(600) {
            try {
                when {
                    lots.isEmpty() || r.nextInt(10) == 0 -> lots += buy(vintages.random(r), r.nextInt(1, 13), places.random(r))
                    else -> {
                        val lot = lots.random(r)
                        when (r.nextInt(6)) {
                            0 -> inventory.record(session, MovementDraft(MovementKind.TRANSFER, lot, r.nextInt(1, 4), places.random(r), places.random(r)))
                            1 -> inventory.record(session, MovementDraft(MovementKind.CONSUME, lot, r.nextInt(1, 3), places.random(r), null))
                            2 -> inventory.record(session, MovementDraft(MovementKind.SELL, lot, r.nextInt(1, 3), places.random(r), null))
                            3 -> inventory.correctCount(session, lot, places.random(r), r.nextInt(0, 8))
                            4 -> inventory.history(db.lotQueries.byId(lot).executeAsOne().vintage_id).filter { it.kind != MovementKind.REVERSAL }
                                .randomOrNull(r)?.let { inventory.reverse(session, it.id) }
                            else -> inventory.record(session, MovementDraft(MovementKind.LOSS, lot, 1, places.random(r), null))
                        }
                    }
                }
            } catch (e: MovementError) {
                rejected++
            } catch (e: InvalidInputException) {
                rejected++
            }
        }
        println("random ledger test: ${movementCount()} movements, $rejected rejected attempts")
        // Recompute every balance from the ledger and compare with the stored balances.
        val fromLedger = db.movementQueries.ledgerBalances(session.householdId).executeAsList()
            .groupBy { it.lot_id to it.location_id }.mapValues { (_, rows) -> rows.sumOf { it.qty ?: 0L } }
        val stored = db.stockQueries.all(session.householdId).executeAsList()
            .associate { (it.lot_id to it.location_id) to (it.confirmed_qty + it.pending_delta) }
        val keys = fromLedger.keys + stored.keys
        keys.forEach { k -> assertEquals(fromLedger[k] ?: 0L, stored[k] ?: 0L, "balance for $k") }
        assertTrue(stored.values.all { it >= 0 }, "no negative balances")
        assertHoldingsConsistent()
        assertTrue(db.stockQueries.negativeOrConflicting(session.householdId).executeAsList().isEmpty())
        // Positions never outnumber bottles.
        places.forEach { loc ->
            db.positionQueries.inLocation(loc).executeAsList().groupBy { it.lot_id }.forEach { (lot, ps) ->
                assertTrue(ps.size <= inventory.available(lot, loc))
            }
        }
    }

    @Test
    fun everyMovementIsQueuedForSync() = withEnv {
        val v = vintage(wine("Pyramid Valley"), 2020)
        val lot = buy(v, 4)
        inventory.record(session, MovementDraft(MovementKind.CONSUME, lot, 1, cellarId, null))
        assertEquals(2L, scalarLong("SELECT count(*) FROM sync_outbox WHERE op = 'movement'"))
    }
}
