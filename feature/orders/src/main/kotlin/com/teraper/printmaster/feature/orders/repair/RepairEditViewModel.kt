package com.teraper.printmaster.feature.orders.repair

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.OrdersRepository
import com.teraper.printmaster.core.data.repository.PriceListRepository
import com.teraper.printmaster.core.data.repository.CatalogRepository
import com.teraper.printmaster.core.data.repository.ClientItemsRepository
import com.teraper.printmaster.core.data.repository.RepairsRepository
import com.teraper.printmaster.core.data.repository.SaveRepairResult
import com.teraper.printmaster.core.data.repository.WarehouseRepository
import com.teraper.printmaster.core.model.ClientItem
import com.teraper.printmaster.core.model.StockMatch
import com.teraper.printmaster.core.model.CatalogSearch
import com.teraper.printmaster.core.model.CatalogItem
import com.teraper.printmaster.core.model.CatalogEntry
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.PriceItem
import com.teraper.printmaster.core.model.PriceItemDraft
import com.teraper.printmaster.core.model.PriceListSearch
import com.teraper.printmaster.core.model.RepairDraft
import com.teraper.printmaster.core.model.RepairDraftError
import com.teraper.printmaster.core.model.RepairLine
import com.teraper.printmaster.feature.orders.navigation.ORDER_ID_ARG
import com.teraper.printmaster.feature.orders.navigation.REPAIR_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The price-list sheet: open or not, and how it is filtered. [group] is an item type's id,
 * [PricePicker.GENERAL] for work not tied to a type, null = everything.
 */
data class PricePicker(val group: Long? = null, val query: String = "", val goods: Boolean = false) {
    companion object {
        const val GENERAL = 0L
    }
}

data class RepairForm(
    val isLoading: Boolean = true,
    val draft: RepairDraft,
    val errors: Set<RepairDraftError> = emptySet(),
    val isSaving: Boolean = false,
    val hasChanges: Boolean = false,
    val dialog: RepairEditDialog? = null,
    /** null = closed. */
    val picker: PricePicker? = null,
)

data class RepairEditUiState(
    val form: RepairForm,
    val items: List<ClientItem> = emptyList(),
    /** Items shown on the open price-list sheet. */
    val pickerItems: List<PriceItem> = emptyList(),
    /** The price list's groups: types that have prices, and whether some prices have no type. */
    val pickerTypes: List<ItemType> = emptyList(),
    val hasGeneralPrices: Boolean = false,
    /** Catalog items with a price, for the Goods side of the sheet; those that fit the device first. */
    val pickerGoods: List<CatalogItem> = emptyList(),
    /** Of [pickerGoods], the ids that fit the chosen item or part. */
    val fittingGoods: Set<Long> = emptySet(),
    /** Goods can be sold on this order: an own company's, with priced catalog items. */
    val goodsAvailable: Boolean = false,
    /** How many of each goods item the order's warehouse has. */
    val goodsStock: Map<Long, Int> = emptyMap(),
    val priceListIsEmpty: Boolean = false,
) {
    val isNew: Boolean get() = form.draft.isNew
}

sealed interface RepairEditDialog {
    data object Discard : RepairEditDialog
    data object ConfirmDelete : RepairEditDialog

    /** The order was finished meanwhile; nothing can be saved. */
    data object Locked : RepairEditDialog
    data object CustomLine : RepairEditDialog
    data class LinePrice(val index: Int) : RepairEditDialog
}

sealed interface RepairEditEvent {
    data object Close : RepairEditEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RepairEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    ordersRepository: OrdersRepository,
    clientItemsRepository: ClientItemsRepository,
    catalogRepository: CatalogRepository,
    priceListRepository: PriceListRepository,
    private val repairsRepository: RepairsRepository,
    warehouseRepository: WarehouseRepository,
) : ViewModel() {

    private val orderId: Long = checkNotNull(savedStateHandle[ORDER_ID_ARG])
    private val repairId: Long = savedStateHandle[REPAIR_ID_ARG] ?: 0L
    private var initialDraft = RepairDraft(id = repairId, orderId = orderId)
    private var triedToSave = false

    private val form = MutableStateFlow(RepairForm(draft = initialDraft))
    private val clientId = MutableStateFlow<Long?>(null)
    private val companyId = MutableStateFlow<Long?>(null)
    /** Another owner's order is priced from their list only: no goods from this phone's catalog. */
    private val ownOrder = MutableStateFlow(false)

    private val _events = Channel<RepairEditEvent>(Channel.BUFFERED)
    val events: Flow<RepairEditEvent> = _events.receiveAsFlow()

    private val clientItems = clientId.filterNotNull().flatMapLatest { clientItemsRepository.observeClientItems(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val goodsStock = companyId.filterNotNull().flatMapLatest { warehouseRepository.observeCountsFor(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val uiState: StateFlow<RepairEditUiState> = combine(
        form,
        clientItems,
        // An attached company's order is priced from that company's list.
        companyId.filterNotNull().flatMapLatest { priceListRepository.observeItemsFor(it) },
        combine(catalogRepository.observeTypes(), catalogRepository.observeCatalog(), ownOrder, ::Catalog),
        goodsStock,
    ) { form, clientItems, items, catalog, stock ->
        val types = catalog.types
        val goods = if (catalog.own) catalog.entries.map { it.item }.filter { it.isGoods } else emptyList()
        val fitting = fittingGoods(form.draft, clientItems.orEmpty(), goods)
        RepairEditUiState(
            form = form,
            items = clientItems.orEmpty(),
            pickerItems = form.picker?.let { picker ->
                items.filter { it.inGroup(picker.group) && PriceListSearch.matches(it, picker.query) }
            }.orEmpty(),
            pickerTypes = types.filter { type -> items.any { it.typeId == type.id } },
            hasGeneralPrices = items.any { it.typeId == null },
            priceListIsEmpty = items.isEmpty(),
            goodsAvailable = goods.isNotEmpty(),
            goodsStock = stock,
            fittingGoods = fitting,
            pickerGoods = form.picker?.takeIf { it.goods }?.let { picker ->
                goods.filter { CatalogSearch.matches(it, picker.query) }.sortedByDescending { it.id in fitting }
            }.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RepairEditUiState(form.value))

    init {
        viewModelScope.launch {
            val order = ordersRepository.observeOrder(orderId).first()
            if (order == null) {
                _events.send(RepairEditEvent.Close)
                return@launch
            }
            clientId.value = order.clientId
            companyId.value = order.companyId
            ownOrder.value = !order.fromAttachedCompany
            val owned = clientItems.filterNotNull().first()
            initialDraft = if (repairId != 0L) {
                val repair = repairsRepository.observeRepair(repairId).first()
                if (repair == null) {
                    _events.send(RepairEditEvent.Close)
                    return@launch
                }
                RepairDraft.from(repair)
            } else {
                // A client with one item: that's almost always the one worked on.
                owned.singleOrNull()?.let { initialDraft.withDevice(it.id) } ?: initialDraft
            }
            form.update { it.copy(isLoading = false, draft = initialDraft) }
        }
    }

    /** Tapping the selected device again clears it. */
    fun onDeviceClick(itemId: Long?, partId: Long? = null) = editDraft { draft ->
        if (draft.itemId == itemId && draft.itemPartId == partId && itemId != null) {
            if (partId != null) draft.withDevice(itemId) else draft.withDevice(null)
        } else {
            draft.withDevice(itemId, partId)
        }
    }

    fun onNoteChange(note: String) = editDraft { it.copy(note = note) }

    fun onQuantityChange(index: Int, quantity: Int) = editDraft { it.withQuantity(index, quantity) }

    /** Opens on the prices for the chosen item or part's type, when there are some. */
    fun onOpenPicker() {
        val state = uiState.value
        val device = state.items.firstOrNull { it.id == state.form.draft.itemId }
        val typeId = (device?.parts?.firstOrNull { it.id == state.form.draft.itemPartId }?.item ?: device?.item)?.typeId
        form.update { it.copy(picker = PricePicker(group = typeId?.takeIf { id -> state.pickerTypes.any { t -> t.id == id } })) }
    }

    fun onPickerGroupClick(group: Long?) =
        form.update { it.copy(picker = it.picker?.copy(group = if (it.picker.group == group) null else group)) }

    fun onPickerQueryChange(query: String) = form.update { it.copy(picker = it.picker?.copy(query = query)) }

    fun onPickItem(item: PriceItem) = editDraft { it.plus(item) }

    /** More than the warehouse has is marked "to buy". */
    fun onPickGoods(goods: CatalogItem) = editDraft { it.plus(goods, inStock = goodsStock.value[goods.id] ?: 0) }

    fun onToBuyChange(index: Int, toBuy: Boolean) = editDraft { it.withToBuy(index, toBuy) }

    /** Services (price list) or goods (catalog items with a price). */
    fun onPickerGoods(goods: Boolean) = form.update { it.copy(picker = it.picker?.copy(goods = goods)) }

    fun onClosePicker() = form.update { it.copy(picker = null) }

    fun onAddCustomClick() = form.update { it.copy(picker = null, dialog = RepairEditDialog.CustomLine) }

    /** A line that is not in the price list. Ignored without a name or price. */
    fun onAddCustomLine(name: String, priceText: String, costText: String) {
        val price = PriceItemDraft().withPrice(priceText).withCost(costText)
        if (name.isBlank() || !price.price.isPositive) return
        form.update { it.copy(dialog = null) }
        editDraft { it.plus(RepairLine(partId = null, name = name.trim(), price = price.price, cost = price.cost)) }
    }

    fun onLinePriceClick(index: Int) = form.update { it.copy(dialog = RepairEditDialog.LinePrice(index)) }

    fun onLinePriceChange(index: Int, priceText: String) {
        val price = Money.ofDram(PriceItemDraft.amountDigits(priceText).toLongOrNull() ?: 0L)
        form.update { it.copy(dialog = null) }
        editDraft { it.withPrice(index, price) }
    }

    fun onSave() {
        val state = form.value
        if (state.isSaving || state.isLoading) return
        triedToSave = true
        viewModelScope.launch {
            form.update { it.copy(isSaving = true) }
            when (val result = repairsRepository.saveRepair(state.draft)) {
                is SaveRepairResult.Saved -> {
                    form.update { it.copy(isSaving = false, hasChanges = false) }
                    _events.send(RepairEditEvent.Close)
                }
                is SaveRepairResult.Invalid -> form.update { it.copy(isSaving = false, errors = result.errors) }
                SaveRepairResult.Locked -> form.update { it.copy(isSaving = false, dialog = RepairEditDialog.Locked) }
            }
        }
    }

    fun onDeleteClick() = form.update { it.copy(dialog = RepairEditDialog.ConfirmDelete) }

    fun onConfirmDelete() {
        form.update { it.copy(dialog = null) }
        viewModelScope.launch {
            if (repairsRepository.deleteRepair(repairId)) {
                form.update { it.copy(hasChanges = false) }
                _events.send(RepairEditEvent.Close)
            } else {
                form.update { it.copy(dialog = RepairEditDialog.Locked) }
            }
        }
    }

    fun onCloseRequest() {
        if (form.value.hasChanges) {
            form.update { it.copy(dialog = RepairEditDialog.Discard) }
        } else {
            viewModelScope.launch { _events.send(RepairEditEvent.Close) }
        }
    }

    fun onDiscardConfirmed() {
        form.update { it.copy(dialog = null, hasChanges = false) }
        viewModelScope.launch { _events.send(RepairEditEvent.Close) }
    }

    /** After "the order is finished" the form can't be saved, so it closes. */
    fun onDismissDialog() {
        val wasLocked = form.value.dialog == RepairEditDialog.Locked
        form.update { it.copy(dialog = null) }
        if (wasLocked) viewModelScope.launch { _events.send(RepairEditEvent.Close) }
    }

    private fun editDraft(change: (RepairDraft) -> RepairDraft) = form.update { state ->
        val draft = change(state.draft)
        state.copy(
            draft = draft,
            hasChanges = draft != initialDraft,
            errors = if (triedToSave) draft.validate() else emptySet(),
        )
    }
}

private fun PriceItem.inGroup(group: Long?): Boolean = when (group) {
    null -> true
    PricePicker.GENERAL -> typeId == null
    else -> typeId == group
}

private class Catalog(val types: List<ItemType>, val entries: List<CatalogEntry>, val own: Boolean)

/**
 * Goods that fit what the work is on: the chosen part itself (or ones matching what a described
 * part asks for), else the parts the chosen item's model takes.
 */
private fun fittingGoods(draft: RepairDraft, items: List<ClientItem>, goods: List<CatalogItem>): Set<Long> {
    val item = items.firstOrNull { it.id == draft.itemId } ?: return emptySet()
    val part = item.parts.firstOrNull { it.id == draft.itemPartId }
    return when {
        part != null -> goods.filter { StockMatch.fits(part, it) }.mapTo(mutableSetOf()) { it.id }
        else -> {
            val modelParts = item.item?.parts.orEmpty().mapTo(mutableSetOf()) { it.id }
            goods.filter { g -> g.id in modelParts || item.parts.any { StockMatch.fits(it, g) } }.mapTo(mutableSetOf()) { it.id }
        }
    }
}
