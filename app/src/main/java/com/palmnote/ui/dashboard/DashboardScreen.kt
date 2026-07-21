package com.palmnote.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.shape.CircleShape
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.R
import com.palmnote.data.db.dao.CategoryCount
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
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rawConfigs by viewModel.visibleConfigs.collectAsStateWithLifecycle()

    val hapticFeedback = LocalHapticFeedback.current
    val spacingPx = with(LocalDensity.current) { 16.dp.toPx() }
    val itemHeights = remember { mutableStateMapOf<CardType, Int>() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val greeting = remember { getGreeting(context) }
    val weekDay = remember { getWeekDay(context) }

    var draggedType by remember { mutableStateOf<CardType?>(null) }
    var overlayTopPx by remember { mutableFloatStateOf(0f) }
    var initialTouchOffset by remember { mutableFloatStateOf(0f) }
    var showCardDialog by remember { mutableStateOf(false) }
    var lastSwapTime by remember { mutableLongStateOf(0L) }

    val filterVisible: (DashboardCardConfig) -> Boolean = { config ->
        when (config.type) {
            CardType.BUDGET_ALERT -> {
                val budget = state.budget
                state.budgetReminderEnabled && budget != null && budget.totalBudget > 0 &&
                    state.monthlyExpense > 0 && state.monthlyExpense > budget.totalBudget * 0.8
            }
            CardType.ASSET_DISTRIBUTION -> state.assetDistribution.isNotEmpty()
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
                    configs.forEach { config ->
                        key(config.type) {
                            val isDragged = draggedType == config.type
                            val cardShape = MaterialTheme.shapes.large

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (isDragged) 100f else 0f)
                                    .graphicsLayer {
                                        alpha = if (isDragged) 0f else 1f
                                    }
                                    .onGloballyPositioned {
                                        cardGlobalYs[config.type] = it.positionInWindow().y
                                        itemHeights[config.type] = it.size.height
                                    }
                                    .pointerInput(config.type) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { offset ->
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                draggedType = config.type
                                                initialTouchOffset = offset.y
                                                val cardY = cardGlobalYs[config.type] ?: 0f
                                                overlayTopPx = cardY - boxGlobalY.floatValue
                                                lastSwapTime = System.currentTimeMillis()
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                val cardY = cardGlobalYs[config.type] ?: return@detectDragGesturesAfterLongPress
                                                overlayTopPx = cardY + change.position.y - boxGlobalY.floatValue - initialTouchOffset

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
                                                lastSwapTime = 0L
                                            },
                                            onDragCancel = {
                                                draggedType = null
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
                                    onNavigateToLife = onNavigateToLife
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(80.dp))
                }

                // 浮层
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
                    ) {
                        DashboardCardContent(
                            type = type,
                            state = state,
                            onNavigateToAsset = onNavigateToAsset,
                            onNavigateToBill = onNavigateToBill,
                            onNavigateToLife = onNavigateToLife
                        )
                    }
                }
            }
        }

        if (showCardDialog) {
            CardManagementDialog(
                allConfigs = viewModel.cardConfigs.value,
                onToggle = { type -> viewModel.toggleCard(type) },
                onDismiss = { showCardDialog = false }
            )
        }
    }
}

@Composable
private fun DashboardCardContent(
    type: CardType,
    state: DashboardState,
    onNavigateToAsset: () -> Unit,
    onNavigateToBill: () -> Unit,
    onNavigateToLife: () -> Unit
) {
    when (type) {
        CardType.NET_WORTH -> NetWorthCard(state)
        CardType.QUICK_ACTIONS -> QuickActionsCard(onNavigateToBill, onNavigateToAsset, onNavigateToLife)
        CardType.BUDGET_ALERT -> BudgetAlertCard(state)
        CardType.GOALS -> GoalsCard(state, onNavigateToLife)
        CardType.ANNIVERSARIES -> AnniversariesCard(state, onNavigateToLife)
        CardType.ASSET_DISTRIBUTION -> AssetDistributionCard(state, onNavigateToAsset)
        CardType.TODAY -> TodayCard(state)
    }
}

@Composable
private fun NetWorthCard(state: DashboardState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, PrimaryGreenLight.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(PrimaryGreenLight, PrimaryGreenLight.copy(alpha = 0.85f))
                    )
                )
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.dashboard_net_worth),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = CurrencyUtils.formatCompact(state.totalAssetValue),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(stringResource(R.string.dashboard_monthly_expense), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                        Text(
                            CurrencyUtils.formatCurrency(state.monthlyExpense),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.dashboard_monthly_income), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                        Text(
                            CurrencyUtils.formatCurrency(state.monthlyIncome),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                val netIncome = state.monthlyIncome - state.monthlyExpense
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (netIncome >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = if (netIncome >= 0) IncomeGreen else StatusLost,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (netIncome >= 0) stringResource(R.string.dashboard_monthly_balance, CurrencyUtils.formatCurrency(netIncome))
                               else stringResource(R.string.dashboard_monthly_over_budget, CurrencyUtils.formatCurrency(-netIncome)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                    )
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.TopEnd),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Inventory2, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(pluralStringResource(R.plurals.dashboard_items_count, state.activeAssetCount, state.activeAssetCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun QuickActionsCard(
    onNavigateToBill: () -> Unit,
    onNavigateToAsset: () -> Unit,
    onNavigateToLife: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickActionButton(Icons.Outlined.AddCircle, stringResource(R.string.dashboard_quick_bill), AccentOrange, onNavigateToBill)
            QuickActionButton(Icons.Outlined.Inventory2, stringResource(R.string.dashboard_quick_asset), MaterialTheme.colorScheme.primary, onNavigateToAsset)
            QuickActionButton(Icons.Outlined.Flag, stringResource(R.string.dashboard_quick_goal), InfoBlue, onNavigateToLife)
            QuickActionButton(Icons.Outlined.Celebration, stringResource(R.string.dashboard_quick_anniversary), ModuleLife, onNavigateToLife)
        }
    }
}

@Composable
private fun QuickActionButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(MaterialTheme.shapes.large)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BudgetAlertCard(state: DashboardState) {
    val budget = state.budget
    if (state.budgetReminderEnabled && budget != null && budget.totalBudget > 0 &&
        state.monthlyExpense > 0 && state.monthlyExpense > budget.totalBudget * 0.8
    ) {
        AlertCard(
            icon = Icons.Outlined.Warning,
            title = stringResource(R.string.dashboard_budget_alert),
            message = stringResource(R.string.dashboard_budget_alert_message, state.monthlyExpense),
            color = if (state.monthlyExpense > budget.totalBudget) ErrorLight else AccentOrange
        )
    }
}

@Composable
private fun GoalsCard(state: DashboardState, onNavigateToLife: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Flag, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(stringResource(R.string.dashboard_goal_progress), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.dashboard_goals_completed, state.completedGoalCount, state.goalCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box(modifier = Modifier.size(48.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onNavigateToLife() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.recentGoals.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                state.recentGoals.forEach { goal ->
                    val progress = if (goal.totalCount > 0) goal.currentCount.toFloat() / goal.totalCount else 0f
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(goal.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("${goal.currentCount}/${goal.totalCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        ProgressBar(progress = progress, color = MaterialTheme.colorScheme.primary, height = 6.dp)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.dashboard_no_goals), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onNavigateToLife))
            }
        }
    }
}

@Composable
private fun AnniversariesCard(state: DashboardState, onNavigateToLife: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp)
                            .clip(CircleShape)
                            .background(AccentOrange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Celebration, null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(stringResource(R.string.dashboard_card_anniversaries), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(                            stringResource(R.string.dashboard_anniversaries_count, state.anniversaryCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Box(modifier = Modifier.size(48.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onNavigateToLife() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.upcomingAnniversaries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                state.upcomingAnniversaries.forEach { anniversary ->
                    val daysUntil = anniversary.daysUntil
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(anniversary.typeIcon.tint.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = anniversary.typeIcon.imageVector,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = anniversary.typeIcon.tint
                                )
                            }
                            Column {
                                Text(anniversary.displayTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(DateUtils.formatDisplayDate(context, anniversary.solarDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        StatusChip(
                            text = when (anniversary.displayMode) {
                                "COUNT_DOWN" -> if (daysUntil == 0) stringResource(R.string.dashboard_today) else if (daysUntil > 0) stringResource(R.string.dashboard_days_until, daysUntil) else stringResource(R.string.dashboard_days_passed, -daysUntil)
                                else -> if (anniversary.daysSince == 0) stringResource(R.string.dashboard_today) else stringResource(R.string.dashboard_days_passed, anniversary.daysSince)
                            },
                            color = when {
                                daysUntil == 0 -> AccentOrange; daysUntil in 1..7 -> ModuleLife; daysUntil in 8..30 -> Amber
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.dashboard_no_anniversaries), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onNavigateToLife))
            }
        }
    }
}

@Composable
private fun AssetDistributionCard(state: DashboardState, onNavigateToAsset: () -> Unit) {
    if (state.assetDistribution.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.dashboard_asset_distribution), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onNavigateToAsset))
            Spacer(modifier = Modifier.height(12.dp))
            val context = androidx.compose.ui.platform.LocalContext.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssetDistributionChart(state.assetDistribution, Modifier.size(100.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val longItems = mutableListOf<Pair<CategoryCount, Int>>()
                    val shortItems = mutableListOf<Pair<CategoryCount, Int>>()
                    state.assetDistribution.forEachIndexed { index, item ->
                        val name = com.palmnote.ui.components.getCategoryName(item.category, context)
                        if (name.length > 5) {
                            longItems.add(item to index)
                        } else {
                            shortItems.add(item to index)
                        }
                    }
                    val rows = mutableListOf<Pair<List<Pair<CategoryCount, Int>>, Boolean>>()
                    val paired = shortItems.chunked(2)
                    val hasOdd = paired.lastOrNull()?.size == 1
                    paired.dropLast(if (hasOdd) 1 else 0).forEach { rows.add(it to false) }
                    longItems.forEach { rows.add(listOf(it) to true) }
                    if (hasOdd) rows.add(paired.last() to false)
                    rows.forEach { (rowItems, fullWidth) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            rowItems.forEach { (item, idx) ->
                                if (fullWidth) {
                                    LegendItem(item, idx, Modifier.fillMaxWidth(), maxLines = 2)
                                } else {
                                    LegendItem(item, idx, Modifier.weight(1f), maxLines = 1)
                                }
                            }
                            if (rowItems.size < 2) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(item: CategoryCount, colorIndex: Int, modifier: Modifier = Modifier, maxLines: Int = 1) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = modifier
            .background(ChartColors[colorIndex % ChartColors.size].copy(alpha = 0.35f), MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(ChartColors[colorIndex % ChartColors.size]))
        Text(com.palmnote.ui.components.getCategoryName(item.category, context), style = MaterialTheme.typography.bodySmall, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(pluralStringResource(R.plurals.dashboard_items_count, item.count, item.count), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TodayCard(state: DashboardState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val weekDay = remember { getWeekDay(context) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(DateUtils.formatDisplayYearDate(context, System.currentTimeMillis()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(weekDay, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.dashboard_recorded), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.dashboard_items_recorded, state.activeAssetCount + state.goalCount + state.anniversaryCount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun AlertCard(icon: ImageVector, title: String, message: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
                Text(message, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
fun AssetDistributionChart(distribution: List<CategoryCount>, modifier: Modifier = Modifier) {
    val total = distribution.sumOf { it.count }.toFloat()
    if (total == 0f) {
        Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Inventory2, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(32.dp))
        }
        return
    }
    Canvas(modifier = modifier) {
        val strokeWidth = 12.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val center = Offset(size.width / 2, size.height / 2)
        var startAngle = -90f
        distribution.forEachIndexed { index, item ->
            val sweepAngle = (item.count / total) * 360f
            drawArc(
                color = ChartColors[index % ChartColors.size],
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            startAngle += sweepAngle
        }
    }
}

@Composable
private fun CardManagementDialog(
    allConfigs: List<DashboardCardConfig>,
    onToggle: (CardType) -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_card_manage), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                allConfigs.forEach { config ->
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
                                CardType.NET_WORTH -> stringResource(R.string.dashboard_card_net_worth)
                                CardType.QUICK_ACTIONS -> stringResource(R.string.dashboard_card_quick_actions)
                                CardType.BUDGET_ALERT -> stringResource(R.string.dashboard_card_budget_alert)
                                CardType.GOALS -> stringResource(R.string.dashboard_card_goals)
                                CardType.ANNIVERSARIES -> stringResource(R.string.dashboard_card_anniversaries)
                                CardType.ASSET_DISTRIBUTION -> stringResource(R.string.dashboard_card_asset_distribution)
                                CardType.TODAY -> stringResource(R.string.dashboard_card_today)
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        XiaomiSwitch(
                            checked = config.visible,
                            onCheckedChange = { onToggle(config.type) },
                            checkedTrackColor = LocalSwitchColor.current
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } }
    )
}

private fun getGreeting(context: android.content.Context): String {
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

private fun getWeekDay(context: android.content.Context): String {
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
