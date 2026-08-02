package com.palmnote.data.backup

import android.content.Context
import android.os.Environment
import com.palmnote.R
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.DbKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager {

    companion object {
        private const val BACKUP_DIR = "PalmNote"
        private const val BACKUP_EXTENSION = ".palmnote"
        /** 旧版加密备份 MAGIC（无版本字段） */
        private const val MAGIC = "PNBK"
        /** 新版加密备份 MAGIC */
        private const val MAGIC_ENCRYPTED_V2 = "PNB2"
        /** 新版明文备份 MAGIC（ZIP + SHA-256 校验） */
        private const val MAGIC_PLAIN_V3 = "PNB3"
        private const val HASH_SIZE = 32
        private const val LOCK_PREFS_NAME = "app_lock_prefs"
        /** 备份所需最小可用空间（50MB） */
        private const val MIN_FREE_SPACE = 50L * 1024 * 1024
    }

    // 获取备份存储目录（使用应用专属外部存储，无需权限；外部存储不可用时回退到内部存储）
    private fun getBackupDir(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val dir = File(base, BACKUP_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // 创建备份：打包 DB 一致快照 + 图片 + DataStore + 应用锁 SharedPreferences 为 ZIP，支持可选加密
    fun createBackup(context: Context, db: AppDatabase, password: String? = null): File {
        val timestamp = System.currentTimeMillis()
        val fileName = "palmnote_backup_${timestamp}$BACKUP_EXTENSION"
        val backupFile = File(getBackupDir(context), fileName)

        // 低存储预检：可用空间不足时提前失败，避免写一半
        val usable = getBackupDir(context).usableSpace
        if (usable < MIN_FREE_SPACE) {
            throw java.io.IOException("存储空间不足（可用 ${usable / (1024 * 1024)}MB，至少需 ${MIN_FREE_SPACE / (1024 * 1024)}MB）")
        }

        // 创建临时ZIP文件（UUID 命名避免并发冲突）
        val tempZip = File(context.cacheDir, "temp_backup_${java.util.UUID.randomUUID()}.zip")
        try {
            createZipFile(context, db, tempZip)

            // 如果有密码，加密ZIP文件（流式处理避免OOM）
            if (!password.isNullOrBlank()) {
                val salt = CryptoUtils.generateSalt()
                val key = CryptoUtils.deriveKey(password, salt)
                FileOutputStream(backupFile).use { fos ->
                    fos.write(MAGIC_ENCRYPTED_V2.toByteArray())
                    fos.write(salt)
                    FileInputStream(tempZip).use { fis -> CryptoUtils.encryptStream(fis, fos, key) }
                }
            } else {
                // 无密码：写入 MAGIC + ZIP 内容 + SHA-256 完整性校验
                FileOutputStream(backupFile).use { fos ->
                    fos.write(MAGIC_PLAIN_V3.toByteArray())
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    FileInputStream(tempZip).use { fis ->
                        val buf = ByteArray(8192)
                        var read: Int
                        while (fis.read(buf).also { read = it } != -1) {
                            fos.write(buf, 0, read)
                            digest.update(buf, 0, read)
                        }
                    }
                    fos.write(digest.digest())
                }
            }
        } finally {
            // 无论成功失败都清理临时文件
            if (tempZip.exists()) tempZip.delete()
        }
        return backupFile
    }

    // 创建ZIP文件
    private fun createZipFile(context: Context, db: AppDatabase, zipFile: File) {
        // 先做 WAL checkpoint 再复制主库文件，保证快照一致（避免 -wal/-shm 与主库不一致）
        val snapshot = File(context.cacheDir, "db_snapshot_${System.currentTimeMillis()}")
        val hasSnapshot = checkpointAndSnapshot(context, db, snapshot)

        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            // 1. 数据库（优先用一致快照）
            if (hasSnapshot) {
                addFileToZip(zipOut, snapshot, "db/${AppDatabase.DATABASE_NAME}")
            } else {
                val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
                if (dbFile.exists()) {
                    addFileToZip(zipOut, dbFile, "db/${AppDatabase.DATABASE_NAME}")
                }
                val walFile = File(dbFile.path + "-wal")
                if (walFile.exists()) addFileToZip(zipOut, walFile, "db/${AppDatabase.DATABASE_NAME}-wal")
                val shmFile = File(dbFile.path + "-shm")
                if (shmFile.exists()) addFileToZip(zipOut, shmFile, "db/${AppDatabase.DATABASE_NAME}-shm")
            }

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

            // 4. 应用锁 SharedPreferences（PIN salt），缺失会导致恢复后旧版 SHA-256 PIN 无法验证
            val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            val lockPrefs = File(sharedPrefsDir, "$LOCK_PREFS_NAME.xml")
            if (lockPrefs.exists()) {
                addFileToZip(zipOut, lockPrefs, "shared_prefs/${lockPrefs.name}")
            }

            // 5. SQLCipher 数据库密钥，缺失则跨设备恢复后加密库无法解密
            val dbKeyPrefs = File(sharedPrefsDir, "${DbKeyStore.PREFS_NAME}.xml")
            if (dbKeyPrefs.exists()) {
                addFileToZip(zipOut, dbKeyPrefs, "shared_prefs/${dbKeyPrefs.name}")
            }
        }

        if (hasSnapshot) snapshot.delete()
    }

    // 执行 WAL checkpoint 并复制主库为一致快照；失败或 checkpoint 未完成（busy>0）返回 false，
    // 由调用方回退为原始 db+wal+shm 打包，避免遗漏尚未并入主库的 WAL 页导致备份丢数据
    private fun checkpointAndSnapshot(context: Context, db: AppDatabase, target: File): Boolean {
        return try {
            val busy = db.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else -1
                }
            if (busy != 0) return false
            val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
            if (!dbFile.exists()) return false
            dbFile.copyTo(target, overwrite = true)
            true
        } catch (_: Exception) {
            false
        }
    }

    // 恢复备份：解压 ZIP 覆盖对应数据，支持可选解密
    fun restoreBackup(context: Context, backupFile: File, password: String? = null) {
        if (backupFile.length() == 0L) {
            throw IllegalArgumentException(context.getString(R.string.backup_error_corrupted))
        }

        val magic = readMagic(backupFile)
        val tempZip = File(context.cacheDir, "temp_restore.zip")

        try {
            when {
                // 加密备份（旧 PNBK / 新 PNB2）
                magic == MAGIC || magic == MAGIC_ENCRYPTED_V2 -> {
                    if (password.isNullOrBlank()) {
                        throw IllegalArgumentException(context.getString(com.palmnote.R.string.backup_password_required))
                    }
                    // 流式解密避免OOM；先用当前迭代，失败则用旧版本（100k）迭代重试以兼容旧备份
                    val currentOk = runCatching { decryptToTemp(backupFile, password, tempZip, CryptoUtils.CURRENT_PBKDF2_ITERATIONS) }
                    if (currentOk.isFailure) {
                        if (tempZip.exists()) tempZip.delete()
                        decryptToTemp(backupFile, password, tempZip, CryptoUtils.LEGACY_PBKDF2_ITERATIONS)
                    }
                    restoreFromZip(context, tempZip)
                }
                // 新明文备份：PNB3 + ZIP + SHA-256 校验
                magic == MAGIC_PLAIN_V3 -> {
                    extractPlainWithChecksum(backupFile, tempZip)
                    restoreFromZip(context, tempZip)
                }
                // 旧明文备份：纯 ZIP
                else -> restoreFromZip(context, backupFile)
            }
        } finally {
            // 无论成功失败都清理临时文件
            if (tempZip.exists()) tempZip.delete()
        }
    }

    private fun readMagic(backupFile: File): String {
        return try {
            FileInputStream(backupFile).use { fis ->
                val magic = ByteArray(4)
                if (fis.read(magic) != 4) "" else String(magic)
            }
        } catch (_: Exception) { "" }
    }

    /** 提取 PNB3 明文 ZIP 并验证末尾 SHA-256 校验和 */
    @Suppress("NestedBlockDepth", "ThrowsCount", "UseRequire")
    private fun extractPlainWithChecksum(backupFile: File, tempZip: File) {
        val fileLen = backupFile.length()
        val zipLen = fileLen - 4 - HASH_SIZE
        if (zipLen <= 0) throw IllegalArgumentException("备份文件损坏")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        FileInputStream(backupFile).use { fis ->
            val magic = ByteArray(4); fis.read(magic)
            FileOutputStream(tempZip).use { fos ->
                val buf = ByteArray(8192)
                var remaining = zipLen
                while (remaining > 0) {
                    val read = fis.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                    if (read < 0) throw java.io.IOException("备份文件截断")
                    fos.write(buf, 0, read)
                    digest.update(buf, 0, read)
                    remaining -= read
                }
            }
            val expected = ByteArray(HASH_SIZE)
            if (fis.read(expected) != HASH_SIZE) throw java.io.IOException("备份文件截断")
            if (!java.security.MessageDigest.isEqual(digest.digest(), expected)) {
                throw java.io.IOException("备份完整性校验失败")
            }
        }
    }

    private fun decryptToTemp(backupFile: File, password: String, tempZip: File, iterations: Int) {
        FileInputStream(backupFile).use { fis ->
            val magic = ByteArray(4); fis.read(magic)
            val salt = ByteArray(16); fis.read(salt)
            val key = CryptoUtils.deriveKey(password, salt, iterations)
            FileOutputStream(tempZip).use { fos -> CryptoUtils.decryptStream(fis, fos, key) }
        }
    }

    // 从ZIP文件恢复（带回滚保护）
    private fun restoreFromZip(context: Context, zipFile: File) {
        val dbDir = context.getDatabasePath(AppDatabase.DATABASE_NAME).parentFile?.canonicalFile
            ?: throw IllegalStateException("数据库目录不可用")
        val prefsDir = File(context.filesDir, "datastore").canonicalFile
        val imagesDir = File(context.filesDir, "images").canonicalFile
        val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs").canonicalFile
        val rollbackDir = File(context.cacheDir, "restore_rollback_${System.currentTimeMillis()}")

        // Phase 1: Backup existing files to rollback directory
        val backedUpFiles = mutableListOf<File>()
        try {
            for (dir in listOf(dbDir, prefsDir, imagesDir, sharedPrefsDir)) {
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
                        entryName.startsWith("shared_prefs/") -> {
                            val cleanName = entryName.removePrefix("shared_prefs/")
                            if (!cleanName.contains("..") && !cleanName.startsWith("/")) {
                                val targetFile = File(sharedPrefsDir, cleanName).canonicalFile
                                if (targetFile.canonicalPath.startsWith(sharedPrefsDir.canonicalPath)) {
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
    fun createPreRestoreBackup(context: Context, db: AppDatabase): File {
        return createBackup(context, db, null)
    }

    // 列出所有备份文件（IO：文件扫描 + MD5 计算）
    suspend fun listBackups(context: Context): List<BackupInfo> = withContext(Dispatchers.IO) {
        val dir = getBackupDir(context)
        dir.listFiles { file -> file.extension == "palmnote" }
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

    // 计算文件 MD5（IO）
    suspend fun calculateMd5(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
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
