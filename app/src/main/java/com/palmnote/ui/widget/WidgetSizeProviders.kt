package com.palmnote.ui.widget

/**
 * 30 个「尺寸形态」薄子类（v37：每族四档 4×2 / 2×2 / 4×1 / 4×4，记账三套旧样式已收敛）。
 * 每个变体在桌面选择器里是**独立一行**，所以各注册一个 receiver 类 + 一份 info
 * （targetCell 声明目标格数），用户选到的形态与落桌后的渲染档位一一对应。
 *
 * 类体为空是刻意的：渲染档位由基类 `onUpdateAsync` **按实际宽高切档**
 * （minWidth/minHeight 由 info 的 targetCell* 换算，落桌后自然对上档），
 * 子类不写任何切档代码 —— 一来零重复，二来用户长按缩放后仍能正确换档。
 * 刷新链路（WidgetUpdateHelper.sendFamily 按 isAssignableFrom 过滤 entries）与
 * 目录（WidgetPin）按 [Class] 派生，见各自注释。
 */

// ── 概览 ──

/** 概览 2×2（v37 档）。 */
class DashboardSquare : DashboardWidgetProvider()

/** 概览 4×1（v37 档）。 */
class DashboardBar : DashboardWidgetProvider()

/** 概览 4×4（v37 档）。 */
class DashboardTall : DashboardWidgetProvider()

// ── 本月记账（单族四档，v37 收敛） ──

/** 记账 2×2（v37 档）。 */
class BillSquare : BillWidgetProvider()

/** 记账 4×1（v37 档）。 */
class BillBar : BillWidgetProvider()

/** 记账 4×4（v37 档）。 */
class BillTall : BillWidgetProvider()

// ── 待办 ──

/** 待办 2×2（v37 档）。 */
class TodoSquare : TodoWidgetProvider()

/** 待办 4×1（v37 档）。 */
class TodoWide : TodoWidgetProvider()

/** 待办 4×4（v37 档）。 */
class TodoTall : TodoWidgetProvider()

// ── 习惯 ──

/** 习惯 2×2（v37 档）。 */
class HabitSquare : HabitWidgetProvider()

/** 习惯 4×1（v37 档）。 */
class HabitWide : HabitWidgetProvider()

/** 习惯 4×4（v37 档）。 */
class HabitTall : HabitWidgetProvider()

// ── 计数 ──

/** 计数 2×2（v37 档）。 */
class CounterSquare : LifeCounterWidgetProvider()

/** 计数 4×1（v37 档）。 */
class CounterBar : LifeCounterWidgetProvider()

/** 计数 4×4（v37 档）。 */
class CounterTall : LifeCounterWidgetProvider()

// ── 接下来（基类即 2×2 档） ──

/** 接下来 4×2（v37 档）。 */
class NextUpWide : NextUpWidgetProvider()

/** 接下来 4×1（v37 档）。 */
class NextUpBar : NextUpWidgetProvider()

/** 接下来 4×4（v37 档）。 */
class NextUpTall : NextUpWidgetProvider()

// ── 净资产（基类即 2×2 档） ──

/** 净资产 4×2（v37 档）。 */
class NetWorthWide : NetWorthWidgetProvider()

/** 净资产 4×1（v37 档）。 */
class NetWorthBar : NetWorthWidgetProvider()

/** 净资产 4×4（v37 档）。 */
class NetWorthTall : NetWorthWidgetProvider()

// ── 订阅 ──

/** 订阅 2×2（v37 档）。 */
class SubscriptionSquare : SubscriptionWidgetProvider()

/** 订阅 4×1（v37 档）。 */
class SubscriptionBar : SubscriptionWidgetProvider()

/** 订阅 4×4（v37 档）。 */
class SubscriptionTall : SubscriptionWidgetProvider()

// ── 物品 ──

/** 物品 2×2（v37 档）。 */
class AssetSquare : AssetWidgetProvider()

/** 物品 4×1（v37 档）。 */
class AssetBar : AssetWidgetProvider()

/** 物品 4×4（v37 档）。 */
class AssetTall : AssetWidgetProvider()

// ── 快捷入口（基类即 2×2 档） ──

/** 快捷入口 4×2 八格（v37 档）。 */
class ShortcutsFull : ShortcutsWidgetProvider()

/** 快捷入口 4×1 四格横排（v37 档）。 */
class ShortcutsWide : ShortcutsWidgetProvider()

/** 快捷入口 4×4 九宫格（v37 档）。 */
class ShortcutsTall : ShortcutsWidgetProvider()
