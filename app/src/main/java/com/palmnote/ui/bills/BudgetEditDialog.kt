// 预算编辑弹窗独立成文件：BudgetScreen 主文件函数数顶着 detekt 的 TooManyFunctions 上限，
// 弹窗这组（标题 / 字段 / 提醒分组 / 滑杆）自成一块，放这里正好把它摘出去。

package com.palmnote.ui.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.palmnote.app.R
import com.palmnote.domain.model.BudgetPeriod
import com.palmnote.domain.model.BudgetScope
import com.palmnote.domain.model.Money
import com.palmnote.domain.model.toMoney
import com.palmnote.domain.model.toYuanString
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.CapsuleSwitch
import com.palmnote.ui.theme.AccentOrange
import com.palmnote.ui.theme.ErrorLight
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.delay

/** 编辑中的预算草稿（`BudgetDraft`）定义在 `BudgetScreen.kt`，弹窗与主页面共用。 */

/**
 * 弹窗标题：主标题是预算主体（分类名 / 账本名 / 「本周总预算」），
 * 副标题说明类型与周期（「分类预算 · 本周」）。总预算一行说得清，无副标题。
 *
 * 原来拼成一行「本周「餐饮」预算」，主体名埋在中间；重排后主体名是弹窗最大的字。
 * 范围是 BOOK 时必须写出账本名——早前这里直接复用「选择账本」当标题，
 * 编辑已有账本预算时会显示成一句像是还没选账本的提示。
 */
@Composable
private fun budgetDialogTitles(draft: BudgetDraft): Pair<String, String?> {
    val context = LocalContext.current
    val periodLabel = budgetPeriodLabel(context, draft.period)
    return when (draft.scope) {
        BudgetScope.TOTAL -> stringResource(R.string.budget_label_total, periodLabel) to null
        BudgetScope.CATEGORY -> budgetCategoryDisplayName(context, draft.category.orEmpty()) to
            stringResource(R.string.budget_dialog_sub_category, periodLabel)
        BudgetScope.BOOK -> (draft.bookName ?: stringResource(R.string.budget_label_book_missing)) to
            stringResource(R.string.budget_dialog_sub_book, periodLabel)
    }
}

/** 弹窗字段区的状态束：金额、错误位、阈值、开关。集中收着，避免字段函数十来个参数。 */
private class BudgetEditState(draft: BudgetDraft) {
    var amount: String by mutableStateOf(draft.existing?.totalBudget?.toYuanString() ?: "")
    var amountError: Boolean by mutableStateOf(false)
    var threshold: Float by mutableFloatStateOf((draft.existing?.reminderThreshold ?: 0.8).toFloat())
    var notify: Boolean by mutableStateOf(draft.existing?.reminderEnabled ?: true)
}

/**
 * 预算编辑弹窗（重排版）：
 * 标题区（主体名 + 类型周期）→ 额度输入（大字 + 已用状态）→ 提醒分组（开关 / 阈值 / 滑杆）→ 底部（删除 | 取消 保存）。
 *
 * 阈值是**每条预算自己的**（写进 `reminderThreshold`），不再是首页写死的 80%——
 * 预警卡、进度条颜色、通知都读同一个值。
 */
@Composable
internal fun BudgetEditDialog(
    draft: BudgetDraft,
    onDismiss: () -> Unit,
    onSave: (amount: Long, threshold: Double, notify: Boolean) -> Unit,
    onDelete: (() -> Unit)?
) {
    val state = remember(draft) { BudgetEditState(draft) }
    var confirmDelete by remember(draft) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    fun submit() {
        val value = Money.parse(state.amount)?.cents
        if (value != null && value > 0) {
            // 落库也取整到 1%：Float 的 0.81f 转 Double 是 0.8100000023841858，
            // 和界面显示的 81% 差一个尾数，虽然不影响判定，但存进去不好看。
            onSave(value, (state.threshold * 100).roundToInt() / 100.0, state.notify)
        } else {
            state.amountError = true
        }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(draft = draft) },
        text = {
            BudgetEditFields(
                draft = draft,
                state = state,
                focusRequester = focusRequester,
                onDone = { submit() }
            )
        },
        confirmButton = { SaveButton(onClick = { submit() }) },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        // 危险操作不与保存同行：推到最左，靠空白隔开。新建态没有这条。
        leadingButton = onDelete?.let { { DeleteLeadingButton(onClick = { confirmDelete = true }) } }
    )

    // 删除不可逆，误触代价太高：先过一道确认。
    if (confirmDelete) {
        BudgetDeleteConfirmDialog(
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete?.invoke()
            }
        )
    }

    // 新建态自动聚焦额度框，省一次点按。稍等一拍再请求：Dialog 的 window 还没挂稳时
    // requestFocus 会落空。
    LaunchedEffect(draft) {
        if (draft.existing == null) {
            delay(120)
            focusRequester.requestFocus()
        }
    }
}

/** 标题两行：主体名（大）+ 类型与周期（小）。 */
@Composable
private fun DialogTitle(draft: BudgetDraft) {
    val (main, sub) = budgetDialogTitles(draft)
    Column {
        Text(main, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (sub != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 保存：实心主按钮。保存是弹窗里唯一的正动作，文字型按钮压不住左下角的红字删除。 */
@Composable
private fun SaveButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color.White)
    ) {
        Text(stringResource(R.string.confirm), fontWeight = FontWeight.Bold)
    }
}

/** 左下角的删除入口（红色文字 + 图标）。真正执行前还有一道确认弹窗。 */
@Composable
private fun DeleteLeadingButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(
            Icons.Outlined.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(stringResource(R.string.budget_delete), color = MaterialTheme.colorScheme.error)
    }
}

/** 删除预算的二次确认。独立成函数，别把编辑弹窗撑过 detekt 的 LongMethod 阈值。 */
@Composable
private fun BudgetDeleteConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budget_delete_confirm_title)) },
        text = { Text(stringResource(R.string.budget_delete_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.budget_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/**
 * 预算弹窗的字段区：额度（标签 + 大字输入 + 已用状态）→ 提醒分组。
 *
 * 外套一层可滚 + 限高：小屏弹起键盘时，内容区滚得动，底部按钮不会被裁出屏幕。
 */
@Composable
private fun BudgetEditFields(
    draft: BudgetDraft,
    state: BudgetEditState,
    focusRequester: FocusRequester,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .heightIn(max = 380.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.budget_amount_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (draft.existing != null) {
                Spacer(modifier = Modifier.weight(1f))
                UsedStatusLine(draft = draft)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        AmountField(
            state = state,
            focusRequester = focusRequester,
            onDone = onDone
        )
        Spacer(modifier = Modifier.height(16.dp))
        ReminderGroup(state = state)
    }
}

/**
 * 编辑态额度标签右侧的「已用 ¥820.00 · 82%」：调额度时看得见现在的消耗，不必退出去查。
 *
 * 超支时整行转红——这行数字如果超支还染常规灰，等于把最要紧的状态说了个寂寞。
 */
@Composable
private fun UsedStatusLine(draft: BudgetDraft) {
    val context = LocalContext.current
    val limit = draft.existing?.totalBudget ?: 0L
    val over = draft.spent > limit
    val percent = if (limit > 0L) {
        ((draft.spent.toDouble() / limit) * 100).roundToInt()
    } else {
        0
    }
    Text(
        text = stringResource(
            R.string.budget_used_short,
            CurrencyUtils.formatCurrency(context, draft.spent.toMoney()),
            percent
        ),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (over) FontWeight.Bold else FontWeight.Normal,
        color = if (over) ErrorLight else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 额度输入：26sp 大字。金额是弹窗里最重的一块，字号跟着设计稿走。 */
@Composable
private fun AmountField(
    state: BudgetEditState,
    focusRequester: FocusRequester,
    onDone: () -> Unit
) {
    OutlinedTextField(
        value = state.amount,
        onValueChange = { state.amount = it; state.amountError = false },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        placeholder = {
            Text(
                "0.00",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline
            )
        },
        prefix = {
            Text(
                stringResource(R.string.currency_symbol),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        isError = state.amountError,
        supportingText = if (state.amountError) {
            { Text(stringResource(R.string.budget_invalid_amount)) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            // 键盘上给「完成」，输完直接收键盘或触发保存，不必伸手去点按钮
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        shape = MaterialTheme.shapes.medium,
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentOrange)
    )
}

/**
 * 提醒分组：开关、阈值换算、滑杆收进一个浅底容器，读起来是「一组设置」。
 *
 * 顺序有讲究：开关在上——阈值是「开关打开时才生效」的从属项，先说条件再说参数。
 */
@Composable
private fun ReminderGroup(state: BudgetEditState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.budget_notify_label),
                style = MaterialTheme.typography.bodyMedium
            )
            // 这页的强调色是橙：周期胶囊、保存按钮、开关保持同一支，不和主题青打架。
            CapsuleSwitch(
                checked = state.notify,
                onCheckedChange = { state.notify = it },
                checkedTrackColor = AccentOrange
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        BudgetThresholdRow(
            threshold = state.threshold,
            amountCents = Money.parse(state.amount)?.cents,
            onThresholdChange = { state.threshold = it },
            enabled = state.notify
        )
    }
}

/**
 * 阈值行 + 滑杆（1%~100%，无级、落值量化 1%）。
 *
 * 左侧「支出达到 81% 时」给百分比，右侧折算金额——纯百分比没感觉，1% 的下限尤其如此：
 * 用户拖到底不知道那是「花 10 块就提醒」。额度还没填时给「—」。
 * 通知关掉时整行降灰、滑杆禁用：留着能拖但拖了不生效，比灰掉更容易被当成 bug。
 */
@Composable
private fun BudgetThresholdRow(
    threshold: Float,
    amountCents: Long?,
    onThresholdChange: (Float) -> Unit,
    enabled: Boolean
) {
    val context = LocalContext.current
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.budget_reach_fmt, (threshold * 100).roundToInt()),
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor
        )
        Text(
            text = amountCents
                ?.takeIf { it > 0L }
                ?.let { CurrencyUtils.formatCurrency(context, (it * threshold).roundToLong().toMoney()) }
                ?: stringResource(R.string.budget_value_dash),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
    ThresholdSlider(
        threshold = threshold,
        onThresholdChange = onThresholdChange,
        enabled = enabled
    )
    if (!enabled) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.budget_threshold_off_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
}

/** 阈值滑杆：无级拖动、落值 1% 粒度，两端给 1% / 100% 端点。 */
@Composable
private fun ThresholdSlider(
    threshold: Float,
    onThresholdChange: (Float) -> Unit,
    enabled: Boolean
) {
    val trackColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline
    Slider(
        value = threshold,
        // 读屏默认念的是 0~1 的浮点（「0.81」），补一条人类可读的百分比
        modifier = Modifier.semantics {
            stateDescription = "${(threshold * 100).roundToInt()}%"
        },
        onValueChange = { raw ->
            // 无极滑动，落值量化到 1%：界面里的「81%」必须就是判定用的 81%，
            // 不量化会出现「显示 81% 实际 81.37%」这种对不上。
            onThresholdChange(quantizeThreshold(raw))
        },
        enabled = enabled,
        valueRange = 0.01f..1f,
        // 0 = 无级；原来 steps = 9 是 50%~100% 每 5% 一档
        steps = 0,
        colors = SliderDefaults.colors(
            thumbColor = AccentOrange,
            activeTrackColor = AccentOrange,
            // 分组容器底就是 surfaceVariant 的半透明，轨道若也用 surfaceVariant 会整条消失；
            // 用 outlineVariant 保持「比底深一档」的可见性
            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 端点标签是纯数字，中英文同形，不走资源
        Text(text = "1%", style = MaterialTheme.typography.labelSmall, color = trackColor)
        Text(text = "100%", style = MaterialTheme.typography.labelSmall, color = trackColor)
    }
}
