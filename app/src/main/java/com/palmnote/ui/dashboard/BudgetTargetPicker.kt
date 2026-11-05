package com.palmnote.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.palmnote.app.R
import com.palmnote.domain.model.BudgetPeriod
import com.palmnote.domain.model.BudgetProgress
import com.palmnote.domain.model.BudgetScope
import com.palmnote.ui.bills.budgetCategoryDisplayName
import com.palmnote.ui.theme.AccentOrange
import com.palmnote.ui.theme.ErrorLight
import com.palmnote.ui.theme.StatusActive

/**
 * 卡片管理里给预算卡选题材：先选周期，再选该周期下的具体预算。
 *
 * 三个设计决定：
 * 1. 周期用分段控件（整块三选一），比三个独立胶囊更像单选；
 * 2. 列表里不再重复周期前缀（上面的分段已经说了），只留「总预算 / 「餐饮」预算 / 「旅行账本」预算」；
 * 3. 「自动」是**显式选项**而不是藏在注释里——没选具体预算时卡片就是按它挑的，
 *    右端顺带标出它会挑到哪条、用掉多少，用户能直接判断要不要换。
 */
@Composable
internal fun BudgetTargetPicker(
    config: DashboardCardConfig,
    choices: List<BudgetProgress>,
    bookNames: Map<Long, String>,
    /** 卡面当前展示的那条：没显式选过周期时，弹窗默认落在它的周期上，避免「弹窗显示本月、卡面是本周」。 */
    currentBudget: BudgetProgress?,
    onSelect: (String?, String?, String?, Long?) -> Unit
) {
    val period = config.budgetPeriod ?: currentBudget?.budget?.period ?: BudgetPeriod.MONTH.name
    val inPeriod = choices.filter { it.budget.period == period }
    val auto = inPeriod.firstOrNull { it.severity > 0 } ?: inPeriod.maxByOrNull { it.ratio }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = stringResource(R.string.dashboard_budget_pick),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        // 换周期回到自动口径：原选的分类/账本在该周期多半没有对应预算
        BudgetPeriodSegments(selected = period, onSelect = { onSelect(it, null, null, null) })
        Spacer(Modifier.height(4.dp))
        BudgetTargetRow(
            label = stringResource(R.string.budget_target_auto),
            usage = auto?.let { usageText(it) },
            usageColor = auto?.let { usageColor(it) },
            active = config.budgetScope == null,
            onClick = { onSelect(period, null, null, null) }
        )
        if (inPeriod.isEmpty()) {
            Text(
                text = stringResource(R.string.dashboard_budget_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 2.dp)
            )
        } else {
            inPeriod.forEach { p ->
                BudgetTargetRow(
                    label = compactBudgetLabel(p, bookNames),
                    usage = usageText(p),
                    usageColor = usageColor(p),
                    active = matchesTarget(p, config),
                    onClick = {
                        onSelect(p.budget.period, p.budget.scope, p.budget.category, p.budget.bookId)
                    }
                )
            }
        }
    }
}

/** 这条预算是否就是卡片配置里显式选中的那条（BOOK 比 id、CATEGORY 比分类名）。 */
private fun matchesTarget(progress: BudgetProgress, config: DashboardCardConfig): Boolean {
    val scope = config.budgetScope ?: return false
    return progress.budget.scope == scope &&
        (scope != BudgetScope.CATEGORY.name || progress.budget.category == config.budgetCategory) &&
        (scope != BudgetScope.BOOK.name || progress.budget.bookId == config.budgetBookId)
}

/** 右端的用量：超支给「超支」，其余给百分比（颜色同卡片的绿/橙/红）。 */
@Composable
private fun usageText(progress: BudgetProgress): String =
    if (progress.isOver) stringResource(R.string.budget_target_over) else "${progress.usedPercent}%"

@Composable
private fun usageColor(progress: BudgetProgress): Color = when {
    progress.isOver -> ErrorLight
    progress.reachedThreshold -> AccentOrange
    else -> StatusActive
}

/** 列表里的短名：周期靠分段表达，这里只留范围（总预算 / 「餐饮」预算 / 「账本」预算）。 */
@Composable
private fun compactBudgetLabel(progress: BudgetProgress, bookNames: Map<Long, String>): String {
    val context = LocalContext.current
    return when (progress.budget.scopeEnum) {
        BudgetScope.TOTAL -> stringResource(R.string.budget_scope_total)
        BudgetScope.CATEGORY -> stringResource(
            R.string.budget_scope_category,
            budgetCategoryDisplayName(context, progress.budget.category.orEmpty())
        )
        BudgetScope.BOOK -> stringResource(
            R.string.budget_scope_book,
            bookNames[progress.budget.bookId] ?: stringResource(R.string.budget_label_book_missing)
        )
    }
}

@Composable
private fun BudgetPeriodSegments(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(2.dp)
    ) {
        listOf(
            BudgetPeriod.WEEK to R.string.budget_period_week,
            BudgetPeriod.MONTH to R.string.budget_period_month,
            BudgetPeriod.YEAR to R.string.budget_period_year
        ).forEach { (period, labelRes) ->
            val active = period.name == selected
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                    // 与预算页分段控件同口径：选中态用底色表达，关掉水波纹
                    .clickable(
                        interactionSource = remember(period) { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(period.name) }
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun BudgetTargetRow(
    label: String,
    usage: String?,
    usageColor: Color?,
    active: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RadioButton(selected = active, onClick = null, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f)
        )
        if (usage != null && usageColor != null) {
            Text(
                text = usage,
                style = MaterialTheme.typography.labelSmall,
                color = usageColor,
                maxLines = 1
            )
        }
    }
}
