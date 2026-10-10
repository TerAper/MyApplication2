package com.teraper.printmaster.feature.team.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.CompaniesRepository
import com.teraper.printmaster.core.data.sync.SyncController
import com.teraper.printmaster.core.data.team.TeamRepository
import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.core.model.CompanyDraft
import com.teraper.printmaster.core.model.JoinResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class JoinError { UNAVAILABLE, SIGN_IN, NAME_REQUIRED, CODE_REQUIRED, WRONG_CODE, FAILED }

data class JoinUiState(
    val available: Boolean = true,
    /** Signed-in Google account; null = sign in first. */
    val email: String? = null,
    val name: String = "",
    val code: String = "",
    val busy: Boolean = false,
    val error: JoinError? = null,
)

private data class Form(val name: String = "", val code: String = "", val busy: Boolean = false, val error: JoinError? = null)

/**
 * A master joining a company: sign in with Google, type the company's code and own name.
 * Registering the phone as JOINED makes the app open the master's screens by itself.
 */
@HiltViewModel
class JoinViewModel @Inject constructor(
    private val team: TeamRepository,
    private val companies: CompaniesRepository,
    private val syncRunner: SyncController,
) : ViewModel() {

    private val form = MutableStateFlow(Form())

    val googleClientId: String? get() = team.googleClientId

    val uiState: StateFlow<JoinUiState> = combine(team.observeState(), form) { team, form ->
        JoinUiState(team.available, team.email, form.name, form.code, form.busy, form.error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JoinUiState())

    fun onSignInResult(idToken: String?) {
        if (idToken == null) {
            form.update { it.copy(error = JoinError.SIGN_IN) }
            return
        }
        viewModelScope.launch {
            form.update { it.copy(busy = true, error = null) }
            val ok = team.signIn(idToken)
            form.update { it.copy(busy = false, error = if (ok) null else JoinError.SIGN_IN) }
        }
    }

    fun onNameChange(name: String) = form.update { it.copy(name = name, error = null) }

    /** Letters and digits only, upper case: the code is read out over the phone. */
    fun onCodeChange(code: String) = form.update { it.copy(code = code.filter { c -> c.isLetterOrDigit() }.uppercase().take(8), error = null) }

    fun onJoin() {
        val state = form.value
        if (state.busy) return
        val error = when {
            state.name.isBlank() -> JoinError.NAME_REQUIRED
            state.code.length < 8 -> JoinError.CODE_REQUIRED
            else -> null
        }
        if (error != null) {
            form.update { it.copy(error = error) }
            return
        }
        viewModelScope.launch {
            form.update { it.copy(busy = true, error = null) }
            when (team.join(state.code, state.name)) {
                JoinResult.JOINED -> {
                    val spaceName = team.observeState().first().space?.name.orEmpty().ifBlank { state.code }
                    companies.register(AccountMode.JOINED, state.name.trim(), CompanyDraft(name = spaceName))
                    syncRunner.syncNow()
                }
                JoinResult.WRONG_CODE -> form.update { it.copy(busy = false, error = JoinError.WRONG_CODE) }
                JoinResult.NOT_SIGNED_IN -> form.update { it.copy(busy = false, error = JoinError.SIGN_IN) }
                JoinResult.FAILED -> form.update { it.copy(busy = false, error = JoinError.FAILED) }
            }
        }
    }
}
