package com.palmnote.data.repository

import com.palmnote.data.db.dao.CategoryMappingDao
import com.palmnote.data.db.entity.CategoryMapping
import kotlinx.coroutines.flow.Flow
import com.palmnote.domain.repository.CategoryMappingRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryMappingRepository @Inject constructor(
    private val categoryMappingDao: CategoryMappingDao
) : CategoryMappingRepository {
    override fun getAllMappings(): Flow<List<CategoryMapping>> = categoryMappingDao.getAllMappings()

    override suspend fun getMappingByAssetCategory(assetCategory: String): CategoryMapping? =
        categoryMappingDao.getMappingByAssetCategory(assetCategory)

    override fun getMappingsByBillCategory(billCategory: String): Flow<List<CategoryMapping>> =
        categoryMappingDao.getMappingsByBillCategory(billCategory)

    override suspend fun insertMapping(mapping: CategoryMapping): Long =
        categoryMappingDao.insertMapping(mapping)

    override suspend fun updateMapping(mapping: CategoryMapping) =
        categoryMappingDao.updateMapping(mapping)

    override suspend fun deleteMapping(mapping: CategoryMapping) =
        categoryMappingDao.deleteMapping(mapping)

    override suspend fun deleteMappingById(id: Long) =
        categoryMappingDao.deleteMappingById(id)
}
