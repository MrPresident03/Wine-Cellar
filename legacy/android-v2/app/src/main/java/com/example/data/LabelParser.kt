package com.example.data

import java.text.Normalizer

/**
 * Turns the text read off a wine label into form values. Labels vary wildly, so these are best
 * guesses for the user to check, not facts.
 */
object LabelParser {

    /** One line of text from the label, with its height in pixels (bigger = more prominent). */
    data class Line(val text: String, val height: Int)

    data class Result(
        val winery: String? = null,
        val vintage: String? = null,
        /** Matches an entry in [STANDARD_VARIETALS] when found. */
        val varietal: String? = null,
        val classification: String? = null
    ) {
        val isEmpty: Boolean get() = winery == null && vintage == null && varietal == null && classification == null
    }

    private val YEAR = Regex("""\b(19[5-9]\d|20[0-4]\d)\b""")
    private val FOUNDING_WORDS = Regex("""\b(est|estd|established|since|founded|depuis|fondee|seit)\b""")

    /** Extra spellings seen on labels, mapped to an entry in [STANDARD_VARIETALS]. */
    private val EXTRA_ALIASES = mapOf(
        "cab sav" to "Cabernet Sauvignon",
        "cab sauv" to "Cabernet Sauvignon",
        "sauv blanc" to "Sauvignon Blanc",
        "pinot grigio" to "Pinot Gris / Pinot Grigio",
        "prosecco" to "Glera",
        "methode traditionnelle" to "Champagne / Sparkling",
        "methode champenoise" to "Champagne / Sparkling",
        "cremant" to "Champagne / Sparkling",
        "cava" to "Champagne / Sparkling",
        "brut" to "Champagne / Sparkling",
        "rose" to "Rosé",
        "rosado" to "Rosé",
        "rosato" to "Rosé"
    )

    /** Classification phrases, longest first so "Gran Reserva" beats "Reserva". */
    private val CLASSIFICATIONS = listOf(
        "premier grand cru classe" to "Premier Grand Cru Classé",
        "grand cru classe" to "Grand Cru Classé",
        "premier grand cru" to "Premier Grand Cru",
        "single vineyard" to "Single Vineyard",
        "limited release" to "Limited Release",
        "limited edition" to "Limited Edition",
        "late harvest" to "Late Harvest",
        "gran reserva" to "Gran Reserva",
        "premier cru" to "Premier Cru",
        "grand cru" to "Grand Cru",
        "old vines" to "Old Vines",
        "old vine" to "Old Vine",
        "extra brut" to "Extra Brut",
        "brut nature" to "Brut Nature",
        "1er cru" to "1er Cru",
        "reserva" to "Reserva",
        "riserva" to "Riserva",
        "reserve" to "Reserve",
        "crianza" to "Crianza",
        "docg" to "DOCG",
        "private bin" to "Private Bin"
    )

    /** Lines that are almost never the winery name. */
    private val NOT_WINERY = Regex(
        """\b(wine of|product of|produce of|produced|bottled|vinified|grown|ml|cl|litre|liter|vol|alc|alcohol|""" +
            """contains|sulphites|sulfites|allergens|standard drinks|new zealand|australia|france|italy|spain|""" +
            """portugal|germany|austria|chile|argentina|south africa|usa|california|united states|vintage|""" +
            """www|http|com|appellation|controlee|denominacion|denominazione|origine|origen|origin)\b"""
    )

    private val WINERY_WORDS = Regex("""\b(estate|estates|winery|wines|vineyard|vineyards|cellars|chateau|domaine|bodega|bodegas|tenuta|weingut|cantina|quinta|maison)\b""")

    fun parse(lines: List<Line>, currentYear: Int): Result {
        val cleaned = lines
            .map { Line(it.text.trim().replace(Regex("""\s+"""), " "), it.height) }
            .filter { it.text.isNotEmpty() }
        if (cleaned.isEmpty()) return Result()
        val normLines = cleaned.map { normalize(it.text) }
        val allText = normLines.joinToString(" \n ")

        // ---- vintage: a plausible year that isn't a founding date
        val years = mutableListOf<Pair<Int, Boolean>>() // year, line is just the year
        cleaned.forEachIndexed { i, line ->
            val norm = normLines[i]
            if (FOUNDING_WORDS.containsMatchIn(norm)) return@forEachIndexed
            YEAR.findAll(line.text).forEach { m ->
                val y = m.value.toInt()
                if (y <= currentYear + 1) years += Pair(y, line.text.trim() == m.value)
            }
        }
        val vintage = (years.firstOrNull { it.second } ?: years.maxByOrNull { it.first })?.first?.toString()
        val isNonVintage = Regex("""\b(nv|non vintage|non-vintage|sans annee)\b""").containsMatchIn(allText)

        // ---- varietal: the longest known grape name that appears as whole words
        val aliases = mutableListOf<Pair<String, String>>()
        STANDARD_VARIETALS.filter { it != "Other" }.forEach { entry ->
            entry.split("/").map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
                aliases += Pair(normalize(part), entry)
            }
        }
        EXTRA_ALIASES.forEach { (alias, entry) -> aliases += Pair(alias, entry) }
        val varietal = aliases
            .filter { (alias, _) -> Regex("""\b""" + Regex.escape(alias) + """\b""").containsMatchIn(allText) }
            .maxByOrNull { it.first.length }
            ?.second

        // ---- classification
        val classification = CLASSIFICATIONS.sortedByDescending { it.first.length }.firstOrNull { (phrase, _) ->
            Regex("""\b""" + Regex.escape(phrase) + """\b""").containsMatchIn(allText)
        }?.second

        // ---- winery: the most prominent line that isn't something else
        val varietalAliases = aliases.map { it.first }.toSet()
        val classificationPhrases = CLASSIFICATIONS.map { it.first }.toSet()
        var best: Line? = null
        var bestScore = 0.0
        cleaned.forEachIndexed { i, line ->
            val norm = normLines[i]
            val letters = line.text.count { it.isLetter() }
            if (letters < 3 || line.text.length > 40) return@forEachIndexed
            if (YEAR.containsMatchIn(line.text)) return@forEachIndexed
            if (NOT_WINERY.containsMatchIn(norm)) return@forEachIndexed
            if (norm in varietalAliases || norm in classificationPhrases) return@forEachIndexed
            if (letters.toDouble() / line.text.length < 0.6) return@forEachIndexed
            var score = line.height.toDouble()
            if (WINERY_WORDS.containsMatchIn(norm)) score *= 1.3
            if (score > bestScore) {
                best = line
                bestScore = score
            }
        }
        val winery = best?.text?.let { niceCase(it) }

        return Result(
            winery = winery,
            vintage = vintage ?: if (isNonVintage) "NV" else null,
            varietal = varietal,
            classification = classification
        )
    }

    /** Lower case, accents removed, punctuation turned into spaces. */
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .lowercase()
            .replace(Regex("""[^a-z0-9%]+"""), " ")
            .trim()

    /** "CLOUDY BAY" -> "Cloudy Bay"; mixed-case text is left as printed. */
    private fun niceCase(s: String): String {
        val hasLower = s.any { it.isLowerCase() }
        if (hasLower) return s
        return s.lowercase().split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { it.titlecase() }
        }
    }
}
