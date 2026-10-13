package com.teraper.printmaster.feature.clients.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import com.teraper.printmaster.core.designsystem.component.formatShort
import com.teraper.printmaster.core.model.ClientSort
import java.time.Instant
import java.time.ZoneId
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.AmountText
import com.teraper.printmaster.core.designsystem.component.AmountTone
import com.teraper.printmaster.core.designsystem.component.LocalShowMoney
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmFilterChip
import com.teraper.printmaster.core.designsystem.component.PmScreenTitle
import com.teraper.printmaster.core.designsystem.component.PmSearchField
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Client
import com.teraper.printmaster.core.model.SerialMatch
import com.teraper.printmaster.core.model.ClientPhone
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.feature.clients.R
import com.teraper.printmaster.feature.clients.common.rememberSerialScanner
import com.teraper.printmaster.feature.clients.common.ScanOutcome
import com.teraper.printmaster.feature.clients.common.ClientAvatar
import com.teraper.printmaster.feature.clients.common.subtitle

@Composable
internal fun ClientListRoute(
    onClientClick: (Long) -> Unit,
    onAddClient: () -> Unit,
    onSearchSerial: (String) -> Unit,
    viewModel: ClientListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    // Scan an item's label: the serial search shows whose it is. No scanner = type it there.
    val scan = rememberSerialScanner { outcome ->
        when (outcome) {
            is ScanOutcome.Read -> onSearchSerial(outcome.text)
            ScanOutcome.Unavailable -> onSearchSerial("")
            ScanOutcome.Closed -> Unit
        }
    }
    ClientListScreen(
        state = state,
        query = query,
        onQueryChange = viewModel::onQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onSortChange = viewModel::onSortChange,
        onClientClick = onClientClick,
        onAddClient = onAddClient,
        onScanSerial = scan,
        onOpenSerial = onSearchSerial,
    )
}

@Composable
internal fun ClientListScreen(
    state: ClientListUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onFilterChange: (ClientFilter) -> Unit,
    onClientClick: (Long) -> Unit,
    onAddClient: () -> Unit,
    onSortChange: (ClientSort) -> Unit = {},
    onScanSerial: () -> Unit = {},
    onOpenSerial: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var sorting by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxSize().background(PmTheme.colors.background)) {
        Column(Modifier.fillMaxSize()) {
            PmScreenTitle(title = stringResource(R.string.feature_clients_title))

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PmSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    placeholder = stringResource(R.string.feature_clients_search_hint),
                    clearLabel = stringResource(R.string.feature_clients_clear_search),
                    emptyAction = {
                        IconButton(onClick = onScanSerial) {
                            Icon(PmIcons.Scan, contentDescription = stringResource(R.string.feature_clients_scan_serial), tint = PmTheme.colors.primary)
                        }
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterRow(selected = state.filter, totalCount = state.totalCount, onSelect = onFilterChange, modifier = Modifier.weight(1f))
                    // The chosen order is shown, so a list sorted by debt isn't mistaken for A–Z.
                    TextButton(onClick = { sorting = true }) {
                        Icon(PmIcons.Sort, contentDescription = null, tint = PmTheme.colors.primary)
                        Text(
                            if (state.sort == ClientSort.NAME) stringResource(R.string.feature_clients_sort) else state.sort.label(),
                            color = PmTheme.colors.primary,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            when {
                state.isLoading -> Unit
                state.totalCount == 0 -> PmEmptyState(
                    icon = PmIcons.Clients,
                    title = stringResource(R.string.feature_clients_empty_title),
                    message = stringResource(R.string.feature_clients_empty_message),
                )
                state.clients.isEmpty() && state.serials.isEmpty() -> PmEmptyState(
                    icon = PmIcons.Search,
                    title = stringResource(R.string.feature_clients_no_results_title),
                    message = stringResource(R.string.feature_clients_no_results_message),
                )
                else -> LazyColumn(
                    // Room at the bottom so the last row isn't hidden by the button.
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(state.serials, key = { "item-${it.item.id}" }) { match ->
                        SerialRow(match, onClick = { onOpenSerial(match.item.serialNumber) })
                        HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                    }
                    items(state.clients, key = { it.client.id }) { summary ->
                        ClientRow(summary = summary, sort = state.sort, onClick = { onClientClick(summary.client.id) })
                        HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                    }
                }
            }
        }

        if (sorting) {
            SortSheet(selected = state.sort, onPick = { onSortChange(it); sorting = false }, onDismiss = { sorting = false })
        }

        ExtendedFloatingActionButton(
            onClick = onAddClient,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = PmTheme.colors.primary,
            contentColor = PmTheme.colors.onPrimary,
            icon = { Icon(PmIcons.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.feature_clients_new_client)) },
        )
    }
}

@Composable
private fun FilterRow(selected: ClientFilter, totalCount: Int, onSelect: (ClientFilter) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val showMoney = LocalShowMoney.current
        ClientFilter.entries.filter { showMoney || it != ClientFilter.IN_DEBT }.forEach { filter ->
            val label = when (filter) {
                ClientFilter.ALL -> stringResource(R.string.feature_clients_filter_all, totalCount)
                ClientFilter.FIRMS -> stringResource(R.string.feature_clients_filter_firms)
                ClientFilter.PRIVATE -> stringResource(R.string.feature_clients_filter_private)
                ClientFilter.IN_DEBT -> stringResource(R.string.feature_clients_filter_debt)
            }
            PmFilterChip(text = label, selected = filter == selected, onClick = { onSelect(filter) })
        }
    }
}

@Composable
private fun ClientRow(summary: ClientSummary, sort: ClientSort, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PmTheme.colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClientAvatar(name = summary.client.name, type = summary.client.type)
        Column(Modifier.weight(1f)) {
            Text(
                summary.client.name,
                style = MaterialTheme.typography.titleSmall,
                color = PmTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                summary.subtitle(),
                style = MaterialTheme.typography.bodySmall,
                color = PmTheme.colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            sortDetail(summary, sort)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.primary, maxLines = 1)
            }
        }
        if (LocalShowMoney.current) BalanceAmount(summary.balance)
    }
}

/** Right side of a row: red if they owe, blue if overpaid, nothing when settled. */
@Composable
internal fun BalanceAmount(balance: Money, modifier: Modifier = Modifier) {
    when {
        balance.isPositive -> AmountText(balance, modifier, tone = AmountTone.Debt, style = MaterialTheme.typography.bodyMedium)
        balance.isNegative -> AmountText(-balance, modifier, tone = AmountTone.Overpaid, style = MaterialTheme.typography.bodyMedium, withSign = true)
        else -> Unit
    }
}

private fun previewClient(id: Long, name: String, type: ClientType, taxId: String?, balance: Long) = ClientSummary(
    client = Client(id, name, type, taxId, "", listOf(ClientPhone(id, "091 123456", "")), emptyList()),
    itemCount = if (type == ClientType.FIRM) 3 else 0,
    charged = Money.ofDram(balance.coerceAtLeast(0)),
    paid = Money.ofDram((-balance).coerceAtLeast(0)),
)

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun ClientListScreenPreview() {
    PmTheme {
        ClientListScreen(
            state = ClientListUiState(
                isLoading = false,
                totalCount = 3,
                clients = listOf(
                    previewClient(1, "«ԱԲԳ Սերվիս» ՍՊԸ", ClientType.FIRM, "01234567", 180_000),
                    previewClient(2, "Թիվ 5 դպրոց", ClientType.FIRM, "07654321", 0),
                    previewClient(3, "Աննա Մկրտչյան", ClientType.PRIVATE, null, -5_000),
                ),
            ),
            query = "",
            onQueryChange = {},
            onFilterChange = {},
            onClientClick = {},
            onAddClient = {},
        )
    }
}

/** A client item whose serial number has what was typed: opens its client and orders. */
@Composable
private fun SerialRow(match: SerialMatch, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(PmIcons.Item, contentDescription = null, tint = PmTheme.colors.primary)
        Column(Modifier.weight(1f)) {
            Text(match.item.serialNumber, style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.feature_clients_serial_row, match.item.title, match.clientName),
                style = MaterialTheme.typography.bodySmall,
                color = PmTheme.colors.inkMuted,
            )
        }
    }
}

@Composable
private fun ClientSort.label(): String = stringResource(
    when (this) {
        ClientSort.NAME -> R.string.feature_clients_sort_name
        ClientSort.DEBT -> R.string.feature_clients_sort_debt
        ClientSort.LONGEST_UNPAID -> R.string.feature_clients_sort_longest_unpaid
        ClientSort.LAST_PAYMENT -> R.string.feature_clients_sort_last_payment
        ClientSort.LAST_ORDER -> R.string.feature_clients_sort_last_order
        ClientSort.MOST_ORDERS -> R.string.feature_clients_sort_most_orders
        ClientSort.NEWEST -> R.string.feature_clients_sort_newest
        ClientSort.MOST_ITEMS -> R.string.feature_clients_sort_most_items
    },
)

/** What the list is sorted by, under the name: "Paid 12.09.2026", "Last visit 10.10.2026"… */
@Composable
private fun sortDetail(summary: ClientSummary, sort: ClientSort): String? {
    val showMoney = LocalShowMoney.current
    return when (sort) {
        ClientSort.LAST_PAYMENT, ClientSort.LONGEST_UNPAID -> if (!showMoney) null else summary.lastPaymentDate
            ?.let { stringResource(R.string.feature_clients_row_paid, it.formatShort()) }
            ?: stringResource(R.string.feature_clients_row_never_paid)
        ClientSort.LAST_ORDER -> summary.lastOrderDate?.let { stringResource(R.string.feature_clients_row_last_order, it.formatShort()) }
            ?: stringResource(R.string.feature_clients_row_no_orders)
        ClientSort.MOST_ORDERS -> pluralStringResource(R.plurals.feature_clients_row_orders, summary.orderCount, summary.orderCount)
        ClientSort.NEWEST -> summary.createdAt.takeIf { it > 0 }?.let {
            stringResource(R.string.feature_clients_row_added, Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().formatShort())
        }
        ClientSort.NAME, ClientSort.DEBT, ClientSort.MOST_ITEMS -> null
    }
}

/** The ways to order the list; money ones only where money is shown. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(selected: ClientSort, onPick: (ClientSort) -> Unit, onDismiss: () -> Unit) {
    val showMoney = LocalShowMoney.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PmTheme.colors.background,
    ) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.feature_clients_sort_title),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            ClientSort.entries.filter { showMoney || !it.needsMoney }.forEach { sort ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(sort) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            sort.label(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (sort == selected) PmTheme.colors.primary else PmTheme.colors.ink,
                        )
                        if (sort == ClientSort.LONGEST_UNPAID) {
                            Text(stringResource(R.string.feature_clients_sort_longest_unpaid_help), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
                        }
                    }
                    if (sort == selected) Icon(PmIcons.Check, contentDescription = null, tint = PmTheme.colors.primary)
                }
            }
        }
    }
}
