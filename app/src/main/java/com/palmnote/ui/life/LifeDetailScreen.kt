package com.palmnote.ui.life

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.ui.theme.ModuleLife

/**
 * 详情页静态骨架（对齐 21 张详情 SVG 四段骨架：英雄区 / 主指标 / 结构区 / 时间关联）。
 * 第一阶段不含业务逻辑，字段与数值均为示例。
 */
@Composable
fun LifeDetailScreen(
    title: String,
    iconKey: String,
    accentHex: String,
    heroLabel: String,
    heroValue: String,
    onBack: () -> Unit
) {
    val accent = runCatching { Color(android.graphics.Color.parseColor(accentHex)) }.getOrNull() ?: ModuleLife
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        // 顶栏（返回）
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // 英雄区
            Surface(
                color = accent.copy(alpha = 0.10f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = accent.copy(alpha = 0.18f), shape = RoundedCornerShape(14.dp), modifier = Modifier.size(52.dp)) {
                        Icon(iconFor(iconKey), null, tint = accent, modifier = Modifier.size(26.dp).wrapContentSize())
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        if (heroLabel.isNotEmpty()) {
                            Text(heroLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // 主指标
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("主指标", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    if (heroValue.isNotEmpty()) {
                        Text(heroValue, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = accent)
                    } else {
                        Text("—", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { 0.68f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = accent,
                        trackColor = accent.copy(alpha = 0.15f)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // 结构区
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("结构区", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    listOf("字段一" to "示例值", "字段二" to "示例值", "字段三" to "示例值").forEach { (k, v) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(k, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text(v, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // 时间关联
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("创建于 今天 14:30", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
