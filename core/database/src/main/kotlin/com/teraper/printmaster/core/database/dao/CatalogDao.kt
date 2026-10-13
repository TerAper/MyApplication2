package com.teraper.printmaster.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.teraper.printmaster.core.database.entity.ClientItemEntity
import com.teraper.printmaster.core.database.entity.ClientItemPartEntity
import com.teraper.printmaster.core.database.entity.ClientItemPartValueEntity
import com.teraper.printmaster.core.database.entity.ClientItemValueEntity
import com.teraper.printmaster.core.database.entity.ItemEntity
import com.teraper.printmaster.core.database.entity.ItemFieldEntity
import com.teraper.printmaster.core.database.entity.ItemFitEntity
import com.teraper.printmaster.core.database.entity.ItemTypeEntity
import com.teraper.printmaster.core.database.entity.ItemValueEntity
import com.teraper.printmaster.core.database.model.IdCount
import com.teraper.printmaster.core.database.model.ItemOwnerRow
import kotlinx.coroutines.flow.Flow

/**
 * The customer's item types and fields, the catalog of items, and what clients own.
 * The catalog is small, so it is read whole and put together in the repository.
 */
@Dao
interface CatalogDao {

    // ---- Types and fields ----

    @Query("SELECT * FROM item_types")
    fun observeTypes(): Flow<List<ItemTypeEntity>>

    @Query("SELECT * FROM item_fields ORDER BY position, id")
    fun observeFields(): Flow<List<ItemFieldEntity>>

    @Query("SELECT * FROM item_types")
    suspend fun getTypes(): List<ItemTypeEntity>

    @Query("SELECT * FROM item_fields ORDER BY position, id")
    suspend fun getFields(): List<ItemFieldEntity>

    @Insert
    suspend fun insertType(type: ItemTypeEntity): Long

    @Update
    suspend fun updateType(type: ItemTypeEntity)

    @Query("DELETE FROM item_types WHERE id = :id")
    suspend fun deleteType(id: Long)

    @Query("SELECT COUNT(*) FROM items WHERE type_id = :typeId")
    suspend fun countItemsOfType(typeId: Long): Int

    @Insert
    suspend fun insertField(field: ItemFieldEntity): Long

    @Update
    suspend fun updateField(field: ItemFieldEntity)

    @Query("DELETE FROM item_fields WHERE type_id = :typeId AND id NOT IN (:keepIds)")
    suspend fun deleteFieldsExcept(typeId: Long, keepIds: List<Long>)

    // ---- Catalog items ----

    @Query("SELECT * FROM items")
    fun observeItems(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM item_values")
    fun observeValues(): Flow<List<ItemValueEntity>>

    @Query("SELECT * FROM item_fits")
    fun observeFits(): Flow<List<ItemFitEntity>>

    @Query("SELECT * FROM items")
    suspend fun getItems(): List<ItemEntity>

    @Query("SELECT * FROM item_values")
    suspend fun getValues(): List<ItemValueEntity>

    @Query("SELECT * FROM item_fits")
    suspend fun getFits(): List<ItemFitEntity>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getItem(id: Long): ItemEntity?

    @Query("SELECT item_id AS id, COUNT(*) AS count FROM client_items WHERE item_id IS NOT NULL GROUP BY item_id")
    fun observeOwnedCounts(): Flow<List<IdCount>>

    @Query(
        """
        SELECT c.id AS client_id, c.name AS client_name, i.place AS place
        FROM client_items i JOIN clients c ON c.id = i.client_id
        WHERE i.item_id = :itemId
        ORDER BY c.name
        """,
    )
    fun observeOwners(itemId: Long): Flow<List<ItemOwnerRow>>

    /** Client items and parts of this model: it can't leave the catalog while they exist. */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM client_items WHERE item_id = :itemId)
             + (SELECT COUNT(*) FROM client_item_parts WHERE item_id = :itemId)
             + (SELECT COUNT(*) FROM stock_moves WHERE item_id = :itemId)
        """,
    )
    suspend fun countUses(itemId: Long): Int

    @Insert
    suspend fun insertItem(item: ItemEntity): Long

    @Update
    suspend fun updateItem(item: ItemEntity)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertValues(values: List<ItemValueEntity>)

    @Query("DELETE FROM item_values WHERE item_id = :itemId AND field_id NOT IN (:keepFieldIds)")
    suspend fun deleteValuesExcept(itemId: Long, keepFieldIds: List<Long>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkFits(links: List<ItemFitEntity>)

    /** Unlinks what [itemId] goes into, except [keepIds]. */
    @Query("DELETE FROM item_fits WHERE item_id = :itemId AND into_id NOT IN (:keepIds)")
    suspend fun unlinkIntoExcept(itemId: Long, keepIds: List<Long>)

    /** Unlinks the parts that go into [intoId], except [keepIds]. */
    @Query("DELETE FROM item_fits WHERE into_id = :intoId AND item_id NOT IN (:keepIds)")
    suspend fun unlinkPartsExcept(intoId: Long, keepIds: List<Long>)

    // ---- Client items ----

    @Query("SELECT * FROM client_items WHERE client_id = :clientId ORDER BY id")
    fun observeClientItems(clientId: Long): Flow<List<ClientItemEntity>>

    @Query("SELECT p.* FROM client_item_parts p JOIN client_items i ON i.id = p.client_item_id WHERE i.client_id = :clientId ORDER BY p.id")
    fun observeClientItemParts(clientId: Long): Flow<List<ClientItemPartEntity>>

    @Query("SELECT * FROM client_items WHERE id = :id")
    suspend fun getClientItem(id: Long): ClientItemEntity?

    @Query("SELECT * FROM client_items WHERE client_id = :clientId ORDER BY id")
    suspend fun getClientItems(clientId: Long): List<ClientItemEntity>

    @Query("SELECT * FROM client_item_parts WHERE client_item_id = :clientItemId ORDER BY id")
    suspend fun getClientItemParts(clientItemId: Long): List<ClientItemPartEntity>

    @Query("SELECT v.* FROM client_item_values v JOIN client_items i ON i.id = v.client_item_id WHERE i.client_id = :clientId")
    fun observeClientItemValues(clientId: Long): Flow<List<ClientItemValueEntity>>

    @Query(
        """
        SELECT v.* FROM client_item_part_values v
        JOIN client_item_parts p ON p.id = v.part_id JOIN client_items i ON i.id = p.client_item_id
        WHERE i.client_id = :clientId
        """,
    )
    fun observeClientPartValues(clientId: Long): Flow<List<ClientItemPartValueEntity>>

    @Query("SELECT * FROM client_item_values WHERE client_item_id = :clientItemId")
    suspend fun getClientItemValues(clientItemId: Long): List<ClientItemValueEntity>

    @Query("SELECT v.* FROM client_item_part_values v JOIN client_item_parts p ON p.id = v.part_id WHERE p.client_item_id = :clientItemId")
    suspend fun getClientPartValues(clientItemId: Long): List<ClientItemPartValueEntity>

    @Query("DELETE FROM client_item_values WHERE client_item_id = :clientItemId")
    suspend fun deleteClientItemValues(clientItemId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClientItemValues(values: List<ClientItemValueEntity>)

    @Query("DELETE FROM client_item_part_values WHERE part_id = :partId")
    suspend fun deleteClientPartValues(partId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClientPartValues(values: List<ClientItemPartValueEntity>)

    @Insert
    suspend fun insertClientItemPart(row: ClientItemPartEntity): Long

    @Update
    suspend fun updateClientItemPart(row: ClientItemPartEntity)

    /** Items whose serial is [key] exactly, or contains it when [partial] (a part of the label read). */
    @Query(
        """
        SELECT * FROM client_items WHERE serial_key = :key
           OR (:partial AND length(:key) >= 4 AND serial_key LIKE '%' || :key || '%')
        ORDER BY serial_key = :key DESC, id
        """,
    )
    suspend fun findClientItemsBySerial(key: String, partial: Boolean): List<ClientItemEntity>

    @Insert
    suspend fun insertClientItem(item: ClientItemEntity): Long

    @Update
    suspend fun updateClientItem(item: ClientItemEntity)

    @Query("DELETE FROM client_items WHERE id = :id")
    suspend fun deleteClientItem(id: Long): Int

    @Insert
    suspend fun insertClientItemParts(rows: List<ClientItemPartEntity>)

    @Query("DELETE FROM client_item_parts WHERE client_item_id = :clientItemId AND id NOT IN (:keepIds)")
    suspend fun deleteClientItemPartsExcept(clientItemId: Long, keepIds: List<Long>)
}
