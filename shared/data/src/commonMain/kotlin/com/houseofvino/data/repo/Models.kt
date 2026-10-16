package com.houseofvino.data.repo

import com.houseofvino.core.model.BottleFormat
import com.houseofvino.core.model.CaseType
import com.houseofvino.core.model.DrinkingWindow
import com.houseofvino.core.model.GrapeShare
import com.houseofvino.core.model.Grapes
import com.houseofvino.core.model.LocationKind
import com.houseofvino.core.model.MovementKind
import com.houseofvino.core.model.Vintage
import com.houseofvino.core.model.WindowSource
import com.houseofvino.core.model.WineStyle
import com.houseofvino.core.money.Currency
import com.houseofvino.core.money.Decimal
import com.houseofvino.core.money.Money
import com.houseofvino.data.db.Inventory_movement
import com.houseofvino.data.db.Location
import com.houseofvino.data.db.Lot
import com.houseofvino.data.db.Wine
import com.houseofvino.data.db.Wine_vintage
import kotlinx.datetime.LocalDate

// ---------------------------------------------------------------- inputs

data class WineInput(
    val producer: String,
    val name: String = "",
    val style: WineStyle? = null,
    val country: String? = null,
    val region: String? = null,
    val subRegion: String? = null,
    val appellation: String? = null,
    val classification: String? = null,
    val grapes: List<GrapeShare> = emptyList(),
    val notes: String? = null,
)

data class VintageInput(
    val vintage: Vintage,
    val alcoholPct: Decimal? = null,
    val drinkFrom: Int? = null,
    val drinkTo: Int? = null,
    val drinkSource: WindowSource? = null,
    val drinkNote: String? = null,
    val notes: String? = null,
)

data class PurchaseInput(
    val vintageId: String,
    val quantity: Int,
    val locationId: String,
    val formatMl: Int = BottleFormat.STANDARD_ML,
    val unitPrice: Money? = null,
    /** Shipping, duty etc. for the whole purchase, in the same currency as [unitPrice]. */
    val extraCosts: Money? = null,
    val acquiredOn: LocalDate? = null,
    val source: String? = null,
    val sourceRef: String? = null,
    val caseType: CaseType = CaseType.NONE,
    val caseSize: Int? = null,
    val notes: String? = null,
    val wishlistItemId: String? = null,
    /** Slots to fill in a gridded location; may be fewer than [quantity]. */
    val slots: List<Slot> = emptyList(),
    /** Use RECEIVE for gifts and bottles of unknown origin. */
    val kind: MovementKind = MovementKind.PURCHASE,
)

data class Slot(val row: Int, val col: Int)

data class LocationInput(
    val name: String,
    val kind: LocationKind,
    val parentId: String? = null,
    val gridRows: Int? = null,
    val gridCols: Int? = null,
    val notes: String? = null,
    val sortOrder: Int = 0,
)

// ---------------------------------------------------------------- read models

data class WineDetails(
    val id: String,
    val householdId: String,
    val producer: String,
    val name: String,
    val style: WineStyle?,
    val country: String?,
    val region: String?,
    val subRegion: String?,
    val appellation: String?,
    val classification: String?,
    val grapes: List<GrapeShare>,
    val notes: String?,
    val deleted: Boolean,
) {
    /** "Clos de la Roche Grand Cru" or the producer if there is no cuvée name. */
    val displayName: String get() = listOfNotNull(name.ifBlank { null }, classification).joinToString(" ").ifBlank { producer }
    val place: String get() = listOfNotNull(appellation ?: subRegion ?: region, country).distinct().joinToString(", ")
}

data class VintageDetails(
    val id: String,
    val wineId: String,
    val vintage: Vintage,
    val alcoholPct: Decimal?,
    val window: DrinkingWindow?,
    val notes: String?,
    val deleted: Boolean,
)

data class LotDetails(
    val id: String,
    val vintageId: String,
    val formatMl: Int,
    val quantityAcquired: Int,
    val unitPrice: Money?,
    val extraCosts: Money?,
    val acquiredOn: LocalDate?,
    val source: String?,
    val sourceRef: String?,
    val caseType: CaseType,
    val caseSize: Int?,
    val notes: String?,
    val wishlistItemId: String?,
)

data class LocationDetails(
    val id: String,
    val parentId: String?,
    val kind: LocationKind,
    val name: String,
    val gridRows: Int?,
    val gridCols: Int?,
    val notes: String?,
    val sortOrder: Int,
) {
    val hasGrid: Boolean get() = gridRows != null && gridCols != null
}

data class MovementRecord(
    val id: String,
    val lotId: String,
    val kind: MovementKind,
    val quantity: Int,
    val fromLocationId: String?,
    val toLocationId: String?,
    val occurredAt: Long,
    val recordedAt: Long,
    val recordedBy: String?,
    val price: Money?,
    val counterparty: String?,
    val note: String?,
    val reversesId: String?,
    val syncState: String,
    val rejectReason: String?,
)

/** Bottles of one purchase at one place. */
data class Holding(
    val lotId: String,
    val locationId: String,
    val locationName: String,
    val formatMl: Int,
    val quantity: Int,
    val locationHasGrid: Boolean,
)

// ---------------------------------------------------------------- mapping

internal fun Wine.toDetails() = WineDetails(
    id = id, householdId = household_id, producer = producer, name = name, style = WineStyle.fromCode(style),
    country = country, region = region, subRegion = sub_region, appellation = appellation, classification = classification,
    grapes = Grapes.decode(grapes), notes = notes, deleted = deleted_at != null,
)

internal fun Wine_vintage.toDetails() = VintageDetails(
    id = id,
    wineId = wine_id,
    vintage = Vintage.fromStorage(vintage_kind, vintage_year),
    alcoholPct = alcohol_pct?.let(Decimal::parseOrNull),
    window = if (drink_from == null && drink_to == null) null else runCatching {
        DrinkingWindow(drink_from?.toInt(), drink_to?.toInt(), WindowSource.fromCode(drink_source), drink_note)
    }.getOrNull(),
    notes = notes,
    deleted = deleted_at != null,
)

internal fun money(minor: Long?, currency: String?): Money? =
    if (minor == null || currency == null) null else Money(minor, Currency(currency))

internal fun Lot.toDetails() = LotDetails(
    id = id, vintageId = vintage_id, formatMl = format_ml.toInt(), quantityAcquired = quantity_acquired.toInt(),
    unitPrice = money(unit_price_minor, price_currency), extraCosts = money(extra_costs_minor, price_currency),
    acquiredOn = acquired_on?.let { runCatching { LocalDate.parse(it) }.getOrNull() }, source = source, sourceRef = source_ref,
    caseType = CaseType.fromCode(case_type), caseSize = case_size?.toInt(), notes = notes, wishlistItemId = wishlist_item_id,
)

internal fun Location.toDetails() = LocationDetails(
    id = id, parentId = parent_id, kind = LocationKind.fromCode(kind), name = name, gridRows = grid_rows?.toInt(),
    gridCols = grid_cols?.toInt(), notes = notes, sortOrder = sort_order.toInt(),
)

internal fun Inventory_movement.toRecord() = MovementRecord(
    id = id, lotId = lot_id, kind = MovementKind.fromCode(kind), quantity = quantity.toInt(), fromLocationId = from_location_id,
    toLocationId = to_location_id, occurredAt = occurred_at, recordedAt = recorded_at, recordedBy = recorded_by,
    price = money(price_minor, price_currency), counterparty = counterparty, note = note, reversesId = reverses_id,
    syncState = sync_state, rejectReason = reject_reason,
)
