package com.palmnote.ui.widget

import android.content.Context
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.LifeItem
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

    suspend fun readAccentTheme(context: Context, preferencesManager: PreferencesManager): AccentTheme {
        val themeId = preferencesManager.themeColor.first()
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
        return if (cents == 0L) "$sign¥$whole" else "$sign¥$whole.${cents.toString().padStart(2, '0')}"
    }

    // 卡片场景的金额缩写：5 位整数（≥¥10,000）起万/千单位折叠 —— 最窄的统计卡只放得下 4-5 个字符，
    // 再大的数不折就会被省略号截断（zh 用万，其他用 k/M）。低于此值保留原值，不做无谓的精度损失。
    fun formatMoneyCompact(context: Context, amount: Long): String {
        if (Math.abs(amount) / 100 < 10_000) return formatMoneyShort(amount)
        val sign = if (amount < 0) "-" else ""
        return "$sign¥${formatAmountCompact(context, amount)}"
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
        if (abs / 100 < 10_000) return (abs / 100).toString()
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
}
