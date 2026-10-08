package com.palmnote.ui.asset

import com.palmnote.ui.theme.AccentOrange
import com.palmnote.ui.theme.ModuleItem
import com.palmnote.ui.theme.StatusLost
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 到期徽标紧迫度配色的分档口径：已过期=红，7 天内=橙，其余=普通色。
 * 纯函数，直接量边界（0 / 7 / 8 天）。
 */
class AssetExpiryColorTest {

    private fun daysFromToday(days: Long): Long = LocalDate.now()
        .plusDays(days)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    @Test
    fun `已过期用红色`() {
        assertEquals(StatusLost, expiryAccentColor(daysFromToday(-1)))
        assertEquals(StatusLost, expiryAccentColor(daysFromToday(-365)))
    }

    @Test
    fun `到期当天到第 7 天算临期用橙色`() {
        assertEquals(AccentOrange, expiryAccentColor(daysFromToday(0)))
        assertEquals(AccentOrange, expiryAccentColor(daysFromToday(7)))
    }

    @Test
    fun `第 8 天起回到普通色`() {
        assertEquals(ModuleItem, expiryAccentColor(daysFromToday(8)))
        assertEquals(ModuleItem, expiryAccentColor(daysFromToday(3650)))
    }
}
