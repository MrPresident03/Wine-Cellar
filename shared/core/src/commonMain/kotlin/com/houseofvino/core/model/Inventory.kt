package com.houseofvino.core.model

/**
 * Every change in stock is a row in an append-only ledger. A movement takes [quantity] bottles of
 * one acquisition lot out of [fromLocationId] and/or puts them into [toLocationId]. Balances are
 * the sum of the ledger, and are never edited directly. Mistakes are corrected with a REVERSAL
 * movement that points at the original, so history stays auditable.
 */
enum class MovementKind(val code: String, val direction: Direction, val label: String) {
    PURCHASE("purchase", Direction.IN, "Purchased"),
    RECEIVE("receive", Direction.IN, "Received"),
    ADJUST_IN("adjust_in", Direction.IN, "Stock count correction (+)"),
    CONSUME("consume", Direction.OUT, "Opened"),
    SELL("sell", Direction.OUT, "Sold"),
    GIFT_OUT("gift_out", Direction.OUT, "Given away"),
    LOSS("loss", Direction.OUT, "Broken / spoiled"),
    ADJUST_OUT("adjust_out", Direction.OUT, "Stock count correction (−)"),
    TRANSFER("transfer", Direction.MOVE, "Moved"),
    REVERSAL("reversal", Direction.ANY, "Reversal");

    enum class Direction { IN, OUT, MOVE, ANY }

    /** Movements that permanently remove bottles from the collection (for consumption stats). */
    val removesFromCollection: Boolean get() = direction == Direction.OUT

    companion object {
        fun fromCode(code: String): MovementKind =
            entries.firstOrNull { it.code == code } ?: throw IllegalArgumentException("Unknown movement kind: $code")
    }
}

data class MovementDraft(
    val kind: MovementKind,
    val lotId: String,
    val quantity: Int,
    val fromLocationId: String?,
    val toLocationId: String?,
    /** Set only for REVERSAL. */
    val reversesMovementId: String? = null,
)

sealed class MovementError(message: String) : IllegalArgumentException(message) {
    class NonPositiveQuantity : MovementError("Quantity must be at least 1")
    class MissingSource(kind: MovementKind) : MovementError("${kind.label} needs a location to take bottles from")
    class MissingDestination(kind: MovementKind) : MovementError("${kind.label} needs a location to put bottles in")
    class UnexpectedSource(kind: MovementKind) : MovementError("${kind.label} must not take bottles from a location")
    class UnexpectedDestination(kind: MovementKind) : MovementError("${kind.label} must not put bottles in a location")
    class SameLocation : MovementError("Choose a different location to move to")
    class MissingReversalTarget : MovementError("A reversal must refer to the movement it undoes")
    class InsufficientStock(val available: Int, val requested: Int) :
        MovementError("Only $available bottle${if (available == 1) "" else "s"} available here, $requested requested")
    class AlreadyReversed : MovementError("That movement has already been reversed")
    class CannotReverseReversal : MovementError("A reversal can't itself be reversed; record a new movement instead")
}

object Ledger {
    /** Structural checks that don't need the database. Throws [MovementError]. */
    fun validate(d: MovementDraft) {
        if (d.quantity < 1) throw MovementError.NonPositiveQuantity()
        when (d.kind.direction) {
            MovementKind.Direction.IN -> {
                if (d.toLocationId == null) throw MovementError.MissingDestination(d.kind)
                if (d.fromLocationId != null) throw MovementError.UnexpectedSource(d.kind)
            }
            MovementKind.Direction.OUT -> {
                if (d.fromLocationId == null) throw MovementError.MissingSource(d.kind)
                if (d.toLocationId != null) throw MovementError.UnexpectedDestination(d.kind)
            }
            MovementKind.Direction.MOVE -> {
                if (d.fromLocationId == null) throw MovementError.MissingSource(d.kind)
                if (d.toLocationId == null) throw MovementError.MissingDestination(d.kind)
                if (d.fromLocationId == d.toLocationId) throw MovementError.SameLocation()
            }
            MovementKind.Direction.ANY -> {
                if (d.reversesMovementId == null) throw MovementError.MissingReversalTarget()
                if (d.fromLocationId == null && d.toLocationId == null) throw MovementError.MissingSource(d.kind)
                if (d.fromLocationId != null && d.fromLocationId == d.toLocationId) throw MovementError.SameLocation()
            }
        }
        if (d.kind != MovementKind.REVERSAL && d.reversesMovementId != null) throw MovementError.MissingReversalTarget()
    }

    /** The draft that undoes [original]: same lot and quantity, locations swapped. */
    fun reversalOf(originalId: String, original: MovementDraft): MovementDraft {
        if (original.kind == MovementKind.REVERSAL) throw MovementError.CannotReverseReversal()
        return MovementDraft(
            kind = MovementKind.REVERSAL,
            lotId = original.lotId,
            quantity = original.quantity,
            fromLocationId = original.toLocationId,
            toLocationId = original.fromLocationId,
            reversesMovementId = originalId,
        )
    }

    /** Net balance change for one location. */
    fun deltaAt(locationId: String, d: MovementDraft): Int =
        (if (d.toLocationId == locationId) d.quantity else 0) - (if (d.fromLocationId == locationId) d.quantity else 0)
}

enum class LocationKind(val code: String, val label: String, val canHoldBottles: Boolean = true) {
    SITE("site", "Cellar / site", canHoldBottles = true),
    ROOM("room", "Room"),
    FRIDGE("fridge", "Wine fridge"),
    RACK("rack", "Rack"),
    SHELF("shelf", "Shelf"),
    BIN("bin", "Bin"),
    CASE("case", "Case"),
    OFFSITE("offsite", "Off-site storage"),
    OTHER("other", "Other");

    companion object {
        fun fromCode(code: String?): LocationKind = entries.firstOrNull { it.code == code } ?: OTHER
    }
}

enum class CaseType(val code: String, val label: String) {
    NONE("none", "Loose bottles"),
    OWC("owc", "Original wooden case"),
    OC("oc", "Original carton"),
    OTHER("other", "Other case");

    companion object {
        fun fromCode(code: String?): CaseType = entries.firstOrNull { it.code == code } ?: NONE
    }
}

enum class HouseholdRole(val code: String, val canWrite: Boolean, val canManageMembers: Boolean) {
    OWNER("owner", true, true),
    EDITOR("editor", true, false),
    VIEWER("viewer", false, false);

    companion object {
        fun fromCode(code: String?): HouseholdRole = entries.firstOrNull { it.code == code } ?: VIEWER
    }
}
