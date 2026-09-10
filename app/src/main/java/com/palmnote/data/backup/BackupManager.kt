package com.palmnote.data.backup

import android.content.Context
import android.os.Environment
import android.util.Xml
import com.palmnote.app.R
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.DbKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(
    private val dbKeyStore: DbKeyStore? = null
) {

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
        /** 自动备份保留份数 */
        private const val DEFAULT_KEEP_BACKUPS = 7
        /** 便携数据库密钥条目：ZIP 内存放 Base64 原始 db_key，用于跨设备/重装恢复。
         *  **仅写入加密备份**——该条目若与 SQLCipher 密文同处一个未加密的包里，等于把钥匙和保险箱
         *  一起交出去；明文备份不含本条目，故只能在原设备恢复。见 [includePortableKeyInBackup]。 */
        const val PORTABLE_KEY_ENTRY = "db_key.txt"
        private const val TAG = "BackupManager"

        /**
         * 便携密钥是否随备份写入：仅当设置了备份密码（产出加密包 PNB2）时写入。
         * 明文包写入便携密钥会使其自带解密钥匙，数据加密因此归零。
         */
        internal fun includePortableKeyInBackup(password: String?): Boolean = !password.isNullOrBlank()

        // 获取备份存储目录：统一使用应用内部存储，其他应用无法读取。
        // 旧版本使用应用专属外部存储，在 Android 10 及以下可被文件管理器读取，明文备份会经此目录外泄。
        private fun getBackupDir(context: Context): File {
            val dir = File(context.filesDir, BACKUP_DIR)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        /**
         * 迁移旧版本遗留在应用专属外部存储（Android/data/<包名>/files/Download/PalmNote）的备份。
         * 只搬不删：复制成功后才删除源文件，目标同名已存在则跳过。失败静默忽略，不影响启动。
         */
        fun migrateLegacyExternalBackups(context: Context) {
            try {
                val legacyDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?.let { File(it, BACKUP_DIR) } ?: return
                if (!legacyDir.exists()) return
                val targetDir = getBackupDir(context)
                legacyDir.listFiles()?.forEach { file ->
                    if (!file.isFile) return@forEach
                    val dest = File(targetDir, file.name)
                    if (dest.exists()) return@forEach
                    val copied = runCatching { file.copyTo(dest, overwrite = false) }.isSuccess
                    if (copied) runCatching { file.delete() }
                }
            } catch (_: Exception) {
                // 迁移失败不影响功能：新备份已改为写入内部存储
            }
        }
    }

    /**
     * 计算需要清理的旧备份（按最近修改时间排序，保留最新的 [keep] 份）。
     * 纯函数便于单元测试；权限校验/IO 由调用方负责。
     */
    internal fun selectBackupsToPrune(files: List<File>, keep: Int = DEFAULT_KEEP_BACKUPS): List<File> =
        files.sortedByDescending { it.lastModified() }.drop(keep.coerceAtLeast(0))

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
            createZipFile(context, db, tempZip, includePortableKey = includePortableKeyInBackup(password))

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

    // 创建ZIP文件；[includePortableKey] 为真时额外写入便携密钥条目（仅加密备份）
    private fun createZipFile(context: Context, db: AppDatabase, zipFile: File, includePortableKey: Boolean) {
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

            // 2. 密码本独立库（SQLCipher 加密，解密密钥随备份一并打包）
            addVaultDbToZip(context, zipOut)

            // 3. 备份 DataStore 文件
            val datastoreDir = File(context.filesDir, "datastore")
            if (datastoreDir.exists()) {
                datastoreDir.listFiles()?.forEach { file ->
                    addFileToZip(zipOut, file, "prefs/${file.name}")
                }
            }

            // 4. 备份图片目录
            val imagesDir = File(context.filesDir, "images")
            if (imagesDir.exists()) {
                imagesDir.listFiles()?.forEach { file ->
                    addFileToZip(zipOut, file, "images/${file.name}")
                }
            }

            val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")

            // 5. 应用锁 SharedPreferences（PIN salt），缺失会导致恢复后旧版 SHA-256 PIN 无法验证
            val lockPrefs = File(sharedPrefsDir, "$LOCK_PREFS_NAME.xml")
            if (lockPrefs.exists()) {
                addFileToZip(zipOut, lockPrefs, "shared_prefs/${lockPrefs.name}")
            }

            // 6. SQLCipher 数据库密钥（Keystore 包裹，本机恢复用），缺失则恢复后加密库无法解密
            val dbKeyPrefs = File(sharedPrefsDir, "${DbKeyStore.PREFS_NAME}.xml")
            if (dbKeyPrefs.exists()) {
                addFileToZip(zipOut, dbKeyPrefs, "shared_prefs/${dbKeyPrefs.name}")
            }

            // 7. 便携数据库密钥（跨设备/重装恢复用）——仅加密备份写入，明文包携带它等于交出钥匙
            if (includePortableKey) addPortableKeyEntry(zipOut)
        }

        if (hasSnapshot) snapshot.delete()
    }

    /** 打包密码本独立库：带 -wal/-shm 一起保证一致性；缺失会导致恢复后密码本条目丢失。 */
    private fun addVaultDbToZip(context: Context, zipOut: ZipOutputStream) {
        val vaultDb = context.getDatabasePath(com.palmnote.feature.vault.VaultDatabase.DATABASE_NAME)
        if (!vaultDb.exists()) return
        addFileToZip(zipOut, vaultDb, "vault-db/${com.palmnote.feature.vault.VaultDatabase.DATABASE_NAME}")
        for (suffix in listOf("-wal", "-shm")) {
            val sidecar = File(vaultDb.path + suffix)
            if (sidecar.exists()) {
                addFileToZip(zipOut, sidecar, "vault-db/${com.palmnote.feature.vault.VaultDatabase.DATABASE_NAME}$suffix")
            }
        }
    }

    /** 写入便携密钥条目；Keystore 不可用时跳过，不影响本机恢复（仍走 wrapped key）。 */
    private fun addPortableKeyEntry(zipOut: ZipOutputStream) {
        val keyStore = dbKeyStore ?: return
        try {
            val rawKey = keyStore.getOrCreateKey()
            zipOut.putNextEntry(ZipEntry(PORTABLE_KEY_ENTRY))
            zipOut.write(android.util.Base64.encodeToString(rawKey, android.util.Base64.NO_WRAP).toByteArray())
            zipOut.closeEntry()
        } catch (_: Exception) {
            android.util.Log.w(TAG, "createBackup: 便携密钥写入失败，跳过（不影响本机恢复）")
        }
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
                        throw IllegalArgumentException(context.getString(com.palmnote.app.R.string.backup_password_required))
                    }
                    // 流式解密避免OOM；先用当前迭代，失败则用旧版本（100k）迭代重试以兼容旧备份
                    val currentOk = runCatching { decryptToTemp(backupFile, password, tempZip, CryptoUtils.CURRENT_PBKDF2_ITERATIONS) }
                    if (currentOk.isFailure) {
                        if (tempZip.exists()) tempZip.delete()
                        decryptToTemp(backupFile, password, tempZip, CryptoUtils.LEGACY_PBKDF2_ITERATIONS)
                    }
                    restoreFromZipWithPlan(context, tempZip)
                }
                // 新明文备份：PNB3 + ZIP + SHA-256 校验
                magic == MAGIC_PLAIN_V3 -> {
                    extractPlainWithChecksum(backupFile, tempZip)
                    restoreFromZipWithPlan(context, tempZip)
                }
                // 旧明文备份：纯 ZIP
                else -> restoreFromZipWithPlan(context, backupFile)
            }
        } finally {
            // 无论成功失败都清理临时文件
            if (tempZip.exists()) tempZip.delete()
        }
    }

    /** 规划 db_key 恢复方案并执行 ZIP 恢复；方案不可行时在写盘前抛错。 */
    private fun restoreFromZipWithPlan(context: Context, zipFile: File) {
        when (val plan = planDbKeyRestore(zipFile)) {
            is DbKeyPlan.Unsupported -> throw IllegalArgumentException(
                context.getString(R.string.backup_error_cross_device)
            )
            is DbKeyPlan.SameDevice -> restoreFromZip(context, zipFile, skipDbKeyPrefs = false)
            is DbKeyPlan.Portable -> {
                // 跳过备份中的 wrapped key 条目（本机解不开），恢复完成后用本机 Keystore 重新包裹导入；
                // 导入放在恢复成功之后：若导入失败，原密钥未被覆盖，原数据仍可读（另有恢复前自动备份兜底）
                restoreFromZip(context, zipFile, skipDbKeyPrefs = true)
                dbKeyStore?.importRawKey(plan.rawKey)
            }
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

    /**
     * 数据库密钥恢复方案（跨设备/重装兼容）：
     * - [SAME_DEVICE]：备份中 wrapped key 本机 Keystore 能解开 → 原样恢复 prefs 条目；
     * - [PORTABLE]：本机解不开（换机/重装），但备份含便携密钥 → 跳过 prefs 条目，
     *   恢复完成后用 [DbKeyStore.importRawKey] 以本机 Keystore 重新包裹导入；
     * - [UNSUPPORTED]：备份由旧版本创建且无便携密钥 → 拒绝恢复（写盘前拦截）。
     */
    private sealed interface DbKeyPlan {
        data object SameDevice : DbKeyPlan
        class Portable(val rawKey: ByteArray) : DbKeyPlan
        data object Unsupported : DbKeyPlan
    }

    private fun planDbKeyRestore(zipFile: File): DbKeyPlan {
        val keyStore = dbKeyStore ?: return DbKeyPlan.SameDevice
        val wrapped = readZipEntryText(zipFile, "shared_prefs/${DbKeyStore.PREFS_NAME}.xml")
            ?.let { parseDbKeyFromPrefsXml(it) }
            ?: return DbKeyPlan.SameDevice // 无密钥条目：无加密库或极旧备份，按原样恢复
        if (keyStore.canDecryptWrappedKey(wrapped)) return DbKeyPlan.SameDevice

        // 本机解不开 → 依赖便携密钥
        return portableKeyFrom(zipFile) ?: DbKeyPlan.Unsupported
    }

    /** 读取并校验便携密钥条目，合法返回 Portable 方案，缺失/非法返回 null。 */
    private fun portableKeyFrom(zipFile: File): DbKeyPlan.Portable? {
        val b64 = readZipEntryText(zipFile, PORTABLE_KEY_ENTRY)?.trim()
            ?: return null
        val rawKey = runCatching { android.util.Base64.decode(b64, android.util.Base64.NO_WRAP) }
            .getOrNull() ?: return null
        if (rawKey.size != DbKeyStore.KEY_SIZE) return null
        return DbKeyPlan.Portable(rawKey)
    }

    /** 读取 ZIP 中指定条目的完整文本；条目缺失返回 null。 */
    private fun readZipEntryText(zipFile: File, entryName: String): String? {
        return try {
            ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == entryName) {
                        return@use zipIn.readBytes().toString(Charsets.UTF_8)
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 解析 SharedPreferences XML，提取 db_key 值（Base64）。 */
    private fun parseDbKeyFromPrefsXml(xml: String): String? {
        return try {
            val parser = Xml.newPullParser()
            parser.setInput(xml.reader())
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG &&
                    parser.name == "string" &&
                    parser.getAttributeValue(null, "name") == DbKeyStore.KEY_NAME
                ) {
                    return parser.nextText()
                }
                event = parser.next()
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    // 从ZIP文件恢复（带回滚保护）；skipDbKeyPrefs 用于跨设备便携密钥恢复时跳过本机解不开的 wrapped key
    private fun restoreFromZip(context: Context, zipFile: File, skipDbKeyPrefs: Boolean) {
        val dirs = RestoreDirs(
            dbDir = context.getDatabasePath(AppDatabase.DATABASE_NAME).parentFile?.canonicalFile
                ?: throw IllegalStateException("数据库目录不可用"),
            prefsDir = File(context.filesDir, "datastore").canonicalFile,
            imagesDir = File(context.filesDir, "images").canonicalFile,
            sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs").canonicalFile,
            rollbackDir = File(context.cacheDir, "restore_rollback_${System.currentTimeMillis()}")
        )
        val backedUpFiles = backupCurrentData(dirs)
        try {
            extractEntries(zipFile, dirs, skipDbKeyPrefs)
        } catch (e: Exception) {
            rollbackRestore(backedUpFiles, dirs)
            throw e
        } finally {
            dirs.rollbackDir.deleteRecursively()
        }
    }

    /** 恢复涉及的目标目录与回滚目录。 */
    private class RestoreDirs(
        val dbDir: File,
        val prefsDir: File,
        val imagesDir: File,
        val sharedPrefsDir: File,
        val rollbackDir: File
    ) {
        fun all(): List<File> = listOf(dbDir, prefsDir, imagesDir, sharedPrefsDir)
    }

    // Phase 1: 备份现有文件到回滚目录；失败则清理回滚目录并中止恢复
    private fun backupCurrentData(dirs: RestoreDirs): List<File> {
        val backedUpFiles = mutableListOf<File>()
        try {
            for (dir in dirs.all()) {
                if (!dir.exists()) continue
                dir.listFiles()?.forEach { file ->
                    file.copyTo(File(dirs.rollbackDir, file.name), overwrite = true)
                    backedUpFiles.add(file)
                }
            }
        } catch (e: Exception) {
            dirs.rollbackDir.deleteRecursively()
            throw java.io.IOException("Failed to backup current data before restore", e)
        }
        return backedUpFiles
    }

    // Phase 2: 遍历 ZIP 条目逐个解压
    private fun extractEntries(zipFile: File, dirs: RestoreDirs, skipDbKeyPrefs: Boolean) {
        ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                extractEntry(zipIn, entry.name, dirs, skipDbKeyPrefs)
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }

    /** 按条目前缀分发解压；跨设备便携密钥模式下跳过备份的 wrapped key 条目。 */
    private fun extractEntry(zipIn: ZipInputStream, entryName: String, dirs: RestoreDirs, skipDbKeyPrefs: Boolean) {
        val cleanName = entryName.substringAfter('/')
        when {
            entryName.startsWith("db/") -> extractSafe(zipIn, cleanName, dirs.dbDir)
            // 密码本独立库，同样落在 databases 目录，由 dbDir 回滚保护覆盖
            entryName.startsWith("vault-db/") -> extractSafe(zipIn, cleanName, dirs.dbDir)
            entryName.startsWith("prefs/") -> extractSafe(zipIn, cleanName, dirs.prefsDir)
            entryName.startsWith("images/") -> extractSafe(zipIn, cleanName, dirs.imagesDir)
            entryName.startsWith("shared_prefs/") -> {
                if (skipDbKeyPrefs && cleanName == "${DbKeyStore.PREFS_NAME}.xml") return
                extractSafe(zipIn, cleanName, dirs.sharedPrefsDir)
            }
        }
    }

    /** 安全校验后解压单个条目：拒绝路径穿越，目标必须位于目标目录内。 */
    private fun extractSafe(zipIn: ZipInputStream, cleanName: String, targetDir: File) {
        if (cleanName.contains("..") || cleanName.startsWith("/")) return
        val targetFile = File(targetDir, cleanName).canonicalFile
        if (!targetFile.canonicalPath.startsWith(targetDir.canonicalPath)) return
        targetFile.parentFile?.mkdirs()
        extractFile(zipIn, targetFile)
    }

    // 恢复失败回滚：还原已备份文件，并清理恢复过程新产生的 -wal/-shm 残留，
    // 避免旧主库与新 WAL 混用导致数据损坏
    private fun rollbackRestore(backedUpFiles: List<File>, dirs: RestoreDirs) {
        for (file in backedUpFiles) {
            val backupFile = File(dirs.rollbackDir, file.name)
            if (backupFile.exists()) {
                try { backupFile.copyTo(file, overwrite = true) } catch (_: Exception) {}
            }
        }
        for (dir in dirs.all()) {
            deleteWalResidue(backedUpFiles, dir)
        }
    }

    /** 删除回滚目录没有对应副本的 -wal/-shm 残留文件。 */
    private fun deleteWalResidue(backedUpFiles: List<File>, dir: File) {
        dir.listFiles()?.forEach { file ->
            val isResidue = file.name.endsWith("-wal") || file.name.endsWith("-shm")
            val wasBackedUp = backedUpFiles.any { it.canonicalPath == file.canonicalPath }
            if (isResidue && !wasBackedUp) {
                try { file.delete() } catch (_: Exception) {}
            }
        }
    }

    // 恢复前自动备份
    fun createPreRestoreBackup(context: Context, db: AppDatabase): File {
        return createBackup(context, db, null)
    }

    // 列出所有备份文件（IO：文件扫描 + SHA-256 校验和计算）
    suspend fun listBackups(context: Context): List<BackupInfo> = withContext(Dispatchers.IO) {
        val dir = getBackupDir(context)
        dir.listFiles { file -> file.extension == "palmnote" }
            ?.map { file ->
                BackupInfo(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    date = file.lastModified(),
                    size = file.length(),
                    checksum = calculateChecksum(file)
                )
            }
            ?.sortedByDescending { it.date }
            ?: emptyList()
    }

    // 删除备份文件
    fun deleteBackup(file: File) {
        if (file.exists()) file.delete()
    }

    // 清理旧备份：删除超过保留份数（默认 7）的最旧备份
    suspend fun cleanupOldBackups(context: Context, keep: Int = DEFAULT_KEEP_BACKUPS) = withContext(Dispatchers.IO) {
        val files: List<File> = getBackupDir(context).listFiles()
            ?.filter { it.isFile && it.extension == "palmnote" }
            ?: emptyList()
        selectBackupsToPrune(files, keep).forEach { file ->
            if (file.exists() && !file.delete()) {
                android.util.Log.w(TAG, "cleanupOldBackups: failed to delete ${file.name}")
            }
        }
    }

    // 计算文件 SHA-256 校验和（IO；仅用于完整性展示，非加密用途）
    suspend fun calculateChecksum(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
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
