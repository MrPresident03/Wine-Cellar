package com.houseofvino.core.money

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * An exact base-10 number: `unscaled * 10^-scale`. Used for exchange rates, alcohol by volume and
 * other non-money quantities that must not pass through floating point. It is stored and
 * serialised as its plain string form (e.g. "0.61234"), never as a Double.
 */
@Serializable(with = DecimalSerializer::class)
class Decimal private constructor(val unscaled: Long, val scale: Int) : Comparable<Decimal> {

    init {
        require(scale in 0..MAX_SCALE) { "scale must be between 0 and $MAX_SCALE" }
    }

    val isZero: Boolean get() = unscaled == 0L
    val isNegative: Boolean get() = unscaled < 0

    /** The same value with trailing fractional zeros removed ("1.2500" -> "1.25"). */
    fun stripTrailingZeros(): Decimal {
        var u = unscaled
        var s = scale
        while (s > 0 && u % 10 == 0L) {
            u /= 10
            s--
        }
        return Decimal(u, s)
    }

    /** Returns this value with exactly [newScale] fractional digits, rounding when shortening. */
    fun setScale(newScale: Int, rounding: Rounding = Rounding.HALF_EVEN): Decimal {
        require(newScale in 0..MAX_SCALE)
        return when {
            newScale == scale -> this
            newScale > scale -> Decimal(ExactMath.multiplyExact(unscaled, ExactMath.pow10(newScale - scale)), newScale)
            else -> Decimal(ExactMath.mulDiv(unscaled, 1, ExactMath.pow10(scale - newScale), rounding), newScale)
        }
    }

    operator fun plus(other: Decimal): Decimal {
        val s = maxOf(scale, other.scale)
        return Decimal(ExactMath.addExact(setScale(s).unscaled, other.setScale(s).unscaled), s)
    }

    operator fun minus(other: Decimal): Decimal {
        val s = maxOf(scale, other.scale)
        return Decimal(ExactMath.subtractExact(setScale(s).unscaled, other.setScale(s).unscaled), s)
    }

    override fun compareTo(other: Decimal): Int {
        val s = maxOf(scale, other.scale)
        return setScale(s).unscaled.compareTo(other.setScale(s).unscaled)
    }

    override fun equals(other: Any?): Boolean = other is Decimal && compareTo(other) == 0

    override fun hashCode(): Int {
        val n = stripTrailingZeros()
        return 31 * n.unscaled.hashCode() + n.scale
    }

    /** Plain string with no exponent and no grouping, e.g. "-12.050". */
    override fun toString(): String = plain(unscaled, scale)

    /** For display-only ratios. Never use the result for further money arithmetic. */
    fun toDouble(): Double = toString().toDouble()

    companion object {
        const val MAX_SCALE = 18
        val ZERO = Decimal(0, 0)
        val ONE = Decimal(1, 0)

        fun of(unscaled: Long, scale: Int = 0): Decimal = Decimal(unscaled, scale)

        /**
         * Parses a plain decimal string ("12", "-0.5", "1.250", "+3."). Rejects exponents, grouping
         * separators and anything else ambiguous: callers that accept user input with locale
         * conventions should normalise it first with [DecimalParser].
         */
        fun parse(text: String): Decimal = parseOrNull(text) ?: throw NumberFormatException("Not a decimal number: \"$text\"")

        fun parseOrNull(text: String): Decimal? {
            val s = text.trim()
            if (s.isEmpty()) return null
            var i = 0
            var negative = false
            if (s[0] == '-' || s[0] == '+') {
                negative = s[0] == '-'
                i = 1
            }
            var unscaled = 0L
            var scale = 0
            var digits = 0
            var seenPoint = false
            while (i < s.length) {
                val c = s[i]
                when {
                    c == '.' -> {
                        if (seenPoint) return null
                        seenPoint = true
                    }
                    c in '0'..'9' -> {
                        digits++
                        if (seenPoint) {
                            scale++
                            if (scale > MAX_SCALE) return null
                        }
                        unscaled = try {
                            ExactMath.addExact(ExactMath.multiplyExact(unscaled, 10), (c - '0').toLong())
                        } catch (e: ArithmeticException) {
                            return null
                        }
                    }
                    else -> return null
                }
                i++
            }
            if (digits == 0) return null
            return Decimal(if (negative) -unscaled else unscaled, scale)
        }

        internal fun plain(unscaled: Long, scale: Int): String {
            if (scale == 0) return unscaled.toString()
            val negative = unscaled < 0
            // Long.MIN_VALUE has no positive Long counterpart, so work on the digit string.
            val digits = unscaled.toString().removePrefix("-").padStart(scale + 1, '0')
            val intPart = digits.substring(0, digits.length - scale)
            val fracPart = digits.substring(digits.length - scale)
            return (if (negative) "-" else "") + intPart + "." + fracPart
        }
    }
}

object DecimalSerializer : KSerializer<Decimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("com.houseofvino.Decimal", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Decimal) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Decimal = Decimal.parse(decoder.decodeString())
}

/**
 * Turns human-entered numbers into plain decimal strings. Handles "1,234.50", "1.234,50",
 * "1 234,50", "$1,234", "(12.00)" style negatives and stray currency symbols. Returns null when the
 * input is ambiguous rather than guessing.
 */
object DecimalParser {

    data class Style(val decimalSeparator: Char, val groupingSeparator: Char?)

    val DOT = Style('.', ',')
    val COMMA = Style(',', '.')

    /**
     * @param preferred the user's locale convention; used only when the text itself is ambiguous
     * (e.g. "1,234" could be one thousand two hundred and thirty-four or one point two three four).
     */
    fun normalise(input: String, preferred: Style = DOT): String? {
        var s = input.trim()
        if (s.isEmpty()) return null
        var negative = false
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true
            s = s.substring(1, s.length - 1).trim()
        }
        // Drop currency symbols/codes and spaces (including non-breaking and narrow spaces).
        s = s.filter { it.isDigit() || it == '.' || it == ',' || it == '-' || it == '+' || it == '\'' }
        s = s.replace("'", "") // Swiss grouping 1'234.50
        if (s.startsWith("-")) {
            negative = !negative
            s = s.substring(1)
        } else if (s.startsWith("+")) {
            s = s.substring(1)
        }
        if (s.isEmpty() || s.any { it == '-' || it == '+' }) return null

        val dots = s.count { it == '.' }
        val commas = s.count { it == ',' }
        val decimalSep: Char? = when {
            dots == 0 && commas == 0 -> null
            dots > 0 && commas > 0 -> if (s.lastIndexOf('.') > s.lastIndexOf(',')) '.' else ','
            dots > 1 -> null // "1.234.567" => grouping only
            commas > 1 -> null
            else -> {
                val sep = if (dots == 1) '.' else ','
                val after = s.length - s.indexOf(sep) - 1
                // Exactly three digits after a lone separator is ambiguous; fall back to the locale.
                val lead = s.substring(0, s.indexOf(sep))
                if (after == 3 && lead.length in 1..3 && lead != "0") {
                    if (preferred.decimalSeparator == sep) sep else null
                } else {
                    sep
                }
            }
        }
        val grouping = when (decimalSep) {
            '.' -> ','
            ',' -> '.'
            else -> if (dots > 0) '.' else ','
        }
        if (decimalSep != null && s.count { it == decimalSep } > 1) return null
        // Validate grouping: groups after the first must be exactly 3 digits.
        val intPart = if (decimalSep != null) s.substringBefore(decimalSep) else s
        val fracPart = if (decimalSep != null) s.substringAfter(decimalSep) else ""
        if (fracPart.any { !it.isDigit() }) return null
        if (intPart.contains(grouping)) {
            val groups = intPart.split(grouping)
            if (groups.first().isEmpty() || groups.first().length > 3) return null
            if (groups.drop(1).any { it.length != 3 }) return null
        }
        val digits = intPart.replace(grouping.toString(), "")
        if (digits.isEmpty() && fracPart.isEmpty()) return null
        if (digits.any { !it.isDigit() }) return null
        val plain = (if (negative) "-" else "") + (digits.ifEmpty { "0" }) + (if (fracPart.isNotEmpty()) ".$fracPart" else "")
        return Decimal.parseOrNull(plain)?.let { plain }
    }
}
