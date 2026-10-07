package com.teraper.printmaster.feature.clients.printer

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmConfirmDialog
import com.teraper.printmaster.core.designsystem.component.PmFilterChip
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.component.PmSegmentedButtons
import com.teraper.printmaster.core.designsystem.component.PmTextField
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.label
import com.teraper.printmaster.core.designsystem.component.typeLabel
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.CartridgeDraft
import com.teraper.printmaster.core.model.Cartridge
import com.teraper.printmaster.core.model.CatalogNames
import com.teraper.printmaster.core.model.ClientPrinterDraft
import com.teraper.printmaster.core.model.ColorType
import com.teraper.printmaster.core.model.PrintType
import com.teraper.printmaster.core.model.PrinterDraftError
import com.teraper.printmaster.core.model.PrinterModel
import com.teraper.printmaster.core.model.PrinterModelDraft
import com.teraper.printmaster.feature.clients.R

@Composable
internal fun PrinterEditRoute(
    onClose: () -> Unit,
    viewModel: PrinterEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PrinterEditEvent.Close -> onClose()
            }
        }
    }
    BackHandler(onBack = viewModel::onCloseRequest)

    PrinterEditScreen(
        state = state,
        actions = PrinterEditActions(
            onClose = viewModel::onCloseRequest,
            onDelete = viewModel::onDeleteClick,
            onModelQueryChange = viewModel::onModelQueryChange,
            onModelPicked = viewModel::onModelPicked,
            onStartNewModel = viewModel::onStartNewModel,
            onChangeModel = viewModel::onChangeModel,
            onBrandChange = viewModel::onBrandChange,
            onModelNameChange = viewModel::onModelNameChange,
            onPrintTypeChange = viewModel::onPrintTypeChange,
            onColorTypeChange = viewModel::onColorTypeChange,
            onCartridgeToggle = viewModel::onCartridgeToggle,
            onNewCartridgeChange = viewModel::onNewCartridgeChange,
            onAddCartridge = viewModel::onAddCartridge,
            onLocationChange = viewModel::onLocationChange,
            onNoteChange = viewModel::onNoteChange,
            onSave = viewModel::onSave,
            onConfirmDelete = viewModel::onConfirmDelete,
            onDiscardConfirmed = viewModel::onDiscardConfirmed,
            onDismissDialog = viewModel::onDismissDialog,
        ),
    )
}

internal data class PrinterEditActions(
    val onClose: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onModelQueryChange: (String) -> Unit = {},
    val onModelPicked: (PrinterModel) -> Unit = {},
    val onStartNewModel: () -> Unit = {},
    val onChangeModel: () -> Unit = {},
    val onBrandChange: (String) -> Unit = {},
    val onModelNameChange: (String) -> Unit = {},
    val onPrintTypeChange: (PrintType) -> Unit = {},
    val onColorTypeChange: (ColorType) -> Unit = {},
    val onCartridgeToggle: (String) -> Unit = {},
    val onNewCartridgeChange: (CartridgeDraft) -> Unit = {},
    val onAddCartridge: () -> Unit = {},
    val onLocationChange: (String) -> Unit = {},
    val onNoteChange: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDiscardConfirmed: () -> Unit = {},
    val onDismissDialog: () -> Unit = {},
)

@Composable
internal fun PrinterEditScreen(
    state: PrinterEditUiState,
    actions: PrinterEditActions,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    val draft = form.draft
    Column(modifier = modifier.fillMaxSize().background(PmTheme.colors.background).imePadding()) {
        PmTopBar(
            title = stringResource(if (state.isNew) R.string.feature_clients_printer_new else R.string.feature_clients_printer_edit),
            navigationLabel = stringResource(R.string.feature_clients_cancel),
            navigationIcon = PmIcons.Close,
            onNavigate = actions.onClose,
            actions = {
                if (!state.isNew) {
                    IconButton(onClick = actions.onDelete) {
                        Icon(PmIcons.Delete, contentDescription = stringResource(R.string.feature_clients_delete))
                    }
                }
            },
        )
        if (form.isLoading) return@Column

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.feature_clients_printer_section_model))
            when (form.step) {
                ModelStep.SEARCH -> ModelSearch(state, actions)
                ModelStep.PICKED -> PickedModel(draft.model, actions.onChangeModel)
                ModelStep.NEW -> NewModelFields(draft.model, form.errors, state.brands, actions)
            }

            if (form.step != ModelStep.SEARCH) {
                SectionTitle(stringResource(R.string.feature_clients_printer_section_cartridges))
                CartridgeChoices(draft, form.newCartridge, actions)
            }

            SectionTitle(stringResource(R.string.feature_clients_printer_section_place))
            PmTextField(
                value = draft.location,
                onValueChange = actions.onLocationChange,
                label = stringResource(R.string.feature_clients_printer_location),
                placeholder = stringResource(R.string.feature_clients_printer_location_hint),
            )
            PmTextField(
                value = draft.note,
                onValueChange = actions.onNoteChange,
                label = stringResource(R.string.feature_clients_field_note),
                singleLine = false,
                minLines = 2,
            )
        }

        Column(Modifier.fillMaxWidth().background(PmTheme.colors.surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
            PmPrimaryButton(
                text = stringResource(R.string.feature_clients_save),
                onClick = actions.onSave,
                enabled = !form.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    when (form.dialog) {
        PrinterEditDialog.DISCARD -> PmConfirmDialog(
            title = stringResource(R.string.feature_clients_discard_title),
            message = null,
            confirmText = stringResource(R.string.feature_clients_discard),
            dismissText = stringResource(R.string.feature_clients_keep_editing),
            onConfirm = actions.onDiscardConfirmed,
            onDismiss = actions.onDismissDialog,
            destructive = true,
        )
        PrinterEditDialog.CONFIRM_DELETE -> PmConfirmDialog(
            title = stringResource(R.string.feature_clients_printer_delete_title),
            message = draft.model.fullName,
            confirmText = stringResource(R.string.feature_clients_delete),
            dismissText = stringResource(R.string.feature_clients_cancel),
            onConfirm = actions.onConfirmDelete,
            onDismiss = actions.onDismissDialog,
            destructive = true,
        )
        null -> Unit
    }
}

@Composable
private fun ModelSearch(state: PrinterEditUiState, actions: PrinterEditActions) {
    val query = state.form.modelQuery
    PmTextField(
        value = query,
        onValueChange = actions.onModelQueryChange,
        label = stringResource(R.string.feature_clients_printer_model_search),
        placeholder = stringResource(R.string.feature_clients_printer_model_search_hint),
        error = if (state.form.errors.isNotEmpty()) stringResource(R.string.feature_clients_printer_error_no_model) else null,
        capitalization = KeyboardCapitalization.None,
    )
    if (state.suggestions.isNotEmpty()) {
        PmCard(Modifier.fillMaxWidth()) {
            state.suggestions.forEachIndexed { index, model ->
                if (index > 0) HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                Column(
                    Modifier.fillMaxWidth().clickable { actions.onModelPicked(model) }.padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(model.fullName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOf(model.typeLabel(), model.cartridges.joinToString(", ") { it.name }).filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = PmTheme.colors.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    } else if (query.isNotBlank()) {
        Text(
            stringResource(R.string.feature_clients_printer_not_in_catalog),
            style = MaterialTheme.typography.bodySmall,
            color = PmTheme.colors.inkMuted,
        )
    }
    PmSecondaryButton(
        text = if (query.isBlank()) {
            stringResource(R.string.feature_clients_printer_new_model)
        } else {
            stringResource(R.string.feature_clients_printer_new_model_named, CatalogNames.clean(query))
        },
        onClick = actions.onStartNewModel,
        icon = PmIcons.Add,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PickedModel(model: PrinterModelDraft, onChange: () -> Unit) {
    PmCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(PmIcons.Printer, contentDescription = null, tint = PmTheme.colors.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(model.fullName, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${model.printType.label()} · ${model.colorType.label()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = PmTheme.colors.inkMuted,
                )
            }
            TextButton(onClick = onChange) { Text(stringResource(R.string.feature_clients_printer_change_model)) }
        }
    }
}

@Composable
private fun NewModelFields(
    model: PrinterModelDraft,
    errors: Set<PrinterDraftError>,
    brands: List<String>,
    actions: PrinterEditActions,
) {
    PmTextField(
        value = model.brand,
        onValueChange = actions.onBrandChange,
        label = stringResource(R.string.feature_clients_printer_brand),
        error = if (PrinterDraftError.BRAND_REQUIRED in errors) stringResource(R.string.feature_clients_printer_error_brand) else null,
        capitalization = KeyboardCapitalization.Words,
    )
    if (brands.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            brands.forEach { brand ->
                PmFilterChip(
                    text = brand,
                    selected = CatalogNames.key(brand) == CatalogNames.key(model.brand),
                    onClick = { actions.onBrandChange(brand) },
                )
            }
        }
    }
    PmTextField(
        value = model.name,
        onValueChange = actions.onModelNameChange,
        label = stringResource(R.string.feature_clients_printer_model_name),
        placeholder = stringResource(R.string.feature_clients_printer_model_name_hint),
        error = if (PrinterDraftError.MODEL_REQUIRED in errors) stringResource(R.string.feature_clients_printer_error_model) else null,
        capitalization = KeyboardCapitalization.None,
    )
    PmSegmentedButtons(options = PrintType.entries, selected = model.printType, onSelect = actions.onPrintTypeChange, label = { it.label() })
    PmSegmentedButtons(options = ColorType.entries, selected = model.colorType, onSelect = actions.onColorTypeChange, label = { it.label() })
    TextButton(onClick = actions.onChangeModel) { Text(stringResource(R.string.feature_clients_printer_pick_from_catalog)) }
}

@Composable
private fun CartridgeChoices(draft: ClientPrinterDraft, newCartridge: CartridgeDraft, actions: PrinterEditActions) {
    PmCard(Modifier.fillMaxWidth()) {
        if (draft.model.cartridges.isEmpty()) {
            Text(
                stringResource(R.string.feature_clients_printer_no_cartridges),
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = PmTheme.colors.inkMuted,
            )
        }
        draft.model.cartridges.forEachIndexed { index, cartridge ->
            if (index > 0) HorizontalDivider(color = PmTheme.colors.surfaceMuted)
            val checked = cartridge.key in draft.selectedCartridges
            Row(
                Modifier.fillMaxWidth().clickable { actions.onCartridgeToggle(cartridge.key) }.padding(end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { actions.onCartridgeToggle(cartridge.key) },
                    colors = CheckboxDefaults.colors(checkedColor = PmTheme.colors.primary),
                )
                Column(Modifier.weight(1f)) {
                    Text(cartridge.name, style = MaterialTheme.typography.bodyLarge)
                    if (cartridge.chips.isNotBlank()) {
                        Text(
                            stringResource(R.string.feature_clients_printer_chip_value, cartridge.chips),
                            style = MaterialTheme.typography.bodySmall,
                            color = PmTheme.colors.inkMuted,
                        )
                    }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PmTextField(
            value = newCartridge.name,
            onValueChange = { actions.onNewCartridgeChange(newCartridge.copy(name = it)) },
            label = stringResource(R.string.feature_clients_printer_add_cartridge),
            placeholder = "CF283A",
            capitalization = KeyboardCapitalization.Characters,
            modifier = Modifier.weight(1f),
        )
        PmTextField(
            value = newCartridge.chips,
            onValueChange = { actions.onNewCartridgeChange(newCartridge.copy(chips = it)) },
            label = stringResource(R.string.feature_clients_printer_chip),
            capitalization = KeyboardCapitalization.None,
            modifier = Modifier.weight(0.8f),
        )
        IconButton(onClick = actions.onAddCartridge, enabled = newCartridge.name.isNotBlank()) {
            Icon(PmIcons.Add, contentDescription = stringResource(R.string.feature_clients_printer_add_cartridge), tint = PmTheme.colors.primary)
        }
    }
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

@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun PrinterEditPickedPreview() {
    val model = PrinterModel(
        1, 1, "HP", "LaserJet M125", PrintType.LASER, ColorType.MONO,
        listOf(Cartridge(1, "CF283A"), Cartridge(2, "CF283X")),
    )
    PmTheme {
        PrinterEditScreen(
            state = PrinterEditUiState(
                form = PrinterForm(
                    draft = ClientPrinterDraft(clientId = 1, location = "Հաշվապահություն").withModel(model).toggleCartridge("CF283X"),
                    step = ModelStep.PICKED,
                ),
            ),
            actions = PrinterEditActions(),
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun PrinterEditSearchPreview() {
    PmTheme {
        PrinterEditScreen(
            state = PrinterEditUiState(
                form = PrinterForm(modelQuery = "m125"),
                suggestions = listOf(PrinterModel(1, 1, "HP", "LaserJet M125", PrintType.LASER, ColorType.MONO, listOf(Cartridge(1, "CF283A")))),
            ),
            actions = PrinterEditActions(),
        )
    }
}
