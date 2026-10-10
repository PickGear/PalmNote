package com.palmnote.ui.bills
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.domain.model.BillType

import androidx.compose.animation.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.palmnote.ui.theme.ModuleBill
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.getDisplayName
import com.palmnote.data.db.entity.getDisplayDescription
import com.palmnote.domain.model.toMoney
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.components.*
import com.palmnote.ui.life.PillToggle
import com.palmnote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillScreen(
    onNavigateToAdd: (Long) -> Unit = {},
    onNavigateToDetail: (Long) -> Unit = {},
    onNavigateToBudget: () -> Unit = {},
    onNavigateToReport: (Long, String) -> Unit = { _, _ -> },
    onNavigateToImportCsv: () -> Unit = {},
    onNavigateToAccountBook: () -> Unit = {},
    onNavigateToReimbursement: () -> Unit = {},
    viewModel: BillViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val customExpenseCategories by viewModel.customExpenseCategories.collectAsStateWithLifecycle()
    val customIncomeCategories by viewModel.customIncomeCategories.collectAsStateWithLifecycle()
    val allCustomExpenseCategories by viewModel.allCustomExpenseCategories.collectAsStateWithLifecycle()
    val allCustomIncomeCategories by viewModel.allCustomIncomeCategories.collectAsStateWithLifecycle()
    val billPresetOverrides by viewModel.presetCategoryOverrides.collectAsStateWithLifecycle()
    // 待报销支出（含只报了一部分的）。空列表即隐藏列表顶部那张提示卡。
    val pendingReimbursements by viewModel.pendingReimbursements.collectAsStateWithLifecycle()
    val pendingReimburseRemaining = remember(pendingReimbursements) {
        pendingReimbursements.sumOf { it.amount - it.reimbursedAmount }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.clearFilter() }
    }
    
    var calendarExpanded by remember { mutableStateOf(false) }
    // 「回今天」令牌：点卡片左上的「日历」时写一个新时间戳。不能只靠 setMonth 触发 —— 已经在
    // 当月浏览别的周时，setMonth 写进去的是同一个值，StateFlow 不会重发，周视图也就等不到这一跳。
    var calendarTodayJump by remember { mutableLongStateOf(0L) }
    val selectedFilter = state.currentFilter.type?.value ?: "ALL"
    // 无 key 的 remember(derivedStateOf)：状态字段变化时只重算本块，避免整个过滤器随任意 state 发射重建
    val filteredBills by remember {
        derivedStateOf {
            val base = if (state.currentFilter.isActive || state.searchQuery.isNotBlank()) state.filteredBills else state.bills
            val byType = when (state.currentFilter.type?.value ?: "ALL") {
                "EXPENSE" -> base.filter { it.type == BillType.EXPENSE }
                "INCOME" -> base.filter { it.type == BillType.INCOME }
                "TRANSFER" -> base.filter { it.type == BillType.TRANSFER }
                else -> base
            }
            val sd = state.selectedDay
            if (sd != null) byType.filter { DateUtils.getDayOfMonth(it.date) == sd }
            else byType
        }
    }

    val groupedBills = remember(filteredBills) { filteredBills.groupBy { DateUtils.formatDate(it.date) }.mapValues { it.value.sortedByDescending { b -> b.createdAt } } }

    var showBookMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    
    BackHandler(enabled = showSearch) { showSearch = false; viewModel.clearSearch() }
    val currentBook = state.accountBooks.find { it.id == state.selectedBookId }
        ?: state.allAccountBooks.find { it.id == state.selectedBookId }
    
    var billToDelete by remember { mutableStateOf<Bill?>(null) }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .exclude(WindowInsets.navigationBars),
        topBar = {
            CompactTopAppBar(
                title = {
                    if (showSearch) {
                        ModuleSearchBar(
                            query = state.searchQuery,
                            onQueryChange = { viewModel.onSearchQueryChanged(it) },
                            onClear = { viewModel.clearSearch() },
                            placeholder = stringResource(R.string.search),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { showBookMenu = true }
                                    .padding(end = 4.dp)
                            ) {
                                Text(
                                    text = currentBook?.getDisplayName(context) ?: stringResource(R.string.bill_title),
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ModuleBill
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    if (showBookMenu) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                    contentDescription = stringResource(R.string.bill_switch_book),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (showBookMenu) {
                                Popup(
                                    alignment = Alignment.TopStart,
                                    offset = with(LocalDensity.current) { IntOffset(0, 56.dp.roundToPx()) },
                                    onDismissRequest = { showBookMenu = false },
                                    properties = PopupProperties(focusable = true)
                                ) {
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.background,
                                        shadowElevation = 3.dp
                                    ) {
                                        // 账本数无上界（用户可自建）：菜单封顶可滚，否则账本一多下半截就点不到
                                        Column(
                                            modifier = Modifier
                                                .width(260.dp)
                                                .heightIn(max = 320.dp)
                                                .verticalScroll(rememberScrollState())
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Text(stringResource(R.string.bill_book_list), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp))
                                            state.accountBooks.forEach { book ->
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            viewModel.selectAccountBook(book.id)
                                                            showBookMenu = false
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                                ) {
                                                    AccountBookBadge(
                                                        icon = book.icon,
                                                        colorHex = book.color,
                                                        size = 32.dp,
                                                        iconSize = 16.dp
                                                    )
                                                    Spacer(Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(book.getDisplayName(context), style = MaterialTheme.typography.bodyMedium)
                                                            if (book.isDefault) {
                                                                Spacer(Modifier.width(6.dp))
                                                                Surface(
                                                                    shape = MaterialTheme.shapes.extraSmall,
                                                                    color = AccentOrange.copy(alpha = 0.1f)
                                                                ) {
                                                                    Text(stringResource(R.string.bill_default), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                                        style = MaterialTheme.typography.labelSmall, color = AccentOrange)
                                                                }
                                                            }
                                                        }
                                                        if (book.getDisplayDescription(context).isNotEmpty()) {
                                                            Text(book.getDisplayDescription(context), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                                        }
                                                    }
                                                    if (book.id == state.selectedBookId) {
                                                        Icon(
                                                            Icons.Filled.Check,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        showBookMenu = false
                                                        onNavigateToAccountBook()
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                            ) {
                                                Icon(Icons.Filled.Add, null, modifier = Modifier.padding(end = 8.dp))
                                                Text(stringResource(R.string.settings_bill_manage))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (showSearch) {
                        TextButton(onClick = { showSearch = false; viewModel.clearSearch() }, modifier = Modifier.padding(end = 4.dp)) {
                            Text(stringResource(R.string.cancel), style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.search),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onNavigateToImportCsv() }) {
                            Icon(
                                Icons.Outlined.FileDownload,
                                contentDescription = stringResource(R.string.bill_import),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val allBooksLabel = stringResource(R.string.bill_all_books)
                        IconButton(onClick = { onNavigateToReport(state.selectedBookId, currentBook?.getDisplayName(context) ?: allBooksLabel) }) {
                            Icon(
                                Icons.Outlined.Assessment,
                                contentDescription = stringResource(R.string.bill_report),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // 顶栏放高频动作：搜索 / 导入 / 统计（报表）。
                        // 预算与报销属低频配置，收进溢出菜单，菜单项带文字标签更好认。
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(
                                    Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(R.string.more),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.bill_budget_tab)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.AccountBalance,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        onNavigateToBudget()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reimbursement_title)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.ReceiptLong,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        onNavigateToReimbursement()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val sd = state.selectedDay
                    val date = if (sd != null) {
                        DateUtils.preserveTimeOfDay(System.currentTimeMillis(), DateUtils.toMillis(state.currentYearMonth, sd))
                    } else {
                        System.currentTimeMillis()
                    }
                    // 记一笔继承当前筛选的账本（全部账本视图时交由默认逻辑决定）
                    com.palmnote.PalmNoteApp.pendingAddBillBookId =
                        state.selectedBookId.takeIf { it != com.palmnote.data.db.entity.AccountBook.ALL_BOOKS_ID }
                    onNavigateToAdd(date)
                },
                containerColor = ModuleBill,
                contentColor = Color.White,
                shape = MaterialTheme.shapes.large,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.bill_add), fontWeight = FontWeight.Medium) }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Calendar (fixed)
                AnimatedVisibility(
                    visible = !showSearch,
                    enter = fadeIn(tween(120)) + expandVertically(tween(120)),
                    exit = fadeOut(tween(300)) + shrinkVertically(tween(300))
                ) {
                    AnimatedCard(index = 1) {
                    ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        // 头部一行三元素：左「日历」（点击 = 回今天）、中月份（相对整卡居中）、右「周 | 月」。
                        // 月份用 Box 叠放而非 Row + weight：左右元素宽度天然不等（中文「日历」窄、
                        // 英文 "Calendar" 宽），weight 版会把月份挤得偏离中心；叠放才是真正的整行居中。
                        // 切月仍只靠左右滑动，滑动落定后才回写 currentYearMonth，故这里取外部当前月。
                        val isOnToday = state.selectedDay == DateUtils.getDayOfMonth(System.currentTimeMillis()) &&
                            state.currentYearMonth == DateUtils.getCurrentYearMonth()
                        val todayLabel = stringResource(R.string.common_today)
                        Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                            Text(
                                text = DateUtils.formatDisplayMonth(LocalContext.current, state.currentYearMonth),
                                fontSize = TypeScale.metricValue,
                                fontWeight = FontWeight.Medium,
                                // 与生活页月历标题（LifeMonthCalendar 的 displayMonth）同款：onSurface
                                // 才是正文色。原先误用 onSurfaceVariant（#7A7570 次要灰）→ 15sp Medium
                                // 的小字在白卡上对比度仅 4.6:1，看着「发虚」（用户 2026-10-10 截图指出）。
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.Center)
                            )
                            // 左：点「日历」= 回到今天（切回当月并选中今日），取代原「今天」按钮；
                            // 已在今天用常规色、离开后转主色，兼作「可点回今天」的提示。
                            Row(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .clip(CircleShape)
                                    .clickable(onClickLabel = todayLabel) {
                                        viewModel.setMonth(DateUtils.getCurrentYearMonth())
                                        // 周视图下 month 可能本来就是当月，只靠上面这行不会引起任何
                                        // 状态变化；补一个令牌，保证「回今天」在周视图里也真的跳回本周。
                                        calendarTodayJump = System.currentTimeMillis()
                                    }
                                    // 上下 8dp 把点击区撑到整行 40dp（titleMedium 行高约 24dp）
                                    .padding(horizontal = 6.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.bill_calendar),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOnToday) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.primary
                                )
                            }
                            // 右：周 / 月 —— 沿用生活页月历同款 PillToggle（胶囊轨道 + 选中侧圆片），
                            // 与原来的「展开 / 收起」是同一个状态，只是换成了全 App 统一的分段控件。
                            // 选中片传品牌橙：账单页从顶栏到「记一笔」都是橙色系，默认主色（蓝）
                            // 的胶囊夹在日历卡里突兀（用户 2026-10-11 截图反馈改橙）。
                            PillToggle(
                                options = listOf(
                                    stringResource(R.string.life_calendar_view_week),
                                    stringResource(R.string.life_calendar_view_month)
                                ),
                                selectedIndex = if (calendarExpanded) 1 else 0,
                                onSelect = { calendarExpanded = it == 1 },
                                selectedColor = AccentOrange,
                                contentDescriptions = listOf(
                                    stringResource(R.string.life_calendar_toggle_week),
                                    stringResource(R.string.life_calendar_toggle_month)
                                ),
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        CalendarView(
                            yearMonth = state.currentYearMonth,
                            dailyData = state.dailySummary,
                            selectedDay = state.selectedDay,
                            onDaySelected = { day ->
                                viewModel.setSelectedDay(if (state.selectedDay == day) null else day)
                            },
                            collapsed = !calendarExpanded,
                            onMonthChanged = { newMonth -> viewModel.setMonth(newMonth) },
                            todayJumpSignal = calendarTodayJump
                        )
                    }
                    }
                }

                // Filter chips (fixed)
                AnimatedCard(index = 2) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    // 英文下四个 chip（All / Expense / Income / Transfer）比中文宽得多，
                    // 挤在一行里会把末尾的 "Transfer" 拦腰折断成 "Tran/sfer"（真机截图 15-09-19）。
                    // 改为：chip 行占满剩余宽度并**可横向滚动**——装得下就一行排开，
                    // 装不下就滑，文案永远不折行。
                    Row(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val interactionSource = remember { MutableInteractionSource() }
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = if (selectedFilter == "ALL") ModuleBill.copy(alpha = 0.85f) else Color.Transparent,
                            modifier = Modifier.clickable(interactionSource = interactionSource, indication = null) { viewModel.setFilterType("ALL") }
                        ) {
                            Text(
                                text = stringResource(R.string.bill_all),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = if (selectedFilter == "ALL") Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selectedFilter == "ALL") FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        }
                        val interactionSource2 = remember { MutableInteractionSource() }
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = if (selectedFilter == "EXPENSE") ExpenseRed.copy(alpha = 0.85f) else Color.Transparent,
                            modifier = Modifier.clickable(interactionSource = interactionSource2, indication = null) { viewModel.setFilterType("EXPENSE") }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.TrendingDown,
                                    null,
                                    Modifier.size(16.dp),
                                    tint = if (selectedFilter == "EXPENSE") Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.bill_expense),
                                    color = if (selectedFilter == "EXPENSE") Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (selectedFilter == "EXPENSE") FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                            }
                        }
                        val interactionSource3 = remember { MutableInteractionSource() }
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = if (selectedFilter == "INCOME") StatusActive.copy(alpha = 0.85f) else Color.Transparent,
                            modifier = Modifier.clickable(interactionSource = interactionSource3, indication = null) { viewModel.setFilterType("INCOME") }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.TrendingUp,
                                    null,
                                    Modifier.size(16.dp),
                                    tint = if (selectedFilter == "INCOME") Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.bill_income),
                                    color = if (selectedFilter == "INCOME") Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (selectedFilter == "INCOME") FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                            }
                        }
                        val interactionSource4 = remember { MutableInteractionSource() }
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = if (selectedFilter == "TRANSFER") InfoBlue.copy(alpha = 0.85f) else Color.Transparent,
                            modifier = Modifier.clickable(
                                interactionSource = interactionSource4,
                                indication = null
                            ) { viewModel.setFilterType("TRANSFER") }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Icon(
                                    Icons.Outlined.SwapVert,
                                    null,
                                    Modifier.size(16.dp),
                                    tint = if (selectedFilter == "TRANSFER") Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.bill_transfer),
                                    color = if (selectedFilter == "TRANSFER") Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (selectedFilter == "TRANSFER") FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    val hasAdvancedFilter = state.currentFilter.category != null ||
                        state.currentFilter.amountMin != null || state.currentFilter.amountMax != null
                    IconButton(onClick = { viewModel.toggleFilterSheet() }) {
                        Icon(
                            imageVector = if (hasAdvancedFilter) Icons.Filled.FilterList else Icons.Outlined.FilterList,
                            contentDescription = stringResource(R.string.bill_filter),
                            tint = if (hasAdvancedFilter) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                }

                // Bill list (scrollable)
                val billListState = rememberLazyListState()
                // 滚动状态用 derivedStateOf 收敛，列表项读取它才不会在每次滚动帧重组整张列表
                val isListScrolling by remember { derivedStateOf { billListState.isScrollInProgress } }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = billListState,
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    // 待报销提示卡（列表首项，随列表滚走，不占固定高度）。
                    // 报销管理在顶栏只是溢出菜单里的二级入口，靠这张卡补上「进账单页就能看见」的曝光。
                    if (pendingReimbursements.isNotEmpty()) {
                        item {
                            AnimatedCard(index = 3, instant = isListScrolling) {
                                ReimbursementPendingCard(
                                    count = pendingReimbursements.size,
                                    remaining = pendingReimburseRemaining,
                                    // 列表 contentPadding 已给 16dp 页边距；不再加内边距，
                                    // 让卡片与上方日历卡左右对齐（列表行才缩进 12dp）。
                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                                    onClick = onNavigateToReimbursement
                                )
                            }
                        }
                    }
                    item {
                        AnimatedCard(index = 3, instant = billListState.isScrollInProgress) {
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp, start = 12.dp, end = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.bill_detail),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = pluralStringResource(
                                    R.plurals.bill_count, filteredBills.size, filteredBills.size
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(100.dp)
                            )
                        }
                    }
                }

                    if (filteredBills.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Receipt,
                        title = stringResource(R.string.bill_no_records),
                        subtitle = stringResource(R.string.bill_start_recording),
                        tint = AccentOrange
                    )
                }
            } else {
                groupedBills.forEach { (_, bills) ->
                    item {
                        val dayIncome = bills.filter { it.type == BillType.INCOME }.sumOf { it.amount }
                        val dayExpense = bills.filter { it.type == BillType.EXPENSE }.sumOf { it.amount }
                        AnimatedCard(instant = billListState.isScrollInProgress) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 4.dp, start = 12.dp, end = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = DateUtils.formatDisplayDateWithWeekday(context, bills.first().date),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    if (dayIncome > 0) {
                                        Text(
                                            text = stringResource(
                                                R.string.bill_income_short,
                                                CurrencyUtils.formatCurrency(context, dayIncome.toMoney())
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (dayExpense > 0) {
                                        Text(
                                            text = stringResource(
                                                R.string.bill_expense_short,
                                                CurrencyUtils.formatCurrency(context, dayExpense.toMoney())
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    itemsIndexed(bills, key = { _, bill -> bill.id }) { index, bill ->
                        AnimatedCard(index = (index + 4).coerceAtMost(10), instant = isListScrolling) {
                        BillListItem(
                            bill = bill,
                            wallets = state.wallets,
                            onDetail = { onNavigateToDetail(bill.id) },
                            onDelete = { billToDelete = bill },
                            customExpenseItems = allCustomExpenseCategories,
                            customIncomeItems = allCustomIncomeCategories,
                            presetOverrides = billPresetOverrides
                        )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
        }
    }
    
    // 删除确认弹窗
    billToDelete?.let { bill ->
        AppDialog(
            onDismissRequest = { billToDelete = null },
            title = { Text(stringResource(R.string.delete), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBill(bill.id)
                    billToDelete = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { billToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // 高级筛选Sheet
    if (state.showFilterSheet) {
        val resolvedExpense = expenseCategoryItems.map { item ->
            val resolved = ColorResolver.resolve(item.name, item.color)
            if (resolved != item.color) item.copy(color = resolved) else item
        }
        val resolvedIncome = incomeCategoryItems.map { item ->
            val resolved = ColorResolver.resolve(item.name, item.color)
            if (resolved != item.color) item.copy(color = resolved) else item
        }
        BillFilterSheet(
            onDismiss = { viewModel.toggleFilterSheet() },
            onApply = { filter -> viewModel.applyFilter(filter) },
            currentFilter = state.currentFilter,
            expenseCategories = resolvedExpense + customExpenseCategories,
            incomeCategories = resolvedIncome + customIncomeCategories,
            presetOverrides = billPresetOverrides
        )
    }
}

/**
 * 账单列表首项的「待报销」提示卡，只在存在未报完的支出时渲染（调用方判空）。
 * 数字口径与报销页一致：笔数 = 待报销条数，金额 = 各笔「原额 − 已报额」之和。
 */
@Composable
private fun ReimbursementPendingCard(
    count: Int,
    remaining: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    ModuleCard(
        tint = billTint(),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AccentOrange.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.ReceiptLong,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.reimbursement_tab_pending),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(
                        R.string.reimbursement_pending_card_subtitle,
                        count,
                        CurrencyUtils.formatCurrency(context, remaining.toMoney())
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BillListItem(bill: Bill, wallets: Map<Long, String> = emptyMap(), onDetail: () -> Unit, onDelete: () -> Unit,
    customExpenseItems: List<CategoryItem>? = null, customIncomeItems: List<CategoryItem>? = null,
    presetOverrides: Map<String, String> = emptyMap()) {
    val context = LocalContext.current
    val isExpense = bill.type == BillType.EXPENSE
    val categoryItem = remember(bill.category, bill.type, customExpenseItems, customIncomeItems, presetOverrides) {
        val presetList = if (isExpense) expenseCategoryItems else incomeCategoryItems
        presetList.find { it.name == bill.category }?.let {
            it.copy(color = ColorResolver.resolve(it.name, it.color))
        } ?: (if (isExpense) customExpenseItems else customIncomeItems)?.find { it.name == bill.category }
    }
    // 孤儿分类兜底（预设/自定义都匹配不上 = 分类已被删除，多见于回收站恢复后）
    // 红 ✕ 是有意的产品决策：当「分类已失效」的警示信号，提醒用户及时重挂分类
    val catColor = categoryItem?.color ?: ErrorLight
    val displayName = remember(bill.category, bill.type, presetOverrides) {
        resolvePresetCategoryName(presetOverrides, bill.category, bill.type.value, context)
    }

    val density = LocalDensity.current
    var offsetX by remember { mutableFloatStateOf(0f) }
    val deleteWidthPx = with(density) { 80.dp.toPx() }
    val thresholdPx = with(density) { 60.dp.toPx() }

    Box(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier.fillMaxHeight().width(80.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.errorContainer).clickable { onDelete() }.align(Alignment.CenterEnd),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.delete), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().offset { IntOffset(offsetX.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(bill.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = { offsetX = if (offsetX < -thresholdPx) -deleteWidthPx else 0f },
                        onDragCancel = { offsetX = 0f },
                        onHorizontalDrag = { _, dragAmount -> offsetX = (offsetX + dragAmount).coerceIn(-deleteWidthPx, 0f) }
                    )
                }
                .clickable { if (offsetX == 0f) onDetail() else offsetX = 0f }
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).padding(start = 12.dp, end = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Row(modifier = Modifier.weight(1f, false), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(if (bill.type == BillType.TRANSFER) InfoBlue.copy(alpha = 0.12f) else catColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(
                            if (bill.type == BillType.TRANSFER) Icons.Outlined.SwapVert else categoryItem?.icon ?: Icons.Outlined.Cancel,
                            null, tint = if (bill.type == BillType.TRANSFER) InfoBlue else catColor, modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            if (bill.type == BillType.TRANSFER)
                                stringResource(R.string.bill_transfer)
                            else displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        val merchantText = if (bill.type == BillType.TRANSFER) null else if (bill.merchant.isNotEmpty() && bill.location.isNotEmpty()) {
                            "${bill.merchant} · ${bill.location}"
                        } else if (bill.merchant.isNotEmpty()) {
                            bill.merchant
                        } else if (bill.location.isNotEmpty()) {
                            bill.location
                        } else null
                        if (merchantText != null) {
                            Text(merchantText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                        }
                        if (bill.type == BillType.TRANSFER && bill.walletId != null && bill.toWalletId != null) {
                            val fromName = wallets[bill.walletId] ?: ""
                            val toName = wallets[bill.toWalletId] ?: ""
                            Text(
                                "$fromName \u2192 $toName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
                // 尾部列：金额 + 钱包名。银行卡的显示名是「招商银行 ****6214」，比普通钱包名长得多。
                // 原先这里写死 `width(100.dp)`，那个长度正好比它短一点点，于是字符串在空格处折成两行，
                // 白占一行高度（用户 2026-10-10 反馈：右边明明还有空位却折了行）。
                // 现改为「下限 100dp、上限 180dp」：普通行仍是 100dp、外观与原先完全一致；
                // 名字偏长的行按内容撑开，一行放得下就不再折行。
                // 注意两个子项都不再 `fillMaxWidth()` —— 列宽要按内容撑开，右对齐交给
                // Column 的 horizontalAlignment = End（带 fillMaxWidth 的子项会把列直接撑到行满宽）。
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 100.dp, max = 180.dp)) {
                    val prefix = if (bill.type == BillType.EXPENSE) "-"
                    else if (bill.type == BillType.TRANSFER) "" else "+"
                    val amountText = "$prefix${CurrencyUtils.formatCompact(context, bill.amount.toMoney())}"
                    val amountColor = if (bill.type == BillType.EXPENSE) ExpenseRed
                    else if (bill.type == BillType.TRANSFER) InfoBlue else StatusActive
                    Text(
                        text = amountText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = amountColor,
                        textAlign = TextAlign.End
                    )
                    bill.walletId?.let { walletId ->
                        wallets[walletId]?.let { walletName ->
                            // 单行显示：撑开列宽就是为了这一行不折行；真的长到 180dp 还放不下
                            // （银行名起得很长的用户）才退化为省略号，此时至少不会挤掉金额。
                            Text(
                                walletName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}
