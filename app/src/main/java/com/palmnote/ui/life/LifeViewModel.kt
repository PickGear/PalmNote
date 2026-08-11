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
import com.palmnote.domain.repository.FocusRecordRepository
import com.palmnote.domain.repository.GoalRepository
import com.palmnote.domain.repository.LifeMomentRepository
import com.palmnote.domain.repository.LifeTemplateRepository
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.domain.repository.MoodDiaryRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.ZoneId


data class LifeUiState(
    val templates: List<LifeTemplate> = emptyList(),
    val planTemplates: List<LifeTemplate> = emptyList(),
    val timeTemplates: List<LifeTemplate> = emptyList(),
    val recordTemplates: List<LifeTemplate> = emptyList(),
    val templatePreviewItems: Map<Long, List<LifeItem>> = emptyMap(),
    val todayTodos: Int = 0,
    val todayFocusMinutes: Int = 0,
    val habitCompletionRate: Int = 0,
    val greeting: String = "",
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(LifeUiState())
    val uiState: StateFlow<LifeUiState> = _uiState.asStateFlow()

    init {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greet = when (hour) { in 0..4 -> application.getString(R.string.greeting_night); in 5..8 -> application.getString(R.string.greeting_morning); in 9..11 -> application.getString(R.string.greeting_forenoon); in 12..13 -> application.getString(R.string.greeting_noon); in 14..17 -> application.getString(R.string.greeting_afternoon); else -> application.getString(R.string.greeting_evening) }
        _uiState.update { it.copy(greeting = greet) }

        observeTemplates()
        loadTodayData()

        goalRepo.getHabitGoals().onEach { habits ->
            if (habits.isEmpty()) { _uiState.update { it.copy(habitCompletionRate = 0) }; return@onEach }
            val completed = habits.count { it.isCompleted }
            _uiState.update { it.copy(habitCompletionRate = (completed * 100 / habits.size)) }
        }.launchIn(viewModelScope)
    }

    private fun loadTodayData() {
        viewModelScope.launch {
            try {
                val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val todayEnd = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val minutes = focusRepo.getTodayTotalMinutes(todayStart, todayEnd)
                _uiState.update { it.copy(todayFocusMinutes = minutes) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = application.getString(R.string.life_data_temp_unavailable)) }
            }
        }
    }

    private fun countTodayTodos(items: Map<Long, List<LifeItem>>): Int {
        val todayMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEndMillis = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        var count = 0
        items.forEach { (_, itemList) ->
            itemList.forEach { item ->
                try {
                    val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                    val dateStr = (obj["target_date"]?.jsonPrimitive?.content?.toLongOrNull()
                        ?: obj["targetDate"]?.jsonPrimitive?.content?.toLongOrNull()
                        ?: obj["date"]?.jsonPrimitive?.content?.toLongOrNull())
                    if (dateStr != null && dateStr in todayMillis until todayEndMillis) count++
                } catch (_: Exception) {}
            }
        }
        return count
    }

    fun retry() {
        _uiState.update { it.copy(error = null) }
        observeTemplates()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeTemplates() {
        templateRepo.getAllVisibleTemplates()
            .flatMapLatest { templates ->
                val planNames = setOf(application.getString(R.string.life_type_saving), application.getString(R.string.life_type_shopping), application.getString(R.string.life_type_todo), application.getString(R.string.life_type_travel), application.getString(R.string.life_type_reading), application.getString(R.string.life_type_study))
                val timeNames = setOf(application.getString(R.string.life_type_countdown), application.getString(R.string.life_type_countup), application.getString(R.string.life_type_birthday), application.getString(R.string.life_type_anniversary))
                val recordNames = setOf(application.getString(R.string.life_type_habit), application.getString(R.string.life_type_mood), application.getString(R.string.life_type_journal), application.getString(R.string.life_type_focus), application.getString(R.string.life_type_subscription), application.getString(R.string.life_type_report), application.getString(R.string.life_report_tab_weekly), application.getString(R.string.life_type_subscription))
                val plans = templates.filter { it.category == "\u8BA1\u5212" || it.category == "PLAN" || it.name in planNames }
                val times = templates.filter { it.category == "\u65F6\u95F4" || it.category == "TIME" || it.name in timeNames }
                val records = templates.filter { it.category == "\u8BB0\u5F55" || it.category == "RECORD" || it.name in recordNames }
                val flows: List<Flow<Pair<Long, List<LifeItem>>>> = templates.map { tpl ->
                    val flow: Flow<Pair<Long, List<LifeItem>>> = when (tpl.icon) {
                        "calendar_month" -> goalRepo.getHabitGoals().map { goals -> tpl.id to if (goals.isEmpty()) emptyList() else listOf(LifeItem(templateId = tpl.id, title = goals.first().title)) }
                        "book" -> momentRepo.getAllMoments().map { moments -> tpl.id to if (moments.isEmpty()) emptyList() else listOf(LifeItem(templateId = tpl.id, title = moments.first().title.take(30))) }
                        "mood" -> moodRepo.getAllMoodDiaries().map { diaries -> tpl.id to if (diaries.isEmpty()) emptyList() else listOf(LifeItem(templateId = tpl.id, title = diaries.first().content.take(20))) }
                        "timer" -> flow {
                            val todayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                            val todayEnd = todayStart + 86400000L
                            emit(focusRepo.getTodayTotalMinutes(todayStart, todayEnd))
                        }.map { mins -> tpl.id to if (mins > 0) listOf(LifeItem(templateId = tpl.id, title = application.applicationContext.getString(R.string.life_focus_preview_today, mins.toInt()))) else emptyList() }
                        "BarChart", "assessment" -> itemRepo.getActiveItemsByTemplate(tpl.id, 3).map { items -> tpl.id to items }
                        else -> itemRepo.getActiveItemsByTemplate(tpl.id, 3).map { items -> tpl.id to items }
                    }
                    flow
                }
                if (flows.isEmpty()) {
                    _uiState.update { it.copy(templates = templates, planTemplates = plans, timeTemplates = times, recordTemplates = records, templatePreviewItems = emptyMap(), isLoading = false) }
                        flowOf(emptyMap<Long, List<LifeItem>>())
                } else {
                    combine(flows) { arrays ->
                        val merged = mutableMapOf<Long, List<LifeItem>>()
                        arrays.forEach { (id, items) -> merged[id] = items }
                        val todayCount = countTodayTodos(merged)
                        _uiState.update { it.copy(templates = templates, planTemplates = plans, timeTemplates = times, recordTemplates = records, templatePreviewItems = merged, todayTodos = todayCount, isLoading = false) }
                                merged
                    }
                }
            }
            .catch { e -> _uiState.update { it.copy(error = e.message ?: application.getString(R.string.life_data_load_error), isLoading = false) } }
            .launchIn(viewModelScope)
    }
}
