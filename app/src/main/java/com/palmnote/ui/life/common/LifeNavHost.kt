package com.palmnote.ui.life.common
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.repository.LifeTemplateRepository
import com.palmnote.domain.util.LifeTemplateRouteType
import com.palmnote.domain.util.getRouteType
import com.palmnote.ui.life.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import com.palmnote.ui.life.record.focus.FocusTimerScreen
import com.palmnote.ui.life.record.habit.HabitListScreen
import com.palmnote.ui.life.record.journal.JournalListScreen
import com.palmnote.ui.life.record.mood.MoodListScreen
import com.palmnote.ui.life.record.habit.AchievementScreen
import com.palmnote.ui.life.record.report.ReportListScreen
import com.palmnote.ui.life.time.anniversary.AnniversaryListScreen
import com.palmnote.ui.life.time.birthday.BirthdayListScreen
import com.palmnote.ui.life.time.countdown.CountdownListScreen
import com.palmnote.ui.life.time.countup.CountUpListScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*


data class TplDispState(val template: LifeTemplate? = null, val isLoading: Boolean = true)

@HiltViewModel
class TplDispViewModel @Inject constructor(
    private val templateRepo: LifeTemplateRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(TplDispState())
    val uiState: StateFlow<TplDispState> = _uiState.asStateFlow()
    fun load(templateId: Long) {
        templateRepo.getTemplateByIdFlow(templateId).onEach { tpl ->
            _uiState.update { it.copy(template = tpl, isLoading = false) }
        }.launchIn(viewModelScope)
    }
}

data class TodoTplState(val template: LifeTemplate? = null, val isLoading: Boolean = true)

@HiltViewModel
class TodoTemplateViewModel @Inject constructor(
    private val templateRepo: LifeTemplateRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(TodoTplState())
    val uiState: StateFlow<TodoTplState> = _uiState.asStateFlow()
    fun load() {
        templateRepo.getAllTemplates()
            .map { templates -> templates.firstOrNull { it.icon == "checklist" } }
            .onEach { tpl ->
                _uiState.update { it.copy(template = tpl, isLoading = false) }
            }.launchIn(viewModelScope)
    }
}

private val animSpec = tween<androidx.compose.ui.unit.IntOffset>(300)
private val slideIn = slideInHorizontally(animationSpec = animSpec) { it }
private val slideOut = slideOutHorizontally(animationSpec = animSpec) { -it / 3 }
private val popIn = slideInHorizontally(animationSpec = animSpec) { -it / 3 }
private val popOut = slideOutHorizontally(animationSpec = animSpec) { it }

@Composable
private fun DispatchScreen(tpl: LifeTemplate, tid: Long, navController: NavHostController) {
    val back: () -> Unit = { navController.popBackStack(); Unit }
    val onClick: (Long) -> Unit = { id -> navController.navigate(LifeItemRoute(id)); Unit }
    val onCreate: () -> Unit = { navController.navigate(LifeCreateRoute(tid)); Unit }
    
    when (tpl.getRouteType()) {
        LifeTemplateRouteType.HABIT -> HabitListScreen(onBack = back, onItemClick = onClick)
        LifeTemplateRouteType.MOOD -> MoodListScreen(onBack = back)
        LifeTemplateRouteType.JOURNAL -> JournalListScreen(onBack = back, onItemClick = onClick)
        LifeTemplateRouteType.FOCUS -> FocusTimerScreen(onBack = back)
        LifeTemplateRouteType.COUNTUP -> CountUpListScreen(
            templateId = tid, onBack = back, onItemClick = onClick, onCreateClick = onCreate
        )
        LifeTemplateRouteType.COUNTDOWN -> CountdownListScreen(
            templateId = tid, onBack = back, onItemClick = onClick, onCreateClick = onCreate
        )
        LifeTemplateRouteType.BIRTHDAY -> BirthdayListScreen(
            templateId = tid, onBack = back, onItemClick = onClick, onCreateClick = onCreate
        )
        LifeTemplateRouteType.ANNIVERSARY -> AnniversaryListScreen(
            templateId = tid, onBack = back, onItemClick = onClick, onCreateClick = onCreate
        )
        LifeTemplateRouteType.TODO -> GenericTemplateListScreen(
            template = tpl, templateId = tid, onBack = back, onItemClick = onClick, onCreateClick = onCreate
        )
        LifeTemplateRouteType.GENERIC -> GenericTemplateListScreen(
            template = tpl, templateId = tid, onBack = back, onItemClick = onClick, onCreateClick = onCreate
        )
    }
}

@Composable
private fun dispatchTemplateScreen(tpl: LifeTemplate, tid: Long, navController: NavHostController) {
    DispatchScreen(tpl = tpl, tid = tid, navController = navController)
}

@Composable
@Suppress("CyclomaticComplexMethod")
fun LifeNavHost(modifier: Modifier = Modifier, onChildNavigated: (Boolean) -> Unit = {}, navController: NavHostController = rememberNavController()) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(navBackStackEntry?.destination?.route) {
        onChildNavigated(navBackStackEntry?.destination?.hasRoute<LifeHomeRoute>() == true)
    }
    NavHost(navController = navController, startDestination = LifeHomeRoute, modifier = modifier,
        enterTransition = { slideIn + fadeIn(tween(150)) },
        exitTransition = { slideOut + fadeOut(tween(150)) },
        popEnterTransition = { popIn + fadeIn(tween(150)) },
        popExitTransition = { popOut + fadeOut(tween(150)) }
    ) {
        composable<LifeHomeRoute> {
            LifeScreen(
                onNavigateToItem = { itemId -> navController.navigate(LifeItemRoute(itemId)) },
                onNavigateToCreate = { tplId -> navController.navigate(LifeCreateRoute(tplId)) },
                onNavigateToFocus = { navController.navigate(LifeFocusRoute) },
                onNavigateToHabit = { navController.navigate(LifeHabitRoute) },
                onNavigateToMood = { navController.navigate(LifeMoodRoute) },
                onNavigateToJournal = { navController.navigate(LifeJournalRoute) },
                onNavigateToReport = { navController.navigate(LifeReportRoute) },
                onNavigateToManage = { navController.navigate(LifeTemplateManageRoute) },
                onNavigateToTodo = { navController.navigate(LifeTodoRoute) },
                onNavigateToStats = { navController.navigate(LifeStatsRoute) },
                onNavigateToCategory = { category -> navController.navigate(LifeCategoryDetailRoute(category)) },
            )
        }
        composable<LifeTemplateRoute> { entry ->
            val tid = entry.toRoute<LifeTemplateRoute>().templateId
            val vm: TplDispViewModel = hiltViewModel()
            val s by vm.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(tid) { vm.load(tid) }
            val tpl = s.template
            if (s.isLoading) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (tpl != null) {
                dispatchTemplateScreen(tpl, tid, navController)
            }
        }
        composable<LifeItemRoute> { entry ->
            val iid = entry.toRoute<LifeItemRoute>().itemId
            val vm: ItemDetailViewModel = hiltViewModel(); val s by vm.uiState.collectAsStateWithLifecycle(); LaunchedEffect(iid) { vm.load(iid) }
            val currentBackStackEntry by navController.currentBackStackEntryAsState()
            LaunchedEffect(currentBackStackEntry?.destination?.route) {
                if (currentBackStackEntry?.destination?.route?.contains("LifeItemRoute") == true && iid > 0) {
                    vm.load(iid)
                }
            }
            ItemDetailScreen(item = s.item, template = s.template, viewModel = vm, onBack = { navController.popBackStack() }, onEdit = { s.item?.let { item -> navController.navigate(LifeEditRoute(item.id)) } }, onDelete = { vm.deleteItem(); navController.popBackStack() })
        }
        composable<LifeCreateRoute> { entry ->
            val tid = entry.toRoute<LifeCreateRoute>().templateId
            val vm: CreateItemViewModel = hiltViewModel(); val s by vm.uiState.collectAsStateWithLifecycle(); LaunchedEffect(tid) { vm.load(tid) }
            LaunchedEffect(s.savedItemId) { if (s.savedItemId != null) { val id = s.savedItemId ?: return@LaunchedEffect; vm.resetSaved(); navController.navigate(LifeItemRoute(id)) { popUpTo(LifeCreateRoute(tid)) { inclusive = true } } } }
            val createTpl = s.template
            if (createTpl != null) {
                DynamicFormScreen(template = createTpl, existingItem = null, onSave = { title, data -> vm.saveItem(title, data) }, onBack = { navController.popBackStack() }, viewModel = vm)
            }
        }
        composable<LifeEditRoute> { entry ->
            val iid = entry.toRoute<LifeEditRoute>().itemId
            val vm: CreateItemViewModel = hiltViewModel(); val s by vm.uiState.collectAsStateWithLifecycle(); LaunchedEffect(iid) { vm.loadEdit(iid) }
            LaunchedEffect(s.savedItemId) { if (s.savedItemId != null) { vm.resetSaved(); navController.popBackStack() } }
            if (s.isLoading) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val createTpl = s.template
                val existingItem = s.existingItem
                if (createTpl != null && existingItem != null) {
                    DynamicFormScreen(template = createTpl, existingItem = existingItem, onSave = { title, data -> vm.saveItem(title, data) }, onBack = { navController.popBackStack() }, viewModel = vm)
                }
            }
        }
        composable<LifeFocusRoute> { FocusTimerScreen(onBack = { navController.popBackStack() }) }
        composable<LifeHabitRoute> { HabitListScreen(onBack = { navController.popBackStack() }, onItemClick = { navController.navigate(LifeItemRoute(it)) }, onAchievementClick = { navController.navigate(LifeAchievementRoute) }) }
        composable<LifeMoodRoute> { MoodListScreen(onBack = { navController.popBackStack() }) }
        composable<LifeJournalRoute> { JournalListScreen(onBack = { navController.popBackStack() }, onItemClick = { navController.navigate(LifeItemRoute(it)) }) }
        composable<LifeTodoRoute> {
            val vm: TodoTemplateViewModel = hiltViewModel()
            val s by vm.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { vm.load() }
            val tpl = s.template
            if (s.isLoading) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (tpl != null) {
                dispatchTemplateScreen(tpl, tpl.id, navController)
            } else {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }
        composable<LifeReportRoute> { ReportListScreen(onBack = { navController.popBackStack() }, onItemClick = { }) }
        composable<LifeAchievementRoute> { AchievementScreen(onBack = { navController.popBackStack() }) }
        composable<LifeTemplateManageRoute> { TemplateManageScreen(onBack = { navController.popBackStack() }, onCreateClick = { navController.navigate(LifeTemplateCreateRoute) }) }
        composable<LifeTemplateCreateRoute> { TemplateCreateScreen(onBack = { navController.popBackStack() }, onCreated = { navController.popBackStack() }) }
        composable<LifeStatsRoute> { LifeStatsScreen(onBack = { navController.popBackStack() }) }
        composable<LifeCategoryDetailRoute> { entry ->
            val category = entry.toRoute<LifeCategoryDetailRoute>().category
            CategoryDetailScreen(
                category = category,
                onBack = { navController.popBackStack() },
                onTemplateClick = { itemId -> navController.navigate(LifeItemRoute(itemId)) },
                onCreateClick = { tplId -> navController.navigate(LifeCreateRoute(tplId)) }
            )
        }
    }
}
