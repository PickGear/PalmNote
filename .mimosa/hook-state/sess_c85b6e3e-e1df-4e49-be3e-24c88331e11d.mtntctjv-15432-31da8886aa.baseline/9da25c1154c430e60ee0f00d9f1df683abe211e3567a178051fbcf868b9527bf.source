package com.palmnote.data.db.dao

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.palmnote.data.db.entity.LifeItem

class LifeItemPagingSource(
    private val dao: LifeItemDao,
    private val templateId: Long? = null
) : PagingSource<Int, LifeItem>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, LifeItem> {
        val page = params.key ?: 0
        val pageSize = params.loadSize
        return try {
            val items = if (templateId != null) {
                dao.getItemsByTemplatePaged(templateId, page * pageSize, pageSize)
            } else {
                dao.getAllItemsPaged(page * pageSize, pageSize)
            }
            LoadResult.Page(data = items, prevKey = if (page > 0) page - 1 else null, nextKey = if (items.size == pageSize) page + 1 else null)
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, LifeItem>): Int? = state.anchorPosition?.let { pos ->
        state.closestPageToPosition(pos)?.prevKey?.plus(1) ?: state.closestPageToPosition(pos)?.nextKey?.minus(1)
    }
}
