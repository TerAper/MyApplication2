package com.teraper.printmaster.feature.warehouse.item

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.CatalogRepository
import com.teraper.printmaster.core.data.repository.CompaniesRepository
import com.teraper.printmaster.core.data.repository.SaveStockResult
import com.teraper.printmaster.core.data.repository.WarehouseRepository
import com.teraper.printmaster.core.model.AdjustReason
import com.teraper.printmaster.core.model.CatalogItem
import com.teraper.printmaster.core.model.Company
import com.teraper.printmaster.core.model.CompanyKind
import com.teraper.printmaster.core.model.StockDraft
import com.teraper.printmaster.core.model.StockDraftError
import com.teraper.printmaster.core.model.StockKind
import com.teraper.printmaster.core.model.StockMove
import com.teraper.printmaster.core.model.WarehouseMode
import com.teraper.printmaster.feature.warehouse.navigation.ITEM_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItemStockUiState(
    val isLoading: Boolean = true,
    val item: CatalogItem? = null,
    val count: Int = 0,
    val moves: List<StockMove> = emptyList(),
    /** Company names, for "from / to" of moves. */
    val companyNames: Map<Long, String> = emptyMap(),
    /** Other own companies stock can move to (separate warehouses only). */
    val moveTargets: List<Company> = emptyList(),
    /** The open receive / adjust / move sheet. */
    val draft: StockDraft? = null,
    val errors: Set<StockDraftError> = emptySet(),
) {
    val isLow: Boolean get() = item != null && item.keepAtLeast > 0 && count < item.keepAtLeast
}

@HiltViewModel
class ItemStockViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    catalog: CatalogRepository,
    companies: CompaniesRepository,
    private val warehouse: WarehouseRepository,
) : ViewModel() {

    private val itemId: Long = checkNotNull(savedStateHandle[ITEM_ID_ARG])
    private val sheet = MutableStateFlow<Pair<StockDraft?, Set<StockDraftError>>>(null to emptySet())

    private val companyInfo = combine(companies.observeCompanies(), companies.observeActiveCompany(), warehouse.observeMode()) { all, active, mode ->
        val targets = if (mode == WarehouseMode.PER_COMPANY) all.filter { it.kind == CompanyKind.OWN && it.id != active?.id } else emptyList()
        all.associate { it.id to it.name } to targets
    }

    val uiState: StateFlow<ItemStockUiState> = combine(
        catalog.observeItem(itemId),
        warehouse.observeCount(itemId),
        warehouse.observeMoves(itemId),
        companyInfo,
        sheet,
    ) { item, count, moves, (names, targets), (draft, errors) ->
        ItemStockUiState(false, item, count, moves, names, targets, draft, errors)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemStockUiState())

    /** Receiving starts from what it cost last time. */
    fun onReceive() = open(
        StockDraft(itemId, StockKind.RECEIVE, costDigits = uiState.value.item?.cost?.dram?.takeIf { it > 0 }?.toString().orEmpty()),
    )

    /** Adjusting starts from today's count, to type the counted number over it. */
    fun onAdjust() = open(StockDraft(itemId, StockKind.ADJUST, quantityDigits = uiState.value.count.toString()))

    fun onMove() = open(StockDraft(itemId, StockKind.MOVE, toCompanyId = uiState.value.moveTargets.singleOrNull()?.id))

    fun onQuantityChange(text: String) = edit { it.withQuantity(text) }

    fun onCostChange(text: String) = edit { it.withCost(text) }

    fun onReasonChange(reason: AdjustReason) = edit { it.copy(reason = reason) }

    fun onNoteChange(note: String) = edit { it.copy(note = note) }

    fun onTargetChange(companyId: Long) = edit { it.copy(toCompanyId = companyId) }

    fun onDismiss() = sheet.update { null to emptySet() }

    fun onSave() {
        val draft = sheet.value.first ?: return
        viewModelScope.launch {
            when (val result = warehouse.save(draft)) {
                SaveStockResult.Saved -> sheet.value = null to emptySet()
                is SaveStockResult.Invalid -> sheet.update { it.first to result.errors }
            }
        }
    }

    private fun open(draft: StockDraft) = sheet.update { draft to emptySet() }

    private fun edit(change: (StockDraft) -> StockDraft) = sheet.update { (draft, _) -> draft?.let(change) to emptySet() }
}
