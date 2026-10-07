package com.teraper.printmaster.feature.catalog.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.CatalogRepository
import com.teraper.printmaster.core.model.CatalogModel
import com.teraper.printmaster.core.model.CatalogSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Models of one brand, shown under a brand header. */
data class BrandGroup(val brand: String, val models: List<CatalogModel>)

data class CatalogUiState(
    val isLoading: Boolean = true,
    val totalCount: Int = 0,
    val groups: List<BrandGroup> = emptyList(),
)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    catalogRepository: CatalogRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val uiState: StateFlow<CatalogUiState> = combine(catalogRepository.observeCatalog(), _query) { catalog, query ->
        CatalogUiState(
            isLoading = false,
            totalCount = catalog.size,
            // The repository already sorts by brand, so grouping keeps that order.
            groups = catalog
                .filter { CatalogSearch.matches(it.model, query) }
                .groupBy { it.model.brandId }
                .values
                .map { BrandGroup(it.first().model.brand, it) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogUiState())

    fun onQueryChange(query: String) {
        _query.value = query
    }
}
