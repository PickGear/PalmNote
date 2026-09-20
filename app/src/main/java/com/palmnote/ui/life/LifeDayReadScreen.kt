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
 * 当日回读（只读）——点月格某天后进入（§五）。
 * 第一阶段为示例静态内容。
 */
@Composable
fun LifeDayReadScreen(dayLabel: String, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(dayLabel, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("这天记了 5 条", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            val items = listOf(
                Triple("晨跑 5.2 km", "身体记录", "#00897B"),
                Triple("读书笔记 第 42 页", "远方与成长", "#26A69A"),
                Triple("买菜 ¥86", "记账", "#FF7043"),
                Triple("给妈妈打电话", "待办", "#5C6BC0"),
                Triple("心情不错", "心情", "#FFA726")
            )
            items.forEach { (name, sub, hex) ->
                val accent = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull() ?: ModuleLife
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = accent.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(42.dp)) {
                            Icon(Icons.Filled.Circle, null, tint = accent, modifier = Modifier.size(20.dp).wrapContentSize())
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Surface(
                color = ModuleLife,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { }
            ) {
                Text(
                    "在这天补记一条",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
