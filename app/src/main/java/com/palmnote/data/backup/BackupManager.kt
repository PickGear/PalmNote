package com.palmnote.data.backup

import android.content.Context
import android.os.Environment
import com.palmnote.R
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.palmnote.data.db.AppDatabase
import javax.crypto.SecretKey

class BackupManager {

    companion object {
        private const val BACKUP_DIR = "PalmNote"
        private const val BACKUP_EXTENSION = ".palmnote"
        private const val MAGIC = "PNBK"
    }

    // 获取备份存储目录（使用应用专属外部存储，无需权限，兼容所有 API 级别）
    private fun getBackupDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), BACKUP_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // 创建备份：打包 DB + 图片 + DataStore 为 ZIP，支持可选加密
    fun createBackup(context: Context, password: String? = null): File {
        val timestamp = System.currentTimeMillis()
        val fileName = "palmnote_backup_${timestamp}$BACKUP_EXTENSION"
        val backupFile = File(getBackupDir(context), fileName)

        // 创建临时ZIP文件
        val tempZip = File(context.cacheDir, "temp_backup.zip")
        createZipFile(context, tempZip)

        // 如果有密码，加密ZIP文件（流式处理避免OOM）
        if (!password.isNullOrBlank()) {
            val salt = CryptoUtils.generateSalt()
            val key = CryptoUtils.deriveKey(password, salt)
            FileOutputStream(backupFile).use { fos ->
                fos.write(MAGIC.toByteArray())
                fos.write(salt)
                FileInputStream(tempZip).use { fis -> CryptoUtils.encryptStream(fis, fos, key) }
            }
        } else {
            // 无密码，直接复制
            tempZip.copyTo(backupFile, overwrite = true)
        }

        tempZip.delete()
        return backupFile
    }

    // 创建ZIP文件
    private fun createZipFile(context: Context, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            // 1. 备份数据库文件
            val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
            if (dbFile.exists()) {
                addFileToZip(zipOut, dbFile, "db/${AppDatabase.DATABASE_NAME}")
            }
            // 备份 WAL 和 SHM 文件（如果存在）
            val walFile = File(dbFile.path + "-wal")
            if (walFile.exists()) addFileToZip(zipOut, walFile, "db/${AppDatabase.DATABASE_NAME}-wal")
            val shmFile = File(dbFile.path + "-shm")
            if (shmFile.exists()) addFileToZip(zipOut, shmFile, "db/${AppDatabase.DATABASE_NAME}-shm")

            // 2. 备份 DataStore 文件
            val datastoreDir = File(context.filesDir, "datastore")
            if (datastoreDir.exists()) {
                datastoreDir.listFiles()?.forEach { file ->
                    addFileToZip(zipOut, file, "prefs/${file.name}")
                }
            }

            // 3. 备份图片目录
            val imagesDir = File(context.filesDir, "images")
            if (imagesDir.exists()) {
                imagesDir.listFiles()?.forEach { file ->
                    addFileToZip(zipOut, file, "images/${file.name}")
                }
            }
        }
    }

    // 恢复备份：解压 ZIP 覆盖对应数据，支持可选解密
    fun restoreBackup(context: Context, backupFile: File, password: String? = null) {
        // 简单校验：文件大小大于 0
        if (backupFile.length() == 0L) {
            throw IllegalArgumentException(context.getString(R.string.backup_error_corrupted))
        }

        // 检测是否为加密备份
        val isEncrypted = CryptoUtils.isEncryptedBackup(backupFile)

        if (isEncrypted) {
            if (password.isNullOrBlank()) {
                throw IllegalArgumentException("需要密码才能恢复此备份")
            }

            // 流式解密避免OOM
            val tempZip = File(context.cacheDir, "temp_restore.zip")
            FileInputStream(backupFile).use { fis ->
                val magic = ByteArray(4); fis.read(magic)
                val salt = ByteArray(16); fis.read(salt)
                val key = CryptoUtils.deriveKey(password, salt)
                FileOutputStream(tempZip).use { fos -> CryptoUtils.decryptStream(fis, fos, key) }
            }
            restoreFromZip(context, tempZip)
            tempZip.delete()
        } else {
            // 未加密，直接恢复
            restoreFromZip(context, backupFile)
        }
    }

    // 从ZIP文件恢复（带回滚保护）
    private fun restoreFromZip(context: Context, zipFile: File) {
        val dbDir = context.getDatabasePath(AppDatabase.DATABASE_NAME).parentFile!!.canonicalFile
        val prefsDir = File(context.filesDir, "datastore").canonicalFile
        val imagesDir = File(context.filesDir, "images").canonicalFile
        val rollbackDir = File(context.cacheDir, "restore_rollback_${System.currentTimeMillis()}")

        // Phase 1: Backup existing files to rollback directory
        val backedUpFiles = mutableListOf<File>()
        try {
            for (dir in listOf(dbDir, prefsDir, imagesDir)) {
                if (dir.exists()) {
                    dir.listFiles()?.forEach { file ->
                        val backup = File(rollbackDir, file.name)
                        file.copyTo(backup, overwrite = true)
                        backedUpFiles.add(file)
                    }
                }
            }
        } catch (e: Exception) {
            // If backup fails, clean up and abort restore
            rollbackDir.deleteRecursively()
            throw java.io.IOException("Failed to backup current data before restore", e)
        }

        // Phase 2: Perform restore
        var restoreFailed = false
        try {
            ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    when {
                        entryName.startsWith("db/") -> {
                            val cleanName = entryName.removePrefix("db/")
                            if (!cleanName.contains("..") && !cleanName.startsWith("/")) {
                                val targetFile = File(dbDir, cleanName).canonicalFile
                                if (targetFile.canonicalPath.startsWith(dbDir.canonicalPath)) {
                                    targetFile.parentFile?.mkdirs()
                                    extractFile(zipIn, targetFile)
                                }
                            }
                        }
                        entryName.startsWith("prefs/") -> {
                            val cleanName = entryName.removePrefix("prefs/")
                            if (!cleanName.contains("..") && !cleanName.startsWith("/")) {
                                val targetFile = File(prefsDir, cleanName).canonicalFile
                                if (targetFile.canonicalPath.startsWith(prefsDir.canonicalPath)) {
                                    targetFile.parentFile?.mkdirs()
                                    extractFile(zipIn, targetFile)
                                }
                            }
                        }
                        entryName.startsWith("images/") -> {
                            val cleanName = entryName.removePrefix("images/")
                            if (!cleanName.contains("..") && !cleanName.startsWith("/")) {
                                val targetFile = File(imagesDir, cleanName).canonicalFile
                                if (targetFile.canonicalPath.startsWith(imagesDir.canonicalPath)) {
                                    targetFile.parentFile?.mkdirs()
                                    extractFile(zipIn, targetFile)
                                }
                            }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        } catch (e: Exception) {
            restoreFailed = true
            // Rollback: restore backed up files
            for (file in backedUpFiles) {
                val backupFile = File(rollbackDir, file.name)
                if (backupFile.exists()) {
                    try { backupFile.copyTo(file, overwrite = true) } catch (_: Exception) {}
                }
            }
            throw e
        } finally {
            rollbackDir.deleteRecursively()
        }
    }

    // 恢复前自动备份
    fun createPreRestoreBackup(context: Context): File {
        return createBackup(context, null)
    }

    // 列出所有备份文件
    fun listBackups(context: Context): List<BackupInfo> {
        val dir = getBackupDir(context)
        return dir.listFiles { file -> file.extension == "palmnote" }
            ?.map { file ->
                BackupInfo(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    date = file.lastModified(),
                    size = file.length(),
                    md5 = calculateMd5(file)
                )
            }
            ?.sortedByDescending { it.date }
            ?: emptyList()
    }

    // 删除备份文件
    fun deleteBackup(file: File) {
        if (file.exists()) file.delete()
    }

    // 计算文件 MD5
    fun calculateMd5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // 添加文件到 ZIP
    private fun addFileToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
        zipOut.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { fis ->
            fis.copyTo(zipOut)
        }
        zipOut.closeEntry()
    }

    // 从 ZIP 解压文件
    private fun extractFile(zipIn: ZipInputStream, targetFile: File) {
        FileOutputStream(targetFile).use { fos ->
            zipIn.copyTo(fos)
        }
    }
}
