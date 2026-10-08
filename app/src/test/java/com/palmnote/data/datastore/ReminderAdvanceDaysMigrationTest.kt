package com.palmnote.data.datastore

import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「提前几天提醒」从三个键合并成一个键时，**不能把存量用户设过的值弄丢**。
 *
 * 这是那种「不报错、不崩溃，只是悄悄换了值」的缺陷：用户设的生日提前 14 天，升级后显示 3，
 * 页面看着一切正常。所以两条都钉住：旧键名就是 v1.4.0 发布的那几个（写错了等于没兜底），
 * 以及优先级（新键 > 旧键 > 默认）。
 */
class ReminderAdvanceDaysMigrationTest {

    @Test
    fun `旧键名必须与 v1_4_0 发布的完全一致`() {
        assertEquals("birthday_reminder_advance_days", PreferencesManager.LEGACY_BIRTHDAY_ADVANCE_DAYS.name)
        assertEquals("anniversary_reminder_advance_days", PreferencesManager.LEGACY_ANNIVERSARY_ADVANCE_DAYS.name)
        assertEquals("asset_expiry_reminder_advance_days", PreferencesManager.LEGACY_ASSET_EXPIRY_ADVANCE_DAYS.name)
        assertEquals("reminder_advance_days", PreferencesManager.REMINDER_ADVANCE_DAYS.name)
    }

    @Test
    fun `没设过新键时沿用生日旧值`() {
        val prefs = preferencesOf(PreferencesManager.LEGACY_BIRTHDAY_ADVANCE_DAYS to 14)
        assertEquals(14, resolveReminderAdvanceDays(prefs))
    }

    @Test
    fun `生日没设过时退到纪念日`() {
        val prefs = preferencesOf(PreferencesManager.LEGACY_ANNIVERSARY_ADVANCE_DAYS to 7)
        assertEquals(7, resolveReminderAdvanceDays(prefs))
    }

    @Test
    fun `只有保质期设过时也能读到`() {
        val prefs = preferencesOf(PreferencesManager.LEGACY_ASSET_EXPIRY_ADVANCE_DAYS to 5)
        assertEquals(5, resolveReminderAdvanceDays(prefs))
    }

    @Test
    fun `三个旧键都有值时按生日优先，取值稳定`() {
        val prefs = preferencesOf(
            PreferencesManager.LEGACY_BIRTHDAY_ADVANCE_DAYS to 14,
            PreferencesManager.LEGACY_ANNIVERSARY_ADVANCE_DAYS to 2,
            PreferencesManager.LEGACY_ASSET_EXPIRY_ADVANCE_DAYS to 5
        )
        assertEquals(14, resolveReminderAdvanceDays(prefs))
    }

    @Test
    fun `新键压过所有旧键`() {
        val prefs = preferencesOf(
            PreferencesManager.REMINDER_ADVANCE_DAYS to 30,
            PreferencesManager.LEGACY_BIRTHDAY_ADVANCE_DAYS to 14,
            PreferencesManager.LEGACY_ANNIVERSARY_ADVANCE_DAYS to 7
        )
        assertEquals(30, resolveReminderAdvanceDays(prefs))
    }

    @Test
    fun `全都没设过时用默认值 3`() {
        assertEquals(3, resolveReminderAdvanceDays(preferencesOf()))
        assertEquals(PreferencesManager.DEFAULT_REMINDER_ADVANCE_DAYS, resolveReminderAdvanceDays(preferencesOf()))
    }

    @Test
    fun `0 是有效值，不能被当成没设过`() {
        val prefs = preferencesOf(
            PreferencesManager.LEGACY_BIRTHDAY_ADVANCE_DAYS to 0,
            PreferencesManager.LEGACY_ANNIVERSARY_ADVANCE_DAYS to 7
        )
        assertEquals(0, resolveReminderAdvanceDays(prefs))
    }
}
