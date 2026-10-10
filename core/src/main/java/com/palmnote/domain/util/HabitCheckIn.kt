package com.palmnote.domain.util

import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.entity.LifeItem
import java.time.LocalDate
import java.time.ZoneId

/**
 * 打卡开关的**唯一实现**：桌面组件、首页「今日打卡」卡、生活页详情都走这里，
 * 三处口径不再各写一份。
 *
 * 「打卡」不是某个字段，而是「今天这条模板有没有一条非 ARCHIVED 的行」：
 * - 今天已有 → 全部归档（**软撤销**，当天填过的字段数据保留，再点一次原样恢复）
 * - 今天没有 → 有归档行就复活（不重复插行），否则新插一条
 */
object HabitCheckIn {

    /**
     * @param title 新建行时的标题（内置模板要走本地化名，调用方从 Context 取）
     * @return 切换后是否处于「已打卡」态
     */
    suspend fun toggle(
        dao: LifeItemDao,
        templateId: Long,
        title: String,
        includeDemo: Boolean,
        demoMeta: String,
        now: Long = System.currentTimeMillis(),
        today: LocalDate = LocalDate.now()
    ): Boolean {
        val rows = dao.getDayItemsOfTemplate(templateId, today.toString(), includeDemo, demoMeta)
            .mapNotNull { dao.getItemById(it.itemId) }

        val active = rows.filter { it.status != "ARCHIVED" }
        if (active.isNotEmpty()) {
            active.forEach { dao.updateStatus(it.id, "ARCHIVED") }
            return false
        }

        val archived = rows.filter { it.status == "ARCHIVED" }
        if (archived.isNotEmpty()) {
            archived.forEach { dao.updateStatus(it.id, "ACTIVE") }
            return true
        }

        dao.insertItem(
            LifeItem(
                templateId = templateId,
                title = title,
                fieldsData = "{}",
                status = "ACTIVE",
                createdAt = now,
                updatedAt = now,
                dueDate = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                meta = if (includeDemo) demoMeta else null
            )
        )
        return true
    }
}
