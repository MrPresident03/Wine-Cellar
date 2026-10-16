package com.houseofvino.data.search

import com.houseofvino.core.model.Grapes
import com.houseofvino.data.db.HouseOfVinoDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Keeps the FTS5 index in step with the data. Repositories call these inside the same transaction
 * as the change; [rebuild] recreates everything (after migrations, or if [verify] finds drift).
 */
class SearchIndexer(private val db: HouseOfVinoDatabase) {

    data class DocText(
        val producer: String,
        val name: String,
        val vintage: String,
        val region: String,
        val grapes: String,
        val tags: String,
        val notes: String,
    )

    enum class DocType(val code: String) { VINTAGE("vintage"), WISHLIST("wishlist"), TASTING("tasting") }

    fun reindexVintage(vintageId: String) {
        remove(DocType.VINTAGE, vintageId)
        val s = db.indexerQueries.vintageDocSource(vintageId).executeAsOneOrNull() ?: return
        if (s.vintage_deleted != null || s.wine_deleted != null) return
        insert(
            s.household_id, DocType.VINTAGE, vintageId,
            DocText(
                producer = s.producer,
                name = join(s.name, s.classification),
                vintage = vintageText(s.vintage_kind, s.vintage_year),
                region = join(s.country, s.region, s.sub_region, s.appellation),
                grapes = grapesText(s.grapes),
                tags = s.tag_names.orEmpty(),
                notes = join(s.wine_notes, s.vintage_notes, s.drink_note, s.lot_text),
            ),
        )
    }

    fun reindexWine(wineId: String) {
        db.vintageQueries.idsForWine(wineId).executeAsList().forEach(::reindexVintage)
        db.indexerQueries.tastingIdsForWine(wineId).executeAsList().forEach(::reindexTasting)
        db.wishlistQueries.idsForWine(wineId).executeAsList().forEach(::reindexWishlist)
    }

    fun reindexTasting(noteId: String) {
        remove(DocType.TASTING, noteId)
        val s = db.indexerQueries.tastingDocSource(noteId).executeAsOneOrNull() ?: return
        if (s.deleted_at != null) return
        insert(
            s.household_id, DocType.TASTING, noteId,
            DocText(
                producer = s.producer,
                name = s.name,
                vintage = vintageText(s.vintage_kind, s.vintage_year),
                region = join(s.country, s.region, s.sub_region, s.appellation),
                grapes = grapesText(s.grapes),
                tags = s.tag_names.orEmpty(),
                notes = join(s.appearance, s.nose, s.palate, s.finish, s.conclusion, jsonList(s.aromas), s.notes, s.occasion, s.companions, s.food_pairing, s.place),
            ),
        )
    }

    fun reindexWishlist(itemId: String) {
        remove(DocType.WISHLIST, itemId)
        val s = db.indexerQueries.wishlistDocSource(itemId).executeAsOneOrNull() ?: return
        if (s.deleted_at != null) return
        val vintage = when {
            s.vintage_kind != null -> vintageText(s.vintage_kind, s.vintage_year)
            else -> join(s.vintage_from?.toString(), s.vintage_to?.toString(), if (s.accepts_nv == 1L) "NV non vintage" else null)
        }
        insert(
            s.household_id, DocType.WISHLIST, itemId,
            DocText(
                producer = join(s.linked_producer, s.producer),
                name = join(s.title, s.linked_name, s.wine_name),
                vintage = vintage,
                region = join(s.country, s.linked_region, s.region, s.appellation),
                grapes = grapesText(s.grapes),
                tags = s.tag_names.orEmpty(),
                notes = join(s.notes, s.merchant_text),
            ),
        )
    }

    fun remove(type: DocType, id: String) {
        val rowId = db.searchQueries.docId(type.code, id).executeAsOneOrNull() ?: return
        db.searchQueries.deleteFts(rowId)
        db.searchQueries.deleteDoc(rowId)
    }

    /** Recreates the whole index for a household. Call inside a transaction. */
    fun rebuild(householdId: String) {
        db.indexerQueries.docs(householdId).executeAsList().forEach { db.searchQueries.deleteFts(it.id) }
        db.searchQueries.clearHousehold(householdId)
        db.indexerQueries.allVintageIds(householdId).executeAsList().forEach(::reindexVintage)
        db.indexerQueries.allTastingIds(householdId).executeAsList().forEach(::reindexTasting)
        db.indexerQueries.allWishlistIds(householdId).executeAsList().forEach(::reindexWishlist)
    }

    /** Current index contents, keyed by (type, id), for consistency checks. */
    fun snapshot(householdId: String): Map<Pair<String, String>, DocText> =
        db.indexerQueries.docs(householdId).executeAsList().associate {
            (it.doc_type to it.doc_id) to DocText(
                it.producer.orEmpty(), it.name.orEmpty(), it.vintage.orEmpty(), it.region.orEmpty(),
                it.grapes.orEmpty(), it.tags.orEmpty(), it.notes.orEmpty(),
            )
        }

    private fun insert(householdId: String, type: DocType, id: String, text: DocText) {
        db.searchQueries.insertDoc(householdId, type.code, id)
        val rowId = db.indexerQueries.lastInsertRowId().executeAsOne()
        db.searchQueries.insertFts(
            rowId,
            withJoinedForms(text.producer),
            withJoinedForms(text.name),
            text.vintage,
            withJoinedForms(text.region),
            text.grapes,
            text.tags,
            text.notes,
        )
    }

    companion object {
        fun vintageText(kind: String?, year: Long?): String = when (kind) {
            "year" -> year?.toString().orEmpty()
            "nv" -> "NV non vintage"
            else -> ""
        }

        fun join(vararg parts: String?): String = parts.filterNot { it.isNullOrBlank() }.joinToString(" ") { it!!.trim() }

        fun grapesText(json: String?): String = Grapes.decode(json).joinToString(" ") { it.name }

        private fun jsonList(text: String?): String? = runCatching {
            Json.parseToJsonElement(text ?: return null).jsonArray.joinToString(" ") { it.jsonPrimitive.content }
        }.getOrNull()

        /**
         * FTS splits "d'Yquem" into "d" + "yquem" and "Saint-Émilion" into "saint" + "emilion".
         * Index the joined forms too, so "dyquem" and "saintemilion" also match.
         */
        fun withJoinedForms(text: String): String {
            val extras = Regex("""[\p{L}\p{N}]+(?:['’\-][\p{L}\p{N}]+)+""").findAll(text)
                .map { it.value.replace("'", "").replace("’", "").replace("-", "") }
                .toList()
            return if (extras.isEmpty()) text else text + " " + extras.joinToString(" ")
        }
    }
}
