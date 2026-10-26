package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.LIFE_DEMO_META
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.ProgressValue
import com.palmnote.domain.model.evalProgress
import com.palmnote.domain.model.progressOfStored
import com.palmnote.domain.util.LifeTemplateKind
import com.palmnote.domain.util.getKind
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 生活计数器组件（v22）：**目标进度**口径 —— 主计数「当前 / 目标」+ 八点 + 两枚副计数。
 *
 * 2026-10-23 用户定案：组件从「倒计时（生日/纪念日还有几天）」改为「目标进度」，
 * 与 v22 设计稿（今日喝水 6/8、还差 2 杯、八点、咖啡 2 · 烟 0）对齐；
 * 倒计时数据仍由「接下来」组件承载（[NextUpWidgetProvider] 消费 [collectCounterEvents]）。
 *
 * 取数口径（与详情页/列表卡/概览卡同一份 [progressOfStored]）：
 * - 只取 kind = GOAL 的模板下的条目，能读出「当前值」才计入（读不出不出数）；
 * - 主计数 = 第一条**未完成**（进度 < 100%）的条目，全部完成则取第一条；
 * - 副计数 = 主计数之外的前两条，只给当前值（没有目标值也给数）；
 * - 八点只在目标值是 **≤8 的正整数**时画：点 = 目标值、亮点 = 当前值（四舍五入）。
 *   分母不固定不画点（用户 2026-10-23 定），进度条照给（fraction 是连续值）。
 * - 「还差 N」= |目标 − 当前|（递增递减都成立），≤0 改说「已达成」；递减型目标
 *   的 hero 用「当前 → 目标」（斜杠会把「20 → 0」读成 20/0）。
 */
// open：尺寸由 WidgetSizeClass 在 bindViews 内归档（1×2/1×3 竖条已随「目录只做五档」移除）
open class LifeCounterWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun lifeItemDao(): LifeItemDao
        fun lifeTemplateDao(): LifeTemplateDao
        fun preferencesManager(): PreferencesManager
    }

    /** 一个目标的桌面投影：主计数与副计数共用（副计数的 target 为 null）。 */
    internal data class GoalCounter(
        val name: String,
        val current: Double,
        val target: Double?,
        val unit: String?,
        val fraction: Float?,
        val decreasing: Boolean
    ) {
        /** hero 文本：递增「6/8」，递减「20 → 0」，无目标「6」。 */
        val ratioText: String
            get() = when {
                target == null -> countText(current)
                decreasing -> "${countText(current)} → ${countText(target)}"
                else -> "${countText(current)}/${countText(target)}"
            }

        /** 「还差 N 杯」：无目标 / 已达成 / 递减不可比时返回 null（调用方收起）。 */
        fun remainingText(): String? {
            val t = target ?: return null
            val left = t - current
            if (left <= 0.0) return null
            return countText(left)
        }

        /** 八点的格数：目标值是 ≤8 的正整数才给（否则 0 = 不画）。 */
        val dotsTotal: Int
            get() = target?.takeIf { !decreasing && it > 0 && it <= DOTS_MAX && it == kotlin.math.floor(it) }?.toInt() ?: 0

        /** 八点亮数：当前值四舍五入，夹在 0..格数。 */
        val dotsOn: Int
            get() = if (dotsTotal == 0) 0 else Math.round(current).toInt().coerceIn(0, dotsTotal)
    }

    override suspend fun onUpdateAsync(appContext: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val counters = collectGoalCounters(appContext)

        publish(appContext, appWidgetManager, appWidgetIds) { context, appWidgetId, _ ->
            sizedRemoteViews(
                appWidgetManager.getAppWidgetOptions(appWidgetId),
                DEFAULT_WIDTH_DP,
                DEFAULT_HEIGHT_DP
            ) { width, height ->
                bindViews(context, appWidgetId, counters, width, height)
            }
        }
    }

    /**
     * 目标模板 → 桌面投影。**只数能读出当前值的条目**（fieldsData 里有进度字段的值）；
     * 主计数 = 第一条未完成，其余按原顺序当副计数。
     */
    internal suspend fun collectGoalCounters(context: Context): List<GoalCounter> {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val includeDemo = entryPoint.preferencesManager().lifeDemoMode.first()
        val templates = entryPoint.lifeTemplateDao().getAllVisibleTemplates().first()
            .filter { it.getKind() == LifeTemplateKind.GOAL }
        val all = templates.flatMap { template ->
            entryPoint.lifeItemDao().getWidgetItemsByTemplate(template.id, includeDemo, LIFE_DEMO_META).first()
                .mapNotNull { item -> item.toGoalCounter(template) }
        }
        val mainIndex = all.indexOfFirst { it.fraction != null && it.fraction < 1f }
            .takeIf { it >= 0 } ?: 0
        if (all.isEmpty()) return emptyList()
        return listOf(all[mainIndex]) + all.filterIndexed { index, _ -> index != mainIndex }
    }

    /** 条目 → [GoalCounter]：读不出当前值返回 null（桌面不出数，也不谎报 0）。 */
    private fun LifeItem.toGoalCounter(template: LifeTemplate): GoalCounter? {
        val configs = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString<List<FieldConfig>>(template.fieldsConfig)
        }.getOrDefault(emptyList())
        val field = configs.firstOrNull { it.showAsProgress && !it.disabled } ?: return null
        val obj = runCatching { Json.parseToJsonElement(fieldsData) as? JsonObject }.getOrNull() ?: return null
        fun num(key: String?): Double? =
            key?.let { (obj[it] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() }
        val current = num(field.key) ?: return null
        val target = field.progressTargetKey.takeIf { it.isNotBlank() }?.let(::num) ?: field.max
        val start = field.progressStartKey.takeIf { it.isNotBlank() }?.let(::num)
        val progress: ProgressValue? = target?.let { evalProgress(current, it, start) }
        val unit = (obj["unitText"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        return GoalCounter(
            name = title,
            current = current,
            target = target,
            unit = unit,
            fraction = progress?.fraction,
            decreasing = progress?.decreasing == true
        )
    }

    /**
     * 倒计时事件（「接下来」组件消费；计数器组件 v22 起只显目标进度，不再渲染这份数据）。
     *
     * 取语义身份为 [COUNTER_KINDS] 的模板（显式倒计时 + 生日 + 纪念日）：
     * 生日/纪念日的日期是**过去**的，按**下一次周年**滚（[counterTargetDate]）。
     * 排序按 daysLeft 升序（最紧迫在前）。
     */
    internal suspend fun collectCounterEvents(context: Context): List<CounterEvent> {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val includeDemo = entryPoint.preferencesManager().lifeDemoMode.first()
        val today = LocalDate.now()
        return entryPoint.lifeTemplateDao().getAllVisibleTemplates().first()
            .filter { it.getKind() in COUNTER_KINDS }
            .flatMap { template ->
                entryPoint.lifeItemDao().getWidgetItemsByTemplate(template.id, includeDemo, LIFE_DEMO_META).first()
                    .map { item -> item to template.getKind() }
            }
            .mapNotNull { (item, kind) -> item.toCounterEvent(today, kind) }
            .sortedBy { it.daysLeft }
    }

    private fun com.palmnote.data.db.entity.LifeItem.toCounterEvent(
        today: LocalDate,
        kind: LifeTemplateKind
    ): CounterEvent? {
        val anchorMs = dueDate ?: return null
        val anchor = java.time.Instant.ofEpochMilli(anchorMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val yearly = kind == LifeTemplateKind.BIRTHDAY || kind == LifeTemplateKind.ANNIVERSARY
        val lunar = lunarFlagOf(fieldsData)
        val targetDate = counterTargetDate(anchor, today, yearly, lunar = lunar)
        val daysLeft = ChronoUnit.DAYS.between(today, targetDate)
        if (daysLeft < 0) return null
        return CounterEvent(
            itemId = id,
            name = title,
            daysLeft = daysLeft,
            targetDate = targetDate,
            createdAt = createdAt,
            reminderTime = reminderTimeOf(dueDate)
        )
    }

    /** 事件日自带的时刻「HH:mm」；午夜（只设了日期）按没有时刻处理。 */
    private fun reminderTimeOf(dueMillis: Long?): String? {
        val millis = dueMillis ?: return null
        val time = java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        if (time.getHour() == 0 && time.getMinute() == 0) return null
        return time.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    /** 条目字段里的 `lunar` 开关（生日模板才有）；解析不出按公历。与仪表盘倒数卡共用同一解析。 */
    private fun lunarFlagOf(fieldsData: String): Boolean =
        com.palmnote.ui.life.fieldsDataLunarFlag(fieldsData)

    internal fun bindViews(
        context: Context,
        appWidgetId: Int,
        counters: List<GoalCounter>,
        widthDp: Int = DEFAULT_WIDTH_DP,
        heightDp: Int = DEFAULT_HEIGHT_DP
    ): RemoteViews {
        val size = WidgetSizeClass.of(widthDp, heightDp)
        val layout = when (size) {
            WidgetSizeClass.TALL -> R.layout.widget_v22_tall
            WidgetSizeClass.WIDE -> R.layout.widget_counter_v22_wide
            WidgetSizeClass.SQUARE -> R.layout.widget_counter_v22_square
            WidgetSizeClass.BAR, WidgetSizeClass.CHIP -> R.layout.widget_counter_v22_bar
        }
        val views = widgetViews(context, layout)
        WidgetV22.bindCardBg(views, size)
        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(
                context,
                WidgetDeepLink.SEG_DASHBOARD_GOAL + appWidgetId,
                WidgetDeepLink.TAB_LIFE
            )
        )
        when (size) {
            WidgetSizeClass.TALL -> bindTall4x4(context, views, counters)
            WidgetSizeClass.WIDE ->
                bindWide(context, views, counters)
            WidgetSizeClass.SQUARE -> bindSquare(context, views, counters)
            WidgetSizeClass.BAR, WidgetSizeClass.CHIP -> bindBar(context, views, counters)
        }
        return views
    }

    /**
     * 4×4（v37 §2-7）：hero「当前/目标」+ 四 KPI（目标/已完成/剩余/副计数）+ 弹性区点阵。
     * **柱图区按「不虚构分母」收成八点阵**：计数器只存当前值（目标原地更新），
     * 没有按天的历史记录可画「近 7 天完成」；点 = 目标值（≤8 才画）、亮点 = 当前值，
     * 与 2×2 同源。0 计数走空白态。
     */
    private fun bindTall4x4(context: Context, views: RemoteViews, counters: List<GoalCounter>) {
        val main = counters.firstOrNull()
        if (main == null) {
            WidgetV22.bindTallHeader(
                context, views,
                glyphRes = R.drawable.widget_ic_hash_white,
                title = context.getString(R.string.widget_counter_title),
                meta = null,
                familyIdx = MAIN_FAMILY
            )
            WidgetV22.bindTallHero(context, views, "—", null)
            WidgetV22.bindEmpty(
                context, views,
                iconRes = R.drawable.widget_ic_hash_white,
                title = context.getString(R.string.widget_no_goals),
                hint = context.getString(R.string.widget_dash_empty_hint),
                familyIdx = MAIN_FAMILY
            )
            WidgetV22.tallSections(context, views, "empty")
            return
        }
        WidgetV22.bindTallHeader(
            context, views,
            glyphRes = R.drawable.widget_ic_hash_white,
            title = main.name,
            meta = main.target?.let {
                context.getString(
                    R.string.widget_counter_goal_fmt,
                    countText(it),
                    main.unit ?: ""
                ).trim()
            },
            familyIdx = MAIN_FAMILY
        )
        WidgetV22.bindTallHero(
            context, views,
            main.ratioText,
            main.remainingText()?.let {
                if (main.unit != null) {
                    context.getString(R.string.widget_counter_remaining_unit, it, main.unit)
                } else {
                    context.getString(R.string.widget_counter_remaining, it)
                }
            }
        )
        bindTallKpis(context, views, counters)
        // 弹性区：八点阵（目标 ≤8 才画；否则整块收起，只留 hero + KPI）
        WidgetV22.tallSection(context, views, "chart", false)
        if (main.dotsTotal > 0) {
            val dots = widgetViews(context, R.layout.widget_v22_dotg_center)
            WidgetV22.bindDotg(context, dots, on = main.dotsOn, familyIdx = MAIN_FAMILY)
            views.removeAllViews(R.id.widget_tall_grid)
            views.addView(R.id.widget_tall_grid, dots)
            WidgetV22.tallSections(context, views, "kpis", "grid")
        } else {
            WidgetV22.tallSections(context, views, "kpis")
        }
    }

    /** 4×4 KPI：目标 / 已完成 / 剩余（无目标值的两项不摆）+ 副计数。 */
    private fun bindTallKpis(context: Context, views: RemoteViews, counters: List<GoalCounter>) {
        val main = counters.firstOrNull() ?: return
        val sub = counters.drop(1).firstOrNull()
        WidgetV22.bindKpis(
            context, views,
            buildList {
                main.target?.let {
                    add(
                        WidgetV22.Kpi(
                            context.getString(R.string.widget_counter_label_goal),
                            countText(it),
                            main.unit,
                            6
                        )
                    )
                }
                add(
                    WidgetV22.Kpi(
                        context.getString(R.string.widget_counter_label_done),
                        countText(main.current),
                        main.unit,
                        0
                    )
                )
                main.remainingText()?.let {
                    add(
                        WidgetV22.Kpi(
                            context.getString(R.string.widget_counter_label_left),
                            it,
                            main.unit,
                            4
                        )
                    )
                }
                sub?.let {
                    add(
                        WidgetV22.Kpi(
                            it.name,
                            countText(it.current),
                            it.unit,
                            3
                        )
                    )
                }
            }
        )
    }

    /** 4×2（稿 §2-7 space-between）：hd(目标名+hero) / 副计数胶囊居中 / 八点贴底右对齐。 */
    private fun bindWide(context: Context, views: RemoteViews, counters: List<GoalCounter>) {
        val main = counters.firstOrNull()
        views.setInt(R.id.widget_counter_badge, "setColorFilter", familyD(context, MAIN_FAMILY))
        if (main == null) {
            views.setViewVisibility(R.id.widget_counter_hero, View.GONE)
            views.setViewVisibility(R.id.widget_counter_dots_host, View.GONE)
            views.setViewVisibility(R.id.widget_counter_subs_host, View.GONE)
            views.setViewVisibility(R.id.widget_counter_empty, View.VISIBLE)
            return
        }
        views.setViewVisibility(R.id.widget_counter_empty, View.GONE)
        views.setTextViewText(R.id.widget_counter_title, main.name)
        views.setTextViewText(R.id.widget_counter_hero, main.ratioText)
        views.setViewVisibility(R.id.widget_counter_hero, View.VISIBLE)
        bindDots(context, views, main, R.id.widget_counter_dots_host, R.layout.widget_v22_dotr8, DOTR8_IDS)
        bindSubs(context, views, counters.drop(1), R.id.widget_counter_subs_host)
    }

    /** 2×2：hero / 八点 2×4 / 底行「还差 N」+「副计数 · 副计数」。 */
    private fun bindSquare(context: Context, views: RemoteViews, counters: List<GoalCounter>) {
        val main = counters.firstOrNull()
        views.setInt(R.id.widget_counter_badge, "setColorFilter", familyD(context, MAIN_FAMILY))
        if (main == null) {
            views.setViewVisibility(R.id.widget_counter_hero, View.GONE)
            views.setViewVisibility(R.id.widget_counter_dots_host, View.GONE)
            views.setViewVisibility(R.id.widget_counter_empty, View.VISIBLE)
            return
        }
        views.setViewVisibility(R.id.widget_counter_empty, View.GONE)
        views.setTextViewText(R.id.widget_counter_hero, main.ratioText)
        views.setViewVisibility(R.id.widget_counter_hero, View.VISIBLE)
        bindMeta(context, views, main, R.id.widget_counter_meta)
        bindDots(context, views, main, R.id.widget_counter_dots_host, R.layout.widget_v22_dotg, DOTG_IDS)
        // 底行右侧：副计数「名字 值 · 名字 值」
        val subs = counters.drop(1).take(2)
        if (subs.isEmpty()) {
            views.setViewVisibility(R.id.widget_counter_subs_text, View.GONE)
        } else {
            views.setTextViewText(
                R.id.widget_counter_subs_text,
                subs.joinToString(" · ") { "${it.name} ${countText(it.current)}" }
            )
            views.setViewVisibility(R.id.widget_counter_subs_text, View.VISIBLE)
        }
    }

    /** 4×1：三列读数（主目标 / 副计数 ×2）+ 进度条。 */
    private fun bindBar(context: Context, views: RemoteViews, counters: List<GoalCounter>) {
        views.setInt(R.id.widget_counter_badge, "setColorFilter", familyD(context, MAIN_FAMILY))
        val main = counters.firstOrNull()
        val subs = counters.drop(1)
        if (main == null) {
            views.setTextViewText(R.id.widget_counter_bar_main_lbl, context.getString(R.string.widget_no_goals))
            views.setTextViewText(R.id.widget_counter_bar_main, context.getString(R.string.widget_asset_dash))
            views.setTextViewText(R.id.widget_counter_bar_sub1_lbl, "")
            views.setTextViewText(R.id.widget_counter_bar_sub1, "")
            views.setTextViewText(R.id.widget_counter_bar_sub2_lbl, "")
            views.setTextViewText(R.id.widget_counter_bar_sub2, "")
        } else {
            views.setTextViewText(R.id.widget_counter_bar_main_lbl, main.name)
            views.setTextViewText(R.id.widget_counter_bar_main, main.ratioText)
            bindSubCol(views, subs.getOrNull(0), R.id.widget_counter_bar_sub1_lbl, R.id.widget_counter_bar_sub1)
            bindSubCol(views, subs.getOrNull(1), R.id.widget_counter_bar_sub2_lbl, R.id.widget_counter_bar_sub2)
        }
    }

    /** 2×1：目标名 + hero + 八个点。 */


    /** 「还差 N 杯」：没有目标值 / 已达成都不给（meta 收起）；递减型按 |差| 给。 */
    private fun bindMeta(context: Context, views: RemoteViews, main: GoalCounter, metaId: Int) {
        val left = main.remainingText()
        if (left == null) {
            views.setViewVisibility(metaId, View.GONE)
            return
        }
        views.setViewVisibility(metaId, View.VISIBLE)
        views.setTextViewText(
            metaId,
            if (main.unit != null) {
                context.getString(R.string.widget_counter_remaining_unit, left, main.unit)
            } else {
                context.getString(R.string.widget_counter_remaining, left)
            }
        )
    }

    /** 八点：格数 = 目标值（≤8 才画），亮点 = 当前值四舍五入。 */
    private fun bindDots(
        context: Context,
        views: RemoteViews,
        main: GoalCounter,
        hostId: Int,
        layoutRes: Int,
        dotIds: List<Int>
    ) {
        if (main.dotsTotal == 0) {
            views.setViewVisibility(hostId, View.GONE)
            return
        }
        val dots = widgetViews(context, layoutRes)
        val on = familyD(context, MAIN_FAMILY)
        val off = context.getColor(R.color.widget_v22_outline)
        dotIds.forEachIndexed { index, id ->
            dots.setInt(id, "setColorFilter", if (index < main.dotsOn) on else off)
        }
        views.removeAllViews(hostId)
        views.addView(hostId, dots)
        views.setViewVisibility(hostId, View.VISIBLE)
    }

    /** 副计数胶囊（4×2 用）：色点 + 名字 + 当前值，族色按位次轮转。 */
    private fun bindSubs(context: Context, views: RemoteViews, subs: List<GoalCounter>, hostId: Int) {
        views.removeAllViews(hostId)
        val shown = subs.take(2)
        if (shown.isEmpty()) {
            views.setViewVisibility(hostId, View.GONE)
            return
        }
        shown.forEachIndexed { index, sub ->
            // wrap 变体：稿 .ch.sm 无 flex —— 胶囊按内容收窄靠左排，不摊满整行
            val chip = widgetViews(context, R.layout.widget_v22_chip_wrap)
            WidgetV22.bindChip(
                context, chip,
                name = sub.name,
                sub = null,
                value = countText(sub.current),
                familyIdx = SUB_FAMILIES[index % SUB_FAMILIES.size],
                showDot = true
            )
            views.addView(hostId, chip)
        }
        views.setViewVisibility(hostId, View.VISIBLE)
    }

    /** 副计数列（4×1 用）：名字当标签、当前值当数值。 */
    private fun bindSubCol(views: RemoteViews, sub: GoalCounter?, labelId: Int, valueId: Int) {
        if (sub == null) {
            views.setTextViewText(labelId, "")
            views.setTextViewText(valueId, "")
            return
        }
        views.setTextViewText(labelId, sub.name)
        views.setTextViewText(valueId, countText(sub.current))
    }

    private fun familyD(context: Context, index: Int) = WidgetV22.family(context, index).d

    companion object {
        /** 组件声明的默认尺寸（4×2），桌面没给尺寸时兜底。 */
        const val DEFAULT_WIDTH_DP = 250
        const val DEFAULT_HEIGHT_DP = 180

        /** 八点的格数上限：目标值超过 8 不画点（分母固定才画点）。 */
        const val DOTS_MAX = 8.0

        /** 副计数胶囊的族色（稿内取色：青 / 紫）。 */
        val SUB_FAMILIES = intArrayOf(6, 3)

        /** 主计数的族色序号：c7 青（稿 c-count 的标识色）。 */
        const val MAIN_FAMILY = 6

        val DOTR8_IDS = listOf(
            R.id.widget_v22_dotr8_1, R.id.widget_v22_dotr8_2, R.id.widget_v22_dotr8_3,
            R.id.widget_v22_dotr8_4, R.id.widget_v22_dotr8_5, R.id.widget_v22_dotr8_6,
            R.id.widget_v22_dotr8_7, R.id.widget_v22_dotr8_8
        )

        val DOTG_IDS = listOf(
            R.id.widget_v22_dotg_1, R.id.widget_v22_dotg_2, R.id.widget_v22_dotg_3,
            R.id.widget_v22_dotg_4, R.id.widget_v22_dotg_5, R.id.widget_v22_dotg_6,
            R.id.widget_v22_dotg_7, R.id.widget_v22_dotg_8
        )

        /** 计数取整显示（7.0 → 7，7.5 → 7.5）：计数量别在桌面上写「7.0」。 */
        fun countText(value: Double): String =
            if (value == kotlin.math.floor(value)) value.toLong().toString() else value.toString()
    }
}

/**
 * 一条倒计时事件（「接下来」组件与倒计时数据源共用；计数器组件 v22 起不再渲染它，
 * 但取数与目标日推算仍走这里，[NextUpWidgetProvider] 直接消费）。
 *
 * [createdAt] 与 [reminderTime] 是「接下来」v22 的两个可视化口径：
 * 进度条 = 从建项到目标日已过去多少（createdAt 取不到就不画）；提醒行 = 事件日自带的
 * 时刻（生日设了 09:00 才显示，午夜按「没有时刻」处理）。
 */
internal data class CounterEvent(
    val itemId: Long,
    val name: String,
    val daysLeft: Long,
    val targetDate: LocalDate,
    val createdAt: Long? = null,
    val reminderTime: String? = null
)

/** 倒计时组件纳入的模板身份：显式倒计时 + 生日 + 纪念日（与组件说明文案一致）。 */
private val COUNTER_KINDS = setOf(
    LifeTemplateKind.COUNTDOWN,
    LifeTemplateKind.BIRTHDAY,
    LifeTemplateKind.ANNIVERSARY
)

/**
 * 倒计时组件的**目标日**（纯函数，可单测）：
 * - 显式倒计时：就是锚点那天（已过则由调用方按 `daysLeft >= 0` 过滤掉）；
 * - 生日 / 纪念日：滚到**下一次周年**（`nextYearlyOccurrence`，农历按农历反算）。
 */
internal fun counterTargetDate(
    anchor: LocalDate,
    today: LocalDate,
    yearly: Boolean,
    lunar: Boolean
): LocalDate = if (yearly) com.palmnote.ui.life.nextYearlyOccurrence(anchor, today, lunar) else anchor
