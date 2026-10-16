package com.houseofvino.data.repo

import com.houseofvino.core.model.Ids
import com.houseofvino.core.text.TextNormalizer
import com.houseofvino.data.NotFoundException
import com.houseofvino.data.Session
import com.houseofvino.data.sync.SyncTables

enum class TagTarget(val code: String) { WINE("wine"), VINTAGE("vintage"), LOT("lot"), WISHLIST("wishlist"), TASTING("tasting") }

data class TagDetails(val id: String, val name: String, val color: String?, val uses: Int = 0)

class TagRepository(private val store: Store) {
    private val db get() = store.db

    fun all(session: Session): List<TagDetails> =
        db.tagQueries.all(session.householdId).executeAsList().map { TagDetails(it.id, it.name, it.color, it.uses.toInt()) }

    fun tagsFor(target: TagTarget, targetId: String): List<TagDetails> =
        db.tagQueries.tagsFor(target.code, targetId).executeAsList().map { TagDetails(it.id, it.name, it.color) }

    /** Returns the existing tag with this name (ignoring case and accents) or creates it. */
    fun obtain(session: Session, name: String, color: String? = null): String = store.write {
        session.requireWrite()
        val clean = name.cleaned(40)
        requireInput(clean != null, "tag") { "Tag name is empty" }
        val key = TextNormalizer.fold(clean!!)
        db.tagQueries.byKey(session.householdId, key).executeAsOneOrNull()?.id ?: run {
            val id = Ids.new()
            val now = store.now()
            db.tagQueries.insert(id, session.householdId, clean, key, color, now, now, session.userId)
            store.outbox.afterInsert(SyncTables.TAG, id, session.householdId)
            id
        }
    }

    fun attach(session: Session, tagId: String, target: TagTarget, targetId: String) = store.write {
        session.requireWrite()
        val tag = db.tagQueries.byId(tagId).executeAsOneOrNull()
        if (tag == null || tag.household_id != session.householdId || tag.deleted_at != null) throw NotFoundException("Tag")
        if (db.tagQueries.linkFor(tagId, target.code, targetId).executeAsOneOrNull() == null) {
            val id = Ids.new()
            val now = store.now()
            db.tagQueries.insertLink(id, session.householdId, tagId, target.code, targetId, now, now, session.userId)
            store.outbox.afterInsert(SyncTables.TAG_LINK, id, session.householdId)
            reindexTarget(target, targetId)
        }
    }

    fun detach(session: Session, tagId: String, target: TagTarget, targetId: String) = store.write {
        session.requireWrite()
        db.tagQueries.linkFor(tagId, target.code, targetId).executeAsOneOrNull()?.let {
            store.outbox.beforeUpdate(SyncTables.TAG_LINK, it.id)
            db.tagQueries.softDeleteLink(store.now(), session.userId, it.id)
            reindexTarget(target, targetId)
        }
    }

    fun rename(session: Session, tagId: String, name: String, color: String?) = store.write {
        session.requireWrite()
        val clean = name.cleaned(40)
        requireInput(clean != null, "tag") { "Tag name is empty" }
        store.outbox.beforeUpdate(SyncTables.TAG, tagId)
        db.tagQueries.rename(clean!!, TextNormalizer.fold(clean), color, store.now(), session.userId, tagId)
        db.tagQueries.linksOfTag(tagId).executeAsList().forEach { reindexTarget(TagTarget.entries.first { t -> t.code == it.target_type }, it.target_id) }
    }

    fun delete(session: Session, tagId: String) = store.write {
        session.requireWrite()
        val links = db.tagQueries.linksOfTag(tagId).executeAsList()
        links.forEach {
            store.outbox.beforeUpdate(SyncTables.TAG_LINK, it.id)
            db.tagQueries.softDeleteLink(store.now(), session.userId, it.id)
        }
        store.outbox.beforeUpdate(SyncTables.TAG, tagId)
        db.tagQueries.softDelete(store.now(), session.userId, tagId)
        links.forEach { reindexTarget(TagTarget.entries.first { t -> t.code == it.target_type }, it.target_id) }
    }

    private fun reindexTarget(target: TagTarget, id: String) {
        when (target) {
            TagTarget.WINE -> store.indexer.reindexWine(id)
            TagTarget.VINTAGE -> store.indexer.reindexVintage(id)
            TagTarget.LOT -> db.lotQueries.byId(id).executeAsOneOrNull()?.let { store.indexer.reindexVintage(it.vintage_id) }
            TagTarget.WISHLIST -> store.indexer.reindexWishlist(id)
            TagTarget.TASTING -> store.indexer.reindexTasting(id)
        }
    }
}
