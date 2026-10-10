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
 * 今日打卡组件（定稿）：左栏给连击最长的那个习惯的连续天数，右侧是「习惯 × 近 7 天」点阵。
 * 点阵行的整行点击＝给该习惯打今天的卡。
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
        /** 近 7 天（从 6 天前到今天）是否打卡 —— 点阵的一行 */
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

        publish(context, appWidgetManager, appWidgetIds) { appWidgetId, _ ->
            bindViews(context, appWidgetId, rows)
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
        rows: List<HabitRow>
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_habit_unified)
        // 组件自身的徽章与完成度用第一个色族（装饰色不占全局强调色）
        val widgetFamily = WidgetData.colorFamily(context, 0)
        views.setInt(R.id.widget_habit_badge, "setColorFilter", widgetFamily.hue)
        views.setTextColor(R.id.widget_habit_count, widgetFamily.hue)
        views.setTextViewText(R.id.widget_habit_count, "${rows.count { it.checked }}/${rows.size}")
        views.setViewVisibility(R.id.widget_habit_count, if (rows.isEmpty()) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_habit_empty, if (rows.isEmpty()) View.VISIBLE else View.GONE)

        // 左栏：连击最长的那个习惯（数字与习惯名同色）
        val topIndex = rows.indices.maxByOrNull { rows[it].streak }
        val top = topIndex?.let { rows[it] }
        if (top != null) {
            val topFamily = WidgetData.colorFamily(context, topIndex)
            views.setTextViewText(R.id.widget_habit_streak, "${top.streak}")
            views.setTextColor(R.id.widget_habit_streak, topFamily.hue)
            // 习惯只有一两条时卡片会空掉一大半：连击数字按行数放大，把左栏撑起来
            views.setTextViewTextSize(
                R.id.widget_habit_streak,
                android.util.TypedValue.COMPLEX_UNIT_SP,
                when {
                    rows.size <= 2 -> STREAK_SP_FEW
                    rows.size <= 4 -> STREAK_SP_SOME
                    else -> STREAK_SP_MANY
                }
            )
            views.setTextViewText(R.id.widget_habit_streak_name, top.name)
            views.setTextColor(R.id.widget_habit_streak_name, topFamily.hue)
        }

        // 点阵：一行一个习惯，行本身是打卡热区
        views.removeAllViews(R.id.widget_habit_grid)
        val shown = rows.take(MAX_ROWS)
        shown.forEachIndexed { index, row ->
            views.addView(R.id.widget_habit_grid, dotsRow(context, row, index))
        }
        views.setTextViewText(R.id.widget_habit_names, shown.joinToString(" · ") { it.name })

        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_HABIT + appWidgetId, WidgetDeepLink.TAB_LIFE)
        )
        return views
    }

    /** 点阵的一行：一个习惯的 7 个圆点，已打卡着该行的色族色、没打卡着中性灰。 */
    private fun dotsRow(context: Context, row: HabitRow, index: Int): RemoteViews {
        val rowView = RemoteViews(context.packageName, R.layout.widget_habit_dots)
        val family = WidgetData.colorFamily(context, index)
        val emptyDot = context.getColor(R.color.widget_v2_ring)
        habitDotIds.forEachIndexed { dotIndex, dotId ->
            val done = row.recent.getOrElse(dotIndex) { false }
            rowView.setInt(dotId, "setColorFilter", if (done) family.hue else emptyDot)
        }
        // 今天（最后一格）的描边环跟该行的色族色走
        rowView.setInt(R.id.widget_habit_dot_today_ring, "setColorFilter", family.hue)
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val state = if (row.checked) context.getString(R.string.widget_completed) else ""
            rowView.setContentDescription(R.id.widget_habit_dots_row, "${row.name} $state".trim())
        }
        rowView.setOnClickPendingIntent(
            R.id.widget_habit_dots_row,
            HabitCheckInReceiver.checkInPendingIntent(context, row.templateId)
        )
        return rowView
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
        const val MAX_ROWS = 5

        /** 连击数字字号：习惯越少放得越大（1-2 / 3-4 / 5+ 行）。 */
        private const val STREAK_SP_FEW = 64f
        private const val STREAK_SP_SOME = 48f
        private const val STREAK_SP_MANY = 40f

        /** 点阵一行 7 格，从 6 天前排到今天。 */
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
