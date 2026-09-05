package com.palmnote.ui.life.record.focus
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.palmnote.app.R

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.entity.FocusRecord
import com.palmnote.domain.repository.FocusRecordRepository

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId


data class FocusUiState(val todayMinutes: Int = 0, val totalMinutes: Int = 0, val isLoading: Boolean = true, val error: String? = null)

@HiltViewModel
class FocusViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: FocusRecordRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()
    val todayRecords: Flow<List<FocusRecord>> = repo.getTodayRecords(
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

    private var loadJob: Job? = null
    private var totalJob: Job? = null

    fun load() {
        loadJob?.cancel()
        totalJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val todayEnd = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val todayMin = repo.getTodayTotalMinutes(todayStart, todayEnd)
                _uiState.update { it.copy(todayMinutes = todayMin, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: context.getString(R.string.life_error_load_failed), isLoading = false) }
            }
        }
        totalJob = repo.getTotalMinutes()
            .onEach { total -> _uiState.update { it.copy(totalMinutes = total) } }
            .catch { e -> _uiState.update { it.copy(error = e.message ?: context.getString(R.string.life_error_load_failed)) } }
            .launchIn(viewModelScope)
    }

    fun saveRecord(durationMinutes: Int, completed: Boolean, startTime: Long) {
        viewModelScope.launch {
            try {
                val endTime = System.currentTimeMillis()
                repo.insertRecord(FocusRecord(
                    durationMinutes = durationMinutes,
                    targetMinutes = durationMinutes,
                    completed = completed,
                    startTime = startTime,
                    endTime = endTime
                ))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: context.getString(R.string.life_error_save_failed)) }
            }
        }
    }
}
