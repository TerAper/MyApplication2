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
)

@HiltViewModel
class WarehouseViewModel @Inject constructor(
    private val warehouse: WarehouseRepository,
    companies: CompaniesRepository,
) : ViewModel() {

    private val ui = MutableStateFlow(WarehouseUiState())

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

    /** Null = the user's own warehouse. */
    fun onCompanyChange(companyId: Long?) = ui.update { it.copy(companyId = companyId) }

    fun onShowMode() = ui.update { it.copy(showMode = true) }

    fun onDismissMode() = ui.update { it.copy(showMode = false) }

    fun onModeChange(mode: WarehouseMode) {
        ui.update { it.copy(showMode = false) }
        viewModelScope.launch { warehouse.setMode(mode) }
    }
}
