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
import com.palmnote.domain.event.EventBus
import com.palmnote.domain.event.DomainEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.life.plan.SubtaskKind
import com.palmnote.ui.life.plan.checkinDaysOf
import com.palmnote.ui.life.plan.isSubtaskDone
import com.palmnote.ui.life.plan.subtaskKind
import com.palmnote.ui.life.plan.withAllDatesChecked
import com.palmnote.ui.life.plan.withCheckinDay
import java.time.LocalDate
import java.time.ZoneId


data class ItemDetailUiState(
    val item: LifeItem? = null,
    val template: LifeTemplate? = null,
    val isLoading: Boolean = true,
    val links: List<CrossLink> = emptyList(),
    val subtasks: List<LifeItem> = emptyList()
)

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val itemRepo: LifeItemRepository,
    private val templateRepo: LifeTemplateRepository,
    private val crossLinkRepo: CrossLinkRepository,
    private val eventBus: EventBus
) : ViewModel() {
    private val _uiState = MutableStateFlow(ItemDetailUiState())
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()
    private var linksJob: Job? = null
    private var subtasksJob: Job? = null

    fun load(itemId: Long) {
        linksJob?.cancel()
        subtasksJob?.cancel()
        viewModelScope.launch {
            val item = itemRepo.getItemById(itemId)
            val tpl = item?.let { templateRepo.getTemplateById(it.templateId) }
            _uiState.update { it.copy(item = item, template = tpl, isLoading = false) }
        }
        if (itemId > 0) {
            linksJob = crossLinkRepo.getLinksBySource(EntityType.ITEM, itemId).onEach { links ->
                _uiState.update { it.copy(links = links) }
            }.launchIn(viewModelScope)
            subtasksJob = itemRepo.getSubtasks(itemId).onEach { subs ->
                _uiState.update { it.copy(subtasks = subs) }
            }.launchIn(viewModelScope)
        }
    }

    fun toggleSubtask(sub: LifeItem) {
        viewModelScope.launch {
            if (sub.subtaskKind() == SubtaskKind.MILESTONE) {
                val target = if (sub.status == "COMPLETED") "ACTIVE" else "COMPLETED"
                itemRepo.updateStatus(sub.id, target)
            } else {
                val today = LocalDate.now()
                val days = checkinDays(sub)
                itemRepo.updateFieldsData(sub.id, withCheckinDay(sub.fieldsData, today, !days.contains(today)))
            }
            afterSubtasksChanged()
        }
    }

    fun addSubtask(title: String, kind: SubtaskKind, dueDateMillis: Long?) {
        val parent = _uiState.value.item ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val anchorDate = dueDateMillis?.let { DateUtils.millisToLocalDate(it) } ?: LocalDate.now()
            val anchorMillis = anchorDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val dueDate = when (kind) {
                SubtaskKind.MILESTONE -> dueDateMillis
                else -> anchorMillis
            }
            val recurring = when (kind) {
                SubtaskKind.DAILY -> "DAILY"
                SubtaskKind.WEEKLY -> "WEEKLY"
                SubtaskKind.MONTHLY -> "MONTHLY"
                SubtaskKind.MILESTONE -> null
            }
            itemRepo.insertItem(
                LifeItem(
                    templateId = parent.templateId,
                    title = title,
                    status = "ACTIVE",
                    dueDate = dueDate,
                    recurring = recurring,
                    parentId = parent.id,
                    sortOrder = _uiState.value.subtasks.size,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    fun markAllDoneAndArchive() {
        val parent = _uiState.value.item ?: return
        viewModelScope.launch {
            val planStart = DateUtils.millisToLocalDate(parent.createdAt)
            val planEnd = parent.dueDate?.let { DateUtils.millisToLocalDate(it) }
            val today = LocalDate.now()
            _uiState.value.subtasks.forEach { sub ->
                if (sub.subtaskKind() == SubtaskKind.MILESTONE) {
                    if (sub.status != "COMPLETED") itemRepo.updateStatus(sub.id, "COMPLETED")
                } else {
                    itemRepo.updateFieldsData(sub.id, withAllDatesChecked(sub, planStart, planEnd, today))
                }
            }
            afterSubtasksChanged()
        }
    }

    fun archiveActive() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            itemRepo.updateStatus(item.id, "ARCHIVED")
            load(item.id)
        }
    }

    private fun checkinDays(sub: LifeItem): Set<LocalDate> = checkinDaysOf(sub.fieldsData)

    private suspend fun afterSubtasksChanged() {
        val parent = _uiState.value.item ?: return
        val planStart = DateUtils.millisToLocalDate(parent.createdAt)
        val planEnd = parent.dueDate?.let { DateUtils.millisToLocalDate(it) }
        val today = LocalDate.now()
        val subs = itemRepo.getSubtasks(parent.id).first()
        if (subs.isNotEmpty() && subs.all { isSubtaskDone(it, planStart, planEnd, today) }) {
            itemRepo.updateStatus(parent.id, "ARCHIVED")
            itemRepo.getItemById(parent.id)?.let { eventBus.publish(DomainEvent.LifeItemCreated(it.id)) }
        }
        load(parent.id)
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
            itemRepo.getItemById(item.id)?.let { eventBus.publish(DomainEvent.SavingDeposit(it.id, 0, 0)) }
            load(item.id)
        }
    }

    fun updateStatus(status: String) {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            itemRepo.updateStatus(item.id, status)
            itemRepo.getItemById(item.id)?.let { eventBus.publish(DomainEvent.LifeItemCreated(it.id)) }
            load(item.id)
        }
    }

    fun deleteItem() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            try {
                itemRepo.delete(item.id)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
