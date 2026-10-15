package com.teraper.printmaster.core.data.repository

import com.teraper.printmaster.core.model.CatalogEntry
import com.teraper.printmaster.core.model.CatalogItem
import com.teraper.printmaster.core.model.ClientItem
import com.teraper.printmaster.core.model.ClientItemDraft
import com.teraper.printmaster.core.model.ItemDraft
import com.teraper.printmaster.core.model.ItemDraftError
import com.teraper.printmaster.core.model.ItemOwner
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.core.model.ItemTypeDraft
import com.teraper.printmaster.core.model.ItemTypeError
import com.teraper.printmaster.core.model.SerialMatch
import com.teraper.printmaster.core.model.TypeTemplate
import kotlinx.coroutines.flow.Flow

/** The customer's item types and the catalog of items. */
interface CatalogRepository {
    fun observeTypes(): Flow<List<ItemType>>

    /** Saves the type and its fields; fields not in the draft are removed with their values. */
    suspend fun saveType(draft: ItemTypeDraft): SaveTypeResult

    suspend fun deleteType(id: Long): DeleteResult

    /** Adds the template's types that aren't there yet, and missing fields to those that are. Returns how many types were added. */
    suspend fun applyTemplate(types: List<TypeTemplate>): Int

    /** Every item with how many client items are of it, in type order, then by title. */
    fun observeCatalog(): Flow<List<CatalogEntry>>

    fun observeItem(id: Long): Flow<CatalogItem?>

    fun observeOwners(itemId: Long): Flow<List<ItemOwner>>

    /** Saves the item exactly as drafted: links not in the draft are removed. */
    suspend fun saveItem(draft: ItemDraft): SaveItemResult

    /**
     * Not while clients have it. With warehouse records it answers [DeleteResult.HAS_STOCK]
     * unless [withStock]: then its records (and what's left in stock) go with it.
     */
    suspend fun deleteItem(id: Long, withStock: Boolean = false): DeleteResult

    /** How many are in all warehouses together. */
    suspend fun stockOnHand(id: Long): Int

    /** The catalog item with this barcode, however it was typed or scanned. */
    suspend fun findByBarcode(code: String): CatalogItem?
}

sealed interface SaveTypeResult {
    data class Saved(val typeId: Long) : SaveTypeResult
    data class Invalid(val errors: Set<ItemTypeError>) : SaveTypeResult
    /** Another type already has this name. */
    data object NameTaken : SaveTypeResult
}

sealed interface SaveItemResult {
    data class Saved(val itemId: Long) : SaveItemResult
    data class Invalid(val errors: Set<ItemDraftError>) : SaveItemResult
    /** Another item of this type has the same title. */
    data object NameTaken : SaveItemResult

    /** Another item ([title]) already has this barcode. */
    data class BarcodeTaken(val title: String) : SaveItemResult
}

enum class DeleteResult {
    DELETED, IN_USE, NOT_FOUND,

    /** It has warehouse records; delete again with them to remove it. */
    HAS_STOCK,
}

/** The things clients own. */
interface ClientItemsRepository {
    fun observeClientItems(clientId: Long): Flow<List<ClientItem>>

    suspend fun getClientItem(id: Long): ClientItem?

    /**
     * Saves the client's item. A new model is added to the catalog (or matched to an existing one
     * with the same type and title); new parts are added to the model.
     */
    suspend fun saveClientItem(draft: ClientItemDraft): SaveClientItemResult

    suspend fun deleteClientItem(id: Long): Boolean

    /** Client items with this serial number (exact first), or whose serial contains it. */
    suspend fun findBySerial(serial: String): List<SerialMatch>
}

sealed interface SaveClientItemResult {
    data class Saved(val clientItemId: Long) : SaveClientItemResult
    data class Invalid(val errors: Set<ItemDraftError>) : SaveClientItemResult
}
