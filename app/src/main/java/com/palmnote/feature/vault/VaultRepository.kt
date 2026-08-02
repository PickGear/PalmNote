package com.palmnote.feature.vault

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 密码本仓库：DAO 访问 + 密码字段加解密封装。
 * 上层（ViewModel）不接触加密细节与 DAO。
 */
@Singleton
class VaultRepository @Inject constructor(
    private val dao: VaultDao,
    private val keyManager: VaultKeyManager
) {
    fun observeEntries(query: String, category: String?): Flow<List<VaultEntry>> =
        if (query.isBlank() && category == null) dao.getAllEntries()
        else if (category != null && query.isBlank()) dao.getEntriesByCategory(category)
        else if (category == null) dao.searchEntries(query.trim())
        else dao.searchEntriesInCategory(query.trim(), category)

    fun observeCategories(): Flow<List<String>> = dao.getAllCategories()

    fun observeRecent(limit: Int): Flow<List<VaultEntry>> = dao.getRecentEntries(limit)

    fun observeCount(): Flow<Int> = dao.countEntriesFlow()

    suspend fun countEntries(): Int = dao.countEntries()

    suspend fun getEntry(id: Long): VaultEntry? = dao.getEntryById(id)

    /** 新增：加密密码字段后写入。返回新条目 id；未解锁返回 null。 */
    suspend fun create(
        title: String,
        username: String,
        password: String,
        url: String,
        notes: String,
        category: String
    ): Long? {
        val encrypted = keyManager.encryptPassword(password) ?: return null
        val now = System.currentTimeMillis()
        return dao.insertEntry(
            VaultEntry(
                title = title.trim(),
                username = username.trim(),
                passwordEncrypted = encrypted,
                url = url.trim(),
                notes = notes.trim(),
                category = category.trim().ifEmpty { DEFAULT_CATEGORY },
                createdAt = now,
                updatedAt = now
            )
        )
    }

    /** 更新：保持 createdAt，更新 updatedAt。 */
    suspend fun update(
        id: Long,
        title: String,
        username: String,
        password: String,
        url: String,
        notes: String,
        category: String
    ): Boolean {
        val existing = dao.getEntryById(id) ?: return false
        val encrypted = keyManager.encryptPassword(password) ?: return false
        val now = System.currentTimeMillis()
        dao.updateEntry(
            existing.copy(
                title = title.trim(),
                username = username.trim(),
                passwordEncrypted = encrypted,
                url = url.trim(),
                notes = notes.trim(),
                category = category.trim().ifEmpty { DEFAULT_CATEGORY },
                updatedAt = now
            )
        )
        return true
    }

    suspend fun delete(entry: VaultEntry) = dao.deleteEntry(entry)

    suspend fun deleteById(id: Long) {
        dao.getEntryById(id)?.let { dao.deleteEntry(it) }
    }

    /** 重置密码本：清空全部条目。 */
    suspend fun clearAll() = dao.clearAll()

    fun decryptPassword(entry: VaultEntry): String? = keyManager.decryptPassword(entry.passwordEncrypted)

    private companion object {
        const val DEFAULT_CATEGORY = "其他"
    }
}
