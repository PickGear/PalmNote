package com.palmnote.ui.life

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.theme.ModuleLife
import com.palmnote.ui.theme.Spacing
import com.palmnote.ui.theme.TypeScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 月度/年度回顾页（Moo/格志/Wrapped 式叙事页）：总览 → 最常记录 → 心情分布。
 * 视觉遵循生活模块统一的二级页语言：背景色顶栏（模块色粗体标题）+ 扁平卡片（无渐变）；
 * 右上「分享」把页面渲染成 PNG 长图分享出去。
 */
@Composable
fun LifeMonthlyReviewScreen(
    onBack: () -> Unit,
    viewModel: LifeMonthlyReviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    val shareLabel = stringResource(R.string.life_review_share)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // 与完整列表 / 分类页 / 统计页同一套：背景色容器 + 模块色粗体标题
            // （此前是默认 surface 容器，顶栏是一条白带，和米色页身断开）
            SecondaryTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.life_review_title),
                        fontWeight = FontWeight.Bold,
                        color = ModuleLife
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.life_back)
                        )
                    }
                },
                actions = {
                    if (state.loaded && state.totalCount > 0) {
                        IconButton(onClick = {
                            scope.launch {
                                val result = runCatching {
                                    val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                                    val fileName = lifeShareFileName("review", System.currentTimeMillis())
                                    val file = withContext(Dispatchers.IO) { saveSharePng(context, bitmap, fileName) }
                                    sharePng(context, file)
                                }
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        if (result.isSuccess) R.string.life_review_shared else R.string.life_review_share_failed
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                shareLabel,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            ReviewModeSwitch(state.mode, viewModel::setMode)
            ReviewBody(state, graphicsLayer)
        }
    }
}

/**
 * 上月 / 去年 区间切换。
 *
 * 用房内的 [PillToggle]（月历「周 | 月」同款），不再用 Material 的 `SegmentedButton` ——
 * 后者带对勾 + 描边，与 App 其它分段控件不是一套语言（同一屏两种分段控件正是"不精致"）。
 * 与月历头部不同的是它是**整页级开关**，故放大一档（30dp 选中片）并与上下留出间距，
 * 不再像贴着顶栏的一枚小标签。
 */
@Composable
private fun ReviewModeSwitch(mode: ReviewMode, onModeChange: (ReviewMode) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        PillToggle(
            options = listOf(
                stringResource(R.string.life_review_mode_month),
                stringResource(R.string.life_review_mode_year)
            ),
            selectedIndex = if (mode == ReviewMode.LAST_MONTH) 0 else 1,
            onSelect = { onModeChange(if (it == 0) ReviewMode.LAST_MONTH else ReviewMode.LAST_YEAR) },
            optionHeight = 30.dp,
            optionHorizontalPadding = 14.dp,
            labelFontSize = TypeScale.labelL
        )
    }
}

/** 回顾主体：加载态 / 空态 / 内容（内容挂 graphicsLayer 供分享长图录制）。 */
@Composable
private fun ReviewBody(
    state: LifeMonthlyReviewViewModel.ReviewState,
    graphicsLayer: GraphicsLayer
) {
    val error = state.error
    when {
        // 读取失败：此前没有这一支，load 抛异常后页面永远停在转圈上。
        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        state.totalCount == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LifeEmptyState(
                icon = iconFor("calendar_month"),
                title = stringResource(R.string.life_review_empty),
                hint = stringResource(R.string.life_review_empty_hint)
            )
        }
        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md)
                    .recordInto(graphicsLayer)
            ) {
                Spacer(Modifier.height(Spacing.xs))
                OverviewCard(state)
                if (state.topTemplates.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.sm))
                    TopTemplatesCard(state.topTemplates)
                }
                if (state.moodCounts.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.sm))
                    MoodDistributionCard(state.moodCounts)
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

/**
 * 总览卡：**平涂粉彩**（向表面色 lerp，不用渐变——与统计页指标卡同一处理）
 * + 超大记录数，下挂活跃天数。
 */
@Composable
private fun OverviewCard(state: LifeMonthlyReviewViewModel.ReviewState) {
    val surface = MaterialTheme.colorScheme.surface
    val accent = ModuleLife
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = lerp(accent, surface, 0.90f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
            Text(
                state.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${state.totalCount}",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.life_review_records),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.life_review_active_days, state.activeDays, state.daysInPeriod),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 最常记录卡：区间内用得最多的 3 个模板（图标 + 名称 + 次数）。 */
@Composable
private fun TopTemplatesCard(templates: List<LifeMonthlyReviewViewModel.TopTemplate>) {
    ReviewSectionCard(stringResource(R.string.life_review_top)) {
        templates.forEach { tpl ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(tplColorOf(tpl.color).copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(iconFor(tpl.icon), null, tint = tplColorOf(tpl.color), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    tpl.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${tpl.count}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** 心情分布卡：emoji + 次数的横排胶囊（MOODA 式情绪可视化）。 */
@Composable
private fun MoodDistributionCard(moodCounts: List<Pair<String, Int>>) {
    ReviewSectionCard(stringResource(R.string.life_review_mood)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(moodCounts.size) { i ->
                val (mood, count) = moodCounts[i]
                val wash = lerp(
                    MoodVisuals.colorOf(mood) ?: MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.surface,
                    0.8f
                )
                Row(
                    modifier = Modifier
                        .background(wash, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(MoodVisuals.emojiOf(mood), fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "×$count",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewSectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

/** 模板色 hex → Color（脏数据回退主题色）。 */
private fun tplColorOf(hex: String): Color = runCatching {
    Color(hex.removePrefix("#").toLong(16) or 0xFF000000)
}.getOrDefault(Color.Gray)
