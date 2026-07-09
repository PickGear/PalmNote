package com.palmnote.data.backup

import android.content.Context
import com.palmnote.R
import com.palmnote.data.db.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase
) {
    private val backupManager = BackupManager()

    // 创建备份（支持可选密码）
    fun createBackup(password: String? = null): Flow<BackupState> = flow {
        emit(BackupState.Progress(0))
        try {
            val file = backupManager.createBackup(context, password)
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
            backupManager.createPreRestoreBackup(context)
            
            db.close()
            backupManager.restoreBackup(context, file, password)
            emit(BackupState.Progress(100))
            emit(BackupState.Success(file.absolutePath))
        } catch (e: Exception) {
            emit(BackupState.Error(e.message ?: context.getString(R.string.backup_error_restore_failed)))
        } finally {
            // 无论恢复成功还是失败，确保数据库可重新打开，避免后续操作崩溃
            try { db.openHelper.writableDatabase } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)

    // 列出所有备份
    fun listBackups(): List<BackupInfo> = backupManager.listBackups(context)

    // 删除备份
    fun deleteBackup(file: File) = backupManager.deleteBackup(file)
}
