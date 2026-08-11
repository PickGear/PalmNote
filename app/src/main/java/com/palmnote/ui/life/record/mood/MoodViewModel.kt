package com.palmnote.ui.life.record.mood
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.palmnote.app.R

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.entity.MoodDiary
import com.palmnote.domain.repository.MoodDiaryRepository

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


data class MoodUiState(val diaries: List<MoodDiary> = emptyList(), val isLoading: Boolean = true, val showSheet: Boolean = false, val error: String? = null)

@HiltViewModel
class MoodViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moodDiaryRepository: MoodDiaryRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MoodUiState())
    val uiState: StateFlow<MoodUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init { load() }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                moodDiaryRepository.getAllMoodDiaries().onEach { diaries ->
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
                moodDiaryRepository.deleteMoodDiary(id)
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
                moodDiaryRepository.insertMoodDiary(MoodDiary(
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