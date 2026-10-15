package com.teraper.printmaster.feature.orders.repair

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.teraper.printmaster.core.designsystem.component.AmountText
import com.teraper.printmaster.core.designsystem.component.TagTone
import com.teraper.printmaster.core.designsystem.component.PmTag
import com.teraper.printmaster.core.designsystem.component.PmSegmentedButtons
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.CatalogItem
import com.teraper.printmaster.core.designsystem.component.PmFilterChip
import com.teraper.printmaster.core.designsystem.component.PmSearchField
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.PriceItem
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.feature.orders.R

/**
 * Pick work and parts from the price list. Stays open so several items can be added;
 * each tap adds one more of that item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PricePickerSheet(
    picker: PricePicker,
    items: List<PriceItem>,
    priceListIsEmpty: Boolean,
    quantities: Map<Long, Int>,
    types: List<ItemType>,
    hasGeneral: Boolean,
    onGroupClick: (Long?) -> Unit,
    onQueryChange: (String) -> Unit,
    onPick: (PriceItem) -> Unit,
    onAddCustom: () -> Unit,
    onDismiss: () -> Unit,
    goodsAvailable: Boolean = false,
    goods: List<CatalogItem> = emptyList(),
    fittingGoods: Set<Long> = emptySet(),
    goodsQuantities: Map<Long, Int> = emptyMap(),
    goodsStock: Map<Long, Int> = emptyMap(),
    onGoodsMode: (Boolean) -> Unit = {},
    onPickGoods: (CatalogItem) -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PmTheme.colors.background,
    ) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.feature_orders_repair_price_list), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_orders_repair_done)) }
            }
            if (goodsAvailable) {
                // Services from the price list, or goods (catalog items with a price).
                PmSegmentedButtons(
                    options = listOf(false, true),
                    selected = picker.goods,
                    onSelect = onGoodsMode,
                    label = { stringResource(if (it) R.string.feature_orders_repair_goods else R.string.feature_orders_repair_services) },
                )
            }
            if (picker.goods) {
                PmSearchField(
                    query = picker.query,
                    onQueryChange = onQueryChange,
                    placeholder = stringResource(R.string.feature_orders_repair_search_hint),
                    clearLabel = stringResource(R.string.feature_orders_repair_clear_search),
                )
            } else if (!priceListIsEmpty) {
                PmSearchField(
                    query = picker.query,
                    onQueryChange = onQueryChange,
                    placeholder = stringResource(R.string.feature_orders_repair_search_hint),
                    clearLabel = stringResource(R.string.feature_orders_repair_clear_search),
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PmFilterChip(
                        text = stringResource(R.string.feature_orders_repair_all),
                        selected = picker.group == null,
                        onClick = { onGroupClick(null) },
                    )
                    types.forEach { type ->
                        PmFilterChip(text = type.name, selected = picker.group == type.id, onClick = { onGroupClick(type.id) })
                    }
                    if (hasGeneral && types.isNotEmpty()) {
                        PmFilterChip(
                            text = stringResource(R.string.feature_orders_repair_general),
                            selected = picker.group == PricePicker.GENERAL,
                            onClick = { onGroupClick(PricePicker.GENERAL) },
                        )
                    }
                }
            }
        }
        if (picker.goods) {
            LazyColumn(Modifier.padding(top = 8.dp)) {
                items(goods, key = { it.id }) { item ->
                    PickRow(
                        name = item.title,
                        details = listOf(item.type.name, item.details).filter { it.isNotBlank() }.joinToString(" · "),
                        price = item.price,
                        quantity = goodsQuantities[item.id] ?: 0,
                        fits = item.id in fittingGoods,
                        inStock = goodsStock[item.id] ?: 0,
                        onClick = { onPickGoods(item) },
                    )
                    HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                }
            }
            return@ModalBottomSheet
        }
        LazyColumn(Modifier.padding(top = 8.dp)) {
            if (priceListIsEmpty) {
                item {
                    Text(
                        stringResource(R.string.feature_orders_repair_price_list_empty),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PmTheme.colors.inkMuted,
                    )
                }
            }
            items(items, key = { it.id }) { item ->
                PickRow(item.name, item.description, item.price, quantities[item.id] ?: 0, fits = false, onClick = { onPick(item) })
                HorizontalDivider(color = PmTheme.colors.surfaceMuted)
            }
            item {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onAddCustom).padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(PmIcons.Add, contentDescription = null, tint = PmTheme.colors.primary)
                    Text(
                        stringResource(R.string.feature_orders_repair_other_item_long),
                        style = MaterialTheme.typography.titleSmall,
                        color = PmTheme.colors.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PickRow(name: String, details: String, price: Money, quantity: Int, fits: Boolean, onClick: () -> Unit, inStock: Int? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (details.isNotBlank()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Goods: how many the warehouse has.
            inStock?.let {
                Text(
                    if (it > 0) stringResource(R.string.feature_orders_repair_in_stock, it) else stringResource(R.string.feature_orders_repair_not_in_stock),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it > 0) PmTheme.colors.inkSecondary else PmTheme.colors.debt,
                )
            }
            if (fits) PmTag(stringResource(R.string.feature_orders_repair_fits), TagTone.Paid)
        }
        AmountText(price, style = MaterialTheme.typography.titleSmall)
        // How many are already added; a plus when none.
        Box(
            Modifier.size(32.dp).background(
                if (quantity > 0) PmTheme.colors.primary else PmTheme.colors.primaryContainer,
                PmTheme.shapes.pill,
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (quantity > 0) {
                Text(quantity.toString(), style = MaterialTheme.typography.labelLarge, color = PmTheme.colors.onPrimary)
            } else {
                Icon(PmIcons.Add, contentDescription = null, tint = PmTheme.colors.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
