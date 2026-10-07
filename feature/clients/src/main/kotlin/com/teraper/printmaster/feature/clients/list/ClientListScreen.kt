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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmFilterChip
import com.teraper.printmaster.core.designsystem.component.PmScreenTitle
import com.teraper.printmaster.core.designsystem.component.PmSearchField
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Client
import com.teraper.printmaster.core.model.ClientPhone
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.feature.clients.R
import com.teraper.printmaster.feature.clients.common.ClientAvatar
import com.teraper.printmaster.feature.clients.common.subtitle

@Composable
internal fun ClientListRoute(
    onClientClick: (Long) -> Unit,
    onAddClient: () -> Unit,
    viewModel: ClientListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    ClientListScreen(
        state = state,
        query = query,
        onQueryChange = viewModel::onQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onClientClick = onClientClick,
        onAddClient = onAddClient,
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
    modifier: Modifier = Modifier,
) {
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
                )
                FilterRow(selected = state.filter, totalCount = state.totalCount, onSelect = onFilterChange)
            }
            Spacer(Modifier.height(8.dp))

            when {
                state.isLoading -> Unit
                state.totalCount == 0 -> PmEmptyState(
                    icon = PmIcons.Clients,
                    title = stringResource(R.string.feature_clients_empty_title),
                    message = stringResource(R.string.feature_clients_empty_message),
                )
                state.clients.isEmpty() -> PmEmptyState(
                    icon = PmIcons.Search,
                    title = stringResource(R.string.feature_clients_no_results_title),
                    message = stringResource(R.string.feature_clients_no_results_message),
                )
                else -> LazyColumn(
                    // Room at the bottom so the last row isn't hidden by the button.
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(state.clients, key = { it.client.id }) { summary ->
                        ClientRow(summary = summary, onClick = { onClientClick(summary.client.id) })
                        HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                    }
                }
            }
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
private fun FilterRow(selected: ClientFilter, totalCount: Int, onSelect: (ClientFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ClientFilter.entries.forEach { filter ->
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
private fun ClientRow(summary: ClientSummary, onClick: () -> Unit) {
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
        }
        BalanceAmount(summary.balance)
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
    printerCount = if (type == ClientType.FIRM) 3 else 0,
    balance = Money.ofDram(balance),
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
