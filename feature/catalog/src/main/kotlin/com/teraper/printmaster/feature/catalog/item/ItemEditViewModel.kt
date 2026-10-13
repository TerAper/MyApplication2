package com.teraper.printmaster.feature.catalog.item

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.CatalogRepository
import com.teraper.printmaster.core.data.repository.DeleteResult
import com.teraper.printmaster.core.data.repository.SaveItemResult
import com.teraper.printmaster.core.designsystem.component.usedValues
import com.teraper.printmaster.core.model.CatalogEntry
import com.teraper.printmaster.core.model.CatalogSearch
import com.teraper.printmaster.core.model.ItemDraft
import com.teraper.printmaster.core.model.ItemDraftError
import com.teraper.printmaster.core.model.ItemOwner
import com.teraper.printmaster.core.model.ItemRef
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.core.model.ItemTypes
import com.teraper.printmaster.core.model.LinkDraft
import com.teraper.printmaster.feature.catalog.navigation.INTO_ID_ARG
import com.teraper.printmaster.feature.catalog.navigation.ITEM_ID_ARG
import com.teraper.printmaster.feature.catalog.navigation.TYPE_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItemForm(
    val isLoading: Boolean = false,
    val draft: ItemDraft = ItemDraft(),
    /** What's typed to add a link, by the linked type's id. */
    val linkQueries: Map<Long, String> = emptyMap(),
    /** Only filled after the first Save tap. */
    val errors: Set<ItemDraftError> = emptySet(),
    val nameTaken: Boolean = false,
    val isSaving: Boolean = false,
    val hasChanges: Boolean = false,
    val dialog: ItemEditDialog? = null,
)

/** Links to items of [type]: what this item goes into ([isParent]) or a kind of its parts. */
data class LinkSection(val type: ItemType, val isParent: Boolean, val links: List<LinkDraft>, val query: String, val candidates: List<ItemRef>)

data class ItemEditUiState(
    val form: ItemForm = ItemForm(),
    val types: List<ItemType> = emptyList(),
    val type: ItemType? = null,
    val sections: List<LinkSection> = emptyList(),
    /** Values used before in each text field of this type. */
    val suggestions: Map<Long, List<String>> = emptyMap(),
    val owners: List<ItemOwner> = emptyList(),
    /** A new part made from its item's form: its type is set. */
    val typeLocked: Boolean = false,
) {
    val isNew: Boolean get() = form.draft.isNew
    val title: String get() = form.draft.title(type)
}

enum class ItemEditDialog { DISCARD, CONFIRM_DELETE, IN_USE }

sealed interface ItemEditEvent {
    data object Close : ItemEditEvent

    /** Open a part's own form: an existing one ([itemId]) or a new one of [typeId] going into [intoId]. */
    data class OpenItem(val itemId: Long, val typeId: Long, val intoId: Long) : ItemEditEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItemEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    /** 0 until the item is saved for the first time. */
    private val itemId = MutableStateFlow<Long>(savedStateHandle[ITEM_ID_ARG] ?: 0L)
    private val intoId: Long = savedStateHandle[INTO_ID_ARG] ?: 0L
    private var initialDraft = ItemDraft(typeId = savedStateHandle[TYPE_ID_ARG] ?: 0L)
    private var triedToSave = false

    private val form = MutableStateFlow(ItemForm(isLoading = itemId.value != 0L || intoId != 0L, draft = initialDraft))

    private val _events = Channel<ItemEditEvent>(Channel.BUFFERED)
    val events: Flow<ItemEditEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<ItemEditUiState> = combine(
        form,
        catalogRepository.observeTypes(),
        catalogRepository.observeCatalog(),
        itemId.flatMapLatest { id -> if (id != 0L) catalogRepository.observeOwners(id) else flowOf(emptyList()) },
    ) { form, types, catalog, owners ->
        val type = types.firstOrNull { it.id == form.draft.typeId }
        ItemEditUiState(
            form = form,
            // One-of-a-kind types (a child) are filled in on clients, never in the catalog.
            types = types.filterNot { it.perClient },
            type = type,
            sections = type?.let { sections(it, types, catalog, form) }.orEmpty(),
            suggestions = type?.let { t -> usedValues(catalog.map { it.item }.filter { it.typeId == t.id }, t.fields) }.orEmpty(),
            owners = owners,
            typeLocked = intoId != 0L,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemEditUiState(form.value))

    init {
        viewModelScope.launch {
            if (itemId.value != 0L) {
                val item = catalogRepository.observeItem(itemId.value).first()
                if (item == null) {
                    _events.send(ItemEditEvent.Close)
                    return@launch
                }
                initialDraft = ItemDraft.from(item)
            } else if (intoId != 0L) {
                // A new part made from its item's form already goes into that item.
                catalogRepository.observeItem(intoId).first()?.let { into -> initialDraft = initialDraft.withFitsInto(LinkDraft.from(into.ref())) }
            }
            form.update { it.copy(isLoading = false, draft = initialDraft) }
            // Parts saved in their own forms show up here; unsaved typing is never overwritten.
            itemId.filter { it != 0L }.flatMapLatest { catalogRepository.observeItem(it) }.collect { item ->
                if (item != null && !form.value.hasChanges && !form.value.isSaving) {
                    initialDraft = ItemDraft.from(item)
                    form.update { it.copy(draft = initialDraft) }
                }
            }
        }
    }

    /** Only for a new item: an existing one keeps its type. */
    fun onTypeChange(typeId: Long) {
        if (form.value.draft.isNew && intoId == 0L) editDraft { it.copy(typeId = typeId, values = emptyMap(), fitsInto = emptyList(), parts = emptyList()) }
    }

    fun onNameChange(name: String) = editDraft { it.copy(name = name) }

    fun onValueChange(fieldId: Long, value: String) = editDraft { it.withValue(fieldId, value) }

    fun onLinkQueryChange(typeId: Long, query: String) = form.update { it.copy(linkQueries = it.linkQueries + (typeId to query)) }

    /** A catalog item picked, or (null) the typed name as a new one. */
    fun onAddLink(section: LinkSection, picked: ItemRef?) {
        val link = picked?.let(LinkDraft::from) ?: LinkDraft(typeId = section.type.id, title = section.query)
        if (link.title.isBlank()) return
        editDraft { if (section.isParent) it.withFitsInto(link) else it.withPart(link) }
        form.update { it.copy(linkQueries = it.linkQueries - section.type.id) }
    }

    fun onRemoveLink(section: LinkSection, key: String) = editDraft { draft ->
        if (section.isParent) draft.copy(fitsInto = draft.fitsInto.filterNot { it.key == key }) else draft.copy(parts = draft.parts.filterNot { it.key == key })
    }

    fun onSave() {
        viewModelScope.launch { if (save()) _events.send(ItemEditEvent.Close) }
    }

    /**
     * A part's own form: tapping a listed part, or "+ Cartridge" for a new one. The item is saved
     * first (a part needs something to go into); with errors the form stays.
     */
    fun onOpenPart(section: LinkSection, link: LinkDraft?) {
        viewModelScope.launch {
            val state = form.value
            if ((state.draft.isNew || state.hasChanges) && !save()) return@launch
            val id = itemId.value
            val partId = link?.let { l ->
                l.id.takeIf { it != 0L } ?: catalogRepository.observeItem(id).first()?.parts?.firstOrNull { LinkDraft.from(it).key == l.key }?.id
            } ?: 0L
            _events.send(ItemEditEvent.OpenItem(partId, section.type.id, if (partId == 0L) id else 0L))
        }
    }

    /** Saves and stays; true when saved. */
    private suspend fun save(): Boolean {
        val state = form.value
        if (state.isSaving || state.isLoading) return false
        triedToSave = true
        // A link typed but not added yet is almost certainly meant to be added.
        uiState.value.sections.filter { it.query.isNotBlank() }.forEach { onAddLink(it, null) }
        val draft = form.value.draft
        form.update { it.copy(isSaving = true) }
        return when (val result = catalogRepository.saveItem(draft)) {
            is SaveItemResult.Saved -> {
                initialDraft = draft.copy(id = result.itemId)
                form.update { it.copy(isSaving = false, hasChanges = false, draft = initialDraft) }
                itemId.value = result.itemId
                true
            }
            is SaveItemResult.Invalid -> {
                form.update { it.copy(isSaving = false, errors = result.errors) }
                false
            }
            SaveItemResult.NameTaken -> {
                form.update { it.copy(isSaving = false, errors = emptySet(), nameTaken = true) }
                false
            }
        }
    }

    fun onDeleteClick() = form.update {
        it.copy(dialog = if (uiState.value.owners.isEmpty()) ItemEditDialog.CONFIRM_DELETE else ItemEditDialog.IN_USE)
    }

    fun onConfirmDelete() {
        form.update { it.copy(dialog = null) }
        viewModelScope.launch {
            when (catalogRepository.deleteItem(itemId.value)) {
                DeleteResult.DELETED, DeleteResult.NOT_FOUND -> {
                    form.update { it.copy(hasChanges = false) }
                    _events.send(ItemEditEvent.Close)
                }
                DeleteResult.IN_USE -> form.update { it.copy(dialog = ItemEditDialog.IN_USE) }
            }
        }
    }

    fun onCloseRequest() {
        if (form.value.hasChanges) {
            form.update { it.copy(dialog = ItemEditDialog.DISCARD) }
        } else {
            viewModelScope.launch { _events.send(ItemEditEvent.Close) }
        }
    }

    fun onDiscardConfirmed() {
        form.update { it.copy(dialog = null, hasChanges = false) }
        viewModelScope.launch { _events.send(ItemEditEvent.Close) }
    }

    fun onDismissDialog() = form.update { it.copy(dialog = null) }

    private fun editDraft(change: (ItemDraft) -> ItemDraft) = form.update { state ->
        val draft = change(state.draft)
        state.copy(
            draft = draft,
            hasChanges = draft != initialDraft,
            errors = if (triedToSave) draft.validate() else emptySet(),
            nameTaken = state.nameTaken && draft.name == state.draft.name && draft.values == state.draft.values,
        )
    }

    /** What it goes into (the parent type), then one section per type of its parts. */
    private fun sections(type: ItemType, types: List<ItemType>, catalog: List<CatalogEntry>, form: ItemForm): List<LinkSection> {
        val parent = types.firstOrNull { it.id == type.parentId }
        val linked = (form.draft.fitsInto + form.draft.parts).mapTo(mutableSetOf()) { it.key }
        fun section(of: ItemType, isParent: Boolean): LinkSection {
            val query = form.linkQueries[of.id].orEmpty()
            val candidates = catalog.asSequence().map { it.item }
                .filter { it.typeId == of.id && it.id != itemId.value && LinkDraft.keyOf(it.typeId, it.title) !in linked }
                .filter { query.isBlank() || CatalogSearch.matches(it, query) }
                .take(MAX_CANDIDATES).map { it.ref() }.toList()
            val links = if (isParent) form.draft.fitsInto else form.draft.parts.filter { it.typeId == of.id }
            return LinkSection(of, isParent, links, query, candidates)
        }
        return listOfNotNull(parent?.let { section(it, isParent = true) }) + ItemTypes.partTypes(types, type.id).map { section(it, isParent = false) }
    }

    private companion object {
        const val MAX_CANDIDATES = 8
    }
}
