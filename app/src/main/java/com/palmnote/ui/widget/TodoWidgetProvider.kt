package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.LifeItem
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * 待办 4×3（定稿）：左侧竖进度轴给「今日完成 N/M」与完成度条，右侧是可滚动的色片清单。
 * 行的构造与点击在 [WidgetHelper.todoRowViews] / [TodoWidgetService]，这里只管轴与整体绑定。
 */
class TodoWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun lifeItemDao(): LifeItemDao
        fun lifeTemplateDao(): LifeTemplateDao
        fun preferencesManager(): PreferencesManager
    }

    override suspend fun onUpdateAsync(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val prefs = entryPoint.preferencesManager()
        // 演示感知互斥：开＝只看示例，关＝只看自己的（与其他出口同一口径）
        val board = WidgetData.fetchTodoBoard(
            entryPoint.lifeItemDao(),
            entryPoint.lifeTemplateDao(),
            includeDemo = prefs.lifeDemoMode.first(),
            demoMeta = com.palmnote.data.db.dao.LIFE_DEMO_META
        )

        publish(context, appWidgetManager, appWidgetIds) { appWidgetId, _ ->
            bindViews(context, appWidgetId, board)
        }
    }

    internal fun bindViews(
        context: Context,
        appWidgetId: Int,
        board: WidgetData.TodoBoard
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_todo_unified)
        // 装饰色走色族（不占全局强调色）：徽章与新增片用绿，进度条用蓝（稿子如此）
        val badgeFamily = WidgetData.colorFamily(context, 2)
        val progressFamily = WidgetData.colorFamily(context, 1)
        views.setInt(R.id.widget_todo_badge, "setColorFilter", badgeFamily.hue)
        views.setInt(R.id.widget_todo_add_bg, "setColorFilter", badgeFamily.tint)
        views.setTextColor(R.id.widget_todo_add, badgeFamily.hue)

        // 进度轴是「今日完成」（清单本身是全部活跃待办，两者口径不同，与稿子一致）
        val done = board.todayDone
        val total = board.todayTotal
        val percent = if (total == 0) 0 else done * 100 / total
        views.setTextViewText(R.id.widget_todo_done, "$done")
        views.setTextViewText(R.id.widget_todo_total, "/$total")
        // 完成度条：填充是白底 clip，宽度由 level 定（RemoteViews 改不了控件尺寸）
        views.setInt(R.id.widget_todo_progress_fill, "setColorFilter", progressFamily.hue)
        views.setInt(R.id.widget_todo_progress_fill, "setImageLevel", percent * 100)
        // 没有待办就不摆这根轴，空态由列表区的空文案说
        val hasTodos = board.items.isNotEmpty()
        views.setViewVisibility(R.id.widget_todo_progress_col, if (hasTodos) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_todo_divider, if (hasTodos) View.VISIBLE else View.GONE)

        // 滚动列表：数据由 TodoWidgetService 提供，显示几行交给桌面自己决定。
        // 别对 ListView 调 removeAllViews —— AdapterView 不支持，会抛 UnsupportedOperationException

        views.setRemoteAdapter(
            R.id.widget_todo_list,
            android.content.Intent(context, TodoWidgetService::class.java)
                .putExtra(TodoWidgetService.EXTRA_APPWIDGET_ID, appWidgetId)
        )
        views.setEmptyView(R.id.widget_todo_list, R.id.widget_todo_empty)
        // 行内点击的条目 id 由行的 fill-in intent 带过来，模板只出 action 与组件
        views.setPendingIntentTemplate(R.id.widget_todo_list, TodoToggleReceiver.toggleTemplatePendingIntent(context))

        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_TODO + appWidgetId, WidgetDeepLink.TAB_LIFE)
        )
        views.setOnClickPendingIntent(
            R.id.widget_todo_add,
            WidgetHelper.createLifeListPendingIntent(context, WidgetDeepLink.SEG_TODO_ADD + appWidgetId, "AGENDA")
        )
        // 进度轴 → 直达「今日安排」完整清单
        views.setOnClickPendingIntent(
            R.id.widget_todo_progress_col,
            WidgetHelper.createLifeListPendingIntent(context, WidgetDeepLink.SEG_TODO + 100 + appWidgetId, "AGENDA")
        )
        return views
    }
}
