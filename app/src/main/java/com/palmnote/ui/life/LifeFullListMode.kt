package com.palmnote.ui.life

/**
 * 首页三张清单的全量视图模式。
 *
 * 只承载「从哪张卡进来」，不是新的数据口径：三个模式的过滤逻辑仍由
 * [LifeCalendarViewModel] 的同一份派生数据提供，避免完整页与首页出现两套定义。
 */
enum class LifeFullListMode(val routeValue: String) {
    OVERDUE("OVERDUE"),
    AGENDA("AGENDA"),
    UNSCHEDULED("UNSCHEDULED");

    companion object {
        fun fromRouteValue(value: String): LifeFullListMode? =
            entries.firstOrNull { it.routeValue == value }
    }
}
