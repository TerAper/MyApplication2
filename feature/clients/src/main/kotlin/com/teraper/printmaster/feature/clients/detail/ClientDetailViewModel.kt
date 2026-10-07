package com.teraper.printmaster.feature.clients.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.data.repository.DeleteClientResult
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.feature.clients.navigation.CLIENT_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ClientDetailUiState {
    data object Loading : ClientDetailUiState
    data object NotFound : ClientDetailUiState
    data class Loaded(
        val summary: ClientSummary,
        val dialog: ClientDetailDialog? = null,
    ) : ClientDetailUiState
}

enum class ClientDetailDialog { CONFIRM_DELETE, DELETE_BLOCKED }

sealed interface ClientDetailEvent {
    data object Deleted : ClientDetailEvent
}

@HiltViewModel
class ClientDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val clientsRepository: ClientsRepository,
) : ViewModel() {

    private val clientId: Long = checkNotNull(savedStateHandle[CLIENT_ID_ARG])
    private val dialog = MutableStateFlow<ClientDetailDialog?>(null)
    private val deleting = MutableStateFlow(false)

    private val _events = Channel<ClientDetailEvent>(Channel.BUFFERED)
    val events: Flow<ClientDetailEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<ClientDetailUiState> = combine(
        clientsRepository.observeClientSummary(clientId),
        dialog,
        deleting,
    ) { summary, dialog, deleting ->
        when {
            summary != null -> ClientDetailUiState.Loaded(summary, dialog)
            // Just deleted: keep showing Loading for the moment before the screen closes.
            deleting -> ClientDetailUiState.Loading
            else -> ClientDetailUiState.NotFound
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClientDetailUiState.Loading)

    fun onDeleteClick() = dialog.update { ClientDetailDialog.CONFIRM_DELETE }

    fun onDismissDialog() = dialog.update { null }

    fun onConfirmDelete() {
        dialog.value = null
        viewModelScope.launch {
            deleting.value = true
            when (clientsRepository.deleteClient(clientId)) {
                DeleteClientResult.DELETED, DeleteClientResult.NOT_FOUND -> _events.send(ClientDetailEvent.Deleted)
                DeleteClientResult.HAS_RECORDS -> {
                    deleting.value = false
                    dialog.value = ClientDetailDialog.DELETE_BLOCKED
                }
            }
        }
    }
}
