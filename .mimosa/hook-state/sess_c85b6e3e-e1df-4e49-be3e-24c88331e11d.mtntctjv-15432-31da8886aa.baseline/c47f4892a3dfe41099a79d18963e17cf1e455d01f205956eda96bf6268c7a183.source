package com.palmnote.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ========== bills 表索引 ==========
        // 注意：索引名必须与 @Entity indices 的自动生成名一致，否则 Room 迁移后校验失败

        // 核心: yearMonth + type + isDeleted (覆盖9个查询)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_bills_yearMonth_type_isDeleted " +
            "ON bills(yearMonth, type, isDeleted)"
        )

        // 核心: accountBookId + yearMonth + isDeleted (覆盖7个查询)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_bills_accountBookId_yearMonth_isDeleted " +
            "ON bills(accountBookId, yearMonth, isDeleted)"
        )

        // 核心: type + isDeleted (覆盖4个查询)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_bills_type_isDeleted " +
            "ON bills(type, isDeleted)"
        )

        // 辅助: date + isDeleted (覆盖日期范围查询)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_bills_date_isDeleted " +
            "ON bills(date, isDeleted)"
        )

        // 辅助: isReimbursable + isReimbursed + isDeleted (报销查询)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_bills_isReimbursable_isReimbursed_isDeleted " +
            "ON bills(isReimbursable, isReimbursed, isDeleted)"
        )

        // 辅助: recurringId + isDeleted (周期性账单查询)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_bills_recurringId_isDeleted " +
            "ON bills(recurringId, isDeleted)"
        )

        // 辅助: category + isDeleted (分类查询)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_bills_category_isDeleted " +
            "ON bills(category, isDeleted)"
        )

        // ========== assets 表索引 ==========

        // 核心: status + isDeleted (覆盖8个查询)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_assets_status_isDeleted " +
            "ON assets(status, isDeleted)"
        )

        // 核心: category + isDeleted (覆盖分类分布查询)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_assets_category_isDeleted " +
            "ON assets(category, isDeleted)"
        )

        // 辅助: warrantyExpireDate + isDeleted (保修查询)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_assets_warrantyExpireDate_isDeleted " +
            "ON assets(warrantyExpireDate, isDeleted)"
        )

        // 辅助: nextMaintenanceDate + isDeleted (维护提醒)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_assets_nextMaintenanceDate_isDeleted " +
            "ON assets(nextMaintenanceDate, isDeleted)"
        )

        // 辅助: insuranceExpireDate + isDeleted (保险提醒)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_assets_insuranceExpireDate_isDeleted " +
            "ON assets(insuranceExpireDate, isDeleted)"
        )

        // 辅助: isFavorite + isDeleted (收藏查询)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_assets_isFavorite_isDeleted " +
            "ON assets(isFavorite, isDeleted)"
        )
    }
}
