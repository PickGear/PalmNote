package com.palmnote.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.palmnote.app.R
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToAsset: () -> Unit = {},
    onNavigateToBill: () -> Unit = {},
    onNavigateToLife: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToVault: () -> Unit = {},
    onNavigateToAddBill: () -> Unit = {},
    onNavigateToAddAsset: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rawConfigs by viewModel.visibleConfigs.collectAsStateWithLifecycle()
    val allConfigs by viewModel.cardConfigs.collectAsStateWithLifecycle()
    val presetCategoryOverrides by viewModel.presetCategoryOverrides.collectAsStateWithLifecycle()
    val categoryConfigs by viewModel.categoryConfigs.collectAsStateWithLifecycle()
    val demoModeOn by viewModel.demoModeOn.collectAsStateWithLifecycle()
    val profileNickname by viewModel.profileNickname.collectAsStateWithLifecycle()
    val profileAvatarPath by viewModel.profileAvatarPath.collectAsStateWithLifecycle()

    val hapticFeedback = LocalHapticFeedback.current
    val itemHeights = remember { mutableStateMapOf<CardType, Int>() }
    val context = androidx.compose.ui.platform.LocalContext.current
    // 不包 remember：问候语按时段切换，长驻会话跨过时点后要靠重组时重算跟上
    val greeting = getGreeting(context)

    val dashboardMessages = remember {
        listOf(
            context.getString(R.string.tip_1),
            context.getString(R.string.tip_2),
            context.getString(R.string.tip_3),
            context.getString(R.string.tip_4),
            context.getString(R.string.tip_5),
            context.getString(R.string.tip_6),
            context.getString(R.string.tip_7),
            context.getString(R.string.tip_8),
            context.getString(R.string.tip_9),
            context.getString(R.string.tip_10),
            context.getString(R.string.tip_11),
            context.getString(R.string.tip_12),
            context.getString(R.string.tip_13),
            context.getString(R.string.tip_14),
            context.getString(R.string.tip_15),
            context.getString(R.string.quote_1),
            context.getString(R.string.quote_2),
            context.getString(R.string.quote_3),
            context.getString(R.string.quote_4),
            context.getString(R.string.quote_5),
            context.getString(R.string.quote_6),
            context.getString(R.string.quote_7),
            context.getString(R.string.quote_8),
            context.getString(R.string.quote_9),
            context.getString(R.string.quote_10),
            context.getString(R.string.quote_11),
            context.getString(R.string.quote_12)
        )
    }

    val persistentMessageMode by viewModel.dashboardMessageMode.collectAsStateWithLifecycle()

    var messageIndex by remember {
        mutableIntStateOf(if (persistentMessageMode) (Math.random() * (dashboardMessages.size + 1)).toInt() else 0)
    }

    LaunchedEffect(persistentMessageMode) {
        if (persistentMessageMode) {
            messageIndex = (Math.random() * (dashboardMessages.size + 1)).toInt()
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = object : androidx.lifecycle.LifecycleEventObserver {
            override fun onStateChanged(source: androidx.lifecycle.LifecycleOwner, event: androidx.lifecycle.Lifecycle.Event) {
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && persistentMessageMode) {
                    messageIndex = (Math.random() * (dashboardMessages.size + 1)).toInt()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val showMessage = persistentMessageMode

    var draggedType by remember { mutableStateOf<CardType?>(null) }
    var overlayTopPx by remember { mutableFloatStateOf(0f) }
    var showCardDialog by remember { mutableStateOf(false) }
    var lastSwapTime by remember { mutableLongStateOf(0L) }
// 拖拽起点（卡片相对外层 Box 的 y）与累计位移，拖动过程中保持不变
    // 避免交换后卡片位置/节点坐标变化导致 overlay 跳动、反复横跳
    var dragStartOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragTotalY by remember { mutableFloatStateOf(0f) }

    // 没有数据的卡片自动隐藏，保持首页干净；显隐开关只决定哪些卡片参与展示
    val filterVisible: (DashboardCardConfig) -> Boolean = { config ->
        when (config.type) {
            CardType.BUDGET_ALERT -> {
                val budget = state.budget
                state.budgetReminderEnabled &&
                    budget != null &&
                    budget.totalBudget > 0 &&
                    state.monthlyExpense > 0 &&
                    state.monthlyExpense > budget.totalBudget * 0.8
            }
            CardType.ASSET_DISTRIBUTION -> state.assetDistribution.isNotEmpty()
            CardType.ANNIVERSARIES -> state.anniversaryCount > 0
            CardType.HABIT_TODAY -> state.habitTotal > 0
            CardType.SUBSCRIPTION -> state.upcomingSubscriptions.isNotEmpty()
            else -> true
        }
    }

    val configs = remember(rawConfigs, state) {
        rawConfigs.filter(filterVisible)
    }

    // 加载期不放骨架屏（2026-10-28 用户改定）：渲染页面框架（顶栏身份行），
    // 数据到齐后无缝接卡片游行。门槛保留不能去——否则净资产卡会先以零值渲染
    // 一秒再跳数。冷启动约 1 秒（SQLCipher 开库在 Application 阶段已并行预开，
    // 这段主要是查询集 + 状态装配），用户看到的不再是空窗而是「页面已开、内容就位中」。
    if (!state.loaded) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 静态身份行：与真顶栏同位同形（32dp 圆角方徽标 + 标题 + 三图标，
                // 无交互），数据到齐后无缝换成真顶栏、不跳变
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ModuleHome),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "P",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PalmNote",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(20.dp))
                Icon(Icons.Outlined.GridView, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(20.dp))
                Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val scrollState = rememberScrollState()
    val boxGlobalY = remember { mutableFloatStateOf(0f) }
    val boxHeightPx = remember { mutableIntStateOf(0) }
    val cardGlobalYs = remember { mutableStateMapOf<CardType, Float>() }
    val density = LocalDensity.current
    // 拖拽中指针的窗口 y：边缘自动滚动用
    var dragPointerWindowY by remember { mutableFloatStateOf(0f) }

    // 回归的两档制：会话首次进首页 = 卡片错峰游行；之后每次切回 = 整页一拍
    // 上浮就位（240ms 淡入 + 10dp 上浮，减速曲线）——纯淡入偏平淡，位移给一拍
    // 「落定归位」的到达感；游行中途切走，回来也走整页拍。
    val pageEntered = rememberSaveable { mutableStateOf(false) }
    // 「是否首次入场」必须在组合期一次性定格：九张卡的 LaunchedEffect 同一批次
    // 顺序执行，谁先跑到谁就会把 pageEntered 置 true、把兄弟卡的入场全部短路
    // （真机踩过：冷启动只有第一张卡出现，其余卡 alpha=0 永久隐身）
    val firstEntrance = !pageEntered.value
    val pageProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // 两个分支都以整页淡入开场：回归 240ms（上浮就位），首进 200ms——把
        // 「顶栏框架 → 卡片游行」的内容出现切换盖住，不再有硬切
        pageProgress.animateTo(
            1f,
            animationSpec = tween(if (firstEntrance) 200 else 240, easing = FastOutSlowInEasing)
        )
        pageEntered.value = true
    }

    // 交换判定：onDrag 与边缘自动滚动共用——指针停在边缘不动时内容仍在滚过，
    // 同样要触发换位（此前判定只在 onDrag 里跑，自动滚动换了位也不认）
    fun trySwap(draggedType: CardType) {
        val freshConfigs = viewModel.visibleConfigs.value.filter(filterVisible)
        val idx = freshConfigs.indexOfFirst { it.type == draggedType }
        val now = System.currentTimeMillis()
        if (idx < 0 || now - lastSwapTime <= 50) return
        val belowType = freshConfigs.getOrNull(idx + 1)?.type
        val aboveType = freshConfigs.getOrNull(idx - 1)?.type
        val selfH = itemHeights[draggedType] ?: 0
        val overlayCenter = overlayTopPx + selfH * 0.5f
        val belowCenterY = belowType?.let { t ->
            cardGlobalYs[t]?.let { it - boxGlobalY.floatValue + (itemHeights[t]?.toFloat() ?: 0f) * 0.5f }
        }
        val aboveCenterY = aboveType?.let { t ->
            cardGlobalYs[t]?.let { it - boxGlobalY.floatValue + (itemHeights[t]?.toFloat() ?: 0f) * 0.5f }
        }
        if (belowCenterY != null && overlayCenter > belowCenterY) {
            viewModel.moveCardDown(draggedType)
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            lastSwapTime = now
        } else if (aboveCenterY != null && overlayCenter < aboveCenterY) {
            viewModel.moveCardUp(draggedType)
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            lastSwapTime = now
        }
    }

    // 边缘自动滚动：拖拽手势消费了全部位移，父容器无法自然滚动——指针贴近上/下
    // 边缘时按贴近程度程序化滚动，排在屏外的卡片因此可达。滚一帧做一次交换判定。
    LaunchedEffect(draggedType) {
        val type = draggedType ?: return@LaunchedEffect
        val edgeZone = with(density) { 96.dp.toPx() }
        while (true) {
            val top = boxGlobalY.floatValue
            val bottom = top + boxHeightPx.intValue
            val py = dragPointerWindowY
            val d = when {
                py in top..(top + edgeZone) -> -((py - top) / edgeZone).coerceIn(0f, 1f)
                py in (bottom - edgeZone)..bottom -> ((bottom - py) / edgeZone).coerceIn(0f, 1f)
                else -> 0f
            }
            if (d != 0f && scrollState.maxValue > 0) {
                scrollState.scrollTo((scrollState.value + d * 14f).roundToInt())
                trySwap(type)
            }
            delay(16)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CompactTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (persistentMessageMode) {
                                        messageIndex = (Math.random() * (dashboardMessages.size + 1)).toInt()
                                    }
                                },
                                onLongClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleDashboardMessageMode()
                                    if (!persistentMessageMode) {
                                        messageIndex = (Math.random() * (dashboardMessages.size + 1)).toInt()
                                    }
                                }
                            )
                    ) {
                        AnimatedContent(
                            targetState = showMessage,
                            transitionSpec = {
                                fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 } togetherWith
                                    fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 2 }
                            },
                            label = "titleTransition"
                        ) { showMsg ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!showMsg) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(ModuleHome),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (profileAvatarPath.isNotBlank()) {
                                            AsyncImage(
                                                model = File(profileAvatarPath),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(
                                                text = "P",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                if (showMsg && persistentMessageMode) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(ModuleHome)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = if (showMsg) {
                                        if (messageIndex == 0) greeting else dashboardMessages[messageIndex - 1]
                                    } else if (profileNickname.isNotBlank()) {
                                        profileNickname
                                    } else {
                                        "PalmNote"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (showMsg) ModuleHome else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.search),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showCardDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.GridView,
                            contentDescription = stringResource(R.string.dashboard_card_manage),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned {
                        boxGlobalY.floatValue = it.positionInWindow().y
                        boxHeightPx.intValue = it.size.height
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = pageProgress.value
                            translationY = (1f - pageProgress.value) * 10.dp.toPx()
                        }
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    configs.forEachIndexed { index, config ->
                        key(config.type) {
                            val isDragged = draggedType == config.type
                            val animProgress = remember { Animatable(if (firstEntrance) 0f else 1f) }
                            // 错峰入场，动画规格与各页共用的 AnimatedCard 一致
                            // （340ms FastOutSlowIn、20dp 上滑）。间隔取 100ms = AnimatedCard
                            // 50ms × 2：观感快慢取决于「间隔 ÷ 卡片高度」的扫过速度，首页单卡
                            // 约为物品列表行的两倍高，间隔同步放倍后两种页面扫过速度一致。
                            // 不直接套 AnimatedCard：拖拽置顶的 zIndex 依赖本层 Box 的父子作用域。
                            // 无 NavHost 转场：内容动画独占入场，不被叠加掩盖。
                            // 游行只在会话首次进首页跑（firstEntrance 组合期定格见上）；
                            // 回归走整页一拍，卡片直接就位。
                            LaunchedEffect(Unit) {
                                if (!firstEntrance) return@LaunchedEffect
                                delay(index * 100L)
                                animProgress.animateTo(1f, animationSpec = tween(340, easing = FastOutSlowInEasing))
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (isDragged) 100f else 0f)
                                    .graphicsLayer {
                                        // 拖拽时原卡片保留半透明占位，避免露出透明（0.3f 仍可看出位置）
                                        alpha = if (isDragged) 0.3f else animProgress.value
                                        translationY = if (isDragged) 0f else (1f - animProgress.value) * 20.dp.toPx()
                                    }
                                    .onGloballyPositioned {
                                        cardGlobalYs[config.type] = it.positionInWindow().y
                                        itemHeights[config.type] = it.size.height
                                    }
                                    .pointerInput(config.type) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { _ ->
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                draggedType = config.type
                                                val cardY = cardGlobalYs[config.type] ?: 0f
                                                dragStartOffsetPx = cardY - boxGlobalY.floatValue
                                                dragTotalY = 0f
                                                overlayTopPx = dragStartOffsetPx
                                                lastSwapTime = System.currentTimeMillis()
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                // 累加增量位移（与布局无关），避免交换后节点坐标变化导致跳动
                                                dragTotalY += dragAmount.y
                                                overlayTopPx = dragStartOffsetPx + dragTotalY
                                                dragPointerWindowY = (cardGlobalYs[config.type] ?: 0f) + change.position.y
                                                trySwap(config.type)
                                            },
                                            onDragEnd = {
                                                draggedType = null
                                                dragTotalY = 0f
                                                lastSwapTime = 0L
                                            },
                                            onDragCancel = {
                                                draggedType = null
                                                dragTotalY = 0f
                                                lastSwapTime = 0L
                                            }
                                        )
                                    }
                            ) {
                                DashboardCardContent(
                                    type = config.type,
                                    state = state,
                                    onNavigateToAsset = onNavigateToAsset,
                                    onNavigateToBill = onNavigateToBill,
                                    onNavigateToLife = onNavigateToLife,
                                    onNavigateToVault = onNavigateToVault,
                                    onNavigateToAddBill = onNavigateToAddBill,
                                    onNavigateToAddAsset = onNavigateToAddAsset,
                                    onHabitCheckIn = { viewModel.checkInHabit(it) },
                                    presetCategoryOverrides = presetCategoryOverrides,
                                    categoryConfigs = categoryConfigs,
                                    cardConfigs = allConfigs,
                                    demoModeOn = demoModeOn
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(80.dp))
                }

                // 浮动拖拽层
                draggedType?.let { type ->
                    val cardShape = MaterialTheme.shapes.large
                    Box(
                        modifier = Modifier
                            .zIndex(200f)
                            .offset { IntOffset(0, overlayTopPx.roundToInt()) }
                            .padding(horizontal = 16.dp)
                            .graphicsLayer {
                                scaleX = 1.04f
                                scaleY = 1.04f
                                shadowElevation = 40f
                                shape = cardShape
                                clip = true
                            }
                            .background(MaterialTheme.colorScheme.surface, cardShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), cardShape)
                    ) {
                        DashboardCardContent(
                            type = type,
                            state = state,
                            onNavigateToAsset = onNavigateToAsset,
                            onNavigateToBill = onNavigateToBill,
                            onNavigateToLife = onNavigateToLife,
                            onNavigateToVault = onNavigateToVault,
                            onHabitCheckIn = { viewModel.checkInHabit(it) },
                            presetCategoryOverrides = presetCategoryOverrides,
                            categoryConfigs = categoryConfigs,
                            cardConfigs = allConfigs,
                            demoModeOn = demoModeOn
                        )
                    }
                }
            }
        }

        if (showCardDialog) {
            CardManagementDialog(
                allConfigs = allConfigs,
                onToggle = { type -> viewModel.toggleCard(type) },
                onColorChange = { type, color -> viewModel.setCardCustomColor(type, color) },
                onDismiss = { showCardDialog = false }
            )
        }
    }
}

internal fun getGreeting(context: android.content.Context): String {
    val cal = java.util.Calendar.getInstance()
    val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 6 -> context.getString(R.string.greeting_night)
        hour < 9 -> context.getString(R.string.greeting_morning)
        hour < 12 -> context.getString(R.string.greeting_forenoon)
        hour < 14 -> context.getString(R.string.greeting_noon)
        hour < 18 -> context.getString(R.string.greeting_afternoon)
        hour < 21 -> context.getString(R.string.greeting_evening)
        else -> context.getString(R.string.greeting_night)
    }
}

internal fun getWeekDay(context: android.content.Context): String {
    val cal = java.util.Calendar.getInstance()
    val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
    return when (dayOfWeek) {
        java.util.Calendar.SUNDAY -> context.getString(R.string.weekday_sunday)
        java.util.Calendar.MONDAY -> context.getString(R.string.weekday_monday)
        java.util.Calendar.TUESDAY -> context.getString(R.string.weekday_tuesday)
        java.util.Calendar.WEDNESDAY -> context.getString(R.string.weekday_wednesday)
        java.util.Calendar.THURSDAY -> context.getString(R.string.weekday_thursday)
        java.util.Calendar.FRIDAY -> context.getString(R.string.weekday_friday)
        java.util.Calendar.SATURDAY -> context.getString(R.string.weekday_saturday)
        else -> context.getString(R.string.weekday_sunday)
    }
}

