package com.teraper.printmaster.feature.warehouse.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.CompaniesRepository
import com.teraper.printmaster.core.data.repository.WarehouseRepository
import com.teraper.printmaster.core.model.CatalogNames
import com.teraper.printmaster.core.model.CatalogSearch
import com.teraper.printmaster.core.model.CompanyKind
import com.teraper.printmaster.core.model.Company
import com.teraper.printmaster.core.model.CompanyStockLine
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.StockFilter
import com.teraper.printmaster.core.model.StockLine
import com.teraper.printmaster.core.model.WarehouseMode
import com.teraper.printmaster.core.model.sum
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.teraper.printmaster.core.data.repository.CatalogRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class WarehouseUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val filter: StockFilter = StockFilter.ALL,
    /** Items to buy first, then what's in stock, then the rest; by name within each. */
    val lines: List<StockLine> = emptyList(),
    val totalCount: Int = 0,
    /** Items with at least one piece. */
    val inStock: Int = 0,
    val lowCount: Int = 0,
    val value: Money = Money.ZERO,
    val mode: WarehouseMode = WarehouseMode.SHARED,
    /** More than one own company: the warehouse can be split per company. */
    val canSplit: Boolean = false,
    val showMode: Boolean = false,
    /** Master's phone: attached companies that share their stock with him. */
    val companies: List<Company> = emptyList(),
    /** The attached company whose stock is shown; null = the user's own warehouse. */
    val companyId: Long? = null,
    /** That company's stock, searched; by name. */
    val companyLines: List<CompanyStockLine> = emptyList(),
    /** The last scanned item, received +1 (it can be undone). */
    val scanned: ScannedIn? = null,
    /** A scanned code no item has: offer to add one. */
    val newCode: String? = null,
)

/** "CF283A: +1 → 5"; [moveId] to take it back. */
data class ScannedIn(val title: String, val count: Int, val moveId: Long)

sealed interface WarehouseEvent {
    /** Received: the scanner opens again for the next one. */
    data object ScanNext : WarehouseEvent

    /** Add a new item with this barcode, then receive it. */
    data class NewItem(val barcode: String) : WarehouseEvent
}

@HiltViewModel
class WarehouseViewModel @Inject constructor(
    private val warehouse: WarehouseRepository,
    companies: CompaniesRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {

    private val ui = MutableStateFlow(WarehouseUiState())

    private val _events = Channel<WarehouseEvent>(Channel.BUFFERED)
    val events: Flow<WarehouseEvent> = _events.receiveAsFlow()

    private val shared = combine(warehouse.observeCompanyStock(), companies.observeAttachedCompanies()) { lines, attached ->
        attached.filter { c -> lines.any { it.companyId == c.id } } to lines
    }

    val uiState: StateFlow<WarehouseUiState> = combine(
        ui,
        warehouse.observeStock(),
        warehouse.observeMode(),
        companies.observeCompanies(),
        shared,
    ) { ui, stock, mode, companies, (sharing, companyStock) ->
        val companyId = ui.companyId?.takeIf { id -> sharing.any { it.id == id } }
        val query = ui.query.trim().lowercase()
        val shown = stock.filter { ui.filter.accepts(it) && CatalogSearch.matches(it.item, ui.query) }
        ui.copy(
            isLoading = false,
            lines = shown.sortedWith(
                compareByDescending<StockLine> { it.isLow }.thenByDescending { it.count > 0 }.thenBy { CatalogNames.key(it.item.title) },
            ),
            totalCount = stock.size,
            inStock = stock.count { it.count > 0 },
            lowCount = stock.count { it.isLow },
            value = stock.map { it.value }.sum(),
            mode = mode,
            canSplit = companies.count { it.kind == CompanyKind.OWN } > 1 || mode == WarehouseMode.PER_COMPANY,
            companies = sharing,
            companyId = companyId,
            companyLines = companyStock.filter { it.companyId == companyId && "${it.name} ${it.type}".lowercase().contains(query) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WarehouseUiState())

    fun onQueryChange(query: String) = ui.update { it.copy(query = query) }

    fun onFilterChange(filter: StockFilter) = ui.update { it.copy(filter = filter) }

    /**
     * Scan, scan, scan: a known item comes in +1 and the scanner opens again; an unknown code
     * (or a one-of-a-kind item, which has no stock) asks to add a new item.
     */
    fun onScanned(code: String) {
        viewModelScope.launch {
            val item = catalog.findByBarcode(code)?.takeUnless { it.type.perClient }
            if (item == null) {
                ui.update { it.copy(newCode = code.trim(), scanned = null) }
                return@launch
            }
            val before = uiState.value.lines.firstOrNull { it.item.id == item.id }?.count
                ?: warehouse.observeStock().first().firstOrNull { it.item.id == item.id }?.count ?: 0
            val moveId = warehouse.scanIn(item.id)
            ui.update { it.copy(scanned = ScannedIn(item.title, before + 1, moveId), companyId = null) }
            _events.send(WarehouseEvent.ScanNext)
        }
    }

    fun onUndoScan() {
        val scanned = ui.value.scanned ?: return
        ui.update { it.copy(scanned = null) }
        viewModelScope.launch { warehouse.undoReceive(scanned.moveId) }
    }

    fun onDismissScanned() = ui.update { it.copy(scanned = null) }

    fun onAddNewCode() {
        val code = ui.value.newCode ?: return
        ui.update { it.copy(newCode = null) }
        viewModelScope.launch { _events.send(WarehouseEvent.NewItem(code)) }
    }

    fun onDismissNewCode() = ui.update { it.copy(newCode = null) }

    /** Null = the user's own warehouse. */
    fun onCompanyChange(companyId: Long?) = ui.update { it.copy(companyId = companyId) }

    fun onShowMode() = ui.update { it.copy(showMode = true) }

    fun onDismissMode() = ui.update { it.copy(showMode = false) }

    fun onModeChange(mode: WarehouseMode) {
        ui.update { it.copy(showMode = false) }
        viewModelScope.launch { warehouse.setMode(mode) }
    }
}
