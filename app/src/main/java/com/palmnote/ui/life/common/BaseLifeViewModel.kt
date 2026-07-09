package com.palmnote.ui.life.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.domain.repository.LifeTemplateRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

abstract class BaseLifeViewModel<T : Any>(
    protected val itemRepo: LifeItemRepository,
    protected val templateRepo: LifeTemplateRepository
) : ViewModel() {
    protected val _uiState = MutableStateFlow<T>(initialState())
    val uiState: StateFlow<T> = _uiState.asStateFlow()
    abstract fun initialState(): T

    private var itemsJob: Job? = null

    protected fun loadItems(templateId: Long, update: (List<LifeItem>) -> Unit) {
        itemsJob?.let(Job::cancel)
        itemsJob = itemRepo.getItemsByTemplate(templateId).onEach { items ->
            update(items)
        }.launchIn(viewModelScope)
    }

    fun deleteItem(id: Long, onUndo: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                itemRepo.softDelete(id)
                onUndo()
            } catch (e: Exception) {
                android.util.Log.w("BaseLifeVM", "deleteItem failed", e)
            }
        }
    }

    fun restoreItem(id: Long) {
        viewModelScope.launch {
            try {
                itemRepo.restore(id)
            } catch (e: Exception) {
                android.util.Log.w("BaseLifeVM", "restoreItem failed", e)
            }
        }
    }

    fun updateStatus(id: Long, status: String) {
        viewModelScope.launch {
            try {
                itemRepo.updateStatus(id, status)
            } catch (e: Exception) {
                android.util.Log.w("BaseLifeVM", "updateStatus failed", e)
            }
        }
    }
}
