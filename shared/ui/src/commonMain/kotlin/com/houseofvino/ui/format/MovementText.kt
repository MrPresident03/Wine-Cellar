package com.houseofvino.ui.format

import com.houseofvino.core.model.BottleFormat
import com.houseofvino.core.model.MovementKind

/**
 * One plain sentence per ledger entry, in the user's vocabulary ("Opened 1 bottle from Rack A"),
 * never the ledger's ("CONSUME −1").
 */
object MovementText {

    /** The parts of a movement needed to describe it, so this stays independent of screens. */
    data class Line(
        val kind: MovementKind,
        val quantity: Int,
        val formatMl: Int?,
        val from: String?,
        val to: String?,
    )

    fun sentence(line: Line, undid: Line? = null): String {
        val n = units(line.quantity, line.formatMl)
        val from = line.from
        val to = line.to
        return when (line.kind) {
            MovementKind.PURCHASE -> "Bought $n" + (to?.let { ", stored in $it" } ?: "")
            MovementKind.RECEIVE -> "Received $n" + (to?.let { ", stored in $it" } ?: "")
            MovementKind.CONSUME -> "Opened $n" + (from?.let { " from $it" } ?: "")
            MovementKind.SELL -> "Sold $n" + (from?.let { " from $it" } ?: "")
            MovementKind.GIFT_OUT -> "Gave away $n" + (from?.let { " from $it" } ?: "")
            MovementKind.LOSS -> "Lost $n, broken or spoiled" + (from?.let { ", in $it" } ?: "")
            MovementKind.ADJUST_IN -> "Recount found ${more(line.quantity, line.formatMl, "more")}" + (to?.let { " in $it" } ?: "")
            MovementKind.ADJUST_OUT -> "Recount found ${more(line.quantity, line.formatMl, "fewer")}" + (from?.let { " in $it" } ?: "")
            MovementKind.TRANSFER -> "Moved $n" + when {
                from != null && to != null -> " from $from to $to"
                to != null -> " to $to"
                from != null -> " from $from"
                else -> ""
            }
            MovementKind.REVERSAL -> if (undid != null && undid.kind != MovementKind.REVERSAL) {
                "Undid “${sentence(undid)}”"
            } else {
                "Corrected an earlier entry"
            }
        }
    }

    /** "1 bottle", "3 magnums", "2 half bottles". Standard and unusual sizes are "bottles". */
    fun units(quantity: Int, formatMl: Int?): String {
        val named = formatMl?.takeIf { it != BottleFormat.STANDARD_ML }?.let { ml -> SPOKEN[ml] }
        val (one, many) = named ?: ("bottle" to "bottles")
        return Fmt.count(quantity, one, many)
    }

    private fun more(quantity: Int, formatMl: Int?, word: String): String {
        val u = units(quantity, formatMl)
        val space = u.indexOf(' ')
        return u.substring(0, space) + " $word" + u.substring(space)
    }

    private val SPOKEN = mapOf(
        375 to ("half bottle" to "half bottles"),
        1500 to ("magnum" to "magnums"),
        3000 to ("double magnum" to "double magnums"),
    )
}
