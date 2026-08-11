package com.palmnote.ui.life.record.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.app.R
import com.palmnote.data.db.entity.MoodDiary
import com.palmnote.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun MoodCalendarView(diaries: List<MoodDiary>, month: LocalDate = LocalDate.now()) {
    var currentMonth by remember { mutableStateOf(month) }
    val ym = YearMonth.from(currentMonth)
    val fow = (ym.atDay(1).dayOfWeek.value % 7)
    val dim = ym.lengthOfMonth()
    val map = diaries.groupBy { Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate() }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.date_format_display_month).format(currentMonth.year, currentMonth.monthValue), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Row {
                Text("\u276E", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp).clickable { currentMonth = currentMonth.minusMonths(1) })
                Spacer(modifier = Modifier.width(4.dp))
                Text("\u276F", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp).clickable { currentMonth = currentMonth.plusMonths(1) })
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) { listOf(stringResource(R.string.date_weekday_short_mon), stringResource(R.string.date_weekday_short_tue), stringResource(R.string.date_weekday_short_wed), stringResource(R.string.date_weekday_short_thu), stringResource(R.string.date_weekday_short_fri), stringResource(R.string.date_weekday_short_sat), stringResource(R.string.date_weekday_short_sun)).forEach { Text(it, fontSize = 10.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)) } }
        Spacer(modifier = Modifier.height(2.dp))
        var day = 1; var row = 0
        while (day <= dim) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    if (row == 0 && col < fow) { Box(modifier = Modifier.weight(1f).aspectRatio(1f)) }
                    else if (day <= dim) {
                        val d = ym.atDay(day)
                        val mood = map[d]?.firstOrNull()
                        val mc = when (mood?.mood) { "HAPPY" -> LifeMoodHappy; "GOOD" -> LifeMoodNormal; "NORMAL" -> LifeMoodUpset; "SAD" -> LifeMoodSad; "ANGRY" -> LifeMoodAngry; else -> null }
                        val emoji = when (mood?.mood) { "HAPPY" -> "\uD83D\uDE04"; "GOOD" -> "\uD83D\uDE42"; "NORMAL" -> "\uD83D\uDE14"; "SAD" -> "\uD83D\uDE22"; "ANGRY" -> "\uD83D\uDE21"; else -> null }
                        val isToday = d == LocalDate.now()
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                            Box(modifier = Modifier.size(if (isToday) 40.dp else 36.dp).then(if (mc != null) Modifier.background(mc.copy(alpha = 0.12f), if (isToday) CircleShape else RoundedCornerShape(8.dp)) else Modifier).then(if (isToday && mc == null) Modifier.background(LifeMoodColor.copy(alpha = 0.3f), CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                                if (emoji != null) Text(emoji, fontSize = 14.sp) else Text("$day", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        day++
                    }
                }
            }; row++
        }
    }
}
