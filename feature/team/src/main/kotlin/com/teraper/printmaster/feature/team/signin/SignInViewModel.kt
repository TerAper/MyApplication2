package com.teraper.printmaster.feature.team.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.team.TeamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignInUiState(val signingIn: Boolean = false, val failed: Boolean = false)

/** The first screen: Google sign-in. Once signed in, the app moves on by itself. */
@HiltViewModel
class SignInViewModel @Inject constructor(private val team: TeamRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    val clientId: String? get() = team.googleClientId

    fun onSignInStarted() = _uiState.update { it.copy(signingIn = true, failed = false) }

    /** [idToken] null = the sheet was closed or failed. */
    fun onToken(idToken: String?) {
        if (idToken == null) {
            _uiState.value = SignInUiState(failed = true)
            return
        }
        viewModelScope.launch {
            val ok = team.signIn(idToken)
            _uiState.value = SignInUiState(failed = !ok)
        }
    }
}
