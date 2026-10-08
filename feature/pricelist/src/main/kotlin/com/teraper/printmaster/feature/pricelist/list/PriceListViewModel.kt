package com.teraper.printmaster.feature.pricelist.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.PriceListRepository
import com.teraper.printmaster.core.model.PriceItem
import com.teraper.printmaster.core.model.PriceListSearch
import com.teraper.printmaster.core.model.RepairCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class PriceGroup(val category: RepairCategory, val items: List<PriceItem>)

data class PriceListUiState(
    val isLoading: Boolean = true,
    val totalCount: Int = 0,
    /** Items in each category, for the filter chips. */
    val counts: Map<RepairCategory, Int> = emptyMap(),
    /** null = all categories. */
    val category: RepairCategory? = null,
    val query: String = "",
    val groups: List<PriceGroup> = emptyList(),
)

@HiltViewModel
class PriceListViewModel @Inject constructor(
    priceListRepository: PriceListRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(PriceListUiState())

    val uiState: StateFlow<PriceListUiState> = combine(priceListRepository.observeItems(), filter) { items, filter ->
        filter.copy(
            isLoading = false,
            totalCount = items.size,
            counts = items.groupingBy { it.category }.eachCount(),
            // The repository already sorts by category, so grouping keeps that order.
            groups = items
                .filter { (filter.category == null || it.category == filter.category) && PriceListSearch.matches(it, filter.query) }
                .groupBy { it.category }
                .map { (category, list) -> PriceGroup(category, list) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PriceListUiState())

    fun onQueryChange(query: String) = filter.update { it.copy(query = query) }

    /** Tapping the selected category again shows all. */
    fun onCategoryClick(category: RepairCategory?) =
        filter.update { it.copy(category = if (it.category == category) null else category) }
}
