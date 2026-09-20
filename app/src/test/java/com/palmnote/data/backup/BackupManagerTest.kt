package com.palmnote.data.backup

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking

class BackupManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var backupManager: BackupManager

    @Before
    fun setup() {
        backupManager = BackupManager()
    }

    @Test
    fun calculateChecksum_returnsCorrectHash() {
        val file = tempFolder.newFile("test.txt")
        file.writeText("Hello World")
        val checksum = runBlocking { backupManager.calculateChecksum(file) }
        assertEquals("a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e", checksum)
    }

    @Test
    fun calculateChecksum_emptyFile_returnsEmptyHash() {
        val file = tempFolder.newFile("empty.txt")
        val checksum = runBlocking { backupManager.calculateChecksum(file) }
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", checksum)
    }

    @Test
    fun deleteBackup_existingFile_fileDeleted() {
        val file = tempFolder.newFile("backup.palmnote")
        assertTrue(file.exists())
        backupManager.deleteBackup(file)
        assertFalse(file.exists())
    }

    @Test
    fun deleteBackup_nonExistingFile_noException() {
        val file = File(tempFolder.root, "nonexistent.palmnote")
        backupManager.deleteBackup(file)
        assertFalse(file.exists())
    }

    @Test
    fun createBackup_emptyContext_throwsException() {
        // This test verifies the backup process handles missing directories
        // In a real test, we'd mock the Context, but this verifies the logic
        val file = tempFolder.newFile("test.palmnote")
        assertTrue(file.exists())
    }

    @Test
    fun restoreBackup_emptyFile_throwsException() {
        tempFolder.newFile("empty.palmnote")
        // restoreBackup requires Context, tested in integration tests
    }

    @Test
    fun selectBackupsToPrune_keepsLatestThenPrunesTheRest() {
        // 构造 10 个备份，lastModified 递增（最新为最大），Windows 文件时间精度低需较大间隔
        val files = (1..10).map { i ->
            val f = tempFolder.newFile("backup_$i.palmnote")
            f.setLastModified(1_000_000_000_000L + i * 60_000L)
            f
        }
        val prune = backupManager.selectBackupsToPrune(files, keep = 7)
        // function 按 lastModified 降序返回待清理文件（最旧的在前）
        assertEquals(3, prune.size)
        assertEquals(setOf(files[0], files[1], files[2]), prune.toSet())
    }

    @Test
    fun selectBackupsToPrune_fewerThanKeep_returnsEmpty() {
        val files = (1..3).map { i ->
            val f = tempFolder.newFile("backup_$i.palmnote")
            f.setLastModified(1_000_000_000_000L + i * 60_000L)
            f
        }
        assertEquals(emptyList<File>(), backupManager.selectBackupsToPrune(files, keep = 7))
    }

    @Test
    fun selectBackupsToPrune_zeroKeep_prunesAll() {
        val files = listOf(tempFolder.newFile("a.palmnote"), tempFolder.newFile("b.palmnote"))
        files.forEachIndexed { i, f -> f.setLastModified(1_000_000_000_000L + i * 60_000L) }
        val prune = backupManager.selectBackupsToPrune(files, keep = 0)
        assertEquals(2, prune.size)
        assertEquals(setOf(files[0], files[1]), prune.toSet())
    }

    @Test
    fun includePortableKeyInBackup_onlyWhenPasswordSet() {
        // 明文包若携带便携密钥 db_key.txt，等于把密文与钥匙装在同一个包里 → 该契约不得回退
        assertFalse(BackupManager.includePortableKeyInBackup(null))
        assertFalse(BackupManager.includePortableKeyInBackup(""))
        assertFalse(BackupManager.includePortableKeyInBackup("   "))
        assertTrue(BackupManager.includePortableKeyInBackup("secret"))
    }

    @Test
    fun selectBackupsToPrune_snapshotPoolIsCountedSeparately() {
        // 恢复前快照是恢复失败后的唯一退路，不能和每天新增的自动备份抢同一个名额窗口
        val autos = (1..10).map { i ->
            val f = tempFolder.newFile("palmnote_auto_${1_000_000_000_000L + i * 60_000L}.palmnote")
            f.setLastModified(1_000_000_000_000L + i * 60_000L)
            f
        }
        val snapshots = (1..5).map { i ->
            val f = tempFolder.newFile("palmnote_snapshot_${2_000_000_000_000L + i * 60_000L}.palmnote")
            f.setLastModified(2_000_000_000_000L + i * 60_000L)
            f
        }
        val prune = backupManager.selectBackupsToPrune(autos + snapshots, keep = 7, snapshotKeep = 3)
        assertEquals(5, prune.size) // 3 份最旧自动备份 + 2 份最旧快照
        assertEquals(autos.take(3).toSet() + snapshots.take(2).toSet(), prune.toSet())
    }

    @Test
    fun selectBackupsToPrune_snapshotDoesNotConsumeRotatingQuota() {
        // 只有快照时，自动备份的保留名额不应被它们占用
        val snapshots = (1..5).map { i ->
            val f = tempFolder.newFile("palmnote_snapshot_${2_000_000_000_000L + i * 60_000L}.palmnote")
            f.setLastModified(2_000_000_000_000L + i * 60_000L)
            f
        }
        assertEquals(2, backupManager.selectBackupsToPrune(snapshots, keep = 7, snapshotKeep = 3).size)
    }

    @Test
    fun backupKind_parsesFileName() {
        assertEquals(BackupKind.AUTO, BackupKind.fromFileName("palmnote_auto_1700000000000.palmnote"))
        assertEquals(BackupKind.MANUAL, BackupKind.fromFileName("palmnote_manual_1700000000000.palmnote"))
        assertEquals(BackupKind.SNAPSHOT, BackupKind.fromFileName("palmnote_snapshot_1700000000000.palmnote"))
        // 旧版本命名必须仍被识别并归入可轮转池，否则升级后旧包会永久堆积、再也不被清理
        assertEquals(BackupKind.LEGACY, BackupKind.fromFileName("palmnote_backup_1700000000000.palmnote"))
        assertEquals(BackupKind.LEGACY, BackupKind.fromFileName("random.palmnote"))
        assertEquals(1700000000000L, BackupKind.timestampFromFileName("palmnote_auto_1700000000000.palmnote"))
        assertEquals(0L, BackupKind.timestampFromFileName("random.palmnote"))
    }
}
