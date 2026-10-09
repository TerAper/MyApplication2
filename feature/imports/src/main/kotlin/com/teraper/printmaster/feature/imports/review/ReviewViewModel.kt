package com.teraper.printmaster.feature.imports.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.data.repository.ImportRepository
import com.teraper.printmaster.core.model.ClientSearch
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.PendingPayment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Choosing the client of [payment] by hand. */
data class ClientPick(val payment: PendingPayment, val query: String = "")

data class ReviewUiState(
    val isLoading: Boolean = true,
    val payments: List<PendingPayment> = emptyList(),
    val pick: ClientPick? = null,
    val pickClients: List<ClientSummary> = emptyList(),
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repository: ImportRepository,
    clientsRepository: ClientsRepository,
) : ViewModel() {

    private val pick = MutableStateFlow<ClientPick?>(null)

    val uiState: StateFlow<ReviewUiState> = combine(repository.observePending(), clientsRepository.observeClientSummaries(), pick) { payments, clients, pick ->
        ReviewUiState(
            isLoading = false,
            payments = payments,
            pick = pick,
            // The payer's name is a good first search.
            pickClients = pick?.let { p -> clients.filter { ClientSearch.matches(it.client, p.query) } }.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    fun onConfirm(payment: PendingPayment) {
        val clientId = payment.suggestedClientId ?: return
        viewModelScope.launch { repository.assign(payment.id, clientId) }
    }

    fun onChooseClient(payment: PendingPayment) = pick.update { ClientPick(payment) }

    fun onPickQueryChange(query: String) = pick.update { it?.copy(query = query) }

    fun onClientPicked(clientId: Long) {
        val payment = pick.value?.payment ?: return
        pick.value = null
        viewModelScope.launch { repository.assign(payment.id, clientId) }
    }

    fun onDismissPick() = pick.update { null }

    fun onIgnore(payment: PendingPayment) {
        viewModelScope.launch { repository.ignore(payment.id) }
    }
}
