package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.CrossLink
import com.palmnote.domain.model.EntityType
import com.palmnote.domain.model.LinkType
import kotlinx.coroutines.flow.Flow

@Dao
interface CrossLinkDao {
    @Query("SELECT * FROM cross_links WHERE sourceType = :sourceType AND sourceId = :sourceId ORDER BY createdAt DESC")
    fun getLinksBySource(sourceType: EntityType, sourceId: Long): Flow<List<CrossLink>>

    @Query("SELECT * FROM cross_links WHERE targetType = :targetType AND targetId = :targetId ORDER BY createdAt DESC")
    fun getLinksByTarget(targetType: EntityType, targetId: Long): Flow<List<CrossLink>>

    @Query("SELECT * FROM cross_links WHERE sourceType = :sourceType AND sourceId = :sourceId AND linkType = :linkType")
    suspend fun getLink(sourceType: EntityType, sourceId: Long, linkType: LinkType): CrossLink?

    @Query("SELECT * FROM cross_links WHERE sourceType = :sourceType AND sourceId = :sourceId AND targetType = :targetType AND targetId = :targetId AND linkType = :linkType")
    suspend fun getExactLink(sourceType: EntityType, sourceId: Long, targetType: EntityType, targetId: Long, linkType: LinkType): CrossLink?

    @Query("SELECT EXISTS(SELECT 1 FROM cross_links WHERE sourceType = :sourceType AND sourceId = :sourceId AND targetType = :targetType AND targetId = :targetId AND linkType = :linkType)")
    suspend fun linkExists(sourceType: EntityType, sourceId: Long, targetType: EntityType, targetId: Long, linkType: LinkType): Boolean

    @Query("SELECT * FROM cross_links WHERE linkType = :linkType AND ((sourceType = :typeA AND sourceId = :idA) OR (targetType = :typeA AND targetId = :idA))")
    suspend fun getLinksByEntityAndType(typeA: EntityType, idA: Long, linkType: LinkType): List<CrossLink>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLink(link: CrossLink): Long

    @Delete
    suspend fun deleteLink(link: CrossLink)

    @Query("DELETE FROM cross_links WHERE id = :id")
    suspend fun deleteLinkById(id: Long)

    @Query("DELETE FROM cross_links WHERE sourceType = :sourceType AND sourceId = :sourceId")
    suspend fun deleteLinksBySource(sourceType: EntityType, sourceId: Long)
}
