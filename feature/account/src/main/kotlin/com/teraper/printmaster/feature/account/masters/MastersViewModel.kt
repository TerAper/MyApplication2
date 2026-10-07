package com.teraper.printmaster.feature.account.masters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.CompaniesRepository
import com.teraper.printmaster.core.model.Master
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The master being added ([id] 0) or edited in the dialog. */
data class MasterForm(val id: Long = 0, val name: String = "", val phone: String = "", val nameMissing: Boolean = false)

data class MastersUiState(
    val isLoading: Boolean = true,
    val masters: List<Master> = emptyList(),
    val form: MasterForm? = null,
)

@HiltViewModel
class MastersViewModel @Inject constructor(
    private val companiesRepository: CompaniesRepository,
) : ViewModel() {

    private val form = MutableStateFlow<MasterForm?>(null)

    val uiState: StateFlow<MastersUiState> = combine(companiesRepository.observeMasters(), form) { masters, form ->
        MastersUiState(isLoading = false, masters = masters, form = form)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MastersUiState())

    fun onAdd() = form.update { MasterForm() }

    fun onEdit(master: Master) = form.update { MasterForm(master.id, master.name, master.phone) }

    fun onFormChange(changed: MasterForm) = form.update { changed.copy(nameMissing = it?.nameMissing == true && changed.name.isBlank()) }

    fun onDismiss() = form.update { null }

    fun onSave() {
        val current = form.value ?: return
        if (current.name.isBlank()) {
            form.update { current.copy(nameMissing = true) }
            return
        }
        viewModelScope.launch {
            companiesRepository.saveMaster(current.id, current.name, current.phone)
            form.update { null }
        }
    }

    fun onDelete() {
        val current = form.value ?: return
        viewModelScope.launch {
            companiesRepository.deleteMaster(current.id)
            form.update { null }
        }
    }
}
