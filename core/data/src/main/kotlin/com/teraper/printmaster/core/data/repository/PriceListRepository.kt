package com.teraper.printmaster.core.data.repository

import com.teraper.printmaster.core.model.PriceItem
import com.teraper.printmaster.core.model.PriceItemDraft
import com.teraper.printmaster.core.model.PriceItemError
import kotlinx.coroutines.flow.Flow

/** The master's price list. Shared by all companies. */
interface PriceListRepository {
    /** Sorted by category, then name. */
    /** The user's own price list. */
    fun observeItems(): Flow<List<PriceItem>>

    /** For work on an order of [companyId]: an attached company's prices, otherwise the user's own. */
    fun observeItemsFor(companyId: Long): Flow<List<PriceItem>>

    fun observeItem(id: Long): Flow<PriceItem?>

    suspend fun saveItem(draft: PriceItemDraft): SavePriceItemResult

    /** False if it was already gone. Repairs that used it keep their copy of name and price. */
    suspend fun deleteItem(id: Long): Boolean
}

sealed interface SavePriceItemResult {
    data class Saved(val itemId: Long) : SavePriceItemResult
    data class Invalid(val errors: Set<PriceItemError>) : SavePriceItemResult

    /** The same name already exists in that category. */
    data object NameTaken : SavePriceItemResult
}
