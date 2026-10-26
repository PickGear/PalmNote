package com.palmnote.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.io.FileInputStream

/**
 * SQLCipher 接入：自定义 [SupportSQLiteOpenHelper.Factory]，在打开数据库前
 * 将历史明文数据库原地迁移为加密数据库，再委托给 [SupportOpenHelperFactory]。
 */
class EncryptedOpenHelperFactory(
    private val context: Context,
    private val keyStore: DbKeyStore
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        ensureLibraryLoaded()
        val rawKey = keyStore.getOrCreateKey()
        // 统一用 **raw key 形态**（x'<hex>'）作为密钥材料：SQLCipher 见到它就跳过 PBKDF2。
        // 此前把 32 字节随机密钥当**口令**喂进去，每次开库都要跑 256000 轮派生
        // —— 真机实测 700~750ms（见 2026-10-18 logcat）。
        val rawMaterial = keyStore.rawKeyPassphrase(rawKey)
        val material = configuration.name?.let { dbName ->
            if (migrateIfPlaintext(context, dbName, rawMaterial)) {
                // 明文库刚迁移成加密库（且已用 raw key 建库），无需再做 rekey
                keyStore.markRawKeyMigrated(dbName)
                rawMaterial
            } else {
                keyMaterialFor(dbName, rawKey, rawMaterial)
            }
        } ?: rawMaterial

        // 第三个参数 = enableWriteAheadLogging。不开时 SQLCipher 的连接池只有一条连接，
        // 首页十几个查询、启动时的初始化/播种全挤在这一条上排队 —— 真机实测（小米 13，
        // 2026-10-18）：开库本身 700~750ms，之后还有 1.18s 的 waitForConnection 等待，
        // 首页数据要 ~2s 才齐。WAL 下读不被写阻塞、连接池可并行，这段基本消失。
        //
        // 备份/恢复侧已按 WAL 处理（不是能随便关的开关）：
        //  - 备份：BackupManager.checkpointAndSnapshot 用 `PRAGMA wal_checkpoint(TRUNCATE)` +
        //    排他事务取一致快照，失败时回退 db + -wal + -shm 三件套；
        //  - 恢复：会删掉「备份里没有的」侧车文件，否则旧库残留的 -wal 会被当成新恢复
        //    主库的未提交数据 → 数据损坏。
        return SupportOpenHelperFactory(material, null, true).create(configuration)
    }

    /**
     * 决定本次开库用哪种密钥材料：优先 raw key（快），迁移不成则退回旧口令形态（慢但能开）。
     *
     * 迁移只在「库已存在 且 没打过标记」时发生，等价于「从旧版本升级上来的第一次启动」。
     * 任何一步失败都不会让 app 打不开：先回滚备份，再退回旧形态。
     */
    private fun keyMaterialFor(
        dbName: String,
        rawKey: ByteArray,
        rawMaterial: ByteArray
    ): ByteArray {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) {
            // 全新安装：马上会用 raw key 建库
            keyStore.markRawKeyMigrated(dbName)
            return rawMaterial
        }
        if (keyStore.isRawKeyMigrated(dbName)) {
            // 已迁移：清掉历史失败/半途迁移可能遗留的整库备份
            deleteStaleMigrationBackup(dbFile)
            return rawMaterial
        }

        return try {
            rekeyToRawKey(dbFile, rawKey, rawMaterial)
            keyStore.markRawKeyMigrated(dbName)
            rawMaterial
        } catch (legacyFailure: Throwable) {
            // 旧口令打不开，可能「库其实已是 raw 形态、只是标记缺失」（恢复新备份 / 首次建库时标记没写成）
            if (canOpenWith(dbFile, rawMaterial)) {
                deleteStaleMigrationBackup(dbFile)
                keyStore.markRawKeyMigrated(dbName)
                rawMaterial
            } else {
                // 迁移失败：**退回旧形态**。宁可每次开库慢 700ms，也不能让用户打不开数据。
                android.util.Log.e(TAG, "raw key 迁移失败，回退旧密钥形态：$dbName", legacyFailure)
                rawKey
            }
        }
    }

    /**
     * 把「口令形态」的库一次性重加密成 raw key 形态。
     *
     * 为什么必须重加密：同样 32 字节，按口令派生出的密钥与直接当 raw key 用的密钥**不是同一个**，
     * 不 rekey 就等于换了密码、库再也打不开。迁移前先整库备份，失败回滚。
     */
    private fun rekeyToRawKey(dbFile: File, rawKey: ByteArray, rawMaterial: ByteArray) {
        val backup = File(dbFile.path + ".pre-rawkey-bak")
        // 先把 WAL 收干并切回普通日志，**再**复制备份：备份那时才包含全部已提交事务。
        // 反过来的话，迁移中途失败会拿 checkpoint 之前的副本回滚，丢掉 WAL 里已提交的数据。
        checkpointIntoMainFile(dbFile, rawKey)
        dbFile.copyTo(backup, overwrite = true)
        val started = System.currentTimeMillis()
        try {
            SQLiteDatabase.openDatabase(
                dbFile.path, rawKey, null, SQLiteDatabase.OPEN_READWRITE, null
            ).use { db ->
                // 日志模式此时已是 DELETE（见 checkpointIntoMainFile）；之后 Room 会以
                // enableWriteAheadLogging=true 重新打开，日志模式自动切回 WAL。
                db.execSQL("PRAGMA rekey = \"${rawMaterial.toString(Charsets.UTF_8)}\"")
                // rekey 是惰性的：真读一次，逼 SQLCipher 走完整重加密
                db.query("SELECT count(*) FROM sqlite_master").use { it.moveToFirst() }
            }
            // 校验：必须能用 raw 形态真正打开，否则宁可回滚（在备份还留着的时候判）
            check(canOpenWith(dbFile, rawMaterial)) { "rekey 后无法用 raw key 打开：${dbFile.name}" }
            backup.delete()
            android.util.Log.i(
                TAG,
                "raw key 迁移完成 ${System.currentTimeMillis() - started}ms（${dbFile.name}）"
            )
        } catch (t: Throwable) {
            // 回滚：把备份盖回去，避免留下半迁移的库；回滚成功后立刻删备份，
            // 否则每次启动都可能残留一份整库大小的 .pre-rawkey-bak（跨设备恢复场景必现）。
            val restored = runCatching { backup.copyTo(dbFile, overwrite = true) }.isSuccess
            if (restored) {
                runCatching { backup.delete() }
            } else {
                android.util.Log.e(TAG, "raw key 迁移回滚失败，保留备份：${backup.name}", t)
            }
            throw t
        }
    }

    /** 把 -wal 并入主库并切回普通日志（非 WAL 库是空操作），供备份前取一致主库。 */
    private fun checkpointIntoMainFile(dbFile: File, rawKey: ByteArray) {
        SQLiteDatabase.openDatabase(
            dbFile.path, rawKey, null, SQLiteDatabase.OPEN_READWRITE, null
        ).use { db ->
            runCatching { db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() } }
            runCatching { db.query("PRAGMA journal_mode = DELETE").use { it.moveToFirst() } }
        }
    }

    /** 删除历史迁移遗留的整库备份（成功/回滚路径原本只在部分分支清理）。 */
    private fun deleteStaleMigrationBackup(dbFile: File) {
        runCatching { File(dbFile.path + ".pre-rawkey-bak").takeIf { it.exists() }?.delete() }
    }

    /** 用给定密钥材料能否读出库结构（用于判断「是不是已经是 raw 形态」）。 */
    private fun canOpenWith(dbFile: File, material: ByteArray): Boolean = try {
        SQLiteDatabase.openDatabase(
            dbFile.path, material, null, SQLiteDatabase.OPEN_READWRITE, null
        ).use { db ->
            db.query("SELECT count(*) FROM sqlite_master").use { it.moveToFirst() }
        }
        true
    } catch (_: Throwable) {
        false
    }

    companion object {
        private const val TAG = "PalmNoteDb"

        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

        @Volatile
        private var libraryLoaded = false

        fun ensureLibraryLoaded() {
            if (!libraryLoaded) {
                synchronized(this) {
                    if (!libraryLoaded) {
                        System.loadLibrary("sqlcipher")
                        libraryLoaded = true
                    }
                }
            }
        }

        /**
         * 将历史明文 Room 数据库原地迁移为 SQLCipher 加密数据库（用**当前密钥材料**加密）。
         * 已加密或不存在时直接跳过；幂等，可安全重复调用。
         *
         * @return true = 本次真的做了迁移（调用方据此跳过 raw key 重加密那一步）
         */
        @Synchronized
        fun migrateIfPlaintext(context: Context, dbName: String, key: ByteArray): Boolean {
            val dbFile = context.getDatabasePath(dbName)
            // 进程死亡恢复：改名备份已就位但加密库未替换完成时，dbFile 不存在而备份在。
            // 不恢复的话 SupportOpenHelperFactory 会静默建一个全新空库，用户数据滞留在备份里。
            // 恢复后不能 return：恢复出来的是明文库，必须继续落入下方流程，
            // 在本次调用内完成加密迁移——否则下一步拿 SQLCipher 密钥开明文库必然报错。
            val backup = File(dbFile.path + ".plaintext-bak")
            if (!dbFile.exists() && backup.exists()) {
                backup.renameTo(dbFile)
            }
            if (!dbFile.exists()) return false
            if (!isPlaintext(dbFile)) return false

            ensureLibraryLoaded()
            val temp = File(context.cacheDir, "palmnote_encrypt_${System.currentTimeMillis()}.db")
            try {
                // 1. 打开明文库（空密码），触发 WAL 恢复并读取版本号
                val version = SQLiteDatabase.openDatabase(
                    dbFile.absolutePath, "", null,
                    SQLiteDatabase.OPEN_READWRITE, null
                ).use { plain ->
                    plain.version
                }

                // 2. 创建加密临时库，ATTACH 明文库并导出
                SQLiteDatabase.openOrCreateDatabase(
                    temp.absolutePath, key, null, null
                ).use { encrypted ->
                    val stmt = encrypted.compileStatement("ATTACH DATABASE ? AS plaintext KEY ''")
                    stmt.bindString(1, dbFile.absolutePath)
                    stmt.execute()
                    encrypted.execSQL("SELECT sqlcipher_export('main', 'plaintext')")
                    encrypted.execSQL("DETACH DATABASE plaintext")
                    encrypted.version = version
                    stmt.close()
                }

                // 3. 原子替换：先把明文库改名留作备份，再改名加密库；仅当加密库就位后才删除备份。
                //    直接 delete 明文库再 rename，中途任何一步失败都会永久丢库。
                val backup = File(dbFile.path + ".plaintext-bak")
                backup.delete()
                if (!dbFile.renameTo(backup)) {
                    throw IllegalStateException("明文库重命名失败，中止加密迁移: $dbFile")
                }
                try {
                    File(dbFile.path + "-wal").delete()
                    File(dbFile.path + "-shm").delete()
                    if (!temp.renameTo(dbFile)) {
                        throw IllegalStateException("加密库改名失败，恢复原库: $dbFile")
                    }
                } catch (e: Throwable) {
                    // 加密库未就位：把明文备份改回原名，保住数据
                    backup.renameTo(dbFile)
                    throw e
                }
                backup.delete()
            } finally {
                temp.delete()
            }
            return true
        }

        /** 明文 SQLite 库首字节为固定头；SQLCipher 加密库为随机盐值，据此区分。 */
        private fun isPlaintext(dbFile: File): Boolean {
            return try {
                FileInputStream(dbFile).use { fis ->
                    val header = ByteArray(16)
                    val read = fis.read(header)
                    read == 16 && header.contentEquals(SQLITE_HEADER)
                }
            } catch (_: Exception) {
                false
            }
        }
    }
}
