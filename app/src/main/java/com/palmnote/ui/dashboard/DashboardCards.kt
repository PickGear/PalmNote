package com.palmnote.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.palmnote.app.R
import com.palmnote.data.db.dao.CategoryCount
import com.palmnote.domain.model.toMoney
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*
import com.palmnote.ui.asset.getCategoryDisplayName
import com.palmnote.ui.asset.getCategoryIcon

@Composable
internal fun DashboardCardContent(
    type: CardType,
    state: DashboardState,
    onNavigateToAsset: () -> Unit,
    onNavigateToBill: () -> Unit,
    onNavigateToLife: () -> Unit,
    onNavigateToVault: () -> Unit = {},
    onHabitCheckIn: (Long) -> Unit = {},
    presetCategoryOverrides: Map<String, String>,
    categoryConfigs: List<com.palmnote.data.db.entity.CategoryConfig>
) {
    when (type) {
        CardType.NET_WORTH -> NetWorthCard(state, onNavigateToBill)
        CardType.QUICK_ACTIONS -> QuickActionsCard(onNavigateToBill, onNavigateToAsset, onNavigateToLife)
        CardType.BUDGET_ALERT -> BudgetAlertCard(state, onNavigateToBill)
        CardType.GOALS -> GoalsCard(state, onNavigateToLife)
        CardType.ANNIVERSARIES -> AnniversariesCard(state, onNavigateToLife)
        CardType.ASSET_DISTRIBUTION -> AssetDistributionCard(state, onNavigateToAsset, presetCategoryOverrides, categoryConfigs)
        CardType.TODAY -> TodayCard(state, onNavigateToLife)
        CardType.VAULT -> VaultCard(state, onNavigateToVault)
        CardType.HABIT_TODAY -> HabitTodayCard(state, onNavigateToLife, onHabitCheckIn)
        CardType.SUBSCRIPTION -> SubscriptionCard(state, onNavigateToLife)
    }
}

@Composable
internal fun VaultCard(state: DashboardState, onNavigateToVault: () -> Unit) {
    // 与 Goals/Anniversaries 卡片保持同一视觉与交互规范：
    // surface 底 + outlineVariant 边框 + 圆形图标 + 右侧箭头导航（而非整卡 clickable，
    // 避免与 Dashboard 的长按拖拽换位手势冲突）
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
                            .background(vaultTint().copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Lock, null, tint = vaultTint(), modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(stringResource(R.string.dashboard_card_vault), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (state.vaultCount > 0) stringResource(R.string.vault_card_count, state.vaultCount) else stringResource(R.string.vault_card_empty_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                JumpCapsule(
                    label = stringResource(R.string.dashboard_card_vault),
                    color = com.palmnote.ui.theme.vaultTint(),
                    onClick = onNavigateToVault
                )
            }
            // 隐私保护：不展示条目标题（明文敏感），仅用计数 + 引导
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (state.vaultCount > 0) stringResource(R.string.vault_card_privacy_hint) else stringResource(R.string.vault_card_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
@Composable
@Suppress("LongMethod")
internal fun NetWorthCard(state: DashboardState, onNavigateToBill: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
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
                    text = CurrencyUtils.formatCompact(context, state.totalAssetValue.toMoney()),
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
                            CurrencyUtils.formatCurrency(context, state.monthlyExpense.toMoney()),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.dashboard_monthly_income), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                        Text(
                            CurrencyUtils.formatCurrency(context, state.monthlyIncome.toMoney()),
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
                        text = if (netIncome >= 0) stringResource(R.string.dashboard_monthly_balance, CurrencyUtils.formatCurrency(context, netIncome.toMoney()))
                               else stringResource(R.string.dashboard_monthly_over_budget, CurrencyUtils.formatCurrency(context, (-netIncome).toMoney())),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                    )
                }
            }
            Column(
                modifier = Modifier.align(Alignment.TopEnd),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
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
                JumpCapsule(
                    label = stringResource(R.string.nav_bill),
                    color = MaterialTheme.colorScheme.onPrimary,
                    onClick = onNavigateToBill
                )
            }
        }
    }
}

@Composable
internal fun QuickActionsCard(
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
internal fun BudgetAlertCard(state: DashboardState, onNavigateToBill: () -> Unit) {
    val budget = state.budget
    if (state.budgetReminderEnabled && budget != null && budget.totalBudget > 0 &&
        state.monthlyExpense > 0 && state.monthlyExpense > budget.totalBudget * 0.8
    ) {
        AlertCard(
            icon = Icons.Outlined.Warning,
            title = stringResource(R.string.dashboard_budget_alert),
            message = stringResource(R.string.dashboard_budget_alert_message, state.monthlyExpense / 100.0),
            color = if (state.monthlyExpense > budget.totalBudget) ErrorLight else AccentOrange,
            onClick = onNavigateToBill
        )
    }
}

@Composable
@Suppress("LongMethod")
internal fun GoalsCard(state: DashboardState, onNavigateToLife: () -> Unit) {
    if (state.goalCount <= 0) return
    val progress = if (state.goalCount > 0) state.completedGoalCount.toFloat() / state.goalCount else 0f
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
                JumpCapsule(
                    label = stringResource(R.string.nav_life),
                    color = com.palmnote.ui.theme.ModuleLife,
                    onClick = onNavigateToLife
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(76.dp)) {
                    GoalProgressRing(progress = progress, modifier = Modifier.fillMaxSize())
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = stringResource(R.string.dashboard_goals_view_all),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp).clickable(onClick = onNavigateToLife)
                )
            }
        }
    }
}

@Composable
internal fun AnniversariesCard(state: DashboardState, onNavigateToLife: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    if (state.anniversaryCount <= 0) return
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
                JumpCapsule(
                    label = stringResource(R.string.nav_life),
                    color = AccentOrange,
                    onClick = onNavigateToLife
                )
            }
            val first = state.upcomingAnniversaries.firstOrNull()
            if (first != null) {
                Spacer(modifier = Modifier.height(12.dp))
                val daysUntil = first.daysUntil
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
                                .background(first.typeIcon.tint.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = first.typeIcon.imageVector,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = first.typeIcon.tint
                            )
                        }
                        Column {
                            Text(
                                first.displayTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                DateUtils.formatDisplayDate(context, first.solarDate),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    StatusChip(
                        text = when (first.displayMode) {
                            "COUNT_DOWN" -> if (daysUntil == 0) {
                                stringResource(R.string.dashboard_today)
                            } else if (daysUntil > 0) {
                                stringResource(R.string.dashboard_days_until, daysUntil)
                            } else {
                                stringResource(R.string.dashboard_days_passed, -daysUntil)
                            }
                            else -> if (first.daysSince == 0) {
                                stringResource(R.string.dashboard_today)
                            } else {
                                stringResource(R.string.dashboard_days_passed, first.daysSince)
                            }
                        },
                        color = when {
                            daysUntil == 0 -> AccentOrange; daysUntil in 1..7 -> ModuleLife; daysUntil in 8..30 -> Amber
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                if (state.anniversaryCount > 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.dashboard_anniversaries_more, state.anniversaryCount - 1),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable(onClick = onNavigateToLife)
                    )
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
@Suppress("LongMethod")
internal fun AssetDistributionCard(
    state: DashboardState,
    onNavigateToAsset: () -> Unit,
    presetCategoryOverrides: Map<String, String>,
    categoryConfigs: List<com.palmnote.data.db.entity.CategoryConfig>
) {
    if (state.assetDistribution.isEmpty()) return
    val distPresetOverrides = presetCategoryOverrides

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
                verticalAlignment = Alignment.Top
            ) {
                AssetDistributionChart(state.assetDistribution, Modifier.size(100.dp), distPresetOverrides, categoryConfigs)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val longItems = mutableListOf<Pair<CategoryCount, Int>>()
                    val shortItems = mutableListOf<Pair<CategoryCount, Int>>()
                    state.assetDistribution.forEachIndexed { index, item ->
                        val name = getCategoryDisplayName(item.category, context, distPresetOverrides)
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
                            rowItems.forEach { (item, _) ->
                                if (fullWidth) {
                                    LegendItem(
                                        item = item,
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 2,
                                        presetCategoryOverrides = distPresetOverrides,
                                        categoryConfigs = categoryConfigs
                                    )
                                } else {
                                    LegendItem(
                                        item = item,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        presetCategoryOverrides = distPresetOverrides,
                                        categoryConfigs = categoryConfigs
                                    )
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
private fun LegendItem(
    item: CategoryCount,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    presetCategoryOverrides: Map<String, String>,
    categoryConfigs: List<com.palmnote.data.db.entity.CategoryConfig>
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dashPresetVer = presetCategoryOverrides
    val dashCustomCfg = categoryConfigs
    val dashCustomItems = remember(dashCustomCfg, dashPresetVer) {
        dashCustomCfg.filter { it.type == "ASSET" && it.isEnabled }
            .map { com.palmnote.ui.components.CategoryItem(it.name, it.icon.imageVector, it.color.toComposeColor()) }
    }
    val color = remember(dashCustomItems, dashPresetVer, item.category) {
        getCategoryIcon(item.category, dashCustomItems).color
    }
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.35f), MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(getCategoryDisplayName(item.category, context, dashPresetVer), style = MaterialTheme.typography.bodySmall, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(pluralStringResource(R.plurals.dashboard_items_count, item.count, item.count), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun JumpCapsule(label: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, color.copy(alpha = 0.5f), MaterialTheme.shapes.small)
            .background(color.copy(alpha = 0.08f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = color, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun GoalProgressRing(progress: Float, modifier: Modifier = Modifier, strokeWidth: androidx.compose.ui.unit.Dp = 8.dp) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val strokePx = strokeWidth.toPx()
        val radius = (size.minDimension - strokePx) / 2
        val center = Offset(size.width / 2, size.height / 2)
        val arcSize = Size(radius * 2, radius * 2)
        val topLeft = Offset(center.x - radius, center.y - radius)
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
        val sweep = progress.coerceIn(0f, 1f) * 360f
        if (sweep > 0f) {
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
internal fun HabitTodayCard(
    state: DashboardState,
    onNavigateToLife: () -> Unit,
    onHabitCheckIn: (Long) -> Unit
) {
    if (state.habitTotal == 0) return
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
                            .background(com.palmnote.ui.theme.ModuleLife.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = com.palmnote.ui.theme.ModuleLife, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            stringResource(R.string.dashboard_habit_today_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.dashboard_habit_today_progress, state.habitChecked, state.habitTotal),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                JumpCapsule(
                    label = stringResource(R.string.dashboard_habit_go_all),
                    color = com.palmnote.ui.theme.ModuleLife,
                    onClick = onNavigateToLife
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            state.habitRows.take(4).forEach { row ->
                HabitCheckRow(row = row, onCheckIn = onHabitCheckIn)
            }
        }
    }
}

@Composable
private fun HabitCheckRow(row: HabitTodayRow, onCheckIn: (Long) -> Unit) {
    val periodLabel = when (row.frequency) {
        "WEEKLY" -> stringResource(R.string.dashboard_habit_period_weekly)
        "MONTHLY" -> stringResource(R.string.dashboard_habit_period_monthly)
        else -> stringResource(R.string.dashboard_habit_period_daily)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(row.icon.tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(row.icon.imageVector, null, tint = row.icon.tint, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.widthIn(max = 180.dp)) {
                Text(row.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(periodLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (row.isCheckedToday) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.dashboard_habit_checked),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(com.palmnote.ui.theme.ModuleLife)
                    .clickable { onCheckIn(row.goalId) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    stringResource(R.string.dashboard_habit_check),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
internal fun SubscriptionCard(state: DashboardState, onNavigateToLife: () -> Unit) {
    val subs = state.upcomingSubscriptions
    if (subs.isEmpty()) return
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
                Column {
                    Text(
                        stringResource(R.string.dashboard_card_subscription),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.dashboard_subscription_due_count, subs.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            subs.forEach { sub ->
                SubscriptionRow(sub = sub, onNavigateToLife = onNavigateToLife)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.dashboard_subscription_hint, subs.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SubscriptionRow(sub: com.palmnote.domain.model.SubscriptionDueItem, onNavigateToLife: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(com.palmnote.ui.theme.ModuleLife.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Refresh, null, tint = com.palmnote.ui.theme.ModuleLife, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.widthIn(max = 160.dp)) {
                Text(sub.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.dashboard_subscription_price, sub.priceText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = if (sub.daysLeft <= 0) stringResource(R.string.dashboard_today)
                       else stringResource(R.string.dashboard_days_until, sub.daysLeft),
                style = MaterialTheme.typography.labelSmall,
                color = if (sub.daysLeft <= 0) com.palmnote.ui.theme.AccentOrange else com.palmnote.ui.theme.ModuleLife
            )
            JumpCapsule(
                label = stringResource(R.string.dashboard_card_subscription),
                color = com.palmnote.ui.theme.ModuleLife,
                onClick = onNavigateToLife
            )
        }
    }
}

@Composable
internal fun TodayCard(state: DashboardState, onNavigateToLife: () -> Unit) {
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
                Text(
                    DateUtils.formatDisplayFullDate(context, System.currentTimeMillis()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(weekDay, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.dashboard_recorded), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.dashboard_items_recorded, state.activeAssetCount + state.goalCount + state.anniversaryCount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                JumpCapsule(
                    label = stringResource(R.string.nav_life),
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToLife
                )
            }
        }
    }
}

@Composable
internal fun AlertCard(icon: ImageVector, title: String, message: String, color: Color, onClick: (() -> Unit)? = null) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
                Text(message, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.8f))
            }
            if (onClick != null) {
                JumpCapsule(label = stringResource(R.string.nav_bill), color = color, onClick = onClick)
            }
        }
    }
}

@Composable
fun AssetDistributionChart(
    distribution: List<CategoryCount>,
    modifier: Modifier = Modifier,
    presetCategoryOverrides: Map<String, String>,
    categoryConfigs: List<com.palmnote.data.db.entity.CategoryConfig>
) {
    val total = distribution.sumOf { it.count }.toFloat()
    if (total == 0f) {
        Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Inventory2, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(32.dp))
        }
        return
    }
    val chartPresetVer = presetCategoryOverrides
    val chartCustomCfg = categoryConfigs
    val chartCustomItems = remember(chartCustomCfg, chartPresetVer) {
        chartCustomCfg.filter { it.type == "ASSET" && it.isEnabled }
            .map { com.palmnote.ui.components.CategoryItem(it.name, it.icon.imageVector, it.color.toComposeColor()) }
    }
    val chartColors = remember(chartPresetVer, chartCustomItems, distribution) {
        distribution.map { item ->
            getCategoryIcon(item.category, chartCustomItems).color
        }
    }
    Canvas(modifier = modifier) {
        val strokeWidth = 12.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val center = Offset(size.width / 2, size.height / 2)
        var startAngle = -90f
        distribution.forEachIndexed { index, item ->
            val sweepAngle = (item.count / total) * 360f
            val catColor = chartColors.getOrElse(index) { com.palmnote.ui.theme.Gray400 }
            drawArc(
                color = catColor,
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
internal fun CardManagementDialog(
    allConfigs: List<DashboardCardConfig>,
    onToggle: (CardType) -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_card_manage), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 420.dp)
            ) {
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
                                CardType.VAULT -> stringResource(R.string.dashboard_card_vault)
                                CardType.HABIT_TODAY -> stringResource(R.string.dashboard_card_habit_today)
                                CardType.SUBSCRIPTION -> stringResource(R.string.dashboard_card_subscription)
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        CapsuleSwitch(
                            checked = config.visible,
                            onCheckedChange = { onToggle(config.type) },
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } }
    )
}
