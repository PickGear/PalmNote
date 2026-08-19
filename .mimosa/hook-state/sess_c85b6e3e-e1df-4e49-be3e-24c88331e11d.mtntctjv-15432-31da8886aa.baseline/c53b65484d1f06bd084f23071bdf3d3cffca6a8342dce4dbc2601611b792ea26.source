package com.palmnote.data.repository
import javax.inject.Inject

import com.palmnote.data.db.dao.CrossLinkDao
import com.palmnote.data.db.entity.CrossLink
import com.palmnote.domain.model.EntityType
import com.palmnote.domain.model.LinkType
import com.palmnote.domain.repository.CrossLinkRepository
import kotlinx.coroutines.flow.Flow
class CrossLinkRepositoryImpl @Inject constructor(
    private val dao: CrossLinkDao
) : CrossLinkRepository {
    override fun getLinksBySource(sourceType: EntityType, sourceId: Long): Flow<List<CrossLink>> = dao.getLinksBySource(sourceType, sourceId)
    override fun getLinksByTarget(targetType: EntityType, targetId: Long): Flow<List<CrossLink>> = dao.getLinksByTarget(targetType, targetId)
    override suspend fun getLink(sourceType: EntityType, sourceId: Long, linkType: LinkType): CrossLink? = dao.getLink(sourceType, sourceId, linkType)
    override suspend fun linkExists(sourceType: EntityType, sourceId: Long, targetType: EntityType, targetId: Long, linkType: LinkType): Boolean = dao.linkExists(sourceType, sourceId, targetType, targetId, linkType)
    override suspend fun getLinksByEntityAndType(typeA: EntityType, idA: Long, linkType: LinkType): List<CrossLink> = dao.getLinksByEntityAndType(typeA, idA, linkType)
    override suspend fun createLink(link: CrossLink): Long = dao.insertLink(link)
    override suspend fun deleteLink(id: Long) = dao.deleteLinkById(id)
    override suspend fun deleteLinksBySource(sourceType: EntityType, sourceId: Long) = dao.deleteLinksBySource(sourceType, sourceId)
}
