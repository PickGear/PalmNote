package com.palmnote.data.db.entity

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.palmnote.R
import androidx.compose.runtime.Immutable
import com.palmnote.ui.theme.AppIcon

@Entity(tableName = "account_books")
@Immutable
data class AccountBook(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "MenuBook")
    val icon: AppIcon = AppIcon.MenuBook,
    val color: String = "#4285F4",
    val description: String = "",
    val bookType: String = "CUSTOM", // DAILY, TRAVEL, WORK, FAMILY, STUDY, INVESTMENT, ALL, CUSTOM
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
    val isAllBooks: Boolean = false,
    val isHidden: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val ALL_BOOKS_ID = -1L

        val BOOK_TEMPLATES = listOf(
            BookTemplate("DAILY", "日常", AppIcon.MenuBook, "#2D4A3E", "记录日常生活开支"),
            BookTemplate("TRAVEL", "旅行", AppIcon.Flight, "#34A853", "旅行途中的所有消费"),
            BookTemplate("WORK", "工作", AppIcon.Work, "#FF8C42", "工作相关收支"),
            BookTemplate("FAMILY", "家庭", AppIcon.Home, "#EA4335", "家庭共同开支管理"),
            BookTemplate("STUDY", "学习", AppIcon.School, "#9C27B0", "学习培训相关费用"),
            BookTemplate("INVESTMENT", "投资", AppIcon.Savings, "#FBBC04", "投资理财记录"),
        )
    }
}

fun AccountBook.getDisplayName(context: Context): String {
    return when (bookType) {
        "ALL" -> context.getString(R.string.account_book_all_name)
        "DAILY" -> context.getString(R.string.account_book_daily_name)
        "TRAVEL" -> context.getString(R.string.account_book_travel_name)
        "WORK" -> context.getString(R.string.account_book_work_name)
        "FAMILY" -> context.getString(R.string.account_book_family_name)
        "STUDY" -> context.getString(R.string.account_book_study_name)
        "INVESTMENT" -> context.getString(R.string.account_book_investment_name)
        else -> name
    }
}

fun AccountBook.getDisplayDescription(context: Context): String {
    return when (bookType) {
        "ALL" -> context.getString(R.string.account_book_all_desc)
        "DAILY" -> context.getString(R.string.account_book_daily_desc)
        "TRAVEL" -> context.getString(R.string.account_book_travel_desc)
        "WORK" -> context.getString(R.string.account_book_work_desc)
        "FAMILY" -> context.getString(R.string.account_book_family_desc)
        "STUDY" -> context.getString(R.string.account_book_study_desc)
        "INVESTMENT" -> context.getString(R.string.account_book_investment_desc)
        else -> description
    }
}

data class BookTemplate(
    val type: String,
    val name: String,
    val icon: AppIcon,
    val color: String,
    val description: String
)

fun BookTemplate.getDisplayName(context: Context): String {
    return when (type) {
        "DAILY" -> context.getString(R.string.account_book_daily_name)
        "TRAVEL" -> context.getString(R.string.account_book_travel_name)
        "WORK" -> context.getString(R.string.account_book_work_name)
        "FAMILY" -> context.getString(R.string.account_book_family_name)
        "STUDY" -> context.getString(R.string.account_book_study_name)
        "INVESTMENT" -> context.getString(R.string.account_book_investment_name)
        else -> name
    }
}

fun BookTemplate.getDisplayDescription(context: Context): String {
    return when (type) {
        "DAILY" -> context.getString(R.string.account_book_daily_desc)
        "TRAVEL" -> context.getString(R.string.account_book_travel_desc)
        "WORK" -> context.getString(R.string.account_book_work_desc)
        "FAMILY" -> context.getString(R.string.account_book_family_desc)
        "STUDY" -> context.getString(R.string.account_book_study_desc)
        "INVESTMENT" -> context.getString(R.string.account_book_investment_desc)
        else -> description
    }
}
