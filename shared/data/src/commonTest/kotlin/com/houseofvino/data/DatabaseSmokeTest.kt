package com.houseofvino.data

import app.cash.sqldelight.db.QueryResult
import com.houseofvino.data.db.BundledSqlDriver
import com.houseofvino.data.db.DatabaseFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatabaseSmokeTest {

    private fun BundledSqlDriver.queryString(sql: String): String? =
        executeQuery(null, sql, { c -> QueryResult.Value(if (c.next().value) c.getString(0) else null) }, 0).value

    @Test
    fun bundledSqliteHasFts5AndJson() {
        val opened = DatabaseFactory.open(":memory:")
        val version = opened.driver.queryString("SELECT sqlite_version()")
        println("Bundled SQLite version: $version")
        assertTrue(version!!.split(".")[1].toInt() >= 38, "SQLite $version is older than 3.38")
        assertEquals("1", opened.driver.queryString("SELECT json_valid('[1,2]')"))
        opened.driver.execute(null, "INSERT INTO search_fts(rowid, producer, name, vintage, region, grapes, tags, notes) VALUES (1, 'Château Lafite Rothschild', '', '2010', 'Pauillac', 'Cabernet Sauvignon', '', '')", 0)
        val hit = opened.driver.queryString("SELECT rowid FROM search_fts WHERE search_fts MATCH '\"chateau\"* AND \"pau\"*'")
        assertEquals("1", hit)
        opened.driver.close()
    }

    @Test
    fun schemaVersionIsRecorded() {
        val opened = DatabaseFactory.open(":memory:")
        assertEquals(DatabaseFactory.schemaVersion, opened.driver.userVersion())
        opened.driver.close()
    }

    @Test
    fun ledgerIsAppendOnly() {
        val opened = DatabaseFactory.open(":memory:")
        val d = opened.driver
        d.execute(null, "INSERT INTO household(id, name, created_at, updated_at) VALUES ('h', 'Home', 0, 0)", 0)
        d.execute(null, "INSERT INTO wine(id, household_id, producer, identity_key, created_at, updated_at) VALUES ('w', 'h', 'P', 'p', 0, 0)", 0)
        d.execute(null, "INSERT INTO wine_vintage(id, household_id, wine_id, vintage_kind, vintage_year, created_at, updated_at) VALUES ('v', 'h', 'w', 'year', 2015, 0, 0)", 0)
        d.execute(null, "INSERT INTO location(id, household_id, kind, name, created_at, updated_at) VALUES ('l', 'h', 'site', 'Cellar', 0, 0)", 0)
        d.execute(null, "INSERT INTO lot(id, household_id, vintage_id, quantity_acquired, created_at, updated_at) VALUES ('lot', 'h', 'v', 6, 0, 0)", 0)
        d.execute(null, "INSERT INTO inventory_movement(id, household_id, lot_id, kind, quantity, to_location_id, occurred_at, recorded_at, device_id) VALUES ('m', 'h', 'lot', 'purchase', 6, 'l', 0, 0, 'dev')", 0)
        assertFailsWith<Exception> { d.execute(null, "UPDATE inventory_movement SET quantity = 7 WHERE id = 'm'", 0) }
        assertFailsWith<Exception> { d.execute(null, "DELETE FROM inventory_movement WHERE id = 'm'", 0) }
        // Sync state may change.
        assertEquals(1L, d.execute(null, "UPDATE inventory_movement SET sync_state = 'confirmed' WHERE id = 'm'", 0).value)
        d.close()
    }
}
