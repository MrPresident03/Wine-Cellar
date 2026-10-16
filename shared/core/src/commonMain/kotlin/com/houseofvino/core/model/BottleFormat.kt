package com.houseofvino.core.model

/**
 * Bottle sizes in millilitres. Any positive size is allowed; the named ones are offered first.
 * Large-format names differ between Bordeaux and Champagne/Burgundy, so both are shown.
 */
object BottleFormat {
    const val STANDARD_ML = 750

    data class Named(val ml: Int, val name: String)

    val NAMED: List<Named> = listOf(
        Named(187, "Piccolo"),
        Named(375, "Half bottle"),
        Named(500, "500 ml"),
        Named(620, "Clavelin"),
        Named(750, "Standard"),
        Named(1000, "Litre"),
        Named(1500, "Magnum"),
        Named(3000, "Double magnum / Jeroboam"),
        Named(4500, "Rehoboam / Jeroboam (Bordeaux)"),
        Named(6000, "Imperial / Methuselah"),
        Named(9000, "Salmanazar"),
        Named(12000, "Balthazar"),
        Named(15000, "Nebuchadnezzar"),
    )

    fun isValid(ml: Int): Boolean = ml in 50..30000

    /**
     * Reads sizes as people and spreadsheets write them: "750", "750ml", "75cl", "1.5L", "1,5 l",
     * "Magnum", "half". Returns null when it can't tell.
     */
    fun parse(text: String?): Int? {
        val t = text?.trim()?.lowercase()?.replace(',', '.')?.replace(" ", "") ?: return null
        if (t.isEmpty()) return null
        NAMES[t]?.let { return it }
        val m = Regex("""^(\d+(?:\.\d+)?)(ml|cl|l|ltr|litre|liter|litres|liters)?$""").find(t) ?: return null
        val n = m.groupValues[1]
        val unit = m.groupValues[2]
        val ml = when (unit) {
            "", "ml" -> n.toDoubleOrNull()?.let { if (it < 20) it * 1000 else it }
            "cl" -> n.toDoubleOrNull()?.times(10)
            else -> n.toDoubleOrNull()?.times(1000)
        } ?: return null
        val rounded = kotlin.math.round(ml).toInt()
        return if (isValid(rounded)) rounded else null
    }

    private val NAMES = mapOf(
        "piccolo" to 187, "split" to 187, "quarter" to 187, "half" to 375, "halfbottle" to 375, "demi" to 375,
        "standard" to 750, "bottle" to 750, "magnum" to 1500, "doublemagnum" to 3000, "jeroboam" to 3000,
        "rehoboam" to 4500, "imperial" to 6000, "methuselah" to 6000, "salmanazar" to 9000,
        "balthazar" to 12000, "nebuchadnezzar" to 15000, "clavelin" to 620,
    )

    fun name(ml: Int): String = NAMED.firstOrNull { it.ml == ml }?.name ?: "${label(ml)}"

    /** "750 ml", "1.5 L", "3 L". */
    fun label(ml: Int): String = if (ml >= 1000) {
        val whole = ml / 1000
        val frac = (ml % 1000) / 100
        if (frac == 0) "$whole L" else "$whole.$frac L"
    } else {
        "$ml ml"
    }

    /** Number of standard-bottle equivalents, in tenths (a magnum is 20 = 2.0 bottles). */
    fun standardEquivalentTenths(ml: Int): Int = (ml * 10 + STANDARD_ML / 2) / STANDARD_ML
}
