package com.palmnote.ui.life

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.DemoDataSeeder
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.ui.widget.WidgetUpdateHelper
import com.palmnote.data.db.dao.LIFE_DEMO_META
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.domain.util.QuickEntryParser
import com.palmnote.domain.util.BuiltinTemplates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * 生活页数据源（首页三卡：分类卡 / 今日看板 / 待安排卡）。
 *
 * **数据一律来自数据库**，没有画在界面上的假数据：
 * - 演示模式开启（默认）：先确保示例数据已播种（[LifeDemoSeeder]，真实 `life_items` 行，
 *   可查看 / 编辑 / 删除），查询时**包含**这些行；
 * - 演示模式关闭：查询时**排除**带示例标记的行，只看用户自己的记录。
 *
 * 日历 / 看板口径：**只认滚动后的到期日**（[LifeBoardRows]：普通条目即 `dueDate`，
 * 「每年重复」滚到下一次周年）+ 本地时区（防跨时区错日）。
 * 打卡 / 心情 / 日记这类模板里**没有日期字段**的记录，写入时已由
 * `LifeItemRepositoryImpl.recordFallbackDueDate` 把 `dueDate` 兜底为**记录发生当天**，
 * 所以它们同样会落到日历格与当日看板上，两个消费方口径一致（见 [calendarDayMap]）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LifeCalendarViewModel @Inject constructor(
    private val preferences: PreferencesManager,
    private val lifeItemDao: LifeItemDao,
    private val templateDao: LifeTemplateDao,
    private val itemRepository: LifeItemRepository,
    private val demoSeeder: DemoDataSeeder
) : ViewModel() {

    init {
        // 演示模式的示例数据生命周期（用户定案）：
        // - **开启**：若尚未播种则重建一份 61 条示例行；
        // - **关闭**：**物理删除**示例行（不只是隐藏）——备份是整库拷贝，只隐藏的话
        //   示例仍会被打包进备份；用户要的是「关闭后等同没有数据、不出现在页面也不进备份」；
        // - **再开启**：重新播种 = **重置**（编辑过 / 删过的示例都复原）。
        viewModelScope.launch {
            preferences.lifeDemoMode.collect { enabled ->
                // 播种 / 清理的策略集中在 [LifeDemoSeeder]（应用启动走同一入口），此处只跟随开关。
                // 关闭路径的清理由开关/引导的发起方负责（含「毕业询问」的归宿决定），
                // 收集器只负责开启时的播种/重置——否则会按默认「保留」抢答用户的「删除」选择。
                if (enabled) demoSeeder.ensureSeeded(preferences)
            }
        }
    }

    /**
     * 首页三卡 / 页内搜索的数据源：**条目 + 模板元数据**，演示感知（**互斥**）。
     * 卡片计数、逾期、待办补集、周历标记点都从这份全量行派生，避免多条重复 SQL。
     */
    private val boardRowsSource = preferences.lifeDemoMode
        .flatMapLatest { demo -> lifeItemDao.getBoardItemsDemoAware(demo, LIFE_DEMO_META) }
        // 首页所有派生数据（三卡计数 / 逾期 / 待安排 / 月历密度）都源自这一条流，
        // 兜底放在这里即可覆盖整棵派生树。
        .catchLife("calendar.boardRows", emptyList())

    val boardRows: StateFlow<List<LifeItemDao.LifeBoardItemRow>> =
        boardRowsSource.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 今天的本地日期，跨零点自动再发射：逾期判定 / 「今日新增」/ 那年今天 都以此为准。
     * 此前 `LocalDate.now()` 在 map 求值时固化，挂机过夜后这些读数全是昨天的。
     */
    private val todayFlow: Flow<LocalDate> = flow {
        while (true) {
            val today = LocalDate.now()
            emit(today)
            val nextMidnight = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            delay(nextMidnight - System.currentTimeMillis() + 1_000)
        }
    }

    /**
     * **所有模板都被关闭**（§4.8(8)）：生活页显示轻量空态 + 「去模板管理」入口。
     * 允许全关（不设「至少留一个」）：那会让「只用记账」的用户关不掉最后一个，且零数据风险。
     */
    val allTemplatesClosed: StateFlow<Boolean> = templateDao.getAllTemplates()
        .map { list -> list.isNotEmpty() && list.all { it.isHidden } }
        .catchLife("calendar.allTemplatesClosed", false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * 那年今天：往年同月-日的非归档条目（最多 3 条，今日看板回忆条）。
     * 固定锚定「今天」——回忆的价值在于每条记录满一年自动浮现，不随选中日漂移。
     */
    val onThisDayItems: StateFlow<List<LifeItemDao.OnThisDayRow>> =
        combine(preferences.lifeDemoMode, todayFlow) { demo, today -> demo to today }
            .flatMapLatest { (demo, today) ->
                lifeItemDao.getOnThisDay(
                    today.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd")),
                    today.year,
                    demo,
                    LIFE_DEMO_META
                )
            }
            .catchLife("calendar.onThisDay", emptyList())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 可见模板（FAB 创建面板按 category 分组展示）。 */
    val visibleTemplates: StateFlow<List<LifeTemplate>> = templateDao.getAllTemplates()
        .map { list -> list.filter { !it.isHidden } }
        .catchLife("calendar.visibleTemplates", emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 上次从创建面板选择的模板 **id**（FAB 长按直达；未选过为 null）。 */
    val lastTemplateId: StateFlow<Long?> = preferences.lifeLastTemplateId
        .catchLife<Long?>("calendar.lastTemplateId", null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rememberLastTemplateId(templateId: Long) {
        viewModelScope.launch { preferences.setLifeLastTemplateId(templateId) }
    }

    /**
     * 快捷添加的一次提交结果（UI 转成 snackbar：成功可撤销、失败有交代）。
     * [seq] 区分两次内容相同的结果，UI 以它为 key 消费。
     */
    data class QuickAddOutcome(
        val seq: Long,
        /** 成功创建的条目 id（「撤销」删除用）；失败为 null。 */
        val itemId: Long?,
        val title: String,
        val date: LocalDate,
        val time: LocalTime?,
        /** 没有可用的「待办」模板（被改名 / 全部隐藏）：静默成功是万万不能的。 */
        val noTemplate: Boolean,
        /** 演示模式开启中：条目不标示例 meta（用户真实输入永远不是示例数据），但演示视图看不到它。 */
        val demoMode: Boolean
    )

    private val _quickAddOutcome = MutableStateFlow<QuickAddOutcome?>(null)
    val quickAddOutcome: StateFlow<QuickAddOutcome?> = _quickAddOutcome

    /** UI 展示完 snackbar 后消费掉，避免回进页面重复弹出。 */
    fun consumeQuickAddOutcome() {
        _quickAddOutcome.value = null
    }

    private var quickAddSeq = 0L

    /**
     * 快捷添加（滴答清单式一句话记录）：解析日期/时间 → 落到「待办」模板 →
     * 走 [LifeItemRepository.insertItem] 让 deadline 字段镜像出执行列。
     * 标题 = 去掉日期时间词后的剩余文本；解析不出日期就记今天。
     *
     * 结果一律经 [quickAddOutcome] 交给 UI 反馈（对标 Todoist/滴答清单：识别结果可见、
     * 添加可撤销）。**不写示例 meta**：用户手输的内容不是示例数据，否则关闭演示模式时
     * 会被一并物理删除；演示模式下该条暂不显示，由 snackbar 说明并提供「关闭演示」。
     */
    fun quickAdd(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val parsed = QuickEntryParser.parse(text) ?: return@launch
            val todo = templateDao.getAllTemplates().first()
                .firstOrNull { it.name.contains(BuiltinTemplates.TODO_KEYWORD) && !it.isHidden }
            if (todo == null) {
                _quickAddOutcome.value = QuickAddOutcome(
                    seq = ++quickAddSeq, itemId = null, title = parsed.title.ifBlank { text },
                    date = parsed.date, time = parsed.time, noTemplate = true,
                    demoMode = preferences.lifeDemoMode.first()
                )
                return@launch
            }
            val zone = ZoneId.systemDefault()
            val deadlineMs = parsed.date.atStartOfDay(zone).toInstant().toEpochMilli()
            // deadline 与表单同口径写数值毫秒（mirrorExecutionColumns 与统计口径一致）
            val fieldsData = JsonObject(mapOf("deadline" to JsonPrimitive(deadlineMs))).toString()
            val newId = itemRepository.insertItem(
                LifeItem(
                    templateId = todo.id,
                    title = parsed.title.ifBlank { text },
                    fieldsData = fieldsData,
                    status = "ACTIVE",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    dueDate = deadlineMs,
                    dueTime = parsed.time?.let { it.hour * 60 + it.minute }
                )
            )
            _quickAddOutcome.value = QuickAddOutcome(
                seq = ++quickAddSeq, itemId = newId, title = parsed.title.ifBlank { text },
                date = parsed.date, time = parsed.time, noTemplate = false,
                demoMode = preferences.lifeDemoMode.first()
            )
            WidgetUpdateHelper.refreshTodoWidgets()
            WidgetUpdateHelper.refreshCounterWidgets()
        }
    }

    /** snackbar「关闭演示」动作：关演示触发 LifeDemoSeeder 清理示例行，用户真实条目随即可见。 */
    fun disableDemoMode() {
        viewModelScope.launch { preferences.setLifeDemoMode(false) }
    }

    // ───────── 看板选中日期 / 周历展开（持久化，回进页保持上次状态）─────────

    /** 看板选中日期（周历点选 / 月历点选共用）。 */
    val selectedDate: StateFlow<LocalDate> = preferences.lifeCalendarSelectedDate
        .map { LocalDate.ofEpochDay(it) }
        .catchLife("calendar.selectedDate", LocalDate.now())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDate.now())

    fun setSelectedDate(date: LocalDate) {
        viewModelScope.launch { preferences.setLifeCalendarSelectedDate(date.toEpochDay()) }
    }

    /** 看板日历视图模式（true = 周视图）：持久化，跨启动保持上次的周/月选择；首次默认周视图。 */
    val weekMode: StateFlow<Boolean> = preferences.lifeCalendarWeekMode
        .catchLife("calendar.weekMode", true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setWeekMode(week: Boolean) {
        viewModelScope.launch { preferences.setLifeCalendarWeekMode(week) }
    }

    // ───────── 首页设置 ─────────

    /**
     * 一次性教学卡「右滑完成、左滑删除」是否可见。
     *
     * 统一框架里教学类提示的纪律：**只在该页出现、可永久关闭、学会即隐**。
     * 关闭状态按消息 id 持久化（[PreferencesManager.dismissedBannerIds]）——
     * 关掉下次又冒出来是用户眼里的骚扰。
     */
    val swipeTipVisible: StateFlow<Boolean> =
        combine(preferences.dismissedBannerIds, boardRows) { dismissed, rows ->
            SWIPE_TIP_ID !in dismissed && rows.isNotEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 关闭教学卡：用户点「不再提示」或已经用滑动手势操作过一次。 */
    fun dismissSwipeTip() {
        viewModelScope.launch { preferences.dismissBanner(SWIPE_TIP_ID) }
    }


    /** 分类卡形态：true（默认）= 紧凑胶囊行，false = 三张计数大卡（生活页设置弹层切换）。 */
    val categoryCompact: StateFlow<Boolean> = preferences.lifeCategoryCompact
        .catchLife("calendar.categoryCompact", true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setCategoryCompact(compact: Boolean) {
        viewModelScope.launch { preferences.setLifeCategoryCompact(compact) }
    }

    // ───────── 今日看板 ─────────

    /**
     * 选中日的安排（看板「今日安排」区）：从 [boardRows] 投影（[scheduledOn]），
     * 与月历热力 / 完整清单页**同一口径**——只认滚动后的到期日、本地时区、排除归档。
     * 此前走独立的 DAO 查询（完整实体），年度重复不滚、子条目口径还与热力不一致。
     */
    val scheduledItems: StateFlow<List<LifeItemDao.LifeBoardItemRow>> =
        combine(selectedDate, boardRows, todayFlow) { date, rows, today ->
            rows.scheduledOn(date, today)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 月历格子信息：每日事件总数 + 出现的分类集合（A 版粉阶热力 + 三色点所需）。 */
    data class DayCalInfo(val count: Int, val categories: Set<String>)

    /**
     * 月历派生数据：只统计**滚动后到期日非空**的活跃条目，聚合成「日期 → (事件数, 分类集合)」。
     *
     * ⚠️ 口径必须与「今日看板列表」（[scheduledItems]，同样走 [LifeBoardRows] 的 [scheduledOn]）
     * **严格一致**，保证「日历格有底色/圆点 ⟺ 那天的列表里有条目」，不会出现
     * 「点了有颜色的格子、下面却显示暂无安排」。
     *
     * 曾经用 `COALESCE(dueDate, createdAt)`（月历密度口径）：它会把**没有日期字段**的模板
     * （打卡 / 心情 / 日记 / 专注 / 身体记录）按 `createdAt` 落到创建日上日历，而列表按 `dueDate`
     * 取不到这些行 —— 于是日历有颜色、点进去却是空的。故统一收窄为「有明确日期才上日历」。
     *
     * 收窄后这些记录"凭空消失"的问题，改由**写入侧**解决：`recordFallbackDueDate` 在落库时就把
     * 它们的 `dueDate` 写成记录发生当天 —— 数据是真的有日期，两个消费方依旧是同一口径。
     *
     * 「每年重复」同样滚到下一次周年：否则生日 / 纪念日只在**原始年份**的那一天有热力，
     * 今年的周年日反而不亮。只统计**活跃**（非 ARCHIVED）条目：归档的既不该占热力也不该出点。
     */
    val calendarDayMap: StateFlow<Map<LocalDate, DayCalInfo>> =
        combine(boardRows, todayFlow) { rows, today ->
            val zone = ZoneId.systemDefault()
            rows.asSequence()
                .filter { it.status != "ARCHIVED" }
                .mapNotNull { r ->
                    val ts = r.effectiveDueMillis(today, zone) ?: return@mapNotNull null
                    Instant.ofEpochMilli(ts).atZone(zone).toLocalDate() to r.category
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, cats) -> DayCalInfo(count = cats.size, categories = cats.toSet()) }
            // 月历密度要遍历**整份**看板行做分组：放到 Default，别在组合期占主线程
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * 逾期未完成条目（看板顶部红色区）：滚动后到期日 < 今日零点 ∧ 未完成。
     *
     * **排除记录类**：打卡 / 心情 / 日记 / 专注 / 身体记录的日期是「记录发生日」，
     * 对它们来说落在过去是**常态**（连续 21 天晨跑就是 21 个过去日期）。若一并算逾期，
     * 首页逾期区会瞬间被记录淹没。「逾期」只对**计划 / 时间**类（有截止日语义）成立。
     *
     * **「每年重复」不逾期**：生日 / 纪念日在 [overdueOn] 里先滚到下一次周年（恒为今天或未来），
     * 自然退出红区；否则周年一过就永久挂红，「推迟到今天」还会把锚点改写掉（见 LifeBoardRows.kt）。
     */
    val overdueItems: StateFlow<List<LifeItemDao.LifeBoardItemRow>> =
        combine(boardRows, todayFlow) { rows, today -> rows.overdueOn(today) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 「待安排」卡：**未完成、未归档、无日期**的条目（收件箱；**不限模板**）。
     *
     * 口径与看板互补：**有日期**的条目归日历 / 看板（按时间）；**无日期**的归此卡。
     * 因此逾期与未来项**不再**出现在这里（逾期归看板红区，未来靠日历圆点 / 分类卡 / 当天看板）。
     */
    val unscheduledItems: StateFlow<List<LifeItemDao.LifeBoardItemRow>> = boardRows.map { rows ->
        rows.filter { r ->
            r.status != "COMPLETED" && r.status != "ARCHIVED" && r.dueDate == null
        }.sortedBy { it.createdAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ───────── 分类卡 ─────────

    /**
     * 分类卡计数：模板分类 → 总条数 + 今日件数。
     * 「今日件数」口径 = COALESCE(dueDate, createdAt) 落在今天：计划/时间看「今天到期几件」，
     * 记录类（dueDate 兜底为记录发生日）看「今天记了几条」——文案「今日 N 件」与口径严格一致。
     * （曾叫「今日 +N」，暗示"今天新增"，与口径不符：今天创建、排期下周的计划不会计入。）
     */
    data class CategoryCount(val total: Int, val today: Int)

    val categoryCounts: StateFlow<Map<String, CategoryCount>> =
        combine(boardRows, todayFlow) { rows, today ->
            val zone = ZoneId.systemDefault()
            val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val todayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            rows.groupBy { it.category }.mapValues { (_, list) ->
                CategoryCount(
                    total = list.size,
                    today = list.count { r ->
                        val ts = r.dueDate ?: r.createdAt
                        ts >= todayStart && ts < todayEnd
                    }
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // ───────── 就地操作 ─────────

    /** 条目完成状态切换（看板 / 待安排卡的勾圈）：COMPLETED ⇄ ACTIVE。 */
    fun toggleItemStatus(itemId: Long) {
        viewModelScope.launch {
            val item = lifeItemDao.getItemById(itemId) ?: return@launch
            lifeItemDao.updateStatus(itemId, if (item.status == "COMPLETED") "ACTIVE" else "COMPLETED")
            WidgetUpdateHelper.refreshTodoWidgets()
        }
    }

    /**
     * **按方向**设置完成状态（滑动确认用）。
     *
     * 与 [toggleItemStatus] 的区别是这里不读当前值：滑动对话框的文案已经按
     * `PendingSwipe.Complete.toCompleted` 承诺了方向（「标记为完成」/「标记为未完成」），
     * 若此处仍用 toggle，一旦手势与确认之间状态被别处改过，实际结果就与文案相反。
     * 幂等：已经是目标状态时写回同值，不会把「标记为完成」变成「取消完成」。
     */
    fun setItemCompleted(itemId: Long, completed: Boolean) {
        viewModelScope.launch {
            lifeItemDao.updateStatus(itemId, if (completed) "COMPLETED" else "ACTIVE")
            WidgetUpdateHelper.refreshTodoWidgets()
        }
    }

    /**
     * 逾期条目一键推迟到今天（显式 dueDate 优先；保留原 dueTime 与 fieldsData）。
     *
     * **「每年重复」拒绝推迟**：这些行已滚到下一次周年、本就不该出现在逾期区；
     * 万一从别的入口调进来，也不能把周年锚点改写成今天——那会毁掉整个年度语义
     * （对齐 Todoist：改循环任务的日期必须走"编辑母本"，而不是推迟）。
     */
    fun rescheduleToToday(itemId: Long) {
        viewModelScope.launch {
            val item = lifeItemDao.getItemById(itemId) ?: return@launch
            val template = templateDao.getTemplateById(item.templateId)
            if (template?.repeatYearly == true) return@launch
            val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            lifeItemDao.updateFieldsDataWithSchedule(itemId, item.fieldsData, todayStart, item.dueTime)
            WidgetUpdateHelper.refreshTodoWidgets()
        }
    }

    /**
     * 逾期区「全部推迟」：批量推到今天。推迟是低风险操作，不逐条确认；
     * 年度重复行 [overdueOn] 本就不会出现在逾期区，这里同样按模板跳过兜底。
     */
    fun rescheduleAllToToday(itemIds: List<Long>) {
        viewModelScope.launch {
            val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            itemIds.forEach { itemId ->
                val item = lifeItemDao.getItemById(itemId) ?: return@forEach
                val template = templateDao.getTemplateById(item.templateId)
                if (template?.repeatYearly == true) return@forEach
                lifeItemDao.updateFieldsDataWithSchedule(itemId, item.fieldsData, todayStart, item.dueTime)
            }
            WidgetUpdateHelper.refreshTodoWidgets()
        }
    }

    /**
     * 待安排条目一键排期（收件箱 → 看板的整理闭环）：日期来自 M3 选择器，
     * 落当天零点（本地时区）；保留原 dueTime 与 fieldsData。
     */
    fun scheduleItem(itemId: Long, date: LocalDate) {
        viewModelScope.launch {
            val item = lifeItemDao.getItemById(itemId) ?: return@launch
            val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            lifeItemDao.updateFieldsDataWithSchedule(itemId, item.fieldsData, dayStart, item.dueTime)
            WidgetUpdateHelper.refreshTodoWidgets()
        }
    }

    /** 看板左滑删除（确认弹窗通过后才调用）。 */
    fun deleteItem(itemId: Long) {
        viewModelScope.launch {
            lifeItemDao.deleteItemCascade(itemId)
            WidgetUpdateHelper.refreshTodoWidgets()
        }
    }
    companion object {
        /** 教学卡的消息 id（关闭状态按它持久化）。 */
        private const val SWIPE_TIP_ID = "life_swipe_tip"
    }
}
