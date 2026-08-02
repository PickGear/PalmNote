package com.palmnote.ui.life.record.journal
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.palmnote.R

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.entity.LifeMoment
import com.palmnote.domain.repository.LifeMomentRepository

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


data class JournalUiState(val moments: List<LifeMoment> = emptyList(), val isLoading: Boolean = true, val error: String? = null)

@HiltViewModel
class JournalViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lifeMomentRepository: LifeMomentRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(JournalUiState())
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init { load() }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                lifeMomentRepository.getAllMoments().onEach { moments ->
                    _uiState.update { state -> state.copy(moments = moments, isLoading = false) }
                }.launchIn(viewModelScope)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: context.getString(R.string.life_error_load_failed), isLoading = false) }
            }
        }
    }

    fun deleteMoment(id: Long) {
        viewModelScope.launch {
            try {
                lifeMomentRepository.softDeleteMoment(id)
                load()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: context.getString(R.string.life_error_delete_failed)) }
            }
        }
    }

    fun saveMoment(mood: String, content: String, factors: String = "") {
        viewModelScope.launch {
            try {
                lifeMomentRepository.insertMoment(
                    LifeMoment(content = content, mood = mood, tags = factors, date = System.currentTimeMillis())
                )
                load()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: context.getString(R.string.life_error_save_failed)) }
            }
        }
    }
}