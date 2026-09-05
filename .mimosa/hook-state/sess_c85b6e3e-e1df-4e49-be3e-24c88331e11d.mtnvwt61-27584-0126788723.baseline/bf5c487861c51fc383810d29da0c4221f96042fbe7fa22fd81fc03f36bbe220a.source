package com.palmnote.domain.repository

import com.palmnote.data.db.entity.CategoryConfig
import com.palmnote.data.db.entity.CustomTag
import kotlinx.coroutines.flow.Flow

interface CategoryConfigRepository {
    fun getCategoriesByType(type: String): Flow<List<CategoryConfig>>
    fun getAllCategoriesByType(type: String): Flow<List<CategoryConfig>>
    fun getAllCategories(): Flow<List<CategoryConfig>>
    suspend fun getCategoryById(id: Long): CategoryConfig?
    suspend fun getCategoryByName(type: String, name: String): CategoryConfig?
    fun getSubCategories(parentId: Long): Flow<List<CategoryConfig>>
    suspend fun insertCategory(category: CategoryConfig): Long
    suspend fun updateCategory(category: CategoryConfig)
    suspend fun deleteCategory(category: CategoryConfig)
    suspend fun deleteCategoryById(id: Long)
    suspend fun setCategoryEnabled(id: Long, enabled: Boolean)
    fun getAllTags(): Flow<List<CustomTag>>
    fun getTagsByType(type: String): Flow<List<CustomTag>>
    fun searchTags(query: String): Flow<List<CustomTag>>
    suspend fun getTagById(id: Long): CustomTag?
    suspend fun getTagByName(name: String): CustomTag?
    suspend fun insertTag(tag: CustomTag): Long
    suspend fun updateTag(tag: CustomTag)
    suspend fun incrementTagUsage(id: Long)
    suspend fun deleteTag(id: Long)
    suspend fun initDefaultCategories()
}
