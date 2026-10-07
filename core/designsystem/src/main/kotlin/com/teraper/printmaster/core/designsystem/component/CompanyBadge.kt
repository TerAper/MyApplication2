package com.teraper.printmaster.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Company colors; a company stores its index into this list. */
object CompanyColors {
    val palette: List<Color> = listOf(
        Color(0xFF1D4E89), // blue
        Color(0xFF1F6B45), // green
        Color(0xFFB4400F), // orange
        Color(0xFF6A3FA0), // purple
        Color(0xFF0F7C80), // teal
        Color(0xFFB3261E), // red
        Color(0xFFA86B00), // amber
        Color(0xFF455A64), // slate
    )

    fun of(index: Int): Color = palette[Math.floorMod(index, palette.size)]
}

/** The company "logo": its initials in a circle of its color. */
@Composable
fun CompanyBadge(initials: String, colorIndex: Int, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(
        modifier.size(size).background(CompanyColors.of(colorIndex), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            color = Color.White,
            style = TextStyle(fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}
