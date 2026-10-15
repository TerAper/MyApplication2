package com.teraper.printmaster.feature.orders.repair

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.AmountText
import com.teraper.printmaster.core.designsystem.component.AmountTone
import com.teraper.printmaster.core.designsystem.component.DRAM_SIGN
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmConfirmDialog
import com.teraper.printmaster.core.designsystem.component.PmFilterChip
import com.teraper.printmaster.core.designsystem.component.PmMessageDialog
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.component.PmTextField
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.format
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.CatalogItem
import com.teraper.printmaster.core.model.ClientItem
import com.teraper.printmaster.core.model.ClientItemPart
import com.teraper.printmaster.core.model.ItemField
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.RepairDraft
import com.teraper.printmaster.core.model.RepairDraftError
import com.teraper.printmaster.core.model.RepairLine
import com.teraper.printmaster.core.model.PriceItem
import com.teraper.printmaster.feature.orders.R

@Composable
internal fun RepairEditRoute(onClose: () -> Unit, viewModel: RepairEditViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RepairEditEvent.Close -> onClose()
            }
        }
    }
    BackHandler(onBack = viewModel::onCloseRequest)

    RepairEditScreen(
        state = state,
        actions = RepairEditActions(
            onClose = viewModel::onCloseRequest,
            onDelete = viewModel::onDeleteClick,
            onDeviceClick = viewModel::onDeviceClick,
            onNoteChange = viewModel::onNoteChange,
            onQuantityChange = viewModel::onQuantityChange,
            onToBuyChange = viewModel::onToBuyChange,
            onLinePriceClick = viewModel::onLinePriceClick,
            onLinePriceChange = viewModel::onLinePriceChange,
            onOpenPicker = viewModel::onOpenPicker,
            onPickerGroupClick = viewModel::onPickerGroupClick,
            onPickerQueryChange = viewModel::onPickerQueryChange,
            onPickItem = viewModel::onPickItem,
            onPickGoods = viewModel::onPickGoods,
            onPickerGoods = viewModel::onPickerGoods,
            onClosePicker = viewModel::onClosePicker,
            onAddCustomClick = viewModel::onAddCustomClick,
            onAddCustomLine = viewModel::onAddCustomLine,
            onSave = viewModel::onSave,
            onConfirmDelete = viewModel::onConfirmDelete,
            onDiscardConfirmed = viewModel::onDiscardConfirmed,
            onDismissDialog = viewModel::onDismissDialog,
        ),
    )
}

internal data class RepairEditActions(
    val onClose: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onDeviceClick: (Long?, Long?) -> Unit = { _, _ -> },
    val onNoteChange: (String) -> Unit = {},
    val onQuantityChange: (Int, Int) -> Unit = { _, _ -> },
    val onToBuyChange: (Int, Boolean) -> Unit = { _, _ -> },
    val onLinePriceClick: (Int) -> Unit = {},
    val onLinePriceChange: (Int, String) -> Unit = { _, _ -> },
    val onOpenPicker: () -> Unit = {},
    val onPickerGroupClick: (Long?) -> Unit = {},
    val onPickerQueryChange: (String) -> Unit = {},
    val onPickItem: (PriceItem) -> Unit = {},
    val onPickGoods: (CatalogItem) -> Unit = {},
    val onPickerGoods: (Boolean) -> Unit = {},
    val onClosePicker: () -> Unit = {},
    val onAddCustomClick: () -> Unit = {},
    val onAddCustomLine: (String, String, String) -> Unit = { _, _, _ -> },
    val onSave: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDiscardConfirmed: () -> Unit = {},
    val onDismissDialog: () -> Unit = {},
)

@Composable
internal fun RepairEditScreen(state: RepairEditUiState, actions: RepairEditActions, modifier: Modifier = Modifier) {
    val form = state.form
    val draft = form.draft
    Column(modifier.fillMaxSize().background(PmTheme.colors.background).imePadding()) {
        PmTopBar(
            title = stringResource(if (state.isNew) R.string.feature_orders_repair_new else R.string.feature_orders_repair_edit),
            navigationLabel = stringResource(R.string.feature_orders_cancel),
            navigationIcon = PmIcons.Close,
            onNavigate = actions.onClose,
            actions = {
                if (!state.isNew) {
                    IconButton(onClick = actions.onDelete) {
                        Icon(PmIcons.Delete, contentDescription = stringResource(R.string.feature_orders_delete))
                    }
                }
            },
        )
        if (form.isLoading) return@Column

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.feature_orders_repair_section_device))
            DeviceCard(state.items, draft, actions.onDeviceClick)

            SectionTitle(stringResource(R.string.feature_orders_repair_section_work))
            if (draft.lines.isNotEmpty()) {
                PmCard(Modifier.fillMaxWidth()) {
                    draft.lines.forEachIndexed { index, line ->
                        if (index > 0) HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                        LineRow(
                            line = line,
                            onQuantityChange = { actions.onQuantityChange(index, it) },
                            onPriceClick = { actions.onLinePriceClick(index) },
                        )
                        if (line.itemId != null) {
                            GoodsStockRow(line, state.goodsStock[line.itemId] ?: 0, onToBuyChange = { actions.onToBuyChange(index, it) })
                        }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.feature_orders_repair_no_lines),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (RepairDraftError.LINES_REQUIRED in form.errors) PmTheme.colors.debt else PmTheme.colors.inkMuted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PmPrimaryButton(
                    text = stringResource(R.string.feature_orders_repair_from_price_list),
                    onClick = actions.onOpenPicker,
                    icon = PmIcons.PriceList,
                    modifier = Modifier.weight(1f),
                )
                PmSecondaryButton(
                    text = stringResource(R.string.feature_orders_repair_other_item),
                    onClick = actions.onAddCustomClick,
                    icon = PmIcons.Add,
                    modifier = Modifier.weight(1f),
                )
            }

            PmTextField(
                value = draft.note,
                onValueChange = actions.onNoteChange,
                label = stringResource(R.string.feature_orders_repair_note),
                placeholder = stringResource(R.string.feature_orders_repair_note_hint),
                singleLine = false,
                minLines = 2,
            )
        }

        Column(
            Modifier.fillMaxWidth().background(PmTheme.colors.surface).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.feature_orders_work_total), style = MaterialTheme.typography.titleSmall)
                    if (!draft.cost.isZero) {
                        Text(
                            stringResource(R.string.feature_orders_work_profit_short, draft.profit.format()),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (draft.profit.isNegative) PmTheme.colors.debt else PmTheme.colors.paid,
                        )
                    }
                }
                AmountText(draft.total, style = MaterialTheme.typography.titleLarge)
            }
            PmPrimaryButton(
                text = stringResource(R.string.feature_orders_save),
                onClick = actions.onSave,
                enabled = !form.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    form.picker?.let { picker ->
        PricePickerSheet(
            picker = picker,
            items = state.pickerItems,
            priceListIsEmpty = state.priceListIsEmpty,
            quantities = draft.quantities(),
            types = state.pickerTypes,
            hasGeneral = state.hasGeneralPrices,
            onGroupClick = actions.onPickerGroupClick,
            onQueryChange = actions.onPickerQueryChange,
            onPick = actions.onPickItem,
            onAddCustom = actions.onAddCustomClick,
            onDismiss = actions.onClosePicker,
            goodsAvailable = state.goodsAvailable,
            goods = state.pickerGoods,
            fittingGoods = state.fittingGoods,
            goodsQuantities = draft.goodsQuantities(),
            goodsStock = state.goodsStock,
            onGoodsMode = actions.onPickerGoods,
            onPickGoods = actions.onPickGoods,
        )
    }

    when (val dialog = form.dialog) {
        RepairEditDialog.Discard -> PmConfirmDialog(
            title = stringResource(R.string.feature_orders_discard_title),
            message = null,
            confirmText = stringResource(R.string.feature_orders_discard),
            dismissText = stringResource(R.string.feature_orders_keep_editing),
            onConfirm = actions.onDiscardConfirmed,
            onDismiss = actions.onDismissDialog,
            destructive = true,
        )
        RepairEditDialog.ConfirmDelete -> PmConfirmDialog(
            title = stringResource(R.string.feature_orders_repair_delete_title),
            message = null,
            confirmText = stringResource(R.string.feature_orders_delete),
            dismissText = stringResource(R.string.feature_orders_cancel),
            onConfirm = actions.onConfirmDelete,
            onDismiss = actions.onDismissDialog,
            destructive = true,
        )
        RepairEditDialog.Locked -> PmMessageDialog(
            title = stringResource(R.string.feature_orders_repair_locked_title),
            message = stringResource(R.string.feature_orders_repair_locked_message),
            okText = stringResource(R.string.feature_orders_ok),
            onDismiss = actions.onDismissDialog,
        )
        RepairEditDialog.CustomLine -> CustomLineDialog(onAdd = actions.onAddCustomLine, onDismiss = actions.onDismissDialog)
        is RepairEditDialog.LinePrice -> draft.lines.getOrNull(dialog.index)?.let { line ->
            LinePriceDialog(
                line = line,
                onConfirm = { actions.onLinePriceChange(dialog.index, it) },
                onDismiss = actions.onDismissDialog,
            )
        }
        null -> Unit
    }
}

/** The client's items; picking one shows its parts to pick from. */
@Composable
private fun DeviceCard(items: List<ClientItem>, draft: RepairDraft, onDeviceClick: (Long?, Long?) -> Unit) {
    PmCard(Modifier.fillMaxWidth()) {
        items.forEach { item ->
            val selected = draft.itemId == item.id
            ChoiceRow(
                title = item.title,
                subtitle = listOf(item.type.name, item.place).filter { it.isNotBlank() }.joinToString(" · "),
                selected = selected,
                onClick = { onDeviceClick(item.id, null) },
            )
            if (selected && item.parts.isNotEmpty()) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(start = 50.dp, end = 14.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item.parts.forEach { part ->
                        PmFilterChip(
                            text = part.title,
                            selected = draft.itemPartId == part.id,
                            onClick = { onDeviceClick(item.id, part.id) },
                        )
                    }
                }
            }
            HorizontalDivider(color = PmTheme.colors.surfaceMuted)
        }
        ChoiceRow(
            title = stringResource(R.string.feature_orders_repair_no_device),
            subtitle = if (items.isEmpty()) stringResource(R.string.feature_orders_repair_no_items) else "",
            selected = draft.itemId == null,
            onClick = { onDeviceClick(null, null) },
        )
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (selected) PmIcons.Check else PmIcons.Item,
            contentDescription = null,
            tint = if (selected) PmTheme.colors.primary else PmTheme.colors.outlineStrong,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) PmTheme.colors.primary else PmTheme.colors.ink,
            )
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
            }
        }
    }
}

/** "Refill 85A / 3 000 ֏ × 2  [−] 2 [+]  6 000 ֏"; tapping the price changes it for this job. */
@Composable
private fun LineRow(line: RepairLine, onQuantityChange: (Int) -> Unit, onPriceClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(line.name, style = MaterialTheme.typography.titleSmall)
            Text(
                line.price.format(),
                modifier = Modifier.clickable(onClick = onPriceClick).padding(vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
                color = PmTheme.colors.primary,
            )
        }
        IconButton(onClick = { onQuantityChange(line.quantity - 1) }) {
            Icon(
                if (line.quantity > 1) PmIcons.Remove else PmIcons.Delete,
                contentDescription = stringResource(R.string.feature_orders_repair_less),
                tint = PmTheme.colors.inkMuted,
            )
        }
        Text(
            line.quantity.toString(),
            modifier = Modifier.widthIn(min = 20.dp),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = { onQuantityChange(line.quantity + 1) }) {
            Icon(PmIcons.Add, contentDescription = stringResource(R.string.feature_orders_repair_more), tint = PmTheme.colors.primary)
        }
        AmountText(line.total, modifier = Modifier.widthIn(min = 76.dp), style = MaterialTheme.typography.titleSmall)
    }
}

/** Under a goods line: how many the warehouse has, and whether it must be bought first. */
@Composable
private fun GoodsStockRow(line: RepairLine, inStock: Int, onToBuyChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (inStock > 0) stringResource(R.string.feature_orders_repair_in_stock, inStock) else stringResource(R.string.feature_orders_repair_not_in_stock),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = if (inStock < line.quantity) PmTheme.colors.debt else PmTheme.colors.inkMuted,
        )
        PmFilterChip(
            text = stringResource(R.string.feature_orders_repair_to_buy),
            selected = line.toBuy,
            onClick = { onToBuyChange(!line.toBuy) },
        )
    }
}

@Composable
private fun CustomLineDialog(onAdd: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_orders_repair_other_item)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PmTextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.feature_orders_repair_item_name))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PmTextField(
                        value = price,
                        onValueChange = { price = it.filter(Char::isDigit) },
                        label = stringResource(R.string.feature_orders_repair_item_price),
                        keyboardType = KeyboardType.Number,
                        trailingIcon = { Text(DRAM_SIGN) },
                        modifier = Modifier.weight(1f),
                    )
                    PmTextField(
                        value = cost,
                        onValueChange = { cost = it.filter(Char::isDigit) },
                        label = stringResource(R.string.feature_orders_repair_item_cost),
                        keyboardType = KeyboardType.Number,
                        trailingIcon = { Text(DRAM_SIGN) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, price, cost) },
                enabled = name.isNotBlank() && (price.toLongOrNull() ?: 0) > 0,
            ) { Text(stringResource(R.string.feature_orders_repair_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_orders_cancel)) } },
        containerColor = PmTheme.colors.surface,
    )
}

@Composable
private fun LinePriceDialog(line: RepairLine, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var price by remember { mutableStateOf(line.price.dram.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(line.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PmTextField(
                    value = price,
                    onValueChange = { price = it.filter(Char::isDigit) },
                    label = stringResource(R.string.feature_orders_repair_item_price),
                    keyboardType = KeyboardType.Number,
                    trailingIcon = { Text(DRAM_SIGN) },
                )
                Text(
                    stringResource(R.string.feature_orders_repair_price_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = PmTheme.colors.inkMuted,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(price) }, enabled = (price.toLongOrNull() ?: 0) > 0) {
                Text(stringResource(R.string.feature_orders_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_orders_cancel)) } },
        containerColor = PmTheme.colors.surface,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 4.dp, start = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = PmTheme.colors.inkMuted,
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 860)
@Composable
private fun RepairEditScreenPreview() {
    val printer = ItemType(1, "Տպիչ", fields = listOf(ItemField(1, 1, "Բրենդ", inTitle = true)))
    val cartridge = CatalogItem(3, ItemType(2, "Քարտրիջ", 1), "CF283A")
    val items = listOf(
        ClientItem(1, 1, CatalogItem(1, printer, "LaserJet M125", mapOf(1L to "HP")), "Հաշվապահություն", parts = listOf(ClientItemPart(11, cartridge))),
        ClientItem(2, 1, CatalogItem(2, printer, "MF3010", mapOf(1L to "Canon")), "Տնօրեն"),
    )
    PmTheme {
        RepairEditScreen(
            state = RepairEditUiState(
                form = RepairForm(
                    isLoading = false,
                    draft = RepairDraft(
                        orderId = 1,
                        itemId = 1,
                        itemPartId = 11,
                        lines = listOf(
                            RepairLine(1, "Լիցքավորում 85A", Money.ofDram(3_000), Money.ofDram(900), 2),
                            RepairLine(2, "Չիպ", Money.ofDram(2_500), Money.ofDram(1_200)),
                        ),
                    ),
                ),
                items = items,
            ),
            actions = RepairEditActions(),
        )
    }
}
