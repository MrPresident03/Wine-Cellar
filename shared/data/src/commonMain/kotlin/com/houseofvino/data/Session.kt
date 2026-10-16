package com.houseofvino.data

import com.houseofvino.core.model.HouseholdRole

/**
 * Who is acting, on which household, from which device. Every write records [userId] (null in
 * local-only mode) and [deviceId]. Repositories refuse writes when [role] can't write.
 */
data class Session(
    val householdId: String,
    val deviceId: String,
    val userId: String? = null,
    val role: HouseholdRole = HouseholdRole.OWNER,
) {
    fun requireWrite() {
        if (!role.canWrite) throw ReadOnlyHouseholdException()
    }
}

class ReadOnlyHouseholdException : IllegalStateException("You have view-only access to this cellar")

class NotFoundException(what: String) : NoSuchElementException("$what not found")
