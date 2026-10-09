package com.teraper.printmaster.feature.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.CallRecordingsRepository
import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.model.CallFolderStatus
import com.teraper.printmaster.core.model.CallRecording
import com.teraper.printmaster.core.model.ClientSearch
import com.teraper.printmaster.core.model.ClientSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CallsTab { UNMATCHED, ALL }

/** Picking the client a recording belongs to. */
data class AttachPicker(val recording: CallRecording, val query: String = "")

data class CallsUiState(
    val isLoading: Boolean = true,
    val status: CallFolderStatus = CallFolderStatus(folderName = null, canReadContacts = false),
    val tab: CallsTab = CallsTab.UNMATCHED,
    val recordings: List<CallRecording> = emptyList(),
    val isScanning: Boolean = false,
    /** Set after the user picked a folder that can't be read. */
    val folderError: Boolean = false,
    val playing: CallRecording? = null,
    val picker: AttachPicker? = null,
    val pickerClients: List<ClientSummary> = emptyList(),
)

private data class LocalState(
    val tab: CallsTab = CallsTab.UNMATCHED,
    val isScanning: Boolean = false,
    val folderError: Boolean = false,
    val playing: CallRecording? = null,
    val picker: AttachPicker? = null,
)

@HiltViewModel
class CallsViewModel @Inject constructor(
    private val repository: CallRecordingsRepository,
    clientsRepository: ClientsRepository,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<CallsUiState> = combine(
        local,
        repository.observeStatus(),
        repository.observeRecordings(),
        clientsRepository.observeClientSummaries(),
    ) { local, status, recordings, clients ->
        CallsUiState(
            isLoading = false,
            status = status,
            tab = local.tab,
            recordings = when (local.tab) {
                CallsTab.UNMATCHED -> recordings.filter { !it.isMatched && !it.ignored }
                CallsTab.ALL -> recordings
            },
            isScanning = local.isScanning,
            folderError = local.folderError,
            playing = local.playing,
            picker = local.picker,
            pickerClients = local.picker?.let { picker ->
                clients.filter { ClientSearch.matches(it.client, picker.query) }
            }.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CallsUiState())

    init {
        scan()
    }

    fun onFolderPicked(uri: String?) {
        if (uri == null) return
        viewModelScope.launch {
            val ok = repository.setFolder(uri)
            local.update { it.copy(folderError = !ok) }
            if (ok) scan()
        }
    }

    /** Matching by contact name needs the phone book; scan again once it's allowed. */
    fun onContactsPermissionResult() {
        repository.onContactsPermissionChanged()
        scan()
    }

    fun scan() {
        if (local.value.isScanning) return
        viewModelScope.launch {
            local.update { it.copy(isScanning = true) }
            val result = repository.scan()
            local.update { it.copy(isScanning = false, folderError = result.folderMissing) }
        }
    }

    fun onTabChange(tab: CallsTab) = local.update { it.copy(tab = tab) }

    fun onPlay(recording: CallRecording) = local.update { it.copy(playing = recording) }

    fun onStopPlaying() = local.update { it.copy(playing = null) }

    fun onAttachClick(recording: CallRecording) = local.update { it.copy(picker = AttachPicker(recording)) }

    fun onPickerQueryChange(query: String) = local.update { it.copy(picker = it.picker?.copy(query = query)) }

    fun onDismissPicker() = local.update { it.copy(picker = null) }

    fun onClientPicked(clientId: Long) {
        val recording = local.value.picker?.recording ?: return
        local.update { it.copy(picker = null) }
        viewModelScope.launch { repository.attach(recording.id, clientId) }
    }

    fun onIgnore(recording: CallRecording) {
        viewModelScope.launch { repository.setIgnored(recording.id, !recording.ignored) }
    }
}
