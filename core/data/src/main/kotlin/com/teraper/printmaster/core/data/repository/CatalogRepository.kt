package com.teraper.printmaster.core.data.repository

import com.teraper.printmaster.core.model.CatalogModel
import com.teraper.printmaster.core.model.ClientPrinter
import com.teraper.printmaster.core.model.ClientPrinterDraft
import com.teraper.printmaster.core.model.ModelOwner
import com.teraper.printmaster.core.model.PrinterDraftError
import com.teraper.printmaster.core.model.PrinterModel
import com.teraper.printmaster.core.model.PrinterModelDraft
import kotlinx.coroutines.flow.Flow

/** The printer catalog: brands, models, cartridges and chips. */
interface CatalogRepository {
    /** Every model, sorted by brand then name, with how many client printers use it. */
    fun observeCatalog(): Flow<List<CatalogModel>>

    fun observeModel(id: Long): Flow<PrinterModel?>

    fun observeModelOwners(modelId: Long): Flow<List<ModelOwner>>

    /** Saves the model exactly as drafted: cartridges and chips not in the draft are unlinked. */
    suspend fun saveModel(draft: PrinterModelDraft): SaveModelResult

    suspend fun deleteModel(id: Long): DeleteModelResult
}

sealed interface SaveModelResult {
    data class Saved(val modelId: Long) : SaveModelResult
    data class Invalid(val errors: Set<PrinterDraftError>) : SaveModelResult
    /** Another model already has this brand and name. */
    data object NameTaken : SaveModelResult
}

enum class DeleteModelResult { DELETED, IN_USE, NOT_FOUND }

/** Printers that clients own. */
interface PrintersRepository {
    fun observeClientPrinters(clientId: Long): Flow<List<ClientPrinter>>

    suspend fun getPrinter(id: Long): ClientPrinter?

    /**
     * Saves the printer. A new model is added to the catalog (or matched to an existing one
     * with the same brand and name); new cartridges are added to the model.
     */
    suspend fun savePrinter(draft: ClientPrinterDraft): SavePrinterResult

    suspend fun deletePrinter(id: Long): Boolean
}

sealed interface SavePrinterResult {
    data class Saved(val printerId: Long) : SavePrinterResult
    data class Invalid(val errors: Set<PrinterDraftError>) : SavePrinterResult
}
