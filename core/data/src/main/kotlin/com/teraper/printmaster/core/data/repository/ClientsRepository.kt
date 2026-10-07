package com.teraper.printmaster.core.data.repository

import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.ClientDraftError
import com.teraper.printmaster.core.model.ClientSummary
import kotlinx.coroutines.flow.Flow

interface ClientsRepository {

    /** All clients sorted by name, with balances and printer counts. Updates live. */
    fun observeClientSummaries(): Flow<List<ClientSummary>>

    /** One client, or null when it doesn't exist (e.g. was just deleted). */
    fun observeClientSummary(id: Long): Flow<ClientSummary?>

    suspend fun saveClient(draft: ClientDraft): SaveClientResult

    suspend fun deleteClient(id: Long): DeleteClientResult
}

sealed interface SaveClientResult {
    data class Saved(val clientId: Long) : SaveClientResult
    data class Invalid(val errors: Set<ClientDraftError>) : SaveClientResult
    /** Another client already has this ՀՎՀՀ. */
    data class TaxIdTaken(val otherClientName: String) : SaveClientResult
}

enum class DeleteClientResult {
    DELETED,
    /** The client has orders, invoices or payments; deleting would lose money history. */
    HAS_RECORDS,
    NOT_FOUND,
}
