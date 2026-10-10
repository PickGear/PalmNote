package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Intent

/**
 * 小组件配置页与桌面之间的约定：桌面发起配置时带 `EXTRA_APPWIDGET_ID`，
 * 我们必须在**返回前**用同一个 id 回传 RESULT_OK，否则系统会当作用户放弃、不落桌。
 * 逻辑单独拎出来是为了能脱离 Hilt 直接单测（Activity 本体要跑 Hilt）。
 */
internal object WidgetConfigContract {

    const val INVALID_ID: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    /** 取桌面传进来的组件实例 id；缺失/坏值一律归一成 INVALID_ID。 */
    fun widgetIdOf(intent: Intent?): Int =
        intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, INVALID_ID) ?: INVALID_ID

    /** 只有拿到真实 id 才允许配置；否则连保存的目标都没有，直接放弃。 */
    fun canConfigure(widgetId: Int): Boolean = widgetId != INVALID_ID

    /** 确认结果：必须原样带回 id，桌面据此把组件放到桌面。 */
    fun okResult(widgetId: Int): Intent =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}

/** 配置页的产出：账本选择 + 透明度覆盖（null = 跟随全局默认）。 */
internal data class WidgetConfigResult(
    val bookId: Long?,
    val opacityOverride: Float?
)
