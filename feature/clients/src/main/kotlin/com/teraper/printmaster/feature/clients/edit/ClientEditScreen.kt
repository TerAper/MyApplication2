package com.teraper.printmaster.feature.clients.edit

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.PmConfirmDialog
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmSegmentedButtons
import com.teraper.printmaster.core.designsystem.component.PmTextField
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.ClientDraftError
import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.feature.clients.R
import com.teraper.printmaster.feature.clients.common.label

@Composable
internal fun ClientEditRoute(
    onClose: () -> Unit,
    onSaved: (clientId: Long, wasNew: Boolean) -> Unit,
    viewModel: ClientEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ClientEditEvent.Saved -> onSaved(event.clientId, event.wasNew)
                ClientEditEvent.Close -> onClose()
            }
        }
    }
    BackHandler(onBack = viewModel::onCloseRequest)

    ClientEditScreen(
        state = state,
        actions = ClientEditActions(
            onClose = viewModel::onCloseRequest,
            onTypeChange = viewModel::onTypeChange,
            onNameChange = viewModel::onNameChange,
            onTaxIdChange = viewModel::onTaxIdChange,
            onNoteChange = viewModel::onNoteChange,
            onContactValueChange = viewModel::onContactValueChange,
            onContactLabelChange = viewModel::onContactLabelChange,
            onAddContact = viewModel::onAddContact,
            onRemoveContact = viewModel::onRemoveContact,
            onSave = viewModel::onSave,
            onDiscardConfirmed = viewModel::onDiscardConfirmed,
            onDiscardDismissed = viewModel::onDiscardDismissed,
        ),
    )
}

/** Everything the form can do, grouped so the screen signature stays readable. */
internal data class ClientEditActions(
    val onClose: () -> Unit = {},
    val onTypeChange: (ClientType) -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onTaxIdChange: (String) -> Unit = {},
    val onNoteChange: (String) -> Unit = {},
    val onContactValueChange: (ContactList, Int, String) -> Unit = { _, _, _ -> },
    val onContactLabelChange: (ContactList, Int, String) -> Unit = { _, _, _ -> },
    val onAddContact: (ContactList) -> Unit = {},
    val onRemoveContact: (ContactList, Int) -> Unit = { _, _ -> },
    val onSave: () -> Unit = {},
    val onDiscardConfirmed: () -> Unit = {},
    val onDiscardDismissed: () -> Unit = {},
)

@Composable
internal fun ClientEditScreen(
    state: ClientEditUiState,
    actions: ClientEditActions,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    Column(modifier = modifier.fillMaxSize().background(PmTheme.colors.background).imePadding()) {
        PmTopBar(
            title = stringResource(if (state.isNew) R.string.feature_clients_new_client else R.string.feature_clients_edit_title),
            navigationLabel = stringResource(R.string.feature_clients_cancel),
            navigationIcon = PmIcons.Close,
            onNavigate = actions.onClose,
        )

        if (!state.isLoading) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PmSegmentedButtons(
                    options = ClientType.entries,
                    selected = draft.type,
                    onSelect = actions.onTypeChange,
                    label = { it.label() },
                )
                PmTextField(
                    value = draft.name,
                    onValueChange = actions.onNameChange,
                    label = stringResource(
                        if (draft.type == ClientType.FIRM) R.string.feature_clients_field_firm_name else R.string.feature_clients_field_person_name,
                    ),
                    error = if (ClientDraftError.NAME_REQUIRED in state.errors) stringResource(R.string.feature_clients_error_name_required) else null,
                    capitalization = KeyboardCapitalization.Words,
                )
                if (draft.type == ClientType.FIRM) {
                    PmTextField(
                        value = draft.taxId,
                        onValueChange = actions.onTaxIdChange,
                        label = stringResource(R.string.feature_clients_field_tax_id),
                        placeholder = stringResource(R.string.feature_clients_field_tax_id_hint),
                        error = when {
                            state.taxIdTakenBy != null -> stringResource(R.string.feature_clients_error_tax_id_taken, state.taxIdTakenBy)
                            ClientDraftError.TAX_ID_FORMAT in state.errors -> stringResource(R.string.feature_clients_error_tax_id_format)
                            else -> null
                        },
                        keyboardType = KeyboardType.Number,
                    )
                }

                ContactSection(
                    title = stringResource(R.string.feature_clients_section_phones),
                    rows = draft.phones,
                    valueLabel = stringResource(R.string.feature_clients_field_phone),
                    addText = stringResource(R.string.feature_clients_add_phone),
                    keyboardType = KeyboardType.Phone,
                    list = ContactList.PHONES,
                    actions = actions,
                )
                ContactSection(
                    title = stringResource(R.string.feature_clients_section_addresses),
                    rows = draft.addresses,
                    valueLabel = stringResource(R.string.feature_clients_field_address),
                    addText = stringResource(R.string.feature_clients_add_address),
                    keyboardType = KeyboardType.Text,
                    list = ContactList.ADDRESSES,
                    actions = actions,
                )

                PmTextField(
                    value = draft.note,
                    onValueChange = actions.onNoteChange,
                    label = stringResource(R.string.feature_clients_field_note),
                    singleLine = false,
                    minLines = 2,
                )
            }

            Column(
                Modifier.fillMaxWidth().background(PmTheme.colors.surface).padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                PmPrimaryButton(
                    text = stringResource(R.string.feature_clients_save),
                    onClick = actions.onSave,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (state.showDiscardDialog) {
        PmConfirmDialog(
            title = stringResource(R.string.feature_clients_discard_title),
            message = null,
            confirmText = stringResource(R.string.feature_clients_discard),
            dismissText = stringResource(R.string.feature_clients_keep_editing),
            onConfirm = actions.onDiscardConfirmed,
            onDismiss = actions.onDiscardDismissed,
            destructive = true,
        )
    }
}

@Composable
private fun ContactSection(
    title: String,
    rows: List<ClientDraft.ContactDraft>,
    valueLabel: String,
    addText: String,
    keyboardType: KeyboardType,
    list: ContactList,
    actions: ClientEditActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted, modifier = Modifier.padding(start = 2.dp, top = 4.dp))
        rows.forEachIndexed { index, row ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PmTextField(
                        value = row.value,
                        onValueChange = { actions.onContactValueChange(list, index, it) },
                        label = valueLabel,
                        keyboardType = keyboardType,
                    )
                    PmTextField(
                        value = row.label,
                        onValueChange = { actions.onContactLabelChange(list, index, it) },
                        label = stringResource(R.string.feature_clients_field_contact_label),
                    )
                }
                IconButton(onClick = { actions.onRemoveContact(list, index) }, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(PmIcons.Close, contentDescription = stringResource(R.string.feature_clients_remove), tint = PmTheme.colors.inkMuted)
                }
            }
        }
        TextButton(onClick = { actions.onAddContact(list) }) {
            Text(addText, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun ClientEditScreenPreview() {
    PmTheme {
        ClientEditScreen(
            state = ClientEditUiState(
                draft = ClientDraft(name = "«ԱԲԳ Սերվիս» ՍՊԸ", taxId = "0123"),
                errors = setOf(ClientDraftError.TAX_ID_FORMAT),
            ),
            actions = ClientEditActions(),
        )
    }
}
