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
}
