// 本文件是「设计稿逐形态一个渲染器」：16 种 hero × 各自的子块，函数**多而小**是设计使然
// （与 LifeCreateFieldKit / LifeEditKit / LifeScreen 同一条既有惯例）。
@file:Suppress("TooManyFunctions")

package com.palmnote.ui.life

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.palmnote.app.R
import com.palmnote.data.db.entity.getDisplayName
import com.palmnote.domain.model.ChecklistRow
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.model.ProgressForm
import com.palmnote.domain.model.ProgressValue
import com.palmnote.domain.model.parseChecklist
import com.palmnote.domain.model.parseMap
import com.palmnote.domain.model.resolveProgressForm
import com.palmnote.domain.util.LifeTemplateKind
import com.palmnote.domain.util.getKind
import com.palmnote.ui.theme.Spacing
import com.palmnote.ui.theme.Success
import com.palmnote.ui.theme.TypeScale
import com.palmnote.ui.theme.Warning
import com.palmnote.ui.utils.LifeNumFormat
import kotlinx.coroutines.delay
import java.io.File
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * ① 英雄区（§14.2 / §14.12(3)）：**9 种形态**，逐模板标定。
 *
 * 「形态只有 9 种，但高度逐模板标定」——高度由**内容**决定（有没有头像、有没有副行）。
 * 数据不够时回落为「标题 + 身份色」的朴素英雄区，**绝不编造数字**
 * （§14.1 那批 bug 的通用修法：缺失走 null，而不是补 0）。
 *
 * 排印：正文用 M3 `typography`（本 App 既有语言，不新增 fontSize 字面量）；
 * **大数字**用 [TypeScale] 的 display 档（M3 的 display 是 57/45/36，与设计稿的 44/28 不同，
 * 故显式指定，保证「值 ∶ 标签 ≥ 3×」这条硬数字，§3.5.1）。
 *
 * 文案：**一律走字符串资源**（中英对等）——非 Composable 的取数函数通过 [HeroLabels] 接收
 * 已本地化的格式串，**不在代码里硬编码 UI 措辞**。
 */

/** 已本地化的格式串（在 Composable 里取一次，往下传）。 */
internal data class HeroLabels(
    // 还差 %1$s
    val gap: String,
    // 已达成
    val achieved: String,
    // 还有 %1$d 天扣费
    val daysToCharge: String,
    // 已存 %1$d%%
    val savedPct: String,
    // 已花 %1$d%%
    val spentPct: String,
    // 已完成 %1$d%%
    val donePct: String,
    // 目标 %1$s 天
    val targetDays: String,
    // 目标日 %1$s
    val targetDate: String,
    // 下次 %1$s
    val nextBilling: String,
    // %1$d 类
    val categories: String,
    // 预算 %1$s
    val budgetLabel: String,
    // 已花
    val spentUnit: String,
    // 月付 / 季付 / 年付
    val months: List<String>,
    // 还有
    val remaining: String,
    // 已经
    val passed: String,
    // 今天
    val today: String,
    // 超期
    val overdue: String,
    // 第 %1$d 年 · 已经 %2$s 天
    val yearsAndDays: String,
    // 读到 %1$s / %2$s 页
    val pagesRead: String,
    // 剩余 %1$s 页
    val pagesLeft: String,
    // %1$s · 有记录
    val moodDateLine: String,
    // %1$s
    val longest: String
)

@Composable
private fun heroLabels(): HeroLabels = HeroLabels(
    gap = stringResource(R.string.life_detail_remain),
    achieved = stringResource(R.string.life_detail_achieved),
    daysToCharge = stringResource(R.string.life_detail_days_to_charge),
    savedPct = stringResource(R.string.life_detail_note_saved_pct),
    spentPct = stringResource(R.string.life_detail_note_spent_pct),
    donePct = stringResource(R.string.life_detail_note_done_pct),
    targetDays = stringResource(R.string.life_detail_note_target_days),
    targetDate = stringResource(R.string.life_detail_note_target_date),
    nextBilling = stringResource(R.string.life_detail_note_next_billing),
    categories = stringResource(R.string.life_detail_note_categories),
    budgetLabel = stringResource(R.string.life_detail_label_budget),
    spentUnit = stringResource(R.string.life_detail_unit_spent),
    months = listOf(
        stringResource(R.string.life_detail_cycle_monthly),
        stringResource(R.string.life_detail_cycle_quarterly),
        stringResource(R.string.life_detail_cycle_yearly)
    ),
    remaining = stringResource(R.string.life_detail_word_remaining),
    passed = stringResource(R.string.life_detail_word_passed),
    today = stringResource(R.string.life_card_today),
    overdue = stringResource(R.string.life_detail_word_overdue),
    yearsAndDays = stringResource(R.string.life_detail_years_and_days),
    pagesRead = stringResource(R.string.life_detail_pages_read),
    pagesLeft = stringResource(R.string.life_detail_pages_left),
    moodDateLine = stringResource(R.string.life_detail_mood_date_line),
    longest = stringResource(R.string.life_detail_streak_longest)
)

@Composable
fun LifeHero(
    ctx: DetailCtx,
    onToggleChecklist: (String, Int) -> Unit,
    onSetProgress: ((String, Double) -> Unit)? = null,
    onNudgeProgress: ((String, Double, Double?, Double?) -> Unit)? = null,
    onSaveFocus: (Long) -> Unit = {},
    focusTimer: LifeDetailViewModel.FocusTimerState = LifeDetailViewModel.FocusTimerState(),
    onFocusStart: () -> Unit = {},
    onFocusResume: () -> Unit = {},
    onFocusPause: () -> Unit = {}
) {
    val labels = heroLabels()
    when (ctx.heroForm) {
        HeroForm.MONEY3 -> HeroMoney3(ctx, labels, onSetProgress, onNudgeProgress)
        HeroForm.DAYS -> HeroDays(ctx, labels)
        HeroForm.ROUTE -> HeroRoute(ctx)
        HeroForm.COVER -> HeroCover(ctx, labels, onSetProgress, onNudgeProgress)
        HeroForm.NOTE -> HeroNote(ctx)
        HeroForm.TODO -> HeroTodo(ctx, onToggleChecklist)
        HeroForm.MOOD -> HeroMood(ctx, labels)
        HeroForm.TIMER -> HeroTimer(ctx, focusTimer, onSaveFocus, onFocusStart, onFocusResume, onFocusPause)
    }
}

/** 端标 chip（§14.12(3)：h22 / rx11；**只给缺口绝对值**，不给百分比）。 */
@Composable
fun HeroChip(text: String, accent: Color, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    Box(
        modifier = modifier
            // §14.12(3)：端标 chip **h21 / rx10.5 / 10.5sp**（此前 rx11 + labelSmall = 10sp）
            .clip(RoundedCornerShape(10.5.dp))
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = Spacing.xxs)
    ) {
        Text(
            text,
            // §14.12(3)：端标 chip **h21 / rx10.5 / 10.5sp**。
            // `labelSmall` 的默认行高会让 chip 变成 24dp（4 + 16 + 4）——
            // 按 chip 字号收紧行高后才是 4 + 13 + 4 = **21**。
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = TypeScale.chip,
                lineHeight = 13.sp
            ),
            color = accent,
            maxLines = 1
        )
    }
}

// ───────────────────────── P1 · 三段式 ─────────────────────────

/** 左注 / 右注的语义（文案在 Composable 里成型，取数函数只给**数据**）。 */
internal enum class NoteKind { NONE, SAVED_PCT, SPENT_PCT, DONE_PCT, TARGET_DAYS }

internal enum class RightKind { NONE, TARGET_DATE, CATEGORIES, NEXT_BILLING, LONGEST }

internal data class Money3Data(
    val label: String,
    val big: String,
    val unitText: String,
    /** 订阅的周期（monthly/quarterly/yearly）——需要本地化，故交给 Composable。 */
    val cycleRaw: String?,
    val fraction: Float?,
    val form: ProgressForm,
    val segments: Int,
    val leftKind: NoteKind,
    val leftPct: Int,
    val leftTargetDays: String,
    val rightKind: RightKind,
    val rightText: String,
    val rightCount: Int,
    /** 缺口（正数）或 null；chip 文案由 Composable 成型。 */
    val gapAmount: Double?,
    val achieved: Boolean,
    /** 金额型（存钱 / 购物 / 学习 / 订阅…）：缺口要按金额格式化（§14.12(7).1 的例子是「还差 ¥204,000」）。 */
    val monetary: Boolean = false
)

@Composable
private fun HeroMoney3(
    ctx: DetailCtx,
    L: HeroLabels,
    onSetProgress: ((String, Double) -> Unit)?,
    onNudgeProgress: ((String, Double, Double?, Double?) -> Unit)?
) {
    val d = money3Data(ctx) ?: run {
        PlainHero(ctx)
        return
    }
    val chip = heroChipText(
        d = d,
        L = L,
        icon = ctx.template.icon,
        daysToBilling = ctx.cfg("daysToBilling")?.let { ctx.derived(it) }?.toLong()
    )
    val unit = heroUnitText(d.cycleRaw, d.unitText, L)
    val ringForm = d.form.takeIf { d.fraction != null && isRingProgressForm(it) }
    val pcfg = ctx.configs.firstOrNull { it.showAsProgress }
    val pv = ctx.progressOf(pcfg)
    val canAdjust = pcfg != null && pv != null && onSetProgress != null

    // §14.12(3)：money3 英雄区总高 **110dp**。构成 = 内边距 + 小标 + 大字 + 条 + 条下注，
    // 所以内部间距必须紧（此前 14/6/10/6 加起来约 126dp，比设计稿高约 16dp）。
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
        HeroMoney3Header(d.label, chip, ctx.accent)
        Spacer(Modifier.height(4.dp))
        if (ringForm == null) {
            HeroMoney3Amount(
                big = d.big,
                unit = unit,
                pv = pv,
                pcfg = pcfg,
                canAdjust = canAdjust,
                accent = ctx.accent,
                onSetProgress = onSetProgress,
                onNudgeProgress = onNudgeProgress
            )
        }
        if (d.fraction != null) {
            HeroMoney3Progress(
                ctx = ctx,
                d = d,
                L = L,
                unit = unit,
                state = HeroProgressState(
                    pv = pv,
                    pcfg = pcfg,
                    canAdjust = canAdjust,
                    ringForm = ringForm
                ),
                // 对齐必须由**父级**给：子组合函数不在 ColumnScope 里，用不了 Modifier.align
                centeredModifier = Modifier.align(Alignment.CenterHorizontally),
                endModifier = Modifier.align(Alignment.End),
                onSetProgress = onSetProgress,
                onNudgeProgress = onNudgeProgress
            )
        }
    }
}

/** 顶部一行：字段名 + 状态 chip。 */
@Composable
private fun HeroMoney3Header(label: String, chip: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        HeroChip(chip, accent)
    }
}

/**
 * 大数字（**本身就是入口**：点它原地变成输入框）+ 单位后缀 + **紧邻的 `−/+`**。
 *
 * ± 原先挂在卡片右下角，离数值隔着一整行空白 —— 真机反馈"加减进度怎么回事"。
 * 现在与数值同一行：看得见、够得着，和结构区进度行的手感也一致。
 */
@Composable
private fun HeroMoney3Amount(
    big: String,
    unit: String,
    pv: ProgressValue?,
    pcfg: FieldConfig?,
    canAdjust: Boolean,
    accent: Color,
    onSetProgress: ((String, Double) -> Unit)?,
    onNudgeProgress: ((String, Double, Double?, Double?) -> Unit)?
) {
    Row(verticalAlignment = Alignment.Bottom) {
        InlineNumberValue(
            spec = InlineValueSpec(
                display = big,
                current = pv?.current ?: 0.0,
                min = pcfg?.min,
                max = pcfg?.max,
                unit = unit,
                enabled = canAdjust
            ),
            accent = MaterialTheme.colorScheme.onSurface,
            onCommit = { v -> pcfg?.let { c -> onSetProgress?.invoke(c.key, v) } },
            textStyle = MaterialTheme.typography.headlineMedium.copy(fontSize = TypeScale.displayM)
        )
        if (unit.isNotBlank()) {
            Spacer(Modifier.width(6.dp))
            Text(
                unit,
                // §14.12(3)：巨字右侧单位 **13sp**（TypeScale.heroUnit）—— 此前用 bodyMedium（14sp）
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = TypeScale.heroUnit),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.xxs)
            )
        }
        if (canAdjust && pcfg != null) {
            Spacer(Modifier.weight(1f))
            ProgressStepper(
                step = progressStepOf(pcfg, pv?.total),
                accent = accent,
                modifier = Modifier.padding(bottom = Spacing.xxs)
            ) { delta -> onNudgeProgress?.invoke(pcfg.key, delta, pcfg.min, pcfg.max) }
        }
    }
}

/** 进度块需要的派生值：收成一个对象，免得子控件参数列表爆掉。 */
private data class HeroProgressState(
    val pv: ProgressValue?,
    val pcfg: FieldConfig?,
    val canAdjust: Boolean,
    val ringForm: ProgressForm?
)

/**
 * 进度块：进度形态（环 / 条）→ 左右注脚 → 就地推进的 `−/+`。
 *
 * 环形态下大数字画在环里，就地编辑改落在**下面那行计数**上（顺带补上缺失的原始值）。
 */
@Composable
private fun HeroMoney3Progress(
    ctx: DetailCtx,
    d: Money3Data,
    L: HeroLabels,
    unit: String,
    state: HeroProgressState,
    /** 居中 / 右对齐的 Modifier —— 由父级（ColumnScope 内）传入。 */
    centeredModifier: Modifier,
    endModifier: Modifier,
    onSetProgress: ((String, Double) -> Unit)?,
    onNudgeProgress: ((String, Double, Double?, Double?) -> Unit)?
) {
    val ringForm = state.ringForm
    val pv = state.pv
    val pcfg = state.pcfg
    val canAdjust = state.canAdjust
    Spacer(Modifier.height(8.dp))
    LifeProgressForm(
        form = ringForm ?: d.form,
        fraction = d.fraction ?: 0f,
        color = ctx.accent,
        segments = d.segments,
        modifier = if (ringForm != null) centeredModifier else Modifier
    ) {
        if (ringForm != null) {
            HeroRingValue(d.big, unit, ringForm)
        }
    }
    if (ringForm != null && canAdjust) {
        Spacer(Modifier.height(4.dp))
        InlineNumberValue(
            spec = InlineValueSpec(
                display = progressCountDisplay(pv?.current ?: 0.0, pv?.total, unit),
                current = pv?.current ?: 0.0,
                min = pcfg?.min,
                max = pcfg?.max,
                unit = unit,
                enabled = true
            ),
            accent = ctx.accent,
            onCommit = { v -> pcfg?.let { c -> onSetProgress?.invoke(c.key, v) } },
            textStyle = MaterialTheme.typography.labelMedium,
            modifier = centeredModifier
        )
    }
    Spacer(Modifier.height(4.dp))
    // 存钱 / 购物这类进度型条目的 hero 也要能推进：否则用户看到的第一屏仍然「只能看」。
    Row {
        Text(
            heroLeftText(d.leftKind, d.leftPct, d.leftTargetDays, L = L),
            // §14.12(3)：条下左注 **11sp/600/身份色**（此前 labelSmall = 10sp）
            style = MaterialTheme.typography.labelSmall.copy(fontSize = TypeScale.note),
            fontWeight = FontWeight.SemiBold,
            color = ctx.accent
        )
        Spacer(Modifier.weight(1f))
        Text(
            heroRightText(d.rightKind, d.rightText, d.rightCount, L = L),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = TypeScale.note),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    // ± 只在**环形态**下留在进度块里（数值画在环里，没法和它同一行）；
    // 条形态的 ± 已经挪到数值旁边（见 HeroMoney3Amount）。
    if (canAdjust && pcfg != null && ringForm != null) {
        Spacer(Modifier.height(6.dp))
        ProgressStepper(
            step = progressStepOf(pcfg, pv?.total),
            accent = ctx.accent,
            modifier = endModifier
        ) { delta -> onNudgeProgress?.invoke(pcfg.key, delta, pcfg.min, pcfg.max) }
    }
}

// ── 文案拼装（纯函数：把这些 `when` 移出组合函数，既降复杂度也可单测）──────────

/** 状态 chip：达标 / 还差多少 / 距下次扣费天数。 */
internal fun heroChipText(
    d: Money3Data,
    L: HeroLabels,
    icon: String,
    daysToBilling: Long?
): String = when {
    d.achieved -> L.achieved
    // §14.12(7).1：端标给**缺口绝对值**，且金额型要带货币符号（此前一律 fmtNumber，
    // 于是「还差 204000」—— 既没有 ¥，也不是规范例子里的写法）
    d.gapAmount != null -> String.format(L.gap, if (d.monetary) fmtMoney(d.gapAmount) else fmtNumber(d.gapAmount))
    icon == "subscriptions" -> daysToBilling?.let { String.format(L.daysToCharge, it) }.orEmpty()
    else -> ""
}

/** 单位：订阅类模板按周期显示「/ 月」等，其余用模板自己的单位文案。 */
internal fun heroUnitText(cycleRaw: String?, unitText: String, L: HeroLabels): String =
    cycleRaw?.let { cycle ->
        val idx = listOf("monthly", "quarterly", "yearly").indexOf(cycle)
        if (idx >= 0) "/ " + L.months[idx] else unitText
    } ?: unitText

/** 左下注脚（已存 / 已花 / 完成百分比 / 目标天数）。 */
internal fun heroLeftText(kind: NoteKind, leftPct: Int, leftTargetDays: String, L: HeroLabels): String =
    when (kind) {
        NoteKind.SAVED_PCT -> String.format(L.savedPct, leftPct)
        NoteKind.SPENT_PCT -> String.format(L.spentPct, leftPct)
        NoteKind.DONE_PCT -> String.format(L.donePct, leftPct)
        NoteKind.TARGET_DAYS -> String.format(L.targetDays, leftTargetDays)
        NoteKind.NONE -> ""
    }

/** 右下注脚（目标日期 / 下次扣费 / 分类数 / 最长记录）。 */
internal fun heroRightText(kind: RightKind, rightText: String, rightCount: Int, L: HeroLabels): String =
    when (kind) {
        RightKind.TARGET_DATE -> String.format(L.targetDate, rightText)
        RightKind.NEXT_BILLING -> String.format(L.nextBilling, rightText)
        RightKind.CATEGORIES -> String.format(L.categories, rightCount)
        RightKind.LONGEST -> rightText.takeIf { it.isNotBlank() }?.let { String.format(L.longest, it) }.orEmpty()
        RightKind.NONE -> ""
    }

@Composable
private fun HeroRingValue(big: String, unit: String, form: ProgressForm) {
    val valueSize = when (form) {
        ProgressForm.THICK_RING -> TypeScale.ringValueThick
        ProgressForm.THIN_RING -> TypeScale.ringValueThin
        ProgressForm.SEGMENTED_RING -> TypeScale.ringValueSegmented
        ProgressForm.BEADED_RING -> TypeScale.ringValueBeaded
        ProgressForm.THICK_CAPSULE,
        ProgressForm.THIN_TRACK,
        ProgressForm.SEGMENTED_BAR -> TypeScale.displayM
    }
    val unitSize = if (form == ProgressForm.THIN_RING) TypeScale.ringUnitThin else TypeScale.ringUnit
    Column(
        modifier = Modifier.fillMaxWidth(0.72f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            big,
            style = MaterialTheme.typography.headlineMedium,
            fontSize = valueSize,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (unit.isNotBlank()) {
            Text(
                unit,
                style = MaterialTheme.typography.bodySmall,
                fontSize = unitSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun money3Data(ctx: DetailCtx): Money3Data? = when (ctx.template.icon) {
    "savings" -> savingsHeroData(ctx)
    "shopping_cart" -> shoppingHeroData(ctx)
    "school" -> schoolHeroData(ctx)
    "calendar_month" -> checkInHeroData(ctx)
    "subscriptions" -> subscriptionHeroData(ctx)
    "fitness_center" -> fitnessHeroData(ctx)
    else -> null
}

internal fun savingsHeroData(ctx: DetailCtx): Money3Data {
    val target = ctx.num("targetAmount")
    val config = ctx.cfg("currentAmount")
    val form = config?.let(::resolveProgressForm) ?: ProgressForm.THICK_CAPSULE
    return money3Of(
        label = ctx.cfg("targetAmount")?.label.orEmpty(),
        big = ctx.num("currentAmount"),
        target = target,
        fraction = ctx.progressOf(config)?.fraction,
        form = form,
        segments = progressSegmentsFor(form, target),
        monetary = true,
        leftKind = NoteKind.SAVED_PCT,
        rightKind = RightKind.TARGET_DATE,
        rightText = ctx.dateText("deadline").orEmpty()
    )
}

internal fun shoppingHeroData(ctx: DetailCtx): Money3Data {
    val budget = ctx.num("budget")
    val config = ctx.cfg("spent")
    val form = config?.let(::resolveProgressForm) ?: ProgressForm.THICK_CAPSULE
    return money3Of(
        label = ctx.cfg("budget")?.label.orEmpty(),
        big = ctx.num("spent"),
        target = budget,
        fraction = ctx.progressOf(config)?.fraction,
        monetary = true,
        form = form,
        segments = progressSegmentsFor(form, budget),
        unitText = ctx.cfg("spent")?.label.orEmpty(),
        leftKind = NoteKind.SPENT_PCT,
        rightKind = RightKind.CATEGORIES,
        rightCount = ctx.list("category").size
    )
}

internal fun schoolHeroData(ctx: DetailCtx): Money3Data {
    val total = ctx.num("totalLessons")
    val config = ctx.cfg("completedLessons")
    val form = config?.let(::resolveProgressForm) ?: ProgressForm.SEGMENTED_RING
    return money3Of(
        label = config?.label.orEmpty(),
        big = ctx.num("completedLessons"),
        target = total,
        fraction = ctx.progressOf(config)?.fraction,
        form = form,
        segments = progressSegmentsFor(form, total),
        unitText = total?.let { "/ ${fmtNumber(it)} ${ctx.cfg("totalLessons")?.unit.orEmpty()}".trimEnd() }.orEmpty(),
        leftKind = NoteKind.DONE_PCT
    )
}

internal fun checkInHeroData(ctx: DetailCtx): Money3Data {
    val target = ctx.num("targetDays")
    val config = ctx.cfg("currentStreak")
    val form = config?.let(::resolveProgressForm) ?: ProgressForm.SEGMENTED_RING
    val unit = config?.unit.orEmpty()
    return money3Of(
        label = config?.label.orEmpty(),
        big = ctx.num("currentStreak"),
        target = target,
        fraction = ctx.progressOf(config)?.fraction,
        form = form,
        segments = progressSegmentsFor(form, target),
        unitText = unit,
        leftKind = NoteKind.TARGET_DAYS,
        leftTargetDays = target?.let { t ->
            val pct = ctx.progressOf(config)?.fraction?.let { (it * 100).toInt() }
            fmtNumber(t) + (pct?.let { " · $it%" } ?: "")
        }.orEmpty(),
        rightKind = RightKind.LONGEST,
        rightText = ctx.aggregates.checkInLongest?.let { "$it $unit".trim() }.orEmpty()
    )
}

internal fun subscriptionHeroData(ctx: DetailCtx): Money3Data {
    // 本周期已过比例（dtl_14 进度条）：由下次扣费日反推本周期起点（月/季/年）
    val next = ctx.date("nextBilling")
    val fraction = next?.let { n ->
        val start = when (ctx.str("billingCycle")) {
            "quarterly" -> n.minusMonths(3)
            "yearly" -> n.minusYears(1)
            else -> n.minusMonths(1)
        }
        val totalDays = java.time.temporal.ChronoUnit.DAYS.between(start, n).toFloat()
        if (totalDays <= 0f) {
            null
        } else {
            (java.time.temporal.ChronoUnit.DAYS.between(start, ctx.today).toFloat() / totalDays).coerceIn(0f, 1f)
        }
    }
    return money3Of(
        label = ctx.cfg("price")?.label.orEmpty(),
        big = ctx.num("price"),
        target = null,
        fraction = fraction,
        monetary = true,
        cycleRaw = ctx.str("billingCycle"),
        rightKind = RightKind.NEXT_BILLING,
        rightText = ctx.dateText("nextBilling").orEmpty()
    )
}

internal fun fitnessHeroData(ctx: DetailCtx): Money3Data = money3Of(
    label = ctx.cfg("weight")?.label.orEmpty(),
    big = ctx.num("weight"),
    target = null,
    fraction = null,
    unitText = ctx.cfg("weight")?.unit.orEmpty()
)

@Suppress("LongParameterList")
internal fun money3Of(
    label: String,
    big: Double?,
    target: Double?,
    fraction: Float?,
    form: ProgressForm = ProgressForm.THICK_CAPSULE,
    monetary: Boolean = false,
    unitText: String = "",
    cycleRaw: String? = null,
    segments: Int = 0,
    leftKind: NoteKind = NoteKind.NONE,
    leftTargetDays: String = "",
    rightKind: RightKind = RightKind.NONE,
    rightText: String = "",
    rightCount: Int = 0
): Money3Data {
    val gap = if (target != null && big != null) target - big else null
    return Money3Data(
        label = label,
        big = big?.let { if (monetary) fmtMoney(it) else fmtNumber(it) } ?: DetailCtx.PLACEHOLDER,
        unitText = unitText,
        cycleRaw = cycleRaw,
        fraction = fraction,
        form = form,
        segments = segments,
        leftKind = leftKind,
        leftPct = ((fraction ?: 0f) * 100).toInt(),
        leftTargetDays = leftTargetDays,
        rightKind = rightKind,
        rightText = rightText,
        rightCount = rightCount,
        gapAmount = gap?.takeIf { it > 0 },
        achieved = gap != null && gap <= 0,
        monetary = monetary
    )
}

// ───────────────────────── P2 · 天数巨字 ─────────────────────────

@Composable
private fun HeroDays(ctx: DetailCtx, L: HeroLabels) {
    val d = daysData(ctx)
    val person = ctx.str("person")
    val word = when {
        d.days == null -> ""
        // 当天不渲染「还有 0 天」，规范 §9 要求显示「今天」
        d.days == 0L -> L.today
        d.days > 0L -> L.remaining
        else -> L.passed
    }
    val kind = ctx.template.getKind()
    // 纪念日专属补充（原来是 icon == "celebration"）——行为判据，走统一入口
    val extra = if (kind == LifeTemplateKind.ANNIVERSARY) celebrationExtra(ctx, L) else ""
    // 生肖/农历只在生日下显示（原来是 icon == "cake"）
    val lunarAnchor = if (kind == LifeTemplateKind.BIRTHDAY && parseLunarFlag(ctx.raw("lunar"))) {
        ctx.date("date")?.let(::lunarMonthDayOf)
    } else {
        null
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (person != null) {
            AvatarCircle(name = person, accent = ctx.accent)
            Spacer(Modifier.height(Spacing.xs))
        }
        Text(word, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 当天只显示「今天」，不再重复一个大号「0 天」；d.days == null 仍走占位符
        if (d.days == null || d.days != 0L) {
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    d.days?.let { LifeNumFormat.num(kotlin.math.abs(it)) } ?: DetailCtx.PLACEHOLDER,
                    style = MaterialTheme.typography.displayMedium,
                    // 天数巨字 **44sp**（设计稿 dtl_07 / 08 / 09 / 10 实测）——全 App 唯一一处。
                    // 此前按 §11.3 令牌收敛成 28sp，与图不符（金额巨字才是 28sp）。
                    fontSize = TypeScale.dayHero,
                    fontWeight = FontWeight.Bold,
                    color = ctx.accent
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.life_card_days_unit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
        }
        if (extra.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xxs))
            Text(extra, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = ctx.accent)
        }
        Spacer(Modifier.height(10.dp))
        DaysAnchors(d, lunarAnchor)
    }
}

// 注脚（dtl_07~10）：「今天 09-20」「起 2025-09-20」「周年 2026-11-04」—— 标签词 + 日期
@Composable
private fun DaysAnchors(d: LifeHeroDaysData, lunarAnchor: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            anchorText(d.fromLabelRes, d.from, lunarAnchor),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.xs)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        )
        Text(
            anchorText(d.toLabelRes, d.to),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun anchorText(labelRes: Int, date: String, lunarDate: String? = null): String = listOfNotNull(
    lunarDate ?: if (labelRes != 0) stringResource(labelRes) else null,
    date.takeIf { it.isNotBlank() }
).joinToString(" ")

/** 纪念日副行：「第 N 年 · 已经 M 天」（§14.11 #10：放副行，避免同屏两个竞争的大数字）。 */
@Composable
private fun celebrationExtra(ctx: DetailCtx, L: HeroLabels): String {
    val start = ctx.date("date") ?: return ""
    val days = ChronoUnit.DAYS.between(start, ctx.today)
    if (days < 0) return ""
    val years = (days / 365).toInt() + 1
    return String.format(L.yearsAndDays, years, fmtNumber(days.toDouble()))
}

// ───────────────────────── P3 · 媒介型 ─────────────────────────

@Composable
private fun HeroRoute(ctx: DetailCtx) {
    val cfg = ctx.cfgByType(FieldType.MAP)
    val model = cfg?.let { parseMap(ctx.raw(it.key)) }
    if (model == null || (model.route.size < 2 && model.track?.pts.orEmpty().size < 2)) {
        PlainHero(ctx)
        return
    }
    val days = ctx.date("startDate")?.let { ChronoUnit.DAYS.between(ctx.today, it) }
    val chip = when {
        days == null -> ""
        days >= 0 -> {
            val n = days.toInt()
            pluralStringResource(R.plurals.dashboard_days_until, n, n)
        }
        else -> stringResource(R.string.life_detail_on_the_way)
    }
    RouteBoard(model = model, accent = ctx.accent, chip = chip)
}

@Composable
private fun HeroCover(
    ctx: DetailCtx,
    L: HeroLabels,
    onSetProgress: ((String, Double) -> Unit)?,
    onNudgeProgress: ((String, Double, Double?, Double?) -> Unit)?
) {
    val cover = ctx.cfgByType(FieldType.IMAGE)?.let { ctx.str(it.key) }
    val p = ctx.progressOf(ctx.configs.firstOrNull { it.showAsProgress })
    // 磁盘 IO 只在封面路径变化时做一次，不跟重组走
    val coverExists = remember(cover) { cover != null && (cover.startsWith("content:") || File(cover).exists()) }
    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 92.dp, height = 128.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ctx.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            if (cover != null && coverExists) {
                AsyncImage(
                    model = File(cover),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(ctx.item.title.take(1), style = MaterialTheme.typography.displaySmall, color = ctx.accent)
            }
            // 书脊高亮条（dtl_05）：封面左缘 3dp 竖条，书封与首字母回落都带
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(ctx.accent)
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                ctx.item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            ctx.str("author")?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (p != null) {
                HeroCoverProgress(ctx, p, onSetProgress, onNudgeProgress)
            }
            HeroCoverPages(ctx, L, onSetProgress)
        }
    }
}

/**
 * 封面 hero 的进度块：**环 + 百分比（都可点** → 输入精确值）+ `−/+` + 「自定义金额」弹窗。
 *
 * 抽成独立 composable 有两个理由：① 塞在 [HeroCover] 里会把它的圈复杂度顶到 detekt 阈值；
 * ② 这块与结构区 / money hero 是同一件事（就地推进 + 精确输入），单独一座便于对齐。
 *
 * 此前封面型条目只有 `−/+` 固定步长：阅读进度「读到第 173 页」要点 173 次。
 */
@Composable
private fun HeroCoverProgress(
    ctx: DetailCtx,
    p: ProgressValue,
    onSetProgress: ((String, Double) -> Unit)?,
    onNudgeProgress: ((String, Double, Double?, Double?) -> Unit)?
) {
    val pcfg = ctx.configs.firstOrNull { it.showAsProgress }
    val canAdjust = pcfg != null && onSetProgress != null
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 媒介为主、进度让位：**小尺寸环**（§14.12(3) cover）
        LifeRing(fraction = p.fraction, color = ctx.accent, size = 40.dp, strokeWidth = 4.dp)
        Spacer(Modifier.width(Spacing.xs))
        Text(
            "${LifeNumFormat.num((p.fraction * 100).toInt())}%",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = ctx.accent
        )
    }
    // 封面型条目只画了环与百分比。就地编辑**不在这里另加计数行** ——
    // 真机截图暴露过重复：这里一行「164.5 / 350 页」、下面 [HeroCoverPages] 又一行
    // 「读到 164.5 / 350 页」。入口统一放在下面那行（它本来就是"数值"）。
    // 与结构区同一套就地推进控件（阅读「读到第 N 页」也要能整块往前推）。
    if (canAdjust) {
        Spacer(Modifier.height(6.dp))
        ProgressStepper(
            step = progressStepOf(pcfg, p.total),
            accent = ctx.accent
        ) { delta -> onNudgeProgress?.invoke(pcfg.key, delta, pcfg.min, pcfg.max) }
    }
}

/**
 * 封面 hero 的页数行（阅读类）：**「已读 N / 共 M 页」那行本身可点即改**，下方补「还剩 N 页」。
 *
 * 抽出来有两个理由：① 塞在 [HeroCover] 里会让它顶到 detekt 的 `LongMethod`；
 * ② 这行本来就是"数值"，让它直接成为就地编辑的入口 —— 比另加一行计数**更省地方、也不重复信息**。
 */
@Composable
private fun HeroCoverPages(
    ctx: DetailCtx,
    L: HeroLabels,
    onSetProgress: ((String, Double) -> Unit)?
) {
    val cur = ctx.num("currentPage") ?: return
    val total = ctx.num("totalPages") ?: return
    val pcfg = ctx.configs.firstOrNull { it.showAsProgress }
    Spacer(Modifier.height(6.dp))
    InlineNumberValue(
        spec = InlineValueSpec(
            display = String.format(L.pagesRead, fmtNumber(cur), fmtNumber(total)),
            current = cur,
            min = pcfg?.min,
            max = pcfg?.max,
            enabled = pcfg != null && onSetProgress != null
        ),
        accent = ctx.accent,
        onCommit = { v -> pcfg?.let { c -> onSetProgress?.invoke(c.key, v) } },
        textStyle = MaterialTheme.typography.labelSmall
    )
    if (total > cur) {
        Spacer(Modifier.height(2.dp))
        Text(
            String.format(L.pagesLeft, fmtNumber(total - cur)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HeroNote(ctx: DetailCtx) {
    val contentCfg = ctx.cfgByType(FieldType.RICH_TEXT, FieldType.TEXT)
    val text = contentCfg?.let { ctx.str(it.key) }.orEmpty()
    val chips = ctx.configs
        .filter { it.type == FieldType.SELECT || it.type == FieldType.TAG }
        .mapNotNull { ctx.str(it.key) }
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
        Text(
            fmtDateTime(ctx.item.createdAt).orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (chips.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row {
                chips.take(3).forEach { c ->
                    ReadOnlyChip(text = c, accent = ctx.accent)
                    Spacer(Modifier.width(6.dp))
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        if (text.isBlank()) {
            // 正文没写：用标题兜底，**不留白屏**
            Text(
                ctx.item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            // 前 2 行 T1、其余 T2（§14.12(3) note）—— 同一个 Text 的分段着色
            var expanded by remember { mutableStateOf(false) }
            val lines = text.split('\n')
            val body = buildAnnotatedString {
                lines.forEachIndexed { i, line ->
                    if (i > 0) append("\n")
                    withStyle(
                        SpanStyle(
                            color = if (i < 2) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    ) { append(line) }
                }
            }
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) Int.MAX_VALUE else 5,
                overflow = TextOverflow.Ellipsis
            )
            // 「展开全文 ›」（dtl_13）：正文长到可能被截断才出现，展开后可收起
            if (lines.size > 5 || text.length > 160) {
                Text(
                    stringResource(
                        if (expanded) R.string.life_detail_collapse else R.string.life_detail_expand
                    ) + " ›",
                    style = MaterialTheme.typography.labelSmall,
                    color = ctx.accent,
                    modifier = Modifier
                        .padding(top = Spacing.xxs)
                        .clickable { expanded = !expanded }
                )
            }
        }
    }
}

// ───────────────────────── P4 · 内容型 ─────────────────────────

@Composable
private fun HeroTodo(ctx: DetailCtx, onToggle: (String, Int) -> Unit) {
    val cfg = ctx.cfgByType(FieldType.CHECKLIST)
    val rows = cfg?.let { parseChecklist(ctx.raw(it.key)) }.orEmpty()
    val done = rows.count { it.done }
    // 行尾状态 chip（dtl_03）：逾期（黄）> 今天（身份色）——按**日**比较，不比毫秒
    val deadlineDate = ctx.date("deadline")

    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                ctx.item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (done < rows.size) {
                    stringResource(R.string.life_hero_remaining, rows.size - done)
                } else {
                    stringResource(R.string.life_hero_completed, done, rows.size)
                },
                style = MaterialTheme.typography.labelSmall,
                color = ctx.accent
            )
        }
        Spacer(Modifier.height(6.dp))
        if (rows.isEmpty()) {
            Text(
                stringResource(R.string.life_detail_no_value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        // dtl_03：未完成在前（可勾、带今天/逾期 chip），已完成折叠在下方小节（置灰、可反勾）
        val indexed = rows.withIndex().toList()
        val pending = indexed.filter { !it.value.done }
        val doneRows = indexed.filter { it.value.done }
        pending.take(6).forEach { (index, row) -> HeroTodoRow(ctx, cfg, row, index, deadlineDate, onToggle) }
        if (doneRows.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.life_hero_done_group, doneRows.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            doneRows.take(4).forEach { (index, row) -> HeroTodoRow(ctx, cfg, row, index, deadlineDate, onToggle) }
        }
    }
}

@Composable
private fun HeroTodoRow(
    ctx: DetailCtx,
    cfg: com.palmnote.domain.model.FieldConfig?,
    row: ChecklistRow,
    index: Int,
    deadlineDate: java.time.LocalDate?,
    onToggle: (String, Int) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth().height(30.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckDot(done = row.done) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            cfg?.let { onToggle(it.key, index) }
        }
        Spacer(Modifier.width(Spacing.xs))
        Text(
            row.text,
            style = MaterialTheme.typography.bodySmall,
            color = if (row.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        when {
            row.done -> Unit
            deadlineDate != null && deadlineDate.isBefore(ctx.today) ->
                HeroChip(stringResource(R.string.life_board_overdue), Warning)
            deadlineDate == ctx.today ->
                HeroChip(stringResource(R.string.life_detail_word_today), ctx.accent)
        }
    }
}

@Composable
private fun HeroMood(ctx: DetailCtx, L: HeroLabels) {
    val mood = ctx.str("mood").orEmpty()
    val energy = ctx.num("energy")
    val factors = ctx.list("factors")
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
        // dtl_12：左情绪圆 + 右「情绪词 · 记录日期」横排，不居中堆叠
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(ctx.accent.copy(alpha = 0.28f))
                    .border(1.5.dp, ctx.accent.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(MoodVisuals.emojiOf(mood), style = MaterialTheme.typography.displaySmall)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    mood.ifBlank { ctx.item.title },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    String.format(
                        L.moodDateLine,
                        fmtDate(ctx.item.dueDate ?: ctx.item.createdAt).orEmpty()
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (energy != null) {
            Spacer(Modifier.height(Spacing.sm))
            LifeTrackBar(
                fraction = (energy / 100.0).toFloat(),
                color = ctx.accent,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                listOfNotNull(ctx.cfg("energy")?.label, fmtNumber(energy)).joinToString(" "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (factors.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                factors.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 情绪 → 表情的映射收口在 [MoodVisuals]（英雄区与热力格同一真源）。 */

/**
 * 专注计时器（dtl_16 英雄区，唯一带操作的英雄区）：真实计时 —— 开始 / 暂停 / 继续 / 结束。
 *
 * - 环按 `已用时 / 25 分钟` 填充（设计稿 `timer` 规格：r42 / 描边 6），中心显示已用时 mm:ss；
 * - 结束（停止）时把本次时长（毫秒）写入一条专注会话记录（[onSaveFocus]），随后显示「已记录 mm:ss」；
 * - 计时状态纯 UI 本地（不落库），只有「结束」才产生数据 —— 中断退出不污染记录。
 */
@Composable
private fun HeroTimer(
    ctx: DetailCtx,
    timer: LifeDetailViewModel.FocusTimerState,
    onSaveFocus: (Long) -> Unit,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit
) {
    // 计时状态全部来自持久层（startAt/accumMs）：离屏、杀进程后回到本页，计时仍在走。
    val running = timer.running
    var tick by remember { mutableStateOf(0L) }
    var savedLabel by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current
    // 200ms 心跳仅驱动重组；elapsed 每次按挂钟实时算，不累积内存态
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (true) {
            delay(200)
            tick += 1
        }
    }
    val elapsedMs = timer.accumMs + if (running) System.currentTimeMillis() - timer.startAt else 0L
    val targetMs = 25L * 60 * 1000
    val fraction = (elapsedMs.toFloat() / targetMs).coerceIn(0f, 1f)
    // 在组合上下文取一次格式串；onClick 是非组合 lambda，不能再调 stringResource
    val savedFmt = stringResource(R.string.life_detail_timer_saved)
    Column(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            LifeRing(fraction = fraction, color = ctx.accent, size = 84.dp, strokeWidth = 6.dp)
            Text(
                formatElapsed(elapsedMs),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            ctx.item.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(Spacing.xs))
        val saved = savedLabel
        if (saved != null) {
            Text(saved, style = MaterialTheme.typography.labelSmall, color = ctx.accent)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (!running) {
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            savedLabel = null
                            if (elapsedMs > 0) onResume() else onStart()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ctx.accent,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            stringResource(if (elapsedMs > 0) R.string.life_detail_timer_resume else R.string.life_detail_timer_start),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPause()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(
                            stringResource(R.string.life_detail_timer_pause),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                if (elapsedMs > 0) {
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSaveFocus(elapsedMs)
                            savedLabel = String.format(savedFmt, formatElapsed(elapsedMs))
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ctx.accent
                        )
                    ) {
                        Text(
                            stringResource(R.string.life_detail_timer_stop),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

/** 毫秒 → mm:ss（≥1h 时 h:mm:ss）。纯格式，不进资源。 */
private fun formatElapsed(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    // Locale.US：倒计时只含数字与冒号，锁 ASCII 数字（避免部分 locale 输出本地数字字形）
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}

// ───────────────────────── 共用小件 ─────────────────────────

/** 数据不足时的朴素英雄区：标题 + 身份色，**不编数字**。 */
@Composable
private fun PlainHero(ctx: DetailCtx) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
        Text(
            ctx.item.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(Spacing.xxs))
        // 内置模板的 name 列存的是建库时的中文，要走 getDisplayName（按 icon 取资源名），
        // 否则英文界面下这里会漏出中文。
        Text(ctx.template.getDisplayName(LocalContext.current), style = MaterialTheme.typography.labelSmall, color = ctx.accent)
    }
}

/** 勾选圆（§14.12(3) todo：r7.5；已勾 = 实心 + 对勾）。 */
@Composable
private fun CheckDot(done: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            // 视觉 15dp、触达扩到 48dp（TalkBack/手指都不再点空；审计 #27）
            .minimumInteractiveComponentSize()
            .size(15.dp)
            .clip(CircleShape)
            .background(if (done) Success else Color.Transparent)
            .border(1.5.dp, if (done) Success else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (done) Text("✓", style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

/** 人物头像圆（§14.12(3)：身份色 35% 混合填充）。 */
@Composable
private fun AvatarCircle(name: String, accent: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Text(name.take(1), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** 只读选项胶囊（详情页不给改选项，§14.12(8)）。 */
@Composable
fun ReadOnlyChip(text: String, accent: Color, selected: Boolean = true) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = Spacing.xxs)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
