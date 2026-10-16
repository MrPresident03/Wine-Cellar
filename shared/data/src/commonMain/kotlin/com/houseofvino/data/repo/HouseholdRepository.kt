package com.houseofvino.data.repo

import com.houseofvino.core.model.HouseholdRole
import com.houseofvino.core.model.Ids
import com.houseofvino.core.model.LocationKind
import com.houseofvino.core.money.Currency
import com.houseofvino.data.Session
import com.houseofvino.data.sync.SyncTables

data class HouseholdDetails(val id: String, val name: String, val homeCurrency: Currency, val isLocalOnly: Boolean)

/** Device identity and the household the app is currently showing. */
class HouseholdRepository(private val store: Store) {
    private val db get() = store.db

    fun deviceId(): String = store.write {
        db.appMetaQueries.get(KEY_DEVICE).executeAsOneOrNull() ?: Ids.new().also { db.appMetaQueries.put(KEY_DEVICE, it) }
    }

    fun household(id: String): HouseholdDetails? = db.householdQueries.byId(id).executeAsOneOrNull()?.let {
        HouseholdDetails(it.id, it.name, Currency.parseOrNull(it.home_currency) ?: Currency("NZD"), it.is_local_only == 1L)
    }

    /**
     * The session to start the app with. On first launch this creates a local household with one
     * place ("Cellar") so the app is usable immediately, with no account.
     */
    fun startSession(defaultCurrency: Currency, userId: String? = null): Session = store.write {
        val device = deviceId()
        val current = db.appMetaQueries.get(KEY_CURRENT_HOUSEHOLD).executeAsOneOrNull()?.let { household(it) }
        val householdId = current?.id ?: createLocalHousehold(defaultCurrency, userId)
        val role = userId?.let { uid -> db.householdQueries.members(householdId).executeAsList().firstOrNull { it.user_id == uid }?.role }
        Session(householdId, device, userId, role?.let(HouseholdRole::fromCode) ?: HouseholdRole.OWNER)
    }

    fun rename(session: Session, name: String) = store.write {
        session.requireWrite()
        val clean = name.cleaned(80)
        requireInput(clean != null, "name") { "Give the cellar a name" }
        store.outbox.beforeUpdate(SyncTables.HOUSEHOLD, session.householdId)
        db.householdQueries.rename(clean!!, store.now(), session.userId, session.householdId)
    }

    fun setHomeCurrency(session: Session, currency: Currency) = store.write {
        session.requireWrite()
        store.outbox.beforeUpdate(SyncTables.HOUSEHOLD, session.householdId)
        db.householdQueries.setHomeCurrency(currency.code, store.now(), session.userId, session.householdId)
    }

    private fun createLocalHousehold(currency: Currency, userId: String?): String {
        val id = Ids.new()
        val now = store.now()
        db.householdQueries.insert(id, "My cellar", currency.code, if (userId == null) 1L else 0L, now, now, userId)
        store.outbox.afterInsert(SyncTables.HOUSEHOLD, id, id)
        val siteId = Ids.new()
        db.locationQueries.insert(siteId, id, null, LocationKind.SITE.code, LocationRepository.DEFAULT_SITE_NAME, 0L, null, null, null, now, now, userId)
        store.outbox.afterInsert(SyncTables.LOCATION, siteId, id)
        db.appMetaQueries.put(KEY_CURRENT_HOUSEHOLD, id)
        return id
    }

    companion object {
        const val KEY_DEVICE = "device_id"
        const val KEY_CURRENT_HOUSEHOLD = "current_household"
    }
}
