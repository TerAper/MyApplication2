package com.teraper.printmaster.core.designsystem.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Money

/** How an amount should read: neutral, something owed, or money received. */
enum class AmountTone { Neutral, Debt, Paid, Overpaid }

/**
 * A money amount with tabular digits, so columns of amounts line up.
 */
@Composable
fun AmountText(
    amount: Money,
    modifier: Modifier = Modifier,
    tone: AmountTone = AmountTone.Neutral,
    style: TextStyle = LocalTextStyle.current,
    withSign: Boolean = false,
    withCurrency: Boolean = true,
) {
    val colors = PmTheme.colors
    val color = when (tone) {
        AmountTone.Neutral -> colors.ink
        AmountTone.Debt -> colors.debt
        AmountTone.Paid -> colors.paid
        AmountTone.Overpaid -> colors.primary
    }
    Text(
        text = amount.format(withSign = withSign, withCurrency = withCurrency),
        modifier = modifier,
        color = color,
        style = style.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.ExtraBold),
        textAlign = TextAlign.End,
        maxLines = 1,
    )
}
