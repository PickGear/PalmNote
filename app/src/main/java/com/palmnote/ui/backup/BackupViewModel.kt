package com.palmnote.ui.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.backup.BackupManager
import com.palmnote.data.backup.BackupState
import com.palmnote.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.File

class BackupViewModel(
    private val context: Context,
    private val db: AppDatabase
) : ViewModel() {

    private val backupManager = BackupManager()

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState

    private val _password = MutableStateFlow<String?>(null)
    val password: StateFlow<String?> = _password

    fun setPassword(password: String?) {
        _password.value = password
    }

    /**
     * Create backup and copy to user-chosen SAF folder.
     */
    fun createBackupToFolder(folderUri: Uri) {
        viewModelScope.launch {
            flow {
                emit(BackupState.Progress(0))
                try {
                    // 1. Create backup in app cache
                    val tempFile = backupManager.createBackup(context, _password.value)
                    emit(BackupState.Progress(80))

                    // 2. Copy to user-chosen folder via SAF
                    val fileName = tempFile.name
                    val docDir = DocumentFile.fromTreeUri(context, folderUri)
                    val newFile = docDir?.createFile("application/octet-stream", fileName)
                    if (newFile != null) {
                        context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                            tempFile.inputStream().use { input -> input.copyTo(output) }
                        }
                    }

                    tempFile.delete()
                    emit(BackupState.Progress(100))
                    emit(BackupState.Success(newFile?.uri?.toString() ?: folderUri.toString()))
                } catch (e: Exception) {
                    emit(BackupState.Error(e.message ?: "Backup failed"))
                }
            }.flowOn(Dispatchers.IO).collect { state ->
                _backupState.value = state
            }
        }
    }

    /**
     * Restore backup from a user-chosen SAF file URI.
     */
    fun restoreFromUri(fileUri: Uri, password: String? = null) {
        viewModelScope.launch {
            flow {
                emit(BackupState.Progress(0))
                try {
                    // Copy SAF file to temp file for BackupManager
                    val tempFile = File(context.cacheDir, "restore_temp.palmnote")
                    context.contentResolver.openInputStream(fileUri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    emit(BackupState.Progress(30))

                    // Close database before restore
                    db.close()

                    // Restore
                    backupManager.restoreBackup(context, tempFile, password)
                    tempFile.delete()

                    emit(BackupState.Progress(100))
                    emit(BackupState.Success(""))
                } catch (e: Exception) {
                    emit(BackupState.Error(e.message ?: "Restore failed"))
                }
            }.flowOn(Dispatchers.IO).collect { state ->
                _backupState.value = state
            }
        }
    }

    fun resetState() {
        _backupState.value = BackupState.Idle
    }
}
