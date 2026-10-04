package com.palmnote.ui.life

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute

/**
 * 生活页内部嵌套 NavHost（页面内路由：Home / Detail / DayRead / Stats / 模板管理 / 新建记录）。
 *
 * 2026-09-23：日历 / 全部独立视图与跨模块跳转（切底部 Tab、跳外层子页）随首页回退一并退役，
 * 因此不再接收外层的导航控制器，只保留自身导航栈。
 *
 * [onChildNavigated] 用于通知外层 MainTabs：是否处于首页（决定底部导航栏显隐）。
 */
@Composable
fun LifeNavHost(
    onChildNavigated: (Boolean) -> Unit,
    /** 小组件深链：点计数事件 → 直达该记录详情（消费后回调清空）。 */
    pendingDetailItemId: Long? = null,
    onPendingDetailConsumed: () -> Unit = {},
    /** 小组件页脚 → 直达完整清单的指定模式。 */
    pendingListMode: String? = null,
    onPendingListConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    // 首页与首页清单完整页必须共用同一实例，否则完整页操作后首页不会立即刷新。
    val lifeCalendarViewModel: LifeCalendarViewModel = hiltViewModel()
    // ⚠️ 首帧 `backStack` 为 null（目的地尚未解析）：此时**不能判成「不在首页」**。
    // 否则回调 `onChildNavigated(false)` 会让外层抽掉底部导航栏，紧接着下一帧又装回来 ——
    // 表现为「进生活页时底部导航栏与 FAB 闪一下」：栏一消失，外层 Scaffold 的
    // innerPadding.bottom 由「栏高」变 0、内容区变高，FAB 也随之往下跳，随后再弹回。
    // null 只代表「还没解析出目的地」，不构成离页信号 ⟹ 视为仍在首页。
    val homeDestination = backStack?.destination
    val atHome = homeDestination == null || homeDestination.hasRoute<LifeHome>()

    LaunchedEffect(atHome) {
        onChildNavigated(atHome)
    }

    // 小组件深链：待命中的详情 id 一到就进详情页（无论当前停在哪个内层目的地）
    LaunchedEffect(pendingDetailItemId) {
        if (pendingDetailItemId != null) {
            navController.navigate(LifeDetail(itemId = pendingDetailItemId))
            onPendingDetailConsumed()
        }
    }
    LaunchedEffect(pendingListMode) {
        val mode = pendingListMode?.let { LifeFullListMode.fromRouteValue(it) }
        if (mode != null) {
            navController.navigate(LifeFullList(mode = mode.routeValue, dateEpochDay = 0L))
        }
        if (pendingListMode != null) onPendingListConsumed()
    }

    NavHost(
        navController = navController,
        startDestination = LifeHome,
        modifier = Modifier,
        enterTransition = { fadeIn(animationSpec = tween(200)) },
        exitTransition = { fadeOut(animationSpec = tween(200)) },
        popEnterTransition = { fadeIn(animationSpec = tween(200)) },
        popExitTransition = { fadeOut(animationSpec = tween(200)) }
    ) {
        composable<LifeHome> {
            val calendarVm = lifeCalendarViewModel
            val boardRows by calendarVm.boardRows.collectAsStateWithLifecycle()
            val calendarDayMap by calendarVm.calendarDayMap.collectAsStateWithLifecycle()
            val allTemplatesClosed by calendarVm.allTemplatesClosed.collectAsStateWithLifecycle()
            val visibleTemplates by calendarVm.visibleTemplates.collectAsStateWithLifecycle()
            val scheduledItems by calendarVm.scheduledItems.collectAsStateWithLifecycle()
            val overdueItems by calendarVm.overdueItems.collectAsStateWithLifecycle()
            val unscheduledItems by calendarVm.unscheduledItems.collectAsStateWithLifecycle()
            val categoryCounts by calendarVm.categoryCounts.collectAsStateWithLifecycle()
            val selectedDate by calendarVm.selectedDate.collectAsStateWithLifecycle()
            val calendarWeekMode by calendarVm.weekMode.collectAsStateWithLifecycle()
            val lastTemplateId by calendarVm.lastTemplateId.collectAsStateWithLifecycle()
            val onThisDayItems by calendarVm.onThisDayItems.collectAsStateWithLifecycle()
            val quickAddOutcome by calendarVm.quickAddOutcome.collectAsStateWithLifecycle()
            val categoryCompact by calendarVm.categoryCompact.collectAsStateWithLifecycle()
            val swipeTipVisible by calendarVm.swipeTipVisible.collectAsStateWithLifecycle()
            LifeScreen(
                onOpenDetail = { itemId -> navController.navigate(LifeDetail(itemId = itemId)) },
                onOpenStats = { navController.navigate(LifeStats) },
                onOpenManageTemplates = { navController.navigate(LifeTemplateManage) },
                onOpenCategory = { category -> navController.navigate(LifeCategoryDetail(category = category)) },
                onOpenFullList = { mode, dateEpochDay ->
                    navController.navigate(LifeFullList(mode = mode.routeValue, dateEpochDay = dateEpochDay))
                },
                onCreateRecord = { templateId -> navController.navigate(LifeCreateRecord(templateId = templateId)) },
                lastTemplateId = lastTemplateId,
                onRememberTemplate = calendarVm::rememberLastTemplateId,
                onThisDayItems = onThisDayItems,
                onQuickAdd = { calendarVm.quickAdd(it) },
                quickAddOutcome = quickAddOutcome,
                onQuickAddOutcomeShown = calendarVm::consumeQuickAddOutcome,
                onDisableDemoMode = calendarVm::disableDemoMode,
                allTemplatesClosed = allTemplatesClosed,
                boardRows = boardRows,
                calendarDayMap = calendarDayMap,
                scheduledItems = scheduledItems,
                overdueItems = overdueItems,
                unscheduledItems = unscheduledItems,
                categoryCounts = categoryCounts,
                templates = visibleTemplates,
                selectedDate = selectedDate,
                onSelectDate = { calendarVm.setSelectedDate(it) },
                onOpenDayRead = { date -> navController.navigate(LifeDayRead(dateKey = date.toString())) },
                calendarWeekMode = calendarWeekMode,
                onCalendarWeekModeChange = { calendarVm.setWeekMode(it) },
                // completed = null ⇒ 点击勾圈，翻转；非 null ⇒ 滑动确认，按对话框承诺的方向显式设置
                onSetItemStatus = { id, completed ->
                    if (completed == null) calendarVm.toggleItemStatus(id)
                    else calendarVm.setItemCompleted(id, completed)
                },
                onReschedule = { calendarVm.rescheduleToToday(it) },
                onRescheduleAllOverdue = { ids -> calendarVm.rescheduleAllToToday(ids) },
                onScheduleItem = { id, date -> calendarVm.scheduleItem(id, date) },
                onDeleteItem = { calendarVm.deleteItem(it) },
                categoryCompact = categoryCompact,
                swipeTipVisible = swipeTipVisible,
                onDismissSwipeTip = calendarVm::dismissSwipeTip
            )
        }

        composable<LifeTemplateManage> {
            LifeTemplateManageScreen(
                onBack = { navController.popBackStack() },
                onEditTemplate = { id -> navController.navigate(LifeTemplateEdit(templateId = id)) },
                onCreateTemplate = { navController.navigate(LifeTemplateEdit(templateId = 0L)) }
            )
        }

        // 模板 id 走 typed navigation 自动注入：由 LifeTemplateEditViewModel 从 SavedStateHandle 读取，
        // 这里不显式取参（route 对象在此无消费方）。
        composable<LifeTemplateEdit> {
            LifeTemplateEditScreen(
                onBack = { navController.popBackStack() },
                onOpenFieldLibrary = { navController.navigate(LifeFieldLibrary) }
            )
        }

        // 字段库全屏页（§4.7(2)）：与编辑页**共用同一个 ViewModel 实例**，
        // 选中写 pendingAddType、返回后由编辑页消费 —— 不靠导航参数回传，避免类型序列化往返。
        //
        // ⚠️ 下面 `previousBackStackEntry!!` 依赖一个前提：**字段库只能从模板编辑页进入**
        // （因此必有上一个 back stack entry）。若将来支持"深链直达字段库"，
        // 这里会 NPE 崩溃 —— 那时请改成从 NavBackStackEntry 作用域取 VM 的写法。
        composable<LifeFieldLibrary> {
            val editVm: LifeTemplateEditViewModel = hiltViewModel(navController.previousBackStackEntry!!)
            LifeFieldLibraryScreen(
                onBack = { navController.popBackStack() },
                onPick = { type ->
                    editVm.stageAddField(type)
                    navController.popBackStack()
                }
            )
        }

        composable<LifeDetail> {
            LifeDetailScreen(
                onBack = { navController.popBackStack() },
                onEditTemplate = { id -> navController.navigate(LifeTemplateEdit(templateId = id)) },
                onEditRecord = { id -> navController.navigate(LifeCreateRecord(itemId = id)) }
            )
        }

        composable<LifeCategoryDetail> { entry ->
            val r = entry.toRoute<LifeCategoryDetail>()
            CategoryDetailScreen(
                category = r.category,
                onBack = { navController.popBackStack() },
                onItemClick = { itemId -> navController.navigate(LifeDetail(itemId = itemId)) },
                onCreateClick = { templateId -> navController.navigate(LifeCreateRecord(templateId = templateId)) },
                onEditTemplate = { id -> navController.navigate(LifeTemplateEdit(templateId = id)) },
                onOpenManageTemplates = { navController.navigate(LifeTemplateManage) }
            )
        }

        composable<LifeFullList> { entry ->
            val r = entry.toRoute<LifeFullList>()
            val mode = LifeFullListMode.fromRouteValue(r.mode)
            if (mode == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                LifeFullListScreen(
                    mode = mode,
                    dateEpochDay = r.dateEpochDay,
                    calendarVm = lifeCalendarViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenDetail = { itemId -> navController.navigate(LifeDetail(itemId = itemId)) }
                )
            }
        }

        composable<LifeDayRead> { entry ->
            val r = entry.toRoute<LifeDayRead>()
            LifeDayReadScreen(
                dateKey = r.dateKey,
                onBack = { navController.popBackStack() }
            )
        }

        composable<LifeStats> {
            LifeStatsScreen(
                onBack = { navController.popBackStack() },
                onOpenMonthlyReview = { navController.navigate(LifeMonthlyReview) }
            )
        }

        composable<LifeMonthlyReview> {
            LifeMonthlyReviewScreen(onBack = { navController.popBackStack() })
        }

        composable<LifeCreateRecord> {
            LifeCreateRecordScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
    }
}
