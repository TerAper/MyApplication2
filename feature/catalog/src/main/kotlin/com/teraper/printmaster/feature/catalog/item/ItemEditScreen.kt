package com.teraper.printmaster.feature.catalog.item

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
import com.teraper.printmaster.core.designsystem.component.PmItemFields
import com.teraper.printmaster.core.designsystem.component.PmMessageDialog
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.component.PmTextField
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.FieldKind
import com.teraper.printmaster.core.model.ItemDraft
import com.teraper.printmaster.core.model.ItemDraftError
import com.teraper.printmaster.core.model.ItemField
import com.teraper.printmaster.core.model.ItemOwner
import com.teraper.printmaster.core.model.ItemRef
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.core.model.LinkDraft
import com.teraper.printmaster.feature.catalog.R

@Composable
internal fun ItemEditRoute(
    onClose: () -> Unit,
    onOpenClient: (Long) -> Unit,
    onOpenItem: (itemId: Long, typeId: Long, intoId: Long) -> Unit,
    viewModel: ItemEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ItemEditEvent.Close -> onClose()
                is ItemEditEvent.OpenItem -> onOpenItem(event.itemId, event.typeId, event.intoId)
            }
        }
    }
    BackHandler(onBack = viewModel::onCloseRequest)
    ItemEditScreen(
        state = state,
        actions = ItemEditActions(
            onClose = viewModel::onCloseRequest,
            onDelete = viewModel::onDeleteClick,
            onTypeChange = viewModel::onTypeChange,
            onNameChange = viewModel::onNameChange,
            onValueChange = viewModel::onValueChange,
            onLinkQueryChange = viewModel::onLinkQueryChange,
            onAddLink = viewModel::onAddLink,
            onRemoveLink = viewModel::onRemoveLink,
            onOpenPart = viewModel::onOpenPart,
            onOpenClient = onOpenClient,
            onSave = viewModel::onSave,
            onConfirmDelete = viewModel::onConfirmDelete,
            onDiscardConfirmed = viewModel::onDiscardConfirmed,
            onDismissDialog = viewModel::onDismissDialog,
        ),
    )
}

internal data class ItemEditActions(
    val onClose: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onTypeChange: (Long) -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onValueChange: (Long, String) -> Unit = { _, _ -> },
    val onLinkQueryChange: (Long, String) -> Unit = { _, _ -> },
    val onAddLink: (LinkSection, ItemRef?) -> Unit = { _, _ -> },
    val onRemoveLink: (LinkSection, String) -> Unit = { _, _ -> },
    val onOpenPart: (LinkSection, LinkDraft?) -> Unit = { _, _ -> },
    val onOpenClient: (Long) -> Unit = {},
    val onSave: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDiscardConfirmed: () -> Unit = {},
    val onDismissDialog: () -> Unit = {},
)

@Composable
internal fun ItemEditScreen(
    state: ItemEditUiState,
    actions: ItemEditActions,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    val draft = form.draft
    Column(modifier.fillMaxSize().background(PmTheme.colors.background).imePadding()) {
        PmTopBar(
            title = when {
                !state.isNew -> stringResource(R.string.feature_catalog_edit_item)
                state.type != null -> stringResource(R.string.feature_catalog_new_item_of, state.type.name)
                else -> stringResource(R.string.feature_catalog_new_item)
            },
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
            if (state.isNew && !state.typeLocked) {
                SectionTitle(stringResource(R.string.feature_catalog_type))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.types.forEach { type ->
                        PmFilterChip(text = type.name, selected = draft.typeId == type.id, onClick = { actions.onTypeChange(type.id) })
                    }
                }
                if (ItemDraftError.TYPE_REQUIRED in form.errors) {
                    Text(stringResource(R.string.feature_catalog_error_type), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.error)
                }
            } else {
                state.type?.let { Text(it.name.uppercase(), style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.primary) }
            }
            val type = state.type ?: return@Column
            type.titleFields.takeIf { it.isNotEmpty() }?.let { titleFields ->
                PmItemFields(titleFields, draft.values, actions.onValueChange, suggestions = state.suggestions)
            }
            PmTextField(
                value = draft.name,
                onValueChange = actions.onNameChange,
                label = stringResource(R.string.feature_catalog_name),
                placeholder = stringResource(R.string.feature_catalog_name_hint),
                error = when {
                    form.nameTaken -> stringResource(R.string.feature_catalog_error_taken, state.title)
                    ItemDraftError.NAME_REQUIRED in form.errors -> stringResource(R.string.feature_catalog_error_name)
                    else -> null
                },
                capitalization = KeyboardCapitalization.None,
            )
            val otherFields = type.fields.filterNot { it.inTitle }
            if (otherFields.isNotEmpty()) {
                PmItemFields(otherFields, draft.values, actions.onValueChange, suggestions = state.suggestions)
            }

            state.sections.forEach { section -> LinkSectionView(section, actions) }

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
        ItemEditDialog.DISCARD -> PmConfirmDialog(
            title = stringResource(R.string.feature_catalog_discard_title),
            message = null,
            confirmText = stringResource(R.string.feature_catalog_discard),
            dismissText = stringResource(R.string.feature_catalog_keep_editing),
            onConfirm = actions.onDiscardConfirmed,
            onDismiss = actions.onDismissDialog,
            destructive = true,
        )
        ItemEditDialog.CONFIRM_DELETE -> PmConfirmDialog(
            title = stringResource(R.string.feature_catalog_delete_title),
            message = state.title,
            confirmText = stringResource(R.string.feature_catalog_delete),
            dismissText = stringResource(R.string.feature_catalog_cancel),
            onConfirm = actions.onConfirmDelete,
            onDismiss = actions.onDismissDialog,
            destructive = true,
        )
        ItemEditDialog.IN_USE -> PmMessageDialog(
            title = stringResource(R.string.feature_catalog_in_use_title),
            message = stringResource(R.string.feature_catalog_in_use_message),
            okText = stringResource(R.string.feature_catalog_ok),
            onDismiss = actions.onDismissDialog,
        )
        null -> Unit
    }
}

/** Linked items as removable chips, a field to type a new one, and matching catalog items to tap. */
@Composable
private fun LinkSectionView(section: LinkSection, actions: ItemEditActions) {
    SectionTitle(
        if (section.isParent) {
            stringResource(R.string.feature_catalog_section_fits_into, section.type.name.uppercase())
        } else {
            stringResource(R.string.feature_catalog_section_parts, section.type.name.uppercase())
        },
    )
    if (section.links.isNotEmpty()) {
        PmCard(Modifier.fillMaxWidth()) {
            section.links.forEachIndexed { index, link ->
                if (index > 0) HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                // A part opens its own form (with its own parts); what it goes into is only listed.
                val open = if (section.isParent) Modifier else Modifier.clickable { actions.onOpenPart(section, link) }
                Row(open.fillMaxWidth().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(link.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (!section.isParent) Icon(PmIcons.Chevron, contentDescription = stringResource(R.string.feature_catalog_open_part, link.title), tint = PmTheme.colors.outlineStrong)
                    IconButton(onClick = { actions.onRemoveLink(section, link.key) }) {
                        Icon(PmIcons.Close, contentDescription = stringResource(R.string.feature_catalog_remove_link, link.title), tint = PmTheme.colors.inkMuted)
                    }
                }
            }
        }
    }
    if (!section.isParent) {
        PmSecondaryButton(
            text = stringResource(R.string.feature_catalog_add_part, section.type.name),
            onClick = { actions.onOpenPart(section, null) },
            icon = PmIcons.Add,
            modifier = Modifier.fillMaxWidth(),
        )
    } else Row(verticalAlignment = Alignment.CenterVertically) {
        PmTextField(
            value = section.query,
            onValueChange = { actions.onLinkQueryChange(section.type.id, it) },
            label = stringResource(R.string.feature_catalog_add_link, section.type.name),
            placeholder = stringResource(R.string.feature_catalog_add_link_hint),
            capitalization = KeyboardCapitalization.None,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { actions.onAddLink(section, null) }, enabled = section.query.isNotBlank()) {
            Icon(PmIcons.Add, contentDescription = stringResource(R.string.feature_catalog_add_link, section.type.name), tint = PmTheme.colors.primary)
        }
    }
    if (section.candidates.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            section.candidates.forEach { ref -> PmFilterChip(text = ref.title, selected = false, onClick = { actions.onAddLink(section, ref) }) }
        }
    }
}

@Composable
private fun OwnersCard(owners: List<ItemOwner>, onOpenClient: (Long) -> Unit) {
    PmCard(Modifier.fillMaxWidth()) {
        owners.forEachIndexed { index, owner ->
            if (index > 0) HorizontalDivider(color = PmTheme.colors.surfaceMuted)
            Row(
                Modifier.fillMaxWidth().clickable { onOpenClient(owner.clientId) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(owner.clientName, style = MaterialTheme.typography.bodyLarge)
                    if (owner.place.isNotBlank()) {
                        Text(owner.place, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
                    }
                }
                Icon(PmIcons.Chevron, contentDescription = null, tint = PmTheme.colors.outlineStrong)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, modifier = Modifier.padding(top = 8.dp, start = 2.dp), style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted)
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun ItemEditScreenPreview() {
    val printer = ItemType(
        1, "Տպիչ",
        fields = listOf(
            ItemField(1, 1, "Բրենդ", inTitle = true),
            ItemField(2, 1, "Տպման տեսակ", FieldKind.CHOICE, listOf("Լազերային", "Թանաքային")),
            ItemField(3, 1, "Wi-Fi", FieldKind.YES_NO),
        ),
    )
    val cartridge = ItemType(2, "Քարտրիջ", 1)
    PmTheme {
        ItemEditScreen(
            state = ItemEditUiState(
                form = ItemForm(draft = ItemDraft(id = 1, typeId = 1, name = "LaserJet M125", values = mapOf(1L to "HP", 2L to "Լազերային"))),
                types = listOf(printer, cartridge),
                type = printer,
                sections = listOf(
                    LinkSection(cartridge, false, listOf(LinkDraft(5, 2, "CF283A")), "", listOf(ItemRef(6, 2, "Քարտրիջ", "CF283X"))),
                ),
                suggestions = mapOf(1L to listOf("HP", "Canon", "Samsung")),
                owners = listOf(ItemOwner(1, "«ԱԲԳ Սերվիս» ՍՊԸ", "Հաշվապահություն")),
            ),
            actions = ItemEditActions(),
        )
    }
}
