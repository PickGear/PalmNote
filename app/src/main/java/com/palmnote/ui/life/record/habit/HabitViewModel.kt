package com.palmnote.ui.life.record.habit
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.palmnote.R

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.entity.Goal
import com.palmnote.data.db.entity.GoalCheckIn
import com.palmnote.domain.repository.GoalRepository
import com.palmnote.ui.theme.AppIcon

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId


data class HabitUiState(
    val habits: List<Goal> = emptyList(),
    val checkInDates: Map<Long, Set<LocalDate>> = emptyMap(),
    val isLoading: Boolean = true,
    val showCreateSheet: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HabitViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val goalRepo: GoalRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HabitUiState())
    val uiState: StateFlow<HabitUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var habitsFlowJob: Job? = null
    private val habitJobs = mutableMapOf<Long, Job>()

    fun load() {
        habitsFlowJob?.let(Job::cancel)
        loadJob?.let(Job::cancel)
        habitJobs.values.forEach { it.cancel() }
        habitJobs.clear()
        loadJob = viewModelScope.launch {
            try {
                habitsFlowJob = goalRepo.getHabitGoals().onEach { habits ->
                    _uiState.update { state -> state.copy(habits = habits, isLoading = false) }
                }.launchIn(viewModelScope)
                goalRepo.getHabitGoals().first().forEach { habit ->
                    habitJobs[habit.id]?.cancel()
                    habitJobs[habit.id] = launch {
                        goalRepo.getCheckInsByGoal(habit.id).collect { checkIns ->
                            val dates = checkIns.mapNotNull { c ->
                                try {
                                    val millis = c.date
                                    if (millis > 1_000_000_000_000L) {
                                        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                                    } else if (millis > 0) {
                                        LocalDate.ofEpochDay(millis)
                                    } else null
                                } catch (_: Exception) { null }
                            }.toSet()
                            _uiState.update { state ->
                                state.copy(checkInDates = state.checkInDates + (habit.id to dates))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: context.getString(R.string.life_error_load_failed), isLoading = false) }
            }
        }
    }

    fun showCreateSheet() { _uiState.update { it.copy(showCreateSheet = true) } }
    fun dismissCreateSheet() { _uiState.update { it.copy(showCreateSheet = false) } }

    fun createHabit(title: String, category: String, frequency: String, targetPerPeriod: Int) {
        viewModelScope.launch {
            try {
                goalRepo.insertGoal(Goal(
                    title = title,
                    category = category,
                    goalType = "HABIT",
                    frequency = frequency,
                    targetPerPeriod = targetPerPeriod,
                    totalCount = 100,
                    unit = context.getString(R.string.life_habit_times),
                    icon = AppIcon.CheckCircle
                ))
                _uiState.update { it.copy(showCreateSheet = false) }
                load()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: context.getString(R.string.life_error_create_failed)) }
            }
        }
    }

    fun checkIn(goalId: Long) {
        viewModelScope.launch {
            try {
                val today = LocalDate.now()
                val todayMillis = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                goalRepo.insertCheckIn(GoalCheckIn(goalId = goalId, date = todayMillis))
                goalRepo.incrementGoalProgress(goalId)
                load()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: context.getString(R.string.life_error_checkin_failed)) }
            }
        }
    }

    fun deleteHabit(id: Long) {
        viewModelScope.launch {
            try { goalRepo.softDeleteGoal(id) } catch (_: Exception) {}
            load()
        }
    }
}