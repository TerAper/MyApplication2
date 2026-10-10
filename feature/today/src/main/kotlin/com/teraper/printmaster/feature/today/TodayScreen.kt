package com.teraper.printmaster.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.AmountText
import com.teraper.printmaster.core.designsystem.component.AmountTone
import com.teraper.printmaster.core.designsystem.component.LocalShowMoney
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmOrderCard
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmScreenTitle
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.component.formatShort
import com.teraper.printmaster.core.designsystem.component.weekdayShort
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.IncomeTotals
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.Order
import com.teraper.printmaster.core.model.OrderStatus
import java.time.LocalDate
import java.time.LocalTime

/** Where Today's buttons lead; the app wires them to other features. */
data class TodayActions(
    val onOrderClick: (Long) -> Unit = {},
    val onNewOrder: () -> Unit = {},
    val onCashPayment: () -> Unit = {},
    val onOpenDebts: () -> Unit = {},
)

@Composable
internal fun TodayRoute(actions: TodayActions, viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TodayScreen(state, actions)
}

@Composable
internal fun TodayScreen(state: TodayUiState, actions: TodayActions, modifier: Modifier = Modifier) {
    val showMoney = LocalShowMoney.current
    LazyColumn(
        modifier.fillMaxSize().background(PmTheme.colors.background),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PmScreenTitle(title = stringResource(R.string.feature_today_title))
            Text(
                "${state.today.weekdayShort()}, ${state.today.formatShort()}",
                modifier = Modifier.padding(start = 18.dp, bottom = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = PmTheme.colors.inkMuted,
            )
        }
        if (state.isLoading) return@LazyColumn

        if (showMoney) item { MoneyCard(state, actions, Modifier.padding(horizontal = 16.dp)) }

        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PmPrimaryButton(stringResource(R.string.feature_today_new_order), actions.onNewOrder, Modifier.weight(1f), icon = PmIcons.Add)
                if (showMoney) PmSecondaryButton(stringResource(R.string.feature_today_cash), actions.onCashPayment, Modifier.weight(1f), icon = PmIcons.Payments)
            }
        }

        item {
            SectionTitle(
                if (state.visits.isEmpty()) {
                    stringResource(R.string.feature_today_visits_none)
                } else {
                    pluralStringResource(R.plurals.feature_today_visits, state.visits.size, state.visits.size, state.openVisits)
                },
            )
        }
        items(state.visits, key = { it.id }) { order ->
            PmOrderCard(order, onClick = { actions.onOrderClick(order.id) }, modifier = Modifier.padding(horizontal = 16.dp))
        }

        if (state.overdue.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.feature_today_overdue, state.overdue.size), warning = true) }
            items(state.overdue, key = { "overdue-${it.id}" }) { order ->
                PmOrderCard(order, onClick = { actions.onOrderClick(order.id) }, modifier = Modifier.padding(horizontal = 16.dp), today = state.today)
            }
        }
    }
}

@Composable
private fun MoneyCard(state: TodayUiState, actions: TodayActions, modifier: Modifier = Modifier) {
    PmCard(modifier.fillMaxWidth(), onClick = actions.onOpenDebts) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.feature_today_to_collect), style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted)
                    Text(
                        pluralStringResource(R.plurals.feature_today_debtors, state.debtorCount, state.debtorCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = PmTheme.colors.inkMuted,
                    )
                }
                AmountText(state.toCollect, tone = AmountTone.Debt, style = MaterialTheme.typography.headlineSmall)
            }
            HorizontalDivider(color = PmTheme.colors.surfaceMuted)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.feature_today_received),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = PmTheme.colors.inkMuted,
                )
                AmountText(state.receivedToday.total, tone = AmountTone.Paid, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, warning: Boolean = false) {
    Text(
        text,
        modifier = Modifier.padding(start = 18.dp, top = 12.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = if (warning) PmTheme.colors.warning else PmTheme.colors.inkMuted,
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun TodayScreenPreview() {
    val today = LocalDate.of(2026, 10, 8)
    fun order(id: Long, day: LocalDate, hour: Int, client: String, text: String, status: OrderStatus = OrderStatus.NEW) =
        Order(id, 1, id, client, day.atTime(LocalTime.of(hour, 0)), text, status, address = "Կոմիտաս 5")
    PmTheme {
        TodayScreen(
            TodayUiState(
                isLoading = false,
                today = today,
                visits = listOf(
                    order(1, today, 10, "«ԱԲԳ Սերվիս» ՍՊԸ", "Լիցքավորել 2 քարտրիջ"),
                    order(2, today, 14, "Թիվ 5 դպրոց", "Թմբուկի փոխարինում", OrderStatus.DONE),
                ),
                overdue = listOf(order(3, today.minusDays(1), 11, "«Տեխնո» ՓԲԸ", "Տպիչի ստուգում")),
                toCollect = Money.ofDram(318_500),
                debtorCount = 3,
                receivedToday = IncomeTotals(cash = Money.ofDram(17_000)),
            ),
            TodayActions(),
        )
    }
}
