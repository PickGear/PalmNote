package com.palmnote.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v12 → v13：新增 `life_templates.repeatYearly`，把「每年重复」从**提醒路径**提升为**模板属性**。
 *
 * ## 为什么必须拆
 *
 * 「下一次周年」的算法此前**只存在于提醒 Worker 里**（`toThisYear` / `nextLunarBirthday`，
 * 含农历反算与闰月回退）：提醒会在生日前 N 天响，但**记录本身不会滚** ——
 * 生日过去之后，详情页的「剩余天数」仍按当年那个已过去的日期算，读数是负的，
 * 而标签还写着「剩余天数」。倒数日品类（Days Matter 等）把「重复与否」作为事件的显式属性，
 * 这也正是本品类用户的默认期待。
 *
 * ## 回填口径
 *
 * 存量模板**一律回填 0（不重复）**——迁移不该凭空改变任何已有模板的表现。
 * 生日 / 纪念日的用户若想要滚动，在模板编辑器里打开开关即可（一步、可关）。
 *
 * 注意 `NOT NULL` 的新列必须带 `DEFAULT`（SQLite 的硬要求），
 * 且与实体上的 Kotlin 默认值 `false` 一致；写法与 `MIGRATION_10_11` 的 `isSeedSample` 相同。
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `life_templates` ADD COLUMN `repeatYearly` INTEGER NOT NULL DEFAULT 0"
        )
    }
}
