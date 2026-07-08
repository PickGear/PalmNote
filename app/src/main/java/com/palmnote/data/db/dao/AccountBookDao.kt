package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.AccountBook
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountBookDao {
    @Query("SELECT * FROM account_books WHERE isDeleted = 0 ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllBooksIncludingHidden(): Flow<List<AccountBook>>

    @Query("SELECT * FROM account_books WHERE isDeleted = 0 AND isHidden = 0 ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllBooks(): Flow<List<AccountBook>>

    @Query("SELECT * FROM account_books WHERE isDeleted = 0 AND isHidden = 1 ORDER BY sortOrder ASC, createdAt ASC")
    fun getHiddenBooks(): Flow<List<AccountBook>>

    @Query("SELECT * FROM account_books WHERE id = :id AND isDeleted = 0")
    suspend fun getBookById(id: Long): AccountBook?

    @Query("SELECT * FROM account_books WHERE isDefault = 1 AND isDeleted = 0 AND isHidden = 0 LIMIT 1")
    suspend fun getDefaultBook(): AccountBook?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: AccountBook): Long

    @Update
    suspend fun updateBook(book: AccountBook)

    @Query("UPDATE account_books SET isDefault = 1, updatedAt = :now WHERE id = :id")
    suspend fun setDefault(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE account_books SET isDefault = 0, updatedAt = :now WHERE isDefault = 1")
    suspend fun clearAllDefaults(now: Long = System.currentTimeMillis())

    @Query("UPDATE account_books SET isHidden = :hidden, updatedAt = :now WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE account_books SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteBook(id: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM account_books WHERE id = :id")
    suspend fun hardDeleteBook(id: Long)
}
