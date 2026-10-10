package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.LIFE_DEMO_META
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.data.db.entity.getDisplayName
import com.palmnote.domain.util.LifeTemplateKind
import com.palmnote.domain.util.StreakEngine
import com.palmnote.domain.util.getKind
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * 今日打卡组件：列出 kind = HABIT 的生活模板，点行就地切换当天打卡。
 *
 * 与生活页详情**同一份数据**（LifeItem + LifeTemplate）：已打卡 = 今天该模板有一条非
 * ARCHIVED 的行；连击用 [StreakEngine] 按 [LifeItemDao.getDistinctCheckInDays] 的天数算，
 * 与详情页/统计页口径一致。
 */
class HabitWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun lifeTemplateDao(): LifeTemplateDao
        fun lifeItemDao(): LifeItemDao
        fun preferencesManager(): PreferencesManager
    }

    /** 一行打卡：模板 + 今天是否已打卡 + 当前连击。 */
    internal data class HabitRow(
        val templateId: Long,
        val name: String,
        val checked: Boolean,
        val streak: Int,
        /** 近 7 天（从 6 天前到今天）是否打卡 —— 圆点矩阵的一行 */
        val recent: List<Boolean> = emptyList()
    )

    override suspend fun onUpdateAsync(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val preferences = entryPoint.preferencesManager()
        val rows = fetchHabitRows(
            context = context,
            templateDao = entryPoint.lifeTemplateDao(),
            itemDao = entryPoint.lifeItemDao(),
            includeDemo = preferences.lifeDemoMode.first()
        )
        val accent = WidgetData.readAccentTheme(context, preferences)

        publish(context, appWidgetManager, appWidgetIds) { appWidgetId, _ ->
            bindViews(context, appWidgetId, rows, accent)
        }
    }

    /**
     * 打卡模板 → 每行状态。连击要按模板取一次「打卡天集合」，所以这里逐模板查；
     * 模板数量级在十位以内，且整轮刷新只跑一次（不是每个组件实例各跑一遍）。
     */
    private suspend fun fetchHabitRows(
        context: Context,
        templateDao: LifeTemplateDao,
        itemDao: LifeItemDao,
        includeDemo: Boolean,
        today: LocalDate = LocalDate.now()
    ): List<HabitRow> = habitTemplates(templateDao).map { template ->
        val days = itemDao.getDistinctCheckInDays(template.id, includeDemo, LIFE_DEMO_META).first()
        HabitRow(
            templateId = template.id,
            name = template.getDisplayName(context),
            checked = days.contains(today.toString()),
            streak = StreakEngine.compute(days, today).current,
            // 从 6 天前排到今天，最后一格就是今天
            recent = (RECENT_DAYS - 1 downTo 0).map { days.contains(today.minusDays(it.toLong()).toString()) }
        )
    }

    private suspend fun habitTemplates(dao: LifeTemplateDao): List<LifeTemplate> =
        dao.getAllVisibleTemplates().first().filter { it.getKind() == LifeTemplateKind.HABIT }

    internal fun bindViews(
        context: Context,
        appWidgetId: Int,
        rows: List<HabitRow>,
        accent: WidgetData.AccentTheme
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_habit_unified)
        views.removeAllViews(R.id.widget_habit_list)
        // 一个习惯一个颜色（按行序取调色板）：参考稿里就是这么区分的，整片网格也更好看
        val palette = WidgetData.widgetPalette(context)
        rows.take(MAX_ROWS).forEachIndexed { index, row ->
            views.addView(
                R.id.widget_habit_list,
                habitRow(context, row, palette[index % palette.size])
            )
        }

        views.setInt(R.id.widget_habit_badge, "setColorFilter", accent.accent)
        val checkedCount = rows.count { it.checked }
        views.setTextViewText(R.id.widget_habit_count, "$checkedCount/${rows.size}")
        views.setTextColor(R.id.widget_habit_count, accent.accent)
        views.setViewVisibility(R.id.widget_habit_count, if (rows.isEmpty()) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_habit_empty, if (rows.isEmpty()) View.VISIBLE else View.GONE)

        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_HABIT + appWidgetId, WidgetDeepLink.TAB_LIFE)
        )
        return views
    }

    private fun habitRow(
        context: Context,
        row: HabitRow,
        rowColor: Int
    ): RemoteViews {
        val itemView = RemoteViews(context.packageName, R.layout.widget_habit_item)
        // 圆圈三层结构（灰环/可着色圆底/对勾）：主题色经 setColorFilter 运行时着色，全主题生效
        itemView.setViewVisibility(R.id.widget_habit_check_ring, if (row.checked) View.GONE else View.VISIBLE)
        itemView.setViewVisibility(R.id.widget_habit_check_fill, if (row.checked) View.VISIBLE else View.GONE)
        if (row.checked) {
            itemView.setInt(R.id.widget_habit_check_fill, "setColorFilter", rowColor)
        }
        itemView.setViewVisibility(R.id.widget_habit_check, if (row.checked) View.VISIBLE else View.GONE)
        itemView.setTextViewText(R.id.widget_habit_check, if (row.checked) "✓" else "")
        itemView.setTextViewText(R.id.widget_habit_name, row.name)
        val nameColor = if (row.checked) {
            context.getColor(R.color.widget_v2_text_tertiary)
        } else {
            context.getColor(R.color.widget_v2_text_primary)
        }
        itemView.setTextColor(R.id.widget_habit_name, nameColor)
        itemView.setTextViewText(
            R.id.widget_habit_streak,
            if (row.streak > 0) context.getString(R.string.widget_streak_format, row.streak) else ""
        )
        // 近 7 天圆点：打过卡着主题色、没打卡着中性灰（图形是这行的主角）
        val emptyDot = context.getColor(R.color.widget_v2_ring)
        habitDotIds.forEachIndexed { index, dotId ->
            val done = row.recent.getOrElse(index) { false }
            itemView.setInt(dotId, "setColorFilter", if (done) rowColor else emptyDot)
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val state = if (row.checked) context.getString(R.string.widget_completed) else ""
            itemView.setContentDescription(R.id.widget_habit_row, "${row.name} $state".trim())
        }
        // 整行可点：18dp 圆圈太小，全行作为打卡热区
        val toggle = HabitCheckInReceiver.checkInPendingIntent(context, row.templateId)
        itemView.setOnClickPendingIntent(R.id.widget_habit_row, toggle)
        itemView.setOnClickPendingIntent(R.id.widget_habit_check, toggle)
        return itemView
    }

    private val habitDotIds = listOf(
        R.id.widget_habit_dot_0,
        R.id.widget_habit_dot_1,
        R.id.widget_habit_dot_2,
        R.id.widget_habit_dot_3,
        R.id.widget_habit_dot_4,
        R.id.widget_habit_dot_5,
        R.id.widget_habit_dot_6
    )

    companion object {
        /** 组件里最多列几行（再多交给生活页）。 */
        const val MAX_ROWS = 4

        /** 圆点矩阵一行 7 格，从 6 天前排到今天。 */
        private const val RECENT_DAYS = 7

        fun requestUpdateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, HabitWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                android.content.Intent(context, HabitWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }
    }
}
