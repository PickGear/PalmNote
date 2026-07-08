package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.CategoryMapping
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryMappingDao {
    @Query("SELECT * FROM category_mappings ORDER BY isDefault DESC, assetCategory ASC")
    fun getAllMappings(): Flow<List<CategoryMapping>>

    @Query("SELECT * FROM category_mappings WHERE assetCategory = :assetCategory")
    suspend fun getMappingByAssetCategory(assetCategory: String): CategoryMapping?

    @Query("SELECT * FROM category_mappings WHERE billCategory = :billCategory")
    fun getMappingsByBillCategory(billCategory: String): Flow<List<CategoryMapping>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapping(mapping: CategoryMapping): Long

    @Update
    suspend fun updateMapping(mapping: CategoryMapping)

    @Delete
    suspend fun deleteMapping(mapping: CategoryMapping)

    @Query("DELETE FROM category_mappings WHERE id = :id")
    suspend fun deleteMappingById(id: Long)

    @Query("DELETE FROM category_mappings")
    suspend fun deleteAll()
}
