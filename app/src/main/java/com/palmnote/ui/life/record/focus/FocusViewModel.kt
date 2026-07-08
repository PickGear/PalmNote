package com.palmnote.ui.life.record.focus
import com.palmnote.R

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.entity.FocusRecord
import com.palmnote.domain.repository.FocusRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class FocusUiState(val records: List<FocusRecord> = emptyList(), val todayMinutes: Int = 0, val totalMinutes: Int = 0, val isLoading: Boolean = true, val error: String? = null)

@HiltViewModel
class FocusViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: FocusRecordRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            try {
                repo.getAllRecords().onEach { records ->
                    val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val todayEnd = todayStart + 86400000L
                    val todayMin = repo.getTodayTotalMinutes(todayStart, todayEnd)
                    _uiState.update { it.copy(records = records, todayMinutes = todayMin, isLoading = false) }
                }.launchIn(viewModelScope)
                repo.getTotalMinutes().onEach { total -> _uiState.update { state -> state.copy(totalMinutes = total) } }.launchIn(viewModelScope)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: context.getString(R.string.life_error_load_failed), isLoading = false) }
            }
        }
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
