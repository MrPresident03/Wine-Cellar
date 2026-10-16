package com.houseofvino.core.model

import com.houseofvino.core.money.Decimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelTest {
    @Test
    fun vintageParsingKeepsUnknownAndNonVintageApart() {
        assertEquals(Vintage.Year(2016), Vintage.parse("2016"))
        assertEquals(Vintage.NonVintage, Vintage.parse("N.V."))
        assertEquals(Vintage.NonVintage, Vintage.parse("non-vintage"))
        assertEquals(Vintage.Unknown, Vintage.parse(""))
        assertEquals(Vintage.Unknown, Vintage.parse(null))
        assertNull(Vintage.parse("20l6"))
        assertNull(Vintage.parse("1500"))
        assertEquals(listOf(Vintage.Year(1990), Vintage.Year(2010), Vintage.NonVintage, Vintage.Unknown), listOf(Vintage.Unknown, Vintage.NonVintage, Vintage.Year(2010), Vintage.Year(1990)).sorted())
        assertEquals(Vintage.Year(2001), Vintage.fromStorage("year", 2001))
    }

    @Test
    fun idsAreVersion7AndOrdered() {
        val a = Ids.new(1_700_000_000_000)
        val b = Ids.new(1_700_000_000_001)
        assertTrue(Ids.isValid(a))
        assertEquals('7', a[14])
        assertTrue(a < b)
        assertTrue((1..1000).map { Ids.new() }.toSet().size == 1000)
    }

    @Test
    fun ledgerValidation() {
        Ledger.validate(MovementDraft(MovementKind.PURCHASE, "lot", 6, null, "cellar"))
        assertFailsWith<MovementError.NonPositiveQuantity> { Ledger.validate(MovementDraft(MovementKind.CONSUME, "lot", 0, "a", null)) }
        assertFailsWith<MovementError.MissingSource> { Ledger.validate(MovementDraft(MovementKind.CONSUME, "lot", 1, null, null)) }
        assertFailsWith<MovementError.UnexpectedDestination> { Ledger.validate(MovementDraft(MovementKind.CONSUME, "lot", 1, "a", "b")) }
        assertFailsWith<MovementError.UnexpectedSource> { Ledger.validate(MovementDraft(MovementKind.PURCHASE, "lot", 1, "a", "b")) }
        assertFailsWith<MovementError.SameLocation> { Ledger.validate(MovementDraft(MovementKind.TRANSFER, "lot", 1, "a", "a")) }
        assertFailsWith<MovementError.MissingReversalTarget> { Ledger.validate(MovementDraft(MovementKind.REVERSAL, "lot", 1, "a", null)) }
        val reversal = Ledger.reversalOf("m1", MovementDraft(MovementKind.TRANSFER, "lot", 2, "a", "b"))
        assertEquals(MovementDraft(MovementKind.REVERSAL, "lot", 2, "b", "a", "m1"), reversal)
        Ledger.validate(reversal)
        assertEquals(-2, Ledger.deltaAt("b", reversal))
        assertFailsWith<MovementError.CannotReverseReversal> { Ledger.reversalOf("m2", reversal) }
    }

    @Test
    fun drinkingWindows() {
        val w = DrinkingWindow(2025, 2035, WindowSource.CRITIC)
        assertEquals(DrinkStatus.TOO_YOUNG, w.status(2024))
        assertEquals(DrinkStatus.READY, w.status(2025))
        assertEquals(DrinkStatus.DRINK_SOON, w.status(2034))
        assertEquals(DrinkStatus.PAST_PEAK, w.status(2036))
        assertEquals(DrinkStatus.UNKNOWN, DrinkingWindow.statusOf(null, null, 2026))
        assertEquals(DrinkStatus.UNKNOWN, DrinkingWindow.statusOf(2030, 2020, 2026))
        assertEquals("2025–2035", w.label())
        assertFailsWith<IllegalArgumentException> { DrinkingWindow(2030, 2020, WindowSource.USER) }
    }

    @Test
    fun bottleFormats() {
        assertEquals("Magnum", BottleFormat.name(1500))
        assertEquals("1.5 L", BottleFormat.label(1500))
        assertEquals("750 ml", BottleFormat.label(750))
        assertEquals(20, BottleFormat.standardEquivalentTenths(1500))
        assertEquals(5, BottleFormat.standardEquivalentTenths(375))
    }

    @Test
    fun grapes() {
        val parsed = Grapes.parse("Cabernet Sauvignon 60%, Merlot 35 %; Petit Verdot")
        assertEquals(listOf("Cabernet Sauvignon", "Merlot", "Petit Verdot"), parsed.map { it.name })
        assertEquals(Decimal.parse("60"), parsed[0].percent)
        assertEquals(parsed, Grapes.decode(Grapes.encode(parsed)))
        assertEquals(emptyList(), Grapes.decode("not json"))
        assertEquals("Cabernet Sauvignon 60%, Merlot 35%, Petit Verdot", Grapes.display(parsed))
    }

    @Test
    fun styleGuessing() {
        assertEquals(WineStyle.SPARKLING, WineStyle.guess("Méthode Traditionnelle"))
        assertEquals(WineStyle.ROSE, WineStyle.guess("Rosé of Pinot Noir"))
        assertEquals(WineStyle.RED, WineStyle.guess("Nebbiolo"))
        assertEquals(WineStyle.WHITE, WineStyle.guess("Grüner Veltliner"))
        assertNull(WineStyle.guess("Something unusual"))
    }
}
