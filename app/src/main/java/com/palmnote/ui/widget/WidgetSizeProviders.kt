package com.palmnote.ui.widget

/**
 * 11 个「尺寸形态」薄子类：设计稿 22 张卡里除 11 个主形态外，还有同一组件的
 * 单格数变体（记一笔 1×1、订阅 2×1、待办 2×1…）。每个变体在桌面选择器里是
 * **独立一行**，所以各注册一个 receiver 类 + 一份 info（targetCell 声明目标格数），
 * 用户选到的形态与落桌后的渲染档位一一对应。
 *
 * 类体为空是刻意的：渲染档位由基类 `onUpdateAsync` **按实际宽高切档**
 * （minWidth/minHeight 由 info 的 targetCell* 换算，落桌后自然对上档），
 * 子类不写任何切档代码 —— 一来零重复，二来用户长按缩放后仍能正确换档。
 * 刷新链路（WidgetUpdateHelper）与目录（WidgetPin）按 [Class] 派生，见各自注释。
 *
 * 与设计卡对应：记一笔 1×1(20)、订阅 2×1(22)、待办 2×1(23)、习惯 2×1(24)、
 * 待办 4×1(25)、习惯 4×1(26)、快捷 4×1(27)、计数器 1×2(28)、订阅 1×2(29)、
 * 计数器 1×3(30)、习惯 1×3(31)。
 */

/** 待办 2×1 横条（设计卡 23）。 */
class TodoMini : TodoWidgetProvider()

/** 待办 4×1 横条（设计卡 25）。 */
class TodoWide : TodoWidgetProvider()

/** 习惯 2×1 极简（设计卡 24）。 */
class HabitMini : HabitWidgetProvider()

/** 习惯 4×1 横条（设计卡 26）。 */
class HabitWide : HabitWidgetProvider()

/** 习惯 1×3 竖条（设计卡 31）。 */
class HabitNarrow : HabitWidgetProvider()

/** 计数器 1×2 竖条（设计卡 28）。 */
class CounterNarrow : LifeCounterWidgetProvider()

/** 计数器 1×3 竖条（设计卡 30）。 */
class CounterTall : LifeCounterWidgetProvider()

/** 订阅 2×1 极简（设计卡 22）。 */
class SubscriptionMini : SubscriptionWidgetProvider()

/** 订阅 1×2 竖条（设计卡 29）。 */
class SubscriptionNarrow : SubscriptionWidgetProvider()

/** 记一笔 1×1 纯入口（设计卡 20）。 */
class ShortcutsSmall : ShortcutsWidgetProvider()

/** 快捷入口 4×1 横条（设计卡 27）。 */
class ShortcutsWide : ShortcutsWidgetProvider()
