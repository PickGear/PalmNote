package com.palmnote.ui.life.time.anniversary
import com.palmnote.R

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.domain.repository.LifeTemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnniversaryUiState(val template: LifeTemplate? = null, val items: List<LifeItem> = emptyList(), val isLoading: Boolean = true, val error: String? = null)

@HiltViewModel
class AnniversaryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val itemRepo: LifeItemRepository,
    private val templateRepo: LifeTemplateRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AnniversaryUiState())
    val uiState: StateFlow<AnniversaryUiState> = _uiState.asStateFlow()
    fun load(templateId: Long) {
        viewModelScope.launch {
            try {
                templateRepo.getTemplateByIdFlow(templateId).onEach { tpl -> _uiState.update { state -> state.copy(template = tpl) } }.launchIn(viewModelScope)
                itemRepo.getItemsByTemplate(templateId).onEach { items -> _uiState.update { state -> state.copy(items = items, isLoading = false) } }.launchIn(viewModelScope)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: context.getString(R.string.life_error_load_failed), isLoading = false) }
            }
        }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            try {
                itemRepo.softDelete(id)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: context.getString(R.string.life_error_delete_failed)) }
            }
        }
    }
}
