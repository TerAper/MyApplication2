package com.teraper.printmaster.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.teraper.printmaster.core.designsystem.theme.PmTheme

enum class TagTone { Neutral, Info, Debt, Paid, Warning }

/** Small colored label: order status, INVOICE / BANK / CASH, etc. */
@Composable
fun PmTag(text: String, tone: TagTone, modifier: Modifier = Modifier) {
    val c = PmTheme.colors
    val (container, content) = when (tone) {
        TagTone.Neutral -> c.surfaceMuted to c.inkSecondary
        TagTone.Info -> c.primaryContainer to c.primary
        TagTone.Debt -> c.debtContainer to c.onDebtContainer
        TagTone.Paid -> c.paidContainer to c.paid
        TagTone.Warning -> c.warningContainer to c.warning
    }
    Text(
        text = text,
        modifier = modifier
            .background(container, PmTheme.shapes.tag)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        color = content,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
    )
}
