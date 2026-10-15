package com.teraper.printmaster.feature.catalog.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.CatalogRepository
import com.teraper.printmaster.core.model.CatalogEntry
import com.teraper.printmaster.core.model.CatalogSearch
import com.teraper.printmaster.core.model.ItemType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Items of one type, shown under a type header. */
data class TypeGroup(val type: ItemType, val items: List<CatalogEntry>)

data class CatalogUiState(
    val isLoading: Boolean = true,
    val types: List<ItemType> = emptyList(),
    /** Only this type's items; null = all. */
    val typeFilter: Long? = null,
    val totalCount: Int = 0,
    val groups: List<TypeGroup> = emptyList(),
)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    private val _scans = Channel<CatalogScan>(Channel.BUFFERED)

    /** What a scanned code is: an item to open, or a new item to add with it. */
    val scans: Flow<CatalogScan> = _scans.receiveAsFlow()

    fun onScanned(code: String) {
        viewModelScope.launch {
            val item = catalogRepository.findByBarcode(code)
            _scans.send(if (item != null) CatalogScan.Found(item.id) else CatalogScan.New(code.trim()))
        }
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val typeFilter = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<CatalogUiState> = combine(
        catalogRepository.observeTypes(),
        catalogRepository.observeCatalog(),
        _query,
        typeFilter,
    ) { allTypes, catalog, query, filter ->
        // One-of-a-kind types (a child) are filled in on clients, never in the catalog.
        val types = allTypes.filterNot { it.perClient }
        val shownFilter = filter?.takeIf { id -> types.any { it.id == id } }
        CatalogUiState(
            isLoading = false,
            types = types,
            typeFilter = shownFilter,
            totalCount = catalog.size,
            // The repository already sorts by type, so grouping keeps that order.
            groups = catalog
                .filter { (shownFilter == null || it.item.typeId == shownFilter) && CatalogSearch.matches(it.item, query) }
                .groupBy { it.item.typeId }
                .values
                .map { TypeGroup(it.first().item.type, it) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogUiState())

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun onTypeFilter(typeId: Long?) {
        typeFilter.value = typeId
    }
}

sealed interface CatalogScan {
    data class Found(val itemId: Long) : CatalogScan
    data class New(val barcode: String) : CatalogScan
}
