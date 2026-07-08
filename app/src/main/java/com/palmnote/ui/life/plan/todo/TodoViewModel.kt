package com.palmnote.ui.life.plan.todo
import com.palmnote.R

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.domain.repository.LifeItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

data class TodoUiState(
    val items: List<LifeItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class TodoViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val itemRepo: LifeItemRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(TodoUiState())
    val uiState: StateFlow<TodoUiState> = _uiState.asStateFlow()

    fun load(templateId: Long) {
        viewModelScope.launch {
            try {
                itemRepo.getItemsByTemplate(templateId).onEach { items ->
                    _uiState.update { it.copy(items = items, isLoading = false) }
                }.launchIn(viewModelScope)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: context.getString(R.string.life_error_load_failed), isLoading = false) }
            }
        }
    }

    fun toggleComplete(item: LifeItem) {
        viewModelScope.launch {
            try {
                val newStatus = if (item.status == "COMPLETED") "ACTIVE" else "COMPLETED"
                itemRepo.updateStatus(item.id, newStatus)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: context.getString(R.string.life_error_operation_failed)) }
            }
        }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            try { itemRepo.softDelete(id) } catch (_: Exception) {}
        }
    }
}

fun getTodoPriority(item: LifeItem): String {
    return try {
        val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
        (obj["priority"] as? JsonPrimitive)?.content ?: "NONE"
    } catch (_: Exception) { "NONE" }
}