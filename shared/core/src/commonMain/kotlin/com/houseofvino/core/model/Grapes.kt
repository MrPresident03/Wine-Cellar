package com.houseofvino.core.model

import com.houseofvino.core.money.Decimal
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A grape in a wine's blend; [percent] is optional and exact. */
@Serializable
data class GrapeShare(val name: String, val percent: Decimal? = null) {
    init {
        require(name.isNotBlank()) { "Grape name is empty" }
        if (percent != null) require(!percent.isNegative && percent <= Decimal.of(100)) { "Percentage must be between 0 and 100" }
    }
}

object Grapes {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val serializer = ListSerializer(GrapeShare.serializer())

    fun encode(list: List<GrapeShare>): String = json.encodeToString(serializer, list)

    /** Tolerates bad stored data by returning an empty list rather than crashing a screen. */
    fun decode(text: String?): List<GrapeShare> =
        if (text.isNullOrBlank()) emptyList() else runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyList())

    /** "Cabernet Sauvignon 60%, Merlot 40%" or "Cabernet Sauvignon, Merlot" -> shares. */
    fun parse(text: String): List<GrapeShare> = text.split(',', ';', '/', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { part ->
            val m = Regex("""^(.*?)\s*(\d{1,3}(?:[.,]\d+)?)\s*%$""").find(part)
            if (m != null) {
                val pct = Decimal.parseOrNull(m.groupValues[2].replace(',', '.'))
                GrapeShare(m.groupValues[1].trim().ifEmpty { part }, pct?.takeIf { it <= Decimal.of(100) })
            } else {
                GrapeShare(part)
            }
        }

    fun display(list: List<GrapeShare>): String = list.joinToString(", ") { g -> g.percent?.let { "${g.name} ${it.stripTrailingZeros()}%" } ?: g.name }
}
