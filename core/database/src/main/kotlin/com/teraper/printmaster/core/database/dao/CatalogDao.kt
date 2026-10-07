package com.teraper.printmaster.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.teraper.printmaster.core.database.entity.BrandEntity
import com.teraper.printmaster.core.database.entity.CartridgeChipCrossRef
import com.teraper.printmaster.core.database.entity.CartridgeEntity
import com.teraper.printmaster.core.database.entity.ChipEntity
import com.teraper.printmaster.core.database.entity.ClientPrinterCartridgeEntity
import com.teraper.printmaster.core.database.entity.ClientPrinterEntity
import com.teraper.printmaster.core.database.entity.ModelCartridgeCrossRef
import com.teraper.printmaster.core.database.entity.PrinterModelEntity
import com.teraper.printmaster.core.database.model.ClientPrinterWithDetails
import com.teraper.printmaster.core.database.model.IdCount
import com.teraper.printmaster.core.database.model.ModelOwnerRow
import com.teraper.printmaster.core.database.model.PrinterModelWithDetails
import kotlinx.coroutines.flow.Flow

/** Printer catalog (brands, models, cartridges, chips) and the printers clients own. */
@Dao
interface CatalogDao {

    // ---- Reading ----

    @Transaction
    @Query("SELECT * FROM printer_models")
    fun observeModels(): Flow<List<PrinterModelWithDetails>>

    @Transaction
    @Query("SELECT * FROM printer_models WHERE id = :id")
    fun observeModel(id: Long): Flow<PrinterModelWithDetails?>

    @Query("SELECT model_id AS id, COUNT(*) AS count FROM client_printers GROUP BY model_id")
    fun observePrinterCountsByModel(): Flow<List<IdCount>>

    @Query(
        """
        SELECT c.id AS client_id, c.name AS client_name, p.location AS location
        FROM client_printers p JOIN clients c ON c.id = p.client_id
        WHERE p.model_id = :modelId
        ORDER BY c.name
        """,
    )
    fun observeModelOwners(modelId: Long): Flow<List<ModelOwnerRow>>

    @Transaction
    @Query("SELECT * FROM client_printers WHERE client_id = :clientId ORDER BY id")
    fun observeClientPrinters(clientId: Long): Flow<List<ClientPrinterWithDetails>>

    @Transaction
    @Query("SELECT * FROM client_printers WHERE id = :id")
    suspend fun getClientPrinter(id: Long): ClientPrinterWithDetails?

    @Query("SELECT * FROM brands")
    suspend fun getBrands(): List<BrandEntity>

    @Query("SELECT * FROM printer_models")
    suspend fun getModelEntities(): List<PrinterModelEntity>

    @Query("SELECT * FROM printer_models WHERE id = :id")
    suspend fun getModelEntity(id: Long): PrinterModelEntity?

    @Query("SELECT * FROM cartridges")
    suspend fun getCartridges(): List<CartridgeEntity>

    @Query("SELECT * FROM chips")
    suspend fun getChips(): List<ChipEntity>

    @Query("SELECT COUNT(*) FROM client_printers WHERE model_id = :modelId")
    suspend fun countPrintersOfModel(modelId: Long): Int

    @Query("SELECT * FROM client_printer_cartridges WHERE client_printer_id = :printerId")
    suspend fun getClientPrinterCartridges(printerId: Long): List<ClientPrinterCartridgeEntity>

    // ---- Catalog writes ----

    @Insert
    suspend fun insertBrand(brand: BrandEntity): Long

    @Insert
    suspend fun insertModel(model: PrinterModelEntity): Long

    @Update
    suspend fun updateModel(model: PrinterModelEntity)

    @Query("DELETE FROM printer_models WHERE id = :id")
    suspend fun deleteModel(id: Long)

    @Insert
    suspend fun insertCartridge(cartridge: CartridgeEntity): Long

    @Query("UPDATE cartridges SET name = :name WHERE id = :id")
    suspend fun renameCartridge(id: Long, name: String)

    @Insert
    suspend fun insertChip(chip: ChipEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkModelCartridges(links: List<ModelCartridgeCrossRef>)

    @Query("DELETE FROM model_cartridges WHERE model_id = :modelId AND cartridge_id NOT IN (:keepIds)")
    suspend fun unlinkModelCartridgesExcept(modelId: Long, keepIds: List<Long>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkCartridgeChips(links: List<CartridgeChipCrossRef>)

    @Query("DELETE FROM cartridge_chips WHERE cartridge_id = :cartridgeId AND chip_id NOT IN (:keepIds)")
    suspend fun unlinkCartridgeChipsExcept(cartridgeId: Long, keepIds: List<Long>)

    // Names nobody uses any more are removed so the catalog doesn't fill with typos.

    @Query("DELETE FROM brands WHERE id NOT IN (SELECT brand_id FROM printer_models)")
    suspend fun deleteUnusedBrands()

    @Query(
        """
        DELETE FROM cartridges
        WHERE id NOT IN (SELECT cartridge_id FROM model_cartridges)
          AND id NOT IN (SELECT cartridge_id FROM client_printer_cartridges)
        """,
    )
    suspend fun deleteUnusedCartridges()

    @Query("DELETE FROM chips WHERE id NOT IN (SELECT chip_id FROM cartridge_chips)")
    suspend fun deleteUnusedChips()

    // ---- Client printers ----

    @Insert
    suspend fun insertClientPrinter(printer: ClientPrinterEntity): Long

    @Update
    suspend fun updateClientPrinter(printer: ClientPrinterEntity)

    @Query("DELETE FROM client_printers WHERE id = :id")
    suspend fun deleteClientPrinter(id: Long): Int

    @Insert
    suspend fun insertClientPrinterCartridges(rows: List<ClientPrinterCartridgeEntity>)

    @Query("DELETE FROM client_printer_cartridges WHERE client_printer_id = :printerId AND id NOT IN (:keepIds)")
    suspend fun deleteClientPrinterCartridgesExcept(printerId: Long, keepIds: List<Long>)
}
