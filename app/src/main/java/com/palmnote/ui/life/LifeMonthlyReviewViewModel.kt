package com.palmnote.ui.life

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.LIFE_DEMO_META
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.templateDisplayName
import com.palmnote.domain.util.AppLogger
import com.palmnote.domain.util.LifeTemplateKind
import com.palmnote.domain.util.getKind
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** 回顾区间：上一个完整月份 / 去年整年。 */
enum class ReviewMode { LAST_MONTH, LAST_YEAR }

/**
 * 月度/年度回顾（叙事页，Moo/格志/Wrapped 式）：总记录数、活跃天数、
 * 最常记录、心情分布。只读不编辑；今日视角由今日看板承担，聚合视角由统计页承担。
 *
 * 与统计页的分工：统计页是「近 7 天 / 近 16 周」的**进行时**仪表，回顾页是
 * 「上个月 / 去年」的**完成时**叙事——数据源都是 LifeItem，口径走同一批 demo-aware 查询。
 */
@HiltViewModel
class LifeMonthlyReviewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val itemDao: LifeItemDao,
    private val templateDao: LifeTemplateDao,
    private val preferences: PreferencesManager
) : ViewModel() {

    data class TopTemplate(val icon: String, val name: String, val color: String, val count: Int)

    data class ReviewState(
        val mode: ReviewMode = ReviewMode.LAST_MONTH,
        /** 区间标签：月 = 「2026 年 8 月」（走 [R.string.life_review_month]），年 = "2025"。 */
        val label: String = "",
        val totalCount: Int = 0,
        val activeDays: Int = 0,
        val daysInPeriod: Int = 0,
        val topTemplates: List<TopTemplate> = emptyList(),
        /** 心情取值 → 出现次数（降序）。 */
        val moodCounts: List<Pair<String, Int>> = emptyList(),
        val loaded: Boolean = false,

        /** 读取失败时的提示；非 null 时页面显示它而不是「空回顾」。 */
        val error: String? = null
    )

    private val _state = MutableStateFlow(ReviewState())
    val state: StateFlow<ReviewState> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.value = loadSafe(ReviewMode.LAST_MONTH) }
    }

    fun setMode(mode: ReviewMode) {
        if (_state.value.mode == mode) return
        viewModelScope.launch { _state.value = loadSafe(mode) }
    }

    /**
     * [load] 的失败兜底。
     *
     * 此前 `load` 抛异常会让协程直接结束、`loaded` 永远停在 false ——
     * 页面就挂在一个不结束的转圈上，既没有提示也没有日志。
     * 失败时给出明确的错误态（`loaded = true` + [ReviewState.error]）。
     */
    private suspend fun loadSafe(mode: ReviewMode): ReviewState = try {
        // 回顾要聚合整月 / 整年的记录并解析 JSON —— 放到 Default，别占主线程
        withContext(Dispatchers.Default) { load(mode) }
    } catch (e: Exception) {
        AppLogger.e("LifeMonthlyReview", "load($mode) failed", e)
        ReviewState(
            mode = mode,
            loaded = true,
            error = context.getString(R.string.life_data_load_error)
        )
    }

    private suspend fun load(mode: ReviewMode): ReviewState {
        val zone = ZoneId.systemDefault()
        val period = when (mode) {
            ReviewMode.LAST_MONTH -> {
                val lastMonth = YearMonth.now().minusMonths(1)
                Period(
                    start = lastMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                    end = lastMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                    label = context.getString(R.string.life_review_month, lastMonth.year, lastMonth.monthValue),
                    days = lastMonth.lengthOfMonth()
                )
            }
            ReviewMode.LAST_YEAR -> {
                val lastYear = java.time.Year.now().minusYears(1)
                Period(
                    start = lastYear.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                    end = lastYear.plusYears(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                    label = "${lastYear.value}",
                    days = lastYear.length()
                )
            }
        }
        val demo = preferences.lifeDemoMode.first()

        val counts = itemDao.getTemplateCountsBetweenDemoAware(period.start, period.end, demo, LIFE_DEMO_META).first()
        val dayCounts = itemDao.getDayCountsBetweenDemoAware(period.start, period.end, demo, LIFE_DEMO_META).first()
        val moodCounts = moodCountsOf(period.start, period.end, demo)

        return ReviewState(
            mode = mode,
            label = period.label,
            totalCount = counts.sumOf { it.cnt },
            activeDays = dayCounts.size,
            daysInPeriod = period.days,
            topTemplates = counts.take(3).map {
                TopTemplate(
                    it.icon,
                    templateDisplayName(context, it.name, it.icon, it.isBuiltin),
                    it.color,
                    it.cnt
                )
            },
            moodCounts = moodCounts,
            loaded = true
        )
    }

    /** 一个回顾区间：[start, end) 毫秒 + 标签 + 天数。 */
    private data class Period(val start: Long, val end: Long, val label: String, val days: Int)

    /** 心情模板当月的 mood 取值计数（心情模板全模块唯一，按语义身份识别而非图标）。 */
    private suspend fun moodCountsOf(start: Long, end: Long, includeDemo: Boolean): List<Pair<String, Int>> {
        val moodTemplate = templateDao.getAllTemplates().first()
            .firstOrNull { it.getKind() == LifeTemplateKind.MOOD }
            ?: return emptyList()
        val fields = itemDao.getFieldsDataBetween(moodTemplate.id, start, end, includeDemo, LIFE_DEMO_META).first()
        val json = Json { ignoreUnknownKeys = true }
        return fields.mapNotNull { raw ->
            val obj = runCatching { json.decodeFromString<JsonObject>(raw) }.getOrNull()
            (obj?.get("mood") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }
    }
}
