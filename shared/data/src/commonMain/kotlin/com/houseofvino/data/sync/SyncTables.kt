package com.houseofvino.data.sync

/**
 * One description of every synced table, used to snapshot rows for three-way merges, to build
 * push payloads, and to apply pulled rows. A test checks it against the real SQLite schema, and
 * the Supabase migration is checked against it too, so the three can't drift apart silently.
 */
enum class ColType {
    /** UUID text <-> uuid */
    ID,
    TEXT,
    INT,

    /** 0/1 <-> boolean */
    BOOL,

    /** epoch milliseconds <-> timestamptz */
    MILLIS,

    /** YYYY-MM-DD <-> date */
    DATE,

    /** plain decimal string <-> numeric */
    DECIMAL,

    /** JSON text <-> jsonb */
    JSON,

    /** integer minor units <-> bigint */
    MINOR,
}

data class Col(val name: String, val type: ColType)

data class SyncTable(
    val name: String,
    /** Columns that exist on both device and server. */
    val columns: List<Col>,
    /** Columns that only exist on the device and are never sent. */
    val deviceOnly: List<String> = emptyList(),
    /** Column holding the household id ("id" for the household table itself). */
    val householdColumn: String = "household_id",
    /** Synced as ordinary upserts (false for the ledger, which syncs as movements). */
    val upsert: Boolean = true,
) {
    val columnNames: List<String> get() = columns.map { it.name }
    fun col(name: String): Col = columns.first { it.name == name }
}

object SyncTables {
    private val META = listOf(
        Col("created_at", ColType.MILLIS),
        Col("updated_at", ColType.MILLIS),
        Col("updated_by", ColType.ID),
        Col("rev", ColType.INT),
        Col("deleted_at", ColType.MILLIS),
    )

    private fun table(name: String, vararg cols: Pair<String, ColType>, deviceOnly: List<String> = emptyList(), householdColumn: String = "household_id") =
        SyncTable(
            name = name,
            columns = buildList {
                add(Col("id", ColType.ID))
                if (householdColumn != "id") add(Col("household_id", ColType.ID))
                cols.forEach { (n, t) -> add(Col(n, t)) }
                addAll(META)
            },
            deviceOnly = deviceOnly,
            householdColumn = householdColumn,
        )

    val HOUSEHOLD = table("household", "name" to ColType.TEXT, "home_currency" to ColType.TEXT, deviceOnly = listOf("is_local_only"), householdColumn = "id")

    val WINE = table(
        "wine",
        "producer" to ColType.TEXT, "name" to ColType.TEXT, "style" to ColType.TEXT, "country" to ColType.TEXT,
        "region" to ColType.TEXT, "sub_region" to ColType.TEXT, "appellation" to ColType.TEXT, "classification" to ColType.TEXT,
        "grapes" to ColType.JSON, "notes" to ColType.TEXT, "identity_key" to ColType.TEXT,
    )

    val VINTAGE = table(
        "wine_vintage",
        "wine_id" to ColType.ID, "vintage_kind" to ColType.TEXT, "vintage_year" to ColType.INT, "alcohol_pct" to ColType.DECIMAL,
        "drink_from" to ColType.INT, "drink_to" to ColType.INT, "drink_source" to ColType.TEXT, "drink_note" to ColType.TEXT,
        "notes" to ColType.TEXT,
    )

    val LOCATION = table(
        "location",
        "parent_id" to ColType.ID, "kind" to ColType.TEXT, "name" to ColType.TEXT, "sort_order" to ColType.INT,
        "grid_rows" to ColType.INT, "grid_cols" to ColType.INT, "notes" to ColType.TEXT,
    )

    val LOT = table(
        "lot",
        "vintage_id" to ColType.ID, "format_ml" to ColType.INT, "quantity_acquired" to ColType.INT,
        "unit_price_minor" to ColType.MINOR, "price_currency" to ColType.TEXT, "extra_costs_minor" to ColType.MINOR,
        "acquired_on" to ColType.DATE, "source" to ColType.TEXT, "source_ref" to ColType.TEXT, "case_type" to ColType.TEXT,
        "case_size" to ColType.INT, "wishlist_item_id" to ColType.ID, "notes" to ColType.TEXT,
    )

    val POSITION = table(
        "bottle_position",
        "location_id" to ColType.ID, "lot_id" to ColType.ID, "row_index" to ColType.INT, "col_index" to ColType.INT,
    )

    val TASTING = table(
        "tasting_note",
        "vintage_id" to ColType.ID, "lot_id" to ColType.ID, "movement_id" to ColType.ID, "author_id" to ColType.ID,
        "tasted_on" to ColType.DATE, "rating_value" to ColType.DECIMAL, "rating_scale" to ColType.TEXT, "rating_100" to ColType.INT,
        "appearance" to ColType.TEXT, "nose" to ColType.TEXT, "palate" to ColType.TEXT, "finish" to ColType.TEXT,
        "conclusion" to ColType.TEXT, "sweetness" to ColType.INT, "acidity" to ColType.INT, "tannin" to ColType.INT,
        "body" to ColType.INT, "intensity" to ColType.INT, "aromas" to ColType.JSON, "notes" to ColType.TEXT,
        "occasion" to ColType.TEXT, "companions" to ColType.TEXT, "food_pairing" to ColType.TEXT, "place" to ColType.TEXT,
        "would_buy_again" to ColType.BOOL,
    )

    val PHOTO = table(
        "photo",
        "owner_type" to ColType.TEXT, "owner_id" to ColType.ID, "role" to ColType.TEXT, "sort_order" to ColType.INT,
        "storage_path" to ColType.TEXT, "width" to ColType.INT, "height" to ColType.INT, "byte_size" to ColType.INT,
        "sha256" to ColType.TEXT,
        deviceOnly = listOf("local_path", "thumb_path", "upload_state"),
    )

    val WISHLIST = table(
        "wishlist_item",
        "wine_id" to ColType.ID, "vintage_id" to ColType.ID, "title" to ColType.TEXT, "producer" to ColType.TEXT,
        "wine_name" to ColType.TEXT, "style" to ColType.TEXT, "region" to ColType.TEXT, "vintage_from" to ColType.INT,
        "vintage_to" to ColType.INT, "accepts_nv" to ColType.BOOL, "format_ml" to ColType.INT, "desired_quantity" to ColType.INT,
        "target_price_minor" to ColType.MINOR, "max_price_minor" to ColType.MINOR, "price_currency" to ColType.TEXT,
        "priority" to ColType.INT, "status" to ColType.TEXT, "notes" to ColType.TEXT, "acquired_lot_id" to ColType.ID,
    )

    val WISHLIST_LINK = table(
        "wishlist_link",
        "item_id" to ColType.ID, "merchant" to ColType.TEXT, "url" to ColType.TEXT, "last_price_minor" to ColType.MINOR,
        "last_price_currency" to ColType.TEXT, "price_seen_on" to ColType.DATE, "note" to ColType.TEXT,
    )

    val MARKET_VALUE = table(
        "market_value",
        "vintage_id" to ColType.ID, "format_ml" to ColType.INT, "amount_minor" to ColType.MINOR, "currency" to ColType.TEXT,
        "source" to ColType.TEXT, "source_ref" to ColType.TEXT, "as_of" to ColType.DATE, "note" to ColType.TEXT,
    )

    val FX_RATE = table(
        "fx_rate",
        "from_currency" to ColType.TEXT, "to_currency" to ColType.TEXT, "rate" to ColType.DECIMAL, "as_of" to ColType.DATE,
        "source" to ColType.TEXT,
    )

    val TAG = table("tag", "name" to ColType.TEXT, "name_key" to ColType.TEXT, "color" to ColType.TEXT)

    val TAG_LINK = table("tag_link", "tag_id" to ColType.ID, "target_type" to ColType.TEXT, "target_id" to ColType.ID)

    val BARCODE = table("barcode_link", "gtin" to ColType.TEXT, "wine_id" to ColType.ID, "vintage_id" to ColType.ID, "format_ml" to ColType.INT)

    val SAVED_SEARCH = table(
        "saved_search",
        "owner_id" to ColType.ID, "name" to ColType.TEXT, "query_json" to ColType.TEXT, "sort_order" to ColType.INT,
    )

    /** The ledger: pulled like a table, pushed only as movements. */
    val MOVEMENT = SyncTable(
        name = "inventory_movement",
        columns = listOf(
            Col("id", ColType.ID), Col("household_id", ColType.ID), Col("lot_id", ColType.ID), Col("kind", ColType.TEXT),
            Col("quantity", ColType.INT), Col("from_location_id", ColType.ID), Col("to_location_id", ColType.ID),
            Col("occurred_at", ColType.MILLIS), Col("recorded_at", ColType.MILLIS), Col("recorded_by", ColType.ID),
            Col("device_id", ColType.TEXT), Col("price_minor", ColType.MINOR), Col("price_currency", ColType.TEXT),
            Col("counterparty", ColType.TEXT), Col("note", ColType.TEXT), Col("reverses_id", ColType.ID), Col("rev", ColType.INT),
        ),
        deviceOnly = listOf("sync_state", "reject_reason"),
        upsert = false,
    )

    /** Parents before children, so pulled rows never reference something not yet applied. */
    val ALL: List<SyncTable> = listOf(
        HOUSEHOLD, LOCATION, WINE, VINTAGE, WISHLIST, LOT, MOVEMENT, POSITION, TASTING, PHOTO, WISHLIST_LINK,
        MARKET_VALUE, FX_RATE, TAG, TAG_LINK, BARCODE, SAVED_SEARCH,
    )

    fun byName(name: String): SyncTable = ALL.firstOrNull { it.name == name } ?: throw IllegalArgumentException("Not a synced table: $name")
}
