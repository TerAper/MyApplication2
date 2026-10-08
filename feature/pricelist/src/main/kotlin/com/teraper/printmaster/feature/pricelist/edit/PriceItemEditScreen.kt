package com.teraper.printmaster.feature.pricelist.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.AmountText
import com.teraper.printmaster.core.designsystem.component.AmountTone
import com.teraper.printmaster.core.designsystem.component.DRAM_SIGN
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmConfirmDialog
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmSegmentedButtons
import com.teraper.printmaster.core.designsystem.component.PmTextField
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.label
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.PriceItemDraft
import com.teraper.printmaster.core.model.PriceItemError
import com.teraper.printmaster.core.model.RepairCategory
import com.teraper.printmaster.feature.pricelist.R

@Composable
internal fun PriceItemEditRoute(onClose: () -> Unit, viewModel: PriceItemEditViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PriceItemEditEvent.Close -> onClose()
            }
        }
    }
    BackHandler(onBack = viewModel::onCloseRequest)

    PriceItemEditScreen(
        state = state,
        actions = PriceItemEditActions(
            onClose = viewModel::onCloseRequest,
            onDelete = viewModel::onDeleteClick,
            onCategoryChange = viewModel::onCategoryChange,
            onNameChange = viewModel::onNameChange,
            onDescriptionChange = viewModel::onDescriptionChange,
            onPriceChange = viewModel::onPriceChange,
            onCostChange = viewModel::onCostChange,
            onSave = viewModel::onSave,
            onConfirmDelete = viewModel::onConfirmDelete,
            onDiscardConfirmed = viewModel::onDiscardConfirmed,
            onDismissDialog = viewModel::onDismissDialog,
        ),
    )
}

internal data class PriceItemEditActions(
    val onClose: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onCategoryChange: (RepairCategory) -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onDescriptionChange: (String) -> Unit = {},
    val onPriceChange: (String) -> Unit = {},
    val onCostChange: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDiscardConfirmed: () -> Unit = {},
    val onDismissDialog: () -> Unit = {},
)

@Composable
internal fun PriceItemEditScreen(state: PriceItemEditUiState, actions: PriceItemEditActions, modifier: Modifier = Modifier) {
    val draft = state.draft
    Column(modifier.fillMaxSize().background(PmTheme.colors.background).imePadding()) {
        PmTopBar(
            title = stringResource(if (state.isNew) R.string.feature_pricelist_new_item else R.string.feature_pricelist_edit_item),
            navigationLabel = stringResource(R.string.feature_pricelist_cancel),
            navigationIcon = PmIcons.Close,
            onNavigate = actions.onClose,
            actions = {
                if (!state.isNew) {
                    IconButton(onClick = actions.onDelete) {
                        Icon(PmIcons.Delete, contentDescription = stringResource(R.string.feature_pricelist_delete))
                    }
                }
            },
        )
        if (state.isLoading) return@Column

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PmSegmentedButtons(
                options = RepairCategory.entries,
                selected = draft.category,
                onSelect = actions.onCategoryChange,
                label = { it.label() },
            )
            PmTextField(
                value = draft.name,
                onValueChange = actions.onNameChange,
                label = stringResource(R.string.feature_pricelist_name),
                placeholder = stringResource(R.string.feature_pricelist_name_hint),
                error = when {
                    state.nameTaken -> stringResource(R.string.feature_pricelist_error_taken, draft.category.label())
                    PriceItemError.NAME_REQUIRED in state.errors -> stringResource(R.string.feature_pricelist_error_name)
                    else -> null
                },
            )
            PmTextField(
                value = draft.description,
                onValueChange = actions.onDescriptionChange,
                label = stringResource(R.string.feature_pricelist_description),
                placeholder = stringResource(R.string.feature_pricelist_description_hint),
                singleLine = false,
                minLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PmTextField(
                    value = draft.priceDigits,
                    onValueChange = actions.onPriceChange,
                    label = stringResource(R.string.feature_pricelist_price),
                    error = if (PriceItemError.PRICE_REQUIRED in state.errors) stringResource(R.string.feature_pricelist_error_price) else null,
                    keyboardType = KeyboardType.Number,
                    trailingIcon = { Text(DRAM_SIGN) },
                    modifier = Modifier.weight(1f),
                )
                PmTextField(
                    value = draft.costDigits,
                    onValueChange = actions.onCostChange,
                    label = stringResource(R.string.feature_pricelist_cost),
                    keyboardType = KeyboardType.Number,
                    trailingIcon = { Text(DRAM_SIGN) },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                stringResource(R.string.feature_pricelist_cost_help),
                style = MaterialTheme.typography.bodySmall,
                color = PmTheme.colors.inkMuted,
            )
            if (draft.price.isPositive) ProfitCard(draft)
        }

        Column(Modifier.fillMaxWidth().background(PmTheme.colors.surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
            PmPrimaryButton(
                text = stringResource(R.string.feature_pricelist_save),
                onClick = actions.onSave,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    when (state.dialog) {
        PriceItemEditDialog.DISCARD -> PmConfirmDialog(
            title = stringResource(R.string.feature_pricelist_discard_title),
            message = null,
            confirmText = stringResource(R.string.feature_pricelist_discard),
            dismissText = stringResource(R.string.feature_pricelist_keep_editing),
            onConfirm = actions.onDiscardConfirmed,
            onDismiss = actions.onDismissDialog,
            destructive = true,
        )
        PriceItemEditDialog.CONFIRM_DELETE -> PmConfirmDialog(
            title = stringResource(R.string.feature_pricelist_delete_title),
            message = stringResource(R.string.feature_pricelist_delete_message),
            confirmText = stringResource(R.string.feature_pricelist_delete),
            dismissText = stringResource(R.string.feature_pricelist_cancel),
            onConfirm = actions.onConfirmDelete,
            onDismiss = actions.onDismissDialog,
            destructive = true,
        )
        null -> Unit
    }
}

/** Profit per item and its share of the price. */
@Composable
private fun ProfitCard(draft: PriceItemDraft) {
    val profit = draft.profit
    val percent = profit.minor * 100 / draft.price.minor
    PmCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.feature_pricelist_profit), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.feature_pricelist_margin, percent),
                    style = MaterialTheme.typography.bodySmall,
                    color = PmTheme.colors.inkMuted,
                )
            }
            AmountText(
                profit,
                tone = if (profit.isNegative) AmountTone.Debt else AmountTone.Paid,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun PriceItemEditScreenPreview() {
    PmTheme {
        PriceItemEditScreen(
            state = PriceItemEditUiState(
                draft = PriceItemDraft(1, RepairCategory.CARTRIDGE, "Լիցքավորում (սև)", "HP 85A, 83A", "3000", "900"),
            ),
            actions = PriceItemEditActions(),
        )
    }
}
