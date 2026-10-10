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
    val color: String = DEFAULT_COLOR,
    val description: String = "",
    val bookType: String = "CUSTOM", // DAILY, TRAVEL, WORK, FAMILY, STUDY, INVESTMENT, ALL, CUSTOM
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
    val isAllBooks: Boolean = false,
    val isHidden: Boolean = false,
    /**
     * 演示数据标记（v14）：示例账本 / 示例钱包 / 示例账单 / 示例物品。
     * 演示模式关闭时按此列整批物理删除；CSV 导出排除（示例不进「我的数据」）。
     */
    @ColumnInfo(defaultValue = "0")
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),

    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val ALL_BOOKS_ID = -1L

        /**
         * 账本默认色：`PRESET_COLOR_HEXES` 的首色，也是 `ColorPicker` 认不出当前色时的兜底值。
         * 落在这个色板内是有意的 —— 编辑页的颜色选择器才能在「默认色」上打出对勾。
         */
        const val DEFAULT_COLOR = "#4285F4"

        /** 伪账本「全账本」的识别色：汇总所有账本，用同一支主蓝，用户可自行改。 */
        const val ALL_BOOKS_COLOR = DEFAULT_COLOR

        /**
         * 新建账本时可选的模板。色值一律取 `PRESET_COLOR_HEXES` 内的色，
         * 保证编辑页的颜色选择器能对当前色打出对勾；改色时别挑色板外的值。
         * `DAILY` 一条同时是「内置日常账本」的出厂色（见 [presetBookColor]）。
         */
        val BOOK_TEMPLATES = listOf(
            BookTemplate("DAILY", "日常", AppIcon.MenuBook, "#34A853", "记录日常生活开支"),
            BookTemplate("TRAVEL", "旅行", AppIcon.Flight, "#29B6F6", "旅行途中的所有消费"),
            BookTemplate("WORK", "工作", AppIcon.Work, "#FF8C42", "工作相关收支"),
            BookTemplate("FAMILY", "家庭", AppIcon.Home, "#F48FB1", "家庭共同开支管理"),
            BookTemplate("STUDY", "学习", AppIcon.School, "#CE93D8", "学习培训相关费用"),
            BookTemplate("INVESTMENT", "投资", AppIcon.Savings, "#FFD54F", "投资理财记录"),
        )
    }
}

/**
 * 预设账本的当前默认色。**单一真源**：`ALL` 取 [AccountBook.ALL_BOOKS_COLOR]，
 * 其余一律取 [AccountBook.BOOK_TEMPLATES] 里同名模板的色 —— 所以「内置的日常账本」与
 * 「新建时选『日常』模板」必然同色，不会再出现两处各写一个值然后慢慢漂开的情况
 * （2026-10-10 修：此前内置日常写 `#2D4A3E`、模板写 `#4DB6AC`，同名的两个「日常」颜色不一样）。
 *
 * 未知类型返回 null。
 */
fun presetBookColor(bookType: String): String? = when (bookType) {
    "ALL" -> AccountBook.ALL_BOOKS_COLOR
    else -> AccountBook.BOOK_TEMPLATES.find { it.type == bookType }?.color
}

/**
 * 历史出厂默认色（bookType → 旧值）。**只用于一次性刷新**，不要拿它当色板用。
 *
 * 这两个值是早期版本写进 DB 的：全账本 `#607D8B`（蓝灰）、日常 `#2D4A3E`（近黑的深绿），
 * 在浅色底上既灰又暗，且都不在 `PRESET_COLOR_HEXES` 里（编辑页选不中也打不出对勾）。
 * 启动时若某预设账本色**逐字等于**这里登记的值，说明用户从未改过它，刷新为 [presetBookColor]；
 * 用户一旦在编辑页换过色，值就对不上，从此不再触碰。
 *
 * 新值 `#4285F4` / `#34A853` 也是板内色，白图标对比度 3.56:1 / 3.06:1，都在图标 3:1 可读线之上
 * （原值 4.37:1 / 9.72:1 更「清楚」，但代价是发灰发暗 —— 用户要的是不灰不暗，同时图标还得看得见）。
 */
val LEGACY_PRESET_BOOK_COLORS = mapOf(
    "ALL" to "#607D8B",
    "DAILY" to "#2D4A3E"
)

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
