package com.palmnote.ui.life

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.ui.widget.WidgetUpdateHelper
import com.palmnote.data.db.dao.LIFE_DEMO_META
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.BuiltinFieldText
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.data.db.entity.getDisplayName
import com.palmnote.domain.model.EntityType
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.model.compoundRawOf
import com.palmnote.domain.model.encodeChecklist
import com.palmnote.domain.model.parseChecklist
import com.palmnote.domain.repository.CrossLinkRepository
import com.palmnote.domain.util.ConsistencyResult
import com.palmnote.domain.util.DateUtils
import com.palmnote.domain.util.LifeTemplateKind
import com.palmnote.domain.util.StreakEngine
import com.palmnote.domain.util.getKind
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import com.palmnote.domain.repository.LifeItemRepository
import javax.inject.Inject

/** 详情页状态。 */
sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object NotFound : DetailUiState

    /**
     * 数据流**失败**（库打不开 / 迁移失败 / 字段 JSON 结构意外）。
     *
     * 此前没有这个状态：上游抛异常后 `StateFlow` 永远停在 [Loading]，用户看到的是
     * 一个永不结束的转圈，既没提示也没法自愈。区分出来之后页面至少能说清发生了什么。
     */
    data object Error : DetailUiState
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
    val checkInTodayDone: Boolean = false,
    /** 详情页 ④：来源记录关联的账单 / 笔记数量。 */
    val relationCounts: RelationCounts = RelationCounts()
)

data class RelationCounts(val bills: Int = 0, val notes: Int = 0) {
    val isEmpty: Boolean get() = bills == 0 && notes == 0
}

/** 详情页 ② 指标需要的跨记录聚合；窗口由 ViewModel 统一计算。 */
data class DetailAggregates(
    val checkInMonthCount: Int? = null,
    val checkInLongest: Int? = null,
    val checkInTotal: Int? = null,
    /** 近 7 天一致性（打卡）：hits/window，与连击互补的软指标。 */
    val checkInConsistency: ConsistencyResult? = null,
    val todoToday: Int? = null,
    val todoOverdue: Int? = null,
    val todoDoneThisWeek: Int? = null,
    val bookMonthCount: Int? = null,
    val bookStreak: Int? = null,
    val bookPhotoCount: Int? = null,
    val schoolTotalMinutes: Double? = null,
    val schoolCompletedLessons: Double? = null,
    val moodMonthAverage: Double? = null,
    val moodStreak: Int? = null,
    val focusTodayMs: Long? = null,
    val focusWeekMs: Long? = null,
    val focusSessions: List<FocusSessionEntry> = emptyList(),
    val focusWeekBars: List<FocusWeekBar> = emptyList(),
    /** 「N 类中各占多少」的**真实计数**：该模板下所有记录的 MULTI_SELECT 取值出现次数。 */
    val multiSelectCounts: Map<String, Map<String, Int>> = emptyMap()
)

/**
 * 详情页数据源（§14.2 四段骨架）。
 *
 * - 条目与模板都按 **Flow 观察**，编辑 / 勾选后界面自动跟进；
 * - 装配走 [DetailAssembler]（契约驱动），**不猜 key、不补 0**；
 * - 就地操作有**三件**，判据都是「一步完成、可撤销」（§14.12(8)）：
 *   **清单勾选**（[toggleChecklist]）、**明细表勾选**（[toggleTableCell]，购物「已买」）、
 *   **进度 ±**（[setProgress]）。多字段修改一律回编辑页，
 *   外加「⋯」里的删除（带二次确认）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LifeDetailViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
    private val itemDao: LifeItemDao,
    private val templateDao: LifeTemplateDao,
    private val repository: LifeItemRepository,
    private val crossLinkRepository: CrossLinkRepository,
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

    /**
     * 进度写入锁：`setProgress`（绝对值，来自就地编辑）与 `nudgeProgress`（增量，来自 ±）
     * 必须**排序**执行，否则后写的会用旧值覆盖前一笔。
     */
    private val progressWriteLock = Mutex()

    val state: StateFlow<DetailUiState> = itemDao.getItemByIdFlow(itemId)
        .flatMapLatest { item ->
            if (item == null) {
                flowOf(DetailUiState.NotFound)
            } else {
                templateDao.getTemplateByIdFlow(item.templateId).flatMapLatest { template ->
                    if (template == null) {
                        flowOf(DetailUiState.NotFound)
                    } else {
                        // 语义身份只读一次（收敛入口，行为与逐处 icon 比较等价）
                        val kind = template.getKind()
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
                        val daysFlow = if (kind != LifeTemplateKind.HABIT) {
                            flowOf(emptyList())
                        } else {
                            preferences.lifeDemoMode.flatMapLatest { demo ->
                                itemDao.getDistinctCheckInDays(item.templateId, demo, LIFE_DEMO_META)
                            }
                        }
                        val checkInMonthCountFlow = if (kind != LifeTemplateKind.HABIT) {
                            flowOf(0)
                        } else {
                            preferences.lifeDemoMode.flatMapLatest { demo ->
                                val (start, end) = monthWindow()
                                itemDao.getTemplateRecordCount(item.templateId, start, end, demo, LIFE_DEMO_META)
                            }
                        }
                        val checkInTotalFlow = if (kind != LifeTemplateKind.HABIT) {
                            flowOf(0)
                        } else {
                            preferences.lifeDemoMode.flatMapLatest { demo ->
                                itemDao.getTemplateRecordCount(
                                    templateId = item.templateId,
                                    start = 0L,
                                    end = Long.MAX_VALUE,
                                    includeDemo = demo,
                                    demoMeta = LIFE_DEMO_META
                                )
                            }
                        }
                        val bookDaysFlow = if (kind != LifeTemplateKind.JOURNAL) {
                            flowOf(emptyList())
                        } else {
                            preferences.lifeDemoMode.flatMapLatest { demo ->
                                itemDao.getDistinctRecordDays(item.templateId, demo, LIFE_DEMO_META)
                            }
                        }
                        val focusSessionsFlow = if (kind != LifeTemplateKind.FOCUS) {
                            flowOf(emptyList())
                        } else {
                            preferences.lifeDemoMode.flatMapLatest { demo ->
                                val weekStart = LocalDate.now().with(DayOfWeek.MONDAY)
                                val weekEnd = weekStart.plusWeeks(1)
                                itemDao.getFocusSessionsDemoAware(
                                    templateId = item.templateId,
                                    start = weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                                    end = weekEnd.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                                    includeDemo = demo,
                                    demoMeta = LIFE_DEMO_META
                                )
                            }
                        }
                        val todoRowsFlow = if (kind != LifeTemplateKind.TODO) {
                            flowOf(emptyList())
                        } else {
                            itemDao.getTodoMetricRows(item.templateId)
                        }
                        val schoolFieldsFlow = if (kind != LifeTemplateKind.STUDY) {
                            flowOf(emptyList())
                        } else {
                            preferences.lifeDemoMode.flatMapLatest { demo ->
                                itemDao.getFieldsDataBetween(item.templateId, 0L, Long.MAX_VALUE, demo, LIFE_DEMO_META)
                            }
                        }
                        val moodFieldsFlow = if (kind != LifeTemplateKind.MOOD) {
                            flowOf(emptyList())
                        } else {
                            preferences.lifeDemoMode.flatMapLatest { demo ->
                                val (start, end) = monthWindow()
                                itemDao.getFieldsDataBetween(item.templateId, start, end, demo, LIFE_DEMO_META)
                            }
                        }
                        val moodDaysFlow = if (kind != LifeTemplateKind.MOOD) {
                            flowOf(emptyList())
                        } else {
                            preferences.lifeDemoMode.flatMapLatest { demo ->
                                itemDao.getDistinctRecordDays(item.templateId, demo, LIFE_DEMO_META)
                            }
                        }
                        val relationsFlow = crossLinkRepository.getLinksBySource(EntityType.ITEM, itemId)
                        combine(
                            dayRowsFlow,
                            daysFlow,
                            checkInMonthCountFlow,
                            checkInTotalFlow,
                            bookDaysFlow,
                            focusSessionsFlow,
                            todoRowsFlow,
                            schoolFieldsFlow,
                            moodFieldsFlow,
                            moodDaysFlow,
                            relationsFlow
                        ) { values ->
                            val dayRows = values[0] as List<LifeItemDao.TemplateDayRow>

                            @Suppress("UNCHECKED_CAST")
                            val days = values[1] as List<String>
                            val checkInMonthCount = values[2] as Int
                            val checkInTotal = values[3] as Int

                            @Suppress("UNCHECKED_CAST")
                            val bookDays = values[4] as List<String>

                            @Suppress("UNCHECKED_CAST")
                            val focusRows = values[5] as List<LifeItemDao.FocusSessionRow>

                            @Suppress("UNCHECKED_CAST")
                            val todoRows = values[6] as List<LifeItemDao.TodoMetricRow>

                            @Suppress("UNCHECKED_CAST")
                            val schoolFields = values[7] as List<String>

                            @Suppress("UNCHECKED_CAST")
                            val moodFields = values[8] as List<String>

                            @Suppress("UNCHECKED_CAST")
                            val moodDays = values[9] as List<String>
                            val links = values[10] as List<com.palmnote.data.db.entity.CrossLink>
                            val multiSelectCounts = multiSelectCountsOf(dayRows)
                            val today = LocalDate.now()
                            val todayKey = today.toString()
                            val todaySessions = focusSessionEntries(focusRows, todayKey)
                            val todayDurationMs = focusDayDurationMs(focusRows, todayKey)
                            val weekBars = focusWeekBars(focusRows, today.with(DayOfWeek.MONDAY))
                            val streakResult = if (kind == LifeTemplateKind.HABIT) StreakEngine.compute(days) else null
                            val todoMetrics = if (kind == LifeTemplateKind.TODO) todoMetricsOf(todoRows, today) else null
                            val schoolMetrics = if (kind == LifeTemplateKind.STUDY) schoolMetricsOf(schoolFields) else null
                            val moodStreak = if (kind == LifeTemplateKind.MOOD) StreakEngine.compute(moodDays).current else null
                            val todayDone = days.contains(todayKey)
                            DetailUiState.Ready(
                                DetailAssembler.assemble(
                                    item,
                                    template,
                                    dayRows,
                                    aggregates = DetailAggregates(
                                    checkInMonthCount = checkInMonthCount.takeIf { kind == LifeTemplateKind.HABIT },
                                    checkInLongest = streakResult?.longest,
                                    checkInTotal = checkInTotal.takeIf { kind == LifeTemplateKind.HABIT },
                                    checkInConsistency = StreakEngine.consistency(days)
                                        .takeIf { kind == LifeTemplateKind.HABIT },
                                        todoToday = todoMetrics?.today,
                                        todoOverdue = todoMetrics?.overdue,
                                        todoDoneThisWeek = todoMetrics?.doneThisWeek,
                                        bookMonthCount = dayRows.size
                                            .takeIf { kind == LifeTemplateKind.JOURNAL },
                                        bookStreak = StreakEngine.compute(bookDays).current
                                            .takeIf { kind == LifeTemplateKind.JOURNAL },
                                        bookPhotoCount = dayRows.sumOf { row -> detailJsonObject(row.fieldsData).photoCount() }
                                            .takeIf { kind == LifeTemplateKind.JOURNAL },
                                        schoolTotalMinutes = schoolMetrics?.totalMinutes,
                                        schoolCompletedLessons = schoolMetrics?.completedLessons,
                                        moodMonthAverage = moodFields.mapNotNull { energyOf(it) }
                                            .takeIf { it.isNotEmpty() }
                                            ?.average()
                                            ?.takeIf { kind == LifeTemplateKind.MOOD },
                                        moodStreak = moodStreak,
                                        focusTodayMs = todayDurationMs.takeIf { kind == LifeTemplateKind.FOCUS },
                                        focusWeekMs = weekBars.sumOf { it.durationMs }
                                            .takeIf { kind == LifeTemplateKind.FOCUS },
                                        focusSessions = todaySessions,
                                        focusWeekBars = weekBars,
                                        multiSelectCounts = multiSelectCounts
                                    ),
                                    streakOverride = streakResult?.current,
                                    checkInTodayDone = todayDone,
                                    relationCounts = relationCountsOf(links),
                                    context = appContext
                                )
                            )
                        }
                    }
                }
            }
        }
        .catchLife("detail.state", DetailUiState.Error)
        // 装配要解析整条记录的 JSON 并逐字段建行（DetailAssembler），放到 Default 上做
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState.Loading)

    /** 待办清单就地勾选（就地操作之一，另见 [toggleTableCell] / [setProgress]）。 */
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
            WidgetUpdateHelper.refreshTodoWidgets()
        }
    }

    /**
     * 进度**就地推进**（详情页与 hero 的 −/+ 按钮）：把 [fieldKey] 的数值设为 [value]。
     *
     * 走**仓储**而不是像 [toggleChecklist] 那样直连 DAO：仓储那条路径会带上执行列镜像与
     * `field_values` 双写（总纲 §7.1），而这里改的正是「被当作进度分子」的数值字段，
     * 绕过双写会留下统计表与主表不一致的快照。
     *
     * 与 [nudgeProgress] 共用一把锁：详情页上"点数值就地编辑"与 `±` 是两条相邻入口，
     * 写入必须**排序**，否则后写的会用旧值覆盖前一笔。
     */
    fun setProgress(fieldKey: String, value: Double) {
        viewModelScope.launch {
            progressWriteLock.withLock {
                val item = itemDao.getItemById(itemId) ?: return@withLock
                repository.updateFieldsData(itemId, setNumericField(item.fieldsData, fieldKey, value))
            }
        }
    }

    /**
     * 进度**相对推进**：在写入那一刻读库里的最新值再加 [delta]，而不是用界面上的旧值算绝对值。
     *
     * 起因是审查发现的一个真实覆盖：用户点数值就地编辑、敲完数字后紧接着点 `±` ——
     * 失焦提交是异步写库，而 `±` 若拿界面旧值算绝对值，就会**把刚提交的数字覆盖掉**。
     * 边界钳制交给 [setNumericField]（单一真源）。
     */
    fun nudgeProgress(fieldKey: String, delta: Double, min: Double?, max: Double?) {
        viewModelScope.launch {
            progressWriteLock.withLock {
                val item = itemDao.getItemById(itemId) ?: return@withLock
                val current = numericFieldOf(item.fieldsData, fieldKey)
                // 按步长取整：页 / 节 / 天这类**计数**不该被加出小数（真机反馈的 164.5 页 / 9.2 节），
                // 顺带把旧步长留下的历史小数修正回整数。
                val next = snapNudged(current + delta, kotlin.math.abs(delta))
                repository.updateFieldsData(
                    itemId,
                    setNumericField(item.fieldsData, fieldKey, next, min, max)
                )
            }
        }
    }

    /**
     * 单个字段**就地编辑**（详情页「点值即改」，见 `LifeQuickEdit`）。
     *
     * 编码口径走 [applyFieldValue] —— 与表单保存**同一个** `encodeFieldValue`，
     * 所以就地改一个日期与进编辑页改一个日期，落库形态逐字节一致。
     */
    fun setFieldValue(fieldKey: String, raw: String) {
        viewModelScope.launch {
            val item = itemDao.getItemById(itemId) ?: return@launch
            val template = templateDao.getTemplateById(item.templateId) ?: return@launch
            val cfg = runCatching { Json.decodeFromString<List<FieldConfig>>(template.fieldsConfig) }
                .getOrDefault(emptyList())
                .firstOrNull { it.key == fieldKey } ?: return@launch
            repository.updateFieldsData(itemId, applyFieldValue(item.fieldsData, cfg, raw))
        }
    }

    /**
     * 明细表单元格**就地勾选**（购物「已买」这类 BOOLEAN 列）。
     *
     * 判定与写入约定都收在 `LifeTableFieldKit.isTickCell` / `tickCellValue` 一处，
     * 与「已买 N / M」指标共用 —— 否则「勾选框写的值」与「统计读的值」会再次分叉。
     */
    fun toggleTableCell(fieldKey: String, rowIndex: Int, colIndex: Int) {
        viewModelScope.launch {
            val item = itemDao.getItemById(itemId) ?: return@launch
            val next = toggleTableCellValue(item.fieldsData, fieldKey, rowIndex, colIndex) ?: return@launch
            repository.updateFieldsData(itemId, next)
        }
    }

    /** 删除这条记录（「⋯」菜单里唯一动作；调用方负责二次确认与返回）。 */
    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            itemDao.deleteItemCascade(itemId)
            WidgetUpdateHelper.refreshTodoWidgets()
            WidgetUpdateHelper.refreshCounterWidgets()
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
            val rows = existing.mapNotNull { itemDao.getItemById(it.itemId) }
            val active = rows.filter { it.status != "ARCHIVED" }
            if (active.isNotEmpty()) {
                // 撤销 = 归档今天的打卡行（软撤销）：当天填过的字段数据保留，
                // 再点一次「今日打卡」即原样恢复 —— 不再 deleteItemCascade 连带删数据。
                active.forEach { itemDao.updateStatus(it.id, "ARCHIVED") }
                WidgetUpdateHelper.refreshTodoWidgets()
                WidgetUpdateHelper.refreshCounterWidgets()
            } else {
                val archived = rows.filter { it.status == "ARCHIVED" }
                if (archived.isNotEmpty()) {
                    // 今天已有被撤销过的行 → 复活（保留原字段数据），不重复插行
                    archived.forEach { itemDao.updateStatus(it.id, "ACTIVE") }
                    WidgetUpdateHelper.refreshTodoWidgets()
                    WidgetUpdateHelper.refreshCounterWidgets()
                } else {
                    val now = System.currentTimeMillis()
                    itemDao.insertItem(
                        LifeItem(
                            templateId = item.templateId,
                            // 新建记录的标题取自模板名：内置模板要走 getDisplayName，
                            // 否则英文界面下新建出来的记录标题是中文。
                            title = template.getDisplayName(appContext),
                            fieldsData = "{}",
                            status = "ACTIVE",
                            createdAt = now,
                            updatedAt = now,
                            dueDate = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            meta = if (demo) LIFE_DEMO_META else null
                        )
                    )
                    WidgetUpdateHelper.refreshTodoWidgets()
                    WidgetUpdateHelper.refreshCounterWidgets()
                    celebrateMilestoneIfReached(item.templateId, demo)
                }
            }
        }
    }

    /**
     * 打卡目标达成的一次性庆祝事件（连续天数恰等 targetDays 时发；UI 收集后展示浮层）。
     * 只挂在全新打卡路径：撤销复活不重放；连续天数跳过目标（漏卡后追上）也不庆祝。
     */
    private val _milestoneEvents = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val milestoneEvents: SharedFlow<Int> = _milestoneEvents

    private suspend fun celebrateMilestoneIfReached(templateId: Long, demo: Boolean) {
        val template = templateDao.getTemplateById(templateId) ?: return
        val json = Json { ignoreUnknownKeys = true }
        val target = runCatching { json.decodeFromString<List<FieldConfig>>(template.fieldsConfig) }
            .getOrNull()
            ?.firstOrNull { it.key == "targetDays" }
            ?.defaultValue?.toDoubleOrNull()
            ?.takeIf { it > 0 }
            ?.toInt()
            ?: return
        val days = itemDao.getDistinctCheckInDays(templateId, demo, LIFE_DEMO_META).first()
        val streak = StreakEngine.compute(days).current
        if (streak == target) _milestoneEvents.tryEmit(streak)
    }

    /**
     * 专注计时写会话：停止计时时由英雄区回调，插一条会话记录（duration = 毫秒）。
     * 专注模板无字段（§5.1），会话时长即它的全部数据；统计页按此聚合今日 / 本周时长。
     */
    /** 专注计时器持久状态：startAt > 0 = 计时中（离屏/杀进程后仍在走）。 */
    data class FocusTimerState(val startAt: Long = 0L, val accumMs: Long = 0L) {
        val running: Boolean get() = startAt > 0
    }

    val focusTimerState: StateFlow<FocusTimerState> = combine(
        preferences.lifeFocusTimerStartAt,
        preferences.lifeFocusTimerAccumMs
    ) { startAt, accum -> FocusTimerState(startAt, accum) }
        .catchLife("detail.focusTimer", FocusTimerState())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusTimerState())

    /** 从零起跑。 */
    fun startFocusTimer() {
        viewModelScope.launch {
            preferences.setFocusTimerAccum(0L)
            preferences.setFocusTimerRunning(System.currentTimeMillis())
        }
    }

    /** 暂停后继续（累计时长保留）。 */
    fun resumeFocusTimer() {
        viewModelScope.launch {
            preferences.setFocusTimerRunning(System.currentTimeMillis())
        }
    }

    /** 暂停：把当前一段并入累计、清 startAt。 */
    fun pauseFocusTimer() {
        viewModelScope.launch {
            val s = focusTimerState.value
            if (s.running) {
                preferences.setFocusTimerAccum(s.accumMs + (System.currentTimeMillis() - s.startAt))
                preferences.setFocusTimerRunning(0L)
            }
        }
    }

    fun saveFocusSession(durationMs: Long) {
        if (durationMs <= 0) return
        viewModelScope.launch {
            // 落会话即归零计时器（持久层：防离屏后残留 startAt）
            preferences.setFocusTimerRunning(0L)
            preferences.setFocusTimerAccum(0L)
            val item = itemDao.getItemById(itemId) ?: return@launch
            val template = templateDao.getTemplateById(item.templateId) ?: return@launch
            val demo = preferences.lifeDemoMode.first()
            val now = System.currentTimeMillis()
            itemDao.insertItem(
                LifeItem(
                    templateId = item.templateId,
                    title = item.title,
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

internal fun durationMsOf(raw: String): Long = runCatching {
    (Json.decodeFromString<JsonObject>(raw)["duration"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
}.getOrDefault(0L)

/** ④ 页脚关联计数：只统计来源记录明确关联到的账单和笔记。 */
internal fun relationCountsOf(links: List<com.palmnote.data.db.entity.CrossLink>): RelationCounts {
    var bills = 0
    var notes = 0
    links.forEach { link ->
        when (link.targetType) {
            EntityType.BILL -> bills++
            EntityType.NOTE -> notes++
            else -> Unit
        }
    }
    return RelationCounts(bills, notes)
}


/** 订阅费用统一折算：月支出把季付 ÷3、年付 ÷12，未知周期沿用月付。 */
internal fun subscriptionMonthlyCost(price: Double, cycle: String?): Double = price * when (cycle) {
    "quarterly" -> 1.0 / 3.0
    "yearly" -> 1.0 / 12.0
    else -> 1.0
}

/** 订阅费用统一折算：年支出把月付 ×12、季付 ×4，未知周期沿用月付。 */
internal fun subscriptionYearlyCost(price: Double, cycle: String?): Double = price * when (cycle) {
    "quarterly" -> 4.0
    "yearly" -> 1.0
    else -> 12.0
}

/** 某天的专注时长：直接汇总原始毫秒，避免分钟截断后与本周柱图产生不同口径。 */
internal fun focusDayDurationMs(
    rows: List<LifeItemDao.FocusSessionRow>,
    day: String
): Long = rows
    .asSequence()
    .filter { it.day == day }
    .map { durationMsOf(it.fieldsData) }
    .filter { it > 0L }
    .sum()

/** 某天的专注会话：只保留有正时长的记录，按创建时间升序。 */
internal fun focusSessionEntries(
    rows: List<LifeItemDao.FocusSessionRow>,
    day: String
): List<FocusSessionEntry> = rows
    .asSequence()
    .filter { it.day == day }
    .mapNotNull { row ->
        val durationMs = durationMsOf(row.fieldsData)
        if (durationMs <= 0L) return@mapNotNull null
        FocusSessionEntry(
            minutes = (durationMs / 60_000L).toInt(),
            title = row.title,
            time = FOCUS_TIME_FORMATTER.format(
                Instant.ofEpochMilli(row.createdAt).atZone(ZoneId.systemDefault())
            )
        )
    }
    .toList()

/** 本周周一至周日的时长；零记录也保留一格，由 UI 画灰色最小柱。 */
internal fun focusWeekBars(
    rows: List<LifeItemDao.FocusSessionRow>,
    weekStart: LocalDate
): List<FocusWeekBar> {
    val totals = rows
        .filter { durationMsOf(it.fieldsData) > 0L }
        .groupBy { it.day }
        .mapValues { (_, dayRows) -> dayRows.sumOf { durationMsOf(it.fieldsData) } }
    return (0L..6L).map { offset ->
        val day = weekStart.plusDays(offset)
        FocusWeekBar(dayOfWeek = day.dayOfWeek, durationMs = totals[day.toString()] ?: 0L)
    }
}

/** 待办指标：今天到期、已逾期、本周完成；只统计未归档记录。 */
internal data class TodoMetrics(val today: Int, val overdue: Int, val doneThisWeek: Int)

internal fun todoMetricsOf(rows: List<LifeItemDao.TodoMetricRow>, today: LocalDate): TodoMetrics {
    val zone = ZoneId.systemDefault()
    val weekStart = today.with(DayOfWeek.MONDAY)
    fun LocalDate.isInCurrentWeek(): Boolean = !isBefore(weekStart) && !isAfter(today)
    var todayCount = 0
    var overdue = 0
    var doneThisWeek = 0
    rows.forEach { row ->
        val due = row.dueDate?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val done = row.status.equals("COMPLETED", ignoreCase = true)
        if (!done) {
            if (due != null && due == today) todayCount++
            if (due != null && due.isBefore(today)) overdue++
        } else {
            val updated = Instant.ofEpochMilli(row.updatedAt).atZone(zone).toLocalDate()
            if (updated.isInCurrentWeek()) doneThisWeek++
        }
    }
    return TodoMetrics(todayCount, overdue, doneThisWeek)
}

internal data class SchoolMetrics(val totalMinutes: Double?, val completedLessons: Double?)

/** 学习聚合：累计时长与完成节数；没有对应字段的记录不参与汇总。 */
internal fun schoolMetricsOf(rows: List<String>): SchoolMetrics {
    val objects = rows.map(::detailJsonObject)
    val durations = objects.mapNotNull { obj ->
        (obj["duration"] as? JsonPrimitive)?.content?.toDoubleOrNull()
    }
    val completedLessons = objects.mapNotNull { obj ->
        (obj["completedLessons"] as? JsonPrimitive)?.content?.toDoubleOrNull()
    }
    return SchoolMetrics(
        totalMinutes = durations.takeIf { it.isNotEmpty() }?.sum(),
        completedLessons = completedLessons.takeIf { it.isNotEmpty() }?.max()
    )
}

/** 心情聚合：本月 energy 平均值。 */
internal fun energyOf(raw: String): Double? = (detailJsonObject(raw)["energy"] as? JsonPrimitive)?.content?.toDoubleOrNull()

private val FOCUS_TIME_FORMATTER: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.US)

internal fun JsonObject.photoCount(): Int = when (val value = this["photos"]) {
    is JsonArray -> value.size
    is JsonPrimitive -> value.contentOrNull?.split(',')?.count { it.isNotBlank() } ?: 0
    else -> 0
}

private fun detailJsonObject(raw: String): JsonObject = runCatching { Json.decodeFromString<JsonObject>(raw) }
    .getOrNull()
    ?: JsonObject(emptyMap())

/**
 * 多选字段的**真实出现次数**：该模板下所有记录的 `MULTI_SELECT` 取值各被选了几次。
 *
 * 数据源就是详情页已在观察的 [LifeItemDao.TemplateDayRow]（同一窗口、同一批记录），
 * **零额外查询**；数组与逗号串两种历史载荷都按 [detailJsonObject] + 列表解析统一处理。
 * 用来把「分类占比」堆叠条从「均分」换成真实占比；无记录时返回空表（不编数字）。
 */
internal fun multiSelectCountsOf(rows: List<LifeItemDao.TemplateDayRow>): Map<String, Map<String, Int>> {
    val out = mutableMapOf<String, MutableMap<String, Int>>()
    rows.forEach { row ->
        val obj = detailJsonObject(row.fieldsData)
        obj.forEach { (key, element) ->
            val values = when (element) {
                is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                is JsonPrimitive -> element.contentOrNull?.split(',')
                else -> null
            }?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
            if (values.isNotEmpty()) {
                val bucket = out.getOrPut(key) { mutableMapOf() }
                values.forEach { v -> bucket[v] = (bucket[v] ?: 0) + 1 }
            }
        }
    }
    return out
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
        checkInTodayDone: Boolean = false,
        /** 详情页 ④：来源记录关联的账单 / 笔记数量。 */
        relationCounts: RelationCounts = RelationCounts(),
        aggregates: DetailAggregates = DetailAggregates(),
        /**
         * 只用于把内置模板的字段名 / 单位翻成当前语言（`BuiltinFieldText`）。
         * 可空是为了让装配逻辑在测试里保持**无 Context 的纯函数**形态。
         */
        context: Context? = null
    ): DetailUi {
        val configs = parseConfigs(template.fieldsConfig, context)
        val baseObj = runCatching { JSON.decodeFromString<JsonObject>(item.fieldsData) }
            .getOrNull() ?: JsonObject(emptyMap())
        // 连击覆盖：把计算出的真实连击写回 currentStreak，英雄区 / 指标 / 进度全部随之更新。
        val obj = if (streakOverride != null) {
            JsonObject(baseObj.toMutableMap().apply { put("currentStreak", JsonPrimitive(streakOverride)) })
        } else {
            baseObj
        }
        val ctx = DetailCtx(item, template, configs, obj, identityColor(template.color), aggregates)
        val consumed = consumedKeys(ctx)
        val metrics = metricsOf(ctx)
        val metricKeys = metrics.map { it.key }.toSet()
        val visible = configs
            .sortedBy { it.sortOrder }
            .filter { it.key !in consumed && it.key !in metricKeys }
        // ③ **按字段的「手」分组**（§14.2）：同一种手进同一张卡，组标题是手名（`life_detail_group_*`）。
        //
        // 此前是"一字段一张卡 + 组标题 = 字段名"（依据设计稿 dtl_01–16 的卡内小标题），
        // 结果**组标题与卡内标签重复一遍**、卡片又多又重。2026-10-01 用户定案改为按手分组：
        // 手名文案本来就在 strings 里（此前只有旅行用到），且这正是 §14.2 的原文要求。
        val fieldGroups = templateFieldGroups(ctx, visible)
        // 模板专属块（热力 / 时间线）排在字段组**之前**：它们是这一屏的主体（§14.11 #11/#12/#13）。
        return DetailUi(
            ctx = ctx,
            metrics = metrics,
            groups = templateExtraGroups(ctx, dayRows) + fieldGroups,
            checkInTodayDone = checkInTodayDone,
            relationCounts = relationCounts
        )
    }

    /**
     * ③ 字段组：默认**一字段一组**（照设计稿 dtl_01–16 实测：每张卡带自己的小标题）。
     *
     * 例外：旅行计划 dtl_04 把「同行人」与「预算 / 已订」合并成一张卡
     * 「同行人与预算」，把「照片」单列为「相册」；「行程明细」照常独立。这是设计稿的
     * 显式分组（gen_detail_svg.py 第 476–478 行），不是按手名泛化。
     */
    private fun templateFieldGroups(ctx: DetailCtx, visible: List<FieldConfig>): List<DetailGroup> {
        if (ctx.template.getKind() != LifeTemplateKind.TRAVEL) {
            // §14.2：按**字段的「手」**分组（同一种手进同一张卡，组标题是手名）。
            // 此前是"一字段一张卡 + 组标题 = 字段名"：组标题与卡内标签**重复一遍**，
            // 一屏下来卡片又多又重（真机反馈"排版 / 布局不行"）。
            // 手名文案本来就躺在 strings 里（`life_detail_group_*`），此前只有旅行用到。
            // 组顺序 = 该手**首次出现**的字段顺序（groupBy 是 LinkedHashMap，不额外排序）。
            return visible
                .groupBy { handOf(it) }
                .mapNotNull { (hand, cfgs) ->
                    val rows = cfgs.flatMap { buildRows(ctx, it) }
                    if (rows.isEmpty()) null else DetailGroup(rows = rows, title = "", titleRes = hand.titleRes)
                }
        }
        // dtl_04 只画这三组；其余可见字段（如「返程日期」）设计稿里没有，
        // 但它是**真实字段**，不属于展示白名单就整条不出现 —— 不伪造、也不多画。
        val byKey = visible.filter { it.key in FLIGHT_DETAIL_KEYS }.associateBy { it.key }
        fun groupOf(titleRes: Int, vararg keys: String): DetailGroup? {
            val rows = keys.flatMap { key -> byKey[key]?.let { buildRows(ctx, it) }.orEmpty() }
            return if (rows.isEmpty()) null else DetailGroup(rows = rows, title = "", titleRes = titleRes)
        }
        return listOfNotNull(
            groupOf(R.string.life_detail_group_itinerary, "itinerary"),
            groupOf(R.string.life_detail_group_companions_budget, "companions", "booked"),
            groupOf(R.string.life_detail_group_album, "photos")
        )
    }

    /** 字段的「手」（§3.2 的七种）：决定它进哪一张结构卡。 */
    private enum class Hand(val titleRes: Int) {
        COMPOUND(R.string.life_detail_group_compound),
        MEDIA(R.string.life_detail_group_media),
        NUMERIC(R.string.life_detail_group_numeric),
        CHOICE(R.string.life_detail_group_choice),
        TEXT(R.string.life_detail_group_text),
        DERIVED(R.string.life_detail_group_derived)
    }

    private fun handOf(cfg: FieldConfig): Hand = when (cfg.type) {
        FieldType.CHECKLIST, FieldType.TABLE, FieldType.RANGE -> Hand.COMPOUND
        FieldType.IMAGE, FieldType.VIDEO, FieldType.AUDIO, FieldType.FILE,
        FieldType.MAP, FieldType.LOCATION, FieldType.COLOR -> Hand.MEDIA
        FieldType.SELECT, FieldType.MULTI_SELECT, FieldType.TAG, FieldType.PERSON,
        FieldType.BOOLEAN -> Hand.CHOICE
        FieldType.FORMULA, FieldType.REMAINING, FieldType.STREAK, FieldType.ELAPSED -> Hand.DERIVED
        FieldType.NUMBER, FieldType.CURRENCY, FieldType.PERCENT, FieldType.PERCENTAGE,
        FieldType.RATING, FieldType.SLIDER, FieldType.DURATION,
        FieldType.DATE, FieldType.TIME, FieldType.DATETIME -> Hand.NUMERIC
        else -> Hand.TEXT
    }

    private val FLIGHT_DETAIL_KEYS = setOf("itinerary", "companions", "booked", "photos")

    /**
     * 已被 ① 英雄区消费的字段 key —— 避免同一个值在页面上出现两次。
     * 逐模板标定（与 16 张设计稿一一对应）。
     */
    private fun consumedKeys(ctx: DetailCtx): Set<String> = when (ctx.template.icon) {
        "savings" -> setOf("targetAmount", "currentAmount", "remain")
        // 购物：photo/items 是退役字段（定案 20 + 种子 v5）——数据保留在 DB，
        // 但不再进结构区；对已自定义、拿不到新种子的存量安装做兜底隐藏。
        "shopping_cart" -> setOf("budget", "spent", "photo", "items")
        "school" -> setOf("completedLessons", "totalLessons")
        "calendar_month" -> setOf("currentStreak", "targetDays")
        "subscriptions" -> setOf("price", "nextBilling", "daysToBilling", "billingDay")
        "fitness_center" -> setOf("weight")
        "flight" -> setOf("destination", "startDate", "route")
        "menu_book" -> setOf("totalPages", "currentPage", "author", "cover")
        // 截止只以英雄区 chip 呈现（dtl_03），不单列结构组；
        // billingDay 供提醒 Worker 用（Worker 里按号数算扣费日），不单列结构组。
        "checklist" -> setOf("subtasks", "deadline")
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

        fun m(key: String, value: String?): DetailMetric? = value?.let { DetailMetric(key, ctx.cfg(key)?.label ?: key, it) }

        /** 派生指标：名字来自文案（不是字段），值自己算。 */
        fun d(key: String, labelRes: Int, value: String?, formatRes: Int = 0): DetailMetric? = value?.let {
            DetailMetric(key, value = it, labelRes = labelRes, formatRes = formatRes)
        }

        // 照设计稿 dtl_01–16：**每张指标行都是 3 格**，值 15sp/700 + 标签 10sp。
        // 此前只放「算得出的」导致常出现 1–2 格，与图不符 ⟹ 这里尽量凑满 3 格。
        val kept = keptDays(ctx)
        val list = when (ctx.template.icon) {
            // dtl_01 存钱：已存 / 日均 / 已坚持
            "savings" -> listOfNotNull(
                m("currentAmount", ctx.num("currentAmount")?.let { fmtMoney(it) }),
                d(
                    "daily",
                    R.string.life_metric_daily,
                    ctx.num("currentAmount")?.let { cur ->
                        dailyOf(cur, kept)?.let { fmtMoney(it) }
                    }
                ),
                d("kept", R.string.life_metric_kept, kept?.toString())
            )
            // dtl_02 购物：已买 / 分类 / 店铺
            "shopping_cart" -> listOfNotNull(
                d("bought", R.string.life_metric_bought, boughtText(ctx)),
                m("category", ctx.list("category").size.takeIf { n -> n > 0 }?.toString()),
                m("store", ctx.str("store"))
            )
            // dtl_03 待办：今天 / 逾期 / 本周完成
            "checklist" -> listOfNotNull(
                d("today", R.string.life_metric_today, ctx.aggregates.todoToday?.toString()),
                d("overdue", R.string.life_metric_overdue, ctx.aggregates.todoOverdue?.toString()),
                d("doneWeek", R.string.life_metric_done_this_week, ctx.aggregates.todoDoneThisWeek?.toString())
            )
            // dtl_04 旅行：行程 / 同行 / 预算
            "flight" -> listOfNotNull(
                // 指标 key 用 `metric_` 前缀：② 与 ③ 允许同值（预算在两处都出现），
                // 若沿用字段 key，`metricKeys` 会把字段从 ③ 剔掉，D04 的「同行人与预算」组就空了。
                d("metric_itinerary", R.string.life_metric_itinerary, tableRowCount(ctx).takeIf { n -> n > 0 }?.toString()),
                d(
                    "metric_companions",
                    R.string.life_metric_companions,
                    ctx.list("companions").size.takeIf { n -> n > 0 }?.toString()
                ),
                d("metric_budget", R.string.life_metric_budget, ctx.num("budget")?.let { fmtMoney(it) })
            )
            // dtl_05 阅读：已读页 / 剩余页 / 日均
            "menu_book" -> listOfNotNull(
                m("currentPage", withUnit(ctx, "currentPage")),
                d("left", R.string.life_metric_left, leftText(ctx, "totalPages", "currentPage")),
                d(
                    "daily",
                    R.string.life_metric_daily,
                    ctx.num("currentPage")?.let { cur ->
                        dailyOf(cur, kept)?.let { "${fmtNumber(it)} ${ctx.cfg("currentPage")?.unit ?: ""}".trim() }
                    }
                )
            )
            // dtl_06 学习：单次时长 / 累计时长 / 评分（稿第三格是评分；完成节数由英雄区承担）
            "school" -> listOfNotNull(
                m("duration", withUnit(ctx, "duration")),
                d(
                    "totalDuration",
                    R.string.life_metric_total_duration,
                    ctx.aggregates.schoolTotalMinutes?.let { v ->
                        // 单位与「单次时长」一致：真机截图里一格是「45 分钟」、另一格只有「45」
                        ctx.cfg("duration")?.let { c -> ctx.format(c, v.toString()) } ?: fmtNumber(v)
                    }
                ),
                // 与 ③ 结构区的评分行口径一致（那里是「4 / 5」）：指标行不能只给一个「4」
                m("rating", ctx.num("rating")?.let { "${fmtNumber(it)} / 5" })
            )
            // dtl_11 打卡：本月 / 最长 / 累计
            "calendar_month" -> listOfNotNull(
                d("month", R.string.life_metric_month, ctx.aggregates.checkInMonthCount?.toString(), R.string.life_metric_count_times),
                d("longest", R.string.life_metric_longest, ctx.aggregates.checkInLongest?.toString(), R.string.life_metric_streak_days),
                d("total", R.string.life_metric_total, ctx.aggregates.checkInTotal?.toString(), R.string.life_metric_count_times)
            )
            // dtl_14 订阅：月支出 / 年支出 / 下次扣费
            "subscriptions" -> {
                val price = ctx.num("price")
                val cycle = ctx.str("billingCycle")
                listOfNotNull(
                    d(
                        "monthlyExpense",
                        R.string.life_metric_monthly_expense,
                        price?.let { fmtMoney(subscriptionMonthlyCost(it, cycle)) }
                    ),
                    d(
                        "yearlyExpense",
                        R.string.life_metric_yearly_expense,
                        price?.let { fmtMoney(subscriptionYearlyCost(it, cycle)) }
                    ),
                    m("nextBilling", ctx.dateText("nextBilling"))
                )
            }
            "fitness_center" -> listOfNotNull(
                m("weight", withUnit(ctx, "weight")),
                m("sleep", withUnit(ctx, "sleep")),
                m("bmi", ctx.cfg("bmi")?.let { ctx.derived(it) }?.let { fmtNumber(it) })
            )
            "mood" -> listOfNotNull(
                d(
                    "monthAverage",
                    R.string.life_metric_month_average,
                    // dtl_12：月均值带情绪表情（MoodVisuals 单一真源）；数字不再锁 locale
                    ctx.aggregates.moodMonthAverage?.let { MoodVisuals.emojiOf(ctx.str("mood")) + " " + fmtNumber(it) }
                ),
                // 与打卡 / 日记的同类指标一致：连续天数带「天」（此前心情这里只有裸数字）
                d("streak", R.string.life_metric_streak, ctx.aggregates.moodStreak?.toString(), R.string.life_metric_streak_days)
            )
            "book" -> listOfNotNull(
                ctx.aggregates.bookMonthCount?.let {
                    DetailMetric(
                        "month",
                        value = it.toString(),
                        labelRes = R.string.life_metric_month,
                        formatRes = R.string.life_metric_month_pieces
                    )
                },
                ctx.aggregates.bookStreak?.let {
                    DetailMetric(
                        "streak",
                        value = it.toString(),
                        labelRes = R.string.life_metric_streak,
                        formatRes = R.string.life_metric_streak_days
                    )
                },
                ctx.aggregates.bookPhotoCount?.let {
                    DetailMetric(
                        "photos",
                        value = it.toString(),
                        labelRes = R.string.life_metric_photos,
                        formatRes = R.string.life_metric_photos_count
                    )
                }
            )
            "timer" -> listOfNotNull(
                d("focusToday", R.string.life_metric_today, ctx.aggregates.focusTodayMs?.let { fmtDuration(it) }),
                d("focusWeek", R.string.life_metric_week, ctx.aggregates.focusWeekMs?.let { fmtDuration(it) })
            )
            "build" -> listOfNotNull(m("cycle", withUnit(ctx, "cycle")))
            else -> emptyList()
        }
        // 用户定案（2026-10-01）：**指标行不重复英雄区已经显示的东西** ——
        // hero 的进度字段（存钱 currentAmount / 阅读 currentPage …）不进指标行，
        // 只留 hero 没有的（日均 / 已坚持 / 目标日 …）。
        // 真机截图里阅读页「当前页数 164.5 页」与 hero 的环 + 计数**完全重复**。
        val heroKey = ctx.configs.firstOrNull { it.showAsProgress && !it.disabled }?.key
        return list.filter { it.key != heroKey }.take(3)
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
    private fun dailyOf(current: Double, kept: Int?): Double? = kept?.takeIf { it > 0 }?.let { current / it }

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

    /** 购物「已买 / 总数」：明细表行里出现 true / ✓ 即算已买（判定与勾选框共用 `isTickCell`）。 */
    private fun boughtText(ctx: DetailCtx): String? {
        val cfg = ctx.configs.firstOrNull { it.type == FieldType.TABLE } ?: return null
        val rows = buildRows(ctx, cfg).filterIsInstance<DetailRowModel.TableRows>()
            .firstOrNull()?.rows ?: return null
        if (rows.isEmpty()) return null
        val bought = rows.count { cells -> cells.any { isTickCell(it) } }
        return "$bought / ${rows.size}"
    }

    private fun parseConfigs(raw: String, context: Context?): List<FieldConfig> {
        val configs = runCatching { JSON.decodeFromString<List<FieldConfig>>(raw) }.getOrNull().orEmpty()
        return (context?.let { BuiltinFieldText.localizeConfigs(it, configs) } ?: configs)
            .filter { !it.disabled }
    }
}
