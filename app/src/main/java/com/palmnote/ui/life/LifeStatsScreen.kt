package com.palmnote.ui.life

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.app.R
import com.palmnote.ui.theme.LifePlan
import com.palmnote.ui.theme.LifeRecord
import com.palmnote.ui.theme.LifeTime
import com.palmnote.ui.theme.ModuleLife

/**
 * 生活统计页静态骨架（对齐旧版 LifeStatsScreen 的视觉结构：4 张指标卡 + 近 7 天看板 + 近 16 周热力）。
 * 第一阶段不含业务逻辑 / 后端，数值与热力均为示例。
 */
@Composable
fun LifeStatsScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back))
            }
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.life_home_stats),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ModuleLife
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatsCard(stringResource(R.string.life_stats_today_todo), "3", Icons.Filled.Checklist, LifePlan, Modifier.weight(1f))
            StatsCard(stringResource(R.string.life_stats_habit_rate), stringResource(R.string.life_stats_percent, 80), Icons.Filled.LocalFireDepartment, LifeRecord, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatsCard(stringResource(R.string.life_stats_focus_minutes), "45", Icons.Filled.Timer, LifeTime, Modifier.weight(1f))
            StatsCard(stringResource(R.string.life_stats_max_streak), stringResource(R.string.life_stats_days, 21), Icons.Filled.LocalFireDepartment, LifePlan, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        WeekChart()
        Spacer(Modifier.height(12.dp))
        HeatCard()
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StatsCard(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier.size(28.dp).background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** 近 7 天看板（静态示例柱形）。 */
@Composable
private fun WeekChart() {
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    val values = listOf(2, 5, 3, 6, 4, 1, 3)
    val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.life_stats_week_chart_title), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ModuleLife)
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(96.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                labels.forEachIndexed { i, d ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((values[i].toFloat() / max * 72).dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(ModuleLife.copy(alpha = 0.8f))
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(d, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** 近 16 周记录热力（静态示例，颜色取生活模块主色，按强度分级透明度）。 */
@Composable
private fun HeatCard() {
    val sample = listOf(
        0, 1, 3, 2, 4, 1, 0,
        2, 2, 4, 3, 1, 0, 1,
        3, 4, 2, 2, 0, 1, 3,
        1, 0, 2, 4, 3, 2, 1,
        4, 3, 1, 0, 2, 3, 4,
        1, 2, 2, 1, 0, 3, 4
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.life_stats_heat_title), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ModuleLife)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                sample.chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { v ->
                            val alpha = when (v) {
                                0 -> 0.08f
                                1 -> 0.25f
                                2 -> 0.45f
                                3 -> 0.7f
                                else -> 1f
                            }
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(ModuleLife.copy(alpha = alpha))
                            )
                        }
                    }
                }
            }
        }
    }
}
