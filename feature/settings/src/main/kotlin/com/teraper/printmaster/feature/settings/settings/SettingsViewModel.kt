package com.teraper.printmaster.feature.settings.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.LanguageRepository
import com.teraper.printmaster.core.model.AppLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(val language: AppLanguage? = null)

sealed interface SettingsEvent {
    /** Android 12 only: the screen must be rebuilt to show the new language. */
    data object Recreate : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(private val languageRepository: LanguageRepository) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = languageRepository.observeLanguage()
        .map { SettingsUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    fun onLanguageClick(language: AppLanguage) {
        if (language == uiState.value.language) return
        languageRepository.setLanguage(language)
        if (languageRepository.needsRecreate) viewModelScope.launch { _events.send(SettingsEvent.Recreate) }
    }
}
