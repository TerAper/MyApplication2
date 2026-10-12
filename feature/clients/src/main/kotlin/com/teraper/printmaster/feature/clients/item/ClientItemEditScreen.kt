package com.teraper.printmaster.feature.clients.item

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
import com.teraper.printmaster.core.designsystem.component.PmItemFields
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.component.PmTextField
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.CatalogItem
import com.teraper.printmaster.core.model.CatalogNames
import com.teraper.printmaster.core.model.ClientItemDraft
import com.teraper.printmaster.core.model.ItemDraftError
import com.teraper.printmaster.core.model.ItemField
import com.teraper.printmaster.core.model.ItemRef
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.feature.clients.R
import com.teraper.printmaster.feature.clients.common.ScanOutcome
import com.teraper.printmaster.feature.clients.common.rememberSerialScanner

@Composable
internal fun ClientItemEditRoute(
    onClose: () -> Unit,
    onOpenItemTypes: () -> Unit,
    viewModel: ClientItemEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ClientItemEditEvent.Close -> onClose()
            }
        }
    }
    BackHandler(onBack = viewModel::onCloseRequest)
    val scanSerial = rememberSerialScanner { outcome ->
        when (outcome) {
            is ScanOutcome.Read -> viewModel.onSerialScanned(outcome.text)
            ScanOutcome.Unavailable -> viewModel.onScanUnavailable()
            ScanOutcome.Closed -> Unit
        }
    }

    ClientItemEditScreen(
        state = state,
        actions = ClientItemEditActions(
            onClose = viewModel::onCloseRequest,
            onDelete = viewModel::onDeleteClick,
            onModelQueryChange = viewModel::onModelQueryChange,
            onModelPicked = viewModel::onModelPicked,
            onStartNewModel = viewModel::onStartNewModel,
            onNewModelType = viewModel::onNewModelType,
            onChangeModel = viewModel::onChangeModel,
            onModelNameChange = viewModel::onModelNameChange,
            onModelValueChange = viewModel::onModelValueChange,
            onPartToggle = viewModel::onPartToggle,
            onNewPartChange = viewModel::onNewPartChange,
            onNewPartType = viewModel::onNewPartType,
            onAddPart = viewModel::onAddPart,
            onPlaceChange = viewModel::onPlaceChange,
            onNoteChange = viewModel::onNoteChange,
            onSerialChange = viewModel::onSerialChange,
            onScanSerial = scanSerial,
            onOpenItemTypes = onOpenItemTypes,
            onSave = viewModel::onSave,
            onConfirmDelete = viewModel::onConfirmDelete,
            onDiscardConfirmed = viewModel::onDiscardConfirmed,
            onDismissDialog = viewModel::onDismissDialog,
        ),
    )
}

internal data class ClientItemEditActions(
    val onClose: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onModelQueryChange: (String) -> Unit = {},
    val onModelPicked: (CatalogItem) -> Unit = {},
    val onStartNewModel: () -> Unit = {},
    val onNewModelType: (Long) -> Unit = {},
    val onChangeModel: () -> Unit = {},
    val onModelNameChange: (String) -> Unit = {},
    val onModelValueChange: (Long, String) -> Unit = { _, _ -> },
    val onPartToggle: (String) -> Unit = {},
    val onNewPartChange: (String) -> Unit = {},
    val onNewPartType: (Long) -> Unit = {},
    val onAddPart: () -> Unit = {},
    val onPlaceChange: (String) -> Unit = {},
    val onNoteChange: (String) -> Unit = {},
    val onSerialChange: (String) -> Unit = {},
    val onScanSerial: () -> Unit = {},
    val onOpenItemTypes: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDiscardConfirmed: () -> Unit = {},
    val onDismissDialog: () -> Unit = {},
)

@Composable
internal fun ClientItemEditScreen(
    state: ClientItemEditUiState,
    actions: ClientItemEditActions,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    val draft = form.draft
    Column(modifier = modifier.fillMaxSize().background(PmTheme.colors.background).imePadding()) {
        PmTopBar(
            title = stringResource(if (state.isNew) R.string.feature_clients_item_new else R.string.feature_clients_item_edit),
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
            if (state.types.isEmpty()) {
                // A client's thing needs a type; the customer makes them (or picks a template) first.
                Text(stringResource(R.string.feature_clients_item_no_types), style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.inkSecondary)
                PmPrimaryButton(
                    text = stringResource(R.string.feature_clients_item_open_types),
                    onClick = actions.onOpenItemTypes,
                    icon = PmIcons.ItemTypes,
                    modifier = Modifier.fillMaxWidth(),
                )
                return@Column
            }
            SectionTitle(stringResource(R.string.feature_clients_item_section_model))
            when (form.step) {
                ModelStep.SEARCH -> ModelSearch(state, actions)
                ModelStep.PICKED -> PickedModel(state, actions.onChangeModel)
                ModelStep.NEW -> NewModelFields(state, actions)
            }

            if (form.step != ModelStep.SEARCH && state.partTypes.isNotEmpty()) {
                SectionTitle(state.partTypes.joinToString(", ") { it.name }.uppercase())
                PartChoices(state, actions)
            }

            SectionTitle(stringResource(R.string.feature_clients_item_section_place))
            PmTextField(
                value = draft.serialNumber,
                onValueChange = actions.onSerialChange,
                label = stringResource(R.string.feature_clients_item_serial),
                placeholder = stringResource(R.string.feature_clients_item_serial_hint),
                capitalization = KeyboardCapitalization.Characters,
                trailingIcon = {
                    IconButton(onClick = actions.onScanSerial) {
                        Icon(PmIcons.Scan, contentDescription = stringResource(R.string.feature_clients_scan_serial), tint = PmTheme.colors.primary)
                    }
                },
            )
            form.serialTakenBy?.let {
                Text(stringResource(R.string.feature_clients_item_serial_taken, it), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.warning)
            }
            if (form.scanUnavailable) {
                Text(stringResource(R.string.feature_clients_scan_unavailable), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.error)
            }
            PmTextField(
                value = draft.place,
                onValueChange = actions.onPlaceChange,
                label = stringResource(R.string.feature_clients_item_place),
                placeholder = stringResource(R.string.feature_clients_item_place_hint),
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
                enabled = !form.isSaving && state.types.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    when (form.dialog) {
        ClientItemEditDialog.DISCARD -> PmConfirmDialog(
            title = stringResource(R.string.feature_clients_discard_title),
            message = null,
            confirmText = stringResource(R.string.feature_clients_discard),
            dismissText = stringResource(R.string.feature_clients_keep_editing),
            onConfirm = actions.onDiscardConfirmed,
            onDismiss = actions.onDismissDialog,
            destructive = true,
        )
        ClientItemEditDialog.CONFIRM_DELETE -> PmConfirmDialog(
            title = stringResource(R.string.feature_clients_item_delete_title),
            message = state.title,
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
private fun ModelSearch(state: ClientItemEditUiState, actions: ClientItemEditActions) {
    val query = state.form.modelQuery
    PmTextField(
        value = query,
        onValueChange = actions.onModelQueryChange,
        label = stringResource(R.string.feature_clients_item_model_search),
        placeholder = stringResource(R.string.feature_clients_item_model_search_hint),
        error = if (state.form.errors.isNotEmpty()) stringResource(R.string.feature_clients_item_error_no_model) else null,
        capitalization = KeyboardCapitalization.None,
    )
    if (state.suggestions.isNotEmpty()) {
        PmCard(Modifier.fillMaxWidth()) {
            state.suggestions.forEachIndexed { index, model ->
                if (index > 0) HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                Column(
                    Modifier.fillMaxWidth().clickable { actions.onModelPicked(model) }.padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(model.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOf(model.type.name, model.details, model.parts.joinToString(", ") { it.title }).filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = PmTheme.colors.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    } else if (query.isNotBlank()) {
        Text(stringResource(R.string.feature_clients_item_not_in_catalog), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
    }
    PmSecondaryButton(
        text = if (query.isBlank()) {
            stringResource(R.string.feature_clients_item_new_model)
        } else {
            stringResource(R.string.feature_clients_item_new_model_named, CatalogNames.clean(query))
        },
        onClick = actions.onStartNewModel,
        icon = PmIcons.Add,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PickedModel(state: ClientItemEditUiState, onChange: () -> Unit) {
    PmCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(PmIcons.Item, contentDescription = null, tint = PmTheme.colors.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(state.title, style = MaterialTheme.typography.titleSmall)
                val type = state.type
                val details = type?.let { com.teraper.printmaster.core.model.ItemTitles.details(it, state.form.draft.item.values) }.orEmpty()
                Text(
                    listOfNotNull(type?.name, details.takeIf { it.isNotEmpty() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = PmTheme.colors.inkMuted,
                )
            }
            TextButton(onClick = onChange) { Text(stringResource(R.string.feature_clients_item_change_model)) }
        }
    }
}

@Composable
private fun NewModelFields(state: ClientItemEditUiState, actions: ClientItemEditActions) {
    val model = state.form.draft.item
    if (state.types.size > 1) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.types.forEach { type ->
                PmFilterChip(text = type.name, selected = model.typeId == type.id, onClick = { actions.onNewModelType(type.id) })
            }
        }
        if (ItemDraftError.TYPE_REQUIRED in state.form.errors) {
            Text(stringResource(R.string.feature_clients_item_error_type), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.error)
        }
    }
    val type = state.type
    if (type != null && type.titleFields.isNotEmpty()) {
        PmItemFields(type.titleFields, model.values, actions.onModelValueChange, suggestions = state.valueSuggestions)
    }
    PmTextField(
        value = model.name,
        onValueChange = actions.onModelNameChange,
        label = stringResource(R.string.feature_clients_item_model_name),
        placeholder = stringResource(R.string.feature_clients_item_model_name_hint),
        error = if (ItemDraftError.NAME_REQUIRED in state.form.errors) stringResource(R.string.feature_clients_item_error_model) else null,
        capitalization = KeyboardCapitalization.None,
    )
    if (type != null) {
        val others = type.fields.filterNot { it.inTitle }
        if (others.isNotEmpty()) PmItemFields(others, model.values, actions.onModelValueChange, suggestions = state.valueSuggestions)
    }
    TextButton(onClick = actions.onChangeModel) { Text(stringResource(R.string.feature_clients_item_pick_from_catalog)) }
}

/** The model's parts to tick for this client's item, and a field to add one that's missing. */
@Composable
private fun PartChoices(state: ClientItemEditUiState, actions: ClientItemEditActions) {
    val draft = state.form.draft
    val parts = draft.item.parts
    PmCard(Modifier.fillMaxWidth()) {
        if (parts.isEmpty()) {
            Text(
                stringResource(R.string.feature_clients_item_no_parts),
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = PmTheme.colors.inkMuted,
            )
        }
        parts.forEachIndexed { index, part ->
            if (index > 0) HorizontalDivider(color = PmTheme.colors.surfaceMuted)
            Row(
                Modifier.fillMaxWidth().clickable { actions.onPartToggle(part.key) }.padding(end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = part.key in draft.selectedParts,
                    onCheckedChange = { actions.onPartToggle(part.key) },
                    colors = CheckboxDefaults.colors(checkedColor = PmTheme.colors.primary),
                )
                Text(part.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (state.partTypes.size > 1) {
                    Text(state.partTypes.firstOrNull { it.id == part.typeId }?.name.orEmpty(), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
                }
            }
        }
    }
    if (state.partTypes.size > 1) {
        val selected = state.form.newPartTypeId ?: state.partTypes.first().id
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.partTypes.forEach { type -> PmFilterChip(text = type.name, selected = selected == type.id, onClick = { actions.onNewPartType(type.id) }) }
        }
    }
    val partTypeName = (state.partTypes.firstOrNull { it.id == state.form.newPartTypeId } ?: state.partTypes.first()).name
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PmTextField(
            value = state.form.newPart,
            onValueChange = actions.onNewPartChange,
            label = stringResource(R.string.feature_clients_item_add_part, partTypeName),
            capitalization = KeyboardCapitalization.Characters,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = actions.onAddPart, enabled = state.form.newPart.isNotBlank()) {
            Icon(PmIcons.Add, contentDescription = stringResource(R.string.feature_clients_item_add_part, partTypeName), tint = PmTheme.colors.primary)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, modifier = Modifier.padding(top = 4.dp, start = 2.dp), style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted)
}

@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun ClientItemEditPickedPreview() {
    val printer = ItemType(1, "Տպիչ", fields = listOf(ItemField(1, 1, "Բրենդ", inTitle = true), ItemField(2, 1, "Տպման տեսակ")))
    val cartridge = ItemType(2, "Քարտրիջ", 1)
    val model = CatalogItem(
        1, printer, "LaserJet M125", mapOf(1L to "HP", 2L to "Լազերային"),
        parts = listOf(ItemRef(5, 2, "Քարտրիջ", "CF283A"), ItemRef(6, 2, "Քարտրիջ", "CF283X")),
    )
    PmTheme {
        ClientItemEditScreen(
            state = ClientItemEditUiState(
                form = ClientItemForm(draft = ClientItemDraft(clientId = 1, place = "Հաշվապահություն").withItem(model), step = ModelStep.PICKED),
                types = listOf(printer, cartridge),
                type = printer,
                partTypes = listOf(cartridge),
            ),
            actions = ClientItemEditActions(),
        )
    }
}
