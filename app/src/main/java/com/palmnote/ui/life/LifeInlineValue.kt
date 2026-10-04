package com.palmnote.ui.life

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.palmnote.app.R
import com.palmnote.ui.utils.LifeNumFormat
import java.math.BigDecimal

/**
 * 进度值的**就地编辑**：点数值 → 原地变成输入框，回车/失焦提交。
 *
 * ## 为什么是这个形态（三轮返工的结论）
 *
 * 生活页是**计划 / todo**类，那上面的数字是**进度量**（阅读 173/300 页、连续 12/30 天、
 * 存钱 3,500/10,000），语义是"推进进度"，不是"记一笔账"。前两版都错在**设计语言**：
 * 第一版是裸输入框 + 两个等权按钮；第二版更糟 —— 把**记账 App「记一笔」的自绘数字键盘**
 * 搬了进来（键盘 + ¥ 金额 + +100/+500 快捷金额）。那是**创建主流程**的形态，
 * 而且**全 App 没有任何一屏用自绘键盘** —— 外来范式，做得再细也不"精致"。
 *
 * 这一版按计划 / todo 类做法：**不弹面板、不进二级页、不用自绘键盘** ——
 * 数值本身就是入口（Notion 数字属性、AnyList 数量、苹果健康"添加数据"都是就地改）。
 * `−/+` 保留给"顺手推一格"。
 *
 * ## 提交时机：**回车 / 失焦才落库**
 *
 * 表单里的数值胶囊是**逐键**写 VM 状态（本地，便宜）；详情页这里每次提交都会**写数据库**
 * 并刷新小组件 —— 逐键提交就是写风暴。所以这里只在回车 / 失焦时提交一次。
 *
 * 纯逻辑（[sanitizeNumericInput] / [valueBlockReason] / [trimNumber] / [progressCountDisplay]）
 * 抽出来以便单测。
 */
@Composable
internal fun InlineNumberValue(
    spec: InlineValueSpec,
    accent: Color,
    onCommit: (Double) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodySmall
) {
    var editing by remember(spec.display) { mutableStateOf(false) }
    var text by remember(spec.display) { mutableStateOf("") }
    var block by remember { mutableStateOf<ValueBlock?>(null) }

    if (!editing) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .then(
                    if (spec.enabled) {
                        // 点数值即改：数值本身就是入口（不再另设「自定义」按钮）
                        Modifier.clickable {
                            text = trimNumber(spec.current)
                            block = null
                            editing = true
                        }
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                spec.display,
                style = textStyle,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1
            )
            if (spec.enabled) {
                // 真机反馈「没法实时直接编辑吗」—— 能编辑但**看不出来**。
                // 一枚很淡的小铅笔就够：不抢视觉，但明确"这里可以点"。
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.life_inline_edit_hint),
                    tint = accent.copy(alpha = 0.45f),
                    modifier = Modifier.padding(start = 3.dp).size(12.dp)
                )
            }
        }
        return
    }

    InlineNumberField(
        spec = spec,
        text = text,
        block = block,
        textStyle = textStyle,
        modifier = modifier,
        onTextChange = { raw ->
            text = sanitizeNumericInput(raw)
            block = valueBlockReason(text.toDoubleOrNull(), spec.min, spec.max)
        },
        // 合法就提交，不合法就放弃（不留"卡住"的编辑态）
        onFinish = {
            val v = text.toDoubleOrNull()
            if (block == null && v != null) onCommit(v)
            editing = false
        }
    )
}

/** 就地编辑的输入态：胶囊输入框 + 单位后缀 + 越界提示。 */
@Composable
private fun InlineNumberField(
    spec: InlineValueSpec,
    text: String,
    block: ValueBlock?,
    textStyle: TextStyle,
    onTextChange: (String) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focus = remember { FocusRequester() }
    // ⚠️ `onFocusChanged` **在修饰符首次挂载时也会回调一次**（此时 isFocused = false）。
    // 不加这道闸，输入框刚出现就会被自己的"失焦"回调关掉 —— 就地编辑根本打不开。
    var everFocused by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .width(104.dp)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .focusRequester(focus)
                    .onFocusChanged { st ->
                        if (st.isFocused) everFocused = true else if (everFocused) onFinish()
                    },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onFinish() }),
                textStyle = textStyle.copy(
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = if (block == null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                ),
                decorationBox = { inner ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        inner()
                        if (spec.unit.isNotBlank()) {
                            Spacer(Modifier.width(2.dp))
                            Text(
                                spec.unit,
                                style = textStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        }
        block?.let {
            Text(
                valueBlockText(it, spec),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** 就地编辑需要的值：收成一个对象，免得参数列表爆掉。 */
internal data class InlineValueSpec(
    /** 已格式化的显示串（如 `173 / 300 页`、`¥3,500`）。 */
    val display: String,
    val current: Double,
    val min: Double? = null,
    val max: Double? = null,
    /** 编辑态显示的单位后缀（如 `页`）。 */
    val unit: String = "",
    val enabled: Boolean = true
)

/** 输入不合法（或还没输入）的原因。 */
internal enum class ValueBlock { EMPTY, OVER_MAX, UNDER_MIN }

internal fun valueBlockReason(value: Double?, min: Double?, max: Double?): ValueBlock? = when {
    value == null -> ValueBlock.EMPTY
    max != null && value > max -> ValueBlock.OVER_MAX
    min != null && value < min -> ValueBlock.UNDER_MIN
    else -> null
}

/**
 * 数值输入净化：只留数字与**一个**小数点，限长 9（防止粘进离谱长串）。
 *
 * 系统数字键盘也可能带出字母或第二个小数点（粘贴、某些输入法），所以必须净化。
 */
internal fun sanitizeNumericInput(raw: String): String {
    val kept = raw.filter { it.isDigit() || it == '.' }
    val firstDot = kept.indexOf('.')
    val single = if (firstDot < 0) {
        kept
    } else {
        kept.substring(0, firstDot + 1) + kept.substring(firstDot + 1).replace(".", "")
    }
    return single.take(9)
}

/** 编辑态回填：整数不带小数点。 */
internal fun trimNumber(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

/**
 * 「当前 / 目标 单位」的显示串（如 `173 / 300 页`、`3,500 / 10,000`）。
 *
 * 环形态的 hero 只画环与百分比，**没有原始计数** —— 就地编辑要落在一个数上，
 * 所以那行计数既是入口，也顺手补上了缺失的原始值。
 */
internal fun progressCountDisplay(current: Double, total: Double?, unit: String): String {
    val head = LifeNumFormat.num(BigDecimal.valueOf(current))
    val tail = total?.takeIf { it > 0 }?.let { " / " + LifeNumFormat.num(BigDecimal.valueOf(it)) }.orEmpty()
    return head + tail + if (unit.isBlank()) "" else " $unit"
}

@Composable
private fun valueBlockText(block: ValueBlock, spec: InlineValueSpec): String = when (block) {
    ValueBlock.EMPTY -> stringResource(R.string.life_inline_need_number)
    ValueBlock.OVER_MAX -> stringResource(R.string.life_inline_over_max, formatLimit(spec.max, spec))
    ValueBlock.UNDER_MIN -> stringResource(R.string.life_inline_under_min, formatLimit(spec.min, spec))
}

private fun formatLimit(v: Double?, spec: InlineValueSpec): String =
    v?.let { trimNumber(it) + spec.unit } ?: ""
