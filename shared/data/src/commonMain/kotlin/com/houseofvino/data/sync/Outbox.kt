package com.houseofvino.data.sync

import app.cash.sqldelight.db.SqlDriver
import com.houseofvino.core.model.Ids
import com.houseofvino.data.AppClock
import com.houseofvino.data.db.HouseOfVinoDatabase

/**
 * Queues local changes for upload. Must be called inside the same transaction as the change, so
 * a change is never saved without its outbox entry (or the other way round).
 *
 * Edits to one record coalesce into a single entry; the entry keeps the row as it was at its last
 * sync ([beforeUpdate] snapshots it) so the sync engine can do a three-way merge.
 */
class Outbox(private val driver: SqlDriver, private val db: HouseOfVinoDatabase, private val clock: AppClock) {

    /** Call before updating (or soft-deleting) an existing row. */
    fun beforeUpdate(table: SyncTable, id: String) {
        require(table.upsert) { "${table.name} does not sync as upserts" }
        val snapshot = table.columns.joinToString(", ") { "'${it.name}', ${it.name}" }
        driver.execute(
            null,
            """
            INSERT OR IGNORE INTO sync_outbox(op_id, household_id, entity, entity_id, op, base_rev, base_json, created_at)
            SELECT ?, ${table.householdColumn}, ?, id, 'upsert', rev, CASE WHEN rev > 0 THEN json_object($snapshot) END, ?
            FROM ${table.name} WHERE id = ?
            """.trimIndent(),
            4,
        ) {
            bindString(0, Ids.new())
            bindString(1, table.name)
            bindLong(2, clock.nowMillis())
            bindString(3, id)
        }
        db.outboxQueries.bumpVersion(table.name, id)
    }

    /** Call after inserting a brand-new row. */
    fun afterInsert(table: SyncTable, id: String, householdId: String) {
        require(table.upsert) { "${table.name} does not sync as upserts" }
        driver.execute(
            null,
            "INSERT OR IGNORE INTO sync_outbox(op_id, household_id, entity, entity_id, op, base_rev, created_at) VALUES (?, ?, ?, ?, 'upsert', 0, ?)",
            5,
        ) {
            bindString(0, Ids.new())
            bindString(1, householdId)
            bindString(2, table.name)
            bindString(3, id)
            bindLong(4, clock.nowMillis())
        }
        db.outboxQueries.bumpVersion(table.name, id)
    }

    fun movement(householdId: String, movementId: String) {
        db.outboxQueries.insertMovement(Ids.new(), householdId, movementId, clock.nowMillis())
    }

    fun photoUpload(householdId: String, photoId: String) {
        db.outboxQueries.insertPhoto(Ids.new(), householdId, photoId, clock.nowMillis())
    }
}
