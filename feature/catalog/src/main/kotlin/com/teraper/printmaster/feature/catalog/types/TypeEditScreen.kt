package com.teraper.printmaster.feature.catalog.types

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.teraper.printmaster.core.designsystem.component.PmTextField
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.FieldDraft
import com.teraper.printmaster.core.model.FieldKind
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.core.model.ItemTypeDraft
import com.teraper.printmaster.core.model.ItemTypeError
import com.teraper.printmaster.feature.catalog.R

@Composable
internal fun TypeEditRoute(
    onClose: () -> Unit,
    viewModel: TypeEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                TypeEditEvent.Close -> onClose()
            }
        }
    }
    BackHandler(onBack = viewModel::onCloseRequest)
    TypeEditScreen(
        state = state,
        actions = TypeEditActions(
            onClose = viewModel::onCloseRequest,
            onDelete = viewModel::onDeleteClick,
            onNameChange = viewModel::onNameChange,
            onParentChange = viewModel::onParentChange,
            onHasSerialChange = viewModel::onHasSerialChange,
            onPerClientChange = viewModel::onPerClientChange,
            onFieldChange = viewModel::onFieldChange,
            onRemoveField = viewModel::onRemoveField,
            onSave = viewModel::onSave,
            onConfirmDelete = viewModel::onConfirmDelete,
            onDiscardConfirmed = viewModel::onDiscardConfirmed,
            onDismissDialog = viewModel::onDismissDialog,
        ),
    )
}

internal data class TypeEditActions(
    val onClose: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onParentChange: (Long?) -> Unit = {},
    val onHasSerialChange: (Boolean) -> Unit = {},
    val onPerClientChange: (Boolean) -> Unit = {},
    val onFieldChange: (Int, FieldDraft) -> Unit = { _, _ -> },
    val onRemoveField: (Int) -> Unit = {},
    val onSave: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDiscardConfirmed: () -> Unit = {},
    val onDismissDialog: () -> Unit = {},
)

@Composable
internal fun TypeEditScreen(
    state: TypeEditUiState,
    actions: TypeEditActions,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    val draft = form.draft
    Column(modifier.fillMaxSize().background(PmTheme.colors.background).imePadding()) {
        PmTopBar(
            title = stringResource(if (state.isNew) R.string.feature_catalog_new_type else R.string.feature_catalog_edit_type),
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
                value = draft.name,
                onValueChange = actions.onNameChange,
                label = stringResource(R.string.feature_catalog_type_name),
                placeholder = stringResource(R.string.feature_catalog_type_name_hint),
                error = when {
                    form.nameTaken -> stringResource(R.string.feature_catalog_error_type_taken)
                    ItemTypeError.NAME_REQUIRED in form.errors -> stringResource(R.string.feature_catalog_error_type_name)
                    else -> null
                },
                capitalization = KeyboardCapitalization.Sentences,
            )
            if (state.parents.isNotEmpty()) {
                SectionTitle(stringResource(R.string.feature_catalog_parent))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PmFilterChip(
                        text = stringResource(R.string.feature_catalog_parent_none),
                        selected = draft.parentId == null,
                        onClick = { actions.onParentChange(null) },
                    )
                    state.parents.forEach { parent ->
                        PmFilterChip(text = parent.name, selected = draft.parentId == parent.id, onClick = { actions.onParentChange(parent.id) })
                    }
                }
            }

            SwitchRow(stringResource(R.string.feature_catalog_has_serial), stringResource(R.string.feature_catalog_has_serial_help), draft.hasSerial, actions.onHasSerialChange)
            SwitchRow(stringResource(R.string.feature_catalog_per_client), stringResource(R.string.feature_catalog_per_client_help), draft.perClient, actions.onPerClientChange)

            SectionTitle(stringResource(R.string.feature_catalog_section_type_fields))
            Text(stringResource(R.string.feature_catalog_fields_help), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
            if (ItemTypeError.CHOICES_REQUIRED in form.errors) {
                Text(stringResource(R.string.feature_catalog_error_choices), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.error)
            }
            draft.fields.forEachIndexed { index, field ->
                FieldCard(
                    field = field,
                    isLast = index == draft.fields.lastIndex,
                    onChange = { actions.onFieldChange(index, it) },
                    onRemove = { actions.onRemoveField(index) },
                )
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
        TypeEditDialog.DISCARD -> PmConfirmDialog(
            title = stringResource(R.string.feature_catalog_discard_title),
            message = null,
            confirmText = stringResource(R.string.feature_catalog_discard),
            dismissText = stringResource(R.string.feature_catalog_keep_editing),
            onConfirm = actions.onDiscardConfirmed,
            onDismiss = actions.onDismissDialog,
            destructive = true,
        )
        TypeEditDialog.CONFIRM_DELETE -> PmConfirmDialog(
            title = stringResource(R.string.feature_catalog_type_delete_title),
            message = draft.name,
            confirmText = stringResource(R.string.feature_catalog_delete),
            dismissText = stringResource(R.string.feature_catalog_cancel),
            onConfirm = actions.onConfirmDelete,
            onDismiss = actions.onDismissDialog,
            destructive = true,
        )
        TypeEditDialog.IN_USE -> PmMessageDialog(
            title = stringResource(R.string.feature_catalog_in_use_title),
            message = stringResource(R.string.feature_catalog_type_in_use_message),
            okText = stringResource(R.string.feature_catalog_ok),
            onDismiss = actions.onDismissDialog,
        )
        null -> Unit
    }
}

/** One field: its name, what it holds, choices for a choice field, and whether it's written before the name. */
@Composable
private fun FieldCard(field: FieldDraft, isLast: Boolean, onChange: (FieldDraft) -> Unit, onRemove: () -> Unit) {
    PmCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PmTextField(
                    value = field.name,
                    onValueChange = { onChange(field.copy(name = it)) },
                    label = stringResource(if (isLast) R.string.feature_catalog_add_field else R.string.feature_catalog_field_name),
                    placeholder = stringResource(R.string.feature_catalog_field_name_hint),
                    capitalization = KeyboardCapitalization.Sentences,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove, enabled = !isLast) {
                    Icon(
                        PmIcons.Close,
                        contentDescription = stringResource(R.string.feature_catalog_field_remove),
                        tint = if (isLast) PmTheme.colors.surfaceMuted else PmTheme.colors.inkMuted,
                    )
                }
            }
            if (field.name.isNotBlank()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldKind.entries.forEach { kind ->
                        PmFilterChip(text = kind.label(), selected = field.kind == kind, onClick = { onChange(field.copy(kind = kind)) })
                    }
                }
                if (field.kind == FieldKind.CHOICE) {
                    PmTextField(
                        value = field.options,
                        onValueChange = { onChange(field.copy(options = it)) },
                        label = stringResource(R.string.feature_catalog_field_options),
                        singleLine = false,
                        minLines = 3,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                if (field.kind == FieldKind.TEXT || field.kind == FieldKind.CHOICE) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onChange(field.copy(inTitle = !field.inTitle)) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = field.inTitle,
                            onCheckedChange = { onChange(field.copy(inTitle = it)) },
                            colors = CheckboxDefaults.colors(checkedColor = PmTheme.colors.primary),
                        )
                        Text(stringResource(R.string.feature_catalog_field_in_title), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldKind.label(): String = stringResource(
    when (this) {
        FieldKind.TEXT -> R.string.feature_catalog_kind_text
        FieldKind.NUMBER -> R.string.feature_catalog_kind_number
        FieldKind.CHOICE -> R.string.feature_catalog_kind_choice
        FieldKind.YES_NO -> R.string.feature_catalog_kind_yes_no
        FieldKind.DATE -> R.string.feature_catalog_kind_date
    },
)

@Composable
private fun SwitchRow(title: String, help: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(help, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = PmTheme.colors.primary))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, modifier = Modifier.padding(top = 8.dp, start = 2.dp), style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted)
}

@Preview(showBackground = true, widthDp = 390, heightDp = 860)
@Composable
private fun TypeEditScreenPreview() {
    PmTheme {
        TypeEditScreen(
            state = TypeEditUiState(
                form = TypeForm(
                    draft = ItemTypeDraft(
                        id = 1,
                        name = "Տպիչ",
                        fields = listOf(
                            FieldDraft(1, "Բրենդ", inTitle = true),
                            FieldDraft(2, "Տպման տեսակ", FieldKind.CHOICE, "Լազերային\nԹանաքային"),
                            FieldDraft(),
                        ),
                    ),
                ),
                parents = listOf(ItemType(2, "Սարք")),
            ),
            actions = TypeEditActions(),
        )
    }
}
