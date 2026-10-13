package com.teraper.printmaster.feature.pricelist.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.PriceListRepository
import com.teraper.printmaster.core.model.PriceItem
import com.teraper.printmaster.core.model.PriceListSearch
import com.teraper.printmaster.core.model.CatalogItem
import com.teraper.printmaster.core.model.CatalogSearch
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.core.data.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Prices for work on items of [type]; null = general work. */
data class PriceGroup(val type: ItemType?, val items: List<PriceItem>)

data class PriceListUiState(
    val isLoading: Boolean = true,
    val totalCount: Int = 0,
    /** The groups to filter by, with how many prices each has. */
    val chips: List<Pair<PriceGroup, Int>> = emptyList(),
    /** An item type's id, [GENERAL], or null = all. */
    val group: Long? = null,
    val query: String = "",
    val groups: List<PriceGroup> = emptyList(),
    /** Goods side: catalog items with a price (matching the search). */
    val showGoods: Boolean = false,
    val goods: List<CatalogItem> = emptyList(),
    val goodsCount: Int = 0,
) {
    companion object {
        const val GENERAL = 0L
    }
}

@HiltViewModel
class PriceListViewModel @Inject constructor(
    priceListRepository: PriceListRepository,
    catalogRepository: CatalogRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(PriceListUiState())

    val uiState: StateFlow<PriceListUiState> = combine(
        priceListRepository.observeItems(),
        catalogRepository.observeTypes(),
        filter,
        catalogRepository.observeCatalog(),
    ) { items, types, filter, catalog ->
        val allGoods = catalog.map { it.item }.filter { it.isGoods }
        // Type order (parents, then their parts), general work last.
        val all = types.map { type -> PriceGroup(type, items.filter { it.typeId == type.id }) } +
            PriceGroup(null, items.filter { item -> types.none { it.id == item.typeId } })
        val inGroup = { group: PriceGroup -> filter.group == null || filter.group == (group.type?.id ?: PriceListUiState.GENERAL) }
        filter.copy(
            isLoading = false,
            totalCount = items.size,
            chips = all.filter { it.items.isNotEmpty() }.map { it to it.items.size },
            groups = all.filter(inGroup)
                .map { group -> group.copy(items = group.items.filter { PriceListSearch.matches(it, filter.query) }) }
                .filter { it.items.isNotEmpty() },
            goodsCount = allGoods.size,
            goods = allGoods.filter { CatalogSearch.matches(it, filter.query) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PriceListUiState())

    fun onQueryChange(query: String) = filter.update { it.copy(query = query) }

    /** Services (the price list) or goods (catalog items with a price). */
    fun onShowGoods(show: Boolean) = filter.update { it.copy(showGoods = show) }

    /** Tapping the selected group again shows all. */
    fun onGroupClick(group: Long?) = filter.update { it.copy(group = if (it.group == group) null else group) }
}
