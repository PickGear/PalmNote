package com.palmnote.ui.life
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
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.domain.repository.FocusRecordRepository
import com.palmnote.domain.repository.GoalRepository
import com.palmnote.domain.repository.LifeMomentRepository
import com.palmnote.domain.repository.LifeTemplateRepository
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.domain.repository.MoodDiaryRepository
import com.palmnote.domain.util.BuiltinTemplates
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.LocalDate
import java.time.ZoneId


data class LifeUiState(
    val templates: List<LifeTemplate> = emptyList(),
    val planTemplates: List<LifeTemplate> = emptyList(),
    val timeTemplates: List<LifeTemplate> = emptyList(),
    val recordTemplates: List<LifeTemplate> = emptyList(),
    val templatePreviewItems: Map<Long, List<LifeItem>> = emptyMap(),
    val scheduledItems: List<LifeItem> = emptyList(),
    val boardItems: List<LifeItem> = emptyList(),
    val markedDates: Set<LocalDate> = emptySet(),
    val todoItems: List<LifeItem> = emptyList(),
    val cardConfigs: List<LifeHomeCardConfig> = LifeHomeCardConfig.defaults,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class LifeViewModel @Inject constructor(
    @ApplicationContext private val application: Context,
    private val templateRepo: LifeTemplateRepository,
    private val itemRepo: LifeItemRepository,
    private val goalRepo: GoalRepository,
    private val focusRepo: FocusRecordRepository,
    private val momentRepo: LifeMomentRepository,
    private val moodRepo: MoodDiaryRepository,
    private val prefs: PreferencesManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LifeUiState())
    val uiState: StateFlow<LifeUiState> = _uiState.asStateFlow()

    private val _calendarExpanded = MutableStateFlow(false)
    val calendarExpanded: StateFlow<Boolean> = _calendarExpanded.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    init {
        prefs.lifeHomeCardConfigs.onEach { configs ->
            _uiState.update { it.copy(cardConfigs = configs) }
        }.launchIn(viewModelScope)

        prefs.lifeCalendarExpanded.onEach { expanded ->
            _calendarExpanded.value = expanded
        }.launchIn(viewModelScope)

        @OptIn(ExperimentalCoroutinesApi::class)
        prefs.lifeCalendarSelectedDate
            .onEach { epochDay -> _selectedDate.value = LocalDate.ofEpochDay(epochDay) }
            .flatMapLatest { epochDay ->
                val date = LocalDate.ofEpochDay(epochDay)
                val zone = ZoneId.systemDefault()
                val selStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val selEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                itemRepo.getScheduledBetween(selStart, selEnd)
            }
            .onEach { board ->
                _uiState.update { it.copy(boardItems = board) }
            }
            .launchIn(viewModelScope)

        itemRepo.getDistinctDueDatesBetween(0L, Long.MAX_VALUE)
            .onEach { marks ->
                val zone = ZoneId.systemDefault()
                val markDates = marks.map { ts ->
                    java.time.Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
                }.toSet()
                _uiState.update { it.copy(markedDates = markDates) }
            }
            .launchIn(viewModelScope)

        observeTemplates()
    }

    fun retry() {
        _uiState.update { it.copy(error = null) }
        observeTemplates()
    }

    fun setCalendarExpanded(expanded: Boolean) {
        if (_calendarExpanded.value == expanded) return
        _calendarExpanded.value = expanded
        viewModelScope.launch { prefs.setLifeCalendarExpanded(expanded) }
    }

    fun setSelectedDate(date: LocalDate) {
        if (_selectedDate.value == date) return
        _selectedDate.value = date
        viewModelScope.launch { prefs.setLifeCalendarSelectedDate(date.toEpochDay()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeTemplates() {
        templateRepo.getAllVisibleTemplates()
            .flatMapLatest { templates ->
                val planCategory = application.getString(R.string.life_category_plan)
                val timeCategory = application.getString(R.string.life_category_time)
                val recordCategory = application.getString(R.string.life_category_record)
                val plans = templates.filter { it.category == planCategory }
                val times = templates.filter { it.category == timeCategory }
                val records = templates.filter { it.category == recordCategory }
                val todoTemplate = templates.firstOrNull { it.name.contains(BuiltinTemplates.TODO_KEYWORD) }
                val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val todayEnd = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val flows: List<Flow<Pair<Long, List<LifeItem>>>> = templates.map { tpl ->
                    val flow: Flow<Pair<Long, List<LifeItem>>> = when (tpl.icon) {
                        "calendar_month" -> goalRepo.getHabitGoals().map { goals -> tpl.id to if (goals.isEmpty()) emptyList() else listOf(LifeItem(templateId = tpl.id, title = goals.first().title)) }
                        "book" -> momentRepo.getAllMoments().map { moments -> tpl.id to if (moments.isEmpty()) emptyList() else listOf(LifeItem(templateId = tpl.id, title = moments.first().title.take(30))) }
                        "mood" -> moodRepo.getAllMoodDiaries().map { diaries -> tpl.id to if (diaries.isEmpty()) emptyList() else listOf(LifeItem(templateId = tpl.id, title = diaries.first().content.take(20))) }
                        "timer" -> flow {
                            val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            val dayEnd = dayStart + 86400000L
                            emit(focusRepo.getTodayTotalMinutes(dayStart, dayEnd))
                        }.map { mins -> tpl.id to if (mins > 0) listOf(LifeItem(templateId = tpl.id, title = application.applicationContext.getString(R.string.life_focus_preview_today, mins.toInt()))) else emptyList() }
                        else -> itemRepo.getActiveItemsByTemplate(tpl.id, 3).map { items -> tpl.id to items }
                    }
                    flow
                }
                val previewsFlow: Flow<Map<Long, List<LifeItem>>> = if (flows.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(flows) { arrays ->
                        val merged = mutableMapOf<Long, List<LifeItem>>()
                        arrays.forEach { (id, items) -> merged[id] = items }
                        merged
                    }
                }
                val scheduledFlow = itemRepo.getScheduledBetween(todayStart, todayEnd)
                val todoFlow = todoTemplate?.let { itemRepo.getTodoComplement(todayStart, todayEnd, it.id) } ?: flowOf(emptyList())
                combine(previewsFlow, scheduledFlow, todoFlow) { previews, scheduled, todos ->
                    _uiState.update {
                        it.copy(
                            templates = templates,
                            planTemplates = plans,
                            timeTemplates = times,
                            recordTemplates = records,
                            templatePreviewItems = previews,
                            scheduledItems = scheduled,
                            todoItems = todos,
                            isLoading = false
                        )
                    }
                }
            }
            .catch { e -> _uiState.update { it.copy(error = e.message ?: application.getString(R.string.life_data_load_error), isLoading = false) } }
            .launchIn(viewModelScope)
    }

    fun moveCardUp(type: LifeHomeCardType) { reorderCard(type, moveUp = true) }

    fun moveCardDown(type: LifeHomeCardType) { reorderCard(type, moveUp = false) }

    private fun reorderCard(type: LifeHomeCardType, moveUp: Boolean) {
        val current = _uiState.value.cardConfigs
        val idx = current.indexOfFirst { it.type == type }
        val swapWith = if (moveUp) idx - 1 else idx + 1
        if (idx < 0 || swapWith < 0 || swapWith >= current.size) return
        val swapped = current.toMutableList().apply {
            val a = this[idx]
            this[idx] = this[swapWith]
            this[swapWith] = a
        }
        _uiState.update { it.copy(cardConfigs = swapped) }
        viewModelScope.launch { prefs.saveLifeHomeCardConfigs(swapped) }
    }

    fun toggleCardVisible(type: LifeHomeCardType) {
        val current = _uiState.value.cardConfigs.map { if (it.type == type) it.copy(visible = !it.visible) else it }
        _uiState.update { it.copy(cardConfigs = current) }
        viewModelScope.launch { prefs.saveLifeHomeCardConfigs(current) }
    }
}
