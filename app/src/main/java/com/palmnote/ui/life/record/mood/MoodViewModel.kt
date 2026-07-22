package com.palmnote.ui.life.record.mood
import com.palmnote.R

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.dao.LegacyDao
import com.palmnote.data.db.entity.MoodDiary

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


data class MoodUiState(val diaries: List<MoodDiary> = emptyList(), val isLoading: Boolean = true, val showSheet: Boolean = false, val error: String? = null)

class MoodViewModel(
    private val context: Context,
    private val legacyDao: LegacyDao
) : ViewModel() {
    private val _uiState = MutableStateFlow(MoodUiState())
    val uiState: StateFlow<MoodUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init { load() }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                legacyDao.getAllMoodDiaries().onEach { diaries ->
                    _uiState.update { state -> state.copy(diaries = diaries, isLoading = false) }
                }.launchIn(viewModelScope)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: context.getString(R.string.life_error_load_failed), isLoading = false) }
            }
        }
    }

    fun deleteMood(id: Long) {
        viewModelScope.launch {
            try {
                legacyDao.deleteMoodDiary(id)
                load()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: context.getString(R.string.life_error_delete_failed)) }
            }
        }
    }

    fun showSheet() { _uiState.update { it.copy(showSheet = true) } }
    fun dismissSheet() { _uiState.update { it.copy(showSheet = false) } }

    fun saveMood(mood: String, content: String, factors: String) {
        viewModelScope.launch {
            try {
                legacyDao.insertMoodDiary(MoodDiary(
                    mood = mood,
                    content = content,
                    tags = "",
                    factors = factors,
                    date = System.currentTimeMillis()
                ))
                _uiState.update { it.copy(showSheet = false) }
                load()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: context.getString(R.string.life_error_save_failed)) }
            }
        }
    }
}