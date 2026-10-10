package com.teraper.printmaster.feature.expenses.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.AmountText
import com.teraper.printmaster.core.designsystem.component.AmountTone
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.formatShort
import com.teraper.printmaster.core.designsystem.component.label
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Expense
import com.teraper.printmaster.core.model.ExpenseCategory
import com.teraper.printmaster.core.model.ExpenseTotals
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.feature.expenses.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

@Composable
internal fun ExpensesRoute(onBack: () -> Unit, onAdd: () -> Unit, onOpen: (Long) -> Unit, viewModel: ExpensesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExpensesScreen(state, onBack, onAdd, onOpen, viewModel::onPreviousMonth, viewModel::onNextMonth)
}

@Composable
internal fun ExpensesScreen(
    state: ExpensesUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(PmTheme.colors.background)) {
        Column(Modifier.fillMaxSize()) {
            PmTopBar(title = stringResource(R.string.feature_expenses_title), navigationLabel = stringResource(R.string.feature_expenses_back), onNavigate = onBack)
            if (state.isLoading) return@Column
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                item { MonthSwitcher(state.month, state.canGoNext, onPreviousMonth, onNextMonth) }
                item { TotalsCard(state.totals) }
                if (state.days.isEmpty()) {
                    item {
                        PmEmptyState(
                            icon = PmIcons.Payments,
                            title = stringResource(R.string.feature_expenses_empty),
                            message = stringResource(R.string.feature_expenses_empty_message),
                        )
                    }
                }
                state.days.forEach { (day, expenses) ->
                    item(key = "day-$day") {
                        Text(
                            day.formatShort(),
                            Modifier.padding(start = 18.dp, top = 14.dp, bottom = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = PmTheme.colors.inkMuted,
                        )
                    }
                    items(expenses, key = { it.id }) { expense ->
                        ExpenseRow(expense, onClick = { onOpen(expense.id) })
                        HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = PmTheme.colors.primary,
            contentColor = PmTheme.colors.onPrimary,
            icon = { Icon(PmIcons.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.feature_expenses_add)) },
        )
    }
}

@Composable
private fun MonthSwitcher(month: YearMonth, canGoNext: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(PmIcons.Back, contentDescription = stringResource(R.string.feature_expenses_previous)) }
        Text(
            month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } + " " + month.year,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(PmIcons.Back, contentDescription = stringResource(R.string.feature_expenses_next), Modifier.rotate(180f))
        }
    }
}

@Composable
private fun TotalsCard(totals: ExpenseTotals) {
    PmCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.feature_expenses_spent).uppercase(), style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted)
            AmountText(totals.total, tone = AmountTone.Debt, style = MaterialTheme.typography.headlineSmall)
            ExpenseCategory.entries.forEach { category ->
                val amount = totals.byCategory[category] ?: return@forEach
                Row {
                    Text(category.label(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.inkSecondary)
                    AmountText(amount, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(expense.category.label(), style = MaterialTheme.typography.titleSmall)
            if (expense.note.isNotBlank()) {
                Text(expense.note, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        AmountText(expense.amount, style = MaterialTheme.typography.titleSmall)
        Icon(PmIcons.Chevron, contentDescription = null, tint = PmTheme.colors.outlineStrong)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun ExpensesPreview() {
    val day = LocalDate.of(2026, 10, 9)
    PmTheme {
        ExpensesScreen(
            ExpensesUiState(
                isLoading = false,
                month = YearMonth.of(2026, 10),
                days = listOf(day to listOf(Expense(1, day, Money.ofDram(4_000), ExpenseCategory.TRANSPORT, "Fuel"), Expense(2, day, Money.ofDram(12_000), ExpenseCategory.TONER))),
                totals = ExpenseTotals(mapOf(ExpenseCategory.TRANSPORT to Money.ofDram(4_000), ExpenseCategory.TONER to Money.ofDram(12_000))),
            ),
            {}, {}, {}, {}, {},
        )
    }
}
