package com.palmnote.ui.backup
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.backup.BackupManager
import com.palmnote.data.backup.BackupState
import com.palmnote.R
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.DbKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    dbKeyStore: DbKeyStore
) : ViewModel() {

    private val backupManager = BackupManager(dbKeyStore)

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
                    val tempFile = backupManager.createBackup(context, db, _password.value)
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
                } finally {
                    // 无论恢复成功还是失败，确保数据库可重新打开，避免后续操作崩溃
                    try { db.openHelper.writableDatabase } catch (_: Exception) {}
                }
            }.flowOn(Dispatchers.IO).collect { state ->
                _backupState.value = state
            }
        }
    }

    fun resetState() {
        _backupState.value = BackupState.Idle
    }

    // ========== 备份目录持久化（SAF tree URI） ==========

    private val backupPrefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)

    /** 创建备份成功后记录用户选择的目录，恢复时直接列出该目录内备份 */
    fun saveBackupDir(uri: Uri) {
        backupPrefs.edit().putString("backup_dir_uri", uri.toString()).apply()
    }

    fun getBackupDir(): Uri? {
        val s = backupPrefs.getString("backup_dir_uri", null) ?: return null
        return runCatching { Uri.parse(s) }.getOrNull()
    }

    /** 列出已保存备份目录内的 .palmnote 备份文件（IO：DocumentsProvider 查询） */
    suspend fun listBackupsInDir(): List<Pair<String, Uri>> = withContext(Dispatchers.IO) {
        val dirUri = getBackupDir() ?: return@withContext emptyList()
        runCatching {
            val dir = DocumentFile.fromTreeUri(context, dirUri) ?: return@withContext emptyList()
            dir.listFiles()
                .filter { it.isFile && it.name?.endsWith(".palmnote") == true }
                .map { (it.name ?: context.getString(R.string.backup_file)) to it.uri }
                .sortedByDescending { it.first }
        }.getOrDefault(emptyList())
    }
}
