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

class BackupManager {

    companion object {
        private const val BACKUP_DIR = "PalmNote"
        private const val BACKUP_EXTENSION = ".palmnote"
    }

    // 获取备份存储目录（使用应用专属外部存储，无需权限，兼容所有 API 级别）
    private fun getBackupDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), BACKUP_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // 创建备份：打包 DB + 图片 + DataStore 为 ZIP
    fun createBackup(context: Context): File {
        val timestamp = System.currentTimeMillis()
        val fileName = "palmnote_backup_${timestamp}$BACKUP_EXTENSION"
        val backupFile = File(getBackupDir(context), fileName)

        ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->
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

        return backupFile
    }

    // 恢复备份：解压 ZIP 覆盖对应数据
    fun restoreBackup(context: Context, backupFile: File) {
        // 简单校验：文件大小大于 0
        if (backupFile.length() == 0L) {
            throw IllegalArgumentException(context.getString(R.string.backup_error_corrupted))
        }

        ZipInputStream(FileInputStream(backupFile)).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val entryName = entry.name
                when {
                    entryName.startsWith("db/") -> {
                        val targetFile = File(context.getDatabasePath(AppDatabase.DATABASE_NAME).parent, entryName.removePrefix("db/"))
                        extractFile(zipIn, targetFile)
                    }
                    entryName.startsWith("prefs/") -> {
                        val targetFile = File(File(context.filesDir, "datastore"), entryName.removePrefix("prefs/"))
                        targetFile.parentFile?.mkdirs()
                        extractFile(zipIn, targetFile)
                    }
                    entryName.startsWith("images/") -> {
                        val targetFile = File(context.filesDir, entryName)
                        targetFile.parentFile?.mkdirs()
                        extractFile(zipIn, targetFile)
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
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
