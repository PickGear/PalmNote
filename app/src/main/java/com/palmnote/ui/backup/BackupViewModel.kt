package com.palmnote.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.backup.BackupInfo
import com.palmnote.data.backup.BackupRepository
import com.palmnote.data.backup.BackupState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState

    private val _backups = MutableStateFlow<List<BackupInfo>>(emptyList())
    val backups: StateFlow<List<BackupInfo>> = _backups

    init {
        loadBackups()
    }

    private fun loadBackups() {
        _backups.value = backupRepository.listBackups()
    }

    fun createBackup() {
        viewModelScope.launch {
            backupRepository.createBackup().collect { state ->
                _backupState.value = state
            }
            if (_backupState.value is BackupState.Success) {
                loadBackups()
            }
        }
    }

    fun restoreBackup(file: File) {
        viewModelScope.launch {
            backupRepository.restoreBackup(file).collect { state ->
                _backupState.value = state
            }
        }
    }

    fun deleteBackup(file: File) {
        backupRepository.deleteBackup(file)
        loadBackups()
    }

    fun resetState() {
        _backupState.value = BackupState.Idle
    }
}
