package com.houseofvino.core.money

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** An ISO 4217 currency code such as "NZD". */
@Serializable
@JvmInline
value class Currency(val code: String) {
    init {
        require(code.length == 3 && code.all { it in 'A'..'Z' }) { "Currency must be a three-letter ISO 4217 code, got \"$code\"" }
    }

    /** Number of minor-unit digits (2 for NZD, 0 for JPY, 3 for KWD). Unknown codes default to 2. */
    val minorDigits: Int get() = MINOR_DIGITS[code] ?: 2

    override fun toString(): String = code

    companion object {
        fun parseOrNull(text: String?): Currency? {
            if (text == null) return null
            val raw = text.trim()
            val t = raw.uppercase()
            return if (t.length == 3 && t.all { it in 'A'..'Z' }) Currency(t) else SYMBOLS[raw]
        }

        /** Symbols that identify a single currency unambiguously. "$" deliberately isn't here. */
        private val SYMBOLS = mapOf("€" to Currency("EUR"), "£" to Currency("GBP"), "¥" to Currency("JPY"), "₩" to Currency("KRW"), "CHF" to Currency("CHF"))

        private val MINOR_DIGITS: Map<String, Int> = buildMap {
            listOf("BIF", "CLP", "DJF", "GNF", "ISK", "JPY", "KMF", "KRW", "PYG", "RWF", "UGX", "UYI", "VND", "VUV", "XAF", "XOF", "XPF")
                .forEach { put(it, 0) }
            listOf("BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND").forEach { put(it, 3) }
            put("CLF", 4)
            put("UYW", 4)
        }

        /** Currencies offered first in pickers. Any valid ISO code is accepted. */
        val COMMON = listOf("NZD", "AUD", "USD", "EUR", "GBP", "CHF", "CAD", "JPY", "HKD", "SGD", "CNY", "ZAR", "SEK", "NOK", "DKK").map(::Currency)
    }
}

/**
 * An amount of money in its own currency, held as an exact count of minor units (cents). Amounts in
 * different currencies are never added together; use [MoneyTotals] or convert explicitly with an
 * [ExchangeRate] whose source and date are recorded.
 */
@Serializable
data class Money(val minor: Long, val currency: Currency) : Comparable<Money> {

    val isZero: Boolean get() = minor == 0L
    val isNegative: Boolean get() = minor < 0

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(ExactMath.addExact(minor, other.minor), currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(ExactMath.subtractExact(minor, other.minor), currency)
    }

    operator fun times(quantity: Int): Money = Money(ExactMath.multiplyExact(minor, quantity.toLong()), currency)

    operator fun times(quantity: Long): Money = Money(ExactMath.multiplyExact(minor, quantity), currency)

    operator fun unaryMinus(): Money = Money(ExactMath.negateExact(minor), currency)

    /** Divides into a per-unit amount, e.g. a case price into a bottle price. */
    fun dividedBy(count: Int, rounding: Rounding = Rounding.HALF_EVEN): Money {
        require(count > 0) { "count must be positive" }
        return Money(ExactMath.mulDiv(minor, 1, count.toLong(), rounding), currency)
    }

    /** Splits into [count] parts that add back up exactly (remainder cents go to the first parts). */
    fun allocate(count: Int): List<Money> {
        require(count > 0)
        val base = minor / count
        var remainder = minor % count
        return List(count) {
            val extra = when {
                remainder > 0 -> { remainder--; 1L }
                remainder < 0 -> { remainder++; -1L }
                else -> 0L
            }
            Money(base + extra, currency)
        }
    }

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minor.compareTo(other.minor)
    }

    /** "1234.50" — exact, no grouping or symbol. Use a platform formatter for display. */
    fun toPlainString(): String = Decimal.plain(minor, currency.minorDigits)

    fun toDecimal(): Decimal = Decimal.of(minor, currency.minorDigits)

    override fun toString(): String = "${toPlainString()} ${currency.code}"

    private fun requireSameCurrency(other: Money) {
        if (other.currency != currency) throw CurrencyMismatchException(currency, other.currency)
    }

    companion object {
        fun zero(currency: Currency) = Money(0, currency)

        /**
         * Exact conversion from a decimal string. Rejects more fractional digits than the currency
         * has rather than silently rounding a user's entry.
         */
        fun of(amount: Decimal, currency: Currency): Money {
            val digits = currency.minorDigits
            val stripped = amount.stripTrailingZeros()
            if (stripped.scale > digits) {
                throw IllegalArgumentException("${currency.code} amounts have at most $digits decimal places")
            }
            return Money(amount.setScale(digits).unscaled, currency)
        }

        fun parse(text: String, currency: Currency, style: DecimalParser.Style = DecimalParser.DOT): Money? {
            val plain = DecimalParser.normalise(text, style) ?: return null
            val d = Decimal.parseOrNull(plain) ?: return null
            return try {
                of(d, currency)
            } catch (e: IllegalArgumentException) {
                null
            } catch (e: ArithmeticException) {
                null
            }
        }
    }
}

class CurrencyMismatchException(val a: Currency, val b: Currency) : IllegalArgumentException("Cannot combine $a with $b without an exchange rate")

/**
 * A rate saying one unit of [from] is worth [rate] units of [to], as of [asOf], according to
 * [source] (e.g. "user", "ECB reference rate"). Every converted figure in the app can point back
 * to the rate that produced it.
 */
@Serializable
data class ExchangeRate(
    val from: Currency,
    val to: Currency,
    val rate: Decimal,
    val asOf: LocalDate,
    val source: String,
) {
    init {
        require(!rate.isNegative && !rate.isZero) { "Exchange rate must be positive" }
    }

    fun convert(money: Money, rounding: Rounding = Rounding.HALF_EVEN): Money {
        require(money.currency == from) { "Rate is for ${from.code}, not ${money.currency.code}" }
        if (from == to) return money
        // target_minor = minor * rate * 10^(toDigits - fromDigits)
        val shift = to.minorDigits - from.minorDigits
        var numerator = rate.unscaled
        var denominator = ExactMath.pow10(rate.scale)
        if (shift > 0) numerator = ExactMath.multiplyExact(numerator, ExactMath.pow10(shift))
        if (shift < 0) denominator = ExactMath.multiplyExact(denominator, ExactMath.pow10(-shift))
        return Money(ExactMath.mulDiv(money.minor, numerator, denominator, rounding), to)
    }

    fun inverse(scale: Int = 10): ExchangeRate {
        val inv = ExactMath.mulDiv(ExactMath.pow10(scale), ExactMath.pow10(rate.scale), rate.unscaled, Rounding.HALF_EVEN)
        return ExchangeRate(to, from, Decimal.of(inv, scale).stripTrailingZeros(), asOf, source)
    }
}

/** Running totals kept separately per currency. Nothing is converted implicitly. */
class MoneyTotals {
    private val totals = linkedMapOf<Currency, Long>()

    fun add(money: Money?) {
        if (money == null) return
        totals[money.currency] = ExactMath.addExact(totals[money.currency] ?: 0L, money.minor)
    }

    fun addAll(values: Iterable<Money?>) = values.forEach(::add)

    val currencies: Set<Currency> get() = totals.keys

    fun get(currency: Currency): Money = Money(totals[currency] ?: 0L, currency)

    fun toList(): List<Money> = totals.map { (c, m) -> Money(m, c) }

    val isEmpty: Boolean get() = totals.isEmpty()

    /**
     * Converts everything into [target] using [rates]. Currencies with no rate are returned in
     * [Converted.unconverted] instead of being guessed.
     */
    fun convertTo(target: Currency, rates: List<ExchangeRate>): Converted {
        var sum = Money.zero(target)
        val missing = mutableListOf<Money>()
        val used = mutableListOf<ExchangeRate>()
        for (m in toList()) {
            if (m.currency == target) {
                sum += m
                continue
            }
            val direct = rates.filter { it.from == m.currency && it.to == target }.maxByOrNull { it.asOf }
            val rate = direct ?: rates.filter { it.from == target && it.to == m.currency }.maxByOrNull { it.asOf }?.inverse()
            if (rate == null) {
                missing += m
            } else {
                sum += rate.convert(m)
                used += rate
            }
        }
        return Converted(sum, missing, used)
    }

    data class Converted(val total: Money, val unconverted: List<Money>, val ratesUsed: List<ExchangeRate>) {
        val isComplete: Boolean get() = unconverted.isEmpty()
    }
}
