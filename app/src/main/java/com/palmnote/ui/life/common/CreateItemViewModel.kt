package com.palmnote.ui.life.common
import android.content.Context
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.app.R
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.domain.repository.LifeTemplateRepository
import com.palmnote.domain.event.EventBus
import com.palmnote.domain.event.DomainEvent
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


data class CreateItemUiState(
    val template: LifeTemplate? = null,
    val existingItem: LifeItem? = null,
    val isLoading: Boolean = true,
    val savedItemId: Long? = null,
    val itemWarning: String? = null,
    val saveError: String? = null
)

@HiltViewModel
class CreateItemViewModel @Inject constructor(
    @ApplicationContext private val application: Context,
    private val itemRepo: LifeItemRepository,
    private val templateRepo: LifeTemplateRepository,
    private val eventBus: EventBus
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateItemUiState())
    val uiState: StateFlow<CreateItemUiState> = _uiState.asStateFlow()

    fun load(templateId: Long) {
        templateRepo.getTemplateByIdFlow(templateId).onEach { tpl ->
            _uiState.update { it.copy(template = tpl, isLoading = false) }
            tpl?.let { checkItemCount(it.id) }
        }.launchIn(viewModelScope)
    }

    fun loadEdit(itemId: Long) {
        viewModelScope.launch {
            val item = itemRepo.getItemById(itemId)
            if (item != null) {
                val tpl = templateRepo.getTemplateById(item.templateId)
                _uiState.update { it.copy(template = tpl, existingItem = item, isLoading = false) }
                tpl?.let { checkItemCount(it.id) }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun checkItemCount(templateId: Long) {
        try {
            val count = itemRepo.getItemCountByTemplate(templateId).first()
            val msg = when {
                count >= 10000 -> application.getString(R.string.life_item_limit_reached)
                count >= 9500 -> application.getString(R.string.life_item_limit_warning, count)
                count >= 8000 -> application.getString(R.string.life_item_limit_info, count)
                else -> null
            }
            _uiState.update { it.copy(itemWarning = msg) }
        } catch (e: Exception) {
            android.util.Log.w("CreateItemVM", "checkItemCount failed", e)
        }
    }

    fun saveItem(title: String, fieldsData: String) {
        val tpl = _uiState.value.template ?: return
        val existing = _uiState.value.existingItem
        viewModelScope.launch {
            try {
                val id = if (existing != null) {
                    itemRepo.updateItem(existing.copy(title = title, fieldsData = fieldsData))
                    existing.id
                } else {
                    itemRepo.insertItem(LifeItem(templateId = tpl.id, title = title, fieldsData = fieldsData))
                }
                itemRepo.getItemById(id)?.let { item ->
                    eventBus.publish(DomainEvent.LifeItemCreated(item.id))
                }
                _uiState.update { it.copy(savedItemId = id, saveError = null) }
            } catch (e: Exception) {
                android.util.Log.e("CreateItemVM", "saveItem failed", e)
                _uiState.update { it.copy(saveError = application.getString(R.string.life_template_save_error)) }
            }
        }
    }

    fun resetSaved() { _uiState.update { it.copy(savedItemId = null, saveError = null) } }
}
