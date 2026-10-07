package com.teraper.printmaster.feature.payments.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.AmountText
import com.teraper.printmaster.core.designsystem.component.AmountTone
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmFilterChip
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmScreenTitle
import com.teraper.printmaster.core.designsystem.component.format
import com.teraper.printmaster.core.designsystem.component.formatShort
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Client
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.core.model.IncomeTotals
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.feature.payments.R
import java.time.LocalDate

@Composable
internal fun PaymentsOverviewRoute(
    onOpenClient: (Long) -> Unit,
    onCashPayment: () -> Unit,
    viewModel: PaymentsOverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PaymentsOverviewScreen(
        state = state,
        onFilterChange = viewModel::onFilterChange,
        onOpenClient = onOpenClient,
        onCashPayment = onCashPayment,
    )
}

@Composable
internal fun PaymentsOverviewScreen(
    state: PaymentsOverviewUiState,
    onFilterChange: (BalanceFilter) -> Unit,
    onOpenClient: (Long) -> Unit,
    onCashPayment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(PmTheme.colors.background),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { PmScreenTitle(title = stringResource(R.string.feature_payments_title)) }
        if (state.isLoading) return@LazyColumn

        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(state)
                PmPrimaryButton(
                    text = stringResource(R.string.feature_payments_cash_payment),
                    onClick = onCashPayment,
                    icon = PmIcons.Add,
                    enabled = state.hasClients,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BalanceFilter.entries.forEach { filter ->
                        PmFilterChip(
                            text = filter.label(state.debtorCount),
                            selected = filter == state.filter,
                            onClick = { onFilterChange(filter) },
                        )
                    }
                }
            }
        }

        if (state.rows.isEmpty()) {
            item {
                Box(Modifier.height(320.dp)) {
                    PmEmptyState(
                        icon = PmIcons.Payments,
                        title = stringResource(
                            if (state.hasClients) R.string.feature_payments_nobody_title else R.string.feature_payments_empty_title,
                        ),
                        message = stringResource(
                            if (state.hasClients) R.string.feature_payments_nobody_message else R.string.feature_payments_no_clients_message,
                        ),
                    )
                }
            }
        } else {
            item {
                Text(
                    stringResource(R.string.feature_payments_clients_header),
                    modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = PmTheme.colors.inkMuted,
                )
            }
            items(state.rows, key = { it.client.id }) { summary ->
                BalanceRow(summary, onClick = { onOpenClient(summary.client.id) })
                HorizontalDivider(color = PmTheme.colors.surfaceMuted)
            }
        }
    }
}

@Composable
private fun BalanceFilter.label(debtorCount: Int): String = when (this) {
    BalanceFilter.IN_DEBT -> stringResource(R.string.feature_payments_filter_debt, debtorCount)
    BalanceFilter.PAID -> stringResource(R.string.feature_payments_filter_paid)
    BalanceFilter.OVERPAID -> stringResource(R.string.feature_payments_filter_overpaid)
    BalanceFilter.ALL -> stringResource(R.string.feature_payments_filter_all)
}

@Composable
private fun SummaryCard(state: PaymentsOverviewUiState) {
    PmCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.feature_payments_total_debt), style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted)
                AmountText(state.totalDebt, tone = AmountTone.Debt, style = MaterialTheme.typography.headlineSmall)
            }
            HorizontalDivider(color = PmTheme.colors.surfaceMuted)
            Text(stringResource(R.string.feature_payments_month_income), style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IncomePart(stringResource(R.string.feature_payments_cash), state.incomeThisMonth.cash, AmountTone.Paid)
                IncomePart(stringResource(R.string.feature_payments_bank), state.incomeThisMonth.bank, AmountTone.Overpaid)
                IncomePart(stringResource(R.string.feature_payments_total), state.incomeThisMonth.total, AmountTone.Neutral)
            }
        }
    }
}

@Composable
private fun IncomePart(label: String, amount: Money, tone: AmountTone) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
        AmountText(amount, tone = tone, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun BalanceRow(summary: ClientSummary, onClick: () -> Unit) {
    val balance = summary.balance
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PmTheme.colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                summary.client.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                balance.isPositive -> AmountText(balance, tone = AmountTone.Debt, style = MaterialTheme.typography.titleSmall)
                balance.isNegative -> AmountText(-balance, tone = AmountTone.Overpaid, style = MaterialTheme.typography.titleSmall, withSign = true)
                summary.charged.isPositive -> Text(stringResource(R.string.feature_payments_settled), style = MaterialTheme.typography.labelLarge, color = PmTheme.colors.paid)
            }
        }
        if (summary.charged.isPositive) {
            PaidBar(paid = summary.paid, charged = summary.charged)
        }
        Text(rowDetails(summary), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
    }
}

/** Green share = how much of everything charged has been paid. */
@Composable
private fun PaidBar(paid: Money, charged: Money) {
    val share = (paid.minor.toFloat() / charged.minor).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(PmTheme.shapes.pill)
            .background(PmTheme.colors.surfaceMuted),
    ) {
        Box(
            Modifier
                .fillMaxWidth(share)
                .height(6.dp)
                .background(PmTheme.colors.paid),
        )
    }
}

@Composable
private fun rowDetails(summary: ClientSummary): String {
    val parts = buildList {
        if (summary.charged.isPositive) {
            add(stringResource(R.string.feature_payments_paid_of, summary.paid.format(), summary.charged.format()))
        } else if (summary.paid.isPositive) {
            add(stringResource(R.string.feature_payments_paid_total, summary.paid.format()))
        } else {
            add(stringResource(R.string.feature_payments_no_activity))
        }
        summary.lastPaymentDate?.let { add(stringResource(R.string.feature_payments_last_payment, it.formatShort())) }
    }
    return parts.joinToString(" · ")
}

@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun PaymentsOverviewPreview() {
    fun row(id: Long, name: String, charged: Long, paid: Long) = ClientSummary(
        client = Client(id, name, ClientType.FIRM, null, "", emptyList(), emptyList()),
        charged = Money.ofDram(charged),
        paid = Money.ofDram(paid),
        lastPaymentDate = if (paid > 0) LocalDate.of(2026, 9, 28) else null,
    )
    PmTheme {
        PaymentsOverviewScreen(
            state = PaymentsOverviewUiState(
                isLoading = false,
                totalDebt = Money.ofDram(318_500),
                debtorCount = 3,
                incomeThisMonth = IncomeTotals(cash = Money.ofDram(170_000), bank = Money.ofDram(690_000)),
                rows = listOf(
                    row(1, "«ԱԲԳ Սերվիս» ՍՊԸ", 420_000, 240_000),
                    row(2, "Թիվ 12 դպրոց", 120_000, 24_000),
                    row(3, "«Տեխնո» ՓԲԸ", 42_500, 0),
                ),
                hasClients = true,
            ),
            onFilterChange = {},
            onOpenClient = {},
            onCashPayment = {},
        )
    }
}
