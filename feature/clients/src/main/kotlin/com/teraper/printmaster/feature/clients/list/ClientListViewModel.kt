package com.teraper.printmaster.feature.clients.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.data.repository.ClientItemsRepository
import com.teraper.printmaster.core.data.repository.ClientListSettingsRepository
import com.teraper.printmaster.core.model.ClientSort
import com.teraper.printmaster.core.model.SerialMatch
import com.teraper.printmaster.core.model.SerialNumbers
import com.teraper.printmaster.core.model.ClientSearch
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.ClientType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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
    val sort: ClientSort = ClientSort.NAME,
    val clients: List<ClientSummary> = emptyList(),
    /** All clients, ignoring search and filter (for "All · 214" and the empty state). */
    val totalCount: Int = 0,
    /** Client items whose serial number has what's typed: a serial can be searched like a name. */
    val serials: List<SerialMatch> = emptyList(),
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class ClientListViewModel @Inject constructor(
    clientsRepository: ClientsRepository,
    clientItemsRepository: ClientItemsRepository,
    private val settings: ClientListSettingsRepository,
) : ViewModel() {

    // Kept outside uiState so the search field updates synchronously while typing.
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val filter = MutableStateFlow(ClientFilter.ALL)

    private val serialMatches = _query.debounce(300).map { query ->
        if (SerialNumbers.key(query).length >= MIN_SERIAL) clientItemsRepository.findBySerial(query) else emptyList()
    }.onStart { emit(emptyList()) }

    val uiState: StateFlow<ClientListUiState> = combine(
        clientsRepository.observeClientSummaries(),
        _query,
        filter,
        serialMatches,
        settings.observeSort(),
    ) { all, query, filter, serials, sort ->
        ClientListUiState(
            isLoading = false,
            filter = filter,
            sort = sort,
            clients = sort.sort(all.filter { filter.accepts(it) && ClientSearch.matches(it.client, query) }),
            totalCount = all.size,
            serials = if (query.isBlank()) emptyList() else serials,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClientListUiState())

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun onFilterChange(filter: ClientFilter) {
        this.filter.value = filter
    }

    /** Remembered for next time. */
    fun onSortChange(sort: ClientSort) = settings.setSort(sort)

    private companion object {
        /** Fewer letters would find too many serials. */
        const val MIN_SERIAL = 4
    }
}
