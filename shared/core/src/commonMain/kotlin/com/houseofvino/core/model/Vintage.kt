package com.houseofvino.core.model

/**
 * A wine's vintage. "Unknown" (we don't know yet) is deliberately different from "non-vintage"
 * (a blend of years, e.g. most Champagne), and both are different from a specific year.
 */
sealed interface Vintage : Comparable<Vintage> {
    data class Year(val year: Int) : Vintage {
        init {
            require(year in MIN_YEAR..MAX_YEAR) { "Vintage $year is outside $MIN_YEAR..$MAX_YEAR" }
        }
        override fun toString() = year.toString()
    }

    data object NonVintage : Vintage {
        override fun toString() = "NV"
    }

    data object Unknown : Vintage {
        override fun toString() = "Unknown"
    }

    /** Years first (ascending), then NV, then unknown. */
    override fun compareTo(other: Vintage): Int = sortKey().compareTo(other.sortKey())

    /** Storage form: kind column. */
    val kind: String
        get() = when (this) {
            is Year -> KIND_YEAR
            NonVintage -> KIND_NV
            Unknown -> KIND_UNKNOWN
        }

    val yearOrNull: Int? get() = (this as? Year)?.year

    companion object {
        const val MIN_YEAR = 1700
        const val MAX_YEAR = 2200
        const val KIND_YEAR = "year"
        const val KIND_NV = "nv"
        const val KIND_UNKNOWN = "unknown"

        fun fromStorage(kind: String, year: Long?): Vintage = when (kind) {
            KIND_YEAR -> Year(requireNotNull(year) { "year kind needs a year" }.toInt())
            KIND_NV -> NonVintage
            else -> Unknown
        }

        /** Lenient parsing for forms and imports: "2016", "NV", "N.V.", "non-vintage", "" -> Unknown. */
        fun parse(text: String?, maxYear: Int = MAX_YEAR): Vintage? {
            val t = text?.trim()?.lowercase()?.replace(".", "")?.replace("-", " ") ?: return Unknown
            if (t.isEmpty() || t == "unknown" || t == "?") return Unknown
            if (t == "nv" || t == "non vintage" || t == "nonvintage" || t == "sa" || t == "sans annee") return NonVintage
            val y = t.toIntOrNull() ?: return null
            return if (y in MIN_YEAR..maxYear) Year(y) else null
        }
    }
}

private fun Vintage.sortKey(): Int = when (this) {
    is Vintage.Year -> year
    Vintage.NonVintage -> Int.MAX_VALUE - 1
    Vintage.Unknown -> Int.MAX_VALUE
}
