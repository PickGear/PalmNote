package com.palmnote.ui.asset

import androidx.compose.foundation.background
import com.palmnote.domain.model.AssetStatus
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.animation.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.palmnote.ui.theme.ModuleItem
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.provider.Settings
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.roundToInt
import coil3.compose.AsyncImage
import com.palmnote.app.R
import com.palmnote.data.db.entity.Asset
import com.palmnote.data.db.entity.getWarrantyStatusText
import com.palmnote.data.db.entity.getShelfLifeStatusText
import com.palmnote.data.db.entity.getNearestExpiryText
import com.palmnote.data.db.entity.nearestExpiryDate
import com.palmnote.domain.model.toMoney
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*
import com.palmnote.ui.theme.ColorResolver

fun getCategoryIcon(category: String, customItems: List<CategoryItem>? = null): CategoryItem {
    val item = assetCategoryItems.find { it.name == category }
        ?: customItems?.find { it.name == category }
        // 兜底（预设/自定义分类都匹配不上）= 分类已被删除的「孤儿物品」，多发生在回收站恢复之后。
        // 红色 ✕ 是**有意的产品决策**（2026-10-11 用户定稿）：当作「分类已失效，该去编辑重挂」的
        // 警示信号，提醒用户及时调整。曾试改中性 Category 图标 + Gray400，被用户否掉换回。
        ?: CategoryItem(category, Icons.Outlined.Cancel, ErrorLight)
    val resolved = ColorResolver.resolve(category, item.color)
    return if (resolved != item.color) item.copy(color = resolved) else item
}

fun getCategoryDisplayName(category: String, context: android.content.Context, overrides: Map<String, String>? = null): String {
    val overrideKey = "preset_$category"
    val json = overrides?.get(overrideKey)
    if (json != null) {
        try {
            val obj = org.json.JSONObject(json)
            if (obj.has("name")) return obj.getString("name")
        } catch (_: Exception) {}
    }
    return if (assetCategoryItems.any { it.name == category })
        com.palmnote.ui.components.getCategoryName(category, context)
    else category
}

private val statusColorMap = mapOf(AssetStatus.HELD to StatusHeld, AssetStatus.AWAY to StatusAway, AssetStatus.REMOVED to StatusRemoved)
fun getStatusColor(status: AssetStatus): Color = statusColorMap[status] ?: StatusHeld

/** 「临期」门槛：距到期 7 天内。 */
internal const val EXPIRY_SOON_DAYS = 7

/** 面板吸附的「快甩」速度门槛（panelFraction 每秒变化量）：超过即由甩动方向定收/开。 */
private const val PANEL_FLING_FRACTION_VELOCITY = 1.5f

/**
 * 松手吸附目标：快甩看方向（iOS 大标题/苹果地图同款），慢放看位置（0.8 分界防误展开）。
 *
 * 速度为 panelFraction 每秒变化量（上滑收合方向为负）。抽成纯函数供单元测试穷举分支。
 */
internal fun panelSnapTarget(fraction: Float, velocity: Float): Float = when {
    velocity < -PANEL_FLING_FRACTION_VELOCITY -> 0f
    velocity > PANEL_FLING_FRACTION_VELOCITY -> 1f
    fraction > 0.8f -> 1f
    else -> 0f
}

/**
 * 到期徽标的紧迫度配色：已过期=红，[EXPIRY_SOON_DAYS] 天内=橙，其余=物品模块蓝。
 *
 * 颜色只作辅助，语义始终由文案承载（已过期 / 保质期N天 / 质保中）——不靠颜色单独传信息。
 */
internal fun expiryAccentColor(expireDate: Long): Color {
    val daysLeft = DateUtils.getDaysUntil(expireDate)
    return when {
        daysLeft < 0 -> StatusLost
        daysLeft <= EXPIRY_SOON_DAYS -> AccentOrange
        else -> ModuleItem
    }
}

@Composable
fun getStatusText(status: AssetStatus): String = when (status) {
    AssetStatus.HELD -> stringResource(R.string.asset_held)
    AssetStatus.AWAY -> stringResource(R.string.asset_away)
    AssetStatus.REMOVED -> stringResource(R.string.asset_removed)
}

@Composable
fun getAcquisitionText(type: String): String = when (type) {
    "PURCHASE" -> stringResource(R.string.acquisition_purchase)
    "GIFT" -> stringResource(R.string.acquisition_gift)
    "LOTTERY" -> stringResource(R.string.acquisition_lottery)
    "PRIZE" -> stringResource(R.string.acquisition_prize)
    "INHERITANCE" -> stringResource(R.string.acquisition_inheritance)
    "OTHER" -> stringResource(R.string.acquisition_other)
    else -> type
}

@Composable
fun getAcquisitionColor(type: String): Color = when (type) {
    "PURCHASE" -> AccentOrange
    "GIFT" -> Purple
    "LOTTERY" -> DeepOrange
    "PRIZE" -> Amber
    "INHERITANCE" -> Brown
    "OTHER" -> ModuleSettings
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun getCostText(costMode: String, purchasePrice: Long, useCount: Int, daysOwned: Int): String = when (costMode) {
    "PER_USE" -> if (useCount > 0) stringResource(R.string.asset_cost_per_use_price, String.format("%.1f", purchasePrice / 100.0 / useCount)) else stringResource(R.string.asset_cost_per_use_none)
    else -> stringResource(R.string.asset_cost_daily_price, String.format("%.1f", purchasePrice / 100.0 / daysOwned))
}

@Composable
fun AssetScreen(
    onNavigateToDetail: (Long) -> Unit = {},
    onNavigateToAdd: () -> Unit = {},
    viewModel: AssetViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var showSearch by remember { mutableStateOf(false) }
    val assetPresetOverrides by viewModel.presetCategoryOverrides.collectAsStateWithLifecycle()
    val assetCustomItems by viewModel.customCategories.collectAsStateWithLifecycle()
    BackHandler(enabled = showSearch) { showSearch = false; viewModel.setSearchQuery("") }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .exclude(WindowInsets.navigationBars),
        topBar = {
            CompactTopAppBar(
                title = {
                    if (showSearch) {
                        ModuleSearchBar(
                            query = state.searchQuery,
                            onQueryChange = { viewModel.setSearchQuery(it) },
                            onClear = { viewModel.setSearchQuery("") },
                            placeholder = stringResource(R.string.search),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    focusManager.clearFocus()
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = stringResource(R.string.settings_items),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = ModuleItem
                            )
                        }
                    }
                },
                actions = {
                    if (showSearch) {
                        TextButton(onClick = { showSearch = false; viewModel.setSearchQuery("") }, modifier = Modifier.padding(end = 4.dp)) {
                            Text(stringResource(R.string.cancel), style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        IconButton(onClick = { showSearch = true }) {
                Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.search),
                    tint = Color(0xFF616161))
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAdd,
                containerColor = ModuleItem,
                contentColor = Color.White,
                shape = MaterialTheme.shapes.large,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.asset_add), fontWeight = FontWeight.Medium) }
            )
        }
    ) { padding ->
        val isGridView = state.isGridView
        // 用**错落网格**的状态（瀑布流）而不是 LazyListState：见下方列表处的说明。
        val listState = rememberLazyStaggeredGridState()
        var showScrollToTop by remember { mutableStateOf(false) }
        val scrolledPast by remember {
            derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 100 }
        }
        LaunchedEffect(listState.isScrollInProgress, scrolledPast) {
            if (listState.isScrollInProgress && scrolledPast) {
                showScrollToTop = true
            } else if (scrolledPast) {
                kotlinx.coroutines.delay(600)
                showScrollToTop = false
            } else {
                showScrollToTop = false
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            // 布局：汇总卡（跟手面板）与筛选行固定在网格上方，物品卡在网格里滚。
            // 面板收合走**嵌套滚动消费**（CollapsingToolbar 同款）：上滑的滚动量先于
            // 网格被消费、面板跟手 1:1 收合，收完之后网格才从 0 开始滚——面板收到位
            // 时第一张物品卡必然完整贴合在筛选行下（不会被截）；下滑在网格顶部反向
            // 先展开面板、网格归零后再跟随。松手停在中间时弹簧吸附到最近档位
            // （跟手 + 弹性吸附，不是两档磁吸）。吸附方向「快甩看方向、慢放看位置」见
            // [panelSnapTarget]；想更 Q 弹调 dampingRatio，想更快调 stiffness
            // （磁吸强度：stiffness 越高吸附越快越果断，dampingRatio 越高越少回弹）。
            var panelHeightPx by remember { mutableStateOf(0) }
            // 面板与筛选行之间的 8dp 间距（收合时随面板一起消失）
            val panelGapPx = with(LocalDensity.current) { 8.dp.toPx() }
            // 面板开合比例：1 = 展开（汇总卡全显），0 = 收起（只剩筛选行）。跟手时直接
            // 写入（1:1 消费滚动量），松手/搜索时经 settlePanel 弹簧吸附或收合。
            // 初始值取 DataStore 的**实时**状态（panelExpanded StateFlow），而不是 ViewModel
            // 构造时的快照——页面切换到别处再回来时 remember 会重置，用快照会回到进入页面
            // 时的旧状态，用实时 StateFlow 才能保持用户**操作后**的最新收起/展开状态。
            val panelExpanded by viewModel.panelExpanded.collectAsStateWithLifecycle()
            var panelFraction by remember { mutableStateOf(if (panelExpanded) 1f else 0f) }
            // 跟手末速度记录：跟手时逐帧算 panelFraction 变化速度，松手时既作为「快甩
            // 看方向」的判据，也传给弹簧做初速度（速度连续才丝滑，否则从 0 起步会顿挫）。
            val followRef = remember { floatArrayOf(1f, 0f) } // [上一帧 fraction, 速度(每秒)]
            val followNanos = remember { longArrayOf(0L) }
            // 逐帧记录跟手速度（panelFraction 每秒变化量），供吸附继承末速度/判方向
            fun recordFollowVelocity(newFraction: Float) {
                val now = System.nanoTime()
                val prevNanos = followNanos[0]
                if (prevNanos != 0L) {
                    val dt = (now - prevNanos) / 1e9f
                    if (dt > 0f) followRef[1] = (newFraction - followRef[0]) / dt
                }
                followRef[0] = newFraction
                followNanos[0] = now
            }
            // 面板动画**单一所有权**：所有收/开动画只从 settlePanel 走，同一时刻只有这一个
            // Job 在写 panelFraction（新 settle 先 cancel 旧的）——松手吸附、搜索联动、
            // 无障碍动作互不打架；跟手抢回控制权靠 onPreScroll 消费时先 cancel。
            val panelScope = rememberCoroutineScope()
            val settleJob = remember { mutableStateOf<Job?>(null) }
            // 面板是否在**当前手势**里被消费过：松手吸附只在这个手势真动过面板时才结算，
            // 纯滚动网格的松手不能拿上一手势的陈旧末速度判方向（会误开/误收）。
            val panelTouched = remember { mutableStateOf(false) }
            // 落位触觉反馈（iOS 底部卡片同款）：只给手势结算，搜索联动/无障碍动作不给。
            val hapticFeedback = LocalHapticFeedback.current
            // 系统「移除动画」无障碍开关（动画时长缩放=0）：结算不做弹簧、直接落位，
            // Reduce Motion 同款语义（iOS 行为对齐）。
            val panelHostContext = LocalContext.current
            val reduceMotion = remember {
                Settings.Global.getFloat(
                    panelHostContext.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f
                ) == 0f
            }
            fun settlePanel(target: Float, initialVelocity: Float = 0f, persist: Boolean = true, haptic: Boolean = false) {
                settleJob.value?.cancel()
                settleJob.value = panelScope.launch {
                    if (reduceMotion) {
                        panelFraction = target
                    } else {
                        animate(
                            initialValue = panelFraction,
                            targetValue = target,
                            initialVelocity = initialVelocity.coerceIn(-3f, 3f),
                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                        ) { value, _ -> panelFraction = value }
                    }
                    if (haptic) hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (persist) viewModel.setPanelExpanded(target == 1f)
                }
            }
            val panelScrollConnection = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        // 上滑：面板跟手 1:1 收合，收完前的滚动量被面板消费（不让网格滚）
                        if (available.y < 0f && panelFraction > 0f) {
                            // 手指接住面板 = 收回动画控制权（settleJob 在飞时就地取消）
                            settleJob.value?.cancel()
                            panelTouched.value = true
                            val maxH = panelHeightPx + panelGapPx
                            val oldH = panelFraction * maxH
                            val newH = (oldH + available.y).coerceAtLeast(0f)
                            val newFraction = newH / maxH
                            recordFollowVelocity(newFraction)
                            panelFraction = newFraction
                            // 完全收到底：立即持久化收起（中途松手时由吸附分支写回）
                            if (newH <= 0f) viewModel.setPanelExpanded(false)
                            return Offset(0f, newH - oldH)
                        }
                        // 网格在顶部时下拉：面板跟手 1:1 展开（网格无法再下拉，滚动量给面板）
                        val atTop = listState.firstVisibleItemIndex == 0 &&
                            listState.firstVisibleItemScrollOffset == 0
                        if (available.y > 0f && panelFraction < 1f && atTop) {
                            settleJob.value?.cancel()
                            panelTouched.value = true
                            val maxH = panelHeightPx + panelGapPx
                            val oldH = panelFraction * maxH
                            val newH = (oldH + available.y).coerceAtMost(maxH)
                            val newFraction = newH / maxH
                            recordFollowVelocity(newFraction)
                            panelFraction = newFraction
                            // 完全展开到顶：立即持久化展开（中途松手时由吸附分支写回）
                            if (newH >= maxH) viewModel.setPanelExpanded(true)
                            return Offset(0f, newH - oldH)
                        }
                        return Offset.Zero
                    }
                }
            }
            // 搜索时面板收起；退出搜索**回到搜索前的状态**（持久化里存的就是它——
            // 搜索收合从不写持久化），而不是无条件展开：先收起了面板再搜索的用户，
            // 关掉搜索后应该还在收起态。首次不动画（面板已按持久化状态初始化）。
            var searchApplied by remember { mutableStateOf(false) }
            LaunchedEffect(showSearch) {
                if (!searchApplied) { searchApplied = true; return@LaunchedEffect }
                settlePanel(
                    if (showSearch) 0f else if (panelExpanded) 1f else 0f,
                    persist = false
                )
            }
            // 松手必收档：面板静止时永远停在展开(1)/收起(0)两档之一，不停半路。
            // 方向由 [panelSnapTarget] 定——快甩看方向、慢放看 0.8 分界（收 20% 即收，
            // 拉过 80% 才开，更稳、不易误展开；调 0.6/0.9 改松紧）。搜索态不吸附。
            LaunchedEffect(listState.isScrollInProgress) {
                if (listState.isScrollInProgress) {
                    panelTouched.value = false
                } else if (!showSearch && panelTouched.value) {
                    settlePanel(
                        panelSnapTarget(panelFraction, followRef[1]),
                        initialVelocity = followRef[1],
                        haptic = true
                    )
                }
            }

            val assetIndexMap = remember(state.filteredAssets) {
                state.filteredAssets.withIndex().associate { (i, a) -> a.id to i }
            }
            // 筛选行内容：常驻固定在汇总卡下方（见下方 Column），不随网格滚动。
            val filterItem: @Composable () -> Unit = {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            // Status + Category Filter Dropdowns + View Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status filter dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        var statusExpanded by remember { mutableStateOf(false) }
                        var statusBoxHeight by remember { mutableStateOf(0) }
                        val statusFilters = listOf(
                            AssetFilter.ALL to stringResource(R.string.asset_all),
                            AssetFilter.HELD to stringResource(R.string.asset_held),
                            AssetFilter.AWAY to stringResource(R.string.asset_away),
                            AssetFilter.REMOVED to stringResource(R.string.asset_removed)
                        )
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                                .clip(MaterialTheme.shapes.medium)
                                .onGloballyPositioned { statusBoxHeight = it.size.height }
                                .clickable { statusExpanded = true },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = statusFilters.find { it.first == state.selectedStatus }?.second ?: stringResource(R.string.asset_status_filter),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (state.selectedStatus != AssetFilter.ALL) FontWeight.Medium else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = if (statusExpanded) stringResource(R.string.bill_collapse) else stringResource(R.string.bill_expand),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(if (statusExpanded) 180f else 0f),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (statusExpanded) {
                            Popup(
                                onDismissRequest = { statusExpanded = false },
                                alignment = Alignment.TopEnd,
                                offset = IntOffset(0, statusBoxHeight),
                                properties = PopupProperties(focusable = true)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
                                    shape = MaterialTheme.shapes.large,
                                    // 弹层底色与它下方的筛选按钮同为 surface（浅色下即纯白 #FFFFFF）：
                                    // 原来用 background（#F8F6F3 米白）会和按钮差出一个色阶，看着像两块拼接。
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Column(Modifier.wrapContentWidth()) {
                                        statusFilters.forEach { (filter, label) ->
                                            Text(
                                                text = label,
                                                modifier = Modifier
                                                    .clickable { viewModel.setStatusFilter(filter); statusExpanded = false }
                                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Category filter dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        var catExpanded by remember { mutableStateOf(false) }
                        var catBoxHeight by remember { mutableStateOf(0) }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                                .clip(MaterialTheme.shapes.medium)
                                .onGloballyPositioned { catBoxHeight = it.size.height }
                                .clickable { catExpanded = true },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = state.selectedCategory?.let { getCategoryDisplayName(it, LocalContext.current, assetPresetOverrides) } ?: stringResource(R.string.bill_category),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (state.selectedCategory != null) FontWeight.Medium else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = if (catExpanded) stringResource(R.string.bill_collapse) else stringResource(R.string.bill_expand),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(if (catExpanded) 180f else 0f),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (catExpanded) {
                            Popup(
                                onDismissRequest = { catExpanded = false },
                                alignment = Alignment.TopEnd,
                                offset = IntOffset(0, catBoxHeight),
                                properties = PopupProperties(focusable = true)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
                                    shape = MaterialTheme.shapes.large,
                                    // 弹层底色与它下方的筛选按钮同为 surface（浅色下即纯白 #FFFFFF）：
                                    // 原来用 background（#F8F6F3 米白）会和按钮差出一个色阶，看着像两块拼接。
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    // 分类数无上界（20 个预设 + 用户自建），弹层必须封顶可滚：
                                    // 不限高的话条目一多就撑出屏幕，排在下面的分类永远点不到
                                    Column(
                                        Modifier
                                            .wrapContentWidth()
                                            .heightIn(max = 320.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Text(
                                            text = stringResource(R.string.asset_all_categories),
                                            modifier = Modifier
                                                .clickable { viewModel.setCategoryFilter(null); catExpanded = false }
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        state.categoryDistribution.forEach { cat ->
                                            Text(
                                                text = "${getCategoryDisplayName(cat.category, LocalContext.current, assetPresetOverrides)} (${cat.count})",
                                                modifier = Modifier
                                                    .clickable { viewModel.setCategoryFilter(cat.category); catExpanded = false }
                                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Sort filter dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        var sortExpanded by remember { mutableStateOf(false) }
                        var sortBoxHeight by remember { mutableStateOf(0) }
                        val sortOptions = listOf(
                            SortOption.RECENT to stringResource(R.string.asset_sort_recent),
                            SortOption.NAME to stringResource(R.string.asset_sort_name),
                            SortOption.PRICE to stringResource(R.string.asset_sort_price),
                            SortOption.DAILY_COST to stringResource(R.string.asset_sort_daily_cost),
                            SortOption.DATE to stringResource(R.string.asset_sort_date)
                        )
                        val isDefault = state.selectedSort == SortOption.RECENT
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                                .clip(MaterialTheme.shapes.medium)
                                .onGloballyPositioned { sortBoxHeight = it.size.height }
                                .clickable { sortExpanded = true },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (!isDefault) {
                                        "${sortOptions.find { it.first == state.selectedSort }?.second ?: ""}${if (state.sortAscending) " ↑" else " ↓"}"
                                    } else stringResource(R.string.asset_sort),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (!isDefault) FontWeight.Medium else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = if (sortExpanded) stringResource(R.string.bill_collapse) else stringResource(R.string.bill_expand),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(if (sortExpanded) 180f else 0f),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (sortExpanded) {
                            Popup(
                                onDismissRequest = { sortExpanded = false },
                                alignment = Alignment.TopEnd,
                                offset = IntOffset(0, sortBoxHeight),
                                properties = PopupProperties(focusable = true)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
                                    shape = MaterialTheme.shapes.large,
                                    // 弹层底色与它下方的筛选按钮同为 surface（浅色下即纯白 #FFFFFF）：
                                    // 原来用 background（#F8F6F3 米白）会和按钮差出一个色阶，看着像两块拼接。
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Column(Modifier.wrapContentWidth()) {
                                        sortOptions.forEach { (option, label) ->
                                            val isActive = option == state.selectedSort
                                            Text(
                                                text = if (isActive) {
                                                    "${label} ${if (state.sortAscending) "↑" else "↓"}"
                                                } else label,
                                                modifier = Modifier
                                                    .clickable { viewModel.setSortFilter(option); sortExpanded = false }
                                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // View toggle button
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { viewModel.toggleViewMode() },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.Apps,
                            contentDescription = if (isGridView) stringResource(R.string.asset_list_view) else stringResource(R.string.asset_grid_view),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
            }

            // **错落网格（瀑布流）**：列表态 1 列 = 普通列表，网格态 2 列各自按内容高度堆叠。
            //
            // 为什么不用等高网格：标题长度天然不等（"Yoga mat" 一行 / "Levoit LV-H133" 两行），
            // 等高只有两条路——**预留两行**（短名留一行空白，用户否掉）或**砍成一行**（长名被截，
            // 用户也否掉）。错落是唯一既不空白也不截字的排法；代价是同一横排的
            // 「日期 / 价格」不再横向对齐，这是瀑布流的固有取舍而不是缺陷。
            // 奇数张时最后一张会落进较矮的列（可能居右）——2026-10-11 曾试过换
            // LazyVerticalGrid 等宽网格解决，用户权衡后仍要瀑布流的自然高度，放弃。
            // 汇总卡（两档面板）+ 筛选行固定在网格上方，只有物品卡在网格里滚动：
            // 筛选行「滑动后位置固定」——上滑汇总卡弹合收起时它上移到顶并常驻，
            // 下拉面板弹开展现时它随之回落，始终不随内容滚走。
            // 汇总卡（两档面板）+ 筛选行 + 网格的间距全部显式控制（不用 spacedBy）：
            // 面板高度含其与筛选行间的 8dp（随收合消失），筛选行自带底部 8dp——
            // 收起后筛选行贴着顶栏下缘（0dp 基准），间距不会残留
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                val wasScrolling = remember { listState.isScrollInProgress }
                // 面板高度 = （内容高度 + 与筛选行间的 8dp 间距） × 档位比例：
                // 间距随面板一起收合，收起后筛选行贴着顶栏下缘（0dp 基准，与展开时
                // 汇总卡的顶边一致）；内容锚定顶部、超出部分裁掉——收档时面板像被
                // 筛选行「压」上去，弹簧回弹让切档带一次 Q 弹。
                // 无障碍：滚动手势/拖卡片之外，再给 TalkBack 补 stateDescription 与
                // 自定义「展开/收起」动作（零视觉变化），面板操作不依赖手势。
                val panelStateText = stringResource(
                    if (panelFraction > 0.5f) R.string.asset_panel_expanded else R.string.asset_panel_collapsed
                )
                val panelExpandActionLabel = stringResource(R.string.asset_panel_action_expand)
                val panelCollapseActionLabel = stringResource(R.string.asset_panel_action_collapse)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            // 回弹过冲会低于 0（收起方向）或高于内容高（展开方向），展开方向
                            // 的过冲保留为 Q 弹，收起方向钳到 0 防负尺寸崩溃
                            val height = ((panelHeightPx + 8.dp.toPx()) * panelFraction)
                                .coerceAtLeast(0f).roundToInt()
                            layout(constraints.maxWidth, height) {
                                placeable.place(0, 0)
                            }
                        }
                        .clipToBounds()
                        .semantics {
                            stateDescription = panelStateText
                            customActions = listOf(
                                CustomAccessibilityAction(panelExpandActionLabel) { settlePanel(1f); true },
                                CustomAccessibilityAction(panelCollapseActionLabel) { settlePanel(0f); true }
                            )
                        }
                        // 直接拖汇总卡本身也能收合/展开（iOS 大标题的导航栏区域同款）——
                        // 消费与结算和网格滚动共用同一套（fraction 消费 + panelSnapTarget）。
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = { settleJob.value?.cancel() },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    val maxH = panelHeightPx + panelGapPx
                                    val newH = (panelFraction * maxH + dragAmount).coerceIn(0f, maxH)
                                    val newFraction = newH / maxH
                                    recordFollowVelocity(newFraction)
                                    panelFraction = newFraction
                                    // 拖到两档端点同样即时持久化
                                    if (newH <= 0f) viewModel.setPanelExpanded(false)
                                    if (newH >= maxH) viewModel.setPanelExpanded(true)
                                },
                                onDragEnd = {
                                    settlePanel(
                                        panelSnapTarget(panelFraction, followRef[1]),
                                        initialVelocity = followRef[1],
                                        haptic = true
                                    )
                                },
                                onDragCancel = {
                                    settlePanel(
                                        panelSnapTarget(panelFraction, followRef[1]),
                                        initialVelocity = followRef[1],
                                        haptic = true
                                    )
                                }
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 收合后段渐隐（iOS 大标题同款）：内容被筛选行压到快截断时开始
                            // 淡出——0.65 起淡、0.15 全隐（expand 同一条曲线渐显）。顺带替代
                            // 原先的 alpha=0 兜底：面板高度为 0 时 clipToBounds 不裁剪、内容会
                            // 画到筛选行后面（Compose 特性），如今淡到全透明后自然不再可见。
                            // 只在 lambda 里读 fraction，拖动期间只更新图层不重组。
                            .graphicsLayer {
                                alpha = ((panelFraction - 0.15f) / 0.5f).coerceIn(0f, 1f)
                            }
                            .padding(horizontal = 16.dp)
                            .onGloballyPositioned { panelHeightPx = it.size.height },
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AnimatedCard(Modifier.weight(1f), index = 0, instant = showSearch || wasScrolling) {
                        ModuleCard(
                            tint = assetTint(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.asset_held),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = CurrencyUtils.formatCompact(LocalContext.current, state.heldValue.toMoney()),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = ModuleItem
                            )
                            Text(
                                text = pluralStringResource(R.plurals.asset_count, state.heldCount, state.heldCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        }

                        AnimatedCard(Modifier.weight(1f), index = 1, instant = showSearch || wasScrolling) {
                        ModuleCard(
                            tint = billTint(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.asset_away),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = pluralStringResource(R.plurals.asset_count, state.awayCount, state.awayCount),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = AccentOrange
                            )
                            Text(
                                text = stringResource(R.string.asset_removed) + " " + pluralStringResource(
                                    R.plurals.asset_count, state.removedCount, state.removedCount
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        }
                    }
                }
                // 筛选行：常驻固定在汇总卡下方，不随网格滚动（滑动后位置固定）。
                AnimatedCard(
                    modifier = Modifier.padding(bottom = 8.dp),
                    index = 2,
                    instant = showSearch
                ) {
                    filterItem()
                }
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(if (isGridView) 2 else 1),
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .nestedScroll(panelScrollConnection)
                        .nestedScroll(object : NestedScrollConnection {
                            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                focusManager.clearFocus()
                                return Offset.Zero
                            }
                        })
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                focusManager.clearFocus()
                            }
                        },
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalItemSpacing = 8.dp,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {

            // Asset List
            if (state.filteredAssets.isEmpty()) {
                // 占满整行：两列网格下空态只占半屏会很难看
                item(span = StaggeredGridItemSpan.FullLine) {
                    EmptyState(
                        icon = Icons.Outlined.Inventory2,
                        title = if (state.searchQuery.isNotEmpty()) stringResource(R.string.asset_not_found) else stringResource(R.string.asset_no_items),
                        subtitle = if (state.searchQuery.isNotEmpty()) stringResource(R.string.asset_try_other_keywords) else stringResource(R.string.asset_add_first_item),
                        tint = InfoBlue
                    )
                }
            } else {
                items(state.filteredAssets, key = { it.id }) { asset ->
                    // 错峰按「视觉上的行」推进，而不是按卡片序号：网格一行两张，
                    // 直接用序号会让每往下一行实际推进 100ms，比列表视图慢一倍；
                    // 除以列数后每行恒定 50ms，两种视图扫过速度一致。
                    val index = (assetIndexMap[asset.id] ?: 0) / (if (isGridView) 2 else 1) + 3
                    AnimatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        index = index.coerceAtMost(10),
                        // instant 只在入场/重播那一刻读一次，所以 key 要带上 isGridView：
                        // 只按 asset.id 记的话，"先滚动过再切换"的卡片会沿用旧的 instant=true 瞬跳，
                        // 和旁边重播动画的卡片混在一起。带上 isGridView 就按切换当下的真实滚动状态算。
                        instant = remember(asset.id, isGridView) { listState.isScrollInProgress },
                        // 列表/网格是同一批卡片换排法，item 的 key（asset.id）不变，
                        // 已经入场的卡片不会自动重播；这里显式重放，避免前几排静悄悄过去
                        restartKey = isGridView
                    ) {
                        if (isGridView) {
                            GridAssetCard(
                                modifier = Modifier.fillMaxWidth(),
                                asset = asset,
                                onClick = { onNavigateToDetail(asset.id) },
                                presetOverrides = assetPresetOverrides,
                                customItems = assetCustomItems
                            )
                        } else {
                            EnhancedAssetCard(
                                asset = asset,
                                onClick = { onNavigateToDetail(asset.id) },
                                presetOverrides = assetPresetOverrides,
                                customItems = assetCustomItems
                            )
                        }
                    }
                }
            }

            item(span = StaggeredGridItemSpan.FullLine) { Spacer(modifier = Modifier.height(80.dp)) }
            }
            }
            val scope = rememberCoroutineScope()
            AnimatedVisibility(
                visible = showScrollToTop,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 100.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.large)
                        .clickable {
                            // 回到顶部 = 「筛选行吸顶、汇总卡收起」状态：只滚网格归零，
                            // 面板保持收起；要看汇总卡请在顶部下拉
                            scope.launch { listState.animateScrollToItem(0) }
                        },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.asset_back_to_top),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EnhancedAssetCard(
    asset: Asset,
    onClick: () -> Unit,
    presetOverrides: Map<String, String>? = null,
    customItems: List<CategoryItem>? = null
) {
    val context = LocalContext.current
    val statusColor = getStatusColor(asset.status)
    val statusText = getStatusText(asset.status)
    val acquisitionText = getAcquisitionText(asset.acquisitionType)

    val acquisitionColor = getAcquisitionColor(asset.acquisitionType)
    val catInfo = remember(asset.category, presetOverrides, customItems) { getCategoryIcon(asset.category, customItems) }
    val daysOwned = DateUtils.getDaysSince(asset.effectiveDate).coerceAtLeast(1)

    ModuleCard(
        tint = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail / Category Icon + Quantity badge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(catInfo.color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    val firstImage = asset.images.toImageList().firstOrNull()
                    if (firstImage != null) {
                        AsyncImage(
                            model = firstImage,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = catInfo.icon,
                            contentDescription = null,
                            tint = catInfo.color,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                if (asset.quantity > 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = AccentOrange.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "×${asset.quantity}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentOrange,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Content
            Column(modifier = Modifier.weight(1f)) {
                // Row 1: Name —— **独占整行**且允许两行。
                // 原先名称与「保修/持有中」两个标签挤在同一行，两列网格下名称只剩三四个字，
                // 长名全被截成「罗技 G9…」「宜家 MA…」；标签移到下一行后名称有整行可用。
                Text(
                    text = asset.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (asset.warrantyExpireDate != null) {
                        StatusChip(
                            text = asset.getWarrantyStatusText(context),
                            color = expiryAccentColor(asset.warrantyExpireDate!!)
                        )
                    }
                    if (asset.shelfLifeExpireDate != null) {
                        StatusChip(
                            text = asset.getShelfLifeStatusText(context),
                            color = expiryAccentColor(asset.shelfLifeExpireDate!!)
                        )
                    }
                    StatusChip(text = statusText, color = statusColor)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Row 2: Acquisition type + Category + Days Used
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = acquisitionColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = acquisitionText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = acquisitionColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = getCategoryDisplayName(asset.category, context, presetOverrides),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = pluralStringResource(R.plurals.asset_days_used, daysOwned, daysOwned),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Row 3: Cost info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val costText = getCostText(asset.costMode, asset.purchasePrice, asset.useCount, daysOwned)

                    Text(
                        text = costText,
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentOrange,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = CurrencyUtils.formatCurrency(LocalContext.current, asset.purchasePrice.toMoney()),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Row 4: Location + Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (asset.location.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = asset.location,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Text(
text = DateUtils.formatDisplayYearDate(context, asset.effectiveDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun GridAssetCard(
    modifier: Modifier = Modifier,
    asset: Asset,
    onClick: () -> Unit,
    presetOverrides: Map<String, String>? = null,
    customItems: List<CategoryItem>? = null
) {
    val context = LocalContext.current
    val statusColor = getStatusColor(asset.status)
    val statusText = getStatusText(asset.status)
    val catInfo = remember(asset.category, presetOverrides, customItems) { getCategoryIcon(asset.category, customItems) }
    val daysOwned = DateUtils.getDaysSince(asset.effectiveDate).coerceAtLeast(1)
    val costText = getCostText(asset.costMode, asset.purchasePrice, asset.useCount, daysOwned)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Image/Avatar
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(catInfo.color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    val firstImage = asset.images.toImageList().firstOrNull()
                    if (firstImage != null) {
                        AsyncImage(
                            model = firstImage,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = catInfo.icon,
                            contentDescription = null,
                            tint = catInfo.color,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Content
                Column(modifier = Modifier.weight(1f)) {
                    // 与列表卡同一处理：名称独占整行、允许两行。两列网格下名称只剩半宽，
                    // 一行放不下长名，全被截成「IKEA MA…」「Logitech…」（真机截图 15-09-04）。
                    //
                    // 不加 minLines：瀑布流里各卡独立按内容堆叠，短名占一行天经地义；
                    // 强制两行只会给「机械键盘」这类一行名平白添一行空白
                    // （2026-10-11 等宽网格实验的残留曾导致「和之前不一样」，已撤）。
                    Text(
                        text = asset.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // 徽标行用 FlowRow：两列窄卡里「到期 + 持有中」放不下时**换行**，
                    // 而不是把最后一个徽标的文字挤成一条缝（真机截图 16:00：持有中被压成绿细条）。
                    // 放得下时仍是单行右对齐，观感不变。
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // 窄卡只放一个到期徽标：取质保 / 保质期里更紧迫的那个，按紧迫度着色。
                        // 加第三个徽标在英文（"Warranty" + "Shelf life"）下会顶出两列卡片宽度。
                        val deadline = asset.nearestExpiryDate
                        if (deadline != null) {
                            val deadlineColor = expiryAccentColor(deadline)
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = deadlineColor.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = asset.getNearestExpiryText(context),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = deadlineColor,
                                    maxLines = 1
                                )
                            }
                        }
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = statusColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = statusText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = statusColor,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (asset.quantity > 1) {
                            Text(
                                text = "×${asset.quantity}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentOrange,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        Text(
                            text = pluralStringResource(R.plurals.asset_used_days, daysOwned, daysOwned),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 网格卡不放「购买/赠与」那枚 chip：两列宽度下
                    // 「Purchase + Furniture + Daily ¥2.1」在英文里必然有一个被截
                    // （真机截图里是「Furni…」）。取舍标准是信息量——示例物品 10 件里 9 件都是
                    // 「购买」，而分类各不相同，所以留分类、把获得方式让给列表卡与详情页。
                    Text(
                        text = getCategoryDisplayName(asset.category, context, presetOverrides),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = costText,
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentOrange,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = DateUtils.formatDisplayYearDate(context, asset.effectiveDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = CurrencyUtils.formatCurrency(LocalContext.current, asset.purchasePrice.toMoney()),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
