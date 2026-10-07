package com.teraper.printmaster.core.data.repository

import androidx.room.withTransaction
import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.database.dao.CatalogDao
import com.teraper.printmaster.core.database.entity.ClientPrinterCartridgeEntity
import com.teraper.printmaster.core.database.entity.ClientPrinterEntity
import com.teraper.printmaster.core.database.model.CartridgeWithChips
import com.teraper.printmaster.core.database.model.ClientPrinterWithDetails
import com.teraper.printmaster.core.database.model.PrinterModelWithDetails
import com.teraper.printmaster.core.model.Cartridge
import com.teraper.printmaster.core.model.CatalogModel
import com.teraper.printmaster.core.model.CatalogNames
import com.teraper.printmaster.core.model.Chip
import com.teraper.printmaster.core.model.ClientPrinter
import com.teraper.printmaster.core.model.ClientPrinterCartridge
import com.teraper.printmaster.core.model.ClientPrinterDraft
import com.teraper.printmaster.core.model.ModelOwner
import com.teraper.printmaster.core.model.PrinterModel
import com.teraper.printmaster.core.model.PrinterModelDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class OfflineCatalogRepository @Inject constructor(
    private val db: PrintMasterDatabase,
    private val dao: CatalogDao,
    private val writer: CatalogWriter,
) : CatalogRepository {

    override fun observeCatalog(): Flow<List<CatalogModel>> = combine(
        dao.observeModels(),
        dao.observePrinterCountsByModel(),
    ) { models, counts ->
        val countById = counts.associate { it.id to it.count }
        models.map { CatalogModel(it.toModel(), countById[it.model.id] ?: 0) }
            .sortedWith(compareBy({ CatalogNames.key(it.model.brand) }, { CatalogNames.key(it.model.name) }))
    }

    override fun observeModel(id: Long): Flow<PrinterModel?> = dao.observeModel(id).map { it?.toModel() }

    override fun observeModelOwners(modelId: Long): Flow<List<ModelOwner>> =
        dao.observeModelOwners(modelId).map { rows -> rows.map { ModelOwner(it.clientId, it.clientName, it.location) } }

    override suspend fun saveModel(draft: PrinterModelDraft): SaveModelResult {
        val errors = draft.validate()
        if (errors.isNotEmpty()) return SaveModelResult.Invalid(errors)
        val clean = draft.normalized()
        return db.withTransaction {
            val brandId = writer.brandId(clean.brand)
            if (writer.findModel(brandId, clean.name, excludeId = clean.id) != null) {
                // The brand may have just been created for nothing.
                writer.deleteUnused()
                return@withTransaction SaveModelResult.NameTaken
            }
            val modelId = if (clean.isNew) {
                writer.findOrCreateModel(clean)
            } else {
                val current = dao.getModelEntity(clean.id) ?: return@withTransaction SaveModelResult.Invalid(emptySet())
                dao.updateModel(
                    current.copy(brandId = brandId, name = clean.name, printType = clean.printType, colorType = clean.colorType),
                )
                current.id
            }
            writer.linkCartridges(modelId, clean.cartridges, exact = true)
            writer.deleteUnused()
            SaveModelResult.Saved(modelId)
        }
    }

    override suspend fun deleteModel(id: Long): DeleteModelResult = db.withTransaction {
        when {
            dao.getModelEntity(id) == null -> DeleteModelResult.NOT_FOUND
            dao.countPrintersOfModel(id) > 0 -> DeleteModelResult.IN_USE
            else -> {
                dao.deleteModel(id)
                writer.deleteUnused()
                DeleteModelResult.DELETED
            }
        }
    }
}

internal class OfflinePrintersRepository @Inject constructor(
    private val db: PrintMasterDatabase,
    private val dao: CatalogDao,
    private val writer: CatalogWriter,
) : PrintersRepository {

    override fun observeClientPrinters(clientId: Long): Flow<List<ClientPrinter>> =
        dao.observeClientPrinters(clientId).map { rows -> rows.map { it.toClientPrinter() } }

    override suspend fun getPrinter(id: Long): ClientPrinter? = dao.getClientPrinter(id)?.toClientPrinter()

    override suspend fun savePrinter(draft: ClientPrinterDraft): SavePrinterResult {
        val errors = draft.validate()
        if (errors.isNotEmpty()) return SavePrinterResult.Invalid(errors)
        val model = draft.model.normalized()
        val location = CatalogNames.clean(draft.location)
        val note = draft.note.trim()

        return db.withTransaction {
            val modelId = if (model.isNew) writer.findOrCreateModel(model) else model.id
            // Add-only: this form never removes cartridges from the shared catalog model.
            val cartridgeIds = writer.linkCartridges(modelId, model.cartridges, exact = false)
            val chosenIds = model.cartridges.filter { it.key in draft.selectedCartridges }.mapNotNull { cartridgeIds[it.key] }

            val printerId = if (draft.isNew) {
                dao.insertClientPrinter(
                    ClientPrinterEntity(clientId = draft.clientId, modelId = modelId, location = location, note = note),
                )
            } else {
                dao.updateClientPrinter(
                    ClientPrinterEntity(id = draft.id, clientId = draft.clientId, modelId = modelId, location = location, note = note),
                )
                draft.id
            }

            // Rows for cartridges still chosen keep their id, so repairs stay linked to them.
            val existing = dao.getClientPrinterCartridges(printerId)
            val kept = existing.filter { it.cartridgeId in chosenIds }
            dao.deleteClientPrinterCartridgesExcept(printerId, kept.map { it.id })
            val keptCartridgeIds = kept.mapTo(mutableSetOf()) { it.cartridgeId }
            dao.insertClientPrinterCartridges(
                chosenIds.filterNot { it in keptCartridgeIds }.map { ClientPrinterCartridgeEntity(clientPrinterId = printerId, cartridgeId = it) },
            )
            writer.deleteUnused()
            SavePrinterResult.Saved(printerId)
        }
    }

    override suspend fun deletePrinter(id: Long): Boolean = db.withTransaction {
        (dao.deleteClientPrinter(id) > 0).also { writer.deleteUnused() }
    }
}

private fun CartridgeWithChips.toCartridge() = Cartridge(
    id = cartridge.id,
    name = cartridge.name,
    chips = chips.map { Chip(it.id, it.name) }.sortedBy { CatalogNames.codeKey(it.name) },
)

private fun PrinterModelWithDetails.toModel() = PrinterModel(
    id = model.id,
    brandId = brand.id,
    brand = brand.name,
    name = model.name,
    printType = model.printType,
    colorType = model.colorType,
    cartridges = cartridges.map { it.toCartridge() }.sortedBy { CatalogNames.codeKey(it.name) },
)

private fun ClientPrinterWithDetails.toClientPrinter() = ClientPrinter(
    id = printer.id,
    clientId = printer.clientId,
    model = model.toModel(),
    location = printer.location,
    note = printer.note,
    cartridges = cartridges.map { ClientPrinterCartridge(it.row.id, it.cartridge.toCartridge()) }
        .sortedBy { CatalogNames.codeKey(it.cartridge.name) },
)
