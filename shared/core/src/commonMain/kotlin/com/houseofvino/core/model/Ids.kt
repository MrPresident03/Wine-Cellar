package com.houseofvino.core.model

import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Stable identifiers. Every record gets a UUID on the device that creates it, so records can be
 * created offline and synced later without collisions. Version 7 UUIDs start with a millisecond
 * timestamp, which keeps database indexes ordered by creation time.
 */
object Ids {
    fun new(nowMillis: Long = Clock.System.now().toEpochMilliseconds()): String {
        val bytes = Uuid.random().toByteArray() // cryptographically secure random bits
        bytes[0] = (nowMillis ushr 40).toByte()
        bytes[1] = (nowMillis ushr 32).toByte()
        bytes[2] = (nowMillis ushr 24).toByte()
        bytes[3] = (nowMillis ushr 16).toByte()
        bytes[4] = (nowMillis ushr 8).toByte()
        bytes[5] = nowMillis.toByte()
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte() // version 7
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte() // RFC 4122 variant
        return Uuid.fromByteArray(bytes).toString()
    }

    fun isValid(id: String): Boolean = id.length == 36 && runCatching { Uuid.parse(id) }.isSuccess
}
