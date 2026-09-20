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
 * 通用列表页静态骨架（供习惯 / 心情 / 纪念日 / 计划等子页复用）。
 * 第一阶段为示例数据，不含业务逻辑。
 */
@Composable
fun LifeListScreen(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
            }
            Column {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            val samples = listOf(
                Triple("示例条目一", "副标题说明", "#EC407A"),
                Triple("示例条目二", "副标题说明", "#3F51B5"),
                Triple("示例条目三", "副标题说明", "#FFA726"),
                Triple("示例条目四", "副标题说明", "#00ACC1"),
                Triple("示例条目五", "副标题说明", "#66BB6A")
            )
            samples.forEach { (name, sub, hex) ->
                val accent = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull() ?: ModuleLife
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp)).clickable { }
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
                        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
