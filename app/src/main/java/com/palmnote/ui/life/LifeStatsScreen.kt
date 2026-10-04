package com.palmnote.ui.life

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.domain.util.BuiltinTemplates
import com.palmnote.ui.components.ModuleCard
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.theme.LifePlan
import com.palmnote.ui.theme.LifeRecord
import com.palmnote.ui.theme.LifeTime
import com.palmnote.ui.theme.ModuleLife
import com.palmnote.ui.theme.Spacing
import com.palmnote.ui.theme.TypeScale
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * 生活统计页（4 张指标卡 + 近 7 天看板 + 近 16 周热力）。
 *
 * 四项指标与两张图**全部来自真实聚合**（[LifeStatsViewModel]），无示例值：
 * 今日待办 / 习惯完成率 / 今日专注 / 最长连胜；近 7 天看板按「每日记录条数」出柱，
 * 热力按 16 周 × 7 天的记录密度分深浅。演示感知（互斥）由 DAO 查询端处理。
 *
 * 视觉遵循全 App 统一的二级页语言：背景色顶栏（标题走模块色）+ 扁平卡片（细描边、无投影）。
 * 指标卡是**平涂粉彩**（向表面色 lerp，不用渐变），四张卡结构完全一致 —— 图标行 + 巨值一行，
 * 所以高度天然相等，不会出现「有的卡高有的卡矮」。
 */
@Composable
fun LifeStatsScreen(
    onBack: () -> Unit,
    onOpenMonthlyReview: () -> Unit = {},
    viewModel: LifeStatsViewModel = hiltViewModel()
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            // 与完整列表/分类页同一套：背景色容器 + 模块色粗体标题
            SecondaryTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.life_home_stats),
                        fontWeight = FontWeight.Bold,
                        color = ModuleLife
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (!ui.loaded) {
            // 首批聚合未到：占位中转，避免全 0 默认值闪一帧假「无数据」
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ModuleLife)
            }
            return@Scaffold
        }
        if (ui.heatWeeks.all { it.sum() == 0 } && ui.todoToday == 0) {
            // 全零 = 还没有任何生活记录：给引导而不是一排 0
            LifeEmptyState(
                icon = Icons.Filled.Timer,
                title = stringResource(R.string.life_stats_empty_title),
                hint = stringResource(R.string.life_stats_empty_hint),
                modifier = Modifier.fillMaxWidth().padding(innerPadding).padding(top = 120.dp)
            )
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
        ) {
            Spacer(Modifier.height(Spacing.xs))
            MetricsGrid(ui)
            // 月度回顾入口（叙事页）：上月总结在回顾页，本页仍是进行时仪表。
            MonthlyReviewEntryCard(onOpen = onOpenMonthlyReview)
            Spacer(Modifier.height(Spacing.sm))
            // 报告已从模板退役到本页（v1.27）：这块即设计稿 dtl#15 的「本期变化」。
            CompareCard(ui)
            Spacer(Modifier.height(Spacing.sm))
            // ③ 顺序沿用设计稿 dtl#15：**分布（堆叠占比条）→ 趋势（每日条数）**。
            CategoryShareCard(ui.categoryShare)
            Spacer(Modifier.height(Spacing.sm))
            // 近 7 天看板 = 16 周网格的最后一列（本周，周一起始）。
            WeekChart(ui.heatWeeks.last())
            Spacer(Modifier.height(Spacing.sm))
            HeatCard(ui.heatWeeks)
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/** 四张指标卡（2×2）：结构一致、天然等高；独立成函数也让主函数保持在 detekt 的 60 行内。 */
@Composable
private fun MetricsGrid(ui: LifeStatsUi) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
        StatsCard(
            stringResource(R.string.life_stats_today_todo),
            ui.todoToday.toString(),
            Icons.Filled.Checklist, LifePlan, Modifier.weight(1f)
        )
        StatsCard(
            stringResource(R.string.life_stats_habit_rate),
            stringResource(R.string.life_stats_percent, ui.habitRatePercent),
            Icons.Filled.LocalFireDepartment, LifeRecord, Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(Spacing.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
        StatsCard(
            stringResource(R.string.life_stats_focus_minutes),
            stringResource(R.string.life_stats_minutes, ui.focusMinutesToday),
            Icons.Filled.Timer, LifeTime, Modifier.weight(1f)
        )
        StatsCard(
            stringResource(R.string.life_stats_max_streak),
            pluralStringResource(R.plurals.life_stats_days, ui.maxStreak, ui.maxStreak),
            Icons.Filled.LocalFireDepartment, LifePlan, Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(Spacing.md))
}

/**
 * 指标卡：**平涂粉彩**卡（领域色向表面色 lerp，暗色主题下同样干净）+ 图标行 + 巨值。
 *
 * 不用渐变 —— 渐变在四张卡并排时会产生「每张卡各自一条斜向色带」的噪声，
 * 与全 App 的扁平卡片语言也不一致。四张卡结构与行数严格一致，高度天然相同。
 */
@Composable
private fun StatsCard(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.surface
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(lerp(color, surface, 0.90f))
            .padding(Spacing.sm)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
            }
            Text(
                title,
                fontSize = TypeScale.bodyS,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            value,
            fontSize = TypeScale.displayS,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1
        )
    }
}

/** 卡片小标题：模块色 + 半粗，本页所有分析卡统一用它。 */
@Composable
private fun CardTitle(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = TypeScale.bodyM, color = ModuleLife)
}

/**
 * 「与上期对比」（设计稿 dtl#15 的「本期变化」表格）：本周 vs 上周。
 *
 * 报告于 v1.27 从模板退役到统计页，这一块就是它的落地。只列**真实算得出**的两项 ——
 * 设计稿原文的「支出」在生活模块并不存在（记账是独立模块），**不编造**。
 */
@Composable
private fun CompareCard(ui: LifeStatsUi) {
    ModuleCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.life_stats_compare_title))
        Spacer(Modifier.height(Spacing.sm))
        CompareRow(
            label = stringResource(R.string.life_stats_records),
            valueText = stringResource(R.string.life_stats_records_count, ui.recordsThisWeek),
            delta = ui.recordsThisWeek - ui.recordsPrevWeek,
            asPercent = false
        )
        Spacer(Modifier.height(Spacing.xs))
        CompareRow(
            label = stringResource(R.string.life_stats_habit_rate),
            valueText = stringResource(R.string.life_stats_percent, ui.habitRatePercent),
            delta = ui.habitRatePercent - ui.habitRatePrevPercent,
            asPercent = true
        )
    }
}

/** 一行对比：标签 + 本期值 + 变化量（↑/↓ + 绝对值；两期相同显示「—」）。 */
@Composable
private fun CompareRow(label: String, valueText: String, delta: Int, asPercent: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = TypeScale.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(valueText, fontSize = TypeScale.bodyS, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(Spacing.sm))
        // 变化量**不上红/绿**：记录条数与习惯率都不是「涨跌」，套金融配色会被读错（§11.1 语义色规则）。
        Text(
            text = when {
                delta > 0 -> "↑ " + deltaText(delta, asPercent)
                delta < 0 -> "↓ " + deltaText(-delta, asPercent)
                else -> "—"
            },
            fontSize = TypeScale.bodyS,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 变化量绝对值 → 文本（条数走「N 条」，完成率走「N%」）。 */
@Composable
private fun deltaText(magnitude: Int, asPercent: Boolean): String = if (asPercent) {
    stringResource(R.string.life_stats_percent, magnitude)
} else {
    stringResource(R.string.life_stats_records_count, magnitude)
}

/**
 * 本周记录分布：**堆叠占比条** + 图例（§3.5.1 共用语言；设计稿 dtl#15「分布 · 堆叠占比条」）。
 *
 * 分类取自模板自身的 `category`（计划 / 时间 / 记录，用户自建分类会落到模块色）。
 * 本周无记录时**整卡不出现**（不画一条空的占位条）。
 */
@Composable
private fun CategoryShareCard(shares: List<LifeCategoryShare>) {
    if (shares.isEmpty()) return
    ModuleCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.life_stats_category_share))
        Spacer(Modifier.height(Spacing.sm))
        // 堆叠条：按条数分权重（weight 必须 > 0，故保底 0.01f）。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(6.dp))
        ) {
            shares.forEach { s ->
                Box(
                    modifier = Modifier
                        .weight(s.count.coerceAtLeast(1).toFloat())
                        .fillMaxHeight()
                        .background(categoryColor(s.name))
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        shares.forEach { s ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 色取原始分类键（categoryColor 认的是 DB 值），文案走本地化映射
                Box(Modifier.size(Spacing.xs).clip(CircleShape).background(categoryColor(s.name)))
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    categoryChipLabel(s.name),
                    fontSize = TypeScale.bodyS,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.life_stats_percent, s.percent),
                    fontSize = TypeScale.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 模板分类 → 身份色（复用 `Color.kt` 既有三个预置分类色，**不新增字面量**）。 */
private fun categoryColor(name: String): Color = when (name) {
    BuiltinTemplates.PLAN_CATEGORY -> LifePlan
    BuiltinTemplates.TIME_CATEGORY -> LifeTime
    BuiltinTemplates.RECORD_CATEGORY -> LifeRecord
    else -> ModuleLife
}

/** 近 7 天看板：柱高 = 当日记录条数。**0 条不画柱**（不留一根「凑数的小柱」），只保留点位。 */
@Composable
private fun WeekChart(bars: List<Int>) {
    val max = (bars.maxOrNull() ?: 0).coerceAtLeast(1)
    val labels = remember { weekdayLabels() }
    ModuleCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.life_stats_week_chart_title))
        Spacer(Modifier.height(Spacing.md))
        Row(
            modifier = Modifier.fillMaxWidth().height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.Bottom
        ) {
            labels.forEachIndexed { i, d ->
                val v = bars.getOrElse(i) { 0 }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barHeight(v, max))
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (v > 0) ModuleLife.copy(alpha = 0.8f) else Color.Transparent)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(d, fontSize = TypeScale.labelM, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 柱高：按当日条数与本周峰值等比，有数据时至少 3dp（否则 1 条会被画成一条看不见的线）。 */
private fun barHeight(value: Int, max: Int) = if (value <= 0) 0.dp else (value.toFloat() / max * 72).dp.coerceAtLeast(3.dp)

/** 星期窄名（本地化）：中文「一二三…日」/ 英文「M T W…」，无需新增字串资源。 */
private fun weekdayLabels(): List<String> {
    val locale = Locale.getDefault()
    return (1..7).map { DayOfWeek.of(it).getDisplayName(TextStyle.NARROW, locale) }
}

/**
 * 近 16 周记录热力：外层 16 列（周，最右＝本周）、内层 7 天（周一…周日）。
 *
 * 格子用 `weight + aspectRatio` 自适应卡片宽度（**不写死尺寸**）——16 列 × 14dp 在 320dp 旧机上会溢出，
 * 按权重分配后任意屏宽都不越界。强度分级沿用原视觉（4 档 + 空档）。
 */
@Composable
private fun HeatCard(weeks: List<List<Int>>) {
    val max = (weeks.flatten().maxOrNull() ?: 0).coerceAtLeast(1)
    ModuleCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.life_stats_heat_title))
        Spacer(Modifier.height(Spacing.sm))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            weeks.forEach { week ->
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    week.forEach { v ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(3.dp))
                                .background(ModuleLife.copy(alpha = heatAlpha(v, max)))
                        )
                    }
                }
            }
        }
    }
}

/** 热力强度 → 透明度（§14.12：热力按模板语义取深浅，这里是全生活记录密度）。 */
private fun heatAlpha(value: Int, max: Int): Float = when {
    value <= 0 -> 0.08f
    else -> {
        val ratio = value.toFloat() / max
        when {
            ratio <= 0.25f -> 0.25f
            ratio <= 0.5f -> 0.45f
            ratio <= 0.75f -> 0.7f
            else -> 1f
        }
    }
}

/** 月度回顾入口卡：**平涂粉彩** + 「上月回顾」，点击进回顾页（同样不用渐变）。 */
@Composable
private fun MonthlyReviewEntryCard(onOpen: () -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(lerp(ModuleLife, surface, 0.90f))
            .clickable(onClick = onOpen)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.life_review_entry),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.life_review_entry_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
