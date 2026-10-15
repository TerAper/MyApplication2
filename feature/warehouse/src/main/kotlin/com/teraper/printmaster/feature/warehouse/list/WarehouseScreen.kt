package com.teraper.printmaster.feature.warehouse.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.teraper.printmaster.core.designsystem.component.LocalShowMoney
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmFilterChip
import com.teraper.printmaster.core.designsystem.component.PmSearchField
import com.teraper.printmaster.core.designsystem.component.PmTag
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.TagTone
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.CatalogItem
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.StockFilter
import com.teraper.printmaster.core.model.StockLine
import com.teraper.printmaster.core.model.WarehouseMode
import com.teraper.printmaster.feature.warehouse.R

@Composable
internal fun WarehouseRoute(onBack: () -> Unit, onItemClick: (Long) -> Unit, viewModel: WarehouseViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WarehouseScreen(
        state = state,
        onBack = onBack,
        onItemClick = onItemClick,
        onQueryChange = viewModel::onQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onShowMode = viewModel::onShowMode,
        onCompanyChange = viewModel::onCompanyChange,
    )
    if (state.showMode) ModeDialog(state.mode, onPick = viewModel::onModeChange, onDismiss = viewModel::onDismissMode)
}

@Composable
internal fun WarehouseScreen(
    state: WarehouseUiState,
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (StockFilter) -> Unit,
    onShowMode: () -> Unit = {},
    onCompanyChange: (Long?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(PmTheme.colors.background)) {
        PmTopBar(
            title = stringResource(R.string.feature_warehouse_title),
            navigationLabel = stringResource(R.string.feature_warehouse_back),
            onNavigate = onBack,
            actions = {
                if (state.canSplit) {
                    IconButton(onClick = onShowMode) {
                        Icon(PmIcons.Company, contentDescription = stringResource(R.string.feature_warehouse_mode), tint = PmTheme.colors.primary)
                    }
                }
            },
        )
        if (state.isLoading) return@Column
        if (state.companies.isNotEmpty()) {
            // Master's phone: his own warehouse, or the stock a company he works for shares.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PmFilterChip(text = stringResource(R.string.feature_warehouse_mine), selected = state.companyId == null, onClick = { onCompanyChange(null) })
                state.companies.forEach { company ->
                    PmFilterChip(text = company.name, selected = state.companyId == company.id, onClick = { onCompanyChange(company.id) })
                }
            }
        }
        if (state.companyId != null) {
            CompanyStock(state, onQueryChange)
            return@Column
        }
        if (state.totalCount == 0) {
            PmEmptyState(PmIcons.Warehouse, stringResource(R.string.feature_warehouse_empty_title), stringResource(R.string.feature_warehouse_empty_message))
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Summary(state, Modifier.padding(16.dp)) }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PmSearchField(
                        query = state.query,
                        onQueryChange = onQueryChange,
                        placeholder = stringResource(R.string.feature_warehouse_search_hint),
                        clearLabel = stringResource(R.string.feature_warehouse_clear),
                    )
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StockFilter.entries.forEach { filter ->
                            PmFilterChip(text = filter.label(state.lowCount), selected = filter == state.filter, onClick = { onFilterChange(filter) })
                        }
                    }
                }
            }
            if (state.lines.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.feature_warehouse_no_results),
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PmTheme.colors.inkMuted,
                    )
                }
            }
            items(state.lines, key = { it.item.id }) { line ->
                StockRow(line, onClick = { onItemClick(line.item.id) })
                HorizontalDivider(color = PmTheme.colors.surfaceMuted)
            }
        }
    }
}

/** Another company's stock as it shares it: read only, prices when it shows them. */
@Composable
private fun CompanyStock(state: WarehouseUiState, onQueryChange: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                PmSearchField(
                    query = state.query,
                    onQueryChange = onQueryChange,
                    placeholder = stringResource(R.string.feature_warehouse_search_hint),
                    clearLabel = stringResource(R.string.feature_warehouse_clear),
                )
            }
        }
        if (state.companyLines.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.feature_warehouse_no_results),
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PmTheme.colors.inkMuted,
                )
            }
        }
        items(state.companyLines, key = { "${it.type}/${it.name}" }) { line ->
            Row(
                Modifier.fillMaxWidth().background(PmTheme.colors.surface).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(line.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(line.type, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted, maxLines = 1)
                }
                line.price?.let { AmountText(it, style = MaterialTheme.typography.bodyMedium) }
                Text(
                    stringResource(R.string.feature_warehouse_count, line.count),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (line.count <= 0) PmTheme.colors.error else PmTheme.colors.ink,
                )
            }
            HorizontalDivider(color = PmTheme.colors.surfaceMuted)
        }
    }
}

@Composable
private fun Summary(state: WarehouseUiState, modifier: Modifier = Modifier) {
    PmCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.feature_warehouse_in_stock), style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted)
                Text("${state.inStock}", style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.feature_warehouse_to_buy), style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted)
                Text("${state.lowCount}", style = MaterialTheme.typography.titleLarge, color = if (state.lowCount > 0) PmTheme.colors.warning else PmTheme.colors.ink)
            }
            if (LocalShowMoney.current) {
                Column(Modifier.weight(1.4f), horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.feature_warehouse_value), style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted)
                    AmountText(state.value, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun StockRow(line: StockLine, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(PmTheme.colors.surface).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(line.item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(line.item.type.name, line.item.details).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = PmTheme.colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (line.isLow) PmTag(stringResource(R.string.feature_warehouse_buy, line.toBuy), TagTone.Warning)
        }
        Text(
            stringResource(R.string.feature_warehouse_count, line.count),
            style = MaterialTheme.typography.titleMedium,
            color = when {
                line.count <= 0 -> PmTheme.colors.error
                line.isLow -> PmTheme.colors.warning
                else -> PmTheme.colors.ink
            },
        )
    }
}

@Composable
private fun StockFilter.label(low: Int): String = when (this) {
    StockFilter.ALL -> stringResource(R.string.feature_warehouse_filter_all)
    StockFilter.IN_STOCK -> stringResource(R.string.feature_warehouse_filter_in_stock)
    StockFilter.LOW -> stringResource(R.string.feature_warehouse_filter_low, low)
    StockFilter.OUT -> stringResource(R.string.feature_warehouse_filter_out)
}

@Composable
private fun ModeDialog(mode: WarehouseMode, onPick: (WarehouseMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_warehouse_mode)) },
        text = {
            Column {
                WarehouseMode.entries.forEach { option ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(option) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = option == mode, onClick = { onPick(option) }, colors = RadioButtonDefaults.colors(selectedColor = PmTheme.colors.primary))
                        Text(
                            stringResource(if (option == WarehouseMode.SHARED) R.string.feature_warehouse_mode_shared else R.string.feature_warehouse_mode_per_company),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                Text(stringResource(R.string.feature_warehouse_mode_help), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_warehouse_cancel)) } },
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun WarehousePreview() {
    val cartridge = ItemType(2, "Քարտրիջ")
    PmTheme {
        WarehouseScreen(
            state = WarehouseUiState(
                isLoading = false,
                totalCount = 3,
                inStock = 2,
                lowCount = 1,
                value = Money.ofDram(84_000),
                lines = listOf(
                    StockLine(CatalogItem(1, cartridge, "CF283A", cost = Money.ofDram(7_000)), 1, 4),
                    StockLine(CatalogItem(2, cartridge, "CE285A", cost = Money.ofDram(6_000)), 12),
                    StockLine(CatalogItem(3, cartridge, "Q2612A"), 0),
                ),
            ),
            onBack = {},
            onItemClick = {},
            onQueryChange = {},
            onFilterChange = {},
        )
    }
}
