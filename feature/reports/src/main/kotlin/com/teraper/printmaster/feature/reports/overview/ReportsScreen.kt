package com.teraper.printmaster.feature.reports.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.AmountText
import com.teraper.printmaster.core.designsystem.component.AmountTone
import com.teraper.printmaster.core.designsystem.component.CompanyBadge
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.BilledTotals
import com.teraper.printmaster.core.model.Client
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.core.model.Company
import com.teraper.printmaster.core.model.IncomeTotals
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.MonthIncome
import com.teraper.printmaster.core.model.MonthReport
import com.teraper.printmaster.core.model.WorkTotals
import com.teraper.printmaster.feature.reports.R
import java.time.YearMonth

@Composable
internal fun ReportsRoute(
    onBack: () -> Unit,
    onOpenClient: (Long) -> Unit,
    onExportDebts: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportsScreen(
        state = state,
        onBack = onBack,
        onPreviousMonth = viewModel::onPreviousMonth,
        onNextMonth = viewModel::onNextMonth,
        onMonthClick = viewModel::onMonthClick,
        onOpenClient = onOpenClient,
        onExportDebts = onExportDebts,
    )
}

@Composable
internal fun ReportsScreen(
    state: ReportsUiState,
    onBack: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onMonthClick: (YearMonth) -> Unit,
    onOpenClient: (Long) -> Unit,
    onExportDebts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(PmTheme.colors.background)) {
        PmTopBar(
            title = stringResource(R.string.feature_reports_title),
            navigationLabel = stringResource(R.string.feature_reports_back),
            onNavigate = onBack,
            actions = {
                state.company?.let { CompanyBadge(it.initials, it.colorIndex, Modifier.padding(end = 12.dp)) }
            },
        )
        if (state.isLoading) return@Column
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MonthSwitcher(state, onPreviousMonth, onNextMonth)
            ReceivedCard(state, onMonthClick)
            BilledCard(state.report.billed)
            WorkCard(state.report.work)
            DebtCard(state, onOpenClient, onExportDebts)
        }
    }
}

@Composable
private fun MonthSwitcher(state: ReportsUiState, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(PmIcons.Back, contentDescription = stringResource(R.string.feature_reports_previous_month))
        }
        Text(
            state.month.longName(),
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onNext, enabled = state.canGoNext) {
            Icon(PmIcons.Back, contentDescription = stringResource(R.string.feature_reports_next_month), Modifier.rotate(180f))
        }
    }
}

@Composable
private fun ReceivedCard(state: ReportsUiState, onMonthClick: (YearMonth) -> Unit) {
    val income = state.report.income
    ReportCard(stringResource(R.string.feature_reports_received), income.total) {
        AmountRow(stringResource(R.string.feature_reports_cash), income.cash)
        AmountRow(stringResource(R.string.feature_reports_bank), income.bank)
        IncomeChart(state.history, state.month, onMonthClick, Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun BilledCard(billed: BilledTotals) {
    ReportCard(stringResource(R.string.feature_reports_billed), billed.total) {
        AmountRow(stringResource(R.string.feature_reports_billed_repairs), billed.repairs)
        AmountRow(stringResource(R.string.feature_reports_billed_invoices), billed.invoices)
        AmountRow(stringResource(R.string.feature_reports_billed_manual), billed.manual)
    }
}

@Composable
private fun WorkCard(work: WorkTotals) {
    ReportCard(stringResource(R.string.feature_reports_profit), work.profit, tone = if (work.profit.isNegative) AmountTone.Debt else AmountTone.Paid) {
        Text(
            pluralStringResource(R.plurals.feature_reports_orders_done, work.orders, work.orders) +
                (work.marginPercent?.let { " · " + stringResource(R.string.feature_reports_margin, it) } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = PmTheme.colors.inkMuted,
        )
        AmountRow(stringResource(R.string.feature_reports_work_revenue), work.revenue)
        AmountRow(stringResource(R.string.feature_reports_work_cost), work.cost)
    }
}

@Composable
private fun DebtCard(state: ReportsUiState, onOpenClient: (Long) -> Unit, onExportDebts: () -> Unit) {
    ReportCard(stringResource(R.string.feature_reports_owed_now), state.totalDebt, tone = if (state.totalDebt.isPositive) AmountTone.Debt else AmountTone.Neutral) {
        Text(
            pluralStringResource(R.plurals.feature_reports_debtors, state.debtorCount, state.debtorCount),
            style = MaterialTheme.typography.bodySmall,
            color = PmTheme.colors.inkMuted,
        )
        state.topDebtors.forEachIndexed { index, debtor ->
            if (index > 0) HorizontalDivider(color = PmTheme.colors.surfaceMuted)
            Row(
                Modifier.fillMaxWidth().clickable { onOpenClient(debtor.client.id) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    debtor.client.name,
                    Modifier.weight(1f).padding(end = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AmountText(debtor.balance, tone = AmountTone.Debt, style = MaterialTheme.typography.bodyMedium)
            }
        }
        PmSecondaryButton(
            text = stringResource(R.string.feature_reports_export_debts),
            onClick = onExportDebts,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            icon = PmIcons.Export,
        )
    }
}

@Composable
private fun ReportCard(
    title: String,
    total: Money,
    tone: AmountTone = AmountTone.Neutral,
    content: @Composable () -> Unit,
) {
    PmCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted)
            AmountText(total, tone = tone, style = MaterialTheme.typography.headlineSmall)
            content()
        }
    }
}

@Composable
private fun AmountRow(label: String, amount: Money) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.inkSecondary)
        AmountText(amount, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 1400)
@Composable
private fun ReportsScreenPreview() {
    val month = YearMonth.of(2026, 10)
    PmTheme {
        ReportsScreen(
            state = ReportsUiState(
                isLoading = false,
                company = Company(1, "Alfa Print"),
                month = month,
                report = MonthReport(
                    month,
                    IncomeTotals(Money.ofDram(84_000), Money.ofDram(260_000)),
                    BilledTotals(Money.ofDram(96_000), Money.ofDram(240_000)),
                    WorkTotals(14, Money.ofDram(96_000), Money.ofDram(31_000)),
                ),
                history = (5 downTo 0).map {
                    MonthIncome(month.minusMonths(it.toLong()), IncomeTotals(Money.ofDram(20_000L * (it + 1)), Money.ofDram(50_000L * it)))
                },
                totalDebt = Money.ofDram(180_000),
                debtorCount = 7,
                topDebtors = listOf(
                    ClientSummary(Client(1, "«Ալֆա» ՍՊԸ", ClientType.FIRM, null, "", emptyList(), emptyList()), charged = Money.ofDram(90_000)),
                ),
            ),
            onBack = {}, onPreviousMonth = {}, onNextMonth = {}, onMonthClick = {}, onOpenClient = {}, onExportDebts = {},
        )
    }
}
