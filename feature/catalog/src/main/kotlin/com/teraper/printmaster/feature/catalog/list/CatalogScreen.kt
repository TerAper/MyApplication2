package com.teraper.printmaster.feature.catalog.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmSearchField
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.typeLabel
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Cartridge
import com.teraper.printmaster.core.model.CatalogModel
import com.teraper.printmaster.core.model.ColorType
import com.teraper.printmaster.core.model.PrintType
import com.teraper.printmaster.core.model.PrinterModel
import com.teraper.printmaster.feature.catalog.R

@Composable
internal fun CatalogRoute(
    onBack: () -> Unit,
    onModelClick: (Long) -> Unit,
    onAddModel: () -> Unit,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    CatalogScreen(
        state = state,
        query = query,
        onQueryChange = viewModel::onQueryChange,
        onBack = onBack,
        onModelClick = onModelClick,
        onAddModel = onAddModel,
    )
}

@Composable
internal fun CatalogScreen(
    state: CatalogUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onModelClick: (Long) -> Unit,
    onAddModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(PmTheme.colors.background)) {
        Column(Modifier.fillMaxSize()) {
            PmTopBar(
                title = stringResource(R.string.feature_catalog_title),
                navigationLabel = stringResource(R.string.feature_catalog_back),
                onNavigate = onBack,
            )
            if (state.totalCount > 0) {
                PmSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    placeholder = stringResource(R.string.feature_catalog_search_hint),
                    clearLabel = stringResource(R.string.feature_catalog_clear_search),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            when {
                state.isLoading -> Unit
                state.totalCount == 0 -> PmEmptyState(
                    icon = PmIcons.Printer,
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
                        item(key = "brand-${group.brand}") {
                            Text(
                                group.brand.uppercase(),
                                modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = PmTheme.colors.inkMuted,
                            )
                        }
                        items(group.models, key = { it.model.id }) { entry ->
                            ModelRow(entry, onClick = { onModelClick(entry.model.id) })
                            HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAddModel,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = PmTheme.colors.primary,
            contentColor = PmTheme.colors.onPrimary,
            icon = { Icon(PmIcons.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.feature_catalog_new_model)) },
        )
    }
}

@Composable
private fun ModelRow(entry: CatalogModel, onClick: () -> Unit) {
    val model = entry.model
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
            Text(model.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildList {
                    add(model.typeLabel())
                    if (entry.printerCount > 0) add(pluralStringResource(R.plurals.feature_catalog_printer_count, entry.printerCount, entry.printerCount))
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = PmTheme.colors.inkMuted,
            )
            if (model.cartridges.isNotEmpty()) {
                Text(
                    model.cartridges.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                    color = PmTheme.colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(PmIcons.Chevron, contentDescription = null, tint = PmTheme.colors.outlineStrong)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun CatalogScreenPreview() {
    fun model(id: Long, brand: String, name: String, vararg cartridges: String) =
        PrinterModel(id, brand.length.toLong(), brand, name, PrintType.LASER, ColorType.MONO, cartridges.mapIndexed { i, c -> Cartridge(i.toLong(), c) })
    PmTheme {
        CatalogScreen(
            state = CatalogUiState(
                isLoading = false,
                totalCount = 3,
                groups = listOf(
                    BrandGroup("Canon", listOf(CatalogModel(model(1, "Canon", "i-SENSYS MF3010", "725"), 4))),
                    BrandGroup(
                        "HP",
                        listOf(
                            CatalogModel(model(2, "HP", "LaserJet M125", "CF283A", "CF283X"), 2),
                            CatalogModel(model(3, "HP", "LaserJet P1102", "CE285A")),
                        ),
                    ),
                ),
            ),
            query = "",
            onQueryChange = {},
            onBack = {},
            onModelClick = {},
            onAddModel = {},
        )
    }
}
