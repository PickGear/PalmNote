package com.palmnote.ui.life

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler

import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.palmnote.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.PalmNoteApp
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.data.db.entity.getDisplayDescription
import com.palmnote.ui.components.CompactTopAppBar
import com.palmnote.ui.components.LifeScreenSkeleton
import com.palmnote.ui.components.AppBottomSheet
import com.palmnote.ui.components.ModuleSearchBar
import com.palmnote.ui.components.toComposeColor
import com.palmnote.ui.life.common.PlanCard
import com.palmnote.ui.life.common.TimeCard
import com.palmnote.ui.life.common.displayName
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeScreen(
    onNavigateToTemplate: (Long) -> Unit = {},
    onNavigateToItem: (Long) -> Unit = {},
    onNavigateToCreate: (Long) -> Unit = {},
    onNavigateToFocus: () -> Unit = {},
    onNavigateToHabit: () -> Unit = {},
    onNavigateToMood: () -> Unit = {},
    onNavigateToJournal: () -> Unit = {},
    onNavigateToReport: () -> Unit = {},
    onNavigateToManage: () -> Unit = {},
    onNavigateToTodo: () -> Unit = {},
    viewModel: LifeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    BackHandler(enabled = showSearch) { showSearch = false; searchQuery = "" }
    val contextLife = LocalContext.current
    val sectionExpanded by remember {
        contextLife.dataStore.data.map { data ->
            Triple(
                data[booleanPreferencesKey("life_plan_expanded")] ?: true,
                data[booleanPreferencesKey("life_time_expanded")] ?: true,
                data[booleanPreferencesKey("life_record_expanded")] ?: true
            )
        }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = Triple(true, true, true))
    val planExpanded = sectionExpanded.first
    val timeExpanded = sectionExpanded.second
    val recordExpanded = sectionExpanded.third
    var showFuncPage by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val searchPredicate: (LifeTemplate) -> Boolean = { tpl ->
        searchQuery.isBlank() || tpl.name.contains(searchQuery, ignoreCase = true)
    }
    val hasItems: (LifeTemplate) -> Boolean = { tpl ->
        state.templatePreviewItems[tpl.id]?.isNotEmpty() == true
    }
    val filteredPlans = state.planTemplates.filter { tpl -> searchPredicate(tpl) && hasItems(tpl) }
    val filteredTimes = state.timeTemplates.filter { tpl -> searchPredicate(tpl) && hasItems(tpl) }
    val filteredRecords = state.recordTemplates.filter { tpl -> searchPredicate(tpl) && hasItems(tpl) }

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
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                onClear = { searchQuery = "" },
                                placeholder = stringResource(R.string.search),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(stringResource(com.palmnote.R.string.life_title), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = ModuleLife)
                        }
                    },
                    actions = {
                        if (showSearch) {
                            TextButton(onClick = { showSearch = false; searchQuery = "" }, modifier = Modifier.padding(end = 4.dp)) {
                                Text(stringResource(R.string.cancel), style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            IconButton(onClick = { showSearch = true }) { Icon(Icons.Outlined.Search, stringResource(R.string.search), tint = MaterialTheme.colorScheme.primary) }
                            IconButton(onClick = onNavigateToManage) { Icon(Icons.Outlined.Dashboard, stringResource(R.string.life_template_manage), tint = MaterialTheme.colorScheme.primary) }
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
            LifeContent(
                innerPadding = innerPadding, state = state, showSearch = showSearch, searchQuery = searchQuery, onSearchChange = { searchQuery = it },
                filteredPlans = filteredPlans, filteredTimes = filteredTimes, filteredRecords = filteredRecords,
                planExpanded = planExpanded, timeExpanded = timeExpanded, recordExpanded = recordExpanded,
                onPlanToggle = { scope.launch { contextLife.dataStore.edit { it[booleanPreferencesKey("life_plan_expanded")] = !planExpanded } } }, onTimeToggle = { scope.launch { contextLife.dataStore.edit { it[booleanPreferencesKey("life_time_expanded")] = !timeExpanded } } }, onRecordToggle = { scope.launch { contextLife.dataStore.edit { it[booleanPreferencesKey("life_record_expanded")] = !recordExpanded } } },
                onTemplateClick = onNavigateToTemplate, onHabitClick = onNavigateToHabit, onFocusClick = onNavigateToFocus, onTodayTodosClick = onNavigateToTodo, onRetry = { viewModel.retry() },
                snackbarHostState = snackbarHostState, scope = scope
            )
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
                        when (tpl.icon) {
                            "calendar_month" -> onNavigateToHabit()
                            "book" -> onNavigateToJournal()
                            "mood" -> onNavigateToMood()
                            "timer" -> onNavigateToFocus()
                            "BarChart", "assessment" -> onNavigateToReport()
                            else -> onNavigateToCreate(it)
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (planTemplates.isNotEmpty()) {
                    item {
                        SectionHeader(stringResource(R.string.life_section_title_plan), LifePlan)
                    }
                    items(planTemplates, key = { it.id }) { tpl ->
                        TemplateCard(tpl, onTemplateClick)
                    }
                }
                if (timeTemplates.isNotEmpty()) {
                    item {
                        SectionHeader(stringResource(R.string.life_section_title_time), LifeTime)
                    }
                    items(timeTemplates, key = { it.id }) { tpl ->
                        TemplateCard(tpl, onTemplateClick)
                    }
                }
                if (recordTemplates.isNotEmpty()) {
                    item {
                        SectionHeader(stringResource(R.string.life_section_title_record), LifeRecord)
                    }
                    items(recordTemplates, key = { it.id }) { tpl ->
                        TemplateCard(tpl, onTemplateClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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



@Composable
private fun LifeContent(
    innerPadding: PaddingValues, state: LifeUiState, showSearch: Boolean, searchQuery: String, onSearchChange: (String) -> Unit,
    filteredPlans: List<LifeTemplate>, filteredTimes: List<LifeTemplate>, filteredRecords: List<LifeTemplate>,
    planExpanded: Boolean, timeExpanded: Boolean, recordExpanded: Boolean,
    onPlanToggle: () -> Unit, onTimeToggle: () -> Unit, onRecordToggle: () -> Unit,
    onTemplateClick: (Long) -> Unit, onHabitClick: () -> Unit, onFocusClick: () -> Unit, onTodayTodosClick: () -> Unit, onRetry: () -> Unit,
    snackbarHostState: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope
) {
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()).widthIn(max = 600.dp)) {
        if (state.error != null) {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(state.error ?: "", fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.life_screen_retry), color = MaterialTheme.colorScheme.onErrorContainer) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        AnimatedVisibility(
            visible = !showSearch,
            enter = fadeIn(tween(120)) + expandVertically(tween(120)),
            exit = fadeOut(tween(300)) + shrinkVertically(tween(300))
        ) {
            StatsRow(state = state, scope = scope, snackbarHostState = snackbarHostState, onHabitClick = onHabitClick, onFocusClick = onFocusClick, onTodayTodosClick = onTodayTodosClick)
        }
        if (!showSearch) Spacer(modifier = Modifier.height(16.dp))
        var hasVisible = false
        if (filteredPlans.isNotEmpty()) { hasVisible = true
            LifeSection(title = stringResource(R.string.life_section_title_plan), iconColor = LifePlan, count = stringResource(R.string.life_section_count, filteredPlans.size), templates = filteredPlans, isTimeSection = false, previewItems = state.templatePreviewItems, onTemplateClick = onTemplateClick, expanded = planExpanded, onToggle = onPlanToggle)
            Spacer(modifier = Modifier.height(20.dp))
        }
        if (filteredTimes.isNotEmpty()) { hasVisible = true
            LifeSection(title = stringResource(R.string.life_section_title_time), iconColor = LifeTime, count = stringResource(R.string.life_section_count_time, filteredTimes.size), templates = filteredTimes, isTimeSection = true, previewItems = state.templatePreviewItems, onTemplateClick = onTemplateClick, expanded = timeExpanded, onToggle = onTimeToggle)
            Spacer(modifier = Modifier.height(20.dp))
        }
        if (filteredRecords.isNotEmpty()) { hasVisible = true
            LifeSection(title = stringResource(R.string.life_section_title_record), iconColor = LifeRecord, count = stringResource(R.string.life_section_count_record, filteredRecords.size), templates = filteredRecords, isTimeSection = false, previewItems = state.templatePreviewItems, onTemplateClick = onTemplateClick, expanded = recordExpanded, onToggle = onRecordToggle)
            Spacer(modifier = Modifier.height(20.dp))
        }
        if (!hasVisible) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (showSearch && searchQuery.isNotBlank()) {
                        Icon(Icons.Default.SearchOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.life_screen_search_template_not_found, searchQuery), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                            Icon(Icons.Default.AutoStories, null, tint = ModuleLife.copy(alpha = 0.3f), modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.life_empty_here), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.life_empty_hint), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(80.dp))
    }
    }
}

@Composable
private fun StatsRow(state: LifeUiState, scope: kotlinx.coroutines.CoroutineScope, snackbarHostState: SnackbarHostState, onHabitClick: () -> Unit, onFocusClick: () -> Unit, onTodayTodosClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val cards = mutableListOf<OverviewCard>()
        cards.add(OverviewCard("${state.todayTodos}", stringResource(R.string.life_screen_overview_today_todos), LifeTime))
        cards.add(OverviewCard("${state.habitCompletionRate}%", stringResource(R.string.life_screen_overview_habit_rate), LifeRecord))
        cards.add(OverviewCard("${state.todayFocusMinutes}m", stringResource(R.string.life_screen_overview_today_focus), LifeFocus))
        val click0: () -> Unit = onTodayTodosClick
        val click1: () -> Unit = { onHabitClick() }
        val click2: () -> Unit = { onFocusClick() }
        val clicks = listOf(click0, click1, click2)
        cards.forEachIndexed { i, card ->
            FadeUp(delay = i * 50, modifier = Modifier.weight(1f)) {
                Card(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = clicks[i]), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(card.value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = card.color)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(card.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LifeSection(
    title: String, iconColor: Color, count: String,
    templates: List<LifeTemplate>, isTimeSection: Boolean,
    previewItems: Map<Long, List<LifeItem>>,
    onTemplateClick: (Long) -> Unit,
    expanded: Boolean = true, onToggle: () -> Unit = {}
) {
    val planTitle = stringResource(R.string.life_section_title_plan)
    val timeTitle = stringResource(R.string.life_section_title_time)
    val sectionIcon = when (title) { planTitle -> Icons.AutoMirrored.Filled.Assignment; timeTitle -> Icons.Default.CalendarMonth; else -> Icons.Default.AutoStories }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(MaterialTheme.shapes.large).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onToggle),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(24.dp).background(iconColor.copy(alpha = 0.12f), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) { Icon(sectionIcon, null, tint = iconColor, modifier = Modifier.size(16.dp)) }
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.weight(1f))
                Text(count, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            if (expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    templates.forEachIndexed { i, tpl ->
                        FadeUp(delay = i * 30) {
                            if (isTimeSection) TimeCard(tpl, previewItems[tpl.id] ?: emptyList(), iconColor, onClick = { onTemplateClick(tpl.id) })
                            else PlanCard(tpl, previewItems[tpl.id] ?: emptyList(), iconColor, onClick = { onTemplateClick(tpl.id) })
                        }
                    }
                }
            }
            if (!expanded && templates.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    templates.forEach { tpl ->
                        val tplColor = tpl.color.toComposeColor(iconColor)
                        Box(
                            modifier = Modifier.size(32.dp).background(tplColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(iconFromName(tpl.icon), null, tint = tplColor, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

private data class OverviewCard(val value: String, val label: String, val color: Color)

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun LifeScreenPreview() { PalmNoteTheme { LifeScreen() } }
