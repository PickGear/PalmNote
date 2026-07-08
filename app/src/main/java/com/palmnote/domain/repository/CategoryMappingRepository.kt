package com.palmnote.domain.repository

import com.palmnote.data.db.entity.CategoryMapping
import kotlinx.coroutines.flow.Flow

interface CategoryMappingRepository {
    fun getAllMappings(): Flow<List<CategoryMapping>>
    suspend fun getMappingByAssetCategory(assetCategory: String): CategoryMapping?
    fun getMappingsByBillCategory(billCategory: String): Flow<List<CategoryMapping>>
    suspend fun insertMapping(mapping: CategoryMapping): Long
    suspend fun updateMapping(mapping: CategoryMapping)
    suspend fun deleteMapping(mapping: CategoryMapping)
    suspend fun deleteMappingById(id: Long)
}
