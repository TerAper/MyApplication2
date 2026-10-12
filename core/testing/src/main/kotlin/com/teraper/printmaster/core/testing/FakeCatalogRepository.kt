package com.teraper.printmaster.core.testing

import com.teraper.printmaster.core.data.repository.CatalogRepository
import com.teraper.printmaster.core.data.repository.DeleteResult
import com.teraper.printmaster.core.data.repository.SaveItemResult
import com.teraper.printmaster.core.data.repository.SaveTypeResult
import com.teraper.printmaster.core.model.CatalogEntry
import com.teraper.printmaster.core.model.CatalogItem
import com.teraper.printmaster.core.model.ItemDraft
import com.teraper.printmaster.core.model.ItemOwner
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.core.model.ItemTypeDraft
import com.teraper.printmaster.core.model.TypeTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Types and items kept in memory; saves are recorded, not applied. */
class FakeCatalogRepository(types: List<ItemType> = emptyList(), items: List<CatalogEntry> = emptyList()) : CatalogRepository {
    val types = MutableStateFlow(types)
    val entries = MutableStateFlow(items)
    val owners = MutableStateFlow<List<ItemOwner>>(emptyList())
    val savedTypes = mutableListOf<ItemTypeDraft>()
    val savedItems = mutableListOf<ItemDraft>()
    val deletedItems = mutableListOf<Long>()
    val deletedTypes = mutableListOf<Long>()
    val templates = mutableListOf<List<TypeTemplate>>()

    /** What the next delete answers. */
    var deleteResult = DeleteResult.DELETED

    /** What the next item save answers when the draft is valid; null = Saved. */
    var saveItemResult: SaveItemResult? = null

    override fun observeTypes(): Flow<List<ItemType>> = types

    override suspend fun saveType(draft: ItemTypeDraft): SaveTypeResult {
        val errors = draft.validate()
        if (errors.isNotEmpty()) return SaveTypeResult.Invalid(errors)
        savedTypes += draft
        return SaveTypeResult.Saved(if (draft.isNew) 99 else draft.id)
    }

    override suspend fun deleteType(id: Long): DeleteResult {
        if (deleteResult == DeleteResult.DELETED) deletedTypes += id
        return deleteResult
    }

    override suspend fun applyTemplate(types: List<TypeTemplate>): Int {
        templates += types
        return types.size
    }

    override fun observeCatalog(): Flow<List<CatalogEntry>> = entries

    override fun observeItem(id: Long): Flow<CatalogItem?> = entries.map { list -> list.firstOrNull { it.item.id == id }?.item }

    override fun observeOwners(itemId: Long): Flow<List<ItemOwner>> = owners

    override suspend fun saveItem(draft: ItemDraft): SaveItemResult {
        val errors = draft.validate()
        if (errors.isNotEmpty()) return SaveItemResult.Invalid(errors)
        saveItemResult?.let { return it }
        savedItems += draft
        return SaveItemResult.Saved(if (draft.isNew) 99 else draft.id)
    }

    override suspend fun deleteItem(id: Long): DeleteResult {
        if (deleteResult == DeleteResult.DELETED) deletedItems += id
        return deleteResult
    }
}
