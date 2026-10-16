package com.houseofvino.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

interface AppClock {
    fun nowMillis(): Long
    fun timeZone(): TimeZone
    fun today(): LocalDate = Instant.fromEpochMilliseconds(nowMillis()).toLocalDateTime(timeZone()).date
    fun currentYear(): Int = today().year
}

object SystemClock : AppClock {
    override fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
    override fun timeZone(): TimeZone = TimeZone.currentSystemDefault()
}

/** For tests: time only moves when told to. */
class FixedClock(var millis: Long, private val zone: TimeZone = TimeZone.UTC) : AppClock {
    override fun nowMillis(): Long = millis
    override fun timeZone(): TimeZone = zone
    fun advance(ms: Long) {
        millis += ms
    }
}
