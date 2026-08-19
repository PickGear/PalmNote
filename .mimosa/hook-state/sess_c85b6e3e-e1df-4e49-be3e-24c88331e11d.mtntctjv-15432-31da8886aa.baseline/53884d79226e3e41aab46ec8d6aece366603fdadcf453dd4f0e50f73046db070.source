package com.palmnote.domain.repository

import com.palmnote.data.db.entity.CrossLink
import com.palmnote.domain.model.EntityType
import com.palmnote.domain.model.LinkType
import kotlinx.coroutines.flow.Flow

interface CrossLinkRepository {
    fun getLinksBySource(sourceType: EntityType, sourceId: Long): Flow<List<CrossLink>>
    fun getLinksByTarget(targetType: EntityType, targetId: Long): Flow<List<CrossLink>>
    suspend fun getLink(sourceType: EntityType, sourceId: Long, linkType: LinkType): CrossLink?
    suspend fun linkExists(sourceType: EntityType, sourceId: Long, targetType: EntityType, targetId: Long, linkType: LinkType): Boolean
    suspend fun getLinksByEntityAndType(typeA: EntityType, idA: Long, linkType: LinkType): List<CrossLink>
    suspend fun createLink(link: CrossLink): Long
    suspend fun deleteLink(id: Long)
    suspend fun deleteLinksBySource(sourceType: EntityType, sourceId: Long)
}
