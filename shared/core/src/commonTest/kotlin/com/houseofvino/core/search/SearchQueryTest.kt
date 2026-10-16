package com.houseofvino.core.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchQueryTest {
    @Test
    fun parsesTermsAndInlineVintageRanges() {
        val p = QueryParser.parse("Château Margaux 2000–2010")
        assertEquals(listOf("chateau", "margaux"), p.terms)
        assertEquals(2000, p.vintageFrom)
        assertEquals(2010, p.vintageTo)
        assertEquals(listOf("2015"), QueryParser.parse("2015").terms)
        val reversed = QueryParser.parse("1995 to 1990")
        assertEquals(1990, reversed.vintageFrom)
        assertEquals(1995, reversed.vintageTo)
    }

    @Test
    fun buildsSafeFtsExpressions() {
        assertNull(FtsExpression.build(emptyList()))
        assertEquals("\"pinot\"* AND \"noir\"*", FtsExpression.build(listOf("pinot", "noir")))
        assertEquals("(\"barollo\"* OR \"barolo\"*)", FtsExpression.build(listOf("barollo"), mapOf("barollo" to listOf("barolo"))))
        assertEquals("\"a\"\"b\"", FtsExpression.quote("a\"b"))
    }

    @Test
    fun typoCorrectionUsesOnlyKnownWords() {
        val vocab = sequenceOf("barolo" to 12L, "bardolino" to 3L, "barbaresco" to 5L, "barossa" to 9L)
        assertEquals(listOf("barolo"), TypoCorrector.suggest("barollo", vocab))
        assertEquals(emptyList(), TypoCorrector.suggest("bar", vocab)) // too short to guess
        assertEquals(emptyList(), TypoCorrector.suggest("zinfandel", vocab))
    }

    @Test
    fun filterCounts() {
        assertEquals(0, SearchFilters().activeCount)
        assertEquals(true, SearchFilters().isEmpty)
        assertEquals(2, SearchFilters(vintageFrom = 2000, minRating100 = 90).activeCount)
    }
}
