package com.teraper.printmaster.core.model

/**
 * An amount of money in Armenian dram, stored in minor units (1 dram = 100 luma)
 * so invoice amounts with decimals add up exactly. Never use Double for money.
 */
@JvmInline
value class Money(val minor: Long) : Comparable<Money> {

    operator fun plus(other: Money) = Money(minor + other.minor)
    operator fun minus(other: Money) = Money(minor - other.minor)
    operator fun times(quantity: Int) = Money(minor * quantity)
    operator fun unaryMinus() = Money(-minor)

    override fun compareTo(other: Money) = minor.compareTo(other.minor)

    val isZero: Boolean get() = minor == 0L
    val isPositive: Boolean get() = minor > 0L
    val isNegative: Boolean get() = minor < 0L

    /** Whole dram, rounded toward zero. */
    val dram: Long get() = minor / MINOR_PER_DRAM

    companion object {
        const val MINOR_PER_DRAM = 100L
        val ZERO = Money(0)

        fun ofDram(dram: Long) = Money(dram * MINOR_PER_DRAM)
    }
}

fun Iterable<Money>.sum(): Money = fold(Money.ZERO) { acc, m -> acc + m }
