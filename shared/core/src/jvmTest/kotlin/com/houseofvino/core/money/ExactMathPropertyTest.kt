package com.houseofvino.core.money

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/** Checks the 128-bit mulDiv against java.math on a large number of random and edge-case inputs. */
class ExactMathPropertyTest {

    private val modes = mapOf(
        Rounding.HALF_EVEN to RoundingMode.HALF_EVEN,
        Rounding.HALF_UP to RoundingMode.HALF_UP,
        Rounding.DOWN to RoundingMode.DOWN,
        Rounding.UP to RoundingMode.UP,
        Rounding.FLOOR to RoundingMode.FLOOR,
        Rounding.CEILING to RoundingMode.CEILING,
    )

    private val edges = listOf(
        0L, 1L, -1L, 2L, -2L, 3L, 10L, 99L, 100L, 1_000_000L, Int.MAX_VALUE.toLong(), Int.MIN_VALUE.toLong(),
        Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE - 1, Long.MIN_VALUE + 1, 1L shl 32, (1L shl 32) - 1, 1L shl 62,
    )

    private fun check(a: Long, b: Long, d: Long, mode: Rounding) {
        if (d == 0L) return
        val exact = BigDecimal(BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)))
            .divide(BigDecimal(BigInteger.valueOf(d)), 0, modes.getValue(mode)).toBigIntegerExact()
        val fits = exact.bitLength() <= 63
        try {
            val got = ExactMath.mulDiv(a, b, d, mode)
            if (!fits) fail("expected overflow for $a*$b/$d $mode, got $got")
            assertEquals(exact.toLong(), got, "$a*$b/$d $mode")
        } catch (e: ArithmeticException) {
            if (fits) fail("unexpected overflow for $a*$b/$d $mode (exact $exact)")
        }
    }

    @Test
    fun edgeCases() {
        for (a in edges) for (b in edges) for (d in edges) for (m in modes.keys) check(a, b, d, m)
    }

    @Test
    fun randomValues() {
        val r = Random(20261017)
        repeat(200_000) {
            val a = when (r.nextInt(3)) { 0 -> r.nextLong(); 1 -> r.nextLong(-1_000_000_000_000, 1_000_000_000_000); else -> r.nextInt().toLong() }
            val b = when (r.nextInt(3)) { 0 -> r.nextLong(); 1 -> r.nextLong(-10_000_000_000, 10_000_000_000); else -> r.nextInt(-1000, 1000).toLong() }
            val d = when (r.nextInt(3)) { 0 -> r.nextLong(); 1 -> r.nextLong(1, 1_000_000_000_000); else -> r.nextInt(1, 1000).toLong() }
            check(a, b, d, modes.keys.random(r))
        }
    }

    @Test
    fun addAndSubtractOverflow() {
        val r = Random(7)
        repeat(100_000) {
            val a = r.nextLong()
            val b = r.nextLong()
            val sum = BigInteger.valueOf(a).add(BigInteger.valueOf(b))
            val diff = BigInteger.valueOf(a).subtract(BigInteger.valueOf(b))
            runCatching { ExactMath.addExact(a, b) }.let {
                if (sum.bitLength() <= 63) assertEquals(sum.toLong(), it.getOrThrow()) else check(it.isFailure)
            }
            runCatching { ExactMath.subtractExact(a, b) }.let {
                if (diff.bitLength() <= 63) assertEquals(diff.toLong(), it.getOrThrow()) else check(it.isFailure)
            }
        }
    }
}
