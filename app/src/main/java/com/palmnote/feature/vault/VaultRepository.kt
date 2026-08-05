package com.palmnote.feature.vault

import kotlinx.coroutines.flow.Flow

/**
 * 密码本仓库接口：DAO 访问 + 密码字段加解密封装。
 * 上层（ViewModel）不接触加密细节与 DAO。
 */
interface VaultRepository {
    fun observeEntries(query: String, category: String?): Flow<List<VaultEntry>>
    fun observeCategories(): Flow<List<String>>
    fun observeRecent(limit: Int): Flow<List<VaultEntry>>
    fun observeCount(): Flow<Int>
    suspend fun countEntries(): Int
    suspend fun getEntry(id: Long): VaultEntry?
    suspend fun create(
        title: String,
        username: String,
        password: String,
        url: String,
        notes: String,
        category: String
    ): Long?
    suspend fun update(
        id: Long,
        title: String,
        username: String,
        password: String,
        url: String,
        notes: String,
        category: String
    ): Boolean
    suspend fun delete(entry: VaultEntry)
    suspend fun deleteById(id: Long)
    suspend fun clearAll()
    fun decryptPassword(entry: VaultEntry): String?
}
