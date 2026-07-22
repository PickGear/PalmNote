package com.palmnote.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.backup.BackupInfo
import com.palmnote.data.backup.BackupRepository
import com.palmnote.data.backup.BackupState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class BackupViewModel(
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState

    private val _backups = MutableStateFlow<List<BackupInfo>>(emptyList())
    val backups: StateFlow<List<BackupInfo>> = _backups
    
    private val _password = MutableStateFlow<String?>(null)
    val password: StateFlow<String?> = _password
    
    private val _restorePassword = MutableStateFlow<String?>(null)
    val restorePassword: StateFlow<String?> = _restorePassword

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _backups.value = backupRepository.listBackups()
        }
    }

    private fun loadBackups() {
        viewModelScope.launch(Dispatchers.IO) {
            _backups.value = backupRepository.listBackups()
        }
    }
    
    fun setPassword(password: String?) {
        _password.value = password
    }
    
    fun setRestorePassword(password: String?) {
        _restorePassword.value = password
    }

    fun createBackup() {
        viewModelScope.launch {
            backupRepository.createBackup(_password.value).collect { state ->
                _backupState.value = state
            }
            if (_backupState.value is BackupState.Success) {
                loadBackups()
            }
        }
    }

    fun restoreBackup(file: File) {
        viewModelScope.launch {
            backupRepository.restoreBackup(file, _restorePassword.value).collect { state ->
                _backupState.value = state
            }
        }
    }

    fun deleteBackup(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            backupRepository.deleteBackup(file)
            _backups.value = backupRepository.listBackups()
        }
    }

    fun resetState() {
        _backupState.value = BackupState.Idle
    }
}
