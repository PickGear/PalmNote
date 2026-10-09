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

    // 卡片场景的金额缩写：超过 6 位整数时万/千单位折叠，避免窄卡截断（zh 用万，其他用 k/M）
    fun formatMoneyCompact(context: Context, amount: Long): String {
        val whole = Math.abs(amount) / 100
        if (whole < 1_000_000) return formatMoneyShort(amount)
        val isZh = context.resources.configuration.locales.get(0).language == "zh"
        val sign = if (amount < 0) "-" else ""
        return if (isZh) {
            val wan = Math.abs(amount) / 100.0 / 10_000
            val text = if (wan >= 100) "${wan.toInt()}万" else String.format(java.util.Locale.CHINA, "%.1f万", wan)
            "$sign¥$text"
        } else {
            val value = Math.abs(amount) / 100.0
            val text = when {
                value >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", value / 1_000_000)
                else -> String.format(java.util.Locale.US, "%.0fk", value / 1_000)
            }
            "$sign¥$text"
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
