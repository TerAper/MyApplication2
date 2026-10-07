package com.teraper.printmaster.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class PmSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    /** Side padding of every screen. */
    val screen: Dp = 16.dp,
    /** Minimum height of anything tappable. */
    val touchTarget: Dp = 48.dp,
)

@Immutable
data class PmShapes(
    val card: RoundedCornerShape = RoundedCornerShape(14.dp),
    val button: RoundedCornerShape = RoundedCornerShape(12.dp),
    val tag: RoundedCornerShape = RoundedCornerShape(6.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(percent = 50),
)
