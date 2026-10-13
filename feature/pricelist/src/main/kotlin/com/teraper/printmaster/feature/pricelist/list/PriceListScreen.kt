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
import com.teraper.printmaster.core.designsystem.component.PmSegmentedButtons
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.format
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.PriceItem
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.feature.pricelist.R

@Composable
internal fun PriceListRoute(
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    onAddItem: (typeId: Long) -> Unit,
    onOpenGoods: (Long) -> Unit,
    onAddGoods: () -> Unit,
    viewModel: PriceListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PriceListScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onGroupClick = viewModel::onGroupClick,
        onBack = onBack,
        onItemClick = onItemClick,
        onAddItem = onAddItem,
        onShowGoods = viewModel::onShowGoods,
        onOpenGoods = onOpenGoods,
        onAddGoods = onAddGoods,
    )
}

@Composable
internal fun PriceListScreen(
    state: PriceListUiState,
    onQueryChange: (String) -> Unit,
    onGroupClick: (Long?) -> Unit,
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    onAddItem: (typeId: Long) -> Unit,
    modifier: Modifier = Modifier,
    onShowGoods: (Boolean) -> Unit = {},
    onOpenGoods: (Long) -> Unit = {},
    onAddGoods: () -> Unit = {},
) {
    Box(modifier.fillMaxSize().background(PmTheme.colors.background)) {
        Column(Modifier.fillMaxSize()) {
            PmTopBar(
                title = stringResource(R.string.feature_pricelist_title),
                navigationLabel = stringResource(R.string.feature_pricelist_back),
                onNavigate = onBack,
            )
            // Services are priced here; goods are catalog items with a price.
            PmSegmentedButtons(
                options = listOf(false, true),
                selected = state.showGoods,
                onSelect = onShowGoods,
                label = { stringResource(if (it) R.string.feature_pricelist_goods else R.string.feature_pricelist_services) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (state.showGoods) {
                GoodsList(state, onQueryChange, onOpenGoods)
            } else if (state.totalCount > 0) {
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
                        selected = state.group == null,
                        onClick = { onGroupClick(null) },
                    )
                    state.chips.forEach { (group, count) ->
                        val id = group.type?.id ?: PriceListUiState.GENERAL
                        PmFilterChip(text = "${group.name()} $count", selected = state.group == id, onClick = { onGroupClick(id) })
                    }
                }
            }
            if (!state.showGoods) when {
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
                        item(key = "group-${group.type?.id}") {
                            Text(
                                group.name().uppercase(),
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
            onClick = { if (state.showGoods) onAddGoods() else onAddItem(state.group ?: PriceListUiState.GENERAL) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = PmTheme.colors.primary,
            contentColor = PmTheme.colors.onPrimary,
            icon = { Icon(PmIcons.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.feature_pricelist_add)) },
        )
    }
}

/** Catalog items with a price; tapping one opens it in the catalog, where its price is set. */
@Composable
private fun GoodsList(state: PriceListUiState, onQueryChange: (String) -> Unit, onOpenGoods: (Long) -> Unit) {
    if (state.goodsCount == 0) {
        PmEmptyState(
            icon = PmIcons.Catalog,
            title = stringResource(R.string.feature_pricelist_goods_empty_title),
            message = stringResource(R.string.feature_pricelist_goods_empty_message),
        )
        return
    }
    PmSearchField(
        query = state.query,
        onQueryChange = onQueryChange,
        placeholder = stringResource(R.string.feature_pricelist_search_hint),
        clearLabel = stringResource(R.string.feature_pricelist_clear_search),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        items(state.goods, key = { it.id }) { item ->
            Row(
                Modifier.fillMaxWidth().background(PmTheme.colors.surface).clickable { onOpenGoods(item.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(item.type.name, item.details).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = PmTheme.colors.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    AmountText(item.price, style = MaterialTheme.typography.titleSmall)
                    if (!item.cost.isZero) ProfitText(item.price - item.cost)
                }
            }
            HorizontalDivider(color = PmTheme.colors.surfaceMuted)
        }
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
        PriceItem(1, 2, "Լիցքավորում (սև)", "HP 85A, 83A, Canon 725", Money.ofDram(3_000), Money.ofDram(900)),
        PriceItem(2, 2, "Չիպի փոխարինում", price = Money.ofDram(2_500), cost = Money.ofDram(1_200)),
    )
    val printers = listOf(PriceItem(3, 1, "Թմբուկի փոխարինում", price = Money.ofDram(8_000), cost = Money.ofDram(4_500)))
    val cartridge = ItemType(2, "Քարտրիջ", 1)
    val printer = ItemType(1, "Տպիչ")
    PmTheme {
        PriceListScreen(
            state = PriceListUiState(
                isLoading = false,
                totalCount = 3,
                chips = listOf(PriceGroup(cartridge, cartridges) to 2, PriceGroup(printer, printers) to 1),
                groups = listOf(PriceGroup(cartridge, cartridges), PriceGroup(printer, printers)),
            ),
            onQueryChange = {},
            onGroupClick = {},
            onBack = {},
            onItemClick = {},
            onAddItem = {},
        )
    }
}

/** The type's name, or "General" for work not tied to a type. */
@Composable
private fun PriceGroup.name(): String = type?.name ?: stringResource(R.string.feature_pricelist_general)
