package com.palmnote.ui.life

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.ui.theme.Spacing
import com.palmnote.ui.theme.BigCardShape
import com.palmnote.ui.theme.ListCardShape
import com.palmnote.ui.theme.ModuleLife
import com.palmnote.ui.theme.TypeScale
import com.palmnote.ui.utils.LifeNumFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    val zone = ZoneId.systemDefault()
    val date = runCatching { LocalDate.parse(dateKey) }.getOrNull() ?: LocalDate.now()
    val label = "${date.monthValue}月${date.dayOfMonth}日"
    val weekday = date.dayOfWeek.toChinese()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(label, fontSize = TypeScale.titleM, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(Spacing.xs))
            Text(weekday, fontSize = TypeScale.bodyM, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(modifier = Modifier.padding(horizontal = Spacing.md)) {
            val total = items.size
            Text(
                if (total == 0) "这天还没有记录" else "这天记了 ${LifeNumFormat.num(total)} 条",
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
                        "点上面的格子选别的日期，或回去记一条",
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
                                val time = Instant.ofEpochMilli(row.effective).atZone(zone).toLocalTime()
                                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                                Text(
                                    if (row.isDemo) "$time · 示例" else time,
                                    fontSize = TypeScale.labelM, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (row.isDemo) {
                                Spacer(Modifier.width(Spacing.xs))
                                Surface(color = ModuleLife.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                                    Text("示例", fontSize = TypeScale.labelS, color = ModuleLife, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Surface(
                color = ModuleLife,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { }
            ) {
                Text(
                    "在这天补记一条",
                    fontSize = TypeScale.bodyM, fontWeight = FontWeight.Medium, color = Color.White,
                    modifier = Modifier.padding(vertical = Spacing.sm).fillMaxWidth(), textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

private fun DayOfWeek.toChinese(): String = when (this) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    else -> "周日"
}
