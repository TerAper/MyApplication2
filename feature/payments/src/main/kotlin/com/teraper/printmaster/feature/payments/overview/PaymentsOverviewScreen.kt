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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.teraper.printmaster.core.model.PaymentSort
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
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
import com.teraper.printmaster.core.designsystem.component.PmScreenTitle
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
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
    onImport: () -> Unit,
    onReviewPayments: () -> Unit,
    viewModel: PaymentsOverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PaymentsOverviewScreen(
        state = state,
        onFilterChange = viewModel::onFilterChange,
        onSortChange = viewModel::onSortChange,
        onOpenClient = onOpenClient,
        onImport = onImport,
        onReviewPayments = onReviewPayments,
    )
}

@Composable
internal fun PaymentsOverviewScreen(
    state: PaymentsOverviewUiState,
    onFilterChange: (BalanceFilter) -> Unit,
    onOpenClient: (Long) -> Unit,
    onSortChange: (PaymentSort) -> Unit = {},
    onImport: () -> Unit = {},
    onReviewPayments: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var sorting by remember { mutableStateOf(false) }
    if (sorting) SortSheet(state.sort, onPick = { onSortChange(it); sorting = false }, onDismiss = { sorting = false })
    LazyColumn(
        modifier = modifier.fillMaxSize().background(PmTheme.colors.background),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { PmScreenTitle(title = stringResource(R.string.feature_payments_title)) }
        if (state.isLoading) return@LazyColumn

        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(state)
                if (state.pendingPayments > 0) PendingBanner(state.pendingPayments, onReviewPayments)
                // Cash is taken on the client's card ("Cash in"); here the bank statements come in.
                PmSecondaryButton(
                    text = stringResource(R.string.feature_payments_import),
                    onClick = onImport,
                    icon = PmIcons.ImportExcel,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
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
                    TextButton(onClick = { sorting = true }) {
                        Icon(PmIcons.Sort, contentDescription = null, tint = PmTheme.colors.primary)
                        Text(
                            if (state.sort == PaymentSort.DEBT) stringResource(R.string.feature_payments_sort) else state.sort.label(),
                            color = PmTheme.colors.primary,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
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

/** Imported bank payments the app wasn't sure about. */
@Composable
private fun PendingBanner(count: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(PmTheme.shapes.button).background(PmTheme.colors.warningContainer).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(PmIcons.Warning, contentDescription = null, tint = PmTheme.colors.warning)
        Text(
            pluralStringResource(R.plurals.feature_payments_pending, count, count),
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = PmTheme.colors.warning,
        )
        Icon(PmIcons.Chevron, contentDescription = null, tint = PmTheme.colors.warning)
    }
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
        )
    }
}

@Composable
private fun PaymentSort.label(): String = stringResource(
    when (this) {
        PaymentSort.DEBT -> R.string.feature_payments_sort_debt
        PaymentSort.LONGEST_UNPAID -> R.string.feature_payments_sort_longest_unpaid
        PaymentSort.LAST_PAYMENT -> R.string.feature_payments_sort_last_payment
        PaymentSort.LEAST_PAID_SHARE -> R.string.feature_payments_sort_least_paid
        PaymentSort.MOST_CHARGED -> R.string.feature_payments_sort_most_charged
        PaymentSort.MOST_PAID -> R.string.feature_payments_sort_most_paid
        PaymentSort.NAME -> R.string.feature_payments_sort_name
    },
)

@Composable
private fun PaymentSort.help(): String? = when (this) {
    PaymentSort.LONGEST_UNPAID -> stringResource(R.string.feature_payments_sort_longest_unpaid_help)
    PaymentSort.LEAST_PAID_SHARE -> stringResource(R.string.feature_payments_sort_least_paid_help)
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(selected: PaymentSort, onPick: (PaymentSort) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PmTheme.colors.background,
    ) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.feature_payments_sort_title),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            PaymentSort.entries.forEach { sort ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(sort) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(sort.label(), style = MaterialTheme.typography.bodyLarge, color = if (sort == selected) PmTheme.colors.primary else PmTheme.colors.ink)
                        sort.help()?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted) }
                    }
                    if (sort == selected) Icon(PmIcons.Check, contentDescription = null, tint = PmTheme.colors.primary)
                }
            }
        }
    }
}
