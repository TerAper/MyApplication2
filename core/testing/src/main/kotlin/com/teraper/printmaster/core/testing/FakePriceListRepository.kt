package com.teraper.printmaster.core.testing

import com.teraper.printmaster.core.data.repository.PriceListRepository
import com.teraper.printmaster.core.data.repository.SavePriceItemResult
import com.teraper.printmaster.core.model.PriceItem
import com.teraper.printmaster.core.model.PriceItemDraft
import com.teraper.printmaster.core.model.PriceListSearch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory price list with the real duplicate-name rule. */
class FakePriceListRepository(initial: List<PriceItem> = emptyList()) : PriceListRepository {
    val items = MutableStateFlow(initial)
    val saved = mutableListOf<PriceItemDraft>()

    override fun observeItems(): Flow<List<PriceItem>> = items

    override fun observeItemsFor(companyId: Long): Flow<List<PriceItem>> = items

    override fun observeItem(id: Long): Flow<PriceItem?> = items.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun saveItem(draft: PriceItemDraft): SavePriceItemResult {
        val errors = draft.validate()
        if (errors.isNotEmpty()) return SavePriceItemResult.Invalid(errors)
        val key = PriceListSearch.key(draft.name)
        if (items.value.any { it.id != draft.id && it.category == draft.category && PriceListSearch.key(it.name) == key }) {
            return SavePriceItemResult.NameTaken
        }
        saved += draft
        val id = if (draft.isNew) (items.value.maxOfOrNull { it.id } ?: 0) + 1 else draft.id
        val item = PriceItem(id, draft.category, draft.name.trim(), draft.description.trim(), draft.price, draft.cost)
        items.value = items.value.filterNot { it.id == id } + item
        return SavePriceItemResult.Saved(id)
    }

    override suspend fun deleteItem(id: Long): Boolean {
        val before = items.value.size
        items.value = items.value.filterNot { it.id == id }
        return items.value.size < before
    }
}
