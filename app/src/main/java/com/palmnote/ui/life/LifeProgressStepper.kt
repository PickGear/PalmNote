package com.palmnote.ui.life

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.palmnote.app.R
import com.palmnote.domain.model.FieldConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * 进度**就地推进**（用户真机反馈：「购物计划、存钱计划这些进度无法更新，添加后只能看」）。
 *
 * 此前所有进度型条目（存钱 `currentAmount` / 购物 `spent` / 阅读 `currentPage` …）**唯一**的
 * 更新路径是「⋯ → 编辑记录 → 找到那个数字 → 保存」四步；hero 的进度条与结构区的进度条
 * 都只是展示。而 `FieldConfig.step`（存钱模板明确给了 500）**全仓只有滑块在用**，等于死数据。
 *
 * 这里给出一对紧凑的 −/+ 按钮，**结构区与 hero 共用同一个控件** ——
 * 避免出现「一处能改、另一处只能看」。
 */

/**
 * 进度步长：模板给了 [FieldConfig.step] 就用它（存钱模板明确给 500）；
 * 没给就按**目标的 5%**（至少 1）—— 购物预算 2000 → 每次 100，阅读 300 页 → 每次 15。
 *
 * 为什么不默认 1：对金额型进度「一次一元」等于没有快捷入口，用户还是得回去手打。
 */
internal fun progressStepOf(config: FieldConfig, target: Double?): Double =
    config.step?.takeIf { it > 0 } ?: target?.let { niceStep(it) } ?: 1.0

/**
 * 「目标 ÷ 20」再取整到**好用档位**（1 / 2 / 5 / 10 / 20 / 50 / 100 / 200 / 500 / 1000 …）。
 *
 * 目标是整数时**步长也取整** —— 这是真机反馈暴露的问题：页数 / 节数 / 天数是**计数**，
 * 原先直接 `目标 ÷ 20` 会得到 17.5（350 页）或 1.2（24 节）这种步长，
 * 于是 `+` 一下就把「164.5 页」「9.2 节」写进了数据 —— **不成立的值**。
 */
internal fun niceStep(target: Double): Double {
    val raw = target / 20
    if (raw < 1.0) return 1.0
    val pow = 10.0.pow(floor(log10(raw)))
    val normalized = raw / pow
    val nice = when {
        normalized < 1.5 -> 1.0
        normalized < 3.5 -> 2.0
        normalized < 7.5 -> 5.0
        else -> 10.0
    }
    val step = nice * pow
    return if (target == floor(target)) floor(step).coerceAtLeast(1.0) else step
}

/**
 * 推进后的取值：**步长是整数时把结果取整**。
 *
 * 两个作用：① 计数类字段不会再被加出小数（9.2 节 / 164.5 页）；
 * ② 顺手修正历史遗留的小数 —— 旧步长写进去的 164.5，下一次推进就会回到整数。
 * 步长非整数（如 0.5 kg）时原样返回，不干扰小数语义。
 */
internal fun snapNudged(value: Double, step: Double): Double =
    // 用 HALF_UP（floor(v+0.5)）而不是 kotlin.math.round：后者是**银行家舍入**，
    // 会把 184.5 变成 184，与显示层 `LifeNumFormat` 的 HALF_UP 口径也不一致。
    if (step > 0 && step == floor(step)) floor(value + 0.5) else value

/**
 * **表单**里这个数值字段要不要给步进器，给的话步长是多少；返回 null = 不给。
 *
 * 只在这两种情况下给：模板**显式写了 `step`**（存钱「已存金额」= 500），
 * 或者它是**带分母的进度字段**（购物「已花费」→ 预算的 5%）。
 * 其余普通数字（身高 / 体重 / 页数之类）不给 —— 没有有意义的步长，加了只是噪音。
 */
internal fun progressStepFor(config: FieldConfig, target: Double?): Double? {
    val explicit = config.step
    return when {
        explicit != null && explicit > 0 -> explicit
        config.showAsProgress && target != null -> progressStepOf(config, target).takeIf { it > 0 }
        else -> null
    }
}

/**
 * 把 [fieldsData] 里 [key] 的数值**设为** [value]（[min]/[max] 做了钳制），返回完整 JSON。
 *
 * 与 `LifeCreateRecordViewModel.buildFieldsData` 对 NUMBER 的口径一致：写**数值**而不是字符串，
 * 原值解析不出时按 0 起算。
 */
internal fun setNumericField(
    fieldsData: String,
    key: String,
    value: Double,
    min: Double? = null,
    max: Double? = null
): String {
    val obj = runCatching { Json.decodeFromString<JsonObject>(fieldsData) }
        .getOrDefault(JsonObject(emptyMap()))
    var next = value
    if (min != null) next = next.coerceAtLeast(min)
    if (max != null) next = next.coerceAtMost(max)
    return JsonObject(obj.toMutableMap().apply { put(key, JsonPrimitive(next)) }).toString()
}

/**
 * 从 [fieldsData] 读某字段的**当前数值**（解析不出按 0 起算，与 [setNumericField] 同口径）。
 *
 * 用途：−/+ 的增量要在**写入那一刻**基于库里的最新值计算 ——
 * 不能用界面上的旧值算绝对值（见 `LifeDetailViewModel.nudgeProgress`）。
 */
internal fun numericFieldOf(fieldsData: String, key: String): Double {
    val obj = runCatching { Json.decodeFromString<JsonObject>(fieldsData) }.getOrNull() ?: return 0.0
    return (obj[key] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
}

/**
 * −/+ 就地推进控件。[onNudge] 收到的是**增量**（`+step` / `−step`），不是目标值。
 *
 * ## 为什么是增量而不是目标值（审查时发现的问题）
 *
 * 旧版把 `current ± step` 算在**界面**上再交给上层写库。可详情页还有"点数值就地编辑"这条
 * 入口：用户敲完数字、紧接着点 `±`，失焦提交（异步写库）与 `±` 的绝对写入会**交错** ——
 * `±` 用的是界面上的旧值，于是**刚输入的数字被覆盖**。
 *
 * 改成增量后，写入方（VM）在**那一刻**读库里的最新值再加减，两条写入再用互斥锁排序，
 * 顺序就确定了。边界钳制仍在 [setNumericField] 里做（单一真源）。
 */
@Composable
internal fun ProgressStepper(
    step: Double,
    accent: Color,
    modifier: Modifier = Modifier,
    onNudge: (Double) -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        StepButton(
            icon = Icons.Filled.Remove,
            description = stringResource(R.string.life_stepper_minus),
            accent = accent
        ) { onNudge(-step) }
        StepButton(
            icon = Icons.Filled.Add,
            description = stringResource(R.string.life_stepper_plus),
            accent = accent
        ) { onNudge(step) }
    }
}

/**
 * 单步按钮：**单击走一步**（带涟漪与无障碍语义），**按住则连发**（长按加速）。
 *
 * 做法：`Surface(onClick, interactionSource)` 既保留涟漪 / TalkBack，又能从交互流里
 * 读到"按住 / 松手"（`collectIsPressedAsState`）—— 比 `pointerInput` 自绘手势省事，
 * 也不会丢掉无障碍语义。
 *
 * **每一步都会写库**（走 `onNudge` 的增量口径）。这是有意的取舍：连发间隔 150ms→80ms，
 * 一次长按最多几十次写入，都是本地 SQLite 写（且 VM 里有互斥锁排序）——
 * 换来的是**数值与进度条实时跟着走**，比"按住只给个预览、松手才落库"更好懂。
 * 若将来写入成本真的成为问题，升级路径是"按住本地预览 + 松手提交一次"。
 */
@Composable
private fun StepButton(
    icon: ImageVector,
    description: String,
    accent: Color,
    onStep: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Surface(
        shape = CircleShape,
        color = accent.copy(alpha = 0.14f),
        onClick = onStep,
        interactionSource = interaction
    ) {
        Icon(
            icon,
            description,
            tint = accent,
            modifier = Modifier.padding(6.dp).size(16.dp)
        )
    }

    // 按住：先等一个"长按阈值"（避免把普通单击误判成长按），然后按 [repeatIntervalMs] 连发。
    // 松手时 `pressed` 变 false → 本效应被取消，连发立即停。
    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        delay(LONG_PRESS_DELAY_MS)
        var ticks = 0
        while (true) {
            onStep()
            ticks++
            delay(repeatIntervalMs(ticks))
        }
    }
}

/** 长按阈值：短于此仍算单击（单击那一步由 `Surface(onClick)` 负责，不会重复）。 */
private const val LONG_PRESS_DELAY_MS = 350L

/** 连发起始间隔（毫秒）。 */
internal const val REPEAT_INTERVAL_MS = 150L

/** 加速后的间隔（毫秒）。 */
internal const val REPEAT_INTERVAL_FAST_MS = 80L

/** 连发多少步之后开始加速。 */
internal const val REPEAT_ACCEL_AFTER_TICKS = 6

/**
 * 长按连发的间隔曲线（纯函数，可单测）：起步 150ms，连发 6 步后加速到 80ms。
 *
 * 抽出来是因为**时序没法单测**，但"第几步该多快"这条规则可以 —— 曲线写错的话，
 * 长按要么慢得像卡住、要么快到失控。
 */
internal fun repeatIntervalMs(ticks: Int): Long =
    if (ticks >= REPEAT_ACCEL_AFTER_TICKS) REPEAT_INTERVAL_FAST_MS else REPEAT_INTERVAL_MS
