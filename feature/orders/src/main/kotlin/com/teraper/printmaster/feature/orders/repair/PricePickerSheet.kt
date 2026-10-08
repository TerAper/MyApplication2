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
import com.teraper.printmaster.core.designsystem.component.PmFilterChip
import com.teraper.printmaster.core.designsystem.component.PmSearchField
import com.teraper.printmaster.core.designsystem.component.label
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.PriceItem
import com.teraper.printmaster.core.model.RepairCategory
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
    onCategoryClick: (RepairCategory?) -> Unit,
    onQueryChange: (String) -> Unit,
    onPick: (PriceItem) -> Unit,
    onAddCustom: () -> Unit,
    onDismiss: () -> Unit,
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
            if (!priceListIsEmpty) {
                PmSearchField(
                    query = picker.query,
                    onQueryChange = onQueryChange,
                    placeholder = stringResource(R.string.feature_orders_repair_search_hint),
                    clearLabel = stringResource(R.string.feature_orders_repair_clear_search),
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PmFilterChip(
                        text = stringResource(R.string.feature_orders_repair_all),
                        selected = picker.category == null,
                        onClick = { onCategoryClick(null) },
                    )
                    RepairCategory.entries.forEach { category ->
                        PmFilterChip(text = category.label(), selected = picker.category == category, onClick = { onCategoryClick(category) })
                    }
                }
            }
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
                PickRow(item, quantities[item.id] ?: 0, onClick = { onPick(item) })
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
private fun PickRow(item: PriceItem, quantity: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.description.isNotBlank()) {
                Text(item.description, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        AmountText(item.price, style = MaterialTheme.typography.titleSmall)
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
