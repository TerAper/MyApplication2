package com.teraper.printmaster.core.designsystem.component

import com.teraper.printmaster.core.model.Money
import kotlin.math.abs

private const val NBSP = ' '
const val DRAM_SIGN = "֏"

/**
 * Formats money the way it's written in Armenia: "1 240 000 ֏", with ",50" only when
 * there are luma. Non-breaking spaces keep the amount on one line.
 */
fun Money.format(withSign: Boolean = false, withCurrency: Boolean = true): String {
    val absMinor = abs(minor)
    val whole = absMinor / Money.MINOR_PER_DRAM
    val luma = absMinor % Money.MINOR_PER_DRAM

    val grouped = whole.toString().reversed().chunked(3).joinToString(NBSP.toString()).reversed()
    val decimals = if (luma != 0L) "," + luma.toString().padStart(2, '0') else ""
    val sign = when {
        minor < 0 -> "−"
        withSign && minor > 0 -> "+"
        else -> ""
    }
    val currency = if (withCurrency) "$NBSP$DRAM_SIGN" else ""
    return sign + grouped + decimals + currency
}
