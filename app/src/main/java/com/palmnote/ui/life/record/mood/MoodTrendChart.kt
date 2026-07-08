package com.palmnote.ui.life.record.mood

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.data.db.entity.MoodDiary
import com.palmnote.ui.theme.*
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun MoodTrendChart(diaries: List<MoodDiary>) {
    if (diaries.isEmpty()) return
    val months = (0L..5L).map { YearMonth.now().minusMonths(it) }.reversed()
    val moodColors = mapOf("HAPPY" to LifeMoodHappy, "GOOD" to LifeMoodNormal, "NORMAL" to LifeMoodUpset, "SAD" to LifeMoodSad, "ANGRY" to LifeMoodAngry)
    val moodEmojis = mapOf("HAPPY" to "\uD83D\uDE04", "GOOD" to "\uD83D\uDE42", "NORMAL" to "\uD83D\uDE14", "SAD" to "\uD83D\uDE22", "ANGRY" to "\uD83D\uDE21")

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("\u6708\u5EA6\u5FC3\u60C5\u5206\u5E03", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                val counts = months.map { ym ->
                    val entries = diaries.filter { YearMonth.from(Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate()) == ym }
                    ym to entries.groupBy { it.mood }.mapValues { it.value.size }
                }
                val maxCount = counts.flatMap { it.second.values }.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f

                Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                    val barW = size.width / months.size * 0.6f
                    val gap = size.width / months.size * 0.4f
                    counts.forEachIndexed { i, (ym, moodCounts) ->
                        val x = i * (barW + gap) + gap / 2
                        val mostCommon = moodCounts.maxByOrNull { it.value }?.key ?: ""
                        val pct = (moodCounts.values.sum().toFloat() / maxCount).coerceIn(0.05f, 1f)
                        val color = moodColors[mostCommon] ?: LifeMoodNormal
                        drawRoundRect(color = color, topLeft = Offset(x, size.height - pct * 80f - 20f), size = Size(barW, pct * 80f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f))
                        drawContext.canvas.nativeCanvas.drawText(moodEmojis[mostCommon] ?: "\uD83D\uDE04", x + barW / 2, size.height - 4f, android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.CENTER; textSize = 24f })
                    }
                }
            }
        }
    }
}
