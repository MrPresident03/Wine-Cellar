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
