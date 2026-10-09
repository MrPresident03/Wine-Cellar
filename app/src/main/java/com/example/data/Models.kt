package com.example.data

/** A bottle in the shared cellar. [id] is the Firestore document id (stable, never the slot). */
data class WineBottle(
    val id: String,
    val wineryName: String,
    val classification: String? = null,
    val varietal: String,
    val vintage: String,
    val gridRow: Int,
    val gridCol: Int,
    val price: Double? = null,
    val isAging: Boolean = false,
    /** Colour style used when there's no photo, e.g. "preset_cabernet". */
    val preset: String? = null,
    /** Small base64 JPEG used in the grid and list. The full photo lives in photos/{id}. */
    val thumb: String? = null,
    val hasPhoto: Boolean = false,
    val createdAt: Long = 0L
)

/** What the add/edit form produces. */
data class BottleDraft(
    val wineryName: String,
    val classification: String?,
    val varietal: String,
    val vintage: String,
    val gridRow: Int,
    val gridCol: Int,
    val price: Double?,
    val isAging: Boolean,
    val preset: String
)

/** A processed photo: a small thumbnail plus a larger image, both base64 JPEG. */
data class PhotoData(val thumb: String, val full: String)

sealed interface PhotoChange {
    data object Keep : PhotoChange
    data object Remove : PhotoChange
    data class Replace(val photo: PhotoData) : PhotoChange
}

data class Cellar(
    val id: String,
    val name: String,
    val ownerUid: String,
    val members: List<String>,
    val memberEmails: List<String>,
    val sensorKey: String,
    val alertThreshold: Double
)

data class ClimateReading(
    val timestamp: Long,
    val temperature: Float,
    val humidity: Float
)

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val uid: String, val email: String) : AuthState
}

sealed interface UserCellarState {
    data object Loading : UserCellarState
    data object None : UserCellarState
    data class Ready(val cellarId: String) : UserCellarState
    data class Error(val message: String) : UserCellarState
}

enum class TimeFilter(val label: String, val millis: Long) {
    DAY("Day", 24L * 60 * 60 * 1000),
    WEEK("Week", 7L * 24 * 60 * 60 * 1000),
    MONTH("Month", 30L * 24 * 60 * 60 * 1000)
}

object CellarLayout {
    const val ROWS = 18
    const val COLS = 10
}

object Prefs {
    const val NAME = "wine_cellar_v2"
    const val ALERTS_ENABLED = "alerts_enabled"
    const val ALERT_ACTIVE = "alert_active"
    const val LEGACY_IMPORTED_INTO = "legacy_imported_into"
}

val PRESET_STYLES = listOf(
    "preset_cabernet" to "Red",
    "preset_chardonnay" to "White",
    "preset_rose" to "Rosé",
    "preset_champagne" to "Sparkling"
)

/** Picks a bottle colour from the varietal name when no style was chosen. */
fun presetForVarietal(varietal: String): String {
    val v = varietal.lowercase()
    return when {
        v.contains("rosé") || v.contains("rose") -> "preset_rose"
        v.contains("champagne") || v.contains("sparkling") || v.contains("prosecco") || v.contains("glera") -> "preset_champagne"
        v.contains("cabernet") || v.contains("shiraz") || v.contains("syrah") || v.contains("merlot") ||
            v.contains("pinot noir") || v.contains("malbec") || v.contains("tempranillo") ||
            v.contains("nebbiolo") || v.contains("sangiovese") || v.contains("zinfandel") ||
            v.contains("grenache") || v.contains("gamay") || v.contains("barbera") -> "preset_cabernet"
        v.contains("chardonnay") || v.contains("sauvignon blanc") || v.contains("riesling") ||
            v.contains("pinot gris") || v.contains("pinot grigio") || v.contains("viognier") ||
            v.contains("chenin") || v.contains("blanc") || v.contains("white") || v.contains("gewürz") ||
            v.contains("grüner") || v.contains("albari") || v.contains("vermentino") -> "preset_chardonnay"
        else -> "preset_cabernet"
    }
}

val STANDARD_VARIETALS = listOf(
    "Albariño / Alvarinho",
    "Assyrtiko",
    "Barbera",
    "Cabernet Franc",
    "Cabernet Sauvignon",
    "Carmenere",
    "Champagne / Sparkling",
    "Chardonnay",
    "Chenin Blanc",
    "Fiano",
    "Furmint",
    "Gamay",
    "Garganega",
    "Gewürztraminer",
    "Glera",
    "Godello",
    "Grenache / Garnacha",
    "Grüner Veltliner",
    "Malbec",
    "Marsanne",
    "Merlot",
    "Moscato / Muscat",
    "Mourvèdre / Monastrell",
    "Müller-Thurgau",
    "Nebbiolo",
    "Nero d'Avola",
    "Palomino",
    "Pedro Ximénez",
    "Petit Verdot",
    "Petite Sirah",
    "Pinot Gris / Pinot Grigio",
    "Pinot Noir",
    "Pinotage",
    "Riesling",
    "Rosé",
    "Roussanne",
    "Sangiovese",
    "Sauvignon Blanc",
    "Sémillon",
    "Syrah / Shiraz",
    "Tannat",
    "Tempranillo",
    "Torrontés",
    "Touriga Nacional",
    "Vermentino",
    "Viognier",
    "Zinfandel / Primitivo",
    "Other"
)
