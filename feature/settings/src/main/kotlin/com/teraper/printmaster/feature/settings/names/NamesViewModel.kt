package com.teraper.printmaster.feature.settings.names

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.NamesRepository
import com.teraper.printmaster.core.model.CustomNames
import com.teraper.printmaster.core.model.NamedRole
import com.teraper.printmaster.core.model.RoleName
import com.teraper.printmaster.core.model.TextLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The words being typed, for the language the app is shown in. */
data class NamesUiState(
    val language: TextLanguage? = null,
    val names: Map<NamedRole, RoleName> = emptyMap(),
    val hasChanges: Boolean = false,
)

sealed interface NamesEvent {
    /** Saved: the screens are rebuilt so every text shows the new words. */
    data object Saved : NamesEvent
}

@HiltViewModel
class NamesViewModel @Inject constructor(private val repository: NamesRepository) : ViewModel() {

    private val state = MutableStateFlow(NamesUiState())
    val uiState: StateFlow<NamesUiState> = state.asStateFlow()

    private val _events = Channel<NamesEvent>(Channel.BUFFERED)
    val events: Flow<NamesEvent> = _events.receiveAsFlow()

    /** The screen knows the language its texts are in; read once. */
    fun onLanguage(language: TextLanguage) {
        if (state.value.language == language) return
        state.value = NamesUiState(language, repository.names(language).names)
    }

    fun onOneChange(role: NamedRole, text: String) = edit(role) { it.copy(one = text.take(MAX_LENGTH)) }

    fun onManyChange(role: NamedRole, text: String) = edit(role) { it.copy(many = text.take(MAX_LENGTH)) }

    /** Back to the app's own word. */
    fun onReset(role: NamedRole) = edit(role) { RoleName() }

    fun onSave() {
        val current = state.value
        val language = current.language ?: return
        repository.save(CustomNames(language, current.names))
        state.update { it.copy(hasChanges = false) }
        viewModelScope.launch { _events.send(NamesEvent.Saved) }
    }

    private fun edit(role: NamedRole, change: (RoleName) -> RoleName) = state.update {
        it.copy(names = it.names + (role to change(it.names[role] ?: RoleName())), hasChanges = true)
    }

    private companion object {
        const val MAX_LENGTH = 30
    }
}
