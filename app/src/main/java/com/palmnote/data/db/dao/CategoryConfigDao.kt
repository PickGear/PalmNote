package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.CategoryConfig
import com.palmnote.data.db.entity.CustomTag
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryConfigDao {
    @Query("SELECT * FROM category_configs WHERE type = :type AND isEnabled = 1 ORDER BY sortOrder ASC, name ASC")
    fun getCategoriesByType(type: String): Flow<List<CategoryConfig>>

    @Query("SELECT * FROM category_configs WHERE type = :type ORDER BY sortOrder ASC")
    fun getAllCategoriesByType(type: String): Flow<List<CategoryConfig>>

    @Query("SELECT * FROM category_configs WHERE id = :id")
    suspend fun getCategoryById(id: Long): CategoryConfig?

    @Query("SELECT * FROM category_configs WHERE type = :type AND name = :name LIMIT 1")
    suspend fun getCategoryByName(type: String, name: String): CategoryConfig?

    @Query("SELECT * FROM category_configs WHERE parentId = :parentId ORDER BY sortOrder ASC")
    fun getSubCategories(parentId: Long): Flow<List<CategoryConfig>>

    @Query("SELECT * FROM category_configs ORDER BY type, sortOrder ASC")
    fun getAllCategories(): Flow<List<CategoryConfig>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryConfig): Long

    @Update
    suspend fun update(category: CategoryConfig)

    @Delete
    suspend fun delete(category: CategoryConfig)

    @Query("DELETE FROM category_configs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE category_configs SET isEnabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM category_configs")
    suspend fun deleteAll()
}

@Dao
interface CustomTagDao {
    @Query("SELECT * FROM custom_tags WHERE isDeleted = 0 ORDER BY usageCount DESC, name ASC")
    fun getAllTags(): Flow<List<CustomTag>>

    @Query("SELECT * FROM custom_tags WHERE applicableTypes LIKE '%' || :type || '%' AND isDeleted = 0 ORDER BY usageCount DESC")
    fun getTagsByType(type: String): Flow<List<CustomTag>>

    @Query("SELECT * FROM custom_tags WHERE name LIKE '%' || :query || '%' AND isDeleted = 0")
    fun searchTags(query: String): Flow<List<CustomTag>>

    @Query("SELECT * FROM custom_tags WHERE id = :id")
    suspend fun getTagById(id: Long): CustomTag?

    @Query("SELECT * FROM custom_tags WHERE name = :name AND isDeleted = 0 LIMIT 1")
    suspend fun getTagByName(name: String): CustomTag?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: CustomTag): Long

    @Update
    suspend fun update(tag: CustomTag)

    @Query("UPDATE custom_tags SET usageCount = usageCount + 1, updatedAt = :now WHERE id = :id")
    suspend fun incrementUsage(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE custom_tags SET isDeleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM custom_tags WHERE id = :id")
    suspend fun hardDelete(id: Long)

    @Query("DELETE FROM custom_tags")
    suspend fun deleteAll()
}
