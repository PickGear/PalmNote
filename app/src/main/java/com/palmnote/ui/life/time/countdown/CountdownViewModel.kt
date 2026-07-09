package com.palmnote.ui.life.time.countdown
import com.palmnote.R

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.palmnote.ui.life.common.BaseLifeViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.domain.repository.LifeTemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CountdownUiState(val template: LifeTemplate? = null, val items: List<LifeItem> = emptyList(), val isLoading: Boolean = true, val error: String? = null)

@HiltViewModel
class CountdownViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    itemRepo: LifeItemRepository,
    templateRepo: LifeTemplateRepository
) : BaseLifeViewModel<CountdownUiState>(itemRepo, templateRepo) {
    private var templateJob: Job? = null
    override fun initialState() = CountdownUiState()
    fun load(templateId: Long) {
        viewModelScope.launch {
            try {
                templateJob?.let(Job::cancel)
                templateJob = templateRepo.getTemplateByIdFlow(templateId).onEach { tpl -> _uiState.update { it.copy(template = tpl) } }.launchIn(viewModelScope)
                loadItems(templateId) { items -> _uiState.update { it.copy(items = items, isLoading = false) } }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: context.getString(R.string.life_error_load_failed), isLoading = false) }
            }
        }
    }
}