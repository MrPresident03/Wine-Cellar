package com.houseofvino.core.money

/** How a result that falls between two representable values is resolved. */
enum class Rounding {
    /** Banker's rounding: halves go to the even neighbour. The default for money. */
    HALF_EVEN,

    /** Halves round away from zero. */
    HALF_UP,

    /** Truncate toward zero. */
    DOWN,

    /** Away from zero whenever there is a remainder. */
    UP,

    /** Toward negative infinity. */
    FLOOR,

    /** Toward positive infinity. */
    CEILING,
}

/**
 * Exact integer helpers for money. Everything here either returns the exact answer or throws
 * [ArithmeticException]; nothing silently overflows or goes through floating point.
 */
object ExactMath {

    fun addExact(a: Long, b: Long): Long {
        val r = a + b
        // Overflow iff both operands have the same sign and the result's sign differs.
        if (((a xor r) and (b xor r)) < 0) throw ArithmeticException("long overflow: $a + $b")
        return r
    }

    fun subtractExact(a: Long, b: Long): Long {
        val r = a - b
        if (((a xor b) and (a xor r)) < 0) throw ArithmeticException("long overflow: $a - $b")
        return r
    }

    fun multiplyExact(a: Long, b: Long): Long = mulDiv(a, b, 1L, Rounding.DOWN)

    fun negateExact(a: Long): Long {
        if (a == Long.MIN_VALUE) throw ArithmeticException("long overflow: -$a")
        return -a
    }

    /** 10^[exp] as a Long, for 0 <= exp <= 18. */
    fun pow10(exp: Int): Long {
        require(exp in 0..18) { "10^$exp does not fit in a Long" }
        var r = 1L
        repeat(exp) { r *= 10 }
        return r
    }

    /**
     * Computes `a * b / d`, rounded with [rounding], using a 128-bit intermediate so the product
     * never overflows. Throws if `d == 0` or if the final result does not fit in a Long.
     */
    fun mulDiv(a: Long, b: Long, d: Long, rounding: Rounding = Rounding.HALF_EVEN): Long {
        if (d == 0L) throw ArithmeticException("division by zero")
        val negative = (a < 0) xor (b < 0) xor (d < 0)
        val ua = magnitude(a)
        val ub = magnitude(b)
        val ud = magnitude(d)
        if (ua == 0uL || ub == 0uL) return 0L

        // 64 x 64 -> 128 bit product (hi, lo).
        val a0 = ua and MASK32
        val a1 = ua shr 32
        val b0 = ub and MASK32
        val b1 = ub shr 32
        val p00 = a0 * b0
        val p01 = a0 * b1
        val p10 = a1 * b0
        val p11 = a1 * b1
        val mid = (p00 shr 32) + (p01 and MASK32) + (p10 and MASK32)
        val lo = (p00 and MASK32) or (mid shl 32)
        val hi = p11 + (p01 shr 32) + (p10 shr 32) + (mid shr 32)

        // Quotient must fit in 64 bits, which requires hi < d.
        if (hi >= ud) throw ArithmeticException("overflow: $a * $b / $d")

        // Restoring long division of (hi, lo) by ud. rem < ud <= 2^63, so (rem shl 1) fits in 64 bits.
        var rem = 0uL
        var q = 0uL
        for (i in 127 downTo 0) {
            val bit = if (i >= 64) (hi shr (i - 64)) and 1uL else (lo shr i) and 1uL
            rem = (rem shl 1) or bit
            if (i < 64) q = q shl 1
            if (rem >= ud) {
                rem -= ud
                if (i < 64) q = q or 1uL
            }
        }

        val roundUp: Boolean = if (rem == 0uL) {
            false
        } else {
            val twice = rem shl 1 // rem < 2^63 so this cannot overflow
            when (rounding) {
                Rounding.DOWN -> false
                Rounding.UP -> true
                Rounding.FLOOR -> negative
                Rounding.CEILING -> !negative
                Rounding.HALF_UP -> twice >= ud
                Rounding.HALF_EVEN -> twice > ud || (twice == ud && (q and 1uL) == 1uL)
            }
        }
        if (roundUp) {
            if (q == ULong.MAX_VALUE) throw ArithmeticException("overflow: $a * $b / $d")
            q += 1uL
        }

        return if (negative) {
            if (q > LONG_MIN_MAGNITUDE) throw ArithmeticException("overflow: $a * $b / $d")
            (0uL - q).toLong()
        } else {
            if (q > Long.MAX_VALUE.toULong()) throw ArithmeticException("overflow: $a * $b / $d")
            q.toLong()
        }
    }

    private fun magnitude(x: Long): ULong = if (x < 0) 0uL - x.toULong() else x.toULong()

    private const val MASK32: ULong = 0xFFFF_FFFFuL
    private val LONG_MIN_MAGNITUDE: ULong = 1uL shl 63
}
