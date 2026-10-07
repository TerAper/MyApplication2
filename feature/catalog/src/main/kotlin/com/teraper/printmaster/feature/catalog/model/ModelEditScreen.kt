package com.teraper.printmaster.feature.catalog.model

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmConfirmDialog
import com.teraper.printmaster.core.designsystem.component.PmFilterChip
import com.teraper.printmaster.core.designsystem.component.PmMessageDialog
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmSegmentedButtons
import com.teraper.printmaster.core.designsystem.component.PmTextField
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.label
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.CartridgeDraft
import com.teraper.printmaster.core.model.CatalogNames
import com.teraper.printmaster.core.model.ColorType
import com.teraper.printmaster.core.model.ModelOwner
import com.teraper.printmaster.core.model.PrintType
import com.teraper.printmaster.core.model.PrinterDraftError
import com.teraper.printmaster.core.model.PrinterModelDraft
import com.teraper.printmaster.feature.catalog.R

@Composable
internal fun ModelEditRoute(
    onClose: () -> Unit,
    onOpenClient: (Long) -> Unit,
    viewModel: ModelEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ModelEditEvent.Close -> onClose()
            }
        }
    }
    BackHandler(onBack = viewModel::onCloseRequest)

    ModelEditScreen(
        state = state,
        actions = ModelEditActions(
            onClose = viewModel::onCloseRequest,
            onDelete = viewModel::onDeleteClick,
            onBrandChange = viewModel::onBrandChange,
            onNameChange = viewModel::onNameChange,
            onPrintTypeChange = viewModel::onPrintTypeChange,
            onColorTypeChange = viewModel::onColorTypeChange,
            onCartridgeChange = viewModel::onCartridgeChange,
            onRemoveCartridge = viewModel::onRemoveCartridge,
            onOpenClient = onOpenClient,
            onSave = viewModel::onSave,
            onConfirmDelete = viewModel::onConfirmDelete,
            onDiscardConfirmed = viewModel::onDiscardConfirmed,
            onDismissDialog = viewModel::onDismissDialog,
        ),
    )
}

internal data class ModelEditActions(
    val onClose: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onBrandChange: (String) -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onPrintTypeChange: (PrintType) -> Unit = {},
    val onColorTypeChange: (ColorType) -> Unit = {},
    val onCartridgeChange: (Int, CartridgeDraft) -> Unit = { _, _ -> },
    val onRemoveCartridge: (Int) -> Unit = {},
    val onOpenClient: (Long) -> Unit = {},
    val onSave: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDiscardConfirmed: () -> Unit = {},
    val onDismissDialog: () -> Unit = {},
)

@Composable
internal fun ModelEditScreen(
    state: ModelEditUiState,
    actions: ModelEditActions,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    val draft = form.draft
    Column(modifier.fillMaxSize().background(PmTheme.colors.background).imePadding()) {
        PmTopBar(
            title = stringResource(if (state.isNew) R.string.feature_catalog_new_model else R.string.feature_catalog_edit_model),
            navigationLabel = stringResource(R.string.feature_catalog_cancel),
            navigationIcon = PmIcons.Close,
            onNavigate = actions.onClose,
            actions = {
                if (!state.isNew) {
                    IconButton(onClick = actions.onDelete) {
                        Icon(PmIcons.Delete, contentDescription = stringResource(R.string.feature_catalog_delete))
                    }
                }
            },
        )
        if (form.isLoading) return@Column

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PmTextField(
                value = draft.brand,
                onValueChange = actions.onBrandChange,
                label = stringResource(R.string.feature_catalog_brand),
                error = if (PrinterDraftError.BRAND_REQUIRED in form.errors) stringResource(R.string.feature_catalog_error_brand) else null,
                capitalization = KeyboardCapitalization.Words,
            )
            if (state.brands.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.brands.forEach { brand ->
                        PmFilterChip(
                            text = brand,
                            selected = CatalogNames.key(brand) == CatalogNames.key(draft.brand),
                            onClick = { actions.onBrandChange(brand) },
                        )
                    }
                }
            }
            PmTextField(
                value = draft.name,
                onValueChange = actions.onNameChange,
                label = stringResource(R.string.feature_catalog_model),
                placeholder = stringResource(R.string.feature_catalog_model_hint),
                error = when {
                    form.nameTaken -> stringResource(R.string.feature_catalog_error_taken, draft.fullName)
                    PrinterDraftError.MODEL_REQUIRED in form.errors -> stringResource(R.string.feature_catalog_error_model)
                    else -> null
                },
                capitalization = KeyboardCapitalization.None,
            )
            PmSegmentedButtons(options = PrintType.entries, selected = draft.printType, onSelect = actions.onPrintTypeChange, label = { it.label() })
            PmSegmentedButtons(options = ColorType.entries, selected = draft.colorType, onSelect = actions.onColorTypeChange, label = { it.label() })

            SectionTitle(stringResource(R.string.feature_catalog_section_cartridges))
            Text(
                stringResource(R.string.feature_catalog_cartridges_help),
                style = MaterialTheme.typography.bodySmall,
                color = PmTheme.colors.inkMuted,
            )
            draft.cartridges.forEachIndexed { index, cartridge ->
                CartridgeRow(
                    cartridge = cartridge,
                    isLast = index == draft.cartridges.lastIndex,
                    onChange = { actions.onCartridgeChange(index, it) },
                    onRemove = { actions.onRemoveCartridge(index) },
                )
            }

            if (state.owners.isNotEmpty()) {
                SectionTitle(stringResource(R.string.feature_catalog_section_owners, state.owners.size))
                OwnersCard(state.owners, actions.onOpenClient)
            }
        }

        Column(Modifier.fillMaxWidth().background(PmTheme.colors.surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
            PmPrimaryButton(
                text = stringResource(R.string.feature_catalog_save),
                onClick = actions.onSave,
                enabled = !form.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    when (form.dialog) {
        ModelEditDialog.DISCARD -> PmConfirmDialog(
            title = stringResource(R.string.feature_catalog_discard_title),
            message = null,
            confirmText = stringResource(R.string.feature_catalog_discard),
            dismissText = stringResource(R.string.feature_catalog_keep_editing),
            onConfirm = actions.onDiscardConfirmed,
            onDismiss = actions.onDismissDialog,
            destructive = true,
        )
        ModelEditDialog.CONFIRM_DELETE -> PmConfirmDialog(
            title = stringResource(R.string.feature_catalog_delete_title),
            message = draft.fullName,
            confirmText = stringResource(R.string.feature_catalog_delete),
            dismissText = stringResource(R.string.feature_catalog_cancel),
            onConfirm = actions.onConfirmDelete,
            onDismiss = actions.onDismissDialog,
            destructive = true,
        )
        ModelEditDialog.IN_USE -> PmMessageDialog(
            title = stringResource(R.string.feature_catalog_in_use_title),
            message = stringResource(R.string.feature_catalog_in_use_message),
            okText = stringResource(R.string.feature_catalog_ok),
            onDismiss = actions.onDismissDialog,
        )
        null -> Unit
    }
}

@Composable
private fun CartridgeRow(cartridge: CartridgeDraft, isLast: Boolean, onChange: (CartridgeDraft) -> Unit, onRemove: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PmTextField(
            value = cartridge.name,
            onValueChange = { onChange(cartridge.copy(name = it)) },
            label = stringResource(if (isLast) R.string.feature_catalog_add_cartridge else R.string.feature_catalog_cartridge),
            capitalization = KeyboardCapitalization.Characters,
            modifier = Modifier.weight(1f),
        )
        PmTextField(
            value = cartridge.chips,
            onValueChange = { onChange(cartridge.copy(chips = it)) },
            label = stringResource(R.string.feature_catalog_chips),
            capitalization = KeyboardCapitalization.None,
            modifier = Modifier.weight(0.8f),
        )
        IconButton(onClick = onRemove, enabled = !isLast) {
            Icon(
                PmIcons.Close,
                contentDescription = stringResource(R.string.feature_catalog_remove_cartridge),
                tint = if (isLast) PmTheme.colors.surfaceMuted else PmTheme.colors.inkMuted,
            )
        }
    }
}

@Composable
private fun OwnersCard(owners: List<ModelOwner>, onOpenClient: (Long) -> Unit) {
    PmCard(Modifier.fillMaxWidth()) {
        owners.forEachIndexed { index, owner ->
            if (index > 0) HorizontalDivider(color = PmTheme.colors.surfaceMuted)
            Row(
                Modifier.fillMaxWidth().clickable { onOpenClient(owner.clientId) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(owner.clientName, style = MaterialTheme.typography.bodyLarge)
                    if (owner.location.isNotBlank()) {
                        Text(owner.location, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
                    }
                }
                Icon(PmIcons.Chevron, contentDescription = null, tint = PmTheme.colors.outlineStrong)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 8.dp, start = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = PmTheme.colors.inkMuted,
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 860)
@Composable
private fun ModelEditScreenPreview() {
    PmTheme {
        ModelEditScreen(
            state = ModelEditUiState(
                form = ModelForm(
                    draft = PrinterModelDraft(
                        id = 1, brand = "HP", name = "LaserJet M125",
                        cartridges = listOf(CartridgeDraft("CF283A", "83A chip"), CartridgeDraft()),
                    ),
                ),
                owners = listOf(ModelOwner(1, "«ԱԲԳ Սերվիս» ՍՊԸ", "Հաշվապահություն")),
                brands = listOf("Canon", "HP", "Samsung"),
            ),
            actions = ModelEditActions(),
        )
    }
}
