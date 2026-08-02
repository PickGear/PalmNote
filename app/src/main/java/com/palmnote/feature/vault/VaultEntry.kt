package com.palmnote.feature.vault

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 密码本条目的数据库实体。
 * 仅 [passwordEncrypted] 加密存储，其余字段明文存储以支持 DAO 级搜索/排序。
 * [passwordEncrypted] 格式：iv(12) + AES-GCM 密文（含 tag）。
 */
@Entity(tableName = "vault_entries")
data class VaultEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val username: String = "",
    val passwordEncrypted: ByteArray,
    val url: String = "",
    val notes: String = "",
    val category: String = "其他",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
