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
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VaultEntry) return false
        return id == other.id &&
                title == other.title &&
                username == other.username &&
                passwordEncrypted.contentEquals(other.passwordEncrypted) &&
                url == other.url &&
                notes == other.notes &&
                category == other.category &&
                createdAt == other.createdAt &&
                updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + passwordEncrypted.contentHashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + notes.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}
