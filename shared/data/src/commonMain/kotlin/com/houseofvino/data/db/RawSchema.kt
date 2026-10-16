package com.houseofvino.data.db

import app.cash.sqldelight.db.QueryResult

/**
 * Schema objects SQLDelight's parser can't express (triggers whose subqueries use NEW/OLD).
 * Installed by [DatabaseFactory] inside the open/migrate transaction. Bump [VERSION] when they
 * change: old versions are dropped and derived tables rebuilt from the source tables.
 */
internal object RawSchema {
    const val VERSION = 1L
    private const val META_KEY = "raw_schema_version"

    private val DROP = listOf(
        "DROP TRIGGER IF EXISTS stock_insert_holding",
        "DROP TRIGGER IF EXISTS stock_update_holding",
        "DROP TRIGGER IF EXISTS stock_delete_holding",
        "DROP TRIGGER IF EXISTS lot_move_holding",
    )

    private val CREATE = listOf(
        """
        CREATE TRIGGER stock_insert_holding AFTER INSERT ON stock
        BEGIN
            INSERT OR IGNORE INTO vintage_holding(vintage_id, format_ml, household_id, qty)
                SELECT l.vintage_id, l.format_ml, NEW.household_id, 0 FROM lot l WHERE l.id = NEW.lot_id;
            UPDATE vintage_holding SET qty = qty + NEW.confirmed_qty + NEW.pending_delta
            WHERE vintage_id = (SELECT l.vintage_id FROM lot l WHERE l.id = NEW.lot_id)
              AND format_ml = (SELECT l.format_ml FROM lot l WHERE l.id = NEW.lot_id);
        END
        """,
        """
        CREATE TRIGGER stock_update_holding AFTER UPDATE OF confirmed_qty, pending_delta ON stock
        BEGIN
            UPDATE vintage_holding SET qty = qty + (NEW.confirmed_qty + NEW.pending_delta) - (OLD.confirmed_qty + OLD.pending_delta)
            WHERE vintage_id = (SELECT l.vintage_id FROM lot l WHERE l.id = NEW.lot_id)
              AND format_ml = (SELECT l.format_ml FROM lot l WHERE l.id = NEW.lot_id);
        END
        """,
        """
        CREATE TRIGGER stock_delete_holding AFTER DELETE ON stock
        BEGIN
            UPDATE vintage_holding SET qty = qty - (OLD.confirmed_qty + OLD.pending_delta)
            WHERE vintage_id = (SELECT l.vintage_id FROM lot l WHERE l.id = OLD.lot_id)
              AND format_ml = (SELECT l.format_ml FROM lot l WHERE l.id = OLD.lot_id);
        END
        """,
        """
        CREATE TRIGGER lot_move_holding AFTER UPDATE OF vintage_id, format_ml ON lot
        WHEN OLD.vintage_id <> NEW.vintage_id OR OLD.format_ml <> NEW.format_ml
        BEGIN
            UPDATE vintage_holding SET qty = qty - (SELECT coalesce(sum(s.confirmed_qty + s.pending_delta), 0) FROM stock s WHERE s.lot_id = NEW.id)
            WHERE vintage_id = OLD.vintage_id AND format_ml = OLD.format_ml;
            INSERT OR IGNORE INTO vintage_holding(vintage_id, format_ml, household_id, qty) VALUES (NEW.vintage_id, NEW.format_ml, NEW.household_id, 0);
            UPDATE vintage_holding SET qty = qty + (SELECT coalesce(sum(s.confirmed_qty + s.pending_delta), 0) FROM stock s WHERE s.lot_id = NEW.id)
            WHERE vintage_id = NEW.vintage_id AND format_ml = NEW.format_ml;
        END
        """,
    )

    private val REBUILD = listOf(
        "DELETE FROM vintage_holding",
        """
        INSERT INTO vintage_holding(vintage_id, format_ml, household_id, qty)
        SELECT l.vintage_id, l.format_ml, l.household_id, sum(s.confirmed_qty + s.pending_delta)
        FROM stock s JOIN lot l ON l.id = s.lot_id
        GROUP BY l.vintage_id, l.format_ml
        """,
    )

    /** Call inside a transaction. */
    fun install(driver: BundledSqlDriver) {
        val installed = driver.executeQuery(null, "SELECT value FROM app_meta WHERE key = '$META_KEY'", { c ->
            QueryResult.Value(if (c.next().value) c.getString(0)?.toLongOrNull() else null)
        }, 0).value
        if (installed == VERSION) return
        (DROP + CREATE + REBUILD).forEach { driver.execute(null, it.trimIndent(), 0) }
        driver.execute(null, "INSERT INTO app_meta(key, value) VALUES ('$META_KEY', '$VERSION') ON CONFLICT(key) DO UPDATE SET value = excluded.value", 0)
    }
}
