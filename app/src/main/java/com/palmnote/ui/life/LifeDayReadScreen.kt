package com.palmnote.ui.life

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import androidx.compose.ui.res.stringResource
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.theme.Spacing
import com.palmnote.ui.theme.BigCardShape
import com.palmnote.ui.theme.ListCardShape
import com.palmnote.ui.theme.ModuleLife
import com.palmnote.ui.theme.TypeScale
import java.time.LocalDate

/**
 * 当日回读（只读）——点月格某天后进入（§五）。
 * 数据来自数据库，按天取该日全部条目（口径 = COALESCE(dueDate, createdAt)，与月历一致）；
 * 示例条目带「示例」徽标，关闭演示模式后自动消失。
 */
@Composable
fun LifeDayReadScreen(
    dateKey: String,
    onBack: () -> Unit,
    vm: LifeDayReadViewModel = hiltViewModel()
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val date = runCatching { LocalDate.parse(dateKey) }.getOrNull() ?: LocalDate.now()
    val label = stringResource(R.string.life_dayread_title, date.monthValue, date.dayOfMonth)
    val weekday = stringResource(weekdayShortRes(date.dayOfWeek))

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SecondaryTopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        weekday,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (date == LocalDate.now()) {
                        // 看的是「今天」：身份色小标给一点情感反馈
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = ModuleLife.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(7.dp)
                        ) {
                            Text(
                                stringResource(R.string.life_detail_word_today),
                                fontSize = TypeScale.labelS,
                                fontWeight = FontWeight.SemiBold,
                                color = ModuleLife,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
        Spacer(Modifier.height(Spacing.xs))
        Column(modifier = Modifier.padding(horizontal = Spacing.md)) {
            val total = items.size
            Text(
                if (total == 0) stringResource(R.string.life_dayread_empty) else stringResource(R.string.life_dayread_count, total),
                fontSize = TypeScale.bodyM, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.sm))
            if (total == 0) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = BigCardShape,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)
                ) {
                    Text(
                        stringResource(R.string.life_dayread_empty_hint),
                        fontSize = TypeScale.bodyM, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp).fillMaxWidth(), textAlign = TextAlign.Center
                    )
                }
            } else {
                items.forEach { row ->
                    val accent = runCatching { Color(android.graphics.Color.parseColor(row.color)) }.getOrNull() ?: ModuleLife
                    val done = row.status == "COMPLETED"
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = BigCardShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)
                    ) {
                        Row(modifier = Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = accent.copy(alpha = 0.12f), shape = ListCardShape, modifier = Modifier.size(42.dp)) {
                                Icon(iconFor(row.icon), null, tint = accent, modifier = Modifier.size(20.dp).wrapContentSize())
                            }
                            Spacer(Modifier.width(Spacing.sm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    row.title, fontSize = TypeScale.bodyM, fontWeight = FontWeight.SemiBold,
                                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (done) TextDecoration.LineThrough else null
                                )
                                // 时刻只能来自 dueTime（距零点分钟数）。此前拿 effective 的本地时间凑，
                                // 而 dated 记录的 effective 是当天零点 —— 恒显示 00:00，即使模板填了
                                // TIME 字段也照样是 00:00。没有时间语义就整行不渲染，不显示假时刻。
                                val time = row.dueTime?.let {
                                    String.format(java.util.Locale.US, "%02d:%02d", it / 60, it % 60)
                                }
                                val meta = listOfNotNull(
                                    stringResource(R.string.life_demo_badge).takeIf { row.isDemo },
                                    time
                                ).joinToString(" · ")
                                if (meta.isNotBlank()) {
                                    Text(
                                        meta,
                                        fontSize = TypeScale.labelM, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (row.isDemo) {
                                Spacer(Modifier.width(Spacing.xs))
                                Surface(color = ModuleLife.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                                    Text(stringResource(R.string.life_demo_badge), fontSize = TypeScale.labelS, color = ModuleLife, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

