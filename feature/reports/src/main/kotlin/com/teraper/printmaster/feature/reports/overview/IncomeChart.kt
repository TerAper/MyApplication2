package com.teraper.printmaster.feature.reports.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.MonthIncome
import com.teraper.printmaster.feature.reports.R
import java.time.YearMonth

private val CHART_HEIGHT = 120.dp

/** Money received per month: cash below, bank on top. Tapping a month opens it. */
@Composable
internal fun IncomeChart(
    history: List<MonthIncome>,
    selected: YearMonth,
    onMonthClick: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val max = history.maxOfOrNull { it.income.total.minor }?.takeIf { it > 0 } ?: 1L
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().height(CHART_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            history.forEach { month ->
                val isSelected = month.month == selected
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { onMonthClick(month.month) },
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val bank = month.income.bank.minor.toFloat() / max
                    val cash = month.income.cash.minor.toFloat() / max
                    val alpha = if (isSelected) 1f else 0.45f
                    if (bank > 0f) Bar(bank, PmTheme.colors.primary.copy(alpha = alpha), top = true, bottom = cash == 0f)
                    if (cash > 0f) Bar(cash, PmTheme.colors.paid.copy(alpha = alpha), top = bank == 0f, bottom = true)
                    if (bank == 0f && cash == 0f) {
                        Box(Modifier.fillMaxWidth().height(2.dp).background(PmTheme.colors.outline))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            history.forEach { month ->
                val isSelected = month.month == selected
                Text(
                    month.month.shortName(),
                    Modifier.weight(1f).clickable { onMonthClick(month.month) },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) PmTheme.colors.ink else PmTheme.colors.inkMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(PmTheme.colors.paid, stringResource(R.string.feature_reports_cash))
            Spacer(Modifier.width(16.dp))
            Legend(PmTheme.colors.primary, stringResource(R.string.feature_reports_bank))
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color, top: Boolean, bottom: Boolean) {
    val radius = 6.dp
    Box(
        Modifier
            .fillMaxWidth()
            // Fixed heights: in a Column, a fraction would be of the space left, not of the chart.
            .height((CHART_HEIGHT * fraction).coerceAtLeast(2.dp))
            .background(
                color,
                RoundedCornerShape(
                    topStart = if (top) radius else 0.dp,
                    topEnd = if (top) radius else 0.dp,
                    bottomStart = if (bottom) radius else 0.dp,
                    bottomEnd = if (bottom) radius else 0.dp,
                ),
            ),
    )
}

@Composable
private fun Legend(color: Color, label: String) {
    Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
    Spacer(Modifier.width(6.dp))
    Text(label, style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkSecondary)
}
