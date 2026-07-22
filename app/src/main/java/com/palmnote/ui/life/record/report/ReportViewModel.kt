package com.palmnote.ui.life.record.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.entity.LifeReport
import com.palmnote.domain.repository.LifeReportRepository
import kotlinx.coroutines.flow.*


data class ReportUiState(val reports: List<LifeReport> = emptyList(), val isLoading: Boolean = true)

class ReportViewModel(
    private val repo: LifeReportRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()
    fun load() {
        combine(repo.getReportsByType("WEEKLY"), repo.getReportsByType("MONTHLY")) { w, m ->
            w + m
        }.onEach { reports -> _uiState.update { it.copy(reports = reports, isLoading = false) } }.launchIn(viewModelScope)
    }
}