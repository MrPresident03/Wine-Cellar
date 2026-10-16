package com.houseofvino.data

import com.houseofvino.data.repo.WineInput
import com.houseofvino.data.sync.SyncTables
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OutboxTest {

    @Test
    fun editsToOneRecordCoalesce() = withEnv {
        val w = wines.createWine(session, WineInput("Neudorf", "Moutere"))
        repeat(3) { wines.updateWine(session, w, WineInput("Neudorf", "Moutere Chardonnay $it")) }
        val rows = db.outboxQueries.all().executeAsList().filter { it.entity == "wine" && it.entity_id == w }
        assertEquals(1, rows.size)
        assertEquals(4L, rows.single().local_version)
        assertEquals(0L, rows.single().base_rev)
        assertNull(rows.single().base_json) // never synced: nothing to merge against
    }

    @Test
    fun syncedRowsKeepTheirBaseSnapshot() = withEnv {
        val w = wines.createWine(session, WineInput("Neudorf", "Moutere"))
        // Pretend the server accepted it at revision 7 and the queue was cleared.
        driver.execute(null, "UPDATE wine SET rev = 7 WHERE id = '$w'", 0)
        driver.execute(null, "DELETE FROM sync_outbox", 0)
        wines.updateWine(session, w, WineInput("Neudorf", "Moutere Chardonnay"))
        wines.updateWine(session, w, WineInput("Neudorf", "Moutere Chardonnay 2"))
        val row = db.outboxQueries.pendingUpsert("wine", w).executeAsOne()
        assertEquals(7L, row.base_rev)
        val base = assertNotNull(row.base_json)
        // The snapshot is the row as the server had it, before either edit.
        assertEquals(true, base.contains("\"name\":\"Moutere\""))
    }

    @Test
    fun descriptorsMatchTheDatabaseSchema() = withEnv {
        for (table in SyncTables.ALL) {
            val actual = mutableListOf<String>()
            driver.executeQuery(null, "PRAGMA table_info(${table.name})", { c ->
                while (c.next().value) actual += c.getString(1)!!
                app.cash.sqldelight.db.QueryResult.Unit
            }, 0)
            assertEquals(
                actual.toSet(),
                (table.columnNames + table.deviceOnly).toSet(),
                "columns of ${table.name}",
            )
        }
    }
}
