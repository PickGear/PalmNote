package com.palmnote.ui.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.palmnote.app.R
import com.palmnote.data.backup.AutoBackupScheduler
import com.palmnote.data.backup.BackupInfo
import com.palmnote.data.backup.BackupKind
import com.palmnote.data.backup.BackupManager
import com.palmnote.data.backup.BackupPasswordStore
import com.palmnote.data.backup.BackupState
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.DbKeyStore
import com.palmnote.PalmNoteApp
import com.palmnote.data.worker.AutoBackupWorker
import com.palmnote.data.worker.LifeDailyCheckWorker
import com.palmnote.feature.vault.VaultDatabase
import com.palmnote.ui.widget.WidgetUpdateHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    dbKeyStore: DbKeyStore,
    private val vaultDb: VaultDatabase,
    private val passwordStore: BackupPasswordStore,
    private val preferencesManager: PreferencesManager,
    private val autoBackupScheduler: AutoBackupScheduler
) : ViewModel() {

    private val backupManager = BackupManager(dbKeyStore)

    companion object {
        /** 恢复成功标记：供重启后的 Application 补发 Widget 刷新（恢复期间广播被抑制） */
        private const val RESTORE_FLAGS_PREFS = "restore_flags"
        private const val KEY_JUST_RESTORED = "just_restored"

        /** 导出备份的密码最小长度：导出文件会离开应用沙箱，过短密码等于没有保护。 */
        const val MIN_PASSWORD_LENGTH = 6

        private const val BACKUP_SUFFIX = ".palmnote"

        /** SAF 恢复时先落到缓存的临时文件名；每次恢复前都清掉上一次的残留。 */
        private const val RESTORE_TEMP_NAME = "restore_temp.palmnote"
    }

    /** 用户所选备份文件夹内的一条备份（SAF 来源）。 */
    data class FolderBackup(
        val name: String,
        val uri: Uri,
        val date: Long,
        val size: Long,
        /** 由文件名解析的身份；用于在列表里标出「可换机 / 旧版」。 */
        val kind: BackupKind = BackupKind.LEGACY
    )

    /**
     * 自动备份的可配置项 + 最近一次失败信息。
     *
     * 把"失败"作为一等公民收进设置模型：系统级 Auto Backup 已关闭（`allowBackup="false"`），
     * 自动备份是用户唯一的数据退路，静默失败等于没有退路。
     */
    data class AutoBackupSettings(
        // 与 DataStore 默认值一致：自动备份默认关闭，避免首帧闪现"已开启"
        val enabled: Boolean = false,
        val intervalDays: Int = PreferencesManager.DEFAULT_AUTO_BACKUP_INTERVAL_DAYS,
        val keepCount: Int = PreferencesManager.DEFAULT_AUTO_BACKUP_KEEP_COUNT,
        val lastErrorAt: Long = 0L,
        val lastErrorMsg: String = ""
    )

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState

    /**
     * 备份密码是否已设置。
     *
     * 密码是**全局唯一**的备份密码：手动备份、自动备份、「备份到文件夹」共用同一份，
     * 设了就全部加密（PNB2 + 便携密钥，可换机恢复），没设就全部明文（PNB3，仅本机可恢复）。
     * 密码经 [BackupPasswordStore] 由 Keystore 包裹落盘，因此自动备份也能在无人输入时加密。
     */
    private val _backupPasswordSet = MutableStateFlow(passwordStore.load() != null)
    val backupPasswordSet: StateFlow<Boolean> = _backupPasswordSet

    private val autoBackupConfig: Flow<AutoBackupSettings> = combine(
        preferencesManager.autoBackupEnabled,
        preferencesManager.autoBackupIntervalDays,
        preferencesManager.autoBackupKeepCount
    ) { enabled, intervalDays, keepCount ->
        AutoBackupSettings(
            enabled = enabled,
            intervalDays = intervalDays,
            keepCount = keepCount
        )
    }

    val autoBackupSettings: StateFlow<AutoBackupSettings> = combine(
        autoBackupConfig,
        preferencesManager.autoBackupLastErrorAt,
        preferencesManager.autoBackupLastErrorMsg
    ) { settings, lastErrorAt, lastErrorMsg ->
        settings.copy(lastErrorAt = lastErrorAt, lastErrorMsg = lastErrorMsg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutoBackupSettings())

    /** 设置或清除备份密码；空/空白 = 清除（之后所有备份都是明文）。 */
    fun setBackupPassword(password: String?) {
        passwordStore.save(password?.takeIf { it.isNotBlank() })
        _backupPasswordSet.value = passwordStore.load() != null
    }

    /** 对话框预填用：读当前已保存的备份密码（Keystore 解不开时为 null）。 */
    fun loadBackupPassword(): String? = passwordStore.load()

    // ========== 自动备份设置 ==========
    // 每项改完立刻重建任务：偏好写进 DataStore 后若不 sync，用户要等到下次冷启动才生效，
    // 而"我明明关了它却还在备份"是最容易被察觉、也最伤信任的一类不一致。

    fun setAutoBackupEnabled(enabled: Boolean) = updateAutoBackupSettings {
        preferencesManager.setAutoBackupEnabled(enabled)
    }

    fun setAutoBackupIntervalDays(days: Int) = updateAutoBackupSettings {
        preferencesManager.setAutoBackupIntervalDays(days)
    }

    fun setAutoBackupKeepCount(count: Int) = updateAutoBackupSettings {
        preferencesManager.setAutoBackupKeepCount(count)
    }

    /** 用户看过失败原因后确认，清掉告警，避免旧错误长期挂在健康度卡片上。 */
    fun dismissAutoBackupError() {
        viewModelScope.launch { preferencesManager.clearAutoBackupError() }
    }

    private fun updateAutoBackupSettings(update: suspend () -> Unit) {
        viewModelScope.launch {
            update()
            autoBackupScheduler.sync()
        }
    }

    /**
     * Create backup and copy to user-chosen SAF folder.
     *
     * 密码与「立即备份」/自动备份共用同一份备份密码设置：设了就加密（含便携密钥，
     * 新手机可恢复），没设就明文（仅本机可恢复）。不再强制密码——加不加密由用户在
     * 「备份密码」一行里统一决定。
     */
    fun createBackupToFolder(folderUri: Uri, kind: BackupKind = BackupKind.PORTABLE) {
        val password = passwordStore.load()
        viewModelScope.launch {
            flow {
                emit(BackupState.Progress(0))
                var tempFile: File? = null
                try {
                    // 打包前先对密码本库做 WAL checkpoint，保证快照一致
                    checkpointVaultWal()
                    // 1. Create backup in app cache
                    val created = backupManager.createBackup(context, db, password, kind)
                    tempFile = created
                    emit(BackupState.Progress(80))

                    // 2. Copy to user-chosen folder via SAF
                    val targetUri = writeBackupToFolder(folderUri, created)
                    emit(BackupState.Progress(100))
                    emit(BackupState.Success(targetUri.toString()))
                } catch (e: Exception) {
                    emit(BackupState.Error(e.message ?: context.getString(R.string.backup_error_export_failed)))
                } finally {
                    tempFile?.delete()
                }
            }.flowOn(Dispatchers.IO).collect { state ->
                _backupState.value = state
            }
        }
    }

    /**
     * 把缓存中的备份写入用户所选文件夹，返回落盘后的 SAF URI。
     *
     * 目录不可用、建文件失败、输出流打不开、写入 0 字节都必须抛错：静默返回成功会让用户
     * 以为备份已经躺在文件夹里，等真正需要它时才发现是空文件或根本没有文件。
     */
    private fun writeBackupToFolder(folderUri: Uri, source: File): Uri {
        val docDir = DocumentFile.fromTreeUri(context, folderUri) ?: exportFailed()
        val newFile = docDir.createFile("application/octet-stream", source.name) ?: exportFailed()
        val written = try {
            context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: exportFailed()
        } catch (e: Exception) {
            // 半途失败会在目标文件夹留下不完整的包，删掉避免下次被当成有效备份选中
            runCatching { newFile.delete() }
            throw e
        }
        if (written <= 0L) {
            runCatching { newFile.delete() }
            exportFailed()
        }
        return newFile.uri
    }

    /** 导出失败的统一出口；返回 [Nothing]，可直接用在 `?:` 右侧或作为分支的最后一句话。 */
    private fun exportFailed(): Nothing =
        throw IOException(context.getString(R.string.backup_error_export_failed))

    /**
     * 手动创建一份**本机**备份。
     *
     * 与自动备份、「备份到文件夹」共用同一份备份密码设置：设了密码则加密，
     * 没设则明文。本机备份不离开应用沙箱，明文也不外泄。
     */
    fun createLocalBackup() {
        viewModelScope.launch {
            _backupState.value = BackupState.Progress(0)
            try {
                val password = passwordStore.load()
                val file = withContext(Dispatchers.IO) {
                    checkpointVaultWal()
                    backupManager.createBackup(context, db, password, BackupKind.MANUAL).also {
                        // 手动备份也受保留份数约束，否则它会绕过自动备份的轮转无限堆积
                        backupManager.cleanupOldBackups(
                            context,
                            keep = preferencesManager.autoBackupKeepCount.first()
                        )
                    }
                }
                _backupState.value = BackupState.Success(file.absolutePath)
            } catch (e: Exception) {
                _backupState.value = BackupState.Error(
                    e.message ?: context.getString(R.string.backup_error_create_failed)
                )
            }
        }
    }

    /** 删除本机内部存储中的一份备份；返回是否真的删掉了（删除是破坏性操作，失败要让界面说得出话）。 */
    suspend fun deleteLocalBackup(filePath: String): Boolean = withContext(Dispatchers.IO) {
        backupManager.deleteBackup(File(filePath))
    }

    /** 删除备份文件夹（SAF）里的一份备份；返回是否删除成功。 */
    suspend fun deleteFolderBackup(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching { DocumentFile.fromSingleUri(context, uri)?.delete() == true }.getOrDefault(false)
    }

    /**
     * Restore backup from a user-chosen SAF file URI.
     */
    fun restoreFromUri(fileUri: Uri, password: String? = null) {
        viewModelScope.launch {
            flow {
                emit(BackupState.Progress(0))
                val tempFile = File(context.cacheDir, RESTORE_TEMP_NAME)
                try {
                    // 先清掉可能残留的旧临时文件：复制失败必须中止，绝不能拿上一次的包去覆盖数据
                    if (tempFile.exists() && !tempFile.delete()) {
                        throw IOException(context.getString(R.string.backup_error_corrupted))
                    }
                    copyToCache(fileUri, tempFile)
                    emit(BackupState.Progress(30))
                    performRestore(tempFile, password)
                    emit(BackupState.Progress(100))
                    emit(BackupState.Success(""))
                } catch (e: Exception) {
                    emit(BackupState.Error(e.message ?: "Restore failed"))
                } finally {
                    tempFile.delete()
                }
            }.flowOn(Dispatchers.IO).collect { state ->
                _backupState.value = state
            }
        }
    }

    /** 把用户选中的备份复制到缓存；源不可读或为空一律中止，避免进入"关库 → 恢复"流程。 */
    private fun copyToCache(fileUri: Uri, target: File) {
        val input = context.contentResolver.openInputStream(fileUri)
            ?: throw IOException(context.getString(R.string.backup_error_corrupted))
        input.use { source ->
            target.outputStream().use { output -> source.copyTo(output) }
        }
        if (target.length() == 0L) {
            throw IllegalArgumentException(context.getString(R.string.backup_error_corrupted))
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
                    if (!file.exists() || file.length() == 0L) {
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

    /**
     * 恢复公共流程：恢复前快照 → 封堵并发窗口 → 关库 → 覆盖恢复 → 重开库（失败也重开）。
     *
     * 恢复把数据库文件原地替换，窗口期内任何并发数据库访问（自动备份 Worker、
     * 日常检查 Worker、Widget 更新）都会把恢复写坏——三类来源全部封死：
     * 1. 取消 WorkManager 任务（成功重启后 Application.onCreate 会重新注册，无后遗症）
     * 2. 抑制 Widget 刷新广播（WidgetUpdateHelper.restoring）
     * 3. UI 层全屏阻断对话框（BackupScreen），用户无法触发任何导航/点击
     */
    private suspend fun performRestore(sourceFile: File, password: String?) {
        // 封堵必须先于恢复前快照：快照本身也要复制数据库文件，同样怕并发写
        runCatching {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(AutoBackupScheduler.UNIQUE_WORK_NAME)
            wm.cancelUniqueWork(AutoBackupScheduler.INITIAL_WORK_NAME)
            wm.cancelUniqueWork(LifeDailyCheckWorker.UNIQUE_WORK_NAME)
            // cancel 是异步的：正在 RUNNING 的自动备份 Worker 可能仍在复制库文件，
            // 等它退出（最多 3s）再做快照/替换，避免边备份边恢复
            repeat(30) {
                val running = wm.getWorkInfosForUniqueWork(AutoBackupScheduler.UNIQUE_WORK_NAME).get()
                    .orEmpty().any { it.state == androidx.work.WorkInfo.State.RUNNING }
                if (!running) return@runCatching
                kotlinx.coroutines.delay(100)
            }
        }
        WidgetUpdateHelper.setRestoring(true)
        try {
            backupManager.createPreRestoreBackup(context, db)
            closeDatabases()
            try {
                backupManager.restoreBackup(context, sourceFile, password)
                // 成功标记：重启后 Application 补一次全量 Widget 刷新（恢复期间广播被抑制）
                context.getSharedPreferences(RESTORE_FLAGS_PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_JUST_RESTORED, true).commit()
            } finally {
                reopenDatabases()
            }
        } catch (e: Exception) {
            // 失败路径不会重启进程，被取消的后台任务必须立即重建，
            // 否则自动备份/每日检查静默停摆直到下次启动
            runCatching { autoBackupScheduler.sync() }
            runCatching { (context.applicationContext as? PalmNoteApp)?.scheduleDailyCheck() }
            throw e
        } finally {
            WidgetUpdateHelper.setRestoring(false)
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

    // ========== 备份位置（SAF tree URI；空 = 应用私有目录） ==========

    private val backupPrefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)

    /** 备份位置：[folderUri] 为空 = 应用私有目录（默认）。[chosen] = 用户是否已做过首次选择。 */
    data class BackupLocation(val chosen: Boolean, val folderUri: String?)

    private val _backupLocation = MutableStateFlow(readBackupLocation())
    val backupLocation: StateFlow<BackupLocation> = _backupLocation.asStateFlow()

    private fun readBackupLocation(): BackupLocation {
        // 旧版本「导出到文件夹」记录直接升级为备份位置（用户显式选过）
        val uri = backupPrefs.getString("backup_location_uri", null)
            ?: backupPrefs.getString("backup_dir_uri", null)
        return BackupLocation(
            chosen = backupPrefs.getBoolean("backup_location_chosen", false) || uri != null,
            folderUri = uri
        )
    }

    /** 保存备份位置；[folderUri] 为 null 表示应用私有目录。此后手动/自动备份都写到这里。 */
    fun saveBackupLocation(folderUri: String?) {
        backupPrefs.edit()
            .putBoolean("backup_location_chosen", true)
            .putString("backup_location_uri", folderUri)
            .apply()
        _backupLocation.value = BackupLocation(chosen = true, folderUri = folderUri)
    }

    /** 立即备份：写到「备份位置」指向的目录（从未选择过 = 本机内部存储）。 */
    fun createBackupNow() {
        val uri = _backupLocation.value.folderUri
        if (uri != null) createBackupToFolder(Uri.parse(uri))
        else createLocalBackup()
    }

    fun getBackupDir(): Uri? {
        // 新键 backup_location_uri 由「备份位置」写入；旧键 backup_dir_uri 是历史「导出到文件夹」
        val s = backupPrefs.getString("backup_location_uri", null)
            ?: backupPrefs.getString("backup_dir_uri", null) ?: return null
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
                        size = file.length(),
                        kind = BackupKind.fromFileName(name)
                    )
                }
                .sortedByDescending { it.date }
        }.getOrDefault(emptyList())
    }

    /** 备份文件名形如 `palmnote_<kind>_<epochMillis>.palmnote`，从中取回创建时间。 */
    private fun timestampFromName(name: String): Long = BackupKind.timestampFromFileName(name)
}
