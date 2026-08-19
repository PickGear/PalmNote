package com.palmnote.ui.life

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.palmnote.app.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.data.db.entity.getDisplayDescription
import com.palmnote.ui.components.CompactTopAppBar
import com.palmnote.ui.components.LifeScreenSkeleton
import com.palmnote.ui.components.ModuleSearchBar
import com.palmnote.ui.components.AppBottomSheet
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.CapsuleSwitch
import com.palmnote.ui.components.toComposeColor
import com.palmnote.ui.life.common.LifeSearchContent
import com.palmnote.ui.life.common.PlanCard
import com.palmnote.ui.life.common.SearchViewModel
import com.palmnote.ui.life.common.TimeCard
import com.palmnote.ui.life.common.WeeklyCalendar
import com.palmnote.ui.life.common.displayName
import com.palmnote.ui.life.common.formatRelativeTime
import com.palmnote.ui.life.common.getTodoPriority
import com.palmnote.ui.theme.*
import com.palmnote.ui.theme.PalmNoteTheme

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.booleanPreferencesKey

import androidx.datastore.preferences.core.edit
import com.palmnote.data.datastore.dataStore
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import java.time.ZoneId

@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeScreen(
    onNavigateToItem: (Long) -> Unit = {},
    onNavigateToCreate: (Long) -> Unit = {},
    onNavigateToFocus: () -> Unit = {},
    onNavigateToHabit: () -> Unit = {},
    onNavigateToMood: () -> Unit = {},
    onNavigateToJournal: () -> Unit = {},
    onNavigateToReport: () -> Unit = {},
    onNavigateToManage: () -> Unit = {},
    onNavigateToTodo: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToCategory: (String) -> Unit = {},
    viewModel: LifeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val searchViewModel: SearchViewModel = hiltViewModel()
    val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()
    val calendarExpanded by viewModel.calendarExpanded.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    var showSearch by remember { mutableStateOf(false) }
    BackHandler(enabled = showSearch) {
        showSearch = false
        searchViewModel.onQueryChange("")
    }
    var showFuncPage by remember { mutableStateOf(false) }
    var showCardDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets
                .exclude(WindowInsets.navigationBars),
            snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.navigationBarsPadding().padding(bottom = 60.dp)) },
            topBar = {
                CompactTopAppBar(
                    title = {
                        if (showSearch) {
                            ModuleSearchBar(
                                query = searchState.query,
                                onQueryChange = searchViewModel::onQueryChange,
                                onSearch = { searchViewModel.saveQuery() },
                                onClear = { searchViewModel.onQueryChange("") },
                                placeholder = stringResource(R.string.search),
                                autoFocus = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                stringResource(R.string.life_title),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = ModuleLife
                            )
                        }
                    },
                    actions = {
                        if (showSearch) {
                            TextButton(
                                onClick = { showSearch = false; searchViewModel.onQueryChange("") },
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text(stringResource(R.string.cancel), style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            IconButton(onClick = onNavigateToStats) {
                                Icon(
                                    Icons.Outlined.BarChart,
                                    stringResource(R.string.life_home_stats),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { showSearch = true }) {
                                Icon(
                                    Icons.Outlined.Search,
                                    stringResource(R.string.search),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { showCardDialog = true }) {
                                Icon(
                                    Icons.Outlined.GridView,
                                    stringResource(R.string.life_home_card_manage),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = onNavigateToManage) {
                                Icon(
                                    Icons.Outlined.Dashboard,
                                    stringResource(R.string.life_template_manage),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                if (!state.isLoading) {
                    ExtendedFloatingActionButton(
                        onClick = { showFuncPage = true },
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = Color.White,
                        shape = MaterialTheme.shapes.large,
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.life_new_create), fontWeight = FontWeight.Medium) }
                    )
                }
            }
        ) { innerPadding ->
            if (state.isLoading) { Box(Modifier.fillMaxSize().padding(innerPadding)) { LifeScreenSkeleton() }; return@Scaffold }
            if (showSearch) {
                LifeSearchContent(
                    state = searchState,
                    viewModel = searchViewModel,
                    onItemClick = onNavigateToItem,
                    onTemplateClick = onNavigateToCreate,
                    modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)
                )
            } else {
                LifeContent(
                    innerPadding = innerPadding, state = state,
                    onTodayTodosClick = onNavigateToTodo, onRetry = { viewModel.retry() },
                    onMoveCardUp = viewModel::moveCardUp, onMoveCardDown = viewModel::moveCardDown,
                    snackbarHostState = snackbarHostState, scope = scope, onCategoryClick = onNavigateToCategory,
                    calendarExpanded = calendarExpanded, selectedDate = selectedDate,
                    onSelectDate = viewModel::setSelectedDate, onCalendarExpandedChange = viewModel::setCalendarExpanded,
                    onItemClick = onNavigateToItem
                )
            }
        }

        if (showFuncPage) {
            FunctionSheet(
                templates = state.templates,
                planTemplates = state.planTemplates,
                timeTemplates = state.timeTemplates,
                recordTemplates = state.recordTemplates,
                onDismiss = { showFuncPage = false },
                onTemplateClick = { showFuncPage = false;
                    val tpl = state.templates.find { t -> t.id == it }
                    if (tpl != null) {
                        if (!tpl.isBuiltin && !tpl.isSpecial) {
                            onNavigateToCreate(it)
                        } else {
                            when (tpl.icon) {
                                "calendar_month" -> onNavigateToHabit()
                                "book" -> onNavigateToJournal()
                                "mood" -> onNavigateToMood()
                                "timer" -> onNavigateToFocus()
                                "BarChart", "assessment" -> onNavigateToReport()
                                else -> onNavigateToCreate(it)
                            }
                        }
                    }
                },
                onNavigateToCreate = onNavigateToCreate,
                onNavigateToFocus = onNavigateToFocus,
                onNavigateToHabit = onNavigateToHabit,
                onNavigateToMood = onNavigateToMood,
                onNavigateToJournal = onNavigateToJournal,
                onNavigateToReport = onNavigateToReport
            )
        }

        if (showCardDialog) {
            LifeCardManagementDialog(
                cardConfigs = state.cardConfigs,
                onToggle = viewModel::toggleCardVisible,
                onDismiss = { showCardDialog = false }
            )
        }
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FunctionSheet(
    templates: List<LifeTemplate>,
    planTemplates: List<LifeTemplate>,
    timeTemplates: List<LifeTemplate>,
    recordTemplates: List<LifeTemplate>,
    onDismiss: () -> Unit,
    onTemplateClick: (Long) -> Unit,
    onNavigateToCreate: (Long) -> Unit = {},
    onNavigateToFocus: () -> Unit = {},
    onNavigateToHabit: () -> Unit = {},
    onNavigateToMood: () -> Unit = {},
    onNavigateToJournal: () -> Unit = {},
    onNavigateToReport: () -> Unit = {}
) {
    AppBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
        ) {
            Text(stringResource(R.string.life_select_type_to_create), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))

            val planCategory = stringResource(R.string.life_category_plan)
            val timeCategory = stringResource(R.string.life_category_time)
            val recordCategory = stringResource(R.string.life_category_record)
            val customCategories = templates
                .map { it.category }
                .distinct()
                .filter { it != planCategory && it != timeCategory && it != recordCategory }
            val allCategories = buildList {
                add(planCategory)
                add(timeCategory)
                add(recordCategory)
                addAll(customCategories)
            }
            val activeCategories = allCategories.filter { cat ->
                when (cat) {
                    planCategory -> planTemplates.isNotEmpty()
                    timeCategory -> timeTemplates.isNotEmpty()
                    recordCategory -> recordTemplates.isNotEmpty()
                    else -> templates.any { it.category == cat }
                }
            }
            var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text(stringResource(R.string.life_category_filter_all), fontSize = 13.sp) }
                )
                activeCategories.forEach { cat ->
                    val label = when (cat) {
                        planCategory -> stringResource(R.string.life_category_goal)
                        timeCategory -> stringResource(R.string.life_category_memorial)
                        recordCategory -> stringResource(R.string.life_category_record_display)
                        else -> cat
                    }
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(label, fontSize = 13.sp) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val shown = selectedCategory?.let { cat ->
                    when (cat) {
                        planCategory -> planTemplates
                        timeCategory -> timeTemplates
                        recordCategory -> recordTemplates
                        else -> templates.filter { it.category == cat }
                    }
                } ?: templates
                if (shown.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.life_empty_here),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
                items(shown, key = { it.id }) { tpl ->
                    TemplateCard(tpl, onTemplateClick)
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(tpl: LifeTemplate, onTemplateClick: (Long) -> Unit) {
    val tplColor = tpl.color.toComposeColor(ModuleLife)
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onTemplateClick(tpl.id) },
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
                Icon(iconFromName(tpl.icon), null, tint = tplColor, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tpl.displayName(), fontWeight = FontWeight.Medium, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(2.dp))
                Text(tpl.getDisplayDescription(LocalContext.current), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@Composable
private fun LifeContent(
    innerPadding: PaddingValues, state: LifeUiState,
    onTodayTodosClick: () -> Unit, onRetry: () -> Unit,
    onMoveCardUp: (LifeHomeCardType) -> Unit, onMoveCardDown: (LifeHomeCardType) -> Unit,
    snackbarHostState: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope, onCategoryClick: (String) -> Unit,
    calendarExpanded: Boolean, selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit, onCalendarExpandedChange: (Boolean) -> Unit,
    onItemClick: (Long) -> Unit
) {
    val visibleConfigs = state.cardConfigs.filter { it.visible }
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).widthIn(max = 600.dp)) {
            if (state.error != null) {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(state.error ?: "", fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.life_screen_retry), color = MaterialTheme.colorScheme.onErrorContainer) }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            val cardGlobalYs = remember { mutableStateMapOf<LifeHomeCardType, Float>() }
            val itemHeights = remember { mutableStateMapOf<LifeHomeCardType, Int>() }
            val boxGlobalY = remember { mutableFloatStateOf(0f) }
            val draggedType = remember { mutableStateOf<LifeHomeCardType?>(null) }
            val haptic = LocalHapticFeedback.current
            visibleConfigs.forEachIndexed { index, config ->
                key(config.type) {
                    val isDragged = draggedType.value == config.type
                    val dragStartOffsetPx = remember { mutableFloatStateOf(0f) }
                    val overlayTopPx = remember { mutableFloatStateOf(0f) }
                    val lastSwapTime = remember { mutableLongStateOf(0L) }
                    var dragTotalY by remember { mutableFloatStateOf(0f) }
                    val cardModifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                        .zIndex(if (isDragged) 100f else 0f)
                        .graphicsLayer {
                            alpha = if (isDragged) 0.3f else 1f
                            translationY = if (isDragged) overlayTopPx.floatValue - dragStartOffsetPx.floatValue else 0f
                        }
                        .onGloballyPositioned {
                            cardGlobalYs[config.type] = it.positionInWindow().y
                            itemHeights[config.type] = it.size.height
                        }
                        .pointerInput(config.type) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    draggedType.value = config.type
                                    dragStartOffsetPx.floatValue = (cardGlobalYs[config.type] ?: 0f) - boxGlobalY.floatValue
                                    dragTotalY = 0f
                                    overlayTopPx.floatValue = dragStartOffsetPx.floatValue
                                    lastSwapTime.longValue = System.currentTimeMillis()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragTotalY += dragAmount.y
                                    overlayTopPx.floatValue = dragStartOffsetPx.floatValue + dragTotalY
                                    val fresh = state.cardConfigs.filter { it.visible }
                                    val i = fresh.indexOfFirst { it.type == config.type }
                                    val now = System.currentTimeMillis()
                                    if (i >= 0 && now - lastSwapTime.longValue > 50) {
                                        val belowType = fresh.getOrNull(i + 1)?.type
                                        val aboveType = fresh.getOrNull(i - 1)?.type
                                        val selfH = itemHeights[config.type] ?: 0
                                        val overlayCenter = overlayTopPx.floatValue + selfH * 0.5f
                                        val belowCenterY = belowType?.let { t ->
                                            (cardGlobalYs[t] ?: 0f) - boxGlobalY.floatValue + (itemHeights[t]?.toFloat() ?: 0f) * 0.5f
                                        }
                                        val aboveCenterY = aboveType?.let { t ->
                                            (cardGlobalYs[t] ?: 0f) - boxGlobalY.floatValue + (itemHeights[t]?.toFloat() ?: 0f) * 0.5f
                                        }
                                        if (belowCenterY != null && overlayCenter > belowCenterY) {
                                            onMoveCardDown(config.type)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            lastSwapTime.longValue = now
                                        } else if (aboveCenterY != null && overlayCenter < aboveCenterY) {
                                            onMoveCardUp(config.type)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            lastSwapTime.longValue = now
                                        }
                                    }
                                },
                                onDragEnd = { draggedType.value = null; dragTotalY = 0f; lastSwapTime.longValue = 0L },
                                onDragCancel = { draggedType.value = null; dragTotalY = 0f; lastSwapTime.longValue = 0L }
                            )
                        }
                    Box(modifier = Modifier.fillMaxWidth().onGloballyPositioned { boxGlobalY.floatValue = it.positionInWindow().y }) {
                        FadeUp(delay = index * 50, modifier = Modifier.fillMaxWidth()) {
                            when (config.type) {
                                LifeHomeCardType.CATEGORY -> CategoryHomeCard(
                                    state = state,
                                    modifier = cardModifier,
                                    onCategoryClick = onCategoryClick
                                )
                                LifeHomeCardType.TODAY_BOARD -> TodayBoardHomeCard(
                                    state = state,
                                    modifier = cardModifier,
                                    selectedDate = selectedDate,
                                    expanded = calendarExpanded,
                                    onSelectDate = onSelectDate,
                                    onExpandedChange = onCalendarExpandedChange,
                                    onItemClick = onItemClick
                                )
                                LifeHomeCardType.TODO -> TodoHomeCard(
                                    state = state,
                                    modifier = cardModifier,
                                    onViewAll = onTodayTodosClick,
                                    onItemClick = onItemClick
                                )
                            }
                        }
                    }
                }
            }
            if (visibleConfigs.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AutoStories, null, tint = ModuleLife.copy(alpha = 0.3f), modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.life_empty_here), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.life_empty_hint), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun FadeUp(delay: Int = 0, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(delay.toLong()); visible = true }
    val alpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = tween(250, easing = FastOutSlowInEasing))
    val offset by animateDpAsState(targetValue = if (visible) 0.dp else 12.dp, animationSpec = tween(250, easing = FastOutSlowInEasing))
    Box(modifier = modifier.alpha(alpha).offset(y = offset)) { content() }
}

@Composable
private fun CategoryHomeCard(state: LifeUiState, modifier: Modifier = Modifier, onCategoryClick: (String) -> Unit = {}) {
    val planCategory = stringResource(R.string.life_category_plan)
    val timeCategory = stringResource(R.string.life_category_time)
    val recordCategory = stringResource(R.string.life_category_record)
    val planTplIds = state.planTemplates.map { it.id }.toSet()
    val timeTplIds = state.timeTemplates.map { it.id }.toSet()
    val recordTplIds = state.recordTemplates.map { it.id }.toSet()
    val planTotal = state.planTemplates.sumOf { state.templatePreviewItems[it.id]?.size ?: 0 }
    val timeTotal = state.timeTemplates.sumOf { state.templatePreviewItems[it.id]?.size ?: 0 }
    val recordTotal = state.recordTemplates.sumOf { state.templatePreviewItems[it.id]?.size ?: 0 }
    val planToday = state.scheduledItems.count { it.templateId in planTplIds }
    val timeToday = state.scheduledItems.count { it.templateId in timeTplIds }
    val recordToday = state.scheduledItems.count { it.templateId in recordTplIds }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            CategoryMiniCard(
                title = stringResource(R.string.life_category_goal),
                icon = Icons.Default.Star,
                color = LifePlan,
                total = planTotal,
                today = planToday,
                onClick = { onCategoryClick(planCategory) }
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            CategoryMiniCard(
                title = stringResource(R.string.life_category_memorial),
                icon = Icons.Default.CalendarMonth,
                color = LifeTime,
                total = timeTotal,
                today = timeToday,
                onClick = { onCategoryClick(timeCategory) }
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            CategoryMiniCard(
                title = stringResource(R.string.life_category_record_display),
                icon = Icons.Default.AutoStories,
                color = LifeRecord,
                total = recordTotal,
                today = recordToday,
                onClick = { onCategoryClick(recordCategory) }
            )
        }
    }
}

@Composable
private fun CategoryMiniCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, total: Int, today: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(24.dp).background(color.copy(alpha = 0.12f), RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("$total", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
            Text(
                stringResource(R.string.life_home_today_added, today),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TodayBoardHomeCard(state: LifeUiState, modifier: Modifier = Modifier, selectedDate: LocalDate = LocalDate.now(), expanded: Boolean = false, onSelectDate: (LocalDate) -> Unit = {}, onExpandedChange: (Boolean) -> Unit = {}, onItemClick: (Long) -> Unit = {}) {
    Card(
        modifier = modifier.clip(MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            val isToday = selectedDate == LocalDate.now()
            Text(
                if (isToday) stringResource(R.string.life_home_today_board)
                else stringResource(R.string.life_home_board_selected, selectedDate.monthValue, selectedDate.dayOfMonth),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = ModuleLife
            )
            Spacer(modifier = Modifier.height(8.dp))
            WeeklyCalendar(
                selectedDate = selectedDate,
                expanded = expanded,
                markedDates = state.markedDates,
                onSelectDate = onSelectDate,
                onExpandedChange = onExpandedChange
            )
            Spacer(modifier = Modifier.height(10.dp))
            TimeSlots(state = state, onItemClick = onItemClick)
        }
    }
}

@Composable
private fun TimeSlots(state: LifeUiState, onItemClick: (Long) -> Unit = {}) {
    val slots = listOf(
        stringResource(R.string.life_home_slot_morning),
        stringResource(R.string.life_home_slot_forenoon),
        stringResource(R.string.life_home_slot_afternoon),
        stringResource(R.string.life_home_slot_evening),
        stringResource(R.string.life_home_slot_allday)
    )
    val items = state.boardItems
    val morning = items.filter { val t = it.dueTime; t != null && t < 9 * 60 }
    val forenoon = items.filter { val t = it.dueTime; t != null && t in (9 * 60) until (12 * 60) }
    val afternoon = items.filter { val t = it.dueTime; t != null && t in (12 * 60) until (18 * 60) }
    val evening = items.filter { val t = it.dueTime; t != null && t >= 18 * 60 }
    val allDay = items.filter { it.dueTime == null }
    val buckets = listOf(morning, forenoon, afternoon, evening, allDay)
    if (items.isEmpty()) {
        Text(
            stringResource(R.string.life_board_empty),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        return
    }
    buckets.forEachIndexed { i, bucket ->
        if (bucket.isNotEmpty()) {
            Text(slots[i], fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            bucket.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onItemClick(item.id) }
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (item.status == "COMPLETED") Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        null,
                        tint = if (item.status == "COMPLETED") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(item.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    val dueTime = item.dueTime
                    if (dueTime != null) {
                        Text(
                            "${dueTime / 60}:${(dueTime % 60).toString().padStart(2, '0')}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun TodoHomeCard(state: LifeUiState, modifier: Modifier = Modifier, onViewAll: () -> Unit, onItemClick: (Long) -> Unit = {}) {
    val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val overdueCount = state.todoItems.count { val d = it.dueDate; d != null && d < todayStart }
    val shown = state.todoItems.take(4)
    Card(
        modifier = modifier.clip(MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.life_home_todo),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                if (overdueCount > 0) {
                    Text(
                        stringResource(R.string.life_home_todo_overdue, overdueCount),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    stringResource(R.string.life_home_todo_total, state.todoItems.size),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (shown.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.life_home_todo_empty), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                shown.forEach { item ->
                    val priority = getTodoPriority(item)
                    val dotColor = when (priority) {
                        "HIGH_URGENT", "HIGH" -> Color(0xFFF44336)
                        "URGENT" -> Color(0xFFFF9800)
                        else -> MaterialTheme.colorScheme.outline
                    }
                    val relLabel = when {
                        item.dueDate == null -> stringResource(R.string.life_home_todo_no_date)
                        item.dueDate!! < todayStart -> {
                            val days = ((todayStart - item.dueDate!!) / 86400000L).toInt()
                            stringResource(R.string.life_home_todo_overdue_days, days)
                        }
                        else -> stringResource(R.string.life_home_todo_future)
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(MaterialTheme.shapes.small).clickable { onItemClick(item.id) }.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.RadioButtonUnchecked,
                            null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(dotColor))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(item.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(relLabel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (state.todoItems.size > 4) {
                    TextButton(onClick = onViewAll, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.life_home_todo_view_all), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LifeCardManagementDialog(
    cardConfigs: List<LifeHomeCardConfig>,
    onToggle: (LifeHomeCardType) -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.life_home_card_manage), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 420.dp)
            ) {
                cardConfigs.forEach { config ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(config.type) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (config.type) {
                                LifeHomeCardType.CATEGORY -> stringResource(R.string.life_home_card_category)
                                LifeHomeCardType.TODAY_BOARD -> stringResource(R.string.life_home_today_board)
                                LifeHomeCardType.TODO -> stringResource(R.string.life_home_todo)
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        CapsuleSwitch(
                            checked = config.visible,
                            onCheckedChange = { onToggle(config.type) }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } }
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun LifeScreenPreview() { PalmNoteTheme { LifeScreen() } }
