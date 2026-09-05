package com.palmnote.feature.vault

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    @Query("SELECT * FROM vault_entries ORDER BY updatedAt DESC")
    fun getAllEntries(): Flow<List<VaultEntry>>

    @Query("SELECT * FROM vault_entries WHERE id = :id")
    suspend fun getEntryById(id: Long): VaultEntry?

    @Query(
        """SELECT * FROM vault_entries
           WHERE title LIKE '%' || :query || '%'
              OR username LIKE '%' || :query || '%'
              OR email LIKE '%' || :query || '%'
              OR phone LIKE '%' || :query || '%'
              OR url LIKE '%' || :query || '%'
           ORDER BY updatedAt DESC"""
    )
    fun searchEntries(query: String): Flow<List<VaultEntry>>

    @Query("SELECT * FROM vault_entries WHERE category = :category ORDER BY updatedAt DESC")
    fun getEntriesByCategory(category: String): Flow<List<VaultEntry>>

    @Query(
        """SELECT * FROM vault_entries
           WHERE category = :category
             AND (title LIKE '%' || :query || '%'
                  OR username LIKE '%' || :query || '%'
                  OR email LIKE '%' || :query || '%'
                  OR phone LIKE '%' || :query || '%'
                  OR url LIKE '%' || :query || '%')
           ORDER BY updatedAt DESC"""
    )
    fun searchEntriesInCategory(query: String, category: String): Flow<List<VaultEntry>>

    @Query("SELECT DISTINCT category FROM vault_entries ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM vault_entries")
    suspend fun countEntries(): Int

    @Query("SELECT COUNT(*) FROM vault_entries")
    fun countEntriesFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: VaultEntry): Long

    @Update
    suspend fun updateEntry(entry: VaultEntry)

    @Delete
    suspend fun deleteEntry(entry: VaultEntry)

    @Query("DELETE FROM vault_entries")
    suspend fun clearAll()
}
