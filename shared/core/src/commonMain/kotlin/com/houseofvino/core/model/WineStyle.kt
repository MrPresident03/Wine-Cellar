package com.houseofvino.core.model

enum class WineStyle(val code: String, val label: String) {
    RED("red", "Red"),
    WHITE("white", "White"),
    ROSE("rose", "Rosé"),
    SPARKLING("sparkling", "Sparkling"),
    SWEET("sweet", "Sweet / dessert"),
    FORTIFIED("fortified", "Fortified"),
    ORANGE("orange", "Orange / skin contact"),
    OTHER("other", "Other");

    companion object {
        fun fromCode(code: String?): WineStyle? = entries.firstOrNull { it.code == code }

        /** Best guess from a grape or style name, used only to pre-fill forms. Ported from v2. */
        fun guess(text: String): WineStyle? {
            val v = com.houseofvino.core.text.TextNormalizer.fold(text)
            fun has(vararg words: String) = words.any { v.contains(it) }
            return when {
                has("rose", "rosado", "rosato") -> ROSE
                has("champagne", "sparkling", "prosecco", "glera", "cava", "cremant", "spumante", "sekt", "methode") -> SPARKLING
                has("port", "sherry", "madeira", "marsala", "fino", "oloroso", "amontillado", "manzanilla") -> FORTIFIED
                has("sauternes", "tokaji", "late harvest", "icewine", "eiswein", "botrytis", "auslese", "beerenauslese") -> SWEET
                has("orange wine", "skin contact", "amber") -> ORANGE
                has(
                    "cabernet", "shiraz", "syrah", "merlot", "pinot noir", "malbec", "tempranillo", "nebbiolo", "sangiovese",
                    "zinfandel", "primitivo", "grenache", "garnacha", "gamay", "barbera", "mourvedre", "monastrell", "pinotage",
                    "carmenere", "petit verdot", "tannat", "touriga", "nero d avola", "dolcetto", "aglianico", "blaufrankisch",
                ) -> RED
                has(
                    "chardonnay", "sauvignon blanc", "riesling", "pinot gris", "pinot grigio", "viognier", "chenin", "semillon",
                    "gewurz", "gruner", "albarino", "vermentino", "marsanne", "roussanne", "fiano", "furmint", "godello",
                    "assyrtiko", "muller thurgau", "torrontes", "garganega", "white",
                ) -> WHITE
                else -> null
            }
        }
    }
}

/** Grape list offered in pickers; free text is always allowed. Carried over from v2. */
val STANDARD_GRAPES = listOf(
    "Aglianico", "Albariño", "Assyrtiko", "Barbera", "Blaufränkisch", "Cabernet Franc", "Cabernet Sauvignon", "Carménère",
    "Chardonnay", "Chenin Blanc", "Cinsault", "Dolcetto", "Fiano", "Furmint", "Gamay", "Garganega", "Gewürztraminer",
    "Glera", "Godello", "Grenache", "Grüner Veltliner", "Malbec", "Marsanne", "Merlot", "Moscato", "Mourvèdre",
    "Müller-Thurgau", "Nebbiolo", "Nero d'Avola", "Palomino", "Pedro Ximénez", "Petit Verdot", "Petite Sirah",
    "Pinot Blanc", "Pinot Gris", "Pinot Meunier", "Pinot Noir", "Pinotage", "Riesling", "Roussanne", "Sangiovese",
    "Sauvignon Blanc", "Sémillon", "Syrah", "Tannat", "Tempranillo", "Torrontés", "Touriga Nacional", "Vermentino",
    "Viognier", "Zinfandel",
)
