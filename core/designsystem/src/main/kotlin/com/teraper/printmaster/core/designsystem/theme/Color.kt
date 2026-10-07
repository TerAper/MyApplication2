package com.teraper.printmaster.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Every color the app uses. Screens read these through [PmTheme.colors] and never
 * hard-code a Color, so a redesign only changes this file.
 */
@Immutable
data class PmColors(
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val outline: Color,
    val outlineStrong: Color,

    val ink: Color,
    val inkSecondary: Color,
    val inkMuted: Color,

    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,

    /** Client owes money. */
    val debt: Color,
    val onDebtContainer: Color,
    val debtContainer: Color,

    /** Paid / cash received. */
    val paid: Color,
    val paidContainer: Color,

    /** Needs attention, e.g. unmatched bank payments. */
    val warning: Color,
    val warningContainer: Color,

    val error: Color,
)

val LightPmColors = PmColors(
    background = Color(0xFFF4F5F2),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFEEF0EC),
    outline = Color(0xFFE2E4DF),
    outlineStrong = Color(0xFFD5D8D2),

    ink = Color(0xFF1B1F23),
    inkSecondary = Color(0xFF3A4249),
    inkMuted = Color(0xFF5B636B),

    primary = Color(0xFF1D4E89),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3ECF7),

    debt = Color(0xFFB4400F),
    onDebtContainer = Color(0xFF7A2E0B),
    debtContainer = Color(0xFFFBEBDD),

    paid = Color(0xFF1F6B45),
    paidContainer = Color(0xFFE1F0E7),

    warning = Color(0xFFA86B00),
    warningContainer = Color(0xFFFFF8E6),

    error = Color(0xFFB3261E),
)
