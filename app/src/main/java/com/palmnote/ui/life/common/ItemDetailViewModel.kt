package com.palmnote.ui.life.common
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.entity.CrossLink
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.model.Money
import com.palmnote.domain.model.toYuanString
import com.palmnote.domain.model.EntityType
import com.palmnote.domain.repository.CrossLinkRepository
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.domain.repository.LifeTemplateRepository
import com.palmnote.domain.service.TriggerEventBus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive


data class ItemDetailUiState(
    val item: LifeItem? = null,
    val template: LifeTemplate? = null,
    val isLoading: Boolean = true,
    val links: List<CrossLink> = emptyList()
)

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val itemRepo: LifeItemRepository,
    private val templateRepo: LifeTemplateRepository,
    private val crossLinkRepo: CrossLinkRepository,
    private val eventBus: TriggerEventBus
) : ViewModel() {
    private val _uiState = MutableStateFlow(ItemDetailUiState())
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

    fun load(itemId: Long) {
        viewModelScope.launch {
            val item = itemRepo.getItemById(itemId)
            val tpl = item?.let { templateRepo.getTemplateById(it.templateId) }
            _uiState.update { it.copy(item = item, template = tpl, isLoading = false) }
        }
        if (itemId > 0) {
            crossLinkRepo.getLinksBySource(EntityType.ITEM, itemId).onEach { links ->
                _uiState.update { it.copy(links = links) }
            }.launchIn(viewModelScope)
        }
    }

    fun archive() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            itemRepo.updateStatus(item.id, "ARCHIVED")
            load(item.id)
        }
    }

    fun depositAmount(amountCents: Long) {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            val fields = try { Json.decodeFromString<JsonObject>(item.fieldsData) } catch (_: Exception) { JsonObject(emptyMap()) }
            val savedKey = if (fields.containsKey("saved_amount")) "saved_amount" else if (fields.containsKey("currentAmount")) "currentAmount" else "saved_amount"
            val current = (fields[savedKey] as? JsonPrimitive)?.content?.let { Money.parse(it)?.cents } ?: 0L
            val newFields = JsonObject(fields + (savedKey to JsonPrimitive((current + amountCents).toYuanString())))
            itemRepo.updateFieldsData(item.id, newFields.toString())
            itemRepo.getItemById(item.id)?.let { eventBus.postDepositMade(it) }
            load(item.id)
        }
    }

    fun updateStatus(status: String) {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            itemRepo.updateStatus(item.id, status)
            itemRepo.getItemById(item.id)?.let { eventBus.postStatusChanged(it) }
            load(item.id)
        }
    }

    fun deleteItem() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            try {
                itemRepo.softDelete(item.id)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
