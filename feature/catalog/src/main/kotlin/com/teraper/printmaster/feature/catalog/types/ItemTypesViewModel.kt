package com.teraper.printmaster.feature.catalog.types

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.CatalogRepository
import com.teraper.printmaster.core.model.ItemTemplate
import com.teraper.printmaster.core.model.ItemTypes
import com.teraper.printmaster.core.model.TypeInTree
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItemTypesUiState(
    val isLoading: Boolean = true,
    /** Parents first, each followed by the types that go into it. */
    val types: List<TypeInTree> = emptyList(),
    val showTemplates: Boolean = false,
    /** How many types the last template added; shown once. */
    val added: Int? = null,
)

@HiltViewModel
class ItemTypesViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    private val ui = MutableStateFlow(ItemTypesUiState())

    val uiState: StateFlow<ItemTypesUiState> = combine(ui, catalogRepository.observeTypes()) { ui, types ->
        ui.copy(isLoading = false, types = ItemTypes.tree(types))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemTypesUiState())

    fun onShowTemplates() = ui.update { it.copy(showTemplates = true) }

    fun onDismissTemplates() = ui.update { it.copy(showTemplates = false) }

    fun onTemplatePicked(template: ItemTemplate) {
        ui.update { it.copy(showTemplates = false) }
        viewModelScope.launch {
            val added = catalogRepository.applyTemplate(template.types)
            ui.update { it.copy(added = added) }
        }
    }

    fun onAddedShown() = ui.update { it.copy(added = null) }
}
