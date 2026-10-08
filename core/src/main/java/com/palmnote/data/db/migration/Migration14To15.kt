package com.palmnote.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v14 → v15：给物品与物品回收站加保质期相关的四列。
 *
 * - `shelfLifeExpireDate`：到期日，可空 INTEGER（epoch 毫秒）。展示与倒计时一律读它。
 * - `shelfLifeProducedDate` / `shelfLifeDurationValue` / `shelfLifeDurationUnit`：
 *   「生产日期 + 保质期时长」这种录法留下的原始输入，只为了让重新编辑时还原成
 *   「12 个月」而不是一个绝对日期。直接填到期日的物品这三列为 NULL。
 *
 * 四列都可空、都不带默认值，存量行一律 NULL（未填写）；回收站同步加列，
 * 删除/恢复之间带住保质期。索引与实体声明一致。
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `assets` ADD COLUMN `shelfLifeExpireDate` INTEGER")
        db.execSQL("ALTER TABLE `assets` ADD COLUMN `shelfLifeProducedDate` INTEGER")
        db.execSQL("ALTER TABLE `assets` ADD COLUMN `shelfLifeDurationValue` INTEGER")
        db.execSQL("ALTER TABLE `assets` ADD COLUMN `shelfLifeDurationUnit` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_shelfLifeExpireDate` ON `assets` (`shelfLifeExpireDate`)")
        db.execSQL("ALTER TABLE `assets_recycle_bin` ADD COLUMN `shelfLifeExpireDate` INTEGER")
        db.execSQL("ALTER TABLE `assets_recycle_bin` ADD COLUMN `shelfLifeProducedDate` INTEGER")
        db.execSQL("ALTER TABLE `assets_recycle_bin` ADD COLUMN `shelfLifeDurationValue` INTEGER")
        db.execSQL("ALTER TABLE `assets_recycle_bin` ADD COLUMN `shelfLifeDurationUnit` TEXT")
    }
}
