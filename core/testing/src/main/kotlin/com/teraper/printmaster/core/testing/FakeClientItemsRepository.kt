package com.teraper.printmaster.core.testing

import com.teraper.printmaster.core.data.repository.ClientItemsRepository
import com.teraper.printmaster.core.data.repository.SaveClientItemResult
import com.teraper.printmaster.core.model.ClientItem
import com.teraper.printmaster.core.model.ClientItemDraft
import com.teraper.printmaster.core.model.SerialMatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Records what the form saved; client items are returned from [items]. */
class FakeClientItemsRepository(initial: List<ClientItem> = emptyList()) : ClientItemsRepository {
    val items = MutableStateFlow(initial)
    val saved = mutableListOf<ClientItemDraft>()
    val deleted = mutableListOf<Long>()

    /** What [findBySerial] answers. */
    var serialMatches: List<SerialMatch> = emptyList()

    override fun observeClientItems(clientId: Long): Flow<List<ClientItem>> =
        items.map { list -> list.filter { it.clientId == clientId } }

    override suspend fun getClientItem(id: Long): ClientItem? = items.value.firstOrNull { it.id == id }

    override suspend fun saveClientItem(draft: ClientItemDraft): SaveClientItemResult {
        val errors = draft.validate()
        if (errors.isNotEmpty()) return SaveClientItemResult.Invalid(errors)
        saved += draft
        return SaveClientItemResult.Saved(if (draft.isNew) 99 else draft.id)
    }

    override suspend fun deleteClientItem(id: Long): Boolean {
        deleted += id
        return true
    }

    override suspend fun findBySerial(serial: String) = serialMatches
}
