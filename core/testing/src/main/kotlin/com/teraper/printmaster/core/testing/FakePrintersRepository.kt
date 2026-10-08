package com.teraper.printmaster.core.testing

import com.teraper.printmaster.core.data.repository.PrintersRepository
import com.teraper.printmaster.core.data.repository.SavePrinterResult
import com.teraper.printmaster.core.model.ClientPrinter
import com.teraper.printmaster.core.model.ClientPrinterDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Records what the form saved; printers are returned from [printers]. */
class FakePrintersRepository(initial: List<ClientPrinter> = emptyList()) : PrintersRepository {
    val printers = MutableStateFlow(initial)
    val saved = mutableListOf<ClientPrinterDraft>()
    val deleted = mutableListOf<Long>()

    override fun observeClientPrinters(clientId: Long): Flow<List<ClientPrinter>> =
        printers.map { list -> list.filter { it.clientId == clientId } }

    override suspend fun getPrinter(id: Long): ClientPrinter? = printers.value.firstOrNull { it.id == id }

    override suspend fun savePrinter(draft: ClientPrinterDraft): SavePrinterResult {
        val errors = draft.validate()
        if (errors.isNotEmpty()) return SavePrinterResult.Invalid(errors)
        saved += draft
        return SavePrinterResult.Saved(if (draft.isNew) 99 else draft.id)
    }

    override suspend fun deletePrinter(id: Long): Boolean {
        deleted += id
        return true
    }
}
