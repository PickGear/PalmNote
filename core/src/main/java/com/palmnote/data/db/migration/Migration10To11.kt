package com.palmnote.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v10 → v11：新增 `life_items.isSeedSample`，把 `meta` 肩上混着的两种语义拆开。
 *
 * ## 为什么必须拆
 *
 * `meta` 此前同时承担两件事：
 * 1. **可见性口径**——「属于演示数据集」，决定演示模式下这条记录露不露面；
 * 2. **可丢弃口径**——决定关闭演示模式时删不删、导出时带不带。
 *
 * 于是演示模式下**用户自己新建的记录**（快捷添加、详情页打卡）也被打上同一个标记
 * （见 `LifeCalendarViewModel.quickAdd`、`LifeDetailViewModel`），关掉演示模式时
 * 与示例数据一起被物理删除，导出时也被排除。同一件用户意图，经表单创建能活下来、
 * 经快捷添加创建却会被删——这是纯粹的数据丢失。
 *
 * 拆分后：`isSeedSample = 1` 只由 [com.palmnote.data.LifeDemoSeeder] 播种时写入，
 * 是「可丢弃」的唯一判据；`meta` 退回纯展示口径。
 *
 * ## 回填口径
 *
 * 存量 `meta = {"demo":true}` 的行一律视为示例行 —— 与迁移前的删除行为完全一致，
 * 不改变任何已有用户的既有表现（迁移不该顺手改变数据归属）。此后新写入的用户记录
 * 一律为 0，不会再被清掉。
 *
 * 注意：这里必须写**历史字面量** `{"demo":true}`，不能引用
 * `com.palmnote.data.db.dao.LIFE_DEMO_META`。迁移一旦发布即冻结：常量日后改了含义，
 * 也不该反向改写这条回填的语义。
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `life_items` ADD COLUMN `isSeedSample` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("""UPDATE life_items SET isSeedSample = 1 WHERE meta = '{"demo":true}'""")
    }
}
