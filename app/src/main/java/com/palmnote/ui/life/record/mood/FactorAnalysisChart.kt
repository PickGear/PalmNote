package com.palmnote.ui.life.record.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.R
import com.palmnote.data.db.entity.MoodDiary
import kotlinx.serialization.json.*

@Composable
fun FactorAnalysisChart(diaries: List<MoodDiary>) {
    val json = remember { Json { ignoreUnknownKeys = true } }
    val grouped = diaries.flatMap { d ->
        val fs = try { json.decodeFromString<JsonArray>(d.factors).map { (it as JsonPrimitive).content } } catch (_: Exception) { emptyList() }
        fs.map { it to d.mood }
    }.groupBy { it.first }
    if (grouped.isEmpty()) return
    val sorted = grouped.entries.sortedByDescending { it.value.size }.take(8)
    val maxCount = sorted.maxOf { it.value.size }.coerceAtLeast(1)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.life_factor_analysis), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                sorted.forEach { (factor, entries) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(factor, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(50.dp))
                        Box(modifier = Modifier.weight(1f).height(4.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(2.dp))) {
                            Box(modifier = Modifier.fillMaxWidth(fraction = entries.size.toFloat() / maxCount).height(4.dp).background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(2.dp)))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.life_factor_analysis_count, entries.size), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
