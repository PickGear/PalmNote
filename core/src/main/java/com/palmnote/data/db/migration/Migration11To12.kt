package com.palmnote.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v11 → v12：新增 `life_templates.kind`，把**语义身份**从 `icon` 里拆出来。
 *
 * ## 为什么必须拆
 *
 * `icon` 此前同时承担两件事：
 * 1. **显示**——模板列表/详情页画哪个图标，用户在模板编辑器里可以随便改；
 * 2. **行为**——`icon == "calendar_month"` 决定连击算不算、`"checklist"` 决定待办指标、
 *    `"timer"` 决定专注时长、`"cake"` 决定生日 hero …… 全仓 20+ 处这样判断。
 *
 * 于是用户把打卡模板的图标换成 `book`，这个模板立刻**连击不再计算、详情页换 hero、
 * 统计不再计入完成率**。同一件事在提醒功能上已经发生并被修过
 * （见 `LifeTemplateEditScreen.ReminderCard` 注释「改图标……不会像旧图标匹配那样静默弄丢提醒」），
 * 本迁移是把同一个修法推广到其余判据。
 *
 * ## 回填口径
 *
 * 按 `icon` 逐字推导（与 `LifeTemplateKind.kindFromIcon()` 的 Kotlin 版一一对应）。
 * 这是**唯一**能保持存量用户既有行为的回填方式 —— 迁移不该顺手改变任何模板的语义。
 *
 * 注意这里必须写**历史字面量**，不能引用 `getKind()`：迁移一旦发布即冻结，
 * 日后 `kindFromIcon` 改了含义，也不该反向改写这条回填。
 * （同 `MIGRATION_10_11` 里 `meta = '{"demo":true}'` 的处理。）
 *
 * 未匹配到的图标**不回填**（保持 NULL）—— 那正是「自建模板 / 通用模板」，
 * 由 `getKind()` 在运行期兜底为 GENERIC。
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 刻意**不写** `DEFAULT NULL`：SQLite 会把显式默认值原样记进 `dflt_value`（字符串 "NULL"），
        // 而实体上没有 `@ColumnInfo(defaultValue = ...)`，Room 期望的是 SQL NULL。
        // 两者是否相等取决于 Room 版本对 defaultValue 的比较策略 —— 不能赌。
        // 不写默认值时 `dflt_value` 就是 NULL，与期望**逐字节**一致，且语义相同（可空列隐式为 NULL）。
        db.execSQL("ALTER TABLE `life_templates` ADD COLUMN `kind` TEXT")

        // 与 LifeTemplateKind.kindFromIcon() 逐条对应
        db.execSQL("UPDATE life_templates SET kind = 'HABIT' WHERE icon = 'calendar_month'")
        db.execSQL("UPDATE life_templates SET kind = 'FOCUS' WHERE icon = 'timer'")
        db.execSQL("UPDATE life_templates SET kind = 'TODO' WHERE icon = 'checklist'")
        db.execSQL("UPDATE life_templates SET kind = 'COUNTDOWN' WHERE icon = 'timer_off'")
        db.execSQL("UPDATE life_templates SET kind = 'BIRTHDAY' WHERE icon = 'cake'")
        db.execSQL("UPDATE life_templates SET kind = 'ANNIVERSARY' WHERE icon IN ('celebration', 'favorite')")
        db.execSQL("UPDATE life_templates SET kind = 'JOURNAL' WHERE icon = 'book'")
        db.execSQL("UPDATE life_templates SET kind = 'MOOD' WHERE icon = 'mood'")
        db.execSQL("UPDATE life_templates SET kind = 'STUDY' WHERE icon = 'school'")
        db.execSQL("UPDATE life_templates SET kind = 'TRAVEL' WHERE icon = 'flight'")
    }
}
