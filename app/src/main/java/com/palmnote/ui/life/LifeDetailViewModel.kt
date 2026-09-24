package com.palmnote.ui.life

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.dao.LIFE_DEMO_META
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.app.R
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldContracts
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.model.encodeChecklist
import com.palmnote.domain.model.parseChecklist
import com.palmnote.domain.model.compoundRawOf
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.utils.LifeNumFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** 详情页状态。 */
sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object NotFound : DetailUiState
    data class Ready(val ui: DetailUi) : DetailUiState
}

/**
 * 详情页四段骨架的**装配结果**：由 [DetailAssembler] 一次算好，屏幕只负责画。
 * 这样「哪个字段进哪一段」只有一处实现，不在 Composable 里散落。
 */
data class DetailUi(
    val ctx: DetailCtx,
    val metrics: List<DetailMetric>,
    val groups: List<DetailGroup>,
    /** 打卡模板：今天是否已打卡（驱动详情页「今日打卡」按钮态）。 */
    val checkInTodayDone: Boolean = false
)

/**
 * 详情页数据源（§14.2 四段骨架）。
 *
 * - 条目与模板都按 **Flow 观察**，编辑 / 勾选后界面自动跟进；
 * - 装配走 [DetailAssembler]（契约驱动），**不猜 key、不补 0**；
 * - 唯一的就地操作是**待办勾选**（§14.12(8)：只允许「一步完成、可撤销」的动作，
 *   多字段修改一律回编辑页），外加「⋯」里的删除（带二次确认）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LifeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val itemDao: LifeItemDao,
    private val templateDao: LifeTemplateDao,
    private val preferences: PreferencesManager
) : ViewModel() {

    private val itemId: Long = when (val raw = savedStateHandle.get<Any>(ARG_ITEM_ID)) {
        is Long -> raw
        is Int -> raw.toLong()
        is String -> raw.toLongOrNull() ?: 0L
        else -> 0L
    }

    /**
     * 热力 / 时间线取**本月**（§14.12(6)：7 列、最多 35 格，超出换月）。
     * **在流重启时重算**而不是存成 val：跨月挂着详情页后，重新订阅（5s 无订阅即停）
     * 会让上游重启，窗口跟到新月，网格不会停在打开页面那个月。
     */
    private fun monthWindow(): Pair<Long, Long> {
        val now = YearMonth.now()
        val start = now.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = now.plusMonths(1).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return start to end
    }

    val state: StateFlow<DetailUiState> = itemDao.getItemByIdFlow(itemId)
        .flatMapLatest { item ->
            if (item == null) {
                flowOf(DetailUiState.NotFound)
            } else {
                val dayRowsFlow = preferences.lifeDemoMode.flatMapLatest { demo ->
                    val (start, end) = monthWindow()
                    itemDao.getTemplateDayRowsDemoAware(
                        templateId = item.templateId,
                        start = start,
                        end = end,
                        includeDemo = demo,
                        demoMeta = LIFE_DEMO_META
                    )
                }
                val daysFlow = preferences.lifeDemoMode.flatMapLatest { demo ->
                    itemDao.getDistinctCheckInDays(item.templateId, demo, LIFE_DEMO_META)
                }
                combine(
                    templateDao.getTemplateByIdFlow(item.templateId),
                    dayRowsFlow,
                    daysFlow
                ) { template, dayRows, days ->
                    if (template == null) DetailUiState.NotFound
                    else {
                        val streak = if (template.icon == "calendar_month") computeCheckInStreak(days) else null
                        val todayDone = days.contains(LocalDate.now().toString())
                        DetailUiState.Ready(
                            DetailAssembler.assemble(
                                item, template, dayRows,
                                streakOverride = streak, checkInTodayDone = todayDone
                            )
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState.Loading)

    /** 待办清单就地勾选（唯一允许的就地写操作）。 */
    fun toggleChecklist(fieldKey: String, index: Int) {
        viewModelScope.launch {
            val item = itemDao.getItemById(itemId) ?: return@launch
            val obj = runCatching { Json.decodeFromString<JsonObject>(item.fieldsData) }.getOrNull() ?: return@launch
            val raw = compoundRawOf(obj[fieldKey]) ?: return@launch
            val rows = parseChecklist(raw).toMutableList()
            val row = rows.getOrNull(index) ?: return@launch
            rows[index] = row.copy(done = !row.done)
            val updated = JsonObject(obj.toMutableMap().apply { put(fieldKey, JsonPrimitive(encodeChecklist(rows))) })
            itemDao.updateFieldsData(itemId, updated.toString())
        }
    }

    /** 删除这条记录（「⋯」菜单里唯一动作；调用方负责二次确认与返回）。 */
    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            itemDao.deleteItemCascade(itemId)
            onDeleted()
        }
    }

    /**
     * 打卡就地切换（详情页「今日打卡」按钮）：今天有 → 撤销（删今天的行）；今天无 → 插一条。
     * 与格子卡 [CheckInRing] 同一套语义（§7.2），写的是「今天有没有这条」而非某个字段。
     */
    fun toggleCheckIn() {
        viewModelScope.launch {
            val item = itemDao.getItemById(itemId) ?: return@launch
            val template = templateDao.getTemplateById(item.templateId) ?: return@launch
            val demo = preferences.lifeDemoMode.first()
            val today = LocalDate.now().toString()
            val existing = itemDao.getDayItemsOfTemplate(item.templateId, today, demo, LIFE_DEMO_META)
            if (existing.isNotEmpty()) {
                existing.forEach { itemDao.deleteItemCascade(it.itemId) }
            } else {
                val now = System.currentTimeMillis()
                itemDao.insertItem(
                    LifeItem(
                        templateId = item.templateId,
                        title = template.name,
                        fieldsData = "{}",
                        status = "ACTIVE",
                        createdAt = now,
                        updatedAt = now,
                        dueDate = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        meta = if (demo) LIFE_DEMO_META else null
                    )
                )
            }
        }
    }

    /**
     * 专注计时写会话：停止计时时由英雄区回调，插一条会话记录（duration = 毫秒）。
     * 专注模板无字段（§5.1），会话时长即它的全部数据；统计页按此聚合今日 / 本周时长。
     */
    fun saveFocusSession(durationMs: Long) {
        if (durationMs <= 0) return
        viewModelScope.launch {
            val item = itemDao.getItemById(itemId) ?: return@launch
            val template = templateDao.getTemplateById(item.templateId) ?: return@launch
            val demo = preferences.lifeDemoMode.first()
            val now = System.currentTimeMillis()
            itemDao.insertItem(
                LifeItem(
                    templateId = item.templateId,
                    title = template.name,
                    fieldsData = """{"duration":$durationMs}""",
                    status = "ACTIVE",
                    createdAt = now,
                    updatedAt = now,
                    dueDate = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    meta = if (demo) LIFE_DEMO_META else null
                )
            )
        }
    }

    companion object {
        /** 与路由 `LifeDetail(itemId = …)` 的属性名一致（type-safe 导航写入 SavedStateHandle）。 */
        const val ARG_ITEM_ID = "itemId"
    }
}

/**
 * 四段骨架装配器（§14.2 / §14.11 / §14.12）。
 *
 * 顺序：解析 → 定英雄区形态 → 算 ② 指标 → ③ 结构区（**排除已被 ① / ② 用掉的字段**）。
 * ③ 按字段的「手」分组（§14.2），组标题复用既有文案 `life_detail_group_*`。
 */
object DetailAssembler {

    private val JSON = Json { ignoreUnknownKeys = true }

    fun assemble(
        item: LifeItem,
        template: LifeTemplate,
        dayRows: List<LifeItemDao.TemplateDayRow> = emptyList(),
        /** 打卡连击：由真实打卡记录算出，覆盖 fieldsData 里可能陈旧的快照值。 */
        streakOverride: Int? = null,
        /** 打卡模板：今天是否已打卡。 */
        checkInTodayDone: Boolean = false
    ): DetailUi {
        val configs = parseConfigs(template.fieldsConfig)
        val baseObj = runCatching { JSON.decodeFromString<JsonObject>(item.fieldsData) }
            .getOrNull() ?: JsonObject(emptyMap())
        // 连击覆盖：把计算出的真实连击写回 currentStreak，英雄区 / 指标 / 进度全部随之更新。
        val obj = if (streakOverride != null) {
            JsonObject(baseObj.toMutableMap().apply { put("currentStreak", JsonPrimitive(streakOverride)) })
        } else baseObj
        val ctx = DetailCtx(item, template, configs, obj, identityColor(template.color))
        val consumed = consumedKeys(ctx)
        val metrics = metricsOf(ctx)
        val metricKeys = metrics.map { it.key }.toSet()
        val visible = configs
            .sortedBy { it.sortOrder }
            .filter { it.key !in consumed && it.key !in metricKeys }
        // ③ **一字段一组**（照设计稿 dtl_01–16 实测）：每个可见字段一张卡，组标题 = 字段名。
        //
        // 为何不是「按手合并分组」：设计稿里每张卡都带自己的小标题（「凭证」「明细」「备注」…），
        // 且「分组 · subtaskKind」这种标题里还带**字段 key** —— 说明「· 类型」「（新增字段）」
        // 都是设计标注、**不进 UI**，标题只取字段名。合并成「文本 / 明细与清单」这类手名会与图不符。
        val fieldGroups = visible.mapNotNull { cfg ->
            val rows = buildRows(ctx, cfg)
            if (rows.isEmpty()) null else DetailGroup(rows = rows, title = cfg.label.ifBlank { cfg.key })
        }
        // 模板专属块（热力 / 时间线）排在字段组**之前**：它们是这一屏的主体（§14.11 #11/#12/#13）。
        return DetailUi(ctx, metrics, templateExtraGroups(ctx, dayRows) + fieldGroups, checkInTodayDone)
    }

    /**
     * 已被 ① 英雄区消费的字段 key —— 避免同一个值在页面上出现两次。
     * 逐模板标定（与 16 张设计稿一一对应）。
     */
    private fun consumedKeys(ctx: DetailCtx): Set<String> = when (ctx.template.icon) {
        "savings" -> setOf("targetAmount", "currentAmount", "remain")
        "shopping_cart" -> setOf("budget", "spent")
        "school" -> setOf("completedLessons", "totalLessons")
        "calendar_month" -> setOf("currentStreak", "targetDays")
        "subscriptions" -> setOf("price", "nextBilling", "daysToBilling", "billingCycle")
        "fitness_center" -> setOf("weight")
        "flight" -> setOf("destination", "startDate", "route")
        "menu_book" -> setOf("totalPages", "currentPage", "author", "cover")
        "checklist" -> setOf("subtasks")
        "mood" -> setOf("mood", "energy")
        "book" -> setOf("content", "weather", "mood")
        "timer_off" -> setOf("targetDate", "remainDays")
        "trending_up" -> setOf("start_date", "elapsedDays")
        "cake" -> setOf("date", "person")
        "celebration" -> setOf("date", "person")
        "build" -> setOf("boughtAt", "cycle")
        else -> emptySet()
    }

    /**
     * ② 主指标行：逐模板标定，**全部由真实数据算出**（算不出的那一项就不出现）。
     * P2（天数巨字）与「变化」型一律不出现 —— 避免同屏两处进度（§14.12(4)）。
     *
     * ⚠️ 指标**允许**与英雄区同值（设计稿的存钱「已存」两处都有）：② 是**分栏快照**、
     * ① 是**主角**；真正要避免的是同一个字段在 ③ 结构区再列一遍（故 ③ 排除 ② 用掉的 key）。
     * 指标名**取字段自己的 label**（数据），不当场造中文词 —— 否则英文界面会串中文。
     */
    private fun metricsOf(ctx: DetailCtx): List<DetailMetric> {
        if (!ctx.showsMetrics) return emptyList()

        fun m(key: String, value: String?): DetailMetric? =
            value?.let { DetailMetric(key, ctx.cfg(key)?.label ?: key, it) }

        /** 派生指标：名字来自文案（不是字段），值自己算。 */
        fun d(key: String, labelRes: Int, value: String?): DetailMetric? =
            value?.let { DetailMetric(key, value = it, labelRes = labelRes) }

        // 照设计稿 dtl_01–16：**每张指标行都是 3 格**，值 15sp/700 + 标签 10sp。
        // 此前只放「算得出的」导致常出现 1–2 格，与图不符 ⟹ 这里尽量凑满 3 格。
        val kept = keptDays(ctx)
        val list = when (ctx.template.icon) {
            // dtl_01 存钱：已存 / 日均 / 已坚持
            "savings" -> listOfNotNull(
                m("currentAmount", ctx.num("currentAmount")?.let { fmtMoney(it) }),
                d("daily", R.string.life_metric_daily, ctx.num("currentAmount")?.let { cur ->
                    dailyOf(cur, kept)?.let { fmtMoney(it) }
                }),
                d("kept", R.string.life_metric_kept, kept?.toString())
            )
            // dtl_02 购物：已买 / 分类 / 店铺
            "shopping_cart" -> listOfNotNull(
                d("bought", R.string.life_metric_bought, boughtText(ctx)),
                m("category", ctx.list("category").size.takeIf { n -> n > 0 }?.toString()),
                m("store", ctx.str("store"))
            )
            // dtl_03 待办：总数 / 已完成 / 剩余（CHECKLIST 不带日期，故不冒出「今天 / 逾期」）
            "checklist" -> {
                val rows = runCatching { parseChecklist(ctx.raw("subtasks")) }.getOrNull().orEmpty()
                val done = rows.count { it.done }
                listOfNotNull(
                    d("total", R.string.life_metric_total, rows.size.takeIf { n -> n > 0 }?.toString()),
                    d("done", R.string.life_metric_done, done.takeIf { n -> n > 0 }?.toString()),
                    d("left", R.string.life_metric_left, (rows.size - done).takeIf { n -> n > 0 }?.toString())
                )
            }
            // dtl_04 旅行：行程 / 同行 / 预算
            "flight" -> listOfNotNull(
                d("itinerary", R.string.life_metric_itinerary, tableRowCount(ctx).takeIf { n -> n > 0 }?.toString()),
                m("companions", ctx.list("companions").size.takeIf { n -> n > 0 }?.toString()),
                m("budget", ctx.num("budget")?.let { fmtMoney(it) })
            )
            // dtl_05 阅读：已读页 / 剩余页 / 日均
            "menu_book" -> listOfNotNull(
                m("currentPage", withUnit(ctx, "currentPage")),
                d("left", R.string.life_metric_left, leftText(ctx, "totalPages", "currentPage")),
                d("daily", R.string.life_metric_daily, ctx.num("currentPage")?.let { cur ->
                    dailyOf(cur, kept)?.let { "${fmtNumber(it)} ${ctx.cfg("currentPage")?.unit ?: ""}".trim() }
                })
            )
            // dtl_06 学习：单次时长 / 已完成 / 剩余（「累计 / 平均评分」要跨条聚合，P4）
            "school" -> listOfNotNull(
                m("duration", withUnit(ctx, "duration")),
                m("completedLessons", withUnit(ctx, "completedLessons")),
                d("left", R.string.life_metric_left, leftText(ctx, "totalLessons", "completedLessons"))
            )
            // dtl_11 打卡：连续 / 目标 / 完成率
            "calendar_month" -> listOfNotNull(
                m("currentStreak", withUnit(ctx, "currentStreak")),
                m("targetDays", withUnit(ctx, "targetDays")),
                ctx.progressOf(ctx.cfg("currentStreak"))?.fraction?.let {
                    d("rate", R.string.life_metric_done, "${(it * 100).toInt()}%")
                }
            )
            // dtl_14 订阅：每期 / 下次扣费 / 距扣费
            "subscriptions" -> listOfNotNull(
                m("price", ctx.num("price")?.let { fmtMoney(it) }),
                m("nextBilling", ctx.dateText("nextBilling")),
                m("daysToBilling", ctx.cfg("daysToBilling")?.let { ctx.derived(it) }?.let { fmtNumber(it) })
            )
            "fitness_center" -> listOfNotNull(
                m("weight", withUnit(ctx, "weight")),
                m("sleep", withUnit(ctx, "sleep")),
                m("bmi", ctx.cfg("bmi")?.let { ctx.derived(it) }?.let { fmtNumber(it) })
            )
            "mood" -> listOfNotNull(m("energy", ctx.num("energy")?.let { fmtNumber(it) }))
            "book" -> listOfNotNull(d("photos", R.string.life_metric_total, ctx.list("photos").size.takeIf { n -> n > 0 }?.toString()))
            "build" -> listOfNotNull(m("cycle", withUnit(ctx, "cycle")))
            else -> emptyList()
        }
        return list.take(3)
    }

    // ───────── 指标辅助（全部由真实数据算；算不出就 null ⟹ 该格不出现）─────────

    /** 「数值 + 字段自带单位」；单位取自 fieldsConfig（数据），不用中文副词。 */
    private fun withUnit(ctx: DetailCtx, key: String): String? {
        val v = ctx.num(key) ?: ctx.cfg(key)?.let { ctx.derived(it) } ?: return null
        val unit = ctx.cfg(key)?.unit.orEmpty()
        return fmtNumber(v) + if (unit.isBlank()) "" else " $unit"
    }

    /** 已坚持天数 = 今天 − 创建日。 */
    private fun keptDays(ctx: DetailCtx): Int? {
        val created = ctx.item.createdAt.takeIf { it > 0 } ?: return null
        return DateUtils.getDaysSince(created).takeIf { it >= 0 }
    }

    /** 日均 = 当前值 ÷ 已坚持天数（天数必须 > 0，不除 0）。 */
    private fun dailyOf(current: Double, kept: Int?): Double? =
        kept?.takeIf { it > 0 }?.let { current / it }

    /** 剩余 = 总量 − 已完成（为正才显示）。 */
    private fun leftText(ctx: DetailCtx, totalKey: String, doneKey: String): String? {
        val total = ctx.num(totalKey) ?: return null
        val done = ctx.num(doneKey) ?: 0.0
        val unit = ctx.cfg(doneKey)?.unit.orEmpty()
        val left = total - done
        if (left <= 0) return null
        return fmtNumber(left) + if (unit.isBlank()) "" else " $unit"
    }

    /** 表格行数（购物明细 / 行程明细）—— 走 `buildRows()`，它已经把 TABLE 解成行模型。 */
    private fun tableRowCount(ctx: DetailCtx): Int {
        val cfg = ctx.configs.firstOrNull { it.type == FieldType.TABLE } ?: return 0
        return buildRows(ctx, cfg).filterIsInstance<DetailRowModel.TableRows>().firstOrNull()?.rows?.size ?: 0
    }

    /** 购物「已买 / 总数」：TABLE 行里出现 true / ✓ 即算已买。 */
    private fun boughtText(ctx: DetailCtx): String? {
        val cfg = ctx.configs.firstOrNull { it.type == FieldType.TABLE } ?: return null
        val rows = buildRows(ctx, cfg).filterIsInstance<DetailRowModel.TableRows>()
            .firstOrNull()?.rows ?: return null
        if (rows.isEmpty()) return null
        val bought = rows.count { cells -> cells.any { it.equals("true", true) || "✓" in it } }
        return "$bought / ${rows.size}"
    }

    private fun parseConfigs(raw: String): List<FieldConfig> =
        runCatching { JSON.decodeFromString<List<FieldConfig>>(raw) }.getOrNull().orEmpty()
            .filter { !it.disabled }
}

