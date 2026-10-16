package com.houseofvino.ui.format

import com.houseofvino.core.model.BottleFormat
import com.houseofvino.core.money.Currency
import com.houseofvino.core.money.Money
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Display formatting. Exact values are formatted from their decimal strings, never via Double. */
object Fmt {
    private val symbols = mapOf(
        "NZD" to "$", "AUD" to "$", "USD" to "$", "CAD" to "$", "SGD" to "$", "HKD" to "$",
        "EUR" to "€", "GBP" to "£", "JPY" to "¥", "CNY" to "¥", "CHF" to "CHF ", "ZAR" to "R ",
    )

    /** "$89.99" in the home currency; "€45.00" or "45.00 SEK" otherwise; "US$" style for other dollars. */
    fun money(m: Money, home: Currency? = null): String {
        val plain = grouped(m.toPlainString())
        val sym = symbols[m.currency.code]
        return when {
            m.currency == home && sym != null -> "${sign(plain)}$sym${plain.removePrefix("-")}"
            sym == "$" -> "${sign(plain)}${m.currency.code.take(2)}$${plain.removePrefix("-")}"
            sym != null -> "${sign(plain)}$sym${plain.removePrefix("-")}"
            else -> "$plain ${m.currency.code}"
        }
    }

    private fun sign(s: String) = if (s.startsWith("-")) "−" else ""

    /** "1234567.50" -> "1,234,567.50" */
    fun grouped(plain: String): String {
        val negative = plain.startsWith("-")
        val body = plain.removePrefix("-")
        val int = body.substringBefore('.')
        val frac = body.substringAfter('.', "")
        val g = int.reversed().chunked(3).joinToString(",").reversed()
        return (if (negative) "-" else "") + g + (if (frac.isNotEmpty()) ".$frac" else "")
    }

    fun count(n: Int, one: String, many: String = one + "s"): String = "${grouped(n.toString())} ${if (n == 1) one else many}"

    fun bottles(n: Int) = count(n, "bottle")

    fun format(ml: Int) = BottleFormat.label(ml)

    fun date(d: LocalDate): String = "${d.day} ${MONTHS[d.month.ordinal]} ${d.year}"

    fun date(millis: Long, zone: TimeZone): String = date(Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date)

    fun dateTime(millis: Long, zone: TimeZone): String {
        val dt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
        return "${date(dt.date)}, ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
    }

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
}
