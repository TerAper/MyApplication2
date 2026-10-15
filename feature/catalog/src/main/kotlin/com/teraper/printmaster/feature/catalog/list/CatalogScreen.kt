package com.teraper.printmaster.feature.catalog.list

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
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.teraper.printmaster.core.designsystem.component.ScanOutcome
import com.teraper.printmaster.core.designsystem.component.rememberCodeScanner
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.AmountText
import com.teraper.printmaster.core.designsystem.component.LocalShowMoney
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmFilterChip
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmSearchField
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.CatalogEntry
import com.teraper.printmaster.core.model.CatalogItem
import com.teraper.printmaster.core.model.ItemField
import com.teraper.printmaster.core.model.ItemRef
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.feature.catalog.R

@Composable
internal fun CatalogRoute(
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    onAddItem: (typeId: Long) -> Unit,
    onOpenTypes: () -> Unit,
    onNewWithBarcode: (String) -> Unit = {},
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var newCode by remember { mutableStateOf<String?>(null) }
    val scan = rememberCodeScanner { outcome ->
        when (outcome) {
            is ScanOutcome.Read -> viewModel.onScanned(outcome.text)
            ScanOutcome.Unavailable -> Toast.makeText(context, R.string.feature_catalog_scan_unavailable, Toast.LENGTH_LONG).show()
            ScanOutcome.Closed -> Unit
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.scans.collect { found ->
            when (found) {
                is CatalogScan.Found -> onItemClick(found.itemId)
                is CatalogScan.New -> newCode = found.barcode
            }
        }
    }
    newCode?.let { code ->
        AlertDialog(
            onDismissRequest = { newCode = null },
            title = { Text(stringResource(R.string.feature_catalog_new_code_title)) },
            text = { Text(stringResource(R.string.feature_catalog_new_code_text, code)) },
            confirmButton = {
                TextButton(onClick = {
                    newCode = null
                    onNewWithBarcode(code)
                }) { Text(stringResource(R.string.feature_catalog_new_code_add)) }
            },
            dismissButton = { TextButton(onClick = { newCode = null }) { Text(stringResource(R.string.feature_catalog_cancel_scan)) } },
        )
    }
    CatalogScreen(
        onScan = scan,
        state = state,
        query = query,
        onQueryChange = viewModel::onQueryChange,
        onTypeFilter = viewModel::onTypeFilter,
        onBack = onBack,
        onItemClick = onItemClick,
        onAddItem = onAddItem,
        onOpenTypes = onOpenTypes,
    )
}

@Composable
internal fun CatalogScreen(
    state: CatalogUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onTypeFilter: (Long?) -> Unit,
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    onAddItem: (typeId: Long) -> Unit,
    onOpenTypes: () -> Unit,
    modifier: Modifier = Modifier,
    onScan: () -> Unit = {},
) {
    Box(modifier.fillMaxSize().background(PmTheme.colors.background)) {
        Column(Modifier.fillMaxSize()) {
            PmTopBar(
                title = stringResource(R.string.feature_catalog_title),
                navigationLabel = stringResource(R.string.feature_catalog_back),
                onNavigate = onBack,
                actions = {
                    // A product's barcode: opens its item, or adds a new one with it.
                    if (state.types.isNotEmpty()) {
                        IconButton(onClick = onScan) {
                            Icon(PmIcons.Scan, contentDescription = stringResource(R.string.feature_catalog_scan), tint = PmTheme.colors.primary)
                        }
                    }
                    // Types and templates: a labelled button, so it's found without guessing an icon.
                    TextButton(onClick = onOpenTypes) {
                        Icon(PmIcons.ItemTypes, contentDescription = null, tint = PmTheme.colors.primary, modifier = Modifier.padding(end = 6.dp))
                        Text(stringResource(R.string.feature_catalog_types), color = PmTheme.colors.primary, style = MaterialTheme.typography.labelLarge)
                    }
                },
            )
            if (state.isLoading) return@Column
            if (state.types.isEmpty()) {
                // Nothing to put in the catalog before the customer says what they work with.
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // The whole empty state (its big icon too) opens Types and templates.
                    PmEmptyState(
                        icon = PmIcons.ItemTypes,
                        title = stringResource(R.string.feature_catalog_no_types_title),
                        message = stringResource(R.string.feature_catalog_no_types_message),
                        modifier = Modifier.weight(1f).clickable(onClick = onOpenTypes),
                    )
                    PmPrimaryButton(
                        text = stringResource(R.string.feature_catalog_open_types),
                        onClick = onOpenTypes,
                        icon = PmIcons.ItemTypes,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                return@Column
            }
            if (state.totalCount > 0) {
                PmSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    placeholder = stringResource(R.string.feature_catalog_search_hint),
                    clearLabel = stringResource(R.string.feature_catalog_clear_search),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (state.types.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PmFilterChip(text = stringResource(R.string.feature_catalog_all), selected = state.typeFilter == null, onClick = { onTypeFilter(null) })
                    state.types.forEach { type ->
                        PmFilterChip(text = type.name, selected = state.typeFilter == type.id, onClick = { onTypeFilter(type.id) })
                    }
                }
            }
            when {
                state.totalCount == 0 -> PmEmptyState(
                    icon = PmIcons.Catalog,
                    title = stringResource(R.string.feature_catalog_empty_title),
                    message = stringResource(R.string.feature_catalog_empty_message),
                )
                state.groups.isEmpty() -> PmEmptyState(
                    icon = PmIcons.Search,
                    title = stringResource(R.string.feature_catalog_no_results),
                    message = "",
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    state.groups.forEach { group ->
                        item(key = "type-${group.type.id}") {
                            Text(
                                group.type.name.uppercase(),
                                modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = PmTheme.colors.inkMuted,
                            )
                        }
                        items(group.items, key = { it.item.id }) { entry ->
                            ItemRow(entry, onClick = { onItemClick(entry.item.id) })
                            HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                        }
                    }
                }
            }
        }

        if (state.types.isNotEmpty()) {
            val filterType = state.types.firstOrNull { it.id == state.typeFilter }
            ExtendedFloatingActionButton(
                onClick = { onAddItem(filterType?.id ?: 0) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = PmTheme.colors.primary,
                contentColor = PmTheme.colors.onPrimary,
                icon = { Icon(PmIcons.Add, contentDescription = null) },
                text = {
                    Text(filterType?.let { stringResource(R.string.feature_catalog_new_item_of, it.name) } ?: stringResource(R.string.feature_catalog_new_item))
                },
            )
        }
    }
}

@Composable
private fun ItemRow(entry: CatalogEntry, onClick: () -> Unit) {
    val item = entry.item
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
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = buildList {
                item.details.takeIf { it.isNotEmpty() }?.let(::add)
                if (entry.ownedCount > 0) add(pluralStringResource(R.plurals.feature_catalog_owned_count, entry.ownedCount, entry.ownedCount))
            }
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
            }
            val links = (item.parts + item.fitsInto).joinToString(", ") { it.title }
            if (links.isNotEmpty()) {
                Text(links, style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (item.isGoods && LocalShowMoney.current) AmountText(item.price, style = MaterialTheme.typography.bodyMedium)
        Icon(PmIcons.Chevron, contentDescription = null, tint = PmTheme.colors.outlineStrong)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun CatalogScreenPreview() {
    val printer = ItemType(1, "Տպիչ", fields = listOf(ItemField(1, 1, "Բրենդ", inTitle = true), ItemField(2, 1, "Տպման տեսակ")))
    val cartridge = ItemType(2, "Քարտրիջ", 1)
    fun model(id: Long, brand: String, name: String, vararg parts: String) = CatalogItem(
        id, printer, name, mapOf(1L to brand, 2L to "Լազերային"),
        parts = parts.mapIndexed { i, p -> ItemRef(100L + i, 2, "Քարտրիջ", p) },
    )
    PmTheme {
        CatalogScreen(
            state = CatalogUiState(
                isLoading = false,
                types = listOf(printer, cartridge),
                totalCount = 3,
                groups = listOf(
                    TypeGroup(printer, listOf(CatalogEntry(model(1, "Canon", "i-SENSYS MF3010", "725"), 4), CatalogEntry(model(2, "HP", "LaserJet M125", "CF283A", "CF283X"), 2))),
                    TypeGroup(cartridge, listOf(CatalogEntry(CatalogItem(3, cartridge, "CF283A")))),
                ),
            ),
            query = "",
            onQueryChange = {},
            onTypeFilter = {},
            onBack = {},
            onItemClick = {},
            onAddItem = {},
            onOpenTypes = {},
        )
    }
}
