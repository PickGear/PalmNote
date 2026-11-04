package com.palmnote.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.palmnote.app.R
import com.palmnote.data.db.dao.CategoryCount
import com.palmnote.domain.model.BudgetPeriod
import com.palmnote.domain.model.toMoney
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*
import com.palmnote.ui.asset.getCategoryDisplayName
import com.palmnote.ui.asset.getCategoryIcon
import com.palmnote.ui.bills.budgetLabel

// 10 种卡片的统一分发入口：参数为纯数据 + 导航回调，按卡片类型各自取用，
// 收敛成状态对象只会把参数换个地方堆——Compose 惯例下保持显式签名并抑制告警
@Suppress("LongParameterList")
@Composable
internal fun DashboardCardContent(
    type: CardType,
    state: DashboardState,
    onNavigateToAsset: () -> Unit,
    onNavigateToBill: () -> Unit,
    onNavigateToLife: () -> Unit,
    onNavigateToVault: () -> Unit = {},
    onNavigateToAddBill: () -> Unit = {},
    onNavigateToAddAsset: () -> Unit = {},
    /** 预算预警卡的去向：带上是哪一期被预警。 */
    onNavigateToBudget: (BudgetPeriod) -> Unit = {},
    onHabitCheckIn: (Long) -> Unit = {},
    presetCategoryOverrides: Map<String, String>,
    categoryConfigs: List<com.palmnote.data.db.entity.CategoryConfig>,
    cardConfigs: List<DashboardCardConfig> = emptyList(),
    /** 演示模式开启中：净资产等含示例的数字需要标注（「含示例数据」）。 */
    demoModeOn: Boolean = false,
    /** 金额隐私：净资产卡遮住四组金额与涨跌方向。 */
    amountsHidden: Boolean = false,
    onToggleAmounts: () -> Unit = {}
) {
    val netWorthColor = cardConfigs.find { it.type == CardType.NET_WORTH }?.customColor?.toComposeColor() ?: ModuleHome
    when (type) {
        CardType.NET_WORTH -> NetWorthCard(
            state = state,
            cardColor = netWorthColor,
            onNavigateToBill = onNavigateToBill,
            demoModeOn = demoModeOn,
            amountsHidden = amountsHidden,
            onToggleAmounts = onToggleAmounts
        )
        CardType.QUICK_ACTIONS -> QuickActionsCard(onNavigateToAddBill, onNavigateToAddAsset, onNavigateToLife)
        CardType.BUDGET_ALERT -> BudgetAlertCard(state, onNavigateToBudget, amountsHidden)
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
                            text = if (state.vaultCount > 0) pluralStringResource(
                                R.plurals.vault_card_count, state.vaultCount, state.vaultCount
                            ) else stringResource(R.string.vault_card_empty_subtitle),
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
internal fun NetWorthCard(
    state: DashboardState,
    cardColor: Color = ModuleHome,
    onNavigateToBill: () -> Unit,
    demoModeOn: Boolean = false,
    amountsHidden: Boolean = false,
    onToggleAmounts: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, cardColor.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(cardColor, cardColor.copy(alpha = 0.85f))
                    )
                )
                .padding(16.dp)
        ) {
            Column {
                // 标题行：左「净资产」，右「账单 ›」＋ 眼睛。
                // 眼睛放这一行几乎不花高度——标题行 20dp 只是被 24dp 的按钮顶到 24dp；
                // 之前放在结余行（那行本来只有 16dp）会把整张卡撑高 16dp。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_net_worth),
                        style = MaterialTheme.typography.bodyMedium,
                        // 比 bodyMedium 的 400 重一档：0.8 透明度的小字在青底上偏飘，
                        // 上 Medium 就够，别上 Bold（会抢金额的视觉重心）
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f)
                    )
                    BillEntryLink(onClick = onNavigateToBill)
                    Spacer(modifier = Modifier.width(6.dp))
                    AmountPrivacyToggle(hidden = amountsHidden, onToggle = onToggleAmounts)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (amountsHidden) {
                        // 币种符号留着：藏的是数，不是「这是钱」
                        Text(
                            text = stringResource(R.string.money_symbol),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        AmountMaskBlock(width = 136.dp, height = 28.dp, corner = 8.dp)
                    } else {
                        Text(
                            text = CurrencyUtils.formatCompact(context, state.totalAssetValue.toMoney()),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    // 演示模式下资产/钱包含示例：必须标注，否则用户会把示例余额当成自己的
                    if (demoModeOn) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.dashboard_demo_included),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                                    androidx.compose.foundation.shape.RoundedCornerShape(50)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                // 总资产与「本月收支」的分组间距：16dp，仍比下方结余那段的 12dp 大，
                // 但不会在 178dp 的卡中间空出一整块（原 24dp 占卡高 13%，看着像漏了内容）
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(stringResource(R.string.dashboard_monthly_expense), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                        MoneyValue(
                            text = CurrencyUtils.formatCurrency(context, state.monthlyExpense.toMoney()),
                            hidden = amountsHidden,
                            maskWidth = 60.dp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.dashboard_monthly_income), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                        MoneyValue(
                            text = CurrencyUtils.formatCurrency(context, state.monthlyIncome.toMoney()),
                            hidden = amountsHidden,
                            maskWidth = 54.dp,
                            // 收入用浅薄荷绿跟支出（白）分开：IncomeGreen 压在这张青卡上只有 1.02:1
                            valueColor = IncomeGreenLight
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                val netIncome = state.monthlyIncome - state.monthlyExpense
                val balanceText = if (netIncome >= 0) {
                    stringResource(
                        R.string.dashboard_monthly_balance,
                        CurrencyUtils.formatCurrency(context, netIncome.toMoney())
                    )
                } else {
                    stringResource(
                        R.string.dashboard_monthly_over_budget,
                        CurrencyUtils.formatCurrency(context, (-netIncome).toMoney())
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (amountsHidden) {
                        // 涨跌方向本身也是财务信息：不给箭头，用中性块占住原位（与小组件同口径）
                        AmountMaskBlock(width = 14.dp, height = 14.dp, corner = 4.dp)
                    } else {
                        Icon(
                            if (netIncome >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            // 涨用彩底版浅绿（IncomeGreen 压在青卡上只有 1.02:1）；
                            // 跌保留原深红，用户明确不要那版浅粉
                            tint = if (netIncome >= 0) IncomeGreenLight else StatusLost,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    if (amountsHidden) {
                        Text(
                            text = stringResource(R.string.dashboard_monthly_balance_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        AmountMaskBlock(width = 72.dp, height = 12.dp, corner = 4.dp)
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        Text(
                            text = balanceText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // 「N 件」徽标按新稿挪到最后一行右端，右上角只留「账单 ›」与眼睛
                    ItemCountBadge(count = state.activeAssetCount)
                }
            }
        }
    }
}

/**
 * 遮罩块：隐藏金额时的占位灰块（2026-11-02 稿）。
 *
 * 宽度写死、不跟真实数字走——按真实位数画块等于把「这个月花了十万还是几百」也一起泄露了。
 */
@Composable
private fun AmountMaskBlock(width: Dp, height: Dp, corner: Dp) {
    Box(
        modifier = Modifier
            .size(width, height)
            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f), RoundedCornerShape(corner))
    )
}

/** 支出/收入数值：显示态走文本，隐藏态留币种符号 + 灰块（与主金额同一套遮法）。 */
@Composable
private fun MoneyValue(
    text: String,
    hidden: Boolean,
    maskWidth: Dp,
    valueColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    if (hidden) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.money_symbol),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            AmountMaskBlock(width = maskWidth, height = 14.dp, corner = 4.dp)
        }
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

/**
 * 标题行右侧的「账单 ›」chip。
 *
 * 不复用 [JumpCapsule]：那个的竖向 padding 是 8dp，落到标题行会把整行从 24dp 顶到 30dp。
 * 这里用 4dp，chip 高 22dp，与「N 件」徽标同一套（同 rounding / 同内边距 / 同透明度）。
 */
@Composable
private fun BillEntryLink(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.nav_bill),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(14.dp)
        )
    }
}

/** 物品件数徽标（新稿从右上角挪到卡片最后一行右端）。 */
@Composable
private fun ItemCountBadge(count: Int) {
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
            Text(
                pluralStringResource(R.plurals.dashboard_items_count, count, count),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

/**
 * 金额隐私小眼睛：点一下遮住净资产卡上的四组数字。
 *
 * 24dp 触控：它跟「账单 ›」并排待在标题行，撑到 48dp 会把标题行顶高；24dp 让这一行只长 4dp。
 */
@Composable
private fun AmountPrivacyToggle(hidden: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription = stringResource(
                if (hidden) R.string.dashboard_amount_show else R.string.dashboard_amount_hide
            ),
            // 不压 alpha：卡右端（渐变变浅处）85% 白只有 2.57:1，纯白才到 2.99:1，
            // 这是个可点控件，不能再往下让
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
internal fun QuickActionsCard(
    onNavigateToAddBill: () -> Unit,
    onNavigateToAddAsset: () -> Unit,
    onNavigateToLife: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 快捷卡直达创建动作：记账/物品一步进表单，目标/纪念日进生活模块
            QuickActionButton(Icons.Outlined.AddCircle, stringResource(R.string.dashboard_quick_bill), AccentOrange, onNavigateToAddBill)
            QuickActionButton(Icons.Outlined.Inventory2, stringResource(R.string.dashboard_quick_asset), ModuleItem, onNavigateToAddAsset)
            QuickActionButton(Icons.Outlined.Flag, stringResource(R.string.dashboard_quick_goal), StatusHeld, onNavigateToLife)
            QuickActionButton(Icons.Outlined.Celebration, stringResource(R.string.dashboard_quick_anniversary), ModuleLife, onNavigateToLife)
        }
    }
}

@Composable
private fun QuickActionButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
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
internal fun BudgetAlertCard(
    state: DashboardState,
    /**
     * 点卡片的去向：进预算页，并带上这条预警自己的周期。
     *
     * 早前这里接的是「去账单页」，既不显示预算规则、也看不到阈值与剩余；
     * 周/年预算的预警点进去还会落在月页签上，等于找不到那条预警。
     */
    onNavigateToBudget: (BudgetPeriod) -> Unit = {},
    amountsHidden: Boolean = false
) {
    val alert = state.budgetAlert
    if (state.budgetReminderEnabled && alert != null) {
        val label = budgetLabel(
            androidx.compose.ui.platform.LocalContext.current, alert, state.bookNames
        )
        val percent = alert.usedPercent
        AlertCard(
            icon = Icons.Outlined.Warning,
            title = stringResource(R.string.dashboard_budget_alert),
            // 这张卡把已用金额原样印在首页上——不跟着一起遮，净资产卡藏了也等于没藏
            message = when {
                amountsHidden -> stringResource(R.string.budget_alert_message_hidden, label, percent)
                alert.isOver -> stringResource(
                    R.string.budget_alert_over_message, label,
                    alert.spent / 100.0, alert.limit / 100.0
                )
                else -> stringResource(
                    R.string.budget_alert_message, label, percent,
                    alert.spent / 100.0, alert.limit / 100.0
                )
            },
            color = if (alert.isOver) ErrorLight else AccentOrange,
            onClick = { onNavigateToBudget(alert.budget.periodEnum) }
        )
    }
}

@Composable
@Suppress("LongMethod")
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
            // 不画卡片自己的标题行（图标 + 标题 + 条数 + 「生活 >」）：
            // 卡里显示的本来就是纪念日本身，再套一层同名标题是重复信息。
            val first = state.upcomingAnniversaries.firstOrNull()
            if (first != null) {
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
                                pluralStringResource(R.plurals.dashboard_days_until, daysUntil, daysUntil)
                            } else {
                                pluralStringResource(R.plurals.dashboard_days_passed, -daysUntil, -daysUntil)
                            }
                            else -> if (first.daysSince == 0) {
                                stringResource(R.string.dashboard_today)
                            } else {
                                pluralStringResource(R.plurals.dashboard_days_passed, first.daysSince, first.daysSince)
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
                Text(stringResource(R.string.dashboard_no_anniversaries), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onNavigateToLife))
            }
        }
    }
}

@Composable
internal fun AssetDistributionCard(
    state: DashboardState,
    onNavigateToAsset: () -> Unit,
    presetCategoryOverrides: Map<String, String>,
    categoryConfigs: List<com.palmnote.data.db.entity.CategoryConfig>
) {
    // 按数量降序：第一页永远是最重要的分类（环形图扇区顺序随之对齐）
    val items = remember(state.assetDistribution) {
        state.assetDistribution.sortedByDescending { it.count }
    }
    if (items.isEmpty()) return

    val context = androidx.compose.ui.platform.LocalContext.current
    val rows = remember(items, presetCategoryOverrides, categoryConfigs) {
        buildDistributionRows(items) { getCategoryDisplayName(it.category, context, presetCategoryOverrides) }
    }
    // 页 = 4 行；只有一页时行数自适应，多页时页高恒定
    val rowsPerPage = 4
    val pages = maxOf(1, (rows.size + rowsPerPage - 1) / rowsPerPage)
    val pagerState = rememberPagerState(pageCount = { pages })
    val gridRows = if (pages > 1) rowsPerPage else rows.size
    // 分页区高度必须跟着胶囊高与间距走，否则末行被裁
    val pagerHeight = DistributionChipHeight * gridRows + DistributionChipGap * (gridRows - 1)

    val cardSurface = MaterialTheme.colorScheme.surface
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cardSurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                stringResource(R.string.dashboard_asset_distribution),
                fontSize = TypeScale.headlineS,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onNavigateToAsset)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
                AssetDonutWithTotal(items, presetCategoryOverrides, categoryConfigs)
                Spacer(modifier = Modifier.width(DistributionDonutGap))
                DistributionPager(
                    rows = rows,
                    rowsPerPage = rowsPerPage,
                    pagerState = pagerState,
                    presetCategoryOverrides = presetCategoryOverrides,
                    categoryConfigs = categoryConfigs,
                    modifier = Modifier.weight(1f).height(pagerHeight)
                )
            }
            // 不放页点指示器（2026-11-02 定案）：卡面越干净越好，多页只靠右缘渐隐在滑动时提示。
            // 代价是静止状态没有「还有一页」的线索——要补就别再放常驻点，先考虑滑动时淡入。
        }
    }
}

/**
 * 把分类按名称长度折成行：名称 ≤4 字（半宽 chip 的可读上限）的两个配成一行，更长的独占整行。
 *
 * V4 稿的等宽两列只对两字名称成立——名称可用宽度 = 胶囊宽 − 内边距 18 − 小圆点 5
 * − 两处 5dp 间隔 −「N 件」计数：360dp 屏上两列各 88dp，扣完约 2 个字（412dp 屏约 3 个），
 * 所以自建的 3–4 字名即使配成一行也会省略。配对按数量降序贪心进行，
 * [nameOf] 由调用方注入以隔离「拿显示名」的本地化细节。
 */
private fun buildDistributionRows(
    items: List<CategoryCount>,
    nameOf: (CategoryCount) -> String
): List<List<CategoryCount>> {
    val rows = mutableListOf<List<CategoryCount>>()
    var i = 0
    while (i < items.size) {
        val a = items[i]
        val b = items.getOrNull(i + 1)
        if (b != null && nameOf(a).length <= 4 && nameOf(b).length <= 4) {
            rows.add(listOf(a, b)); i += 2
        } else {
            rows.add(listOf(a)); i += 1
        }
    }
    return rows
}

/** 边缘渐隐带宽度；≤ [DistributionEdgePad] 时静止内容不被遮罩咬到。 */
private val DistributionEdgeFade = 16.dp

/**
 * 页两侧留白，同时是 pager 外扩量——必须与物品分布卡片的横向内边距（16dp）相等：
 * pager 外扩后铺到卡片内缘，渐隐带落在卡片留白区上，页宽恢复为槽位原宽，
 * chip 长度与未分页的旧版完全一致。
 */
private val DistributionEdgePad = 16.dp

/**
 * 把布局向左右各外扩 [extra]：按加宽后的约束测量子级，绘制整体回移 extra，
 * 对外仍报告原宽度。物品分布卡用它让 pager 铺到卡片内缘、渐隐带不挤占内容。
 */
private fun Modifier.expandWidthBy(extra: Dp): Modifier = layout { measurable, constraints ->
    val e = extra.roundToPx()
    val w = constraints.maxWidth
    if (w == Constraints.Infinity) {
        val p = measurable.measure(constraints)
        layout(p.width, p.height) { p.placeRelative(0, 0) }
    } else {
        val p = measurable.measure(constraints.copy(minWidth = w + 2 * e, maxWidth = w + 2 * e))
        layout(w, p.height) { p.placeRelative(-e, 0) }
    }
}

/**
 * 标签分页器：HorizontalPager + 固定边缘渐隐遮罩。
 *
 * 渐隐绑在「位置」而非「页」上：标签全程不透明、正常滑动，只有进入容器左右
 * [DistributionEdgeFade] 渐隐带的内容逐渐溶解——观感是标签层从卡片边缘下方滑过。
 * 几何：容器经 [expandWidthBy] 外扩 [DistributionEdgePad] 铺到卡片内缘，
 * 渐隐带正好覆盖卡片留白区，页宽不变；contentPadding 与页间距同为
 * [DistributionEdgePad]，静止时内容起于渐隐带末端、完全落在不透明区。
 * 两个配套约束（缺一不可）：
 * 1. Pager 不裁切内容（1.8.3 源码无 clip），容器必须显式 clipToBounds，否则
 *    滑出的 chip 会画到渐隐带之外、被卡片右缘硬切；
 * 2. 遮罩用 Offscreen 图层 + DstIn，只对本层（pager 内容）做 alpha 渐变——
 *    直接画会连下层卡片底色一起按 DstIn 擦出透明洞。
 */
@Composable
private fun DistributionPager(
    rows: List<List<CategoryCount>>,
    rowsPerPage: Int,
    pagerState: PagerState,
    presetCategoryOverrides: Map<String, String>,
    categoryConfigs: List<com.palmnote.data.db.entity.CategoryConfig>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .expandWidthBy(DistributionEdgePad)
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val ramp = DistributionEdgeFade.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        (ramp / size.width) to Color.Black,
                        ((size.width - ramp) / size.width) to Color.Black,
                        1f to Color.Transparent
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = DistributionEdgePad),
            pageSpacing = DistributionEdgePad,
            //Pager 默认把页内容垂直居中，末页不满 4 行会悬在中间，必须显式靠上
            verticalAlignment = Alignment.Top
        ) { page ->
            Column(verticalArrangement = Arrangement.spacedBy(DistributionChipGap)) {
                rows.drop(page * rowsPerPage).take(rowsPerPage).forEach { rowItems ->
                    // 行距与列距同为 8dp（原左右 10 / 上下 6）。左右让出的 2dp 不清零，
                    // 由 weight(1f) 均分给两枚胶囊各 +1dp（87→88），环图维持 Ø102 不参与，
                    // 所以外层没有需要额外安置的余量。
                    Row(horizontalArrangement = Arrangement.spacedBy(DistributionChipGap)) {
                        rowItems.forEach { item ->
                            DistributionChip(item, Modifier.weight(1f), presetCategoryOverrides, categoryConfigs)
                        }
                    }
                }
            }
        }
    }
}

/** 环形图 + 中心总数：现状的 Chart 只画环、中心是空的，总数与「物品总数」说明是本次新增。 */
@Composable
private fun AssetDonutWithTotal(
    items: List<CategoryCount>,
    presetCategoryOverrides: Map<String, String>,
    categoryConfigs: List<com.palmnote.data.db.entity.CategoryConfig>
) {
    val total = items.sumOf { it.count }
    // Ø102：与右侧间隙一起决定每页胶囊能多宽——加大环径就是压窄胶囊
    Box(modifier = Modifier.size(DistributionDonutSize)) {
        AssetDistributionChart(items, Modifier.fillMaxSize(), presetCategoryOverrides, categoryConfigs)
        Column(
            // 中心块整体下移 2dp：数字无下延部，几何居中会显得偏高（光学修正）
            modifier = Modifier.align(Alignment.Center).offset(y = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(total.toString(), fontSize = TypeScale.displayS, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.dashboard_asset_total),
                fontSize = TypeScale.labelS,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 单页图例：等宽两列、名称单行省略；超长分类不再独占一行（旧「长短分行」算法随分页作废）。 */
@Composable
private fun DistributionChip(
    item: CategoryCount,
    modifier: Modifier = Modifier,
    presetCategoryOverrides: Map<String, String>,
    categoryConfigs: List<com.palmnote.data.db.entity.CategoryConfig>
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val color = remember(presetCategoryOverrides, categoryConfigs, item.category) {
        val customItems = categoryConfigs.filter { it.type == "ASSET" && it.isEnabled }
            .map { com.palmnote.ui.components.CategoryItem(it.name, it.icon.imageVector, it.color.toComposeColor()) }
        getCategoryIcon(item.category, customItems).color
    }
    Row(
        modifier = modifier
            .height(DistributionChipHeight)
            .background(color.copy(alpha = 0.35f), RoundedCornerShape(percent = 50))
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(color))
        Text(
            getCategoryDisplayName(item.category, context, presetCategoryOverrides),
            fontSize = TypeScale.bodyS,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            pluralStringResource(R.plurals.dashboard_items_count, item.count, item.count),
            fontSize = TypeScale.labelM,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 环图直径与其到分页列的间隙：间隙同时是页宽口径，动它会连带改变胶囊宽度。 */
private val DistributionDonutSize = 102.dp
private val DistributionDonutGap = 10.dp

/**
 * 胶囊网格：行高 25dp，上下左右间距同值 8dp（2026-11-02 定案，原左右 10 / 上下 6）。
 *
 * 间距改成同值后左右让出的 2dp 由 `weight(1f)` 均分给两枚胶囊（87→88），环图不参与；
 * 纵向从 6 提到 8 使四行内容区 118→124dp。三个数一起动才是同一套口径，别只改间距。
 */
private val DistributionChipHeight = 25.dp
private val DistributionChipGap = 8.dp

@Composable
private fun JumpCapsule(label: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = 0.08f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            // 纵向触达从约 22dp 提到 32dp：6 处卡片的主导航入口，误触重灾区；
            // 不上 48dp 是为了不让卡片头部被撑高
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = color, modifier = Modifier.size(14.dp))
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
    // 打卡模板天生是「每天」一次，原来的周/月频率标签在生活模板上没有对应字段，
    // 统一显示「每天」——保留这一行也让行高与原来一致
    val periodLabel = stringResource(R.string.dashboard_habit_period_daily)
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
                    .background(row.tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(com.palmnote.ui.life.iconFor(row.iconKey), null, tint = row.tint, modifier = Modifier.size(18.dp))
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
                    .clickable { onCheckIn(row.templateId) }
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
                       else pluralStringResource(R.plurals.dashboard_days_until, sub.daysLeft, sub.daysLeft),
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
    // 不包 remember：跨午夜后靠数据变化触发的重组重算星期，跟 VM 的 dayTick 对齐
    val weekDay = getWeekDay(context)
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
                val recorded = state.activeAssetCount + state.anniversaryCount
                Text(
                    pluralStringResource(R.plurals.dashboard_items_recorded, recorded, recorded),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = StatusHeld
                )
                Spacer(modifier = Modifier.height(4.dp))
                JumpCapsule(
                    label = stringResource(R.string.nav_life),
                    color = ModuleLife,
                    onClick = onNavigateToLife
                )
            }
        }
    }
}

@Composable
internal fun AlertCard(icon: ImageVector, title: String, message: String, color: Color, onClick: (() -> Unit)? = null) {
    // 卡面必须是不透明实底：首页背景是带纹理图案的底，给 surface 上 alpha 会得到一块
    // 「看不出是卡」的淡色区域，内容像浮在页面上。改成把强调色按比例混进 surface —— 混色结果
    // alpha 恒为 1，既是实打实的卡面，又保留告警色的识别度。
    // 描边与首页其他卡一致用 outlineVariant（曾用告警色 25%，太抢眼）；1dp 只负责收边。
    val container = lerp(MaterialTheme.colorScheme.surface, color, 0.12f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
    // 入场扫入：进首页（重新组合）时 0→1 跑一次约 800ms；数据变化不重播
    val sweepProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        sweepProgress.animateTo(1f, tween(durationMillis = 800, easing = FastOutSlowInEasing))
    }
    Canvas(modifier = modifier) {
        val strokeWidth = 12.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val center = Offset(size.width / 2, size.height / 2)
        var startAngle = -90f
        distribution.forEachIndexed { index, item ->
            val sweepAngle = (item.count / total) * 360f * sweepProgress.value
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
@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
internal fun CardManagementDialog(
    allConfigs: List<DashboardCardConfig>,
    onToggle: (CardType) -> Unit,
    onColorChange: (CardType, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val presetColors = listOf(
        "#0891B2" to ModuleHome,
        "#2A6BAB" to ModuleItem,
        "#34A853" to StatusHeld,
        "#FF8C42" to AccentOrange,
        "#C2185B" to ModuleLife,
        "#FFCA28" to Color(0xFFFFCA28),
        "#EF5350" to Color(0xFFEF5350),
        "#29B6F6" to Color(0xFF29B6F6)
    )

    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_card_manage), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 520.dp)
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
                                CardType.ANNIVERSARIES -> stringResource(R.string.dashboard_card_anniversaries)
                                CardType.ASSET_DISTRIBUTION -> stringResource(R.string.dashboard_card_asset_distribution)
                                CardType.TODAY -> stringResource(R.string.dashboard_card_today)
                                CardType.VAULT -> stringResource(R.string.dashboard_card_vault)
                                CardType.HABIT_TODAY -> stringResource(R.string.dashboard_card_habit_today)
                                CardType.SUBSCRIPTION -> stringResource(R.string.dashboard_card_subscription)
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (config.type == CardType.NET_WORTH) {
                                val cardColor = config.customColor?.toComposeColor() ?: ModuleHome
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(cardColor)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                                        .clickable { showColorPicker = !showColorPicker }
                                )
                            }
                            CapsuleSwitch(
                                checked = config.visible,
                                onCheckedChange = { onToggle(config.type) },
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    if (showColorPicker && config.type == CardType.NET_WORTH) {
                        InlineColorPicker(
                            presetColors = presetColors,
                            selectedColor = config.customColor,
                            onColorSelected = { color ->
                                onColorChange(CardType.NET_WORTH, color)
                            },
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } }
    )
}

