package com.teraper.printmaster.feature.orders.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmOrderCard
import com.teraper.printmaster.core.designsystem.component.PmScreenTitle
import com.teraper.printmaster.core.designsystem.component.relativeLabel
import com.teraper.printmaster.core.designsystem.component.weekdayShort
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Order
import com.teraper.printmaster.core.model.OrderStatus
import com.teraper.printmaster.feature.orders.R
import java.time.LocalDate
import java.time.LocalTime

@Composable
internal fun OrdersRoute(
    onOrderClick: (Long) -> Unit,
    onNewOrder: (LocalDate) -> Unit,
    viewModel: OrdersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OrdersScreen(state, viewModel::onDateSelected, onOrderClick, onNewOrder)
}

@Composable
internal fun OrdersScreen(
    state: OrdersUiState,
    onDateSelected: (LocalDate) -> Unit,
    onOrderClick: (Long) -> Unit,
    onNewOrder: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(PmTheme.colors.background)) {
        Column(Modifier.fillMaxSize()) {
            PmScreenTitle(title = stringResource(R.string.feature_orders_title))
            DayStrip(state, onDateSelected)
            Text(
                state.selectedDate.relativeLabel(state.today) +
                    if (state.selectedDate in listOf(state.today, state.today.plusDays(1), state.today.minusDays(1))) {
                        " · " + "%02d.%02d".format(state.selectedDate.dayOfMonth, state.selectedDate.monthValue)
                    } else {
                        ""
                    },
                modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 6.dp),
                style = MaterialTheme.typography.titleMedium,
                color = PmTheme.colors.ink,
            )
            when {
                state.isLoading -> Unit
                state.orders.isEmpty() && state.overdue.isEmpty() -> PmEmptyState(
                    icon = PmIcons.Orders,
                    title = stringResource(R.string.feature_orders_empty_title),
                    message = stringResource(R.string.feature_orders_empty_message),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.orders, key = { it.id }) { order ->
                        PmOrderCard(order, onClick = { onOrderClick(order.id) })
                    }
                    if (state.orders.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.feature_orders_none_today),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PmTheme.colors.inkMuted,
                            )
                        }
                    }
                    if (state.overdue.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.feature_orders_overdue, state.overdue.size),
                                modifier = Modifier.padding(top = 12.dp, start = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = PmTheme.colors.warning,
                            )
                        }
                        items(state.overdue, key = { "overdue-${it.id}" }) { order ->
                            PmOrderCard(order, onClick = { onOrderClick(order.id) }, today = state.today)
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { onNewOrder(state.selectedDate.coerceAtLeast(state.today)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = PmTheme.colors.primary,
            contentColor = PmTheme.colors.onPrimary,
            icon = { Icon(PmIcons.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.feature_orders_new)) },
        )
    }
}

/** Days side by side: weekday, date, and a dot per day that has orders. */
@Composable
private fun DayStrip(state: OrdersUiState, onDateSelected: (LocalDate) -> Unit) {
    // Start with yesterday at the left edge so today is in view.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (state.days.indexOf(state.today) - 1).coerceAtLeast(0))
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.days, key = { it.toEpochDay() }) { day ->
            val selected = day == state.selectedDate
            val isToday = day == state.today
            val c = PmTheme.colors
            Column(
                Modifier
                    .width(52.dp)
                    .background(if (selected) c.primary else c.surface, PmTheme.shapes.button)
                    .border(1.dp, if (isToday && !selected) c.primary else c.outline, PmTheme.shapes.button)
                    .clickable { onDateSelected(day) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(day.weekdayShort(), style = MaterialTheme.typography.labelSmall, color = if (selected) c.onPrimary else c.inkMuted)
                Text(
                    day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) c.onPrimary else if (day < state.today) c.inkMuted else c.ink,
                )
                val count = state.counts[day] ?: 0
                Box(
                    Modifier
                        .size(6.dp)
                        .background(
                            when {
                                count == 0 -> Color.Transparent
                                selected -> c.onPrimary
                                else -> c.primary
                            },
                            CircleShape,
                        ),
                )
            }
        }
    }
    Box(Modifier.height(2.dp))
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun OrdersScreenPreview() {
    val today = LocalDate.of(2026, 10, 8)
    fun order(id: Long, day: LocalDate, hour: Int, client: String, text: String, status: OrderStatus = OrderStatus.NEW) =
        Order(id, 1, id, client, day.atTime(LocalTime.of(hour, 0)), text, status, address = "Կոմիտաս 5")
    PmTheme {
        OrdersScreen(
            state = OrdersUiState(
                isLoading = false,
                today = today,
                selectedDate = today,
                days = (-7..21).map { today.plusDays(it.toLong()) },
                counts = mapOf(today to 2, today.plusDays(1) to 1),
                orders = listOf(
                    order(1, today, 10, "«ԱԲԳ Սերվիս» ՍՊԸ", "Լիցքավորել 2 քարտրիջ"),
                    order(2, today, 14, "Թիվ 5 դպրոց", "Թմբուկի փոխարինում", OrderStatus.IN_PROGRESS),
                ),
                overdue = listOf(order(3, today.minusDays(2), 11, "«Տեխնո» ՓԲԸ", "Տպիչի ստուգում")),
            ),
            onDateSelected = {}, onOrderClick = {}, onNewOrder = {},
        )
    }
}
