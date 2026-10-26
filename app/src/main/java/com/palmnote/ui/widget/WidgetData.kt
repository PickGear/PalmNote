package com.palmnote.ui.widget

import android.content.Context
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.domain.model.ProgressValue
import com.palmnote.domain.model.progressOfStored
import com.palmnote.domain.util.LifeTemplateKind
import com.palmnote.domain.util.getKind
import com.palmnote.ui.theme.ThemePackages
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// 小组件的数据获取与格式化，与 RemoteViews 构建逻辑（WidgetHelper）分离
object WidgetData {

    // 语义色（App 内 Dopamine 体系）：实底白字，深浅共用；组件色卡不再硬编码散落
    val SEMANTIC_GOAL: Int = 0xFFE44444.toInt()

    // 组件强调色跟随应用主题色包：运行时取色（8 个色包 + 自定义色全部生效），
    // 着色用 RemoteViews 的 setColorFilter（ImageView）/ setTextColor，不再依赖逐主题 drawable 变体
    data class AccentTheme(
        val accent: Int,
        val onAccent: Int
    ) {
        companion object {
            val ON_ACCENT: Int = 0xFFFFFFFF.toInt()
        }
    }

    // 广播线程不能阻塞：挂起读 DataStore（原 runBlocking 实现在广播线程上有 ANR 风险）。
    // 缓存键含昼夜（深浅色切换会失配重建）；键与值必须**原子**地一起换——多个组件并发刷新时
    // 两个独立字段会交错出「键=新主题、值=旧主题」的组合并存活到下次失配，故合并为单字段。
    // DataStore 读取失败不在此兜底：异常沿调用方（ScopedWidgetProvider）既有 catch 记日志，
    // 组件本轮不更新，与重构前 runBlocking 抛出的行为一致。
    private data class AccentCacheKey(val themeId: String, val isNight: Boolean)

    @Volatile
    private var cachedAccent: Pair<AccentCacheKey, AccentTheme>? = null

    /**
     * 读某实例的强调色：[overrideThemeId] 非空时强制用该主题包的主色（逐实例配色覆盖），
     * null 跟随应用当前主题包。
     */
    suspend fun readAccentTheme(
        context: Context,
        preferencesManager: PreferencesManager,
        overrideThemeId: String? = null
    ): AccentTheme {
        val themeId = overrideThemeId ?: preferencesManager.themeColor.first()
        val isNight = (
            context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
            ) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val key = AccentCacheKey(themeId, isNight)
        cachedAccent?.takeIf { it.first == key }?.let { return it.second }

        val accent = buildAccentTheme(themeId, isNight)
        cachedAccent = key to accent
        return accent
    }

    /**
     * 某实例的强调色：有逐实例配色覆盖就用覆盖主题包主色，否则用该族设计稿默认色。
     *
     * 为什么要这个入口：WidgetPin 把账单 / 概览 / 接下来标记成 accentEditable，但此前只有账单
     * 真的读覆盖值，「配色」对概览 / 接下来是纯装饰（改了没有任何视觉变化）。
     */
    internal suspend fun accentColorOrDefault(
        context: Context,
        preferencesManager: PreferencesManager,
        appWidgetId: Int,
        defaultFamilyColor: Int
    ): Int {
        val override = preferencesManager.widgetAccentThemeFor(appWidgetId).first()
            ?: return defaultFamilyColor
        return readAccentTheme(context, preferencesManager, override).accent
    }

    private fun buildAccentTheme(themeId: String, isNight: Boolean): AccentTheme {
        val pkg = ThemePackages.getById(themeId)
        val argb = (if (isNight) pkg.darkPrimary else pkg.lightPrimary).toArgb()
        return AccentTheme(accent = argb, onAccent = AccentTheme.ON_ACCENT)
    }

    private fun androidx.compose.ui.graphics.Color.toArgb(): Int = ((alpha * 255).toInt() shl 24) or
        ((red * 255).toInt() shl 16) or
        ((green * 255).toInt() shl 8) or
        (blue * 255).toInt()

    // 金额紧凑显示：整数元不带小数（¥3200），非整元保留两位（¥32.50）
    fun formatMoneyShort(amount: Long): String {
        val sign = if (amount < 0) "-" else ""
        val abs = Math.abs(amount)
        val whole = abs / 100
        val cents = abs % 100
        // 千分位与设计稿一致（¥8,200 / ¥6,820）；整数段按 3 位分组
        val grouped = java.text.DecimalFormat("#,##0").format(whole)
        return if (cents == 0L) "$sign¥$grouped" else "$sign¥$grouped.${cents.toString().padStart(2, '0')}"
    }

    // 卡片场景的金额缩写：5 位整数（≥¥10,000）起万/千单位折叠 —— 最窄的统计卡只放得下 4-5 个字符，
    // 再大的数不折就会被省略号截断（zh 用万，其他用 k/M）。低于此值保留原值，不做无谓的精度损失。
    fun formatMoneyCompact(context: Context, amount: Long): String {
        if (Math.abs(amount) / 100 < 10_000) return formatMoneyShort(amount)
        val sign = if (amount < 0) "-" else ""
        return "$sign¥${formatAmountCompact(context, amount)}"
    }

    /**
     * 柱图顶部的峰值标：**裸数字 + k/万折叠**（稿 `.cv` 的 `128` / `8.2k` / `34.2k`）。
     *
     * 12 槽柱图的每列只有 1/12 卡宽（≈28dp），带 ¥ 与千分位的 `¥8,200` 会被 maxLines=1
     * 截成 `¥8,20`。口径：¥1,000 以下给整数，十万以下给一位小数的 k（`.0` 去掉），
     * 再大按万折叠（128万）——与 [formatAmountCompact] 的分界不同（那是给 5 位整数用的）。
     */
    fun formatChartValue(amount: Long): String {
        val yuan = Math.abs(amount) / 100
        val sign = if (amount < 0) "-" else ""
        val body = when {
            yuan < 1_000 -> yuan.toString()
            yuan < 100_000 -> String.format(java.util.Locale.US, "%.1fk", yuan / 1000.0).replace(".0k", "k")
            else -> {
                val wan = yuan / 10_000.0
                if (wan == wan.toInt().toDouble()) "${wan.toInt()}万"
                else String.format(java.util.Locale.CHINA, "%.1f万", wan)
            }
        }
        return sign + body
    }

    /** 色族序号 → (淡底, 饱和前景)。按条目取不同族，组件里不要只用主题色。 */
    data class ColorFamily(val tint: Int, val hue: Int)

    /** 第 index 个色族的淡底与前景（循环取，条目数超过族数也能用）。 */
    fun colorFamily(context: Context, index: Int): ColorFamily {
        val i = ((index % TINT_IDS.size) + TINT_IDS.size) % TINT_IDS.size
        return ColorFamily(context.getColor(TINT_IDS[i]), context.getColor(HUE_IDS[i]))
    }

    private val TINT_IDS = intArrayOf(
        R.color.widget_tint_1, R.color.widget_tint_2, R.color.widget_tint_3,
        R.color.widget_tint_4, R.color.widget_tint_5, R.color.widget_tint_6
    )

    private val HUE_IDS = intArrayOf(
        R.color.widget_hue_1, R.color.widget_hue_2, R.color.widget_hue_3,
        R.color.widget_hue_4, R.color.widget_hue_5, R.color.widget_hue_6
    )

    /**
     * 窄格子里的金额：**不带货币符号、只到元、不带正负号**（返回绝对值）。
     *
     * 账单组件那三格在 3 格宽时每格只有约 47dp 放文字（格子 67dp 减去 20dp 内边距），
     * 而 `-¥5290.50` 这种值要 66dp —— 真机上实测被省略号截成了 `-¥529…`，金额直接看不全。
     * 格子标签（收入/支出/净收入）已经说明方向，货币符号由调用方按格子给，正负号同理
     * （净收入可正可负，收入/支出不带号），免得拼出 `+-` 这种东西。
     * 结果最长 5 个字符：`9999` 或 `2.7万` / `13k`，加上 `¥` 也放得下。
     */
    fun formatAmountCompact(context: Context, amount: Long): String {
        val abs = Math.abs(amount)
        if (abs / 100 < 10_000) return java.text.DecimalFormat("#,##0").format(abs / 100)
        val isZh = context.resources.configuration.locales.get(0).language == "zh"
        return if (isZh) {
            val wan = abs / 100.0 / 10_000
            // 整数倍不带小数（5万 / 128万），非整数保留一位（1.3万）
            if (wan >= 100 || wan == wan.toInt().toDouble()) {
                "${wan.toInt()}万"
            } else {
                String.format(java.util.Locale.CHINA, "%.1f万", wan)
            }
        } else {
            val value = abs / 100.0
            // 同样：整数倍不带小数（13k / 2M）
            when {
                value >= 1_000_000 && value % 1_000_000 == 0.0 -> "${(value / 1_000_000).toInt()}M"
                value >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", value / 1_000_000)
                value % 1_000 == 0.0 -> "${(value / 1_000).toInt()}k"
                else -> String.format(java.util.Locale.US, "%.1fk", value / 1_000)
            }
        }
    }

    /**
     * 待办组件的一屏数据：清单是「逾期 + 今天及以后」的活跃条目，进度轴用今日完成度。
     *
     * [todayItems] 是**今天到期**的那批（含已完成），按 [items] 的同一种排序（时间升序）——
     * v22 待办四档都只列今天的事项（稿口径），头部「今天 N/M」也由它和 [todayDone] 推出。
     */
    data class TodoBoard(
        val items: List<LifeItem>,
        val todayItems: List<LifeItem> = emptyList(),
        val todayDone: Int,
        val todayTotal: Int
    )

    /**
     * 待办组件取数。**清单不能只查今天**：稿子里列的是「今天 / 明天 / 3 天后 / 本周日 / 11/02」，
     * 只查今天到期会让用户记的（没填日期或排在别的日子的）待办一条都不显示。
     * 排序：有日期的按到期日升序（逾期在最前），没日期的排最后。
     */
    suspend fun fetchTodoBoard(
        lifeItemDao: LifeItemDao,
        lifeTemplateDao: LifeTemplateDao,
        includeDemo: Boolean,
        demoMeta: String
    ): TodoBoard {
        val templates = lifeTemplateDao.getAllVisibleTemplates().first()
        val todoTemplate = templates.firstOrNull { it.getKind() == LifeTemplateKind.TODO }
            ?: return TodoBoard(emptyList(), emptyList(), 0, 0)
        val active = lifeItemDao.getWidgetItemsByTemplate(todoTemplate.id, includeDemo, demoMeta).first()
            .filter { it.parentId == null }
        val today = LocalDate.now()
        val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayItems = active.filter { it.dueDate?.let { d -> d >= todayStart && d < todayEnd } == true }
        val sorted = active.sortedWith(
            compareBy({ it.dueDate == null }, { it.dueDate ?: Long.MAX_VALUE }, { it.sortOrder })
        )
        return TodoBoard(
            items = sorted,
            // 今天的事项沿用同一排序（active 已按 dueDate 升序过滤出来，顺序即时间序）
            todayItems = sorted.filter { item ->
                item.dueDate?.let { d -> d >= todayStart && d < todayEnd } == true
            },
            todayDone = todayItems.count { it.status == "COMPLETED" },
            todayTotal = todayItems.size
        )
    }

    // 今天有待办的活跃条目（TodoWidget 与概览小组件共用）
    suspend fun fetchTodayTodos(
        lifeItemDao: LifeItemDao,
        lifeTemplateDao: LifeTemplateDao,
        includeDemo: Boolean,
        demoMeta: String
    ): List<LifeItem> {
        val templates = lifeTemplateDao.getAllVisibleTemplates().first()
        val todoTemplate = templates.firstOrNull { it.getKind() == LifeTemplateKind.TODO } ?: return emptyList()
        val today = LocalDate.now()
        val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return lifeItemDao.getWidgetItemsByTemplate(todoTemplate.id, includeDemo, demoMeta).first().filter { item ->
            val due = item.dueDate
            item.parentId == null &&
                item.status != "ARCHIVED" &&
                due != null &&
                due >= todayStart &&
                due < todayEnd
        }
    }

    // 每年重复的纪念日滚动到下一次日期；一次性日期已过则返回 null
    fun nextOccurrenceDaysIn(dateMillis: Long, isYearly: Boolean): Long? {
        val today = LocalDate.now()
        var next = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        if (next.isBefore(today)) {
            if (!isYearly) return null
            next = next.plusYears(ChronoUnit.YEARS.between(next, today))
            if (next.isBefore(today)) next = next.plusYears(1)
        }
        return next.toEpochDay() - today.toEpochDay()
    }

    /** 概览组件目标卡的一屏数据（纯数据；空态文案由组件侧注入，[WidgetData] 不持 Context）。 */
    data class GoalTileData(
        val pctText: String,
        val name: String,
        val countsText: String
    )

    /**
     * 目标卡取数：目标自 v1.4.2 起是**目标模板（kind = GOAL）下的生活条目**，不再是 `goals` 表。
     *
     * - 无条目 → null（组件侧显示「暂无目标」）；
     * - 有未完成条目（进度 < 100%）→ 取第一条，给百分比 + 计数文案；
     * - 全部算得出进度且都到 100% → 「100% + [allDoneName]」；
     * - 有任何一条**算不出进度**（如还没填当前值）且没有可展示的未完成项 →
     *   首条的名字 + `--`，与列表卡/详情「算不出就不出进度」同口径，**绝不谎报「全部完成」**。
     *
     * 进度走 [progressOfStored] 与详情页同一口径：**递减型**（起始值 > 目标值）也算得出
     * 正确百分比 —— 旧实现直接 `current/total`，递减型必然算错。
     */
    fun goalTileData(fieldsConfig: String, items: List<LifeItem>, allDoneName: String): GoalTileData? {
        if (items.isEmpty()) return null
        val withProgress = items.mapNotNull { item ->
            progressOfStored(fieldsConfig, item.fieldsData)?.let { item to it }
        }
        val unfinished = withProgress.firstOrNull { (_, progress) -> progress.fraction < 1f }
        val (item, progress) = unfinished
            ?: return if (withProgress.size == items.size) {
                GoalTileData("100%", allDoneName, "--")
            } else {
                GoalTileData("--", items.first().title, "--")
            }
        return GoalTileData("${(progress.fraction * 100).toInt()}%", item.title, goalCountsText(progress))
    }

    /**
     * 目标卡的进度计数文案：递增沿用旧观感 `当前/目标`；
     * 递减用 `当前 → 目标` —— 分子分母反向，斜杠会把「20 → 0」读成「完成了 15/0」。
     */
    fun goalCountsText(progress: ProgressValue): String {
        val current = countText(progress.current ?: return "--")
        val total = countText(progress.total ?: return "--")
        return if (progress.decreasing) "$current → $total" else "$current/$total"
    }

    /** 计数取整显示（7.0 → 7，7.5 → 7.5）：目标是计数量，别在桌面上写「7.0/12.0」。 */
    private fun countText(value: Double): String =
        if (value == kotlin.math.floor(value)) value.toLong().toString() else value.toString()
}
