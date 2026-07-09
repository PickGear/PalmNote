package com.palmnote.ui.life.plan.subscription

import androidx.lifecycle.viewModelScope
import com.palmnote.ui.life.common.BaseLifeViewModel
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.domain.repository.LifeTemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SubscriptionUiState(val template: LifeTemplate? = null, val items: List<LifeItem> = emptyList(), val isLoading: Boolean = true)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    itemRepo: LifeItemRepository,
    templateRepo: LifeTemplateRepository
) : BaseLifeViewModel<SubscriptionUiState>(itemRepo, templateRepo) {
    private var templateJob: Job? = null
    override fun initialState() = SubscriptionUiState()
    fun load(templateId: Long) {
        templateJob?.let(Job::cancel)
                templateJob = templateRepo.getTemplateByIdFlow(templateId).onEach { tpl -> _uiState.update { it.copy(template = tpl) } }.launchIn(viewModelScope)
        loadItems(templateId) { items -> _uiState.update { it.copy(items = items, isLoading = false) } }
    }
}