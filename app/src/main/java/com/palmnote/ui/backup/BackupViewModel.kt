package com.palmnote.ui.backup
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.backup.BackupInfo
import com.palmnote.data.backup.BackupManager
import com.palmnote.data.backup.BackupPasswordStore
import com.palmnote.data.backup.BackupState
import com.palmnote.app.R
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.DbKeyStore
import com.palmnote.feature.vault.VaultDatabase
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
    dbKeyStore: DbKeyStore,
    private val vaultDb: VaultDatabase,
    private val passwordStore: BackupPasswordStore
) : ViewModel() {

    private val backupManager = BackupManager(dbKeyStore)

    companion object {
        /** 导出备份的密码最小长度：导出文件会离开应用沙箱，过短密码等于没有保护。 */
        const val MIN_PASSWORD_LENGTH = 6

        private const val BACKUP_SUFFIX = ".palmnote"
    }

    /** 用户所选备份文件夹内的一条备份（SAF 来源）。 */
    data class FolderBackup(val name: String, val uri: Uri, val date: Long, val size: Long)

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState

    private val _password = MutableStateFlow<String?>(null)
    val password: StateFlow<String?> = _password

    /** 「记住密码」：开启后导出用的密码经本机 Keystore 包裹保存，下次进入自动预填。 */
    private val _rememberPassword = MutableStateFlow(false)
    val rememberPassword: StateFlow<Boolean> = _rememberPassword

    init {
        // 仅当用户此前主动开启过才预填；换机/卸载重装后本机解不开 → 自动回到空白
        if (passwordStore.hasRemembered()) {
            passwordStore.load()?.let { remembered ->
                _password.value = remembered
                _rememberPassword.value = true
            }
        }
    }

    fun setPassword(password: String?) {
        _password.value = password
    }

    fun setRememberPassword(enabled: Boolean) {
        _rememberPassword.value = enabled
        if (enabled) {
            _password.value?.let { passwordStore.save(it) }
        } else {
            passwordStore.clear()
        }
    }

    /**
     * Create backup and copy to user-chosen SAF folder.
     */
    fun createBackupToFolder(folderUri: Uri) {
        // 导出的备份会离开应用沙箱（网盘/微信/U 盘），必须加密：无密码时包内照片、设置均为明文
        val password = _password.value
        if (password.isNullOrBlank() || password.length < MIN_PASSWORD_LENGTH) {
            _backupState.value = BackupState.Error(context.getString(R.string.backup_error_export_needs_password))
            return
        }
        viewModelScope.launch {
            flow {
                emit(BackupState.Progress(0))
                try {
                    // 打包前先对密码本库做 WAL checkpoint，保证快照一致
                    checkpointVaultWal()
                    // 1. Create backup in app cache
                    val tempFile = backupManager.createBackup(context, db, password)
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
                    // 导出成功后再记住，避免密码错/导出失败也留下记忆
                    if (_rememberPassword.value) passwordStore.save(password)
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

                    try {
                        performRestore(tempFile, password)
                    } finally {
                        tempFile.delete()
                    }

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

    /**
     * 从本机内部存储的备份恢复（自动备份 / 恢复前快照都落在这里）。
     * 这些文件不经过 SAF，可直接就地读取，无需先拷贝到缓存。
     */
    fun restoreFromLocalFile(filePath: String, password: String? = null) {
        viewModelScope.launch {
            flow {
                emit(BackupState.Progress(0))
                try {
                    val file = File(filePath)
                    if (!file.exists()) {
                        throw IllegalArgumentException(context.getString(R.string.backup_error_corrupted))
                    }
                    emit(BackupState.Progress(30))
                    performRestore(file, password)
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

    /** 恢复公共流程：恢复前快照 → 关库 → 覆盖恢复 → 重开库（失败也重开，避免后续操作崩溃）。 */
    private suspend fun performRestore(sourceFile: File, password: String?) {
        backupManager.createPreRestoreBackup(context, db)
        closeDatabases()
        try {
            backupManager.restoreBackup(context, sourceFile, password)
        } finally {
            reopenDatabases()
        }
    }

    fun resetState() {
        _backupState.value = BackupState.Idle
    }

    private fun checkpointVaultWal() {
        try {
            vaultDb.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .use { }
        } catch (_: Exception) {}
    }

    private fun closeDatabases() {
        try { db.close() } catch (_: Exception) {}
        try { vaultDb.close() } catch (_: Exception) {}
    }

    private fun reopenDatabases() {
        try { db.openHelper.writableDatabase } catch (_: Exception) {}
        try { vaultDb.openHelper.writableDatabase } catch (_: Exception) {}
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

    /**
     * 列出本机内部存储中的备份（自动备份 + 恢复前快照）。
     * 该目录其他应用不可读，也无法经系统文件管理器进入，必须由本界面提供恢复入口。
     */
    suspend fun listLocalBackups(): List<BackupInfo> = backupManager.listBackups(context)

    /** 列出已保存备份目录内的 .palmnote 备份文件（IO：DocumentsProvider 查询） */
    suspend fun listBackupsInDir(): List<FolderBackup> = withContext(Dispatchers.IO) {
        val dirUri = getBackupDir() ?: return@withContext emptyList()
        runCatching {
            val dir = DocumentFile.fromTreeUri(context, dirUri) ?: return@withContext emptyList()
            dir.listFiles()
                .filter { it.isFile && it.name?.endsWith(BACKUP_SUFFIX) == true }
                .map { file ->
                    val name = file.name ?: context.getString(R.string.backup_file)
                    FolderBackup(
                        name = name,
                        uri = file.uri,
                        // 部分 DocumentsProvider 不返回 lastModified，回退到文件名内嵌的时间戳
                        date = file.lastModified().takeIf { it > 0L } ?: timestampFromName(name),
                        size = file.length()
                    )
                }
                .sortedByDescending { it.date }
        }.getOrDefault(emptyList())
    }

    /** 备份文件名形如 `palmnote_backup_<epochMillis>.palmnote`，从中取回创建时间。 */
    private fun timestampFromName(name: String): Long =
        Regex("""palmnote_backup_(\d+)""").find(name)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
}
