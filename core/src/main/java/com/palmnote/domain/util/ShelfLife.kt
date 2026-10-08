package com.palmnote.domain.util

import com.palmnote.domain.model.ShelfLifeUnit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 「生产日期 + 保质期时长」→ 到期日。纯函数，UI 侧实时预览与保存时算的是同一套。
 *
 * 按自然日/月/年推进而不是固定 30/365 天：包装上写的「保质期 12 个月」就是加 12 个月，
 * 月末自动收敛（1/31 + 1 个月 = 2/28），与人的读法一致。
 *
 * 时长不是正数时返回 null —— 「没填」不该被当成「当天到期」。
 */
fun shelfLifeExpiryFrom(
    producedDate: Long,
    amount: Int,
    unit: ShelfLifeUnit,
    zone: ZoneId = ZoneId.systemDefault()
): Long? {
    if (amount <= 0) return null
    val start: LocalDate = Instant.ofEpochMilli(producedDate).atZone(zone).toLocalDate()
    val expiry = when (unit) {
        ShelfLifeUnit.DAY -> start.plusDays(amount.toLong())
        ShelfLifeUnit.MONTH -> start.plusMonths(amount.toLong())
        ShelfLifeUnit.YEAR -> start.plusYears(amount.toLong())
    }
    return expiry.atStartOfDay(zone).toInstant().toEpochMilli()
}

/**
 * 表单那种「三项可能只填了一半」的场景：任一项缺失就返回 null。
 *
 * 表单允许用户先填生产日期再填时长，中途会经过各种半截状态；用这个函数统一收口，
 * 存库时就能拿「算不算得出到期日」当作「这组输入完不完整」的判据。
 */
fun shelfLifeExpiryOrNull(
    producedDate: Long?,
    amount: Int?,
    unit: ShelfLifeUnit?,
    zone: ZoneId = ZoneId.systemDefault()
): Long? {
    if (producedDate == null || amount == null || unit == null) return null
    return shelfLifeExpiryFrom(producedDate, amount, unit, zone)
}
