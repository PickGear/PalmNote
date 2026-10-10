package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import androidx.annotation.RequiresApi

/**
 * 按候选尺寸各构建一份 RemoteViews，交给启动器按当前格子自己挑。
 *
 * 之前每个组件都在绑定前读一次 `OPTION_APPWIDGET_MIN_WIDTH`，读到什么尺寸就按什么渲染，
 * 于是 resize 回调与数据刷新并发时，后发布的那份可能带着旧尺寸（同一类问题此前在主题色缓存上出现过）。
 * 改成一次给出多个尺寸的 RemoteViews 后，绑定结果不再依赖「绑定时那一瞬的尺寸」。
 *
 * Android 12 起桌面直接给出可用尺寸列表，用 `Map<SizeF, RemoteViews>` 构造；
 * 旧版本退回「横屏一份、竖屏一份」的两参构造器。
 */
internal fun sizedRemoteViews(
    options: Bundle,
    fallbackWidthDp: Int,
    fallbackHeightDp: Int,
    bind: (widthDp: Int, heightDp: Int) -> RemoteViews
): RemoteViews {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val exact = options.exactSizes()
        if (exact.isNotEmpty()) {
            return RemoteViews(exact.associateWith { bind(it.width.toInt(), it.height.toInt()) })
        }
    }
    // 旧版只有 min/max：横屏取「最宽 × 最矮」、竖屏取「最窄 × 最高」（构造器参数横屏在前）
    val minWidth = options.positiveOr(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, fallbackWidthDp)
    val minHeight = options.positiveOr(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, fallbackHeightDp)
    val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWidth).coerceAtLeast(minWidth)
    val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight).coerceAtLeast(minHeight)
    return RemoteViews(bind(maxWidth, minHeight), bind(minWidth, maxHeight))
}

/** 桌面给出的精确尺寸（Android 12+）；旧版本或桌面没给时为空。 */
@RequiresApi(Build.VERSION_CODES.S)
@Suppress("DEPRECATION")
private fun Bundle.exactSizes(): List<SizeF> =
    getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
        .orEmpty()
        .filter { it.width.isFinite() && it.height.isFinite() && it.width > 0f && it.height > 0f }
        .distinct()

/** 取不到或拿到非正值（部分桌面会给 0）时用组件声明的尺寸兜底。 */
private fun Bundle.positiveOr(key: String, fallback: Int): Int =
    getInt(key, fallback).takeIf { it > 0 } ?: fallback
