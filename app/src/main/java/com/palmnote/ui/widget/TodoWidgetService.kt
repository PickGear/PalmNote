package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
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
import kotlinx.coroutines.runBlocking

/**
 * 待办组件的滚动列表：桌面自己滚动取行，不再由我们按尺寸「猜」显示几条。
 *
 * 行内容与点击行为都在 [TodoViewsFactory] 里构造；条目点击走集合的
 * `setPendingIntentTemplate` + 行内 fill-in intent（集合里的行不能各自持有 PendingIntent）。
 */
class TodoWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = TodoViewsFactory(
        context = applicationContext,
        appWidgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    )

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun lifeItemDao(): LifeItemDao
        fun lifeTemplateDao(): LifeTemplateDao
        fun preferencesManager(): PreferencesManager
    }

    companion object {
        const val EXTRA_APPWIDGET_ID = "widget_todo_appwidget_id"
    }
}

internal class TodoViewsFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private var todos: List<LifeItem> = emptyList()
    private var accent = WidgetData.AccentTheme(accent = 0, onAccent = 0)

    override fun onCreate() = Unit

    /**
     * 取数。这里读库是阻塞的，但运行在桌面的 binder 线程上（不是广播/主线程），
     * 也是 RemoteViewsService 的标准用法——广播线程那边仍然只走挂起路径。
     */
    override fun onDataSetChanged() {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            TodoWidgetService.WidgetEntryPoint::class.java
        )
        val prefs = entryPoint.preferencesManager()
        runBlocking {
            accent = WidgetData.readAccentTheme(context, prefs)
            todos = WidgetData.fetchTodayTodos(
                entryPoint.lifeItemDao(),
                entryPoint.lifeTemplateDao(),
                includeDemo = prefs.lifeDemoMode.first(),
                demoMeta = com.palmnote.data.db.dao.LIFE_DEMO_META
            )
        }
    }

    override fun onDestroy() {
        todos = emptyList()
    }

    override fun getCount(): Int = todos.size

    override fun getViewAt(position: Int): RemoteViews? {
        val item = todos.getOrNull(position) ?: return null
        return WidgetHelper.todoRowViews(context, item, accent.accent)
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = todos.getOrNull(position)?.id ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
