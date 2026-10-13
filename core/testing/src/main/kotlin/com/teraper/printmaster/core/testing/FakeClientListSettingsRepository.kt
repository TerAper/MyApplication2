package com.teraper.printmaster.core.testing

import com.teraper.printmaster.core.data.repository.ClientListSettingsRepository
import com.teraper.printmaster.core.model.ClientSort
import com.teraper.printmaster.core.model.PaymentSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeClientListSettingsRepository(initial: ClientSort = ClientSort.NAME) : ClientListSettingsRepository {
    val sort = MutableStateFlow(initial)
    val paymentSort = MutableStateFlow(PaymentSort.DEBT)

    override fun observePaymentSort(): Flow<PaymentSort> = paymentSort

    override fun setPaymentSort(sort: PaymentSort) {
        paymentSort.value = sort
    }

    override fun observeSort(): Flow<ClientSort> = sort

    override fun setSort(sort: ClientSort) {
        this.sort.value = sort
    }
}
