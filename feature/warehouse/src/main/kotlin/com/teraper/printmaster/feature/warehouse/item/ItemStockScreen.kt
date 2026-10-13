package com.teraper.printmaster.feature.warehouse.item

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.LocalShowMoney
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmFilterChip
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.component.PmTag
import com.teraper.printmaster.core.designsystem.component.PmTextField
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.TagTone
import com.teraper.printmaster.core.designsystem.component.format
import com.teraper.printmaster.core.designsystem.component.formatShort
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.AdjustReason
import com.teraper.printmaster.core.model.CatalogItem
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.StockDraftError
import com.teraper.printmaster.core.model.StockKind
import com.teraper.printmaster.core.model.StockMove
import com.teraper.printmaster.feature.warehouse.R
import java.time.Instant
import java.time.ZoneId

@Composable
internal fun ItemStockRoute(onBack: () -> Unit, onOpenCatalogItem: (Long) -> Unit, viewModel: ItemStockViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ItemStockScreen(
        state = state,
        onBack = onBack,
        onOpenCatalogItem = onOpenCatalogItem,
        onReceive = viewModel::onReceive,
        onAdjust = viewModel::onAdjust,
        onMove = viewModel::onMove,
    )
    if (state.draft != null) StockSheet(state, viewModel)
}

@Composable
internal fun ItemStockScreen(
    state: ItemStockUiState,
    onBack: () -> Unit,
    onOpenCatalogItem: (Long) -> Unit = {},
    onReceive: () -> Unit = {},
    onAdjust: () -> Unit = {},
    onMove: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val item = state.item
    Column(modifier.fillMaxSize().background(PmTheme.colors.background)) {
        PmTopBar(
            title = item?.title ?: stringResource(R.string.feature_warehouse_title),
            navigationLabel = stringResource(R.string.feature_warehouse_back),
            onNavigate = onBack,
        )
        if (state.isLoading || item == null) return@Column
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                PmCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.feature_warehouse_count, state.count),
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (state.count <= 0) PmTheme.colors.error else PmTheme.colors.ink,
                        )
                        Text(listOf(item.type.name, item.details).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
                        if (item.keepAtLeast > 0) {
                            Text(stringResource(R.string.feature_warehouse_keep, item.keepAtLeast), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
                        }
                        if (state.isLow) PmTag(stringResource(R.string.feature_warehouse_buy, item.keepAtLeast - state.count.coerceAtLeast(0)), TagTone.Warning)
                        TextButton(onClick = { onOpenCatalogItem(item.id) }, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(R.string.feature_warehouse_open_catalog), color = PmTheme.colors.primary)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PmPrimaryButton(stringResource(R.string.feature_warehouse_receive), onReceive, Modifier.weight(1f), icon = PmIcons.Add)
                    PmSecondaryButton(stringResource(R.string.feature_warehouse_adjust), onAdjust, Modifier.weight(1f), icon = PmIcons.Edit)
                }
            }
            if (state.moveTargets.isNotEmpty()) {
                item { PmSecondaryButton(stringResource(R.string.feature_warehouse_move), onMove, Modifier.fillMaxWidth(), icon = PmIcons.Company) }
            }
            item {
                Text(stringResource(R.string.feature_warehouse_history), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted)
            }
            if (state.moves.isEmpty()) {
                item { Text(stringResource(R.string.feature_warehouse_no_history), style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.inkMuted) }
            } else {
                item {
                    PmCard(Modifier.fillMaxWidth()) {
                        state.moves.forEachIndexed { index, move ->
                            if (index > 0) HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                            MoveRow(move, state.companyNames)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveRow(move: StockMove, companyNames: Map<Long, String>) {
    val showMoney = LocalShowMoney.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val other = move.otherCompanyId?.let { companyNames[it] }.orEmpty()
            Text(
                when (move.kind) {
                    StockKind.RECEIVE -> stringResource(R.string.feature_warehouse_kind_receive)
                    StockKind.ADJUST -> stringResource(R.string.feature_warehouse_kind_adjust) + (move.reason?.let { " · " + it.label() } ?: "")
                    StockKind.ORDER -> stringResource(R.string.feature_warehouse_kind_order) + (move.orderId?.let { " #$it" } ?: "")
                    StockKind.MOVE -> if (move.quantity > 0) stringResource(R.string.feature_warehouse_kind_move_in, other) else stringResource(R.string.feature_warehouse_kind_move_out, other)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            val details = listOfNotNull(
                Instant.ofEpochMilli(move.at).atZone(ZoneId.systemDefault()).toLocalDate().formatShort(),
                move.unitCost.takeIf { showMoney && it.isPositive }?.let { stringResource(R.string.feature_warehouse_paid_each, it.format()) },
                move.note.takeIf { it.isNotBlank() },
            )
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
        }
        Text(
            (if (move.quantity > 0) "+" else "") + move.quantity,
            style = MaterialTheme.typography.titleMedium,
            color = if (move.quantity > 0) PmTheme.colors.paid else PmTheme.colors.debt,
        )
    }
}

@Composable
private fun AdjustReason.label(): String = stringResource(
    when (this) {
        AdjustReason.RECOUNT -> R.string.feature_warehouse_reason_recount
        AdjustReason.LOST -> R.string.feature_warehouse_reason_lost
        AdjustReason.BROKEN -> R.string.feature_warehouse_reason_broken
        AdjustReason.RETURNED -> R.string.feature_warehouse_reason_returned
        AdjustReason.OTHER -> R.string.feature_warehouse_reason_other
    },
)

/** Receive (how many, paid each), adjust (the counted number, why) or move (how many, where). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockSheet(state: ItemStockUiState, viewModel: ItemStockViewModel) {
    val draft = state.draft ?: return
    val errors = state.errors
    ModalBottomSheet(
        onDismissRequest = viewModel::onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PmTheme.colors.background,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(
                    when (draft.kind) {
                        StockKind.RECEIVE -> R.string.feature_warehouse_receive
                        StockKind.ADJUST -> R.string.feature_warehouse_adjust
                        else -> R.string.feature_warehouse_move
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            PmTextField(
                value = draft.quantityDigits,
                onValueChange = viewModel::onQuantityChange,
                label = stringResource(if (draft.kind == StockKind.ADJUST) R.string.feature_warehouse_new_count else R.string.feature_warehouse_quantity),
                keyboardType = KeyboardType.Number,
                error = when {
                    StockDraftError.QUANTITY_REQUIRED in errors -> stringResource(R.string.feature_warehouse_error_quantity)
                    StockDraftError.NO_CHANGE in errors -> stringResource(R.string.feature_warehouse_error_no_change)
                    StockDraftError.NOT_ENOUGH in errors -> stringResource(R.string.feature_warehouse_error_not_enough)
                    else -> null
                },
            )
            when (draft.kind) {
                StockKind.RECEIVE -> if (LocalShowMoney.current) {
                    PmTextField(draft.costDigits, viewModel::onCostChange, stringResource(R.string.feature_warehouse_unit_cost), keyboardType = KeyboardType.Number)
                }
                StockKind.ADJUST -> {
                    Text(stringResource(R.string.feature_warehouse_reason), style = MaterialTheme.typography.labelMedium, color = PmTheme.colors.inkSecondary)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdjustReason.entries.forEach { reason ->
                            PmFilterChip(text = reason.label(), selected = draft.reason == reason, onClick = { viewModel.onReasonChange(reason) })
                        }
                    }
                }
                StockKind.MOVE -> {
                    Text(stringResource(R.string.feature_warehouse_to_company), style = MaterialTheme.typography.labelMedium, color = PmTheme.colors.inkSecondary)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.moveTargets.forEach { company ->
                            PmFilterChip(text = company.name, selected = draft.toCompanyId == company.id, onClick = { viewModel.onTargetChange(company.id) })
                        }
                    }
                    if (StockDraftError.COMPANY_REQUIRED in errors) {
                        Text(stringResource(R.string.feature_warehouse_error_company), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.error)
                    }
                }
                StockKind.ORDER -> Unit
            }
            PmTextField(draft.note, viewModel::onNoteChange, stringResource(R.string.feature_warehouse_note))
            PmPrimaryButton(stringResource(R.string.feature_warehouse_save), viewModel::onSave, Modifier.fillMaxWidth())
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun ItemStockPreview() {
    val cartridge = ItemType(2, "Քարտրիջ")
    PmTheme {
        ItemStockScreen(
            state = ItemStockUiState(
                isLoading = false,
                item = CatalogItem(1, cartridge, "CF283A", cost = Money.ofDram(7_000), keepAtLeast = 4),
                count = 2,
                moves = listOf(
                    StockMove(1, 1, 5, StockKind.RECEIVE, 1_790_000_000_000, Money.ofDram(7_000), note = "Supplier"),
                    StockMove(2, 1, -3, StockKind.ADJUST, 1_790_100_000_000, reason = AdjustReason.BROKEN),
                ),
            ),
            onBack = {},
        )
    }
}
