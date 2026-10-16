package com.houseofvino.data.repo

import com.houseofvino.core.model.BottleFormat
import com.houseofvino.core.model.Ids
import com.houseofvino.core.model.Vintage
import com.houseofvino.core.model.WineStyle
import com.houseofvino.core.money.Currency
import com.houseofvino.core.money.Money
import com.houseofvino.data.NotFoundException
import com.houseofvino.data.Session
import com.houseofvino.data.db.Wishlist_item
import com.houseofvino.data.db.Wishlist_link
import com.houseofvino.data.sync.SyncTables
import kotlinx.datetime.LocalDate

enum class WishStatus(val code: String, val label: String) {
    WANTED("wanted", "Wanted"),
    WATCHING("watching", "Watching the price"),
    ORDERED("ordered", "Ordered"),
    ACQUIRED("acquired", "Bought"),
    DROPPED("dropped", "No longer wanted");

    companion object {
        fun fromCode(c: String?) = entries.firstOrNull { it.code == c } ?: WANTED
    }
}

enum class WishPriority(val value: Int, val label: String) {
    HIGH(1, "High"), NORMAL(2, "Normal"), LOW(3, "Low");

    companion object {
        fun fromValue(v: Int) = entries.firstOrNull { it.value == v } ?: NORMAL
    }
}

data class WishInput(
    val title: String,
    val wineId: String? = null,
    val vintageId: String? = null,
    val producer: String? = null,
    val wineName: String? = null,
    val style: WineStyle? = null,
    val region: String? = null,
    val vintageFrom: Int? = null,
    val vintageTo: Int? = null,
    val acceptsNonVintage: Boolean = false,
    val formatMl: Int? = null,
    val desiredQuantity: Int = 1,
    val targetPrice: Money? = null,
    val maxPrice: Money? = null,
    val priority: WishPriority = WishPriority.NORMAL,
    val status: WishStatus = WishStatus.WANTED,
    val notes: String? = null,
)

data class WishItem(val id: String, val input: WishInput, val acquiredLotId: String?, val createdAt: Long)

data class MerchantLink(
    val id: String,
    val itemId: String,
    val merchant: String?,
    val url: String?,
    val lastPrice: Money?,
    val priceSeenOn: LocalDate?,
    val note: String?,
)

data class MerchantLinkInput(val merchant: String?, val url: String?, val lastPrice: Money?, val priceSeenOn: LocalDate?, val note: String?)

/** What the user paid when converting a wishlist item into bottles in the cellar. */
data class ConversionInput(
    val vintage: Vintage,
    val quantity: Int,
    val locationId: String,
    val formatMl: Int = BottleFormat.STANDARD_ML,
    val unitPrice: Money? = null,
    val acquiredOn: LocalDate? = null,
    val source: String? = null,
)

class WishlistRepository(private val store: Store, private val wines: WineRepository, private val inventory: InventoryRepository) {
    private val db get() = store.db

    fun item(id: String): WishItem? = db.wishlistQueries.byId(id).executeAsOneOrNull()?.takeIf { it.deleted_at == null }?.toItem()

    fun active(session: Session): List<WishItem> = db.wishlistQueries.active(session.householdId).executeAsList().map { it.toItem() }

    fun all(session: Session): List<WishItem> = db.wishlistQueries.all(session.householdId).executeAsList().map { it.toItem() }

    fun links(itemId: String): List<MerchantLink> = db.wishlistQueries.linksFor(itemId).executeAsList().map { it.toLink() }

    fun add(session: Session, input: WishInput): String = store.write {
        session.requireWrite()
        val c = validate(session, input)
        val id = Ids.new()
        val now = store.now()
        db.wishlistQueries.insert(
            id, session.householdId, c.wineId, c.vintageId, c.title, c.producer, c.wineName, c.style?.code, c.region,
            c.vintageFrom?.toLong(), c.vintageTo?.toLong(), if (c.acceptsNonVintage) 1L else 0L, c.formatMl?.toLong(), c.desiredQuantity.toLong(),
            c.targetPrice?.minor, c.maxPrice?.minor, (c.targetPrice ?: c.maxPrice)?.currency?.code, c.priority.value.toLong(), c.status.code,
            c.notes, now, now, session.userId,
        )
        store.outbox.afterInsert(SyncTables.WISHLIST, id, session.householdId)
        store.indexer.reindexWishlist(id)
        id
    }

    fun update(session: Session, id: String, input: WishInput) = store.write {
        session.requireWrite()
        requireOwned(session, id)
        val c = validate(session, input)
        store.outbox.beforeUpdate(SyncTables.WISHLIST, id)
        db.wishlistQueries.update(
            wineId = c.wineId, vintageId = c.vintageId, title = c.title, producer = c.producer, wineName = c.wineName, style = c.style?.code,
            region = c.region, vintageFrom = c.vintageFrom?.toLong(), vintageTo = c.vintageTo?.toLong(), acceptsNv = if (c.acceptsNonVintage) 1L else 0L,
            formatMl = c.formatMl?.toLong(), desiredQuantity = c.desiredQuantity.toLong(), targetPriceMinor = c.targetPrice?.minor,
            maxPriceMinor = c.maxPrice?.minor, priceCurrency = (c.targetPrice ?: c.maxPrice)?.currency?.code, priority = c.priority.value.toLong(),
            status = c.status.code, notes = c.notes, now = store.now(), by = session.userId, id = id,
        )
        store.indexer.reindexWishlist(id)
    }

    fun setStatus(session: Session, id: String, status: WishStatus) = store.write {
        session.requireWrite()
        requireOwned(session, id)
        store.outbox.beforeUpdate(SyncTables.WISHLIST, id)
        db.wishlistQueries.setStatus(status.code, store.now(), session.userId, id)
    }

    fun delete(session: Session, id: String) = store.write {
        session.requireWrite()
        requireOwned(session, id)
        store.outbox.beforeUpdate(SyncTables.WISHLIST, id)
        db.wishlistQueries.softDelete(store.now(), session.userId, id)
        store.indexer.reindexWishlist(id)
    }

    fun addLink(session: Session, itemId: String, input: MerchantLinkInput): String = store.write {
        session.requireWrite()
        requireOwned(session, itemId)
        val c = validateLink(input)
        val id = Ids.new()
        val now = store.now()
        db.wishlistQueries.insertLink(id, session.householdId, itemId, c.merchant, c.url, c.lastPrice?.minor, c.lastPrice?.currency?.code, c.priceSeenOn?.toString(), c.note, now, now, session.userId)
        store.outbox.afterInsert(SyncTables.WISHLIST_LINK, id, session.householdId)
        store.indexer.reindexWishlist(itemId)
        id
    }

    fun updateLink(session: Session, linkId: String, itemId: String, input: MerchantLinkInput) = store.write {
        session.requireWrite()
        requireOwned(session, itemId)
        val c = validateLink(input)
        store.outbox.beforeUpdate(SyncTables.WISHLIST_LINK, linkId)
        db.wishlistQueries.updateLink(c.merchant, c.url, c.lastPrice?.minor, c.lastPrice?.currency?.code, c.priceSeenOn?.toString(), c.note, store.now(), session.userId, linkId)
        store.indexer.reindexWishlist(itemId)
    }

    fun deleteLink(session: Session, linkId: String, itemId: String) = store.write {
        session.requireWrite()
        store.outbox.beforeUpdate(SyncTables.WISHLIST_LINK, linkId)
        db.wishlistQueries.softDeleteLink(store.now(), session.userId, linkId)
        store.indexer.reindexWishlist(itemId)
    }

    /**
     * "I bought it": in one transaction, finds or creates the wine and vintage, records the
     * purchase, and marks the item as bought with a link to the new purchase. If fewer bottles
     * than wanted were bought, the item stays open with the remaining quantity.
     */
    fun convertToPurchase(session: Session, itemId: String, input: ConversionInput, wineIfNew: WineInput? = null): String = store.write {
        session.requireWrite()
        val item = db.wishlistQueries.byId(itemId).executeAsOneOrNull()
        if (item == null || item.household_id != session.householdId || item.deleted_at != null) throw NotFoundException("Wishlist item")
        requireInput(input.quantity >= 1, "quantity") { "At least one bottle" }
        val wineId = item.wine_id ?: wines.createWine(
            session,
            wineIfNew ?: WineInput(producer = item.producer ?: item.title, name = item.wine_name.orEmpty(), style = WineStyle.fromCode(item.style), region = item.region),
            allowDuplicate = true,
        )
        val vintageId = item.vintage_id?.takeIf { wines.vintage(it)?.vintage == input.vintage }
            ?: wines.vintageFor(session, wineId, VintageInput(input.vintage))
        val lotId = inventory.addPurchase(
            session,
            PurchaseInput(
                vintageId = vintageId, quantity = input.quantity, locationId = input.locationId, formatMl = input.formatMl,
                unitPrice = input.unitPrice, acquiredOn = input.acquiredOn, source = input.source, wishlistItemId = itemId,
            ),
        )
        store.outbox.beforeUpdate(SyncTables.WISHLIST, itemId)
        val remaining = item.desired_quantity.toInt() - input.quantity
        if (remaining > 0) {
            db.wishlistQueries.update(
                wineId = wineId, vintageId = item.vintage_id, title = item.title, producer = item.producer, wineName = item.wine_name,
                style = item.style, region = item.region, vintageFrom = item.vintage_from, vintageTo = item.vintage_to,
                acceptsNv = item.accepts_nv, formatMl = item.format_ml, desiredQuantity = remaining.toLong(),
                targetPriceMinor = item.target_price_minor, maxPriceMinor = item.max_price_minor, priceCurrency = item.price_currency,
                priority = item.priority, status = item.status, notes = item.notes, now = store.now(), by = session.userId, id = itemId,
            )
        } else {
            db.wishlistQueries.markAcquired(lotId, wineId, vintageId, store.now(), session.userId, itemId)
        }
        store.indexer.reindexWishlist(itemId)
        lotId
    }

    private fun requireOwned(session: Session, id: String) {
        val row = db.wishlistQueries.byId(id).executeAsOneOrNull()
        if (row == null || row.household_id != session.householdId || row.deleted_at != null) throw NotFoundException("Wishlist item")
    }

    private fun validate(session: Session, i: WishInput): WishInput {
        val title = i.title.cleaned(200) ?: listOfNotNull(i.producer.cleaned(), i.wineName.cleaned()).joinToString(" ").ifBlank { null }
        requireInput(title != null, "title") { "Say which wine you want" }
        requireInput(i.desiredQuantity in 1..10_000, "quantity") { "Quantity must be at least 1" }
        if (i.vintageFrom != null && i.vintageTo != null) requireInput(i.vintageFrom <= i.vintageTo, "vintage") { "The vintage range is back to front" }
        i.formatMl?.let { requireInput(BottleFormat.isValid(it), "format") { "Bottle size must be between 50 ml and 30 L" } }
        if (i.targetPrice != null && i.maxPrice != null) {
            requireInput(i.targetPrice.currency == i.maxPrice.currency, "price") { "Use one currency for target and maximum" }
            requireInput(i.targetPrice <= i.maxPrice, "price") { "The target price is above the maximum" }
        }
        listOfNotNull(i.targetPrice, i.maxPrice).forEach { requireInput(!it.isNegative, "price") { "Prices can't be negative" } }
        i.wineId?.let { w -> wines.wine(w)?.takeIf { it.householdId == session.householdId } ?: throw NotFoundException("Wine") }
        return i.copy(title = title!!, producer = i.producer.cleaned(200), wineName = i.wineName.cleaned(200), region = i.region.cleaned(100), notes = i.notes.cleanedMultiline())
    }

    private fun validateLink(i: MerchantLinkInput): MerchantLinkInput {
        val url = i.url.cleaned(2000)
        if (url != null) requireInput(url.startsWith("https://") || url.startsWith("http://"), "url") { "Links must start with https://" }
        requireInput(i.merchant.cleaned() != null || url != null, "merchant") { "Enter a merchant or a link" }
        i.lastPrice?.let { requireInput(!it.isNegative, "price") { "Prices can't be negative" } }
        return i.copy(merchant = i.merchant.cleaned(150), url = url, note = i.note.cleaned(300))
    }

    private fun Wishlist_item.toItem(): WishItem {
        val currency = price_currency?.let { Currency.parseOrNull(it) }
        return WishItem(
            id = id,
            acquiredLotId = acquired_lot_id,
            createdAt = created_at,
            input = WishInput(
                title = title, wineId = wine_id, vintageId = vintage_id, producer = producer, wineName = wine_name,
                style = WineStyle.fromCode(style), region = region, vintageFrom = vintage_from?.toInt(), vintageTo = vintage_to?.toInt(),
                acceptsNonVintage = accepts_nv == 1L, formatMl = format_ml?.toInt(), desiredQuantity = desired_quantity.toInt(),
                targetPrice = if (currency != null && target_price_minor != null) Money(target_price_minor, currency) else null,
                maxPrice = if (currency != null && max_price_minor != null) Money(max_price_minor, currency) else null,
                priority = WishPriority.fromValue(priority.toInt()), status = WishStatus.fromCode(status), notes = notes,
            ),
        )
    }

    private fun Wishlist_link.toLink() = MerchantLink(
        id = id, itemId = item_id, merchant = merchant, url = url,
        lastPrice = money(last_price_minor, last_price_currency),
        priceSeenOn = price_seen_on?.let { runCatching { LocalDate.parse(it) }.getOrNull() }, note = note,
    )
}
