package com.palmnote.feature.vault

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 密码本仓库实现：DAO 访问 + 密码字段加解密封装。
 */
@Singleton
class VaultRepositoryImpl @Inject constructor(
    private val dao: VaultDao,
    private val keyManager: VaultKeyManager
) : VaultRepository {

    override fun observeEntries(query: String, category: String?): Flow<List<VaultEntry>> =
        if (query.isBlank() && category == null) dao.getAllEntries()
        else if (category != null && query.isBlank()) dao.getEntriesByCategory(category)
        else if (category == null) dao.searchEntries(query.trim())
        else dao.searchEntriesInCategory(query.trim(), category)

    override fun observeCategories(): Flow<List<String>> = dao.getAllCategories()

    override fun observeCount(): Flow<Int> = dao.countEntriesFlow()

    override suspend fun countEntries(): Int = dao.countEntries()

    override suspend fun getEntry(id: Long): VaultEntry? = dao.getEntryById(id)

    override suspend fun create(
        title: String,
        username: String,
        email: String,
        phone: String,
        password: String,
        url: String,
        notes: String,
        category: String,
        avatarPath: String
    ): Long? {
        val encrypted = keyManager.encryptPassword(password) ?: return null
        val now = System.currentTimeMillis()
        return dao.insertEntry(
            VaultEntry(
                title = title.trim(),
                username = username.trim(),
                email = email.trim(),
                phone = phone.trim(),
                passwordEncrypted = encrypted,
                url = url.trim(),
                notes = notes.trim(),
                category = category.trim().ifEmpty { DEFAULT_CATEGORY },
                avatarPath = avatarPath,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override suspend fun update(
        id: Long,
        title: String,
        username: String,
        email: String,
        phone: String,
        password: String,
        url: String,
        notes: String,
        category: String,
        avatarPath: String
    ): Boolean {
        val existing = dao.getEntryById(id) ?: return false
        val encrypted = keyManager.encryptPassword(password) ?: return false
        val now = System.currentTimeMillis()
        dao.updateEntry(
            existing.copy(
                title = title.trim(),
                username = username.trim(),
                email = email.trim(),
                phone = phone.trim(),
                passwordEncrypted = encrypted,
                url = url.trim(),
                notes = notes.trim(),
                category = category.trim().ifEmpty { DEFAULT_CATEGORY },
                avatarPath = avatarPath,
                updatedAt = now
            )
        )
        return true
    }

    override suspend fun delete(entry: VaultEntry) = dao.deleteEntry(entry)

    override suspend fun deleteById(id: Long) {
        dao.getEntryById(id)?.let { dao.deleteEntry(it) }
    }

    override suspend fun clearAll() = dao.clearAll()

    override fun decryptPassword(entry: VaultEntry): String? = keyManager.decryptPassword(entry.passwordEncrypted)

    private companion object {
        const val DEFAULT_CATEGORY = "其他"
    }
}
