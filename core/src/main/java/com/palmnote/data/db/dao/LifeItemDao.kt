package com.palmnote.data.db.dao

import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import com.palmnote.data.db.entity.LifeItem
import kotlinx.coroutines.flow.Flow

@Dao
interface LifeItemDao {
    @Query("SELECT * FROM life_items ORDER BY updatedAt DESC")
    fun getAllItems(): Flow<List<LifeItem>>

    @Query("SELECT * FROM life_items WHERE templateId = :templateId ORDER BY sortOrder, createdAt DESC")
    fun getItemsByTemplate(templateId: Long): Flow<List<LifeItem>>

    @Query("SELECT * FROM life_items WHERE templateId = :templateId AND status = :status ORDER BY sortOrder, createdAt DESC")
    fun getItemsByTemplateAndStatus(templateId: Long, status: String): Flow<List<LifeItem>>

    @Query("SELECT * FROM life_items WHERE id = :id")
    suspend fun getItemById(id: Long): LifeItem?

    @Query("SELECT * FROM life_items WHERE id = :id")
    fun getItemByIdFlow(id: Long): Flow<LifeItem?>

    @Query("SELECT * FROM life_items WHERE templateId = :templateId AND status = 'ACTIVE' ORDER BY sortOrder, createdAt DESC LIMIT :limit")
    fun getActiveItemsByTemplate(templateId: Long, limit: Int = 5): Flow<List<LifeItem>>

    @Query("SELECT COUNT(*) FROM life_items WHERE templateId = :templateId")
    fun getItemCountByTemplate(templateId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM life_items WHERE 1=1")
    fun getTotalItemCount(): Flow<Int>

    @Query("SELECT * FROM life_items ORDER BY updatedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getAllItemsPaged(offset: Int, limit: Int): List<LifeItem>

    @Query("SELECT * FROM life_items WHERE templateId = :templateId ORDER BY sortOrder, createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getItemsByTemplatePaged(templateId: Long, offset: Int, limit: Int): List<LifeItem>

    @Query("SELECT * FROM life_items WHERE (title LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%') ORDER BY updatedAt DESC LIMIT 50")
    suspend fun search(query: String): List<LifeItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: LifeItem): Long

    @Query("""
        UPDATE life_items SET 
            title = :title, fieldsData = :fieldsData, status = :status, 
            note = :note, sortOrder = :sortOrder, isFavorite = :isFavorite, 
            updatedAt = :now 
        WHERE id = :id
    """)
    suspend fun updateItem(id: Long, title: String, fieldsData: String, status: String, note: String, sortOrder: Int, isFavorite: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE life_items SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE life_items SET fieldsData = :fieldsData, updatedAt = :now WHERE id = :id")
    suspend fun updateFieldsData(id: Long, fieldsData: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE life_items SET isFavorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long = System.currentTimeMillis())



    @Query("DELETE FROM life_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("DELETE FROM life_items")
    suspend fun deleteAll()
}
