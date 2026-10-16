package com.houseofvino.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

class TextNormalizerTest {
    @Test
    fun foldsAccentsCaseAndPunctuation() {
        assertEquals("chateau lafite rothschild", TextNormalizer.fold("Château Lafite-Rothschild"))
        assertEquals("chateau dyquem", TextNormalizer.fold("Château d’Yquem"))
        assertEquals("grosses gewachs", TextNormalizer.fold("Großes Gewächs"))
        assertEquals("cotes du rhone", TextNormalizer.fold("Côtes du Rhône"))
        assertEquals("champagne", TextNormalizer.fold("Champagné")) // stray combining mark
        assertEquals(listOf("egon", "muller", "scharzhofberger"), TextNormalizer.tokens("  Egon Müller — Scharzhofberger "))
    }

    @Test
    fun identityKeysIgnoreFillerWords() {
        assertEquals(TextNormalizer.identityKey("Felton Road Wines"), TextNormalizer.identityKey("felton road"))
        assertEquals("", TextNormalizer.identityKey(null))
    }

    @Test
    fun editDistance() {
        assertEquals(1, EditDistance.osa("barolo", "barollo"))
        assertEquals(1, EditDistance.osa("pinot", "pniot")) // transposition
        assertEquals(3, EditDistance.osa("abc", "xyz"))
        assertEquals(2, EditDistance.osa("kitten", "sitting", max = 1)) // early exit returns max + 1
        assertEquals(0, EditDistance.allowance(3))
        assertEquals(2, EditDistance.allowance(9))
    }
}
