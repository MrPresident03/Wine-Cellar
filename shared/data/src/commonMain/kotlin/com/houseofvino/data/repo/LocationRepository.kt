package com.houseofvino.data.repo

import com.houseofvino.core.model.Ids
import com.houseofvino.core.model.LocationKind
import com.houseofvino.data.NotFoundException
import com.houseofvino.data.Session
import com.houseofvino.data.sync.SyncTables

class LocationNotEmptyException(val bottles: Int) :
    IllegalStateException("This place still holds $bottles bottle${if (bottles == 1) "" else "s"}. Move them first.")

class LocationCycleException : IllegalArgumentException("A place can't be inside itself")

/** A location with everything inside it, for the "Places" view. */
data class LocationNode(val location: LocationDetails, val bottles: Int, val bottlesIncludingChildren: Int, val children: List<LocationNode>)

class LocationRepository(private val store: Store) {
    private val db get() = store.db

    fun location(id: String): LocationDetails? = db.locationQueries.byId(id).executeAsOneOrNull()?.takeIf { it.deleted_at == null }?.toDetails()

    fun all(session: Session): List<LocationDetails> = db.locationQueries.all(session.householdId).executeAsList().map { it.toDetails() }

    /** Locations as a tree with bottle counts, roots first. */
    fun tree(session: Session): List<LocationNode> {
        val all = all(session)
        val counts = db.locationQueries.bottleCounts(session.householdId).executeAsList()
            .associate { it.location_id to (it.bottles ?: 0L).toInt() }
        val byParent = all.groupBy { it.parentId }
        val ids = all.map { it.id }.toSet()
        fun build(l: LocationDetails, depth: Int): LocationNode {
            val children = if (depth > 32) emptyList() else byParent[l.id].orEmpty().map { build(it, depth + 1) }
            val own = counts[l.id] ?: 0
            return LocationNode(l, own, own + children.sumOf { it.bottlesIncludingChildren }, children)
        }
        // Orphans (parent deleted or not yet synced) are shown at the top level rather than lost.
        return all.filter { it.parentId == null || it.parentId !in ids }.map { build(it, 0) }
    }

    /** "Home cellar › Rack A › Shelf 3" */
    fun path(id: String): List<LocationDetails> =
        db.locationQueries.ancestorIds(id).executeAsList().mapNotNull { location(it) }

    fun subtreeIds(id: String): List<String> = db.locationQueries.subtreeIds(id).executeAsList()

    fun create(session: Session, input: LocationInput): String = store.write {
        session.requireWrite()
        val clean = validate(session, input, selfId = null)
        val id = Ids.new()
        val now = store.now()
        db.locationQueries.insert(
            id, session.householdId, clean.parentId, clean.kind.code, clean.name, clean.sortOrder.toLong(),
            clean.gridRows?.toLong(), clean.gridCols?.toLong(), clean.notes, now, now, session.userId,
        )
        store.outbox.afterInsert(SyncTables.LOCATION, id, session.householdId)
        id
    }

    fun update(session: Session, id: String, input: LocationInput) = store.write {
        session.requireWrite()
        val current = requireOwned(session, id)
        val clean = validate(session, input, selfId = id)
        // Shrinking a grid must not strand bottles outside it.
        if (clean.gridRows != current.gridRows || clean.gridCols != current.gridCols) {
            val outside = db.positionQueries.inLocation(id).executeAsList().filter {
                clean.gridRows == null || it.row_index >= clean.gridRows || it.col_index >= clean.gridCols!!
            }
            requireInput(outside.isEmpty(), "grid") { "${outside.size} bottle positions fall outside the new grid. Move those bottles first." }
        }
        store.outbox.beforeUpdate(SyncTables.LOCATION, id)
        db.locationQueries.update(
            parentId = clean.parentId, kind = clean.kind.code, name = clean.name, sortOrder = clean.sortOrder.toLong(),
            gridRows = clean.gridRows?.toLong(), gridCols = clean.gridCols?.toLong(), notes = clean.notes,
            now = store.now(), by = session.userId, id = id,
        )
    }

    /** Only empty places with no sub-places can be removed. */
    fun delete(session: Session, id: String) = store.write {
        session.requireWrite()
        requireOwned(session, id)
        val bottles = db.stockQueries.contentsOfLocation(id).executeAsList().sumOf { it.qty.toInt() }
        if (bottles > 0) throw LocationNotEmptyException(bottles)
        requireInput(db.locationQueries.children(id).executeAsList().isEmpty(), "children") { "Remove or move the places inside it first" }
        db.positionQueries.inLocation(id).executeAsList().forEach {
            store.outbox.beforeUpdate(SyncTables.POSITION, it.id)
            db.positionQueries.softDelete(store.now(), session.userId, it.id)
        }
        store.outbox.beforeUpdate(SyncTables.LOCATION, id)
        db.locationQueries.softDelete(store.now(), session.userId, id)
    }

    private fun requireOwned(session: Session, id: String): LocationDetails {
        val row = db.locationQueries.byId(id).executeAsOneOrNull()
        if (row == null || row.household_id != session.householdId || row.deleted_at != null) throw NotFoundException("Place")
        return row.toDetails()
    }

    private fun validate(session: Session, input: LocationInput, selfId: String?): LocationInput {
        val name = input.name.cleaned(100)
        requireInput(name != null, "name") { "Give this place a name" }
        val rows = input.gridRows
        val cols = input.gridCols
        requireInput((rows == null) == (cols == null), "grid") { "Enter both rows and columns, or neither" }
        if (rows != null && cols != null) requireInput(rows in 1..200 && cols in 1..200, "grid") { "Rows and columns must be between 1 and 200" }
        input.parentId?.let { parent ->
            requireOwned(session, parent)
            if (selfId != null) {
                if (parent == selfId || selfId in path(parent).map { it.id }) throw LocationCycleException()
            }
        }
        return input.copy(name = name!!, notes = input.notes.cleanedMultiline(2000))
    }

    companion object {
        const val DEFAULT_SITE_NAME = "Cellar"
        val DEFAULT_KIND = LocationKind.SITE
    }
}
