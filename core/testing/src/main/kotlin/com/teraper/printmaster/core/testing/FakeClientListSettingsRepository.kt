package com.teraper.printmaster.core.testing

import com.teraper.printmaster.core.data.repository.ClientListSettingsRepository
import com.teraper.printmaster.core.model.ClientSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeClientListSettingsRepository(initial: ClientSort = ClientSort.NAME) : ClientListSettingsRepository {
    val sort = MutableStateFlow(initial)

    override fun observeSort(): Flow<ClientSort> = sort

    override fun setSort(sort: ClientSort) {
        this.sort.value = sort
    }
}
