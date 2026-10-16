package com.houseofvino.data.repo

import com.houseofvino.core.model.Ids
import com.houseofvino.core.money.Decimal
import com.houseofvino.core.money.ExactMath
import com.houseofvino.core.money.Rounding
import com.houseofvino.data.NotFoundException
import com.houseofvino.data.Session
import com.houseofvino.data.db.Tasting_note
import com.houseofvino.data.sync.SyncTables
import kotlinx.datetime.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Rating scales people actually use. Values are stored exactly as entered, plus a 0–100 for sorting. */
enum class RatingScale(val code: String, val max: Int, val label: String) {
    HUNDRED("100", 100, "100 points"),
    TWENTY("20", 20, "20 points"),
    TEN("10", 10, "10 points"),
    FIVE("5", 5, "5 stars");

    /** 0–100, rounded half-even. 92/100 -> 92, 17.5/20 -> 88, 4.5/5 -> 90. */
    fun to100(value: Decimal): Int {
        val scaled = ExactMath.mulDiv(value.unscaled, 100, ExactMath.multiplyExact(ExactMath.pow10(value.scale), max.toLong()), Rounding.HALF_EVEN)
        return scaled.toInt().coerceIn(0, 100)
    }

    companion object {
        fun fromCode(code: String?): RatingScale? = entries.firstOrNull { it.code == code }
    }
}

data class Rating(val value: Decimal, val scale: RatingScale) {
    init {
        require(!value.isNegative && value <= Decimal.of(scale.max.toLong())) { "Rating must be between 0 and ${scale.max}" }
    }

    override fun toString() = "${value.stripTrailingZeros()}/${scale.max}"
}

data class TastingInput(
    val vintageId: String,
    val tastedOn: LocalDate,
    val rating: Rating? = null,
    val appearance: String? = null,
    val nose: String? = null,
    val palate: String? = null,
    val finish: String? = null,
    val conclusion: String? = null,
    /** 1–5 each, optional. */
    val sweetness: Int? = null,
    val acidity: Int? = null,
    val tannin: Int? = null,
    val body: Int? = null,
    val intensity: Int? = null,
    val aromas: List<String> = emptyList(),
    val notes: String? = null,
    val occasion: String? = null,
    val companions: String? = null,
    val foodPairing: String? = null,
    val place: String? = null,
    val wouldBuyAgain: Boolean? = null,
    /** Set when the note is about a bottle opened from the cellar. */
    val lotId: String? = null,
    val movementId: String? = null,
)

data class TastingNote(
    val id: String,
    val vintageId: String,
    val authorId: String?,
    val input: TastingInput,
    val createdAt: Long,
)

/**
 * Tasting notes belong to a vintage, not to stock, so they outlive the bottles. They are only
 * ever soft-deleted.
 */
class TastingRepository(private val store: Store) {
    private val db get() = store.db
    private val json = Json
    private val listSer = ListSerializer(String.serializer())

    fun note(id: String): TastingNote? = db.tastingQueries.byId(id).executeAsOneOrNull()?.takeIf { it.deleted_at == null }?.toNote()

    fun notesFor(vintageId: String): List<TastingNote> = db.tastingQueries.forVintage(vintageId).executeAsList().map { it.toNote() }

    fun add(session: Session, input: TastingInput): String = store.write {
        session.requireWrite()
        val clean = validate(session, input)
        val id = Ids.new()
        val now = store.now()
        db.tastingQueries.insert(
            id, session.householdId, clean.vintageId, clean.lotId, clean.movementId, session.userId, clean.tastedOn.toString(),
            clean.rating?.value?.toString(), clean.rating?.scale?.code, clean.rating?.let { it.scale.to100(it.value).toLong() },
            clean.appearance, clean.nose, clean.palate, clean.finish, clean.conclusion,
            clean.sweetness?.toLong(), clean.acidity?.toLong(), clean.tannin?.toLong(), clean.body?.toLong(), clean.intensity?.toLong(),
            json.encodeToString(listSer, clean.aromas), clean.notes, clean.occasion, clean.companions, clean.foodPairing, clean.place,
            clean.wouldBuyAgain?.let { if (it) 1L else 0L }, now, now, session.userId,
        )
        store.outbox.afterInsert(SyncTables.TASTING, id, session.householdId)
        store.indexer.reindexTasting(id)
        id
    }

    fun update(session: Session, id: String, input: TastingInput) = store.write {
        session.requireWrite()
        val existing = db.tastingQueries.byId(id).executeAsOneOrNull()
        if (existing == null || existing.household_id != session.householdId || existing.deleted_at != null) throw NotFoundException("Tasting note")
        val clean = validate(session, input.copy(vintageId = existing.vintage_id, lotId = existing.lot_id, movementId = existing.movement_id))
        store.outbox.beforeUpdate(SyncTables.TASTING, id)
        db.tastingQueries.update(
            tastedOn = clean.tastedOn.toString(), ratingValue = clean.rating?.value?.toString(), ratingScale = clean.rating?.scale?.code,
            rating100 = clean.rating?.let { it.scale.to100(it.value).toLong() }, appearance = clean.appearance, nose = clean.nose,
            palate = clean.palate, finish = clean.finish, conclusion = clean.conclusion, sweetness = clean.sweetness?.toLong(),
            acidity = clean.acidity?.toLong(), tannin = clean.tannin?.toLong(), body = clean.body?.toLong(), intensity = clean.intensity?.toLong(),
            aromas = json.encodeToString(listSer, clean.aromas), notes = clean.notes, occasion = clean.occasion, companions = clean.companions,
            foodPairing = clean.foodPairing, place = clean.place, wouldBuyAgain = clean.wouldBuyAgain?.let { if (it) 1L else 0L },
            now = store.now(), by = session.userId, id = id,
        )
        store.indexer.reindexTasting(id)
    }

    fun delete(session: Session, id: String) = store.write {
        session.requireWrite()
        store.outbox.beforeUpdate(SyncTables.TASTING, id)
        db.tastingQueries.softDelete(store.now(), session.userId, id)
        store.indexer.reindexTasting(id)
    }

    private fun validate(session: Session, i: TastingInput): TastingInput {
        val v = db.vintageQueries.byId(i.vintageId).executeAsOneOrNull()
        if (v == null || v.household_id != session.householdId) throw NotFoundException("Vintage")
        listOf(i.sweetness, i.acidity, i.tannin, i.body, i.intensity).forEach { s ->
            if (s != null) requireInput(s in 1..5, "scales") { "Scales run from 1 to 5" }
        }
        return i.copy(
            appearance = i.appearance.cleanedMultiline(4000), nose = i.nose.cleanedMultiline(4000), palate = i.palate.cleanedMultiline(4000),
            finish = i.finish.cleanedMultiline(4000), conclusion = i.conclusion.cleanedMultiline(4000), notes = i.notes.cleanedMultiline(),
            occasion = i.occasion.cleaned(200), companions = i.companions.cleaned(300), foodPairing = i.foodPairing.cleaned(300),
            place = i.place.cleaned(200), aromas = i.aromas.mapNotNull { it.cleaned(40) }.distinct().take(40),
        )
    }

    private fun Tasting_note.toNote(): TastingNote {
        val scale = RatingScale.fromCode(rating_scale)
        val value = rating_value?.let(Decimal::parseOrNull)
        return TastingNote(
            id = id, vintageId = vintage_id, authorId = author_id, createdAt = created_at,
            input = TastingInput(
                vintageId = vintage_id,
                tastedOn = LocalDate.parse(tasted_on),
                rating = if (scale != null && value != null) runCatching { Rating(value, scale) }.getOrNull() else null,
                appearance = appearance, nose = nose, palate = palate, finish = finish, conclusion = conclusion,
                sweetness = sweetness?.toInt(), acidity = acidity?.toInt(), tannin = tannin?.toInt(), body = body?.toInt(), intensity = intensity?.toInt(),
                aromas = runCatching { json.decodeFromString(listSer, aromas) }.getOrDefault(emptyList()),
                notes = notes, occasion = occasion, companions = companions, foodPairing = food_pairing, place = place,
                wouldBuyAgain = would_buy_again?.let { it == 1L }, lotId = lot_id, movementId = movement_id,
            ),
        )
    }
}
