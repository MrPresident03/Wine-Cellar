package com.houseofvino.core.model

enum class WindowSource(val code: String, val label: String) {
    USER("user", "My estimate"),
    PRODUCER("producer", "Producer"),
    CRITIC("critic", "Critic"),
    MERCHANT("merchant", "Merchant"),
    OTHER("other", "Other");

    companion object {
        fun fromCode(code: String?): WindowSource = entries.firstOrNull { it.code == code } ?: OTHER
    }
}

enum class DrinkStatus(val label: String) {
    UNKNOWN("No window set"),
    TOO_YOUNG("Hold"),
    READY("Ready"),
    DRINK_SOON("Drink soon"),
    PAST_PEAK("Past window"),
}

/**
 * A drinking window in whole years, as recorded by the user with its [source]. Either end may be
 * open. The app never invents one; [status] is UNKNOWN until someone enters it.
 */
data class DrinkingWindow(val fromYear: Int?, val toYear: Int?, val source: WindowSource, val note: String? = null) {
    init {
        if (fromYear != null && toYear != null) require(fromYear <= toYear) { "Window starts after it ends" }
    }

    fun status(currentYear: Int): DrinkStatus = when {
        fromYear == null && toYear == null -> DrinkStatus.UNKNOWN
        fromYear != null && currentYear < fromYear -> DrinkStatus.TOO_YOUNG
        toYear != null && currentYear > toYear -> DrinkStatus.PAST_PEAK
        toYear != null && currentYear >= toYear - 1 -> DrinkStatus.DRINK_SOON
        else -> DrinkStatus.READY
    }

    fun label(): String = when {
        fromYear != null && toYear != null -> if (fromYear == toYear) "$fromYear" else "$fromYear–$toYear"
        fromYear != null -> "From $fromYear"
        toYear != null -> "By $toYear"
        else -> "—"
    }

    companion object {
        fun statusOf(fromYear: Int?, toYear: Int?, currentYear: Int): DrinkStatus =
            if (fromYear != null && toYear != null && fromYear > toYear) {
                DrinkStatus.UNKNOWN
            } else {
                DrinkingWindow(fromYear, toYear, WindowSource.OTHER).status(currentYear)
            }
    }
}
