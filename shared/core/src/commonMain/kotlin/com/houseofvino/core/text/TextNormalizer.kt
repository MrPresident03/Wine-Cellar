package com.houseofvino.core.text

/**
 * Accent- and case-insensitive text folding that works the same on every platform (no
 * java.text.Normalizer on iOS). "Château Lafite-Rothschild" -> "chateau lafite rothschild".
 */
object TextNormalizer {

    private val FOLD: Map<Char, String> = buildMap {
        fun map(chars: String, to: String) = chars.forEach { put(it, to) }
        map("àáâãäåāăąǎǟǡǻȁȃȧ", "a"); map("æǣǽ", "ae")
        map("çćĉċč", "c"); map("ďđ", "d"); map("ð", "d")
        map("èéêëēĕėęěȅȇȩ", "e"); map("ĝğġģǧǵ", "g"); map("ĥħ", "h")
        map("ìíîïĩīĭįıǐȉȋ", "i"); map("ĳ", "ij"); map("ĵǰ", "j"); map("ķǩ", "k")
        map("ĺļľŀł", "l"); map("ñńņňŉǹ", "n"); map("òóôõöøōŏőǒǫǭǿȍȏȫȭȯȱ", "o"); map("œ", "oe")
        map("ŕŗřȑȓ", "r"); map("śŝşšș", "s"); map("ß", "ss"); map("ţťŧț", "t"); map("þ", "th")
        map("ùúûüũūŭůűųǔǖǘǚǜȕȗ", "u"); map("ŵ", "w"); map("ýÿŷȳ", "y"); map("źżžƶ", "z")
        // Typographic punctuation that should behave like its ASCII cousin.
        map("‘’‚‛ʼ`´", "'"); map("“”„‟", "\""); map("‐‑‒–—―", "-")
    }

    /** Lower-case, strip accents and combining marks, keep everything else (including punctuation). */
    fun foldCase(s: String): String {
        val lower = s.lowercase()
        val sb = StringBuilder(lower.length)
        for (c in lower) {
            if (c in '̀'..'ͯ') continue // combining diacritical marks (decomposed input)
            val mapped = FOLD[c]
            if (mapped != null) sb.append(mapped) else sb.append(c)
        }
        return sb.toString()
    }

    /**
     * Search/compare form: folded, punctuation turned into single spaces, trimmed.
     * Apostrophes inside words are dropped so "d'Yquem" and "dYquem" match ("d yquem" is also indexed by FTS).
     */
    fun fold(s: String): String {
        val f = foldCase(s)
        val sb = StringBuilder(f.length)
        var lastSpace = true
        for (c in f) {
            when {
                c.isLetterOrDigit() -> {
                    sb.append(c)
                    lastSpace = false
                }
                c == '\'' -> Unit
                else -> if (!lastSpace) {
                    sb.append(' ')
                    lastSpace = true
                }
            }
        }
        return sb.toString().trimEnd()
    }

    fun tokens(s: String): List<String> = fold(s).split(' ').filter { it.isNotEmpty() }

    /** Identity key used for duplicate detection: folded, with common filler words removed. */
    fun identityKey(s: String?): String {
        if (s.isNullOrBlank()) return ""
        return tokens(s).filterNot { it in FILLER }.joinToString(" ")
    }

    private val FILLER = setOf("the", "wines", "wine", "winery", "estate", "estates", "vineyards", "vineyard", "cellars")
}

object EditDistance {
    /**
     * Optimal string alignment (Damerau-Levenshtein with adjacent transpositions), with an early
     * exit once the distance is known to exceed [max]. Returns max + 1 in that case.
     */
    fun osa(a: String, b: String, max: Int = Int.MAX_VALUE): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prevPrev = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prevPrev[j - 2] + 1)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return max + 1
            val t = prevPrev
            prevPrev = prev
            prev = cur
            cur = t
        }
        return prev[b.length]
    }

    /** How many typos to forgive for a term of this length. */
    fun allowance(termLength: Int): Int = when {
        termLength < 4 -> 0
        termLength < 8 -> 1
        else -> 2
    }
}
