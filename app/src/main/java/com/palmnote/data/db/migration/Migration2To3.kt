package com.palmnote.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ========== bills 表索引 ==========
        
        // 核心: yearMonth + type + isDeleted (覆盖9个查询)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_bills_month_type_deleted " +
            "ON bills(yearMonth, type, isDeleted)"
        )
        
        // 核心: accountBookId + yearMonth + isDeleted (覆盖7个查询)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_bills_book_month_deleted " +
            "ON bills(accountBookId, yearMonth, isDeleted)"
        )
        
        // 核心: type + isDeleted (覆盖4个查询)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_bills_type_deleted " +
            "ON bills(type, isDeleted)"
        )
        
        // 辅助: date + isDeleted (覆盖日期范围查询)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_bills_date_deleted " +
            "ON bills(date, isDeleted)"
        )
        
        // 辅助: isReimbursable + isReimbursed + isDeleted (报销查询)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_bills_reimburse " +
            "ON bills(isReimbursable, isReimbursed, isDeleted)"
        )
        
        // 辅助: recurringId + isDeleted (周期性账单查询)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_bills_recurring_deleted " +
            "ON bills(recurringId, isDeleted)"
        )
        
        // 辅助: category + isDeleted (分类查询)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_bills_category_deleted " +
            "ON bills(category, isDeleted)"
        )
        
        // ========== assets 表索引 ==========
        
        // 核心: status + isDeleted (覆盖8个查询)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_assets_status_deleted " +
            "ON assets(status, isDeleted)"
        )
        
        // 核心: category + isDeleted (覆盖分类分布查询)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_assets_category_deleted " +
            "ON assets(category, isDeleted)"
        )
        
        // 辅助: warrantyExpireDate + isDeleted (保修查询)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_assets_warranty_deleted " +
            "ON assets(warrantyExpireDate, isDeleted)"
        )
        
        // 辅助: nextMaintenanceDate + isDeleted (维护提醒)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_assets_maintenance_deleted " +
            "ON assets(nextMaintenanceDate, isDeleted)"
        )
        
        // 辅助: insuranceExpireDate + isDeleted (保险提醒)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_assets_insurance_deleted " +
            "ON assets(insuranceExpireDate, isDeleted)"
        )
        
        // 辅助: isFavorite + isDeleted (收藏查询)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_assets_favorite_deleted " +
            "ON assets(isFavorite, isDeleted)"
        )
    }
}
