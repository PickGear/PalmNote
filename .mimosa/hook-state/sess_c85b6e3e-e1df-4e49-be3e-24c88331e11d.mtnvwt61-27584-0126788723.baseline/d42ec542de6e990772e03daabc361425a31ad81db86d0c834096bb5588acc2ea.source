package com.palmnote.ui.widget

import android.content.Context
import com.palmnote.app.R
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.LifeItem
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// 小组件的数据获取与格式化，与 RemoteViews 构建逻辑（WidgetHelper）分离
object WidgetData {

    // 组件强调色跟随应用主题色包：按主题 id 映射 drawable 变体，文字色按系统昼夜取亮/暗主色
    data class AccentTheme(
        val accentText: Int,
        val circleFillRes: Int,
        val pillFillRes: Int
    )

    fun readAccentTheme(context: Context, preferencesManager: com.palmnote.data.datastore.PreferencesManager): AccentTheme {
        val themeId = kotlinx.coroutines.runBlocking { preferencesManager.themeColor.first() }
        val isNight = (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val pkg = com.palmnote.ui.theme.ThemePackages.getById(themeId)
        val textArgb = if (isNight) pkg.darkPrimary else pkg.lightPrimary
        val circleRes = when (themeId) {
            "green" -> R.drawable.widget_circle_accent_green
            "blue" -> R.drawable.widget_circle_accent_blue
            "purple" -> R.drawable.widget_circle_accent_purple
            "orange" -> R.drawable.widget_circle_accent_orange
            else -> R.drawable.widget_circle_accent
        }
        val pillRes = when (themeId) {
            "green" -> R.drawable.widget_pill_accent_green
            "blue" -> R.drawable.widget_pill_accent_blue
            "purple" -> R.drawable.widget_pill_accent_purple
            "orange" -> R.drawable.widget_pill_accent_orange
            else -> R.drawable.widget_pill_accent
        }
        return AccentTheme(textArgb.toArgb(), circleRes, pillRes)
    }

    private fun androidx.compose.ui.graphics.Color.toArgb(): Int =
        ((alpha * 255).toInt() shl 24) or ((red * 255).toInt() shl 16) or ((green * 255).toInt() shl 8) or (blue * 255).toInt()

    // 金额紧凑显示：整数元不带小数（¥3200），非整元保留两位（¥32.50）
    fun formatMoneyShort(amount: Long): String {
        val sign = if (amount < 0) "-" else ""
        val abs = Math.abs(amount)
        val whole = abs / 100
        val cents = abs % 100
        return if (cents == 0L) "${sign}¥$whole" else "${sign}¥$whole.${cents.toString().padStart(2, '0')}"
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
    suspend fun fetchTodayTodos(lifeItemDao: LifeItemDao, lifeTemplateDao: LifeTemplateDao): List<LifeItem> {
        val templates = lifeTemplateDao.getAllVisibleTemplates().first()
        val todoTemplate = templates.firstOrNull { it.icon == "checklist" } ?: return emptyList()
        val today = LocalDate.now()
        val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return lifeItemDao.getItemsByTemplate(todoTemplate.id).first().filter { item ->
            val due = item.dueDate
            item.parentId == null && item.status != "ARCHIVED"
                && due != null && due >= todayStart && due < todayEnd
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
