@file:Suppress("TooManyFunctions")

package com.palmnote.ui.life

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.palmnote.app.R
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.data.db.entity.getDisplayDescription
import com.palmnote.data.db.entity.getDisplayName
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.model.parseChecklist
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.domain.repository.LifeTemplateRepository
import com.palmnote.domain.util.BuiltinTemplates
import com.palmnote.ui.components.AppBottomSheet
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.theme.LifePlan
import com.palmnote.ui.theme.LifeRecord
import com.palmnote.ui.theme.LifeTime
import com.palmnote.ui.theme.ModuleLife
import com.palmnote.ui.theme.lifePlanTint
import com.palmnote.ui.theme.lifeRecordTint
import com.palmnote.ui.theme.lifeTimeTint
import com.palmnote.ui.widget.WidgetUpdateHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class CategoryDetailUiState(
    val category: String = "",
    val templates: List<LifeTemplate> = emptyList(),
    val itemsByTemplate: Map<Long, List<LifeItem>> = emptyMap(),
    val weekNew: Int = 0,
    val dueTodayCount: Int = 0,
    val overdueCount: Int = 0,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    private val templateRepo: LifeTemplateRepository,
    private val itemRepo: LifeItemRepository,
    private val lifeItemDao: LifeItemDao,
    private val templateDao: LifeTemplateDao
) : ViewModel() {
    private val _uiState = MutableStateFlow(CategoryDetailUiState())
    val uiState: StateFlow<CategoryDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun load(category: String) {
        // 重新进入页面 / 切换分类会再调一次 load：不取消上一条链就有两条 collector 并存，
        // 旧分类的结果可能后到并盖掉新分类（最终值取决于谁最后发射）。
        loadJob?.cancel()
        val zone = ZoneId.systemDefault()
        // 文案是「本周 +%1$d」：口径必须是**自然周**（周一起始）。
        // 此前用 minusDays(6) 的滚动 7 天窗口，用户看到的「本周」其实是「近 7 天」。
        val weekStart = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay(zone).toInstant().toEpochMilli()
        val todayStart = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val todayEnd = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        templateRepo.getAllVisibleTemplates()
            .flatMapLatest { templates ->
                val inCategory = templates.filter { it.category == category }
                if (inCategory.isEmpty()) {
                    flowOf(CategoryLoad(inCategory, emptyMap(), 0, 0, 0))
                } else {
                    val flows: List<Flow<Pair<Long, List<LifeItem>>>> = inCategory.map { tpl ->
                        itemRepo.getActiveItemsByTemplate(tpl.id, 200).map { tpl.id to it }
                    }
                    combine(flows) { arrays ->
                        val merged = mutableMapOf<Long, List<LifeItem>>()
                        var newCount = 0
                        var dueToday = 0
                        var overdue = 0
                        arrays.forEach { (id, items) ->
                            val template = inCategory.firstOrNull { it.id == id }
                            merged[id] = sortForFirstDisplay(items, template)
                            newCount += items.count { it.createdAt >= weekStart }
                            // 头部统计 badge：口径与排序的 actionBucket 一致（firstActionEpoch）
                            items.forEach { item ->
                                if (item.status.equals("COMPLETED", ignoreCase = true)) return@forEach
                                val epoch = firstActionEpoch(template, item) ?: return@forEach
                                when {
                                    epoch < todayStart -> overdue++
                                    epoch < todayEnd -> dueToday++
                                }
                            }
                        }
                        CategoryLoad(inCategory, merged, newCount, dueToday, overdue)
                    }
                }
            }
            .onEach { loaded ->
                _uiState.update {
                    it.copy(
                        category = category,
                        templates = loaded.templates,
                        itemsByTemplate = loaded.items,
                        weekNew = loaded.weekNew,
                        dueTodayCount = loaded.dueToday,
                        overdueCount = loaded.overdue,
                        isLoading = false
                    )
                }
            }
            .launchIn(viewModelScope)
            .also { loadJob = it }
    }

    /** 条目完成状态切换（长按快捷操作）。幂等写法与看板 setItemCompleted 一致。 */
    fun completeItem(itemId: Long, completed: Boolean) {
        viewModelScope.launch {
            lifeItemDao.updateStatus(itemId, if (completed) "COMPLETED" else "ACTIVE")
            WidgetUpdateHelper.refreshTodoWidgets()
        }
    }

    /** 推迟到今天（看板同款语义：年度重复不参与推迟）。 */
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

    fun deleteItem(itemId: Long) {
        viewModelScope.launch {
            lifeItemDao.deleteItemCascade(itemId)
            WidgetUpdateHelper.refreshTodoWidgets()
        }
    }

    private data class CategoryLoad(
        val templates: List<LifeTemplate>,
        val items: Map<Long, List<LifeItem>>,
        val weekNew: Int,
        val dueToday: Int,
        val overdue: Int
    )
}

@Suppress("LongMethod", "LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    category: String,
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    onCreateClick: (Long) -> Unit,
    onEditTemplate: (Long) -> Unit,
    onOpenManageTemplates: () -> Unit = {},
    viewModel: CategoryDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(category) { viewModel.load(category) }
    var showPicker by remember { mutableStateOf(false) }
    // 长按条目弹出的快捷操作（模板 + 条目成对保存，推迟的年度重复守卫要读模板）
    var quickAction by remember { mutableStateOf<Pair<LifeTemplate, LifeItem>?>(null) }
    val accent = categoryAccent(state.category)
    val tint = categoryTint(state.category)

    Scaffold(
        topBar = {
            SecondaryTopAppBar(
                title = {
                    Text(categoryChipLabel(state.category), fontWeight = FontWeight.Bold, color = accent)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            if (state.templates.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        if (state.templates.size == 1) onCreateClick(state.templates.first().id) else showPicker = true
                    },
                    containerColor = accent
                ) {
                    Icon(Icons.Filled.Add, stringResource(R.string.life_category_new_item), tint = Color.White)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = accent)
            }
            state.templates.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOff,
                        null,
                        tint = accent.copy(alpha = 0.3f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.life_category_detail_empty),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onOpenManageTemplates,
                        colors = ButtonDefaults.textButtonColors(contentColor = accent)
                    ) {
                        Text(stringResource(R.string.life_category_detail_go_manage))
                    }
                }
            }
            else -> CategoryDetailContent(
                state = state,
                accent = accent,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onItemClick = onItemClick,
                onEditTemplate = onEditTemplate,
                onLongPressItem = { tpl, item -> quickAction = tpl to item }
            )
        }
    }

    if (showPicker) {
        TemplatePickerSheet(
            templates = state.templates,
            onDismiss = { showPicker = false },
            onSelect = { templateId ->
                showPicker = false
                onCreateClick(templateId)
            }
        )
    }

    quickAction?.let { (tpl, item) ->
        QuickActionSheet(
            template = tpl,
            item = item,
            onDismiss = { quickAction = null },
            onComplete = { completed ->
                viewModel.completeItem(item.id, completed)
                quickAction = null
            },
            onReschedule = {
                viewModel.rescheduleToToday(item.id)
                quickAction = null
            },
            onDelete = {
                viewModel.deleteItem(item.id)
                quickAction = null
            }
        )
    }
}

@Composable
private fun CategoryDetailContent(
    state: CategoryDetailUiState,
    accent: Color,
    modifier: Modifier = Modifier,
    onItemClick: (Long) -> Unit,
    onEditTemplate: (Long) -> Unit,
    onLongPressItem: (LifeTemplate, LifeItem) -> Unit
) {
    val today = remember { LocalDate.now() }
    // 分区折叠（LongArray 走 Bundle 存储的是副本，改动要整体替换）
    var collapsedSections by rememberSaveable { mutableStateOf(LongArray(0)) }
    var expandedCompleted by rememberSaveable { mutableStateOf(LongArray(0)) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        CategoryHeader(state = state, accent = accent)
        Spacer(modifier = Modifier.height(12.dp))
        state.templates.forEach { tpl ->
            val items = state.itemsByTemplate[tpl.id].orEmpty()
            val collapsed = tpl.id in collapsedSections
            CategorySectionHeader(
                tpl = tpl,
                count = items.size,
                accent = identityColor(tpl.color),
                collapsed = collapsed,
                onToggle = { collapsedSections = toggleId(collapsedSections, tpl.id) },
                onEdit = { onEditTemplate(tpl.id) }
            )
            if (collapsed) return@forEach
            CategoryTemplateSection(
                tpl = tpl,
                items = items,
                today = today,
                completedExpanded = tpl.id in expandedCompleted,
                onToggleCompleted = { expandedCompleted = toggleId(expandedCompleted, tpl.id) },
                onItemClick = onItemClick,
                onLongPressItem = onLongPressItem
            )
        }
        Spacer(modifier = Modifier.height(48.dp))
    }
}

/** 单个模板分区的条目渲染：进行中逐条罗列，已完成按阈值折叠或灰化追加。 */
@Composable
private fun CategoryTemplateSection(
    tpl: LifeTemplate,
    items: List<LifeItem>,
    today: LocalDate,
    completedExpanded: Boolean,
    onToggleCompleted: () -> Unit,
    onItemClick: (Long) -> Unit,
    onLongPressItem: (LifeTemplate, LifeItem) -> Unit
) {
    val accent = identityColor(tpl.color)
    // 排序已把完成沉底（sortForFirstDisplay）；这里只负责把完成态折叠或灰化
    val (active, completed) = items.partition { !it.status.equals("COMPLETED", ignoreCase = true) }
    active.forEach { item ->
        CategoryItemRow(tpl, item, lifeItemMeta(tpl, item, today), accent, { onItemClick(item.id) }) {
            onLongPressItem(tpl, item)
        }
        Spacer(Modifier.height(8.dp))
    }
    when {
        completed.isEmpty() -> Unit
        completed.size >= DONE_COLLAPSE_THRESHOLD -> {
            CompletedToggleRow(count = completed.size, expanded = completedExpanded, onToggle = onToggleCompleted)
            if (completedExpanded) {
                completed.forEach { item ->
                    CategoryItemRow(tpl, item, lifeItemMeta(tpl, item, today), accent, { onItemClick(item.id) }) {
                        onLongPressItem(tpl, item)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        else -> completed.forEach { item ->
            CategoryItemRow(tpl, item, lifeItemMeta(tpl, item, today), accent, { onItemClick(item.id) }) {
                onLongPressItem(tpl, item)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 已完成条目的自动折叠阈值：超过才收进「已完成 N」。 */
private const val DONE_COLLAPSE_THRESHOLD = 3

private fun toggleId(ids: LongArray, id: Long): LongArray =
    if (ids.contains(id)) ids.filterNot { it == id }.toLongArray() else ids + id

@Composable
private fun CategoryHeader(state: CategoryDetailUiState, accent: Color) {
    val total = state.itemsByTemplate.values.sumOf { it.size }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = categoryTint(state.category)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(categoryIcon(state.category), null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    categoryChipLabel(state.category),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = accent
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    StatBadge(
                        text = pluralStringResource(R.plurals.life_category_detail_count, total, total),
                        container = MaterialTheme.colorScheme.surface,
                        content = accent
                    )
                    if (state.dueTodayCount > 0) {
                        StatBadge(
                            text = stringResource(R.string.life_category_detail_due_today, state.dueTodayCount),
                            container = MaterialTheme.colorScheme.surface,
                            content = accent
                        )
                    }
                    if (state.overdueCount > 0) {
                        StatBadge(
                            text = stringResource(R.string.life_category_detail_overdue, state.overdueCount),
                            container = MaterialTheme.colorScheme.surface,
                            content = MaterialTheme.colorScheme.error
                        )
                    }
                    StatBadge(
                        text = stringResource(R.string.life_category_detail_week_new, state.weekNew),
                        container = Color.Transparent,
                        content = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 头部统计小徽标（tint 卡上的胶囊）。 */
@Composable
private fun StatBadge(text: String, container: Color, content: Color) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = content,
        modifier = Modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

@Composable
private fun CategorySectionHeader(
    tpl: LifeTemplate,
    count: Int,
    accent: Color,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(8.dp).background(accent, RoundedCornerShape(4.dp)))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            tpl.getDisplayName(LocalContext.current),
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(pluralStringResource(
            R.plurals.life_category_detail_count, count, count
        ), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            if (collapsed) "▸" else "▾",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = stringResource(R.string.life_template_edit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** 「已完成 N」折叠行（超过阈值时替代逐条罗列，点击展开）。 */
@Composable
private fun CompletedToggleRow(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.06f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.life_category_detail_done_count, count),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(if (expanded) "▾" else "▸", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(8.dp))
}

/** 内联条目卡：模板身份色图标容器 + 标题 + 语义徽标（逾期/今天/日期/重复/清单进度）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryItemRow(
    tpl: LifeTemplate,
    item: LifeItem,
    meta: LifeItemMeta,
    accent: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val done = item.status.equals("COMPLETED", ignoreCase = true)
    val shape = MaterialTheme.shapes.large
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (done) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f) else accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    iconFor(tpl.icon),
                    null,
                    tint = if (done) MaterialTheme.colorScheme.onSurfaceVariant else accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title.ifBlank { tpl.getDisplayName(context) },
                    fontWeight = if (done) FontWeight.Medium else FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (done) TextDecoration.LineThrough else null
                )
                if (!done) ItemMetaBadges(meta = meta, accent = accent)
            }
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

/** 行内语义徽标：时间类（逾期/记录相对时间/今天）+ 标记类（日期/每年/清单进度）。 */
@Composable
private fun ItemMetaBadges(meta: LifeItemMeta, accent: Color) {
    if (!meta.hasAnyBadge()) return
    Spacer(modifier = Modifier.height(3.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        TimeBadges(meta = meta, accent = accent)
        MarkerBadges(meta = meta)
    }
}

/** 时间类徽标：逾期（红）/ 记录相对时间 / 今天到期（身份色）。 */
@Composable
private fun TimeBadges(meta: LifeItemMeta, accent: Color) {
    meta.overdueDays?.let { days ->
        // 深红字 + 浅红底：原来用 onError（白）配 errorContainer（浅粉）＝白字粉底，基本看不见
        MetaBadge(
            text = stringResource(R.string.life_meta_overdue_days, days),
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer
        )
    }
    // 记录类（打卡/心情/日记…）：日期是"发生时间"，显示多久以前比"逾期"更说明问题
    meta.recordDaysAgo?.let { days ->
        MetaBadge(
            text = if (days == 0L) {
                stringResource(R.string.life_tpl_last_short_today)
            } else {
                stringResource(R.string.life_meta_days_ago, days)
            },
            container = accent.copy(alpha = 0.12f),
            content = accent
        )
    }
    meta.dueTodayTime?.let { time ->
        val timeText = if (time > 0) timeText(time) else ""
        MetaBadge(
            text = stringResource(R.string.life_meta_due_today_time, timeText).trim(),
            container = accent.copy(alpha = 0.12f),
            content = accent
        )
    }
}

/** 标记类徽标：未来日期 / 每年重复 / 清单进度（统一中性色）。 */
@Composable
private fun MarkerBadges(meta: LifeItemMeta) {
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val neutralContainer = neutral.copy(alpha = 0.08f)
    meta.dueDateText?.let { date ->
        MetaBadge(text = date, container = neutralContainer, content = neutral)
    }
    if (meta.yearly) {
        MetaBadge(
            text = stringResource(R.string.life_meta_repeat_yearly),
            container = neutralContainer,
            content = neutral
        )
    }
    meta.checklistTotal?.let { total ->
        MetaBadge(text = "${meta.checklistDone ?: 0}/$total", container = neutralContainer, content = neutral)
    }
}

@Composable
private fun MetaBadge(text: String, container: Color, content: Color) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = content,
        maxLines = 1,
        modifier = Modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** 长按条目的快捷操作（完成 ⇄ 未完成 / 推迟到今天 / 删除）。 */
@Composable
private fun QuickActionSheet(
    template: LifeTemplate,
    item: LifeItem,
    onDismiss: () -> Unit,
    onComplete: (Boolean) -> Unit,
    onReschedule: () -> Unit,
    onDelete: () -> Unit
) {
    val done = item.status.equals("COMPLETED", ignoreCase = true)
    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(
            item.title.ifBlank { template.getDisplayName(LocalContext.current) },
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(10.dp))
        QuickActionRow(
            icon = Icons.Filled.CheckCircle,
            label = stringResource(if (done) R.string.life_quick_action_reopen else R.string.life_quick_action_complete),
            tint = MaterialTheme.colorScheme.primary,
            onClick = { onComplete(!done) }
        )
        // 年度重复的日期由周年滚动接管，不参与「推迟到今天」（与看板守卫一致）
        if (!template.repeatYearly) {
            QuickActionRow(
                icon = Icons.Filled.Schedule,
                label = stringResource(R.string.life_quick_action_reschedule),
                tint = MaterialTheme.colorScheme.primary,
                onClick = onReschedule
            )
        }
        QuickActionRow(
            icon = Icons.Filled.Delete,
            label = stringResource(R.string.life_quick_action_delete),
            tint = MaterialTheme.colorScheme.error,
            onClick = onDelete
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun QuickActionRow(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun TemplatePickerSheet(
    templates: List<LifeTemplate>,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    val context = LocalContext.current
    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.life_category_pick_template), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(12.dp))
        templates.forEach { tpl ->
            val accent = identityColor(tpl.color)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable { onSelect(tpl.id) },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(iconFor(tpl.icon), null, tint = accent, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tpl.getDisplayName(context), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(
                            tpl.getDisplayDescription(context),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * M8 首显排序：未完成排在已完成前，再按最近的目标时点排序。
 * 目标时点优先取真实的截止日 / DATE 字段；没有日期时退回 updatedAt。
 */
internal fun sortForFirstDisplay(items: List<LifeItem>, template: LifeTemplate?): List<LifeItem> {
    val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return items.sortedWith(
        compareBy<LifeItem> { it.status.equals("COMPLETED", ignoreCase = true) }
            .thenBy { actionBucket(template, it, todayStart) }
            .thenBy { firstActionEpoch(template, it) ?: Long.MAX_VALUE }
            .thenByDescending { it.updatedAt }
    )
}

/** 0 = 已逾期 / 今天；1 = 未来；2 = 无日期；3 = 已完成。同桶内再按目标时间升序。 */
private fun actionBucket(template: LifeTemplate?, item: LifeItem, todayStart: Long): Int {
    if (item.status.equals("COMPLETED", ignoreCase = true)) return 3
    val epoch = firstActionEpoch(template, item) ?: return 2
    return if (epoch < todayStart + DAY_MS) 0 else 1
}

/** 取条目最近的行动时点；无明确日期时返回 null。 */
internal fun firstActionEpoch(template: LifeTemplate?, item: LifeItem): Long? {
    if (template == null) return null
    val obj = runCatching { Json.decodeFromString<JsonObject>(item.fieldsData) }.getOrNull() ?: return item.dueDate
    val configs = runCatching { Json.decodeFromString<List<FieldConfig>>(template.fieldsConfig) }.getOrDefault(emptyList())
    val checklist = configs.firstOrNull { it.type == FieldType.CHECKLIST && it.key == "subtasks" }
    if (checklist != null) {
        val hasOpen = parseChecklist(((obj["subtasks"] as? JsonPrimitive)?.contentOrNull).orEmpty()).any { !it.done }
        if (hasOpen) return item.dueDate
    }
    val dates = configs.asSequence()
        .filter { it.type == FieldType.DATE }
        .mapNotNull { cfg -> dateFieldEpoch(obj[cfg.key]) }
        .toList()
    return (listOfNotNull(item.dueDate) + dates).minOrNull()
}

internal fun dateFieldEpoch(value: kotlinx.serialization.json.JsonElement?): Long? {
    val raw = (value as? JsonPrimitive)?.contentOrNull ?: return null
    raw.toDoubleOrNull()?.let { return it.toLong() }
    return runCatching { LocalDate.parse(raw).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
}

/** 条目行的元信息：逾期天数 / 今日到期时刻 / 未来日期 / 每年重复 / 清单进度。 */
internal data class LifeItemMeta(
    val overdueDays: Long? = null,
    val dueTodayTime: Int? = null,
    val dueDateText: String? = null,
    /** 记录类条目的「多久以前」（0 = 今天）；非记录类为 null。 */
    val recordDaysAgo: Long? = null,
    val yearly: Boolean = false,
    val checklistDone: Int? = null,
    val checklistTotal: Int? = null
)

private const val DAY_MS = 86_400_000L

/**
 * 从条目现算行内徽标（不改 DB、不改 UiState）：口径与 [actionBucket] 一致，
 * 都以 [firstActionEpoch] 为唯一行动时点。已完成条目不打日期徽标（灰化已表达状态）。
 */
internal fun lifeItemMeta(
    template: LifeTemplate?,
    item: LifeItem,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): LifeItemMeta {
    val done = item.status.equals("COMPLETED", ignoreCase = true)
    val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val todayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val epoch = firstActionEpoch(template, item)
    // 记录类（打卡/心情/日记/专注/身体记录/订阅）与起点型模板（正数日）**不进逾期**——
    // 与首页 LifeBoardRows.overdueOn 同一套排除规则：它们的日期是"发生时间"而非"截止时间"，
    // 落在过去是常态语义。这类条目过去/今天改显示相对时间（今天 / N 天前），
    // **未来日期仍然显示**（订阅的"下次扣费"正是靠它，压掉就丢信息了）。
    val isRecordLike = template?.category == BuiltinTemplates.RECORD_CATEGORY ||
        template?.icon in START_ANCHOR_ICONS
    val (overdueDays, dueTodayTime, dueDateText) = timeBadges(
        isRecordLike = isRecordLike,
        done = done,
        epoch = epoch,
        dueTime = item.dueTime,
        today = today,
        todayStart = todayStart,
        todayEnd = todayEnd,
        zone = zone
    )
    val progress = checklistProgress(template, item)
    return LifeItemMeta(
        overdueDays = overdueDays,
        dueTodayTime = dueTodayTime,
        dueDateText = dueDateText,
        // 记录类条目的"多久以前"：只在有明确日期、且未完成时给（打卡记录正是靠它回答"上次什么时候"）
        recordDaysAgo = recordDaysAgo(isRecordLike, done, epoch, todayStart, todayEnd),
        yearly = template?.repeatYearly == true,
        checklistDone = progress?.first,
        checklistTotal = progress?.second
    )
}

/** 记录类条目的「多久以前」（0 = 今天）；非记录类 / 已完成 / 无日期 / 未来 → null。 */
private fun recordDaysAgo(
    isRecordLike: Boolean,
    done: Boolean,
    epoch: Long?,
    todayStart: Long,
    todayEnd: Long
): Long? {
    if (!isRecordLike || done || epoch == null) return null
    if (epoch >= todayEnd) return null
    return (todayStart - epoch).coerceAtLeast(0) / DAY_MS
}

/**
 * 时间类徽标取值：逾期天数 / 今天时刻 / 未来日期。
 * 记录类的"过去与今天"交给 [recordDaysAgo]（显示相对时间），这里只保留它的未来日期。
 */
private fun timeBadges(
    isRecordLike: Boolean,
    done: Boolean,
    epoch: Long?,
    dueTime: Int?,
    today: LocalDate,
    todayStart: Long,
    todayEnd: Long,
    zone: ZoneId
): Triple<Long?, Int?, String?> = when {
    done || epoch == null -> Triple(null, null, null)
    epoch >= todayEnd -> Triple(null, null, futureDateText(epoch, today, zone))
    isRecordLike -> Triple(null, null, null)
    epoch < todayStart -> Triple(((todayStart - epoch + DAY_MS - 1) / DAY_MS).coerceAtLeast(1), null, null)
    else -> Triple(null, dueTime ?: 0, null)
}

/** 是否至少有一个徽标要显示（决定要不要留出那 3dp 间距）。 */
internal fun LifeItemMeta.hasAnyBadge(): Boolean =
    listOf(overdueDays, dueTodayTime, dueDateText, recordDaysAgo, checklistTotal).any { it != null } || yearly

/** 未来日期：同年给短格式 MM-dd，跨年才带年份。 */
private fun futureDateText(epoch: Long, today: LocalDate, zone: ZoneId): String {
    val date = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()
    val pattern = if (date.year == today.year) "MM-dd" else "yyyy-MM-dd"
    return date.format(DateTimeFormatter.ofPattern(pattern))
}

/** 清单进度（n/m）：兼容纯字符串载荷与 CompoundPayload 对象两种存储形态。 */
private fun checklistProgress(template: LifeTemplate?, item: LifeItem): Pair<Int, Int>? {
    if (template == null) return null
    val obj = runCatching { Json.decodeFromString<JsonObject>(item.fieldsData) }.getOrNull() ?: return null
    val hasChecklist = runCatching { Json.decodeFromString<List<FieldConfig>>(template.fieldsConfig) }
        .getOrDefault(emptyList())
        .any { it.type == FieldType.CHECKLIST && it.key == "subtasks" }
    val payload = obj["subtasks"]?.let { v ->
        if (v is JsonPrimitive) v.contentOrNull else v.toString()
    }
    val parsed = if (hasChecklist) parseChecklist(payload) else emptyList()
    return if (parsed.isEmpty()) null else parsed.count { it.done } to parsed.size
}

/** dueTime 是「当天的分钟数」（agendaTime 同口径）→ HH:mm。 */
private fun timeText(minutesOfDay: Int): String {
    val h = (minutesOfDay / 60).toString().padStart(2, '0')
    val m = (minutesOfDay % 60).toString().padStart(2, '0')
    return "$h:$m"
}

/** 分类 → 身份色：已知三分类走主题真源色，自定义分类退回模块色（页面框架的语义色）。 */
@Composable
private fun categoryAccent(category: String): Color = when (category) {
    BuiltinTemplates.PLAN_CATEGORY -> LifePlan
    BuiltinTemplates.TIME_CATEGORY -> LifeTime
    BuiltinTemplates.RECORD_CATEGORY -> LifeRecord
    else -> ModuleLife
}

/** 分类 → tint 底（头部卡）：已知三分类带暗色适配，自定义分类用模块色 alpha。 */
@Composable
private fun categoryTint(category: String): Color = when (category) {
    BuiltinTemplates.PLAN_CATEGORY -> lifePlanTint()
    BuiltinTemplates.TIME_CATEGORY -> lifeTimeTint()
    BuiltinTemplates.RECORD_CATEGORY -> lifeRecordTint()
    else -> ModuleLife.copy(alpha = 0.12f)
}

private fun categoryIcon(category: String): ImageVector = when (category) {
    BuiltinTemplates.PLAN_CATEGORY -> Icons.Filled.Star
    BuiltinTemplates.TIME_CATEGORY -> Icons.Filled.CalendarMonth
    BuiltinTemplates.RECORD_CATEGORY -> Icons.Filled.AutoStories
    else -> Icons.Filled.Star
}

