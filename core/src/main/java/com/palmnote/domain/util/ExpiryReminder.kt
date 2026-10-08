package com.palmnote.domain.util

import com.palmnote.domain.model.ExpiryReminderKind

/**
 * 该不该为「距到期 [daysLeft] 天」的物品发提醒，以及发哪一种（[ExpiryReminderKind]）。
 *
 * 窗口是**以到期日为中心的对称区间**：提前 [advanceDays] 天起，到过期后 [advanceDays] 天止。
 * 过期那半边是有意加的——对食品/药品来说「它已经过期了」比「还有 3 天」更该知道。
 * 敢推过期的前提是通知用**每件物品固定的 id**（同一条就地更新，不会越堆越多）。
 *
 * @param advanceDays 提前/延后几天；≤0 时只提醒到期当天。
 */
fun expiryReminderKind(daysLeft: Long, advanceDays: Int): ExpiryReminderKind? = when {
    daysLeft > advanceDays -> null
    daysLeft == 0L -> ExpiryReminderKind.TODAY
    daysLeft > 0 -> ExpiryReminderKind.SOON
    daysLeft >= -advanceDays.toLong() -> ExpiryReminderKind.EXPIRED
    else -> null
}
