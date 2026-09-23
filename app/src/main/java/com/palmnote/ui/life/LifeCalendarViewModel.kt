package com.palmnote.ui.life

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.LifeDemoSeeder
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.LIFE_DEMO_META
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.util.BuiltinTemplates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * 生活页数据源（首页三卡：分类卡 / 今日看板 / 待办卡）。
 *
 * **数据一律来自数据库**，没有画在界面上的假数据：
 * - 演示模式开启（默认）：先确保示例数据已播种（[LifeDemoSeeder]，真实 `life_items` 行，
 *   可查看 / 编辑 / 删除），查询时**包含**这些行；
 * - 演示模式关闭：查询时**排除**带示例标记的行，只看用户自己的记录。
 *
 * 日历 / 看板口径：**只认 `dueDate`** + 本地时区（防跨时区错日）。
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
    private val demoSeeder: LifeDemoSeeder
) : ViewModel() {

    init {
        // 演示模式的示例数据生命周期（用户定案）：
        // - **开启**：若尚未播种则重建一份 50 条示例行；
        // - **关闭**：**物理删除**示例行（不只是隐藏）——备份是整库拷贝，只隐藏的话
        //   示例仍会被打包进备份；用户要的是「关闭后等同没有数据、不出现在页面也不进备份」；
        // - **再开启**：重新播种 = **重置**（编辑过 / 删过的示例都复原）。
        viewModelScope.launch {
            preferences.lifeDemoMode.collect { enabled ->
                // 播种 / 清理的策略集中在 [LifeDemoSeeder]（应用启动走同一入口），此处只跟随开关。
                if (enabled) demoSeeder.ensureSeeded(preferences) else demoSeeder.clearAll(preferences)
            }
        }
    }

    /**
     * 生活页「是否已有内容」：**演示模式开启**或**真实条目数 > 0** 即视为有内容。
     *
     * 用于驱动生活页首屏空状态（首次为空时显示「加载示例数据」入口，而不是把示例埋进设置页）。
     * - 演示开启：直接算「有内容」，避免启动补种完成前空状态闪一下；
     * - 演示关闭：靠真实条目数判断（关闭时示例行已被物理删除，[getTotalItemCount] 此时即真实条数）。
     */
    val hasContent: StateFlow<Boolean> =
        combine(preferences.lifeDemoMode, lifeItemDao.getTotalItemCount()) { demo, count -> demo || count > 0 }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /**
     * 首页三卡 / 页内搜索的数据源：**条目 + 模板元数据**，演示感知（**互斥**）。
     * 卡片计数、逾期、待办补集、周历标记点都从这份全量行派生，避免多条重复 SQL。
     */
    val boardRows: StateFlow<List<LifeItemDao.LifeBoardItemRow>> =
        preferences.lifeDemoMode
            .flatMapLatest { demo -> lifeItemDao.getBoardItemsDemoAware(demo, LIFE_DEMO_META) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * **所有模板都被关闭**（§4.8(8)）：生活页显示轻量空态 + 「去模板管理」入口。
     * 允许全关（不设「至少留一个」）：那会让「只用记账」的用户关不掉最后一个，且零数据风险。
     */
    val allTemplatesClosed: StateFlow<Boolean> = templateDao.getAllTemplates()
        .map { list -> list.isNotEmpty() && list.all { it.isHidden } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 可见模板（FAB 创建面板按 category 分组展示）。 */
    val visibleTemplates: StateFlow<List<LifeTemplate>> = templateDao.getAllTemplates()
        .map { list -> list.filter { !it.isHidden } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ───────── 看板选中日期 / 周历展开（持久化，回进页保持上次状态）─────────

    /** 看板选中日期（周历点选 / 月历点选共用）。 */
    val selectedDate: StateFlow<LocalDate> = preferences.lifeCalendarSelectedDate
        .map { LocalDate.ofEpochDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDate.now())

    fun setSelectedDate(date: LocalDate) {
        viewModelScope.launch { preferences.setLifeCalendarSelectedDate(date.toEpochDay()) }
    }

    /** 看板日历视图模式（true = 周视图）：持久化，跨启动保持上次的周/月选择；首次默认周视图。 */
    val weekMode: StateFlow<Boolean> = preferences.lifeCalendarWeekMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setWeekMode(week: Boolean) {
        viewModelScope.launch { preferences.setLifeCalendarWeekMode(week) }
    }

    // ───────── 今日看板 ─────────

    /** 选中日的安排（时段桶需要完整实体的 `dueTime` 列，演示感知互斥）。 */
    val scheduledItems: StateFlow<List<com.palmnote.data.db.entity.LifeItem>> =
        combine(preferences.lifeDemoMode, selectedDate) { demo, date -> demo to date }
            .flatMapLatest { (demo, date) ->
                val zone = ZoneId.systemDefault()
                val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                lifeItemDao.getScheduledBetweenDemoAware(start, end, demo, LIFE_DEMO_META)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 月历格子信息：每日事件总数 + 出现的分类集合（A 版粉阶热力 + 三色点所需）。 */
    data class DayCalInfo(val count: Int, val categories: Set<String>)

    /**
     * 月历派生数据：**只统计 `dueDate` 非空**的活跃条目，聚合成「日期 → (事件数, 分类集合)」。
     *
     * ⚠️ 口径必须与「今日看板列表」（[LifeItemDao.getScheduledBetweenDemoAware]，同样只认 `dueDate`）
     * **严格一致**，保证「日历格有底色/圆点 ⟺ 那天的列表里有条目」，不会出现
     * 「点了有颜色的格子、下面却显示暂无安排」。
     *
     * 曾经用 `COALESCE(dueDate, createdAt)`（月历密度口径）：它会把**没有日期字段**的模板
     * （打卡 / 心情 / 日记 / 专注 / 身体记录）按 `createdAt` 落到创建日上日历，而列表按 `dueDate`
     * 取不到这些行 —— 于是日历有颜色、点进去却是空的。故统一收窄为「有明确日期才上日历」。
     *
     * 收窄后这些记录"凭空消失"的问题，改由**写入侧**解决：`recordFallbackDueDate` 在落库时就把
     * 它们的 `dueDate` 写成记录发生当天 —— 数据是真的有日期，两个消费方依旧是同一个 `dueDate` 口径。
     *
     * 演示感知已下沉到 [boardRows]；只统计**活跃**（非 ARCHIVED）条目：归档的既不该占热力也不该出点。
     */
    val calendarDayMap: StateFlow<Map<LocalDate, DayCalInfo>> = boardRows.map { rows ->
        val zone = ZoneId.systemDefault()
        rows.asSequence()
            .filter { it.status != "ARCHIVED" }
            .mapNotNull { r ->
                val ts = r.dueDate ?: return@mapNotNull null
                Instant.ofEpochMilli(ts).atZone(zone).toLocalDate() to r.category
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, cats) -> DayCalInfo(count = cats.size, categories = cats.toSet()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * 逾期未完成条目（看板顶部红色区 + 待办卡逾期计数；条目级：dueDate < 今日零点 ∧ 未完成）。
     *
     * **排除记录类**：打卡 / 心情 / 日记 / 专注 / 身体记录的日期是「记录发生日」，
     * 对它们来说落在过去是**常态**（连续 21 天晨跑就是 21 个过去日期）。若一并算逾期，
     * 首页逾期区会瞬间被记录淹没。「逾期」只对**计划 / 时间**类（有截止日语义）成立。
     */
    val overdueItems: StateFlow<List<LifeItemDao.LifeBoardItemRow>> = boardRows.map { rows ->
        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        rows.filter { r ->
            val due = r.dueDate
            due != null && due < todayStart && r.status != "COMPLETED" && r.status != "ARCHIVED" &&
                r.category != BuiltinTemplates.RECORD_CATEGORY
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 待办卡：待办模板、非今日（逾期 / 未来 / 无日期）、未完成、非子任务（旧「待办补集」口径）。 */
    val todoItems: StateFlow<List<LifeItemDao.LifeBoardItemRow>> = boardRows.map { rows ->
        val zone = ZoneId.systemDefault()
        val todayStart = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val todayEnd = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        rows.asSequence()
            .filter { r ->
                val due = r.dueDate
                r.templateName.contains(BuiltinTemplates.TODO_KEYWORD) &&
                    r.status != "COMPLETED" && r.status != "ARCHIVED" &&
                    (due == null || due < todayStart || due >= todayEnd)
            }
            .sortedBy { it.dueDate ?: Long.MAX_VALUE }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ───────── 分类卡 ─────────

    /** 分类卡计数：模板分类 → 总条数 + 今日新增数（日期口径 COALESCE(dueDate, createdAt)）。 */
    data class CategoryCount(val total: Int, val today: Int)

    val categoryCounts: StateFlow<Map<String, CategoryCount>> = boardRows.map { rows ->
        val zone = ZoneId.systemDefault()
        val todayStart = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val todayEnd = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
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

    /** 条目完成状态切换（看板 / 待办卡的勾圈）：COMPLETED ⇄ ACTIVE。 */
    fun toggleItemStatus(itemId: Long) {
        viewModelScope.launch {
            val item = lifeItemDao.getItemById(itemId) ?: return@launch
            lifeItemDao.updateStatus(itemId, if (item.status == "COMPLETED") "ACTIVE" else "COMPLETED")
        }
    }

    /** 逾期条目一键推迟到今天（显式 dueDate 优先；保留原 dueTime 与 fieldsData）。 */
    fun rescheduleToToday(itemId: Long) {
        viewModelScope.launch {
            val item = lifeItemDao.getItemById(itemId) ?: return@launch
            val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            lifeItemDao.updateFieldsDataWithSchedule(itemId, item.fieldsData, todayStart, item.dueTime)
        }
    }

    /** 看板左滑删除（确认弹窗通过后才调用）。 */
    fun deleteItem(itemId: Long) {
        viewModelScope.launch { lifeItemDao.deleteItemCascade(itemId) }
    }

    /** 从生活页空状态卡「加载示例数据」按钮调用：开启演示＝重新播种。 */
    fun enableDemoData() {
        viewModelScope.launch { preferences.setLifeDemoMode(true) }
    }
}
