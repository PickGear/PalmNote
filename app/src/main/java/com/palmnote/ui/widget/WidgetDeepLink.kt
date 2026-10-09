package com.palmnote.ui.widget

/**
 * 小组件深链的唯一登记处：目标页与 requestCode 号段。
 *
 * requestCode 必须全局唯一 —— Intent extras 不参与 filterEquals 匹配，
 * 复用同一 requestCode 会 FLAG_UPDATE_CURRENT 互相覆盖，点击所有组件跳到同一页面。
 * 各组件占用固定号段，新增热区先在这里登记再使用。
 */
object WidgetDeepLink {

    // 目标页（MainActivity.handleWidgetIntent 的白名单见其 when 分支）
    const val TAB_DASHBOARD = "dashboard"
    const val TAB_BILL = "bill"
    const val TAB_ADD_BILL = "add_bill"
    const val TAB_ASSET = "asset"
    const val TAB_LIFE = "life"
    const val TAB_VAULT = "vault"
    const val TAB_REPORT = "report"
    const val TAB_BUDGET = "budget"

    const val SEG_BILL = 100_000
    const val SEG_BILL_ADD = 1_000_000
    const val SEG_BILL_REPORT = 1_100_000
    const val SEG_TODO = 200_000
    const val SEG_TODO_ADD = 2_000_000
    const val SEG_COUNTER = 300_000
    const val SEG_ASSET = 400_000
    const val SEG_VAULT = 500_000
    const val SEG_DASHBOARD = 600_000
    const val SEG_DASHBOARD_BUDGET = 610_000
    const val SEG_DASHBOARD_GOAL = 620_000
    const val SEG_DASHBOARD_TODO = 630_000
    const val SEG_DASHBOARD_ANNIVERSARY = 640_000
    const val SEG_HABIT = 800_000
    const val SEG_SHORTCUT_ROOT = 650_000
    const val SEG_SHORTCUT_ADD = 11_000_000
    const val SEG_SHORTCUT_BILL = 12_000_000
    const val SEG_SHORTCUT_TODO = 13_000_000
    const val SEG_SHORTCUT_VAULT = 14_000_000
    const val SEG_TODO_TOGGLE = 21_000_000
    const val SEG_EVENT_DETAIL = 400_000_000
    const val SEG_LIFE_LIST = 500_000_000
}
