package com.palmnote.data.backup

import android.content.Context
import androidx.room.RoomDatabase
import com.palmnote.app.R
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.DbKeyStore
import com.palmnote.feature.vault.VaultDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

class BackupRepository(
    private val context: Context,
    private val db: AppDatabase,
    dbKeyStore: DbKeyStore,
    private val vaultDb: VaultDatabase
) {
    private val backupManager = BackupManager(dbKeyStore)

    // 创建备份（支持可选密码）
    fun createBackup(password: String? = null): Flow<BackupState> = flow {
        emit(BackupState.Progress(0))
        try {
            // 打包前先对密码本库做 WAL checkpoint（TRUNCATE），保证快照一致地并入主文件
            checkpointVaultWal()
            val file = backupManager.createBackup(context, db, password)
            emit(BackupState.Progress(100))
            emit(BackupState.Success(file.absolutePath))
        } catch (e: Exception) {
            emit(BackupState.Error(e.message ?: context.getString(R.string.backup_error_create_failed)))
        }
    }.flowOn(Dispatchers.IO)

    // 恢复备份（支持可选密码）
    fun restoreBackup(file: File, password: String? = null): Flow<BackupState> = flow {
        emit(BackupState.Progress(0))
        try {
            // 恢复前自动备份
            backupManager.createPreRestoreBackup(context, db)

            // 恢复前关闭主库与密码本库，避免文件被占用导致恢复失败
            closeDatabases()
            backupManager.restoreBackup(context, file, password)
            emit(BackupState.Progress(100))
            emit(BackupState.Success(file.absolutePath))
        } catch (e: Exception) {
            emit(BackupState.Error(e.message ?: context.getString(R.string.backup_error_restore_failed)))
        } finally {
            // 无论恢复成功还是失败，确保双数据库可重新打开，避免后续操作崩溃
            reopenDatabases()
        }
    }.flowOn(Dispatchers.IO)

    // 列出所有备份（IO）
    suspend fun listBackups(): List<BackupInfo> = withContext(Dispatchers.IO) {
        backupManager.listBackups(context)
    }

    // 清理旧备份（保留最近 keep 份）
    suspend fun cleanupOldBackups(keep: Int = DEFAULT_KEEP_BACKUPS) {
        backupManager.cleanupOldBackups(context, keep)
    }

    // 删除备份
    fun deleteBackup(file: File) = backupManager.deleteBackup(file)

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

    private companion object {
        const val DEFAULT_KEEP_BACKUPS = 7
    }
}
