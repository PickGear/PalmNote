package com.palmnote.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.app.R
import com.palmnote.data.db.dao.CategoryCount
import com.palmnote.domain.model.toMoney
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToAsset: () -> Unit = {},
    onNavigateToBill: () -> Unit = {},
    onNavigateToLife: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToVault: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rawConfigs by viewModel.visibleConfigs.collectAsStateWithLifecycle()
    val presetCategoryOverrides by viewModel.presetCategoryOverrides.collectAsStateWithLifecycle()
    val categoryConfigs by viewModel.categoryConfigs.collectAsStateWithLifecycle()

    val hapticFeedback = LocalHapticFeedback.current
    val spacingPx = with(LocalDensity.current) { 16.dp.toPx() }
    val itemHeights = remember { mutableStateMapOf<CardType, Int>() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val greeting = remember { getGreeting(context) }
    val weekDay = remember { getWeekDay(context) }

    var draggedType by remember { mutableStateOf<CardType?>(null) }
    var overlayTopPx by remember { mutableFloatStateOf(0f) }
    var showCardDialog by remember { mutableStateOf(false) }
    var lastSwapTime by remember { mutableLongStateOf(0L) }
// 拖拽起点（卡片相对外层 Box 的 y）与累计位移，拖动过程中保持不变
    // 避免交换后卡片位置/节点坐标变化导致 overlay 跳动、反复横跳
    var dragStartOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragTotalY by remember { mutableFloatStateOf(0f) }

    val filterVisible: (DashboardCardConfig) -> Boolean = { config ->
        when (config.type) {
            CardType.BUDGET_ALERT -> {
                val budget = state.budget
                state.budgetReminderEnabled && budget != null && budget.totalBudget > 0 &&
                    state.monthlyExpense > 0 && state.monthlyExpense > budget.totalBudget * 0.8
            }
            CardType.ASSET_DISTRIBUTION -> state.assetDistribution.isNotEmpty()
            CardType.GOALS -> state.goalCount > 0
            CardType.ANNIVERSARIES -> state.anniversaryCount > 0
            CardType.HABIT_TODAY -> state.habitTotal > 0
            CardType.SUBSCRIPTION -> state.upcomingSubscriptions.isNotEmpty()
            else -> true
        }
    }

    val configs = remember(rawConfigs, state) {
        rawConfigs.filter(filterVisible)
    }

    val scrollState = rememberScrollState()
    val boxGlobalY = remember { mutableFloatStateOf(0f) }
    val cardGlobalYs = remember { mutableStateMapOf<CardType, Float>() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CompactTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = ModuleHome
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.search),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showCardDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.GridView,
                            contentDescription = stringResource(R.string.dashboard_card_manage),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { boxGlobalY.floatValue = it.positionInWindow().y }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    configs.forEachIndexed { index, config ->
                        key(config.type) {
                            val isDragged = draggedType == config.type
                            val cardShape = MaterialTheme.shapes.large
                            val animProgress = remember { Animatable(0f) }
                            LaunchedEffect(Unit) {
                                delay(index * 60L)
                                animProgress.animateTo(1f, animationSpec = tween(300))
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (isDragged) 100f else 0f)
                                    .graphicsLayer {
                                        // 拖拽时原卡片保留半透明占位，避免露出透明（0.3f 仍可看出位置）
                                        alpha = if (isDragged) 0.3f else animProgress.value
                                        translationY = if (isDragged) 0f else (1f - animProgress.value) * 12.dp.toPx()
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

                                                val freshConfigs = viewModel.visibleConfigs.value.filter(filterVisible)
                                                val idx = freshConfigs.indexOfFirst { it.type == config.type }
                                                val now = System.currentTimeMillis()

                                                if (idx >= 0 && now - lastSwapTime > 50) {
                                                    val belowType = freshConfigs.getOrNull(idx + 1)?.type
                                                    val aboveType = freshConfigs.getOrNull(idx - 1)?.type
                                                    val selfH = itemHeights[config.type] ?: 0
                                                    val overlayCenter = overlayTopPx + selfH * 0.5f

                                                    val belowCenterY = belowType?.let { t ->
                                                        cardGlobalYs[t]?.let { it - boxGlobalY.floatValue + (itemHeights[t]?.toFloat() ?: 0f) * 0.5f }
                                                    }
                                                    val aboveCenterY = aboveType?.let { t ->
                                                        cardGlobalYs[t]?.let { it - boxGlobalY.floatValue + (itemHeights[t]?.toFloat() ?: 0f) * 0.5f }
                                                    }

                                                    if (belowCenterY != null && overlayCenter > belowCenterY) {
                                                        viewModel.moveCardDown(config.type)
                                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        lastSwapTime = now
                                                    } else if (aboveCenterY != null && overlayCenter < aboveCenterY) {
                                                        viewModel.moveCardUp(config.type)
                                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        lastSwapTime = now
                                                    }
                                                }
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
                                    onHabitCheckIn = { viewModel.checkInHabit(it) },
                                    presetCategoryOverrides = presetCategoryOverrides,
                                    categoryConfigs = categoryConfigs
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
                            categoryConfigs = categoryConfigs
                        )
                    }
                }
            }
        }

        if (showCardDialog) {
            val cardConfigs by viewModel.cardConfigs.collectAsStateWithLifecycle()
            CardManagementDialog(
                allConfigs = cardConfigs,
                onToggle = { type -> viewModel.toggleCard(type) },
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
