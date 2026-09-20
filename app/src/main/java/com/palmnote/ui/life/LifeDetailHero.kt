package com.palmnote.ui.life

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import coil3.compose.AsyncImage
import com.palmnote.app.R
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.model.parseChecklist
import com.palmnote.domain.model.parseMap
import com.palmnote.ui.theme.Spacing
import com.palmnote.ui.theme.Success
import com.palmnote.ui.theme.TypeScale
import com.palmnote.ui.theme.Warning
import java.util.Locale
import com.palmnote.ui.utils.LifeNumFormat
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
private data class HeroLabels(
    val gap: String,          // 还差 %1$s
    val achieved: String,     // 已达成
    val daysToCharge: String, // 还有 %1$d 天扣费
    val savedPct: String,     // 已存 %1$d%%
    val spentPct: String,     // 已花 %1$d%%
    val donePct: String,      // 已完成 %1$d%%
    val targetDays: String,   // 目标 %1$s 天
    val targetDate: String,   // 目标日 %1$s
    val nextBilling: String,  // 下次 %1$s
    val categories: String,   // %1$d 类
    val budgetLabel: String,  // 预算 %1$s
    val spentUnit: String,    // 已花
    val months: List<String>, // 月付 / 季付 / 年付
    val remaining: String,    // 还有
    val passed: String,       // 已经
    val overdue: String,      // 超期
    val yearsAndDays: String, // 第 %1$d 年 · 已经 %2$s 天
    val pagesRead: String,    // 读到 %1$s / %2$s 页
    val moodDateLine: String  // %1$s · 有记录
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
    overdue = stringResource(R.string.life_detail_word_overdue),
    yearsAndDays = stringResource(R.string.life_detail_years_and_days),
    pagesRead = stringResource(R.string.life_detail_pages_read),
    moodDateLine = stringResource(R.string.life_detail_mood_date_line)
)

@Composable
fun LifeHero(ctx: DetailCtx, onToggleChecklist: (String, Int) -> Unit, onSaveFocus: (Long) -> Unit = {}) {
    val labels = heroLabels()
    when (ctx.heroForm) {
        HeroForm.MONEY3 -> HeroMoney3(ctx, labels)
        HeroForm.DAYS -> HeroDays(ctx, labels)
        HeroForm.ROUTE -> HeroRoute(ctx)
        HeroForm.COVER -> HeroCover(ctx, labels)
        HeroForm.NOTE -> HeroNote(ctx)
        HeroForm.TODO -> HeroTodo(ctx, onToggleChecklist)
        HeroForm.MOOD -> HeroMood(ctx, labels)
        HeroForm.TIMER -> HeroTimer(ctx, onSaveFocus)
    }
}

/** 端标 chip（§14.12(3)：h22 / rx11；**只给缺口绝对值**，不给百分比）。 */
@Composable
fun HeroChip(text: String, accent: Color, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = Spacing.xxs)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = accent, maxLines = 1)
    }
}

// ───────────────────────── P1 · 三段式 ─────────────────────────

/** 左注 / 右注的语义（文案在 Composable 里成型，取数函数只给**数据**）。 */
private enum class NoteKind { NONE, SAVED_PCT, SPENT_PCT, DONE_PCT, TARGET_DAYS }

private enum class RightKind { NONE, TARGET_DATE, CATEGORIES, NEXT_BILLING }

private data class Money3Data(
    val label: String,
    val big: String,
    val unitText: String,
    /** 订阅的周期（monthly/quarterly/yearly）——需要本地化，故交给 Composable。 */
    val cycleRaw: String?,
    val fraction: Float?,
    val segments: Int,
    val leftKind: NoteKind,
    val leftPct: Int,
    val leftTargetDays: String,
    val rightKind: RightKind,
    val rightText: String,
    val rightCount: Int,
    /** 缺口（正数）或 null；chip 文案由 Composable 成型。 */
    val gapAmount: Double?,
    val achieved: Boolean
)

@Composable
private fun HeroMoney3(ctx: DetailCtx, L: HeroLabels) {
    val d = money3Data(ctx) ?: run { PlainHero(ctx); return }
    val chip = when {
        d.achieved -> L.achieved
        d.gapAmount != null -> String.format(L.gap, fmtNumber(d.gapAmount))
        ctx.template.icon == "subscriptions" -> ctx.cfg("daysToBilling")?.let { ctx.derived(it) }?.toLong()
            ?.let { String.format(L.daysToCharge, it) }.orEmpty()
        else -> ""
    }
    val unit = d.cycleRaw?.let { cycle ->
        val idx = listOf("monthly", "quarterly", "yearly").indexOf(cycle)
        if (idx >= 0) "/ " + L.months[idx] else d.unitText
    } ?: d.unitText
    val left = when (d.leftKind) {
        NoteKind.SAVED_PCT -> String.format(L.savedPct, d.leftPct)
        NoteKind.SPENT_PCT -> String.format(L.spentPct, d.leftPct)
        NoteKind.DONE_PCT -> String.format(L.donePct, d.leftPct)
        NoteKind.TARGET_DAYS -> String.format(L.targetDays, d.leftTargetDays)
        NoteKind.NONE -> ""
    }
    val right = when (d.rightKind) {
        RightKind.TARGET_DATE -> String.format(L.targetDate, d.rightText)
        RightKind.NEXT_BILLING -> String.format(L.nextBilling, d.rightText)
        RightKind.CATEGORIES -> String.format(L.categories, d.rightCount)
        RightKind.NONE -> ""
    }

    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                d.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            HeroChip(chip, ctx.accent)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                d.big,
                style = MaterialTheme.typography.headlineMedium,
                fontSize = TypeScale.displayM,
                fontWeight = FontWeight.Bold,
                color = ctx.accent
            )
            if (unit.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.xxs)
                )
            }
        }
        if (d.fraction != null) {
            Spacer(Modifier.height(10.dp))
            LifeTrackBar(fraction = d.fraction, color = ctx.accent, segments = d.segments)
            Spacer(Modifier.height(6.dp))
            Row {
                Text(left, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = ctx.accent)
                Spacer(Modifier.weight(1f))
                Text(right, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun money3Data(ctx: DetailCtx): Money3Data? = when (ctx.template.icon) {
    "savings" -> {
        val cur = ctx.num("currentAmount")
        val target = ctx.num("targetAmount")
        val p = ctx.progressOf(ctx.cfg("currentAmount"))
        money3Of(
            label = ctx.cfg("targetAmount")?.label.orEmpty(),
            big = cur, target = target, fraction = p?.fraction, monetary = true,
            leftKind = NoteKind.SAVED_PCT,
            rightKind = RightKind.TARGET_DATE, rightText = ctx.dateText("deadline").orEmpty()
        )
    }
    "shopping_cart" -> {
        val spent = ctx.num("spent")
        val budget = ctx.num("budget")
        val p = ctx.progressOf(ctx.cfg("spent"))
        money3Of(
            label = ctx.cfg("budget")?.label.orEmpty(),
            big = spent, target = budget, fraction = p?.fraction, monetary = true,
            unitText = ctx.cfg("spent")?.label.orEmpty(),
            leftKind = NoteKind.SPENT_PCT,
            rightKind = RightKind.CATEGORIES, rightCount = ctx.list("category").size
        )
    }
    "school" -> {
        val done = ctx.num("completedLessons")
        val total = ctx.num("totalLessons")
        val p = ctx.progressOf(ctx.cfg("completedLessons"))
        money3Of(
            label = ctx.cfg("completedLessons")?.label.orEmpty(),
            big = done, target = total, fraction = p?.fraction,
            unitText = total?.let { "/ ${fmtNumber(it)} ${ctx.cfg("totalLessons")?.unit.orEmpty()}".trimEnd() }.orEmpty(),
            leftKind = NoteKind.DONE_PCT,
            // §3.5.1 §四：可数语义用**段标记条**，比整条更可读（24 节 → 24 段封顶）
            segments = total?.toInt()?.coerceIn(2, 24) ?: 0
        )
    }
    "calendar_month" -> {
        val streak = ctx.num("currentStreak")
        val target = ctx.num("targetDays")
        val p = ctx.progressOf(ctx.cfg("currentStreak"))
        money3Of(
            label = ctx.cfg("currentStreak")?.label.orEmpty(),
            big = streak, target = target, fraction = p?.fraction,
            unitText = ctx.cfg("currentStreak")?.unit.orEmpty(),
            leftKind = NoteKind.TARGET_DAYS, leftTargetDays = target?.let { fmtNumber(it) }.orEmpty()
        )
    }
    "subscriptions" -> {
        val price = ctx.num("price")
        money3Of(
            label = ctx.cfg("price")?.label.orEmpty(),
            big = price, target = null, fraction = null, monetary = true,
            cycleRaw = ctx.str("billingCycle"),
            rightKind = RightKind.NEXT_BILLING, rightText = ctx.dateText("nextBilling").orEmpty()
        )
    }
    "fitness_center" -> {
        val weight = ctx.num("weight")
        money3Of(
            label = ctx.cfg("weight")?.label.orEmpty(),
            big = weight, target = null, fraction = null,
            unitText = ctx.cfg("weight")?.unit.orEmpty()
        )
    }
    else -> null
}

@Suppress("LongParameterList")
private fun money3Of(
    label: String,
    big: Double?,
    target: Double?,
    fraction: Float?,
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
        segments = segments,
        leftKind = leftKind,
        leftPct = ((fraction ?: 0f) * 100).toInt(),
        leftTargetDays = leftTargetDays,
        rightKind = rightKind,
        rightText = rightText,
        rightCount = rightCount,
        gapAmount = gap?.takeIf { it > 0 },
        achieved = gap != null && gap <= 0
    )
}

// ───────────────────────── P2 · 天数巨字 ─────────────────────────

/** 注脚标签词用 @StringRes（0 = 无）：本地化在 Composable 成型，数据层只给资源 id。 */
private data class DaysData(
    val days: Long?,
    val from: String,
    val to: String,
    val extra: String,
    val fromLabelRes: Int = 0,
    val toLabelRes: Int = 0
)

@Composable
private fun HeroDays(ctx: DetailCtx, L: HeroLabels) {
    val d = daysData(ctx)
    val person = ctx.str("person")
    val word = when {
        d.days == null -> ""
        d.days >= 0 -> L.remaining
        else -> L.passed
    }
    val extra = if (ctx.template.icon == "celebration") celebrationExtra(ctx, L) else ""
    // 注脚（dtl_07~10）：「今天 09-20」「起 2025-09-20」「周年 2026-11-04」—— 标签词 + 日期
    @Composable
    fun anchor(labelRes: Int, date: String): String =
        listOfNotNull(
            if (labelRes != 0) stringResource(labelRes) else null,
            date.takeIf { it.isNotBlank() }
        ).joinToString(" ")

    Column(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (person != null) {
            AvatarCircle(name = person, accent = ctx.accent)
            Spacer(Modifier.height(Spacing.xs))
        }
        Text(word, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        if (extra.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xxs))
            Text(extra, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = ctx.accent)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(anchor(d.fromLabelRes, d.from), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.xs)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            )
            Text(anchor(d.toLabelRes, d.to), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

private fun daysData(ctx: DetailCtx): DaysData = when (ctx.template.icon) {
    "timer_off" -> {
        val target = ctx.date("targetDate")
        val days = target?.let { ChronoUnit.DAYS.between(ctx.today, it) }
            ?: ctx.cfg("remainDays")?.let { ctx.derived(it) }?.toLong()
        DaysData(days, fmtDate(ctx.today).orEmpty(), fmtDate(target).orEmpty(), "",
            fromLabelRes = R.string.life_detail_word_today)
    }
    "trending_up" -> {
        val start = ctx.date("start_date")
        val passed = start?.let { ChronoUnit.DAYS.between(it, ctx.today) }
        DaysData(passed, fmtDate(start).orEmpty(), fmtDate(ctx.today).orEmpty(), "",
            fromLabelRes = R.string.life_detail_word_since,
            toLabelRes = R.string.life_detail_word_today)
    }
    "build" -> {
        // 物品维护：距下次更换 =（更换日期 + 周期）− 今天；算不出就不给天数。
        val bought = ctx.date("boughtAt")
        val cycle = ctx.cfg("cycle")?.let { ctx.num(it.key) }
        val next = if (bought != null && cycle != null) bought.plusDays(cycle.toLong()) else null
        val days = next?.let { ChronoUnit.DAYS.between(ctx.today, it) }
        DaysData(days, fmtDate(bought).orEmpty(), fmtDate(next).orEmpty(), "")
    }
    else -> {
        // 生日 / 纪念日：**按年复现**，算到下一个周年（与仪表盘纪念日卡同一口径）。
        val raw = ctx.date("date")
        val next = raw?.let { anniversaryNext(it, ctx.today) }
        val isCelebration = ctx.template.icon == "celebration"
        DaysData(
            next?.let { ChronoUnit.DAYS.between(ctx.today, it) },
            fmtDate(raw).orEmpty(), fmtDate(next).orEmpty(), "",
            fromLabelRes = if (isCelebration) R.string.life_detail_word_since else 0,
            toLabelRes = if (isCelebration) R.string.life_detail_word_anniversary else 0
        )
    }
}

/** 纪念日副行：「第 N 年 · 已经 M 天」（§14.11 #10：放副行，避免同屏两个竞争的大数字）。 */
@Composable
private fun celebrationExtra(ctx: DetailCtx, L: HeroLabels): String {
    val start = ctx.date("date") ?: return ""
    val days = ChronoUnit.DAYS.between(start, ctx.today)
    if (days < 0) return ""
    val years = (days / 365).toInt() + 1
    return String.format(L.yearsAndDays, years, fmtNumber(days.toDouble()))
}

private fun anniversaryNext(date: LocalDate, today: LocalDate): LocalDate {
    val thisYear = date.withYear(today.year)
    return if (thisYear >= today) thisYear else date.withYear(today.year + 1)
}

// ───────────────────────── P3 · 媒介型 ─────────────────────────

@Composable
private fun HeroRoute(ctx: DetailCtx) {
    val cfg = ctx.cfgByType(FieldType.MAP)
    val model = cfg?.let { parseMap(ctx.str(it.key)) }
    if (model == null || model.route.size < 2) {
        PlainHero(ctx)
        return
    }
    val days = ctx.date("startDate")?.let { ChronoUnit.DAYS.between(ctx.today, it) }
    val chip = when {
        days == null -> ""
        days >= 0 -> stringResource(R.string.dashboard_days_until, days.toInt())
        else -> stringResource(R.string.life_detail_on_the_way)
    }
    RouteBoard(model = model, accent = ctx.accent, chip = chip)
}

@Composable
private fun HeroCover(ctx: DetailCtx, L: HeroLabels) {
    val cover = ctx.cfgByType(FieldType.IMAGE)?.let { ctx.str(it.key) }
    val p = ctx.progressOf(ctx.configs.firstOrNull { it.showAsProgress })
    // 磁盘 IO 只在封面路径变化时做一次，不跟重组走
    val coverExists = remember(cover) { cover != null && File(cover).exists() }
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
            }
            val cur = ctx.num("currentPage")
            val total = ctx.num("totalPages")
            if (cur != null && total != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    String.format(L.pagesRead, fmtNumber(cur), fmtNumber(total)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
                            color = if (i < 2) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
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
                stringResource(R.string.life_hero_completed, done, rows.size),
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
        rows.take(5).forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth().height(30.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CheckDot(done = row.done) { cfg?.let { onToggle(it.key, index) } }
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
private fun HeroTimer(ctx: DetailCtx, onSaveFocus: (Long) -> Unit) {
    var running by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var savedLabel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val start = System.currentTimeMillis() - elapsedMs
        while (running) {
            delay(200)
            elapsedMs = System.currentTimeMillis() - start
        }
    }
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
                        onClick = { running = true; savedLabel = null },
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
                        onClick = { running = false },
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
                            running = false
                            onSaveFocus(elapsedMs)
                            savedLabel = String.format(savedFmt, formatElapsed(elapsedMs))
                            elapsedMs = 0
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
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

// ───────────────────────── 共用小件 ─────────────────────────

/**
 * 格子卡摘要：**与详情页英雄区同一口径**（同一批字段、同一个 [DetailCtx]）。
 *
 * 只给**数据**（数值 / 天数 / 计数），文案由卡片在 Composable 里成型 —— 保证中英对等。
 */
fun DetailCtx.cardSummary(): CardSummary = when (heroForm) {
    HeroForm.MONEY3 -> {
        val d = money3Data(this)
        if (d == null) plainSummary(this) else CardSummary(
            kind = if (d.fraction != null) BoardCardKind.PROGRESS else BoardCardKind.NUMBER,
            value = d.big,
            label = d.label,
            fraction = d.fraction,
            // 状态色只表达状态：**超预算**才转 Warning（§14.3）；储蓄达标中性呈现
            warning = template.icon == "shopping_cart" && d.achieved
        )
    }
    HeroForm.DAYS -> {
        val d = daysData(this)
        CardSummary(
            kind = BoardCardKind.NUMBER,
            value = d.days?.let { kotlin.math.abs(it).toString() }.orEmpty(),
            label = item.title,
            fraction = null,
            warning = false,
            days = d.days
        )
    }
    HeroForm.TODO -> {
        val cfg = cfgByType(FieldType.CHECKLIST)
        val rows = cfg?.let { parseChecklist(raw(it.key)) }.orEmpty()
        CardSummary(
            kind = BoardCardKind.TODO,
            value = "",
            label = item.title,
            fraction = null,
            warning = false,
            doneCount = rows.count { it.done },
            totalCount = rows.size
        )
    }
    else -> plainSummary(this)
}

private fun plainSummary(ctx: DetailCtx): CardSummary = CardSummary(
    kind = BoardCardKind.TEXT,
    value = ctx.item.title,
    label = ctx.template.name,
    fraction = null,
    warning = false
)

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
        Text(ctx.template.name, style = MaterialTheme.typography.labelSmall, color = ctx.accent)
    }
}

/** 勾选圆（§14.12(3) todo：r7.5；已勾 = 实心 + 对勾）。 */
@Composable
private fun CheckDot(done: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
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
