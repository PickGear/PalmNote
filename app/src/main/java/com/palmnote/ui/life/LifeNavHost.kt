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
    onChildNavigated: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
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
            val lifeVm: LifeHomeViewModel = hiltViewModel()
            val calendarVm: LifeCalendarViewModel = hiltViewModel()
            val demoHintVisible by lifeVm.demoHintVisible.collectAsStateWithLifecycle()
            val boardRows by calendarVm.boardRows.collectAsStateWithLifecycle()
            val calendarDayMap by calendarVm.calendarDayMap.collectAsStateWithLifecycle()
            val allTemplatesClosed by calendarVm.allTemplatesClosed.collectAsStateWithLifecycle()
            val visibleTemplates by calendarVm.visibleTemplates.collectAsStateWithLifecycle()
            val scheduledItems by calendarVm.scheduledItems.collectAsStateWithLifecycle()
            val overdueItems by calendarVm.overdueItems.collectAsStateWithLifecycle()
            val todoItems by calendarVm.todoItems.collectAsStateWithLifecycle()
            val categoryCounts by calendarVm.categoryCounts.collectAsStateWithLifecycle()
            val selectedDate by calendarVm.selectedDate.collectAsStateWithLifecycle()
            val calendarWeekMode by calendarVm.weekMode.collectAsStateWithLifecycle()
            LifeScreen(
                onOpenDetail = { itemId -> navController.navigate(LifeDetail(itemId = itemId)) },
                onOpenStats = { navController.navigate(LifeStats) },
                onOpenManageTemplates = { navController.navigate(LifeTemplateManage) },
                onOpenCategory = { category -> navController.navigate(LifeCategoryDetail(category = category)) },
                onCreateRecord = { iconKey -> navController.navigate(LifeCreateRecord(templateIconKey = iconKey)) },
                demoHintVisible = demoHintVisible,
                onDismissDemoHint = { lifeVm.dismissDemoHint() },
                allTemplatesClosed = allTemplatesClosed,
                boardRows = boardRows,
                calendarDayMap = calendarDayMap,
                scheduledItems = scheduledItems,
                overdueItems = overdueItems,
                todoItems = todoItems,
                categoryCounts = categoryCounts,
                templates = visibleTemplates,
                selectedDate = selectedDate,
                onSelectDate = { calendarVm.setSelectedDate(it) },
                calendarWeekMode = calendarWeekMode,
                onCalendarWeekModeChange = { calendarVm.setWeekMode(it) },
                onToggleItemStatus = { calendarVm.toggleItemStatus(it) },
                onReschedule = { calendarVm.rescheduleToToday(it) },
                onDeleteItem = { calendarVm.deleteItem(it) }
            )
        }

        composable<LifeTemplateManage> {
            LifeTemplateManageScreen(
                onBack = { navController.popBackStack() },
                onEditTemplate = { id -> navController.navigate(LifeTemplateEdit(templateId = id)) }
            )
        }

        // 模板 id 走 typed navigation 自动注入：由 LifeTemplateEditViewModel 从 SavedStateHandle 读取，
        // 这里不显式取参（route 对象在此无消费方）。
        composable<LifeTemplateEdit> {
            LifeTemplateEditScreen(onBack = { navController.popBackStack() })
        }

        composable<LifeDetail> {
            LifeDetailScreen(onBack = { navController.popBackStack() })
        }

        composable<LifeCategoryDetail> { entry ->
            val r = entry.toRoute<LifeCategoryDetail>()
            CategoryDetailScreen(
                category = r.category,
                onBack = { navController.popBackStack() },
                onItemClick = { itemId -> navController.navigate(LifeDetail(itemId = itemId)) },
                onCreateClick = { iconKey -> navController.navigate(LifeCreateRecord(templateIconKey = iconKey)) }
            )
        }

        composable<LifeDayRead> { entry ->
            val r = entry.toRoute<LifeDayRead>()
            LifeDayReadScreen(
                dateKey = r.dateKey,
                onBack = { navController.popBackStack() }
            )
        }

        composable<LifeStats> {
            LifeStatsScreen(onBack = { navController.popBackStack() })
        }

        composable<LifeCreateRecord> {
            LifeCreateRecordScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
    }
}
