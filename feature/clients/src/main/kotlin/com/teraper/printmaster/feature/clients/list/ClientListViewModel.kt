package com.teraper.printmaster.feature.clients.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.model.ClientSearch
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.ClientType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class ClientFilter {
    ALL, FIRMS, PRIVATE, IN_DEBT;

    fun accepts(summary: ClientSummary): Boolean = when (this) {
        ALL -> true
        FIRMS -> summary.client.type == ClientType.FIRM
        PRIVATE -> summary.client.type == ClientType.PRIVATE
        IN_DEBT -> summary.balance.isPositive
    }
}

data class ClientListUiState(
    val isLoading: Boolean = true,
    val filter: ClientFilter = ClientFilter.ALL,
    val clients: List<ClientSummary> = emptyList(),
    /** All clients, ignoring search and filter (for "All · 214" and the empty state). */
    val totalCount: Int = 0,
)

@HiltViewModel
class ClientListViewModel @Inject constructor(
    clientsRepository: ClientsRepository,
) : ViewModel() {

    // Kept outside uiState so the search field updates synchronously while typing.
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val filter = MutableStateFlow(ClientFilter.ALL)

    val uiState: StateFlow<ClientListUiState> = combine(
        clientsRepository.observeClientSummaries(),
        _query,
        filter,
    ) { all, query, filter ->
        ClientListUiState(
            isLoading = false,
            filter = filter,
            clients = all.filter { filter.accepts(it) && ClientSearch.matches(it.client, query) },
            totalCount = all.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClientListUiState())

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun onFilterChange(filter: ClientFilter) {
        this.filter.value = filter
    }
}
