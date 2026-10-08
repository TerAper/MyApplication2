package com.teraper.printmaster.feature.pricelist.list

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
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmFilterChip
import com.teraper.printmaster.core.designsystem.component.PmSearchField
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.format
import com.teraper.printmaster.core.designsystem.component.label
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.PriceItem
import com.teraper.printmaster.core.model.RepairCategory
import com.teraper.printmaster.feature.pricelist.R

@Composable
internal fun PriceListRoute(
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    onAddItem: (RepairCategory) -> Unit,
    viewModel: PriceListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PriceListScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onCategoryClick = viewModel::onCategoryClick,
        onBack = onBack,
        onItemClick = onItemClick,
        onAddItem = onAddItem,
    )
}

@Composable
internal fun PriceListScreen(
    state: PriceListUiState,
    onQueryChange: (String) -> Unit,
    onCategoryClick: (RepairCategory?) -> Unit,
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    onAddItem: (RepairCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(PmTheme.colors.background)) {
        Column(Modifier.fillMaxSize()) {
            PmTopBar(
                title = stringResource(R.string.feature_pricelist_title),
                navigationLabel = stringResource(R.string.feature_pricelist_back),
                onNavigate = onBack,
            )
            if (state.totalCount > 0) {
                PmSearchField(
                    query = state.query,
                    onQueryChange = onQueryChange,
                    placeholder = stringResource(R.string.feature_pricelist_search_hint),
                    clearLabel = stringResource(R.string.feature_pricelist_clear_search),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PmFilterChip(
                        text = stringResource(R.string.feature_pricelist_all, state.totalCount),
                        selected = state.category == null,
                        onClick = { onCategoryClick(null) },
                    )
                    RepairCategory.entries.forEach { category ->
                        PmFilterChip(
                            text = "${category.label()} ${state.counts[category] ?: 0}",
                            selected = state.category == category,
                            onClick = { onCategoryClick(category) },
                        )
                    }
                }
            }
            when {
                state.isLoading -> Unit
                state.totalCount == 0 -> PmEmptyState(
                    icon = PmIcons.PriceList,
                    title = stringResource(R.string.feature_pricelist_empty_title),
                    message = stringResource(R.string.feature_pricelist_empty_message),
                )
                state.groups.isEmpty() -> PmEmptyState(
                    icon = PmIcons.Search,
                    title = stringResource(R.string.feature_pricelist_no_results),
                    message = "",
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    state.groups.forEach { group ->
                        item(key = "category-${group.category}") {
                            Text(
                                group.category.label().uppercase(),
                                modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = PmTheme.colors.inkMuted,
                            )
                        }
                        items(group.items, key = { it.id }) { item ->
                            PriceRow(item, onClick = { onItemClick(item.id) })
                            HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { onAddItem(state.category ?: RepairCategory.CARTRIDGE) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = PmTheme.colors.primary,
            contentColor = PmTheme.colors.onPrimary,
            icon = { Icon(PmIcons.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.feature_pricelist_add)) },
        )
    }
}

@Composable
private fun PriceRow(item: PriceItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PmTheme.colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.description.isNotBlank()) {
                Text(
                    item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = PmTheme.colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            AmountText(item.price, style = MaterialTheme.typography.titleSmall)
            if (!item.cost.isZero) ProfitText(item.profit)
        }
    }
}

/** "profit 2 000 ֏", red when the item is sold below cost. */
@Composable
internal fun ProfitText(profit: Money, modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.feature_pricelist_profit_short, profit.format()),
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = if (profit.isNegative) PmTheme.colors.debt else PmTheme.colors.paid,
        maxLines = 1,
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun PriceListScreenPreview() {
    val cartridges = listOf(
        PriceItem(1, RepairCategory.CARTRIDGE, "Լիցքավորում (սև)", "HP 85A, 83A, Canon 725", Money.ofDram(3_000), Money.ofDram(900)),
        PriceItem(2, RepairCategory.CARTRIDGE, "Չիպի փոխարինում", price = Money.ofDram(2_500), cost = Money.ofDram(1_200)),
    )
    val printers = listOf(PriceItem(3, RepairCategory.PRINTER, "Թմբուկի փոխարինում", price = Money.ofDram(8_000), cost = Money.ofDram(4_500)))
    PmTheme {
        PriceListScreen(
            state = PriceListUiState(
                isLoading = false,
                totalCount = 3,
                counts = mapOf(RepairCategory.CARTRIDGE to 2, RepairCategory.PRINTER to 1),
                groups = listOf(PriceGroup(RepairCategory.CARTRIDGE, cartridges), PriceGroup(RepairCategory.PRINTER, printers)),
            ),
            onQueryChange = {},
            onCategoryClick = {},
            onBack = {},
            onItemClick = {},
            onAddItem = {},
        )
    }
}
