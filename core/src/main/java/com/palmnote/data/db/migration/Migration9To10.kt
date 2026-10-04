package com.palmnote.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v9 → v10：新增模板提醒配置列 `reminderConfig`（总纲 §提醒显式化）。
 *
 * - 列可空，不加 NOT NULL，老数据无需默认值；
 * - 回填：提醒原本按**图标**识别内置模板（trending_up/timer_off/cake/celebration/subscriptions），
 *   按同一映射把存量内置模板写成显式配置（[com.palmnote.domain.model.ReminderSpec] 的 JSON），
 *   此后用户改图标不再静默弄丢提醒；
 * - 订阅类没有日期字段键，dateKey 留空走 Worker 的 kind 默认键。
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `life_templates` ADD COLUMN `reminderConfig` TEXT")
        db.execSQL(
            """UPDATE life_templates SET reminderConfig = '{"kind":"MILESTONE","dateKey":"start_date","enabled":true}' """ +
                "WHERE icon = 'trending_up' AND reminderConfig IS NULL"
        )
        db.execSQL(
            """UPDATE life_templates SET reminderConfig = '{"kind":"COUNTDOWN","dateKey":"targetDate","enabled":true}' """ +
                "WHERE icon = 'timer_off' AND reminderConfig IS NULL"
        )
        db.execSQL(
            """UPDATE life_templates SET reminderConfig = '{"kind":"BIRTHDAY","dateKey":"date","enabled":true}' """ +
                "WHERE icon = 'cake' AND reminderConfig IS NULL"
        )
        db.execSQL(
            """UPDATE life_templates SET reminderConfig = '{"kind":"ANNIVERSARY","dateKey":"date","enabled":true}' """ +
                "WHERE icon = 'celebration' AND reminderConfig IS NULL"
        )
        db.execSQL(
            """UPDATE life_templates SET reminderConfig = '{"kind":"SUBSCRIPTION","enabled":true}' """ +
                "WHERE icon = 'subscriptions' AND reminderConfig IS NULL"
        )
    }
}
