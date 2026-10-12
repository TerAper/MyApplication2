package com.teraper.printmaster.feature.clients.serial

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmOrderCard
import com.teraper.printmaster.core.designsystem.component.PmTag
import com.teraper.printmaster.core.designsystem.component.PmTextField
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.TagTone
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.CatalogItem
import com.teraper.printmaster.core.model.ClientItem
import com.teraper.printmaster.core.model.ItemField
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.core.model.SerialMatch
import com.teraper.printmaster.feature.clients.R
import com.teraper.printmaster.feature.clients.common.ScanOutcome
import com.teraper.printmaster.feature.clients.common.rememberSerialScanner
import java.time.LocalDate

@Composable
internal fun SerialSearchRoute(
    onBack: () -> Unit,
    onOpenClient: (Long) -> Unit,
    onOpenOrder: (Long) -> Unit,
    viewModel: SerialSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val scan = rememberSerialScanner { outcome ->
        when (outcome) {
            is ScanOutcome.Read -> viewModel.onScanned(outcome.text)
            ScanOutcome.Unavailable -> viewModel.onScanUnavailable()
            ScanOutcome.Closed -> Unit
        }
    }
    SerialSearchScreen(state, query, viewModel::onQueryChange, scan, onBack, onOpenClient, onOpenOrder)
}

@Composable
internal fun SerialSearchScreen(
    state: SerialSearchUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onScan: () -> Unit,
    onBack: () -> Unit,
    onOpenClient: (Long) -> Unit,
    onOpenOrder: (Long) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(PmTheme.colors.background)) {
        PmTopBar(title = stringResource(R.string.feature_clients_serial_title), navigationLabel = stringResource(R.string.feature_clients_back), onNavigate = onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                PmTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = stringResource(R.string.feature_clients_item_serial),
                    capitalization = KeyboardCapitalization.Characters,
                    trailingIcon = {
                        IconButton(onClick = onScan) {
                            Icon(PmIcons.Scan, contentDescription = stringResource(R.string.feature_clients_scan_serial), tint = PmTheme.colors.primary)
                        }
                    },
                )
                if (state.scanUnavailable) {
                    Text(stringResource(R.string.feature_clients_scan_unavailable), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.error)
                }
            }
            if (!state.isSearching && query.isNotBlank() && state.hits.isEmpty()) {
                item {
                    PmEmptyState(PmIcons.Item, stringResource(R.string.feature_clients_serial_none), stringResource(R.string.feature_clients_serial_none_hint))
                }
            }
            state.hits.forEach { hit ->
                item(key = "item-${hit.match.item.id}") { ItemHit(hit.match, onClick = { onOpenClient(hit.match.item.clientId) }) }
                if (hit.orders.isEmpty()) {
                    item(key = "none-${hit.match.item.id}") {
                        Text(stringResource(R.string.feature_clients_serial_no_orders), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
                    }
                } else {
                    item(key = "title-${hit.match.item.id}") {
                        Text(
                            stringResource(R.string.feature_clients_serial_orders, hit.orders.size),
                            Modifier.padding(start = 2.dp, top = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = PmTheme.colors.inkMuted,
                        )
                    }
                    items(hit.orders, key = { "order-${hit.match.item.id}-${it.id}" }) { order ->
                        PmOrderCard(order, onClick = { onOpenOrder(order.id) }, today = state.today, showClient = false)
                    }
                }
            }
        }
    }
}

/** The item found: whose it is, which model, where, its serial. */
@Composable
private fun ItemHit(match: SerialMatch, onClick: () -> Unit) {
    val owned = match.item
    PmCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(PmIcons.Clients, contentDescription = null, tint = PmTheme.colors.primary)
                Text(match.clientName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (!match.exact) PmTag(stringResource(R.string.feature_clients_serial_partial), TagTone.Warning)
            }
            Text(owned.item.title + if (owned.place.isBlank()) "" else " · ${owned.place}", style = MaterialTheme.typography.bodyLarge)
            Text(owned.serialNumber, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), color = PmTheme.colors.inkSecondary)
            Text(stringResource(R.string.feature_clients_serial_open_client), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.primary)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun SerialSearchPreview() {
    val type = ItemType(1, "Printer", fields = listOf(ItemField(1, 1, "Brand", inTitle = true)))
    val printer = ClientItem(5, 1, CatalogItem(1, type, "LaserJet M125", mapOf(1L to "HP")), place = "Accounting", serialNumber = "CNB8K12345")
    PmTheme {
        SerialSearchScreen(
            SerialSearchUiState(isSearching = false, hits = listOf(SerialHit(SerialMatch(printer, "Gamma LLC", exact = true), emptyList())), today = LocalDate.of(2026, 10, 11)),
            "CNB8K12345", {}, {}, {}, {}, {},
        )
    }
}
