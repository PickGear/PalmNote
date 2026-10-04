@file:Suppress("TooManyFunctions")

package com.palmnote.ui.life

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CardMembership
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.palmnote.app.R
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.data.db.entity.getDisplayName
import com.palmnote.domain.model.parseChecklist
import com.palmnote.domain.util.BuiltinTemplates
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.palmnote.ui.components.AnimatedCard
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.CompactTopAppBar
import com.palmnote.ui.components.ModuleSearchBar
import com.palmnote.ui.theme.LifePlan
import com.palmnote.ui.theme.LifeRecord
import com.palmnote.ui.theme.LifeTime
import com.palmnote.ui.theme.ModuleLife
import com.palmnote.ui.theme.Spacing
import com.palmnote.ui.theme.TypeScale
import com.palmnote.ui.theme.lifePlanTint
import com.palmnote.ui.theme.lifeRecordTint
import com.palmnote.ui.theme.lifeTimeTint
import java.time.DayOfWeek
import java.time.LocalDate

// ───────────────────────── 生活页首页（旧版三卡布局回归） ─────────────────────────
//
// 2026-09-23 用户定案：首页 UI 改回旧版（截图版）——顶栏「生活」+ 统计/搜索/管理三图标、
// 三张分类卡、「今日看板」（周历 + 当日安排 + 逾期置顶）、「待办」卡、「+ 新建」FAB。
// **架构不变**：数据全部来自 LifeCalendarViewModel（演示感知互斥），点条目进详情页，
// 新建走记录填写页（LifeCreateRecord）。日历 / 全部独立视图随本轮一并退役。

/** 模板分类的 DB 常量值（中文，与 locale 无关；分类卡的 key，展示文案走 strings）。 */
private const val CAT_PLAN = BuiltinTemplates.PLAN_CATEGORY
private const val CAT_TIME = BuiltinTemplates.TIME_CATEGORY
private const val CAT_RECORD = BuiltinTemplates.RECORD_CATEGORY

/**
 * 首页三张清单（逾期 / 今日安排 / 待安排）的预览条数：超出只展示前 N 条 + 「还有 N 条」。
 *
 * 三者共用一个常量，行高统一 30dp 后「一屏能装几条」的口径才不会各自漂移；
 * 数量再多也不会把下面那张卡挤出首屏。
 */
private const val LIST_PREVIEW_COUNT = 4

// 颜色解析统一走 `LifeDetailModel.identityColor`（曾被复制成第二份 lifeIdentityColor，
// 兜底色还不一样：Gray vs ModuleLife）。同一个决定只该有一处实现。

@Suppress("LongParameterList", "LongMethod")
@Composable
fun LifeScreen(
    /** 打开某条记录的详情页（按 itemId，详情页自己查库）。 */
    onOpenDetail: (itemId: Long) -> Unit,
    onOpenStats: () -> Unit,
    onOpenManageTemplates: () -> Unit,
    /** 点击首页分类卡：跳转到对应分类详情页（计划/时间/记录）。 */
    onOpenCategory: (category: String) -> Unit,
    /** 打开某张清单的完整列表（逾期 / 选中日安排 / 待安排）。 */
    onOpenFullList: (LifeFullListMode, Long) -> Unit,
    /** 新建记录：传入模板 **id**（记录填写页按 id 取模板；图标会撞，见 LifeRoute 的说明）。 */
    onCreateRecord: (templateId: Long) -> Unit,
    /** 上次从创建面板选择的模板 id（null = 还没选过，长按退化为单击）。 */
    lastTemplateId: Long? = null,
    /** 在创建面板选中模板时记录，供下次长按 FAB 直达。 */
    onRememberTemplate: (Long) -> Unit = {},
    /** 那年今天（往年同月-日条目，最多 3 条；今日看板回忆条）。 */
    onThisDayItems: List<LifeItemDao.OnThisDayRow> = emptyList(),
    /** 快捷添加：一句话记一笔（解析日期时间落到待办模板）。 */
    onQuickAdd: (String) -> Unit = {},
    /** 快捷添加的结果（转 snackbar：成功可撤销、失败有交代）；null = 无待展示事件。 */
    quickAddOutcome: LifeCalendarViewModel.QuickAddOutcome? = null,
    /** snackbar 展示完毕后消费事件，避免回进页面重复弹出。 */
    onQuickAddOutcomeShown: () -> Unit = {},
    /** snackbar「关闭演示」动作（演示模式下快捷添加的条目暂不显示）。 */
    onDisableDemoMode: () -> Unit = {},
    /** 所有模板都被关闭：显示轻量空态 + 「去模板管理」。 */
    allTemplatesClosed: Boolean = false,
    /** 全量条目行（条目 + 模板元数据；演示感知已下沉到查询端）。 */
    boardRows: List<LifeItemDao.LifeBoardItemRow> = emptyList(),
    /** 选中日的安排（今日看板时段桶；看板行投影，含 dueTime）。 */
    scheduledItems: List<LifeItemDao.LifeBoardItemRow> = emptyList(),
    /** 月历格子信息：每日事件数 + 分类集合（驱动粉阶热力与三色点）。 */
    calendarDayMap: Map<LocalDate, LifeCalendarViewModel.DayCalInfo> = emptyMap(),
    /** 逾期未完成条目（看板顶部红色区）。 */
    overdueItems: List<LifeItemDao.LifeBoardItemRow> = emptyList(),
    /** 待安排卡条目（未完成、无日期、不限模板）。 */
    unscheduledItems: List<LifeItemDao.LifeBoardItemRow> = emptyList(),
    /** 分类卡计数：DB 分类值 → 总数 + 今日新增。 */
    categoryCounts: Map<String, LifeCalendarViewModel.CategoryCount> = emptyMap(),
    /** 可见模板（FAB 创建面板）。 */
    templates: List<LifeTemplate> = emptyList(),
    /** 看板选中日期（月历共用，持久化）。 */
    selectedDate: LocalDate = LocalDate.now(),
    onSelectDate: (LocalDate) -> Unit = {},
    /** 长按月历格 → 当日回读页。 */
    onOpenDayRead: (LocalDate) -> Unit = {},
    /** 看板日历视图模式（true = 周视图，持久化；首次默认周视图）。 */
    calendarWeekMode: Boolean = true,
    onCalendarWeekModeChange: (Boolean) -> Unit = {},
    /** 条目完成状态就地切换。 */
    onSetItemStatus: (itemId: Long, completed: Boolean?) -> Unit = { _, _ -> },
    /** 逾期条目一键推迟到今天。 */
    onReschedule: (itemId: Long) -> Unit = { _ -> },
    /** 看板左滑删除（确认弹窗通过后调用）。 */
    onDeleteItem: (itemId: Long) -> Unit = { _ -> },
    /** 分类卡形态：true（默认）= 紧凑胶囊行，false = 三张计数大卡（全局设置可切换）。 */
    categoryCompact: Boolean = true,
    /** 一次性教学卡「右滑完成、左滑删除」是否可见（可永久关闭 / 学会即隐）。 */
    swipeTipVisible: Boolean = false,
    onDismissSwipeTip: () -> Unit = {},
    /** 逾期区「全部推迟」：入参 = 红区实际展示的那批逾期 id（排除已排进当日安排的）。 */
    onRescheduleAllOverdue: (itemIds: List<Long>) -> Unit = {},
    /** 待安排行 📅 一键排期（日期来自 M3 选择器）。 */
    onScheduleItem: (itemId: Long, date: LocalDate) -> Unit = { _, _ -> }
) {
    var fabSheetOpen by rememberSaveable { mutableStateOf(false) }
    // 「上次记录：N 天前」的模板维度读数：从**已在用的** boardRows 聚合，不新增查询。
    // 对标 MarkTimes 的「上次发生：N 天前」——它回答的是"我该不该再记一笔"。
    val lastLogByTemplate = remember(boardRows) {
        boardRows.groupBy { it.templateId }.mapValues { (_, rows) -> rows.maxOf { it.updatedAt } }
    }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = showSearch) {
        showSearch = false
        searchQuery = ""
    }

    // ── 快捷添加反馈（对标 Todoist/滴答清单：提交可确认、可撤销、失败有交代）──
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarCopy = quickAddSnackbarCopy(quickAddOutcome, LocalDate.now())
    LaunchedEffect(quickAddOutcome?.seq) {
        val outcome = quickAddOutcome ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = snackbarCopy.message,
            actionLabel = snackbarCopy.action,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            when {
                outcome.noTemplate -> onOpenManageTemplates()
                outcome.demoMode -> onDisableDemoMode()
                outcome.itemId != null -> onDeleteItem(outcome.itemId)
            }
        }
        onQuickAddOutcomeShown()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
            topBar = {
                LifeTopBar(
                    showSearch = showSearch,
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClear = { searchQuery = "" },
                    onCancelSearch = {
                        showSearch = false
                        searchQuery = ""
                    },
                    onOpenSearch = { showSearch = true },
                    onOpenStats = onOpenStats,
                    onOpenManage = onOpenManageTemplates
                )
            },
            floatingActionButton = {
                // 搜索态 / 全模板关闭态不显示：创建动作在这两种场景没有意义，
                // 留着只会打开一个空面板或挡住搜索结果（Google Tasks/Keep 同款处理）。
                if (!showSearch && !allTemplatesClosed) {
                    // 「+ 新建」扩展 FAB：单击展开创建面板；**长按直达上次使用的模板表单**
                    // （未选过退化为单击）。高频用户八成的记录就一两个模板，省掉弹窗一层。
                    //
                    // 修饰符顺序有讲究：`clip → background → combinedClickable`。
                    // 早先把 clickable 放在 background 之前，ripple 被背景整个盖住，
                    // FAB 按下去毫无反馈；clip 还保证 ripple 不会画出圆角外面。
                    val haptics = LocalHapticFeedback.current
                    @OptIn(ExperimentalFoundationApi::class)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(ModuleLife)
                            .combinedClickable(
                                onClick = { fabSheetOpen = true },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    lastTemplateId?.let(onCreateRecord)
                                }
                            )
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.life_new_create),
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            when {
                showSearch -> LifeSearchContent(
                    query = searchQuery,
                    rows = boardRows,
                    onOpenDetail = onOpenDetail,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
                allTemplatesClosed -> Box(Modifier.fillMaxSize().padding(innerPadding)) {
                    AllTemplatesClosedState(onOpenManage = onOpenManageTemplates)
                }
                else -> HomeContent(
                    innerPadding = innerPadding,
                    categoryCounts = categoryCounts,
                    categoryCompact = categoryCompact,
                    swipeTipVisible = swipeTipVisible,
                    onDismissSwipeTip = onDismissSwipeTip,
                    onOpenCategory = onOpenCategory,
                    onOpenFullList = onOpenFullList,
                    selectedDate = selectedDate,
                    onSelectDate = onSelectDate,
                    onOpenDayRead = onOpenDayRead,
                    calendarWeekMode = calendarWeekMode,
                    onCalendarWeekModeChange = onCalendarWeekModeChange,
                    calendarDayMap = calendarDayMap,
                    overdueItems = overdueItems,
                    onThisDayItems = onThisDayItems,
                    onQuickAdd = onQuickAdd,
                    scheduledItems = scheduledItems,
                    unscheduledItems = unscheduledItems,
                    templates = templates,
                    onOpenDetail = onOpenDetail,
                    onSetItemStatus = onSetItemStatus,
                    onReschedule = onReschedule,
                    onRescheduleAllOverdue = onRescheduleAllOverdue,
                    onDeleteItem = onDeleteItem,
                    onScheduleItem = onScheduleItem
                )
            }
        }

        // 创建面板（窗口级 ModalBottomSheet，盖住底部导航栏）
        if (fabSheetOpen) {
            FabSheet(
                templates = templates,
                lastLogByTemplate = lastLogByTemplate,
                onDismiss = { fabSheetOpen = false },
                onTemplateClick = { tpl ->
                    onRememberTemplate(tpl.id)
                    fabSheetOpen = false
                    onCreateRecord(tpl.id)
                }
            )
        }

    }
}

// ───────────────────────── 顶栏（标题 + 三图标 / 搜索态） ─────────────────────────

@Composable
private fun LifeTopBar(
    showSearch: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onCancelSearch: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenManage: () -> Unit
) {
    CompactTopAppBar(
        title = {
            if (showSearch) {
                ModuleSearchBar(
                    query = query,
                    onQueryChange = onQueryChange,
                    onClear = onClear,
                    placeholder = stringResource(R.string.search),
                    autoFocus = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    stringResource(R.string.nav_life),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = ModuleLife
                )
            }
        },
        actions = {
            if (showSearch) {
                TextButton(onClick = onCancelSearch, modifier = Modifier.padding(end = 4.dp)) {
                    Text(stringResource(R.string.cancel), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                IconButton(onClick = onOpenStats) {
                    Icon(
                        Icons.Outlined.BarChart,
                        stringResource(R.string.life_home_stats),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        Icons.Outlined.Search,
                        stringResource(R.string.search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenManage) {
                    Icon(
                        Icons.Outlined.GridView,
                        stringResource(R.string.life_template_manage),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

// ───────────────────────── 页内搜索（真实数据过滤） ─────────────────────────

@Composable
private fun LifeSearchContent(
    query: String,
    rows: List<LifeItemDao.LifeBoardItemRow>,
    onOpenDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // 可搜文本按 rows 变化构建一次（解析 fieldsData 取字符串值与清单行），
    // 不再拿原始 JSON 匹配 —— 否则会命中 "duration"/"weather" 这类键名噪音（审计 #29）
    val snippets = remember(rows) {
        rows.associate { it.itemId to searchSnippetOf(it) }
    }
    val filtered = remember(snippets, query) {
        if (query.isBlank()) {
            emptyList()
        } else {
            rows.filter { r ->
                r.status != "ARCHIVED" && (
                    r.title.contains(query, true) ||
                        r.templateName.contains(query, true) ||
                        (snippets[r.itemId]?.contains(query, true) == true)
                    )
            }
        }
    }
    if (query.isBlank() || filtered.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LifeEmptyState(
                icon = Icons.Outlined.Search,
                title = stringResource(
                    if (query.isBlank()) R.string.life_all_search_hint else R.string.life_search_no_result
                ),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            items(filtered, key = { it.itemId }) { row ->
                FlowCard(row, showDoneState = true) { onOpenDetail(row.itemId) }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun FlowCard(row: LifeItemDao.LifeBoardItemRow, showDoneState: Boolean = false, onClick: () -> Unit) {
    val context = LocalContext.current
    val done = showDoneState && row.status == "COMPLETED"
    // 「卡片字段」（showInCard）此前在这里没有任何渲染方，是死配置；现在与模板预览共用同一份卡片。
    val cardFields = remember(row.fieldsConfig, row.fieldsData, row.repeatYearly, context) {
        localizedCardFieldValues(context, row.fieldsConfig, row.fieldsData, repeatYearly = row.repeatYearly)    }
    val progress = remember(row.fieldsConfig, row.fieldsData) {
        cardProgressFraction(row.fieldsConfig, row.fieldsData)
    }
    LifeRecordCard(
        iconKey = row.icon,
        colorHex = row.color,
        title = row.title,
        subtitle = row.templateName,
        fields = cardFields,
        progress = progress,
        done = done,
        onClick = onClick
    )
}

/** 行的可搜文本：标题/模板名之外，把 fieldsData 里的字符串值与清单行文提取出来（键名与数值不参与）。 */
private fun searchSnippetOf(row: LifeItemDao.LifeBoardItemRow): String {
    val obj = runCatching { Json.decodeFromString<JsonObject>(row.fieldsData) }.getOrNull()
        ?: return ""
    val parts = buildList {
        obj.values.forEach { el ->
            val p = el as? JsonPrimitive ?: return@forEach
            if (p.isString) add(p.content)
        }
        obj.values.forEach { el ->
            val payload = (el as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (payload != null) parseChecklist(payload).forEach { add(it.text) }
        }
    }
    return parts.joinToString(" ").take(2_000)
}

/**
 * 模板 iconKey → 图标。纯映射表（无逻辑分支），分支数随模板种类增长，故抑制复杂度检查——
 * 与 [com.palmnote.feature.vault.VaultEntry] 等映射表的处理一致。
 */
@Suppress("CyclomaticComplexMethod")
internal fun iconFor(key: String): ImageVector = when (key) {
    "checklist", "note_add" -> Icons.Filled.CheckBox
    "savings" -> Icons.Filled.Savings
    "calendar_month" -> Icons.Filled.CalendarMonth
    "timer_off", "timer" -> Icons.Filled.Timer
    "cake" -> Icons.Filled.Cake
    "book", "mood" -> Icons.Filled.Book
    "shopping_cart" -> Icons.Filled.ShoppingCart
    "fitness_center" -> Icons.Filled.FitnessCenter
    "menu_book" -> Icons.AutoMirrored.Filled.MenuBook
    "flight" -> Icons.Filled.Flight
    "BarChart" -> Icons.Filled.BarChart
    "school" -> Icons.Outlined.School
    // material-icons-extended 缺 Filled 版的 School / Celebration / Subscriptions / Build：
    // 这 4 个用 Outlined 变体（AppIcon.kt 已验证可用），其余保持 Filled，尽量贴近旧版观感。
    // trending_up 的 Filled 版已废弃，改用 AutoMirrored 版（观感仍是实心）。
    "celebration" -> Icons.Outlined.Celebration
    "subscriptions" -> Icons.Outlined.CardMembership
    "build" -> Icons.Outlined.Build
    "trending_up" -> Icons.AutoMirrored.Filled.TrendingUp
    else -> Icons.Filled.Circle
}

// ───────────────────────── 首页内容（三卡纵列） ─────────────────────────

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun HomeContent(
    innerPadding: PaddingValues,
    categoryCounts: Map<String, LifeCalendarViewModel.CategoryCount>,
    categoryCompact: Boolean,
    swipeTipVisible: Boolean,
    onDismissSwipeTip: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenFullList: (LifeFullListMode, Long) -> Unit,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onOpenDayRead: (LocalDate) -> Unit,
    calendarWeekMode: Boolean,
    onCalendarWeekModeChange: (Boolean) -> Unit,
    calendarDayMap: Map<LocalDate, LifeCalendarViewModel.DayCalInfo>,
    overdueItems: List<LifeItemDao.LifeBoardItemRow>,
    onThisDayItems: List<LifeItemDao.OnThisDayRow> = emptyList(),
    /** 快捷添加：一句话记一笔（解析日期时间落到待办模板）。 */
    onQuickAdd: (String) -> Unit = {},
    scheduledItems: List<LifeItemDao.LifeBoardItemRow>,
    unscheduledItems: List<LifeItemDao.LifeBoardItemRow>,
    templates: List<LifeTemplate>,
    onOpenDetail: (Long) -> Unit,
    onSetItemStatus: (itemId: Long, completed: Boolean?) -> Unit,
    onReschedule: (Long) -> Unit,
    onRescheduleAllOverdue: (itemIds: List<Long>) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onScheduleItem: (itemId: Long, date: LocalDate) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
    ) {
        AnimatedCard(index = 0) {
            CategoryHomeCard(
                counts = categoryCounts,
                compact = categoryCompact,
                onOpenCategory = onOpenCategory,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 12.dp)
            )
        }
        if (swipeTipVisible) {
            SwipeTipCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 10.dp),
                onDismiss = onDismissSwipeTip
            )
        }
        AnimatedCard(index = 1) {
            TodayBoardHomeCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
                onThisDayItems = onThisDayItems,
                onQuickAdd = onQuickAdd,
                selectedDate = selectedDate,
                onSelectDate = onSelectDate,
                onOpenDayRead = onOpenDayRead,
                weekMode = calendarWeekMode,
                onWeekModeChange = onCalendarWeekModeChange,
                dayMap = calendarDayMap,
                overdueItems = overdueItems,
                scheduledItems = scheduledItems,
                templates = templates,
                onOpenDetail = onOpenDetail,
                onSetItemStatus = onSetItemStatus,
                onReschedule = onReschedule,
                onRescheduleAllOverdue = onRescheduleAllOverdue,
                onDeleteItem = onDeleteItem,
                onDismissSwipeTip = onDismissSwipeTip,
                onOpenFullList = { mode ->
                    onOpenFullList(mode, selectedDate.toEpochDay())
                }
            )
        }
        AnimatedCard(index = 2) {
            UnscheduledCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
                unscheduledItems = unscheduledItems,
                onOpenDetail = onOpenDetail,
                onSetItemStatus = onSetItemStatus,
                onDeleteItem = onDeleteItem,
                onScheduleItem = onScheduleItem,
                onDismissSwipeTip = onDismissSwipeTip,
                onOpenFullList = {
                    onOpenFullList(LifeFullListMode.UNSCHEDULED, selectedDate.toEpochDay())
                }
            )
        }
        Spacer(Modifier.height(96.dp))
    }
}

// ───────────────────────── 分类卡 ─────────────────────────

/** 模板分类 → 首页入口。紧凑 = 一行胶囊（首屏让位给今日安排）；宽松 = 三张计数大卡。 */
@Composable
private fun CategoryHomeCard(
    counts: Map<String, LifeCalendarViewModel.CategoryCount>,
    compact: Boolean,
    onOpenCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries: List<Triple<String, ImageVector, Color>> = listOf(
        Triple(
            stringResource(R.string.life_category_plan),
            Icons.Filled.Star,
            LifePlan
        ),
        Triple(
            stringResource(R.string.life_category_time),
            Icons.Filled.CalendarMonth,
            LifeTime
        ),
        Triple(
            stringResource(R.string.life_category_record),
            Icons.Filled.AutoStories,
            LifeRecord
        )
    )
    val categories = listOf(CAT_PLAN, CAT_TIME, CAT_RECORD)
    if (compact) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.forEachIndexed { index, (title, icon, color) ->
                CategoryChipPill(
                    title = title,
                    icon = icon,
                    color = color,
                    today = counts[categories[index]]?.today ?: 0,
                    onClick = { onOpenCategory(categories[index]) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.forEachIndexed { index, (title, icon, color) ->
                Box(modifier = Modifier.weight(1f)) {
                    CategoryMiniCard(
                        title = title,
                        icon = icon,
                        color = color,
                        count = counts[categories[index]],
                        onClick = { onOpenCategory(categories[index]) }
                    )
                }
            }
        }
    }
}

/** 紧凑分类胶囊：icon + 名 + 「今日 N」，不占总数读数（总数在分类页/统计页自然存在）。 */
@Composable
private fun CategoryChipPill(
    title: String,
    icon: ImageVector,
    color: Color,
    today: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(20.dp).background(color, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, title, tint = Color.White, modifier = Modifier.size(13.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                title,
                fontSize = TypeScale.bodyS,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 权重给中间的 Spacer：分类名靠左、计数贴右（**原始版式**，用户明确要它）。
            //
            // 英文放不下的问题不在版式而在**计数的文案**：三张胶囊平分一屏宽，
            // 每张内宽 ~95dp，扣掉图标与间距只剩 ~69dp，而「Records」+「Today: 12」
            // 约 96dp —— 横排怎么让位都会把某一头截掉（先是挤成「PlansToday 0」，
            // 后来是「P…」）。所以英文的计数只给数字（见 `life_home_chip_today`），
            // 中文仍是「今日 N」（中文宽度够，且用户要的就是这个）。
            Spacer(modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.life_home_chip_today, today),
                fontSize = TypeScale.labelS,
                color = if (today > 0) color else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CategoryMiniCard(
    title: String,
    icon: ImageVector,
    color: Color,
    count: LifeCalendarViewModel.CategoryCount?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(24.dp).background(color, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, title, tint = Color.White, modifier = Modifier.size(15.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontSize = TypeScale.bodyM, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${count?.total ?: 0}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                stringResource(R.string.life_home_today_count, count?.today ?: 0),
                fontSize = TypeScale.labelS,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ───────────────────────── 今日看板 ─────────────────────────

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun TodayBoardHomeCard(
    modifier: Modifier = Modifier,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onOpenDayRead: (LocalDate) -> Unit,
    weekMode: Boolean,
    onWeekModeChange: (Boolean) -> Unit,
    dayMap: Map<LocalDate, LifeCalendarViewModel.DayCalInfo>,
    overdueItems: List<LifeItemDao.LifeBoardItemRow>,
    onThisDayItems: List<LifeItemDao.OnThisDayRow> = emptyList(),
    /** 快捷添加：一句话记一笔（解析日期时间落到待办模板）。 */
    onQuickAdd: (String) -> Unit = {},
    scheduledItems: List<LifeItemDao.LifeBoardItemRow>,
    templates: List<LifeTemplate>,
    onOpenDetail: (Long) -> Unit,
    onSetItemStatus: (itemId: Long, completed: Boolean?) -> Unit,
    onReschedule: (Long) -> Unit,
    onRescheduleAllOverdue: (itemIds: List<Long>) -> Unit,
    onDeleteItem: (Long) -> Unit,
    /** 用户用滑动操作过一次 → 关闭教学卡（学会即隐）。 */
    onDismissSwipeTip: () -> Unit,
    onOpenFullList: (LifeFullListMode) -> Unit
) {
    Card(
        modifier = modifier.clip(MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            val isToday = selectedDate == LocalDate.now()
            val boardTitle = if (isToday) {
                stringResource(R.string.life_home_today_board)
            } else {
                stringResource(R.string.life_home_board_selected, selectedDate.monthValue, selectedDate.dayOfMonth)
            }
            LifeMonthCalendar(
                title = boardTitle,
                selectedDate = selectedDate,
                weekMode = weekMode,
                onWeekModeChange = onWeekModeChange,
                dayMap = dayMap,
                onSelectDate = onSelectDate,
                onOpenDayRead = onOpenDayRead
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 快捷添加（滴答清单式一句话记录）：仅「今天」视图。
            if (isToday) {
                QuickAddBar(onSubmit = onQuickAdd)
                Spacer(modifier = Modifier.height(8.dp))
            }
            // 滑动确认（左滑删除 / 右滑完成）：状态提升到看板卡，逾期与今日安排共用一套弹窗；
            // 行首勾圈不走这条路径（就地完成、一步可撤销）。
            var pendingSwipe by remember { mutableStateOf<PendingSwipe?>(null) }
            // 逾期任务置顶（滴答清单模式）：分类色行 + 一键推迟到今天（仅「今天」视图显示）。
            // 但当日安排列表已包含这些条目（同一天到期，只是时刻已过），再列一遍会重复渲染，
            // 故顶部只保留「不在当日安排里」的逾期项 —— 正常情况即过去日期遗留的未完成项。
            val scheduledIds = remember(scheduledItems) { scheduledItems.map { it.itemId }.toSet() }
            val overdueNotScheduled = if (isToday) {
                overdueItems.filter { it.itemId !in scheduledIds }
            } else {
                emptyList()
            }
            // 看板区左右滑 = 前后一天（日历类 app 的标配手势，纯手势零占位）。
            // 挂在任务区容器上：行自身的左滑删除/右滑完成会优先消费横向手势，
            // 只有从标题行、行间隙、页脚等空白处起手的滑动才会切换日期。
            var dragAccumulated by remember { mutableStateOf(0f) }
            val daySwipe = Modifier.pointerInput(selectedDate) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulated = 0f },
                    onDragEnd = {
                        val threshold = 72.dp.toPx()
                        when {
                            dragAccumulated <= -threshold -> onSelectDate(selectedDate.plusDays(1))
                            dragAccumulated >= threshold -> onSelectDate(selectedDate.minusDays(1))
                        }
                    }
                ) { _, dragAmount ->
                    dragAccumulated += dragAmount
                }
            }
            Column(modifier = daySwipe) {
                OverdueSection(
                    items = overdueNotScheduled,
                    onOpenDetail = onOpenDetail,
                    onRequestComplete = { id, done -> pendingSwipe = PendingSwipe.Complete(id, done) },
                    onToggleComplete = { id -> onSetItemStatus(id, null) },
                    onReschedule = onReschedule,
                    onRescheduleAll = { onRescheduleAllOverdue(overdueNotScheduled.map { it.itemId }) },
                    onRequestDelete = { pendingSwipe = PendingSwipe.Delete(it) },
                    onOpenFullList = { onOpenFullList(LifeFullListMode.OVERDUE) }
                )
                DayAgenda(
                    items = scheduledItems,
                    selectedDate = selectedDate,
                    templates = templates,
                    onOpenDetail = onOpenDetail,
                    onRequestComplete = { id, done -> pendingSwipe = PendingSwipe.Complete(id, done) },
                    onToggleComplete = { id -> onSetItemStatus(id, null) },
                    onRequestDelete = { pendingSwipe = PendingSwipe.Delete(it) },
                    onOpenFullList = { onOpenFullList(LifeFullListMode.AGENDA) }
                )
            }
            // 底部一行 = 「接下来」的轻量信息带：明天预告（点击切到明天）+
            // 那年今天胶囊（点击展开回忆条），只占一行、各有明确目标。
            if (isToday) {
                val tomorrow = LocalDate.now().plusDays(1)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TomorrowPreviewRow(
                        count = dayMap[tomorrow]?.count ?: 0,
                        weekday = stringResource(weekdayShortRes(tomorrow.dayOfWeek)),
                        onSelectTomorrow = { onSelectDate(tomorrow) },
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (onThisDayItems.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        OnThisDayPill(
                            count = onThisDayItems.size,
                            items = onThisDayItems,
                            onOpenDetail = onOpenDetail
                        )
                    }
                }
            }
            pendingSwipe?.let { pending ->
                SwipeConfirmDialog(
                    pending = pending,
                    onConfirm = {
                        when (pending) {
                            is PendingSwipe.Delete -> onDeleteItem(pending.itemId)
                            is PendingSwipe.Complete -> onSetItemStatus(pending.itemId, pending.toCompleted)
                        }
                        // 用户已用滑动操作过一次 → 教学卡自动消失（学会即隐）
                        onDismissSwipeTip()
                        pendingSwipe = null
                    },
                    onDismiss = { pendingSwipe = null }
                )
            }
        }
    }
}

/** 看板滑动待确认动作（左滑删除 / 右滑完成或取消完成）。 */
internal sealed interface PendingSwipe {
    data class Delete(val itemId: Long) : PendingSwipe
    data class Complete(val itemId: Long, val toCompleted: Boolean) : PendingSwipe
}

/** 滑动误操作防护：删除 / 完成均需二次确认。 */
@Composable
internal fun SwipeConfirmDialog(
    pending: PendingSwipe,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    when (pending) {
        is PendingSwipe.Delete -> SwipeDeleteConfirmDialog(onConfirm, onDismiss)
        is PendingSwipe.Complete -> SwipeCompleteConfirmDialog(pending.toCompleted, onConfirm, onDismiss)
    }
}

@Composable
private fun SwipeDeleteConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.life_detail_delete_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = { Text(stringResource(R.string.life_detail_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.life_detail_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        }
    )
}

@Composable
private fun SwipeCompleteConfirmDialog(toCompleted: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (toCompleted) {
                        R.string.life_board_swipe_complete_title
                    } else {
                        R.string.life_board_swipe_uncomplete_title
                    }
                ),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                stringResource(
                    if (toCompleted) {
                        R.string.life_board_swipe_complete_message
                    } else {
                        R.string.life_board_swipe_uncomplete_message
                    }
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(
                        if (toCompleted) {
                            R.string.life_board_swipe_complete_confirm
                        } else {
                            R.string.life_board_swipe_uncomplete_confirm
                        }
                    ),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        }
    )
}

/**
 * 看板顶部逾期区：与「今日安排」同款分类色扁平行（仅今日视图由调用方控制显隐）。
 *
 * 标题固定为「已逾期」——调用方已把「当天到期、已排进当日安排」的条目剔除，
 * 这里剩下的都是**过去日期遗留**的未完成项，语义与标题一致；
 * 若与「今日安排」混排，则标题由调用方另行处理（见 TodayBoardHomeCard）。
 */
@Composable
private fun OverdueSection(
    items: List<LifeItemDao.LifeBoardItemRow>,
    onOpenDetail: (Long) -> Unit,
    onRequestComplete: (itemId: Long, toCompleted: Boolean) -> Unit,
    onToggleComplete: (Long) -> Unit,
    onReschedule: (Long) -> Unit,
    onRescheduleAll: (itemIds: List<Long>) -> Unit,
    onRequestDelete: (Long) -> Unit,
    onOpenFullList: () -> Unit
) {
    // 没有逾期就整个不渲染：红区标题常驻是视觉噪音（TickTick/Todoist 均如此，
    // 「暂无逾期事项」的空态文案只保留给完整清单页）。
    if (items.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.life_board_overdue),
            fontSize = TypeScale.bodyS,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        // 批量推迟（滴答清单模式）：多于一条时出现；推迟是低风险操作，不逐条确认
        if (items.size > 1) {
            Text(
                stringResource(R.string.life_home_overdue_reschedule_all),
                fontSize = TypeScale.bodyS,
                fontWeight = FontWeight.Medium,
                color = ModuleLife,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { onRescheduleAll(items.map { it.itemId }) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
    // 与「今日安排」「待安排」共用同一预览口径：多了只列前 N 条，其余折叠成一行计数。
    items.take(LIST_PREVIEW_COUNT).forEach { item ->
        SwipeTaskRow(
            title = item.title,
            category = item.category,
            completed = item.status == "COMPLETED",
            onClick = { onOpenDetail(item.itemId) },
            onDelete = { onRequestDelete(item.itemId) },
            onSwipeComplete = { done -> onRequestComplete(item.itemId, done) },
            onToggleComplete = { onToggleComplete(item.itemId) },
            trailing = {
                Text(
                    stringResource(R.string.life_board_reschedule),
                    fontSize = TypeScale.bodyS,
                    fontWeight = FontWeight.Medium,
                    color = if (item.status == "COMPLETED") {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    } else {
                        ModuleLife
                    },
                    modifier = Modifier
                        .clickable(
                            enabled = item.status != "COMPLETED",
                            role = Role.Button
                        ) { onReschedule(item.itemId) }
                        .padding(start = 8.dp)
                )
            }
        )
    }
    BoardListFooter(total = items.size, shown = LIST_PREVIEW_COUNT, onOpenFullList = onOpenFullList)
    Spacer(modifier = Modifier.height(2.dp))
}

/** 分类 DB 值 → chip 展示文案（复用分类卡文案，不新造词）。 */
@Composable
internal fun categoryChipLabel(category: String): String = when (category) {
    CAT_PLAN -> stringResource(R.string.life_category_plan)
    CAT_TIME -> stringResource(R.string.life_category_time)
    CAT_RECORD -> stringResource(R.string.life_category_record)
    else -> category
}

internal fun weekdayShortRes(day: DayOfWeek): Int = when (day) {
    DayOfWeek.MONDAY -> R.string.date_weekday_short_mon
    DayOfWeek.TUESDAY -> R.string.date_weekday_short_tue
    DayOfWeek.WEDNESDAY -> R.string.date_weekday_short_wed
    DayOfWeek.THURSDAY -> R.string.date_weekday_short_thu
    DayOfWeek.FRIDAY -> R.string.date_weekday_short_fri
    DayOfWeek.SATURDAY -> R.string.date_weekday_short_sat
    DayOfWeek.SUNDAY -> R.string.date_weekday_short_sun
}

/**
 * 当日安排列表（设计稿：📋 今日安排 · M月d日 周X + 分类色 chip 圆角行）。
 * 按 `dueTime` 升序、**无时间置顶**（随时可做的排前面，有时刻的按时间表排下面，
 * 对齐滴答清单/Google 日历的全天位；SQL 的 `ORDER BY dueTime ASC` 本就是 NULL 在前）。
 */
@Composable
private fun DayAgenda(
    items: List<LifeItemDao.LifeBoardItemRow>,
    selectedDate: LocalDate,
    templates: List<LifeTemplate>,
    onOpenDetail: (Long) -> Unit,
    onRequestComplete: (itemId: Long, toCompleted: Boolean) -> Unit,
    onToggleComplete: (Long) -> Unit,
    onRequestDelete: (Long) -> Unit,
    onOpenFullList: () -> Unit
) {
    if (items.isEmpty()) {
        DayAgendaEmpty()
        return
    }
    val doneCount = items.count { it.status == "COMPLETED" }
    if (doneCount == items.size) {
        // 全部完成：列表让位给庆祝空态（Todoist/Things 的情绪闭环），已完成件数
        // 从完整清单页可回看/可取消完成 —— 首页不再让划掉的行常驻占位。
        DayAgendaAllDone(doneCount, onOpenFullList)
        return
    }
    DayAgendaHeader(selectedDate, doneCount, items.size)
    val categoryByTemplate = remember(templates) { templates.associate { it.id to it.category } }
    // 未完成项按时间排序占用预览名额；已完成项收进「已完成 N 件」可展开行
    // （Apple 提醒事项/滴答清单默认收起的做法，划掉的不消耗首屏空间）。
    val active = remember(items) { items.filter { it.status != "COMPLETED" } }
    val completed = remember(items) {
        items.filter { it.status == "COMPLETED" }
            .sortedWith(compareBy(nullsFirst(naturalOrder())) { it.dueTime })
    }
    val sorted = remember(active) {
        active.sortedWith(compareBy(nullsFirst(naturalOrder())) { it.dueTime })
    }
    // 与「逾期」「待安排」共用同一预览口径（条数按完整清单计，标题里的总数不缩水）。
    sorted.take(LIST_PREVIEW_COUNT).forEach { item ->
        SwipeTaskRow(
            title = item.title,
            category = categoryByTemplate[item.templateId],
            completed = item.status == "COMPLETED",
            onClick = { onOpenDetail(item.itemId) },
            onDelete = { onRequestDelete(item.itemId) },
            onSwipeComplete = { done -> onRequestComplete(item.itemId, done) },
            onToggleComplete = { onToggleComplete(item.itemId) },
            trailing = item.dueTime?.let { t ->
                {
                    Text(
                        agendaTime(t),
                        fontSize = TypeScale.bodyS,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
    BoardListFooter(total = active.size, shown = LIST_PREVIEW_COUNT, onOpenFullList = onOpenFullList)
    // 已完成收起行：默认只留一行读数，展开后可就地取消完成
    if (completed.isNotEmpty()) {
        CompletedCollapsedSection(
            completed = completed,
            categoryByTemplate = categoryByTemplate,
            onOpenDetail = onOpenDetail,
            onRequestComplete = onRequestComplete,
            onToggleComplete = onToggleComplete,
            onRequestDelete = onRequestDelete
        )
    }
}

/** 「▸ 已完成 N 件」收起行：默认折叠，展开后已完成行就地可取消完成。 */
@Composable
private fun CompletedCollapsedSection(
    completed: List<LifeItemDao.LifeBoardItemRow>,
    categoryByTemplate: Map<Long, String>,
    onOpenDetail: (Long) -> Unit,
    onRequestComplete: (itemId: Long, toCompleted: Boolean) -> Unit,
    onToggleComplete: (Long) -> Unit,
    onRequestDelete: (Long) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Text(
        stringResource(
            if (expanded) R.string.life_board_completed_expanded
            else R.string.life_board_completed_collapsed,
            completed.size
        ),
        fontSize = TypeScale.bodyS,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button) { expanded = !expanded }
            .padding(top = 6.dp, bottom = 2.dp, start = 2.dp)
    )
    if (expanded) {
        completed.forEach { item ->
            SwipeTaskRow(
                title = item.title,
                category = categoryByTemplate[item.templateId],
                completed = true,
                onClick = { onOpenDetail(item.itemId) },
                onDelete = { onRequestDelete(item.itemId) },
                onSwipeComplete = { done -> onRequestComplete(item.itemId, done) },
                onToggleComplete = { onToggleComplete(item.itemId) },
                trailing = item.dueTime?.let { t ->
                    {
                        Text(
                            agendaTime(t),
                            fontSize = TypeScale.bodyS,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    }
}

/** 生活页统一空态：柔色圆托底的图标 + 主文案 + 可选副文案 / 动作。多处空态复用，保证视觉语言一致。 */
@Composable
internal fun LifeEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    action: (@Composable () -> Unit)? = null,
    accent: Color = ModuleLife
) {
    Column(
        modifier = modifier.padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 柔色圆托底：空态也要有被创作的感觉（粉彩用 lerp 混表面色，暗色主题同样干净）
        val surface = MaterialTheme.colorScheme.surface
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(lerp(accent, surface, 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            title,
            fontSize = TypeScale.bodyM,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (hint != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                hint,
                fontSize = TypeScale.bodyS,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
        if (action != null) {
            Spacer(modifier = Modifier.height(8.dp))
            action()
        }
    }
}

/** 安排列表空态。 */
@Composable
private fun DayAgendaEmpty() {
    LifeEmptyState(
        icon = Icons.Outlined.EventBusy,
        title = stringResource(R.string.life_board_empty),
        hint = stringResource(R.string.life_board_empty_hint),
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 清单折叠提示：超出预览条数时出现，三段清单（逾期 / 今日安排 / 待安排）共用。
 *
 * 点击进入完整清单页；完整页与首页消费同一份数据，因此这里不是装饰性入口。
 */
@Composable
private fun BoardListFooter(total: Int, shown: Int, onOpenFullList: () -> Unit) {
    if (total <= shown) return
    Text(
        // 传**总数**而不是剩余数：文案是「查看全部 N 条」，指向的目标清单就是 N 条。
        // （此前传 total - shown，出现「查看全部 3 条」而标题写着共 7 条的自相矛盾。）
        stringResource(R.string.life_home_todo_view_all_brief, total),
        fontSize = TypeScale.labelM,
        fontWeight = FontWeight.Medium,
        color = ModuleLife,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpenFullList)
            .padding(top = 8.dp, bottom = 2.dp)
    )
}

/** 「📋 今日安排 · 9月23日 周三」标题行 + 右侧完成读数；非今日为「安排 · 9月22日 周二」。 */
@Composable
private fun DayAgendaHeader(selectedDate: LocalDate, doneCount: Int, totalCount: Int) {
    val isToday = selectedDate == LocalDate.now()
    val title = if (isToday) {
        stringResource(R.string.life_board_agenda_today)
    } else {
        stringResource(R.string.life_board_agenda_on)
    }
    val weekday = stringResource(weekdayShortRes(selectedDate.dayOfWeek))
    val datePart = stringResource(
        R.string.life_board_agenda_date,
        selectedDate.monthValue,
        selectedDate.dayOfMonth,
        weekday
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.EventNote,
            null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            title,
            fontSize = TypeScale.bodyM,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            " · $datePart",
            fontSize = TypeScale.heroUnit,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        // 今日完成读数（Things Progress Pies / TickTick 统计的同款问题：今天过得到底怎么样）
        Text(
            stringResource(R.string.life_board_agenda_progress, doneCount, totalCount),
            fontSize = TypeScale.bodyS,
            fontWeight = FontWeight.Medium,
            color = if (doneCount > 0) ModuleLife else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    // 完成进度细条：粉彩主色，与记录卡进度条同一语言（复用入场填充动画）
    LifeTrackBar(
        fraction = if (totalCount == 0) 0f else doneCount.toFloat() / totalCount,
        color = ModuleLife,
        height = 4.dp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/** 「全部完成」庆祝空态：粉彩圆托底 + 完成件数；入口留给完整清单页（回看/取消完成）。 */
@Composable
private fun DayAgendaAllDone(doneCount: Int, onOpenFullList: () -> Unit) {
    LifeEmptyState(
        icon = Icons.Outlined.CheckCircle,
        title = stringResource(R.string.life_board_all_done_title),
        hint = stringResource(R.string.life_board_all_done_hint, doneCount),
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        stringResource(R.string.life_board_all_done_view, doneCount),
        fontSize = TypeScale.labelM,
        fontWeight = FontWeight.Medium,
        color = ModuleLife,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpenFullList)
            .padding(top = 6.dp, bottom = 2.dp)
    )
}

/**
 * 单条安排：分类 tint 扁平圆角行 + 分类 chip + 标题 + 右侧内容。
 * 左滑 = 删除、右滑 = 完成（均经看板卡确认弹窗，不直接改库）。
 * 行首勾圈 = 就地完成（直接改库，一步可撤销；与滑动的「防误触」职责分开）。
 *
 * 两个入口**必须分开**：滑动要按当前状态决定弹「标记为完成？」还是「取消完成标记？」，
 * 勾圈只做一次无歧义的取反 —— 早先把两者并到同一个 `onComplete` 上，会让已完成的条目
 * 右滑时弹出「标记为完成？」（方向词与语义相反）。
 */
@Composable
private fun SwipeTaskRow(
    title: String,
    category: String?,
    completed: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onSwipeComplete: (toCompleted: Boolean) -> Unit,
    onToggleComplete: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    false
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onSwipeComplete(!completed)
                    false
                }
                SwipeToDismissBoxValue.Settled -> true
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = { SwipeTaskBackground(dismissState.dismissDirection) },
        content = {
            SwipeTaskContent(
                title = title,
                category = category,
                completed = completed,
                onClick = onClick,
                onToggleComplete = onToggleComplete,
                trailing = trailing
            )
        }
    )
}

@Composable
internal fun SwipeTaskBackground(direction: SwipeToDismissBoxValue) {
    // 未滑动时不画底：否则 tertiary/errorContainer 会从行间 2dp 间隙漏出整片色块
    if (direction == SwipeToDismissBoxValue.Settled) return
    val deleting = direction == SwipeToDismissBoxValue.EndToStart
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (deleting) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                }
            )
            .padding(horizontal = 16.dp),
        contentAlignment = if (deleting) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Icon(
            if (deleting) Icons.Outlined.Delete else Icons.Outlined.CheckCircle,
            stringResource(
                if (deleting) {
                    R.string.life_detail_delete_confirm
                } else {
                    R.string.life_item_toggle_complete
                }
            ),
            tint = if (deleting) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onTertiaryContainer
            }
        )
    }
}

@Composable
private fun SwipeTaskContent(
    title: String,
    category: String?,
    completed: Boolean,
    onClick: () -> Unit,
    onToggleComplete: () -> Unit,
    trailing: (@Composable () -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(30.dp)
                .fillMaxHeight()
                .clickable(
                    role = Role.Checkbox,
                    onClick = onToggleComplete
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (completed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                stringResource(R.string.life_item_toggle_complete),
                tint = if (completed) ModuleLife else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        if (category != null) {
            CategoryChip(category)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            title,
            fontSize = TypeScale.bodyRead,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            color = if (completed) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            // 「已完成」的视觉语言与搜索结果卡（LifeRecordCard）统一：灰 + 删除线
            textDecoration = if (completed) TextDecoration.LineThrough else null
        )
        trailing?.invoke()
    }
}

/** 分类 chip：分类浅色底 + 分类色文字（中性行底上的轻量标签）。 */
@Composable
internal fun CategoryChip(category: String) {
    val chipBg: Color = when (category) {
        CAT_PLAN -> lifePlanTint()
        CAT_TIME -> lifeTimeTint()
        CAT_RECORD -> lifeRecordTint()
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val chipFg: Color = when (category) {
        CAT_PLAN -> LifePlan
        CAT_TIME -> LifeTime
        CAT_RECORD -> LifeRecord
        else -> ModuleLife
    }
    Box(
        modifier = Modifier
            .background(chipBg, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            categoryChipLabel(category),
            fontSize = TypeScale.labelS,
            fontWeight = FontWeight.Medium,
            color = chipFg
        )
    }
}

/** 「明天 3 件 · 周六」预告行：有安排报数，没安排也留个切过去的入口。 */
@Composable
private fun TomorrowPreviewRow(
    count: Int,
    weekday: String,
    onSelectTomorrow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        if (count > 0) {
            stringResource(R.string.life_board_tomorrow_with, count, weekday)
        } else {
            stringResource(R.string.life_board_tomorrow_none, weekday)
        },
        fontSize = TypeScale.bodyS,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onSelectTomorrow)
            .padding(vertical = 6.dp, horizontal = 2.dp)
    )
}

/** `dueTime`（分钟）→ `HH:mm`。 */
private fun agendaTime(dueTime: Int): String {
    val h = (dueTime / 60).toString().padStart(2, '0')
    val m = (dueTime % 60).toString().padStart(2, '0')
    return "$h:$m"
}

// ───────────────────────── 待安排卡 ─────────────────────────

@Composable
private fun UnscheduledCard(
    modifier: Modifier = Modifier,
    unscheduledItems: List<LifeItemDao.LifeBoardItemRow>,
    onOpenDetail: (Long) -> Unit,
    onSetItemStatus: (itemId: Long, completed: Boolean?) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onScheduleItem: (itemId: Long, date: LocalDate) -> Unit,
    onDismissSwipeTip: () -> Unit,
    onOpenFullList: () -> Unit
) {
    val shown = unscheduledItems.take(LIST_PREVIEW_COUNT)
    // 滑动确认（左滑删除 / 右滑完成）：与逾期、今日安排同一套防误操作（审计 #19）
    var pendingSwipe by remember { mutableStateOf<PendingSwipe?>(null) }
    // 📅 一键排期：行尾日历图标 → M3 日期选择器 → 落库进看板（收件箱 → 整理闭环）
    var pendingScheduleId by remember { mutableStateOf<Long?>(null) }
    Card(
        modifier = modifier.clip(MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            UnscheduledHeader(
                total = unscheduledItems.size,
                showAll = shown.isNotEmpty(),
                onOpenFullList = onOpenFullList
            )
            if (shown.isEmpty()) {
                LifeEmptyState(
                    icon = Icons.AutoMirrored.Outlined.EventNote,
                    title = stringResource(R.string.life_home_todo_empty),
                    hint = stringResource(R.string.life_home_todo_empty_hint),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                // 与「今日看板」共用同一套行语言（分类 chip + 标题 + 右侧元信息），
                // 两卡在同一屏里读起来才像一家人；完成圈移到行首；滑动操作同看板。
                shown.forEach { item ->
                    UnscheduledRow(
                        item = item,
                        onClick = { onOpenDetail(item.itemId) },
                        onDelete = { pendingSwipe = PendingSwipe.Delete(item.itemId) },
                        onSwipeComplete = { done -> pendingSwipe = PendingSwipe.Complete(item.itemId, done) },
                        onToggleComplete = { onSetItemStatus(item.itemId, null) },
                        onSchedule = { pendingScheduleId = item.itemId }
                    )
                }
                BoardListFooter(
                    total = unscheduledItems.size,
                    shown = LIST_PREVIEW_COUNT,
                    onOpenFullList = onOpenFullList
                )
            }
        }
        SwipeConfirmDialogHost(
            pendingSwipe = pendingSwipe,
            onDelete = onDeleteItem,
            onComplete = { id, done -> onSetItemStatus(id, done) },
            onDismiss = { pendingSwipe = null },
            onSwipeLearned = onDismissSwipeTip
        )
    }
    SchedulePickerHost(
        pendingItemId = pendingScheduleId,
        onPicked = onScheduleItem,
        onDismiss = { pendingScheduleId = null }
    )
}

/** 排期日期选择器 Host：把「选中即落库 + 关弹窗」两条回调收拢（待安排卡专用）。 */
@Composable
private fun SchedulePickerHost(
    pendingItemId: Long?,
    onPicked: (itemId: Long, date: LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    pendingItemId ?: return
    ScheduleDatePickerDialog(
        onConfirm = { date ->
            onPicked(pendingItemId, date)
            onDismiss()
        },
        onDismiss = onDismiss
    )
}

/** 看板/待安排共用的滑动确认弹窗 Host：把「按方向执行」与「关闭弹窗」两条回调收拢。 */
@Composable
private fun SwipeConfirmDialogHost(
    pendingSwipe: PendingSwipe?,
    onDelete: (Long) -> Unit,
    onComplete: (itemId: Long, toCompleted: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSwipeLearned: () -> Unit = {}
) {
    pendingSwipe?.let { pending ->
        SwipeConfirmDialog(
            pending = pending,
            onConfirm = {
                when (pending) {
                    is PendingSwipe.Delete -> onDelete(pending.itemId)
                    is PendingSwipe.Complete -> onComplete(pending.itemId, pending.toCompleted)
                }
                onSwipeLearned()
                onDismiss()
            },
            onDismiss = onDismiss
        )
    }
}

/** 待安排卡标题行：标题 + 右侧「全部 N ›」入口（有内容时才显示）。 */
@Composable
private fun UnscheduledHeader(total: Int, showAll: Boolean, onOpenFullList: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HomeCardTitle(stringResource(R.string.life_home_todo), modifier = Modifier.weight(1f))
        if (showAll) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onOpenFullList)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.life_home_todo_total, total),
                    fontSize = TypeScale.bodyS,
                    fontWeight = FontWeight.Medium,
                    color = ModuleLife
                )
                Icon(
                    Icons.Filled.ChevronRight,
                    null,
                    tint = ModuleLife,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** 待安排行：标题 + 模板名 + 📅 一键排期（收件箱 → 看板的整理闭环）。 */
@Composable
private fun UnscheduledRow(
    item: LifeItemDao.LifeBoardItemRow,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onSwipeComplete: (toCompleted: Boolean) -> Unit,
    onToggleComplete: () -> Unit,
    onSchedule: () -> Unit
) {
    SwipeTaskRow(
        title = item.title,
        category = item.category,
        completed = item.status == "COMPLETED",
        onClick = onClick,
        onDelete = onDelete,
        onSwipeComplete = onSwipeComplete,
        onToggleComplete = onToggleComplete,
        trailing = { UnscheduledRowTrailing(item.templateName, onSchedule) }
    )
}

/** 行尾元信息：模板名 + 📅 排期按钮（待安排条目没有日期，模板名补足信息）。 */
@Composable
private fun UnscheduledRowTrailing(templateName: String, onSchedule: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            templateName,
            fontSize = TypeScale.labelS,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(lerp(ModuleLife, MaterialTheme.colorScheme.surface, 0.88f))
                .clickable(role = Role.Button, onClick = onSchedule),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Event,
                stringResource(R.string.life_unsched_schedule),
                tint = ModuleLife,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * 待安排排期的 M3 日期选择器：默认今天；`selectedDateMillis` 是 UTC 零点，
 * 用 `LocalDate.ofEpochDay(millis / 86400000)` 换算成日历日（与表单选择器同口径）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDatePickerDialog(onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        tonalElevation = 0.dp,
        colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        onConfirm(LocalDate.ofEpochDay(ms / 86_400_000L))
                    }
                },
                enabled = pickerState.selectedDateMillis != null
            ) {
                Text(stringResource(R.string.life_unsched_schedule), color = ModuleLife, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        }
    ) {
        DatePicker(state = pickerState, colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.background))
    }
}

@Composable
private fun HomeCardTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

/**
 * 一次性教学卡：滑动手势怎么用（**右滑完成、左滑删除**）。
 *
 * 统一框架里的「一次性教学」：教在该页（生活看板/清单的滑动就在这里）、
 * 可永久关闭（「不再提示」→ 按消息 id 持久化）、**学会即隐**
 * （用户用滑动操作过一次后自动关闭，见 SwipeConfirmDialog 的确认回调）。
 */
@Composable
private fun SwipeTipCard(modifier: Modifier = Modifier, onDismiss: () -> Unit) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(lerp(ModuleLife, MaterialTheme.colorScheme.surface, 0.92f))
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Swipe,
            contentDescription = null,
            tint = ModuleLife,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.life_swipe_tip),
            fontSize = TypeScale.labelM,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(R.string.life_tip_dismiss),
            fontSize = TypeScale.labelS,
            color = ModuleLife,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onDismiss)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ───────────────────────── 空状态 ─────────────────────────

/**
 * 「所有模板都已关闭」轻量空态：用户主动关掉了全部模板，
 * 只给一句说明 + 「去模板管理」入口。
 */
@Composable
private fun AllTemplatesClosedState(onOpenManage: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.xl),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            LifeEmptyState(
                icon = Icons.Outlined.GridView,
                title = stringResource(R.string.life_template_all_closed),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = 4.dp),
                action = {
                    TextButton(onClick = onOpenManage) {
                        Text(
                            stringResource(R.string.life_template_go_manage),
                            color = ModuleLife,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            )
        }
    }
}

// ───────────────────────── 创建面板（真实模板分组） ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FabSheet(
    templates: List<LifeTemplate>,
    lastLogByTemplate: Map<Long, Long>,
    onDismiss: () -> Unit,
    onTemplateClick: (LifeTemplate) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .navigationBarsPadding()
                .padding(bottom = Spacing.sm)
        ) {
            Text(
                stringResource(R.string.life_select_type_to_create),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            // §5.0：系统型模板（专注等）不进创建选择器，只在此处过滤，不动 ViewModel 源头
            val creatable = remember(templates) { templates.filterNot { it.isSpecial } }
            var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
            var search by remember { mutableStateOf("") }
            val categories = remember(creatable) { creatable.map { it.category }.distinct() }
            // 有效分类 = 选中值仍存在；数据变化后消失则收敛回「全部」
            val effectiveCategory = selectedCategory?.takeIf { it in categories }
            val shown = remember(creatable, effectiveCategory, search) {
                filterCreatable(creatable, effectiveCategory, search)
            }
            // 最近使用（≤3）：长按 FAB 直达的显性版，把高频模板摆到第一层；全新用户整行隐藏
            val today = remember { LocalDate.now() }
            val recent = remember(creatable, lastLogByTemplate) {
                recentTemplates(creatable, lastLogByTemplate)
            }
            FabRecentSection(
                recent = recent,
                lastLogByTemplate = lastLogByTemplate,
                today = today,
                onTemplateClick = onTemplateClick
            )
            FabSheetHeader(
                search = search,
                onSearchChange = { search = it },
                categories = categories,
                selectedCategory = effectiveCategory,
                onSelectCategory = { selectedCategory = it }
            )
            Spacer(Modifier.height(12.dp))
            if (shown.isEmpty()) {
                LifeEmptyState(
                    icon = Icons.Outlined.Search,
                    title = stringResource(R.string.life_search_no_result),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                FabSheetGrid(shown, lastLogByTemplate, today, onTemplateClick)
            }
        }
    }
}

/** 「最近使用」行最多直带的模板数：再多就该用搜索而不是扫胶囊。 */
private const val FAB_RECENT_LIMIT = 3

/** 最近使用的模板（按上次记录时间倒序取前 [FAB_RECENT_LIMIT] 个）。 */
private fun recentTemplates(creatable: List<LifeTemplate>, lastLogByTemplate: Map<Long, Long>): List<LifeTemplate> =
    creatable.mapNotNull { tpl -> lastLogByTemplate[tpl.id]?.let { tpl to it } }
        .sortedByDescending { it.second }
        .take(FAB_RECENT_LIMIT)
        .map { it.first }

/** 「上次记录」短文案（最近胶囊与网格卡共用）：今天 / N 天前 / 从未。 */
@Composable
private fun lastLoggedShort(lastMs: Long?, today: LocalDate): String =
    when (val d = relativeDaysSince(lastMs, today)) {
        null -> stringResource(R.string.life_tpl_last_short_never)
        0L -> stringResource(R.string.life_tpl_last_short_today)
        else -> stringResource(R.string.life_tpl_last_short_days, d)
    }

/** 最近使用的模板胶囊行（图标 tint 圆点 + 名称 + 上次短文案），单击直达表单；空列表整行隐藏。 */
@Composable
private fun FabRecentSection(
    recent: List<LifeTemplate>,
    lastLogByTemplate: Map<Long, Long>,
    today: LocalDate,
    onTemplateClick: (LifeTemplate) -> Unit
) {
    if (recent.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        recent.forEach { tpl ->
            val accent = identityColor(tpl.color)
            Surface(
                onClick = { onTemplateClick(tpl) },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(start = 5.dp, end = 9.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(accent.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(iconFor(tpl.icon), null, tint = accent, modifier = Modifier.size(12.dp))
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(
                        tpl.getDisplayName(LocalContext.current),
                        fontSize = TypeScale.bodyS,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        lastLoggedShort(lastLogByTemplate[tpl.id], today),
                        fontSize = TypeScale.labelS,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

/** 创建面板的筛选：先按分类，再按模板名子串。 */
private fun filterCreatable(
    creatable: List<LifeTemplate>,
    category: String?,
    search: String
): List<LifeTemplate> {
    val byCat = if (category == null) creatable else creatable.filter { it.category == category }
    val query = search.trim()
    return if (query.isEmpty()) byCat else byCat.filter { it.name.contains(query, ignoreCase = true) }
}

/**
 * 创建面板的筛选头（搜索框常驻 + 分类图标 tab）。
 *
 * 搜索常驻对标 Notion 模板选择器——原「≥6 个模板才显示」的门槛已去掉；
 * 分类 tab 携带类别身份色（选中 = tint 底 + 类别色描边），与首页分类卡同源。
 * 仍从 [FabSheet] 抽出：面板本身只负责"取数 + 排布"，筛选控件单独可改。
 */
@Composable
private fun FabSheetHeader(
    search: String,
    onSearchChange: (String) -> Unit,
    categories: List<String>,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit
) {
    FabSearchField(search = search, onSearchChange = onSearchChange)
    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CategoryTab(
            label = stringResource(R.string.life_category_filter_all),
            icon = Icons.Filled.Apps,
            accent = ModuleLife,
            tint = ModuleLife.copy(alpha = 0.12f),
            selected = selectedCategory == null,
            onClick = { onSelectCategory(null) },
            modifier = Modifier.weight(1f)
        )
        categories.forEach { cat ->
            val (accent, tint) = fabCategoryVisual(cat)
            CategoryTab(
                label = categoryChipLabel(cat),
                icon = fabCategoryIcon(cat),
                accent = accent,
                tint = tint,
                selected = selectedCategory == cat,
                onClick = { onSelectCategory(cat) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 创建面板的模板搜索框：常驻第一层（对标 Notion 模板选择器），不自动弹键盘。 */
@Composable
private fun FabSearchField(search: String, onSearchChange: (String) -> Unit) {
    BasicTextField(
        value = search,
        onValueChange = onSearchChange,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Search,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box {
                    if (search.isEmpty()) {
                        Text(
                            stringResource(R.string.life_tpl_search_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    }
                    inner()
                }
            }
        }
    )
}

/** 分类 → (身份色, tint 底)：已知三分类走主题真源色与暗色适配 tint，自定义分类退回模块色。 */
@Composable
private fun fabCategoryVisual(category: String): Pair<Color, Color> = when (category) {
    CAT_PLAN -> LifePlan to lifePlanTint()
    CAT_TIME -> LifeTime to lifeTimeTint()
    CAT_RECORD -> LifeRecord to lifeRecordTint()
    else -> ModuleLife to ModuleLife.copy(alpha = 0.12f)
}

private fun fabCategoryIcon(category: String): ImageVector = when (category) {
    CAT_PLAN -> Icons.Filled.Star
    CAT_TIME -> Icons.Filled.CalendarMonth
    CAT_RECORD -> Icons.Filled.AutoStories
    else -> Icons.AutoMirrored.Filled.Label
}

/** 创建面板的分类 tab：图标 + 名称，选中 = tint 底 + 类别色描边。 */
@Composable
private fun CategoryTab(
    label: String,
    icon: ImageVector,
    accent: Color,
    tint: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(11.dp)
    Column(
        modifier = modifier
            .background(if (selected) tint else Color.Transparent, shape)
            .border(
                width = 1.dp,
                color = if (selected) accent.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            icon,
            label,
            tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp)
        )
        Text(
            label,
            fontSize = TypeScale.labelS,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 创建面板的模板网格（三列，一屏 9 个；最高 420dp 后内部滚动）。 */
@Composable
private fun FabSheetGrid(
    shown: List<LifeTemplate>,
    lastLogByTemplate: Map<Long, Long>,
    today: LocalDate,
    onTemplateClick: (LifeTemplate) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(shown, key = { it.id }) { tpl ->
            FabSheetTemplateCard(tpl, lastLogByTemplate[tpl.id], today) { onTemplateClick(tpl) }
        }
    }
}

@Composable
private fun FabSheetTemplateCard(tpl: LifeTemplate, lastMs: Long?, today: LocalDate, onClick: () -> Unit) {
    val accent = identityColor(tpl.color)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accent.copy(alpha = 0.12f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(iconFor(tpl.icon), null, tint = accent, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.height(7.dp))
            Text(
                tpl.getDisplayName(LocalContext.current),
                fontSize = TypeScale.bodyS,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 「上次记录」短文案：回答此刻"这个该不该现在记一笔"（对标 MarkTimes 的「上次发生」）。
            Text(
                lastLoggedShort(lastMs, today),
                fontSize = TypeScale.labelS,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 快捷添加 snackbar 的成品文案（stringResource 只能在组合期取，先在此备好）。 */
private data class QuickAddSnackbarCopy(val message: String, val action: String?)

/** 把 [LifeCalendarViewModel.QuickAddOutcome] 翻成 snackbar 文案与动作标签。 */
@Composable
private fun quickAddSnackbarCopy(
    outcome: LifeCalendarViewModel.QuickAddOutcome?,
    today: LocalDate
): QuickAddSnackbarCopy = when {
    outcome == null -> QuickAddSnackbarCopy(message = "", action = null)
    outcome.noTemplate -> QuickAddSnackbarCopy(
        message = stringResource(R.string.life_quick_add_no_template),
        action = stringResource(R.string.life_quick_add_go_manage)
    )
    else -> {
        val whenParts = buildList {
            if (outcome.date != today) {
                add(
                    stringResource(
                        R.string.life_quick_add_when_date,
                        outcome.date.monthValue,
                        outcome.date.dayOfMonth
                    )
                )
            }
            outcome.time?.let { add("%02d:%02d".format(it.hour, it.minute)) }
        }
        val message = buildString {
            append(stringResource(R.string.life_quick_add_done, outcome.title))
            if (outcome.demoMode) {
                append(stringResource(R.string.life_quick_add_demo_suffix))
            }
            if (whenParts.isNotEmpty()) {
                append(" · ")
                append(whenParts.joinToString(" "))
            }
        }
        val action = when {
            outcome.demoMode -> stringResource(R.string.life_quick_add_close_demo)
            outcome.itemId != null -> stringResource(R.string.life_quick_add_undo)
            else -> null
        }
        QuickAddSnackbarCopy(message = message, action = action)
    }
}

/** 快捷添加条：一句话记一笔（「明天下午3点 开会」→ 解析日期时间落到待办）。 */
@Composable
private fun QuickAddBar(onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val submit: () -> Unit = {
        if (text.isNotBlank()) {
            onSubmit(text.trim())
            text = ""
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) {
                Text(
                    stringResource(R.string.life_quick_add_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        IconButton(onClick = { submit() }, enabled = text.isNotBlank(), modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.life_quick_add_send),
                modifier = Modifier.size(16.dp),
                tint = if (text.isNotBlank()) {
                    ModuleLife
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/** 那年今天胶囊：默认收成一行读数（「🕰 那年今天 · 2 条 ▸」），点击就地展开回忆条。 */
@Composable
private fun OnThisDayPill(
    count: Int,
    items: List<LifeItemDao.OnThisDayRow>,
    onOpenDetail: (Long) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        Text(
            stringResource(R.string.life_on_this_day_brief, count),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = ModuleLife,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(lerp(ModuleLife, MaterialTheme.colorScheme.surface, 0.9f))
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            OnThisDaySection(items = items, onOpenDetail = onOpenDetail)
        }
    }
}

/** 那年今天回忆条：粉彩 wash 细条 + 年份胶囊，点击直达记录详情（Timehop 式情怀唤醒）。 */
@Composable
private fun OnThisDaySection(
    items: List<LifeItemDao.OnThisDayRow>,
    onOpenDetail: (Long) -> Unit
) {
    val surface = MaterialTheme.colorScheme.surface
    val wash = lerp(ModuleLife, surface, 0.9f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(wash, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            stringResource(R.string.life_on_this_day),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        items.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDetail(row.itemId) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${row.year}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .background(ModuleLife, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(Modifier.width(8.dp))
                Icon(iconFor(row.icon), null, tint = ModuleLife, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    row.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
