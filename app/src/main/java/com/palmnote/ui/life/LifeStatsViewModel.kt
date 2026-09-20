package com.palmnote.ui.life

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.LIFE_DEMO_META
import com.palmnote.data.db.dao.LifeDayCategoryCount
import com.palmnote.data.db.dao.LifeDayCount
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.roundToInt

/** 近 16 周热力的周数（§14.12：热力按模板取语义，此处为「全生活记录密度」总览）。 */
private const val HEAT_WEEKS = 16

/**
 * 生活统计页数据源 —— **统计页即「本期报告」**。
 *
 * ⚠️ v1.27：原来的「周报月报」模板**已退役**（用户 2026-09-22 定：报告不该是模板），
 * 报告内容全部落在本页：4 张指标卡 + 本期/上期对比 + 分布 + 趋势 + 热力。
 *
 * 全部来自**真实聚合**（无硬编码示例值）：
 * - 今日专注分钟：专注模板（`timer`）今天会话的 `duration` 求和；
 * - 最长连胜：各打卡模板（`calendar_month`）真实连击取最大；
 * - 今日待办：待办模板（`checklist`）今天的未完成条目数；
 * - 习惯完成率：各打卡模板本周打卡天数之和 ÷ (7 × 打卡模板数)；
 * - 记录条数 / 习惯完成率的**上期值**：同一批数据按上周窗口再算一遍（**零额外查询**）；
 * - 本周记录分布：按模板分类汇总的本周条数占比（设计稿 dtl#15 的「堆叠占比条」）；
 * - 近 7 天看板 = 本周（周一起始）每日记录条数；
 * - 近 16 周热力 = 16 周 × 7 天网格（最右列 = 本周），格值 = 当日记录条数。
 *
 * 口径统一走 `LifeItemDao` 的 **demo-aware 查询（互斥）**：开演示＝只看示例，关＝只看用户自己的；
 * 且按模板可见性过滤（关闭的模板不进统计，§4.8(3) 的 11 出口之一）。
 */
@HiltViewModel
class LifeStatsViewModel @Inject constructor(
    private val itemDao: LifeItemDao,
    private val templateDao: LifeTemplateDao,
    private val preferences: PreferencesManager
) : ViewModel() {

    private val zone = ZoneId.systemDefault()

    val state: StateFlow<LifeStatsUi> = preferences.lifeDemoMode
        .flatMapLatest { demo ->
            // 窗口在每次订阅时重算：挂着统计页跨天/跨周后重订阅会跟到新窗口。
            val today = LocalDate.now()
            val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val todayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val weekStart: LocalDate = today.with(DayOfWeek.MONDAY)
            val weekStartMs = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
            val weekEnd = weekStart.plusWeeks(1)
            val weekEndMs = weekEnd.atStartOfDay(zone).toInstant().toEpochMilli()
            val gridStart = weekStart.minusWeeks((HEAT_WEEKS - 1).toLong())
            val gridStartMs = gridStart.atStartOfDay(zone).toInstant().toEpochMilli()
            val weekDays = weekDayKeys(weekStart)
            val prevWeekDays = weekDayKeys(weekStart.minusWeeks(1))

            templateDao.getAllTemplates().flatMapLatest { templates ->
                val timerT = templates.firstOrNull { it.icon == "timer" }
                val todoT = templates.firstOrNull { it.icon == "checklist" }
                val checkinTs = templates.filter { it.icon == "calendar_month" }

                val focusFlow = if (timerT == null) {
                    flowOf(0)
                } else {
                    itemDao.getFocusSessionFields(timerT.id, todayStart, todayEnd, demo, LIFE_DEMO_META)
                        .map { sumFocusMinutes(it) }
                }

                val todoFlow = if (todoT == null) {
                    flowOf(0)
                } else {
                    itemDao.getUnfinishedCountByTemplate(
                        todoT.id, todayStart, todayEnd, demo, LIFE_DEMO_META
                    )
                }

                // 各打卡模板的打卡天集合（连击、本期与上期完成率**共用同一份源数据**）。
                val checkinFlow = if (checkinTs.isEmpty()) {
                    flowOf(emptyList<List<String>>())
                } else {
                    combine(checkinTs.map { t -> itemDao.getDistinctCheckInDays(t.id, demo, LIFE_DEMO_META) }) { arr ->
                        arr.toList()
                    }
                }

                // 一张查询同时喂三处：16 周网格、本周柱状、本期/上期记录条数对比。
                val countsFlow = itemDao.getDayCountsBetweenDemoAware(
                    start = gridStartMs,
                    end = weekEndMs,
                    includeDemo = demo,
                    demoMeta = LIFE_DEMO_META
                )

                // 本周（7 天）按模板分类的条数 —— 「堆叠占比条」的源数据。
                val categoryFlow = itemDao.getDayCategoryCountsBetweenDemoAware(
                    start = weekStartMs,
                    end = weekEndMs,
                    includeDemo = demo,
                    demoMeta = LIFE_DEMO_META
                )

                combine(
                    focusFlow, todoFlow, checkinFlow, countsFlow, categoryFlow
                ) { focusMin, todo, checkinDays, counts, categories ->
                    val heat = buildHeatGrid(counts, gridStart, HEAT_WEEKS)
                    LifeStatsUi(
                        focusMinutesToday = focusMin,
                        maxStreak = checkinDays.maxOfOrNull { computeCheckInStreak(it) } ?: 0,
                        todoToday = todo,
                        habitRatePercent = weeklyHabitRate(checkinDays, weekDays),
                        habitRatePrevPercent = weeklyHabitRate(checkinDays, prevWeekDays),
                        recordsThisWeek = heat.lastOrNull()?.sum() ?: 0,
                        recordsPrevWeek = heat.getOrNull(HEAT_WEEKS - 2)?.sum() ?: 0,
                        categoryShare = buildCategoryShare(categories),
                        heatWeeks = heat
                    )
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LifeStatsUi()
        )

    /** 某一周（周一起始）的 7 个日期串，用于按周统计打卡完成率。 */
    private fun weekDayKeys(weekStart: LocalDate): Set<String> =
        (0L until 7L).map { weekStart.plusDays(it).toString() }.toSet()

    /** 本周习惯完成率（%）：各打卡模板打卡天数之和 ÷ (7 × 打卡模板数)，四舍五入。 */
    private fun weeklyHabitRate(checkinDays: List<List<String>>, weekDays: Set<String>): Int {
        if (checkinDays.isEmpty()) return 0
        val done = checkinDays.sumOf { list -> list.count { it in weekDays } }
        val total = weekDays.size * checkinDays.size
        if (total <= 0) return 0
        return (done * 100.0 / total).roundToInt()
    }

    /** 本周各分类条数 → 占比（降序）。无记录返回空表（**不画空占位条**）。 */
    private fun buildCategoryShare(rows: List<LifeDayCategoryCount>): List<LifeCategoryShare> {
        if (rows.isEmpty()) return emptyList()
        val byCategory = rows.groupingBy { it.category }.fold(0) { acc, row -> acc + row.cnt }
        val total = byCategory.values.sum()
        if (total <= 0) return emptyList()
        return byCategory.entries
            .sortedByDescending { it.value }
            .map { LifeCategoryShare(it.key, it.value, (it.value * 100.0 / total).roundToInt()) }
    }

    /**
     * 近 [weeks] 周 × 7 天网格（外层 = 周，最右 = 本周；内层 = 周一…周日）。
     * 无记录的格子留 0（**照常画**，不留白）。
     */
    private fun buildHeatGrid(counts: List<LifeDayCount>, gridStart: LocalDate, weeks: Int): List<List<Int>> {
        val grid = MutableList(weeks) { MutableList(7) { 0 } }
        counts.forEach { row ->
            val date = runCatching { LocalDate.parse(row.day) }.getOrNull() ?: return@forEach
            val weekIndex = ChronoUnit.WEEKS.between(gridStart, date.with(DayOfWeek.MONDAY)).toInt()
            val dayIndex = date.dayOfWeek.value - 1
            if (weekIndex in 0 until weeks && dayIndex in 0..6) {
                grid[weekIndex][dayIndex] = row.cnt
            }
        }
        return grid.map { it.toList() }
    }
}

/** 统计页真实指标（默认全 0 = 无数据态）。 */
data class LifeStatsUi(
    /** 今日专注分钟（专注模板会话 duration 求和，毫秒 / 60000）。 */
    val focusMinutesToday: Int = 0,
    /** 最长连胜（各打卡模板真实连击取最大）。 */
    val maxStreak: Int = 0,
    /** 今日待办：待办模板今天的未完成条目数。 */
    val todoToday: Int = 0,
    /** 本周习惯完成率（%）。 */
    val habitRatePercent: Int = 0,
    /** 上周习惯完成率（%，用于「与上期对比」）。 */
    val habitRatePrevPercent: Int = 0,
    /** 本周记录条数。 */
    val recordsThisWeek: Int = 0,
    /** 上周记录条数。 */
    val recordsPrevWeek: Int = 0,
    /** 本周记录分布（按模板分类，降序）；空 = 本周无记录。 */
    val categoryShare: List<LifeCategoryShare> = emptyList(),
    /** 近 16 周热力：外层 16 列（周，最右＝本周），内层 7 天（周一…周日）。 */
    val heatWeeks: List<List<Int>> = List(HEAT_WEEKS) { List(7) { 0 } }
)

/** 一个分类在本周记录里的占比（[percent] 已按总数四舍五入，可能合计不为 100 —— 属正常）。 */
data class LifeCategoryShare(val name: String, val count: Int, val percent: Int)

/** 会话 `fieldsData` 列表 → 今日专注分钟（解析 `duration` 字段求和）。 */
private fun sumFocusMinutes(fields: List<String>): Int {
    val json = Json { ignoreUnknownKeys = true }
    val ms = fields.sumOf { raw ->
        runCatching { json.decodeFromString<JsonObject>(raw) }.getOrNull()
            ?.get("duration")
            ?.let { (it as? JsonPrimitive)?.content?.toDoubleOrNull() }
            ?: 0.0
    }
    return (ms / 60000).toInt()
}
