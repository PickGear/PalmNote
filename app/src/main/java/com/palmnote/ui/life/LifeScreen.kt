@file:Suppress("TooManyFunctions")

package com.palmnote.ui.life

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.app.R
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// ───────────────────────── 生活页首页（旧版三卡布局回归） ─────────────────────────
//
// 2026-09-23 用户定案：首页 UI 改回旧版（截图版）——顶栏「生活」+ 统计/搜索/管理三图标、
// 三张分类卡、「今日看板」（周历 + 当日安排 + 逾期置顶）、「待办」卡、「+ 新建」FAB。
// **架构不变**：数据全部来自 LifeCalendarViewModel（演示感知互斥），点条目进详情页，
// 新建走记录填写页（LifeCreateRecord）。日历 / 全部独立视图随本轮一并退役。

/** 模板分类的 DB 常量值（中文，与 locale 无关；分类卡的 key，展示文案走 strings）。 */
private const val CAT_PLAN = "计划"
private const val CAT_TIME = "时间"
private const val CAT_RECORD = "记录"

private fun lifeIdentityColor(hex: String): Color = Color(android.graphics.Color.parseColor(hex))

@Suppress("LongParameterList", "LongMethod")
@Composable
fun LifeScreen(
    /** 打开某条记录的详情页（按 itemId，详情页自己查库）。 */
    onOpenDetail: (itemId: Long) -> Unit,
    onOpenStats: () -> Unit,
    onOpenManageTemplates: () -> Unit,
    /** 点击首页分类卡：跳转到对应分类详情页（计划/时间/记录）。 */
    onOpenCategory: (category: String) -> Unit,
    /** 新建记录：传入模板 iconKey（记录填写页按 icon 反查模板）。 */
    onCreateRecord: (templateIconKey: String) -> Unit,
    demoHintVisible: Boolean = false,
    onDismissDemoHint: () -> Unit = {},
    /** 生活页是否已有内容（演示开启 或 真实条目数 > 0）。为空时显示空状态引导卡。 */
    hasContent: Boolean = true,
    /** 空状态卡「加载示例数据」：开启演示＝重新播种示例记录。 */
    onLoadDemo: () -> Unit = {},
    /** 所有模板都被关闭：显示轻量空态 + 「去模板管理」。 */
    allTemplatesClosed: Boolean = false,
    /** 全量条目行（条目 + 模板元数据；演示感知已下沉到查询端）。 */
    boardRows: List<LifeItemDao.LifeBoardItemRow> = emptyList(),
    /** 选中日的安排（今日看板时段桶；完整实体，含 dueTime）。 */
    scheduledItems: List<LifeItem> = emptyList(),
    /** 月历格子信息：每日事件数 + 分类集合（驱动粉阶热力与三色点）。 */
    calendarDayMap: Map<LocalDate, LifeCalendarViewModel.DayCalInfo> = emptyMap(),
    /** 逾期未完成条目（看板顶部红色区）。 */
    overdueItems: List<LifeItemDao.LifeBoardItemRow> = emptyList(),
    /** 待办卡条目（待办模板、非今日、未完成）。 */
    todoItems: List<LifeItemDao.LifeBoardItemRow> = emptyList(),
    /** 分类卡计数：DB 分类值 → 总数 + 今日新增。 */
    categoryCounts: Map<String, LifeCalendarViewModel.CategoryCount> = emptyMap(),
    /** 可见模板（FAB 创建面板）。 */
    templates: List<LifeTemplate> = emptyList(),
    /** 看板选中日期（月历共用，持久化）。 */
    selectedDate: LocalDate = LocalDate.now(),
    onSelectDate: (LocalDate) -> Unit = {},
    /** 看板日历视图模式（true = 周视图，持久化；首次默认周视图）。 */
    calendarWeekMode: Boolean = true,
    onCalendarWeekModeChange: (Boolean) -> Unit = {},
    /** 条目完成状态就地切换。 */
    onToggleItemStatus: (itemId: Long) -> Unit = { _ -> },
    /** 逾期条目一键推迟到今天。 */
    onReschedule: (itemId: Long) -> Unit = { _ -> },
    /** 看板左滑删除（确认弹窗通过后调用）。 */
    onDeleteItem: (itemId: Long) -> Unit = { _ -> }
) {
    var fabSheetOpen by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    BackHandler(enabled = showSearch) {
        showSearch = false
        searchQuery = ""
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
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
                // 旧版「+ 新建」扩展 FAB：单击展开创建面板
                ExtendedFloatingActionButton(
                    onClick = { fabSheetOpen = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.life_new_create)) },
                    containerColor = ModuleLife,
                    contentColor = Color.White
                )
            }
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
                !hasContent -> Box(Modifier.fillMaxSize().padding(innerPadding)) {
                    LifeEmptyState(
                        onLoadDemo = onLoadDemo,
                        onCreateFirst = { fabSheetOpen = true }
                    )
                }
                else -> HomeContent(
                    innerPadding = innerPadding,
                    categoryCounts = categoryCounts,
                    onOpenCategory = onOpenCategory,
                    selectedDate = selectedDate,
                    onSelectDate = onSelectDate,
                    calendarWeekMode = calendarWeekMode,
                    onCalendarWeekModeChange = onCalendarWeekModeChange,
                    calendarDayMap = calendarDayMap,
                    overdueItems = overdueItems,
                    scheduledItems = scheduledItems,
                    todoItems = todoItems,
                    templates = templates,
                    onOpenDetail = onOpenDetail,
                    onToggleItemStatus = onToggleItemStatus,
                    onReschedule = onReschedule,
                    onDeleteItem = onDeleteItem
                )
            }
        }

        // 创建面板（窗口级 ModalBottomSheet，盖住底部导航栏）
        if (fabSheetOpen) {
            FabSheet(
                templates = templates,
                onDismiss = { fabSheetOpen = false },
                onTemplateClick = { tpl ->
                    fabSheetOpen = false
                    onCreateRecord(tpl.icon)
                }
            )
        }

        // 演示模式的一次性说明（默认开启演示，但必须让用户知道这些是示例记录）
        if (demoHintVisible) {
            AppDialog(
                onDismissRequest = onDismissDemoHint,
                title = {
                    Text(
                        stringResource(R.string.life_demo_hint_title),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        stringResource(R.string.life_demo_hint_message),
                        fontSize = TypeScale.bodyM,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDismissDemoHint) {
                        Text(stringResource(R.string.life_demo_hint_ack))
                    }
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
    val filtered = remember(rows, query) {
        if (query.isBlank()) emptyList()
        else rows.filter { r ->
            r.title.contains(query, true) || r.templateName.contains(query, true) ||
                r.fieldsData.contains(query, true)
        }
    }
    if (query.isBlank() || filtered.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                stringResource(
                    if (query.isBlank()) R.string.life_all_search_hint else R.string.life_search_no_result
                ),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            items(filtered, key = { it.itemId }) { row ->
                FlowCard(row) { onOpenDetail(row.itemId) }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun FlowCard(row: LifeItemDao.LifeBoardItemRow, onClick: () -> Unit) {
    val accent = lifeIdentityColor(row.color)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = accent.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
                Icon(iconFor(row.icon), null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    row.templateName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
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

@Suppress("LongParameterList")
@Composable
private fun HomeContent(
    innerPadding: PaddingValues,
    categoryCounts: Map<String, LifeCalendarViewModel.CategoryCount>,
    onOpenCategory: (String) -> Unit,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    calendarWeekMode: Boolean,
    onCalendarWeekModeChange: (Boolean) -> Unit,
    calendarDayMap: Map<LocalDate, LifeCalendarViewModel.DayCalInfo>,
    overdueItems: List<LifeItemDao.LifeBoardItemRow>,
    scheduledItems: List<LifeItem>,
    todoItems: List<LifeItemDao.LifeBoardItemRow>,
    templates: List<LifeTemplate>,
    onOpenDetail: (Long) -> Unit,
    onToggleItemStatus: (Long) -> Unit,
    onReschedule: (Long) -> Unit,
    onDeleteItem: (Long) -> Unit
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
                onOpenCategory = onOpenCategory,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 12.dp)
            )
        }
        AnimatedCard(index = 1) {
            TodayBoardHomeCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
                selectedDate = selectedDate,
                onSelectDate = onSelectDate,
                weekMode = calendarWeekMode,
                onWeekModeChange = onCalendarWeekModeChange,
                dayMap = calendarDayMap,
                overdueItems = overdueItems,
                scheduledItems = scheduledItems,
                templates = templates,
                onOpenDetail = onOpenDetail,
                onToggleItemStatus = onToggleItemStatus,
                onReschedule = onReschedule,
                onDeleteItem = onDeleteItem
            )
        }
        AnimatedCard(index = 2) {
            TodoHomeCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
                todoItems = todoItems,
                onOpenDetail = onOpenDetail,
                onToggleItemStatus = onToggleItemStatus
            )
        }
        Spacer(Modifier.height(96.dp))
    }
}

// ───────────────────────── 分类卡 ─────────────────────────

/** 模板分类 → 小卡（图标 / 身份色）；计数来自真实条目，key 用 DB 中文常量。 */
@Composable
private fun CategoryHomeCard(
    counts: Map<String, LifeCalendarViewModel.CategoryCount>,
    onOpenCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            CategoryMiniCard(
                title = stringResource(R.string.life_category_plan),
                icon = Icons.Filled.Star,
                color = LifePlan,
                count = counts[CAT_PLAN],
                onClick = { onOpenCategory(CAT_PLAN) }
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            CategoryMiniCard(
                title = stringResource(R.string.life_category_time),
                icon = Icons.Filled.CalendarMonth,
                color = LifeTime,
                count = counts[CAT_TIME],
                onClick = { onOpenCategory(CAT_TIME) }
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            CategoryMiniCard(
                title = stringResource(R.string.life_category_record),
                icon = Icons.Filled.AutoStories,
                color = LifeRecord,
                count = counts[CAT_RECORD],
                onClick = { onOpenCategory(CAT_RECORD) }
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
        modifier = modifier.fillMaxWidth().clickable { onClick() },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(24.dp).background(color, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${count?.total ?: 0}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                stringResource(R.string.life_home_today_added, count?.today ?: 0),
                fontSize = 10.sp,
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
    weekMode: Boolean,
    onWeekModeChange: (Boolean) -> Unit,
    dayMap: Map<LocalDate, LifeCalendarViewModel.DayCalInfo>,
    overdueItems: List<LifeItemDao.LifeBoardItemRow>,
    scheduledItems: List<LifeItem>,
    templates: List<LifeTemplate>,
    onOpenDetail: (Long) -> Unit,
    onToggleItemStatus: (Long) -> Unit,
    onReschedule: (Long) -> Unit,
    onDeleteItem: (Long) -> Unit
) {
    Card(
        modifier = modifier.clip(MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            val isToday = selectedDate == LocalDate.now()
            val boardTitle = if (isToday) stringResource(R.string.life_home_today_board)
            else stringResource(R.string.life_home_board_selected, selectedDate.monthValue, selectedDate.dayOfMonth)
            LifeMonthCalendar(
                title = boardTitle,
                selectedDate = selectedDate,
                weekMode = weekMode,
                onWeekModeChange = onWeekModeChange,
                dayMap = dayMap,
                onSelectDate = onSelectDate
            )
            Spacer(modifier = Modifier.height(12.dp))
            // 滑动确认（左滑删除 / 右滑完成）：状态提升到看板卡，两个列表共用一套弹窗
            var pendingSwipe by remember { mutableStateOf<PendingSwipe?>(null) }
            // 逾期任务置顶（滴答清单模式）：分类色行 + 一键推迟到今天（仅「今天」视图显示）
            if (isToday && overdueItems.isNotEmpty()) {
                OverdueSection(
                    items = overdueItems,
                    onOpenDetail = onOpenDetail,
                    onRequestComplete = { id, done -> pendingSwipe = PendingSwipe.Complete(id, done) },
                    onReschedule = onReschedule,
                    onRequestDelete = { pendingSwipe = PendingSwipe.Delete(it) }
                )
            }
            DayAgenda(
                items = scheduledItems,
                selectedDate = selectedDate,
                templates = templates,
                onOpenDetail = onOpenDetail,
                onRequestComplete = { id, done -> pendingSwipe = PendingSwipe.Complete(id, done) },
                onRequestDelete = { pendingSwipe = PendingSwipe.Delete(it) }
            )
            pendingSwipe?.let { pending ->
                SwipeConfirmDialog(
                    pending = pending,
                    onConfirm = {
                        when (pending) {
                            is PendingSwipe.Delete -> onDeleteItem(pending.itemId)
                            is PendingSwipe.Complete -> onToggleItemStatus(pending.itemId)
                        }
                        pendingSwipe = null
                    },
                    onDismiss = { pendingSwipe = null }
                )
            }
        }
    }
}

/** 看板滑动待确认动作（左滑删除 / 右滑完成或取消完成）。 */
private sealed interface PendingSwipe {
    data class Delete(val itemId: Long) : PendingSwipe
    data class Complete(val itemId: Long, val toCompleted: Boolean) : PendingSwipe
}

/** 滑动误操作防护：删除 / 完成均需二次确认。 */
@Composable
private fun SwipeConfirmDialog(
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
                    if (toCompleted) R.string.life_board_swipe_complete_title
                    else R.string.life_board_swipe_uncomplete_title
                ),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                stringResource(
                    if (toCompleted) R.string.life_board_swipe_complete_message
                    else R.string.life_board_swipe_uncomplete_message
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(
                        if (toCompleted) R.string.life_board_swipe_complete_confirm
                        else R.string.life_board_swipe_uncomplete_confirm
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

/** 看板顶部逾期区：与「今日安排」同款分类色扁平行（仅今日视图由调用方控制显隐）。 */
@Composable
private fun OverdueSection(
    items: List<LifeItemDao.LifeBoardItemRow>,
    onOpenDetail: (Long) -> Unit,
    onRequestComplete: (itemId: Long, toCompleted: Boolean) -> Unit,
    onReschedule: (Long) -> Unit,
    onRequestDelete: (Long) -> Unit
) {
    Text(
        stringResource(R.string.life_board_overdue),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
    items.forEach { item ->
        SwipeTaskRow(
            title = item.title,
            category = item.category,
            completed = item.status == "COMPLETED",
            onClick = { onOpenDetail(item.itemId) },
            onDelete = { onRequestDelete(item.itemId) },
            onComplete = { onRequestComplete(item.itemId, item.status != "COMPLETED") },
            trailing = {
                TextButton(
                    onClick = { onReschedule(item.itemId) },
                    enabled = item.status != "COMPLETED",
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text(stringResource(R.string.life_board_reschedule), fontSize = 12.sp)
                }
            }
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
}

/** 分类 DB 值 → chip 展示文案（复用分类卡文案，不新造词）。 */
@Composable
private fun categoryChipLabel(category: String): String = when (category) {
    CAT_PLAN -> stringResource(R.string.life_category_plan)
    CAT_TIME -> stringResource(R.string.life_category_time)
    CAT_RECORD -> stringResource(R.string.life_category_record)
    else -> category
}

private fun weekdayShortRes(day: DayOfWeek): Int = when (day) {
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
 * 按 `dueTime` 升序、无时间置底；**不再**按早晨/上午…分桶。
 */
@Composable
private fun DayAgenda(
    items: List<LifeItem>,
    selectedDate: LocalDate,
    templates: List<LifeTemplate>,
    onOpenDetail: (Long) -> Unit,
    onRequestComplete: (itemId: Long, toCompleted: Boolean) -> Unit,
    onRequestDelete: (Long) -> Unit
) {
    if (items.isEmpty()) {
        DayAgendaEmpty()
        return
    }
    DayAgendaHeader(selectedDate)
    val categoryByTemplate = remember(templates) { templates.associate { it.id to it.category } }
    val sorted = remember(items) {
        items.sortedWith(compareBy(nullsLast(naturalOrder())) { it.dueTime })
    }
    sorted.forEach { item ->
        SwipeTaskRow(
            title = item.title,
            category = categoryByTemplate[item.templateId],
            completed = item.status == "COMPLETED",
            onClick = { onOpenDetail(item.id) },
            onDelete = { onRequestDelete(item.id) },
            onComplete = { onRequestComplete(item.id, item.status != "COMPLETED") },
            trailing = item.dueTime?.let { t ->
                {
                    Text(
                        agendaTime(t),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

/** 安排列表空态（与原时段桶空态一致）。 */
@Composable
private fun DayAgendaEmpty() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.EventBusy,
            null,
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.life_board_empty),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(R.string.life_board_empty_hint),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/** 「📋 今日安排 · 9月23日 周三」标题行；非今日为「安排 · 9月22日 周二」（日期不重复）。 */
@Composable
private fun DayAgendaHeader(selectedDate: LocalDate) {
    val isToday = selectedDate == LocalDate.now()
    val title = if (isToday) stringResource(R.string.life_board_agenda_today)
    else stringResource(R.string.life_board_agenda_on)
    val weekday = stringResource(weekdayShortRes(selectedDate.dayOfWeek))
    val datePart = stringResource(
        R.string.life_board_agenda_date,
        selectedDate.monthValue,
        selectedDate.dayOfMonth,
        weekday
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
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
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            " · $datePart",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 单条安排：分类 tint 扁平圆角行 + 分类 chip + 标题 + 右侧内容。
 * 左滑 = 删除、右滑 = 完成（均经看板卡确认弹窗，不直接改库）。
 */
@Composable
private fun SwipeTaskRow(
    title: String,
    category: String?,
    completed: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onComplete: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete(); false
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onComplete(); false
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
                trailing = trailing
            )
        }
    )
}

@Composable
private fun SwipeTaskBackground(direction: SwipeToDismissBoxValue) {
    // 未滑动时不画底：否则 tertiary/errorContainer 会从行间 2dp 间隙漏出整片色块
    if (direction == SwipeToDismissBoxValue.Settled) return
    val deleting = direction == SwipeToDismissBoxValue.EndToStart
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (deleting) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.tertiaryContainer
            )
            .padding(horizontal = 16.dp),
        contentAlignment = if (deleting) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Icon(
            if (deleting) Icons.Outlined.Delete else Icons.Outlined.CheckCircle,
            stringResource(
                if (deleting) R.string.life_detail_delete_confirm
                else R.string.life_item_toggle_complete
            ),
            tint = if (deleting) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
private fun SwipeTaskContent(
    title: String,
    category: String?,
    completed: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (category != null) {
            CategoryChip(category)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            title,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            color = if (completed) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        trailing?.invoke()
    }
}

/** 分类 chip：分类浅色底 + 分类色文字（中性行底上的轻量标签）。 */
@Composable
private fun CategoryChip(category: String) {
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
            .background(chipBg, RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            categoryChipLabel(category),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = chipFg
        )
    }
}

/** `dueTime`（分钟）→ `HH:mm`。 */
private fun agendaTime(dueTime: Int): String {
    val h = (dueTime / 60).toString().padStart(2, '0')
    val m = (dueTime % 60).toString().padStart(2, '0')
    return "$h:$m"
}

// ───────────────────────── 待办卡 ─────────────────────────

@Composable
private fun TodoHomeCard(
    modifier: Modifier = Modifier,
    todoItems: List<LifeItemDao.LifeBoardItemRow>,
    onOpenDetail: (Long) -> Unit,
    onToggleItemStatus: (Long) -> Unit
) {
    val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val overdueCount = todoItems.count { it.dueDate?.let { d -> d < todayStart } == true }
    val shown = todoItems.take(4)
    Card(
        modifier = modifier.clip(MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HomeCardTitle(stringResource(R.string.life_home_todo), modifier = Modifier.weight(1f))
                if (overdueCount > 0) {
                    Text(
                        stringResource(R.string.life_home_todo_overdue, overdueCount),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (shown.isNotEmpty()) {
                    Text(
                        stringResource(R.string.life_home_todo_total, todoItems.size),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (shown.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.EventNote,
                        null,
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.life_home_todo_empty),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.life_home_todo_empty_hint),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                shown.forEach { item ->
                    val due = item.dueDate
                    val relLabel = when {
                        due == null -> stringResource(R.string.life_home_todo_no_date)
                        due < todayStart -> {
                            val days = ((todayStart - due) / 86400000L).toInt()
                            stringResource(R.string.life_home_todo_overdue_days, days)
                        }
                        else -> {
                            val d = Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalDate()
                            stringResource(R.string.life_home_todo_due, d.monthValue, d.dayOfMonth)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onOpenDetail(item.itemId) }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.RadioButtonUnchecked,
                            stringResource(R.string.life_item_toggle_complete),
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .size(22.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onToggleItemStatus(item.itemId) }
                                .padding(4.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            item.title,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(relLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
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

// ───────────────────────── 空状态 ─────────────────────────

/**
 * 生活页空状态卡：生活页还没有内容时显示。
 * 主按钮「加载示例数据」＝开启演示；次链接「自己创建一个」＝打开创建面板。
 */
@Composable
private fun LifeEmptyState(
    onLoadDemo: () -> Unit,
    onCreateFirst: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = ModuleLife.copy(alpha = 0.12f),
            shape = CircleShape,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = ModuleLife,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.life_empty_sample_title),
            fontSize = TypeScale.headlineS,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            stringResource(R.string.life_empty_sample_subtitle),
            fontSize = TypeScale.bodyM,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(Spacing.lg))
        Button(
            onClick = onLoadDemo,
            colors = ButtonDefaults.buttonColors(containerColor = ModuleLife)
        ) {
            Text(stringResource(R.string.life_empty_load_sample))
        }
        Spacer(Modifier.height(Spacing.xxs))
        TextButton(onClick = onCreateFirst) {
            Text(stringResource(R.string.life_empty_create_first), color = ModuleLife)
        }
    }
}

/**
 * 「所有模板都已关闭」轻量空态：用户主动关掉了全部模板，
 * 只给一句说明 + 「去模板管理」入口。
 */
@Composable
private fun AllTemplatesClosedState(onOpenManage: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.life_template_all_closed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Spacing.xxs))
                TextButton(onClick = onOpenManage) {
                    Text(
                        stringResource(R.string.life_template_go_manage),
                        color = ModuleLife,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ───────────────────────── 创建面板（真实模板分组） ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FabSheet(
    templates: List<LifeTemplate>,
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
            val categories = remember(creatable) { creatable.map { it.category }.distinct() }
            // 数据变化后选中分类可能消失 ⟹ 收敛回「全部」
            val effectiveCategory = selectedCategory?.takeIf { it in categories }
            val shown = remember(creatable, effectiveCategory) {
                if (effectiveCategory == null) creatable
                else creatable.filter { it.category == effectiveCategory }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = effectiveCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text(stringResource(R.string.life_category_filter_all), fontSize = 12.sp) }
                )
                categories.forEach { cat ->
                    val label = when (cat) {
                        CAT_PLAN -> stringResource(R.string.life_category_plan)
                        CAT_TIME -> stringResource(R.string.life_category_time)
                        CAT_RECORD -> stringResource(R.string.life_category_record)
                        else -> cat
                    }
                    FilterChip(
                        selected = effectiveCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shown, key = { it.id }) { tpl ->
                    FabSheetTemplateCard(tpl) { onTemplateClick(tpl) }
                }
            }
        }
    }
}

@Composable
private fun FabSheetTemplateCard(tpl: LifeTemplate, onClick: () -> Unit) {
    val tplColor = lifeIdentityColor(tpl.color)
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(tplColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(iconFor(tpl.icon), null, tint = tplColor, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                tpl.name,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
