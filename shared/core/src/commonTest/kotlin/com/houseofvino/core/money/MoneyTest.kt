package com.houseofvino.core.money

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoneyTest {
    private val nzd = Currency("NZD")
    private val jpy = Currency("JPY")
    private val eur = Currency("EUR")
    private val kwd = Currency("KWD")

    @Test
    fun parsesExactlyWithoutFloatingPoint() {
        assertEquals(Money(1234_50, nzd), Money.parse("1,234.50", nzd))
        assertEquals(Money(1234_50, nzd), Money.parse("$1,234.50", nzd))
        assertEquals(Money(1234_50, eur), Money.parse("1.234,50 €", eur))
        assertEquals(Money(1234_50, eur), Money.parse("1 234,50", eur))
        assertEquals(Money(10, nzd), Money.parse("0.10", nzd))
        assertEquals(Money(-12_00, nzd), Money.parse("(12.00)", nzd))
        assertEquals(Money(5000, jpy), Money.parse("5,000", jpy))
        assertEquals(Money(1500, kwd), Money.parse("1.500", kwd, DecimalParser.DOT))
        // 0.1 + 0.2 is exactly 0.3 here.
        assertEquals(Money.parse("0.30", nzd), Money.parse("0.10", nzd)!! + Money.parse("0.20", nzd)!!)
    }

    @Test
    fun rejectsAmbiguousOrOverPreciseInput() {
        assertNull(Money.parse("12.345", nzd)) // NZD has two decimals; don't silently round
        assertNull(Money.parse("1.2.3", nzd))
        assertNull(Money.parse("abc", nzd))
        assertNull(Money.parse("", nzd))
        assertNull(Money.parse("1,23,4", nzd))
        assertNull(Money.parse("5.5", jpy))
    }

    @Test
    fun neverMixesCurrencies() {
        assertFailsWith<CurrencyMismatchException> { Money(1, nzd) + Money(1, eur) }
    }

    @Test
    fun allocationAddsUp() {
        val parts = Money(100_00, nzd).allocate(3)
        assertEquals(listOf(33_34L, 33_33L, 33_33L), parts.map { it.minor })
        assertEquals(Money(100_00, nzd), parts.reduce(Money::plus))
        assertEquals(Money(-33_34, nzd), Money(-100_00, nzd).allocate(3).first())
    }

    @Test
    fun perBottleFromCasePrice() {
        assertEquals(Money(8_33, nzd), Money(100_00, nzd).dividedBy(12))
        assertEquals(Money(8_34, nzd), Money(100_00, nzd).dividedBy(12, Rounding.UP))
    }

    @Test
    fun convertsBetweenCurrenciesWithDifferentMinorUnits() {
        val day = LocalDate(2026, 10, 1)
        val eurToNzd = ExchangeRate(eur, nzd, Decimal.parse("1.8765"), day, "test")
        assertEquals(Money(187_65, nzd), eurToNzd.convert(Money(100_00, eur)))
        val nzdToJpy = ExchangeRate(nzd, jpy, Decimal.parse("88.123"), day, "test")
        assertEquals(Money(8812, jpy), nzdToJpy.convert(Money(100_00, nzd)))
        val jpyToNzd = ExchangeRate(jpy, nzd, Decimal.parse("0.011348"), day, "test")
        assertEquals(Money(113_48, nzd), jpyToNzd.convert(Money(10_000, jpy)))
    }

    @Test
    fun totalsReportUnconvertibleCurrencies() {
        val totals = MoneyTotals()
        totals.addAll(listOf(Money(10_00, nzd), Money(5_00, eur), Money(2_000, jpy), Money(1_00, nzd)))
        val rates = listOf(ExchangeRate(eur, nzd, Decimal.parse("2"), LocalDate(2026, 1, 1), "test"))
        val converted = totals.convertTo(nzd, rates)
        assertEquals(Money(21_00, nzd), converted.total)
        assertEquals(listOf(Money(2_000, jpy)), converted.unconverted)
        assertTrue(!converted.isComplete)
    }

    @Test
    fun inverseRatesRoundTrip() {
        val r = ExchangeRate(eur, nzd, Decimal.parse("2"), LocalDate(2026, 1, 1), "t")
        assertEquals(Decimal.parse("0.5"), r.inverse().rate)
    }

    @Test
    fun decimalBasics() {
        assertEquals("12.050", Decimal.parse("12.050").toString())
        assertEquals("-0.5", Decimal.parse("-.5").toString())
        assertEquals(Decimal.parse("1.25"), Decimal.parse("1.2500"))
        assertEquals("1.2", Decimal.parse("1.25").setScale(1).toString()) // half-even
        assertEquals("1.4", Decimal.parse("1.35").setScale(1).toString())
        assertEquals("1.3", Decimal.parse("1.25").setScale(1, Rounding.HALF_UP).toString())
        assertNull(Decimal.parseOrNull("1e5"))
        assertNull(Decimal.parseOrNull("."))
        assertEquals("0.75", (Decimal.parse("0.5") + Decimal.parse("0.25")).toString())
    }
}
