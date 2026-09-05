package com.palmnote.ui.life.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.app.R
import com.palmnote.ui.theme.ModuleLife
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * 折叠周历 / 展开月历。
 * 折叠态:当周 7 日横条,左右滑动切周,点按展开。
 * 展开态:完整月历,滑动切月,点日选中;有任务日打点 / 今日实心 / 选中描边。
 */
@Composable
fun WeeklyCalendar(
    selectedDate: LocalDate,
    expanded: Boolean,
    markedDates: Set<LocalDate>,
    onSelectDate: (LocalDate) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().animateContentSize()) {
        if (expanded) {
            MonthCalendar(
                selectedDate = selectedDate,
                markedDates = markedDates,
                onSelectDate = onSelectDate,
                onCollapse = { onExpandedChange(false) }
            )
        } else {
            WeekStrip(
                selectedDate = selectedDate,
                markedDates = markedDates,
                onSelectDate = onSelectDate,
                onExpand = { onExpandedChange(true) }
            )
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun WeekStrip(
    selectedDate: LocalDate,
    markedDates: Set<LocalDate>,
    onSelectDate: (LocalDate) -> Unit,
    onExpand: () -> Unit
) {
    val today = LocalDate.now()
    val startAnchor = selectedDate.with(DayOfWeek.MONDAY)
    val epochMonday = LocalDate.ofEpochDay(0).with(DayOfWeek.MONDAY)
    val weekIndex = ((startAnchor.toEpochDay() - epochMonday.toEpochDay()) / 7).toInt()
    val pagerState = rememberPagerState(
        initialPage = weekIndex,
        pageCount = { Int.MAX_VALUE }
    )
    LaunchedEffect(selectedDate) {
        val target = ((selectedDate.with(DayOfWeek.MONDAY).toEpochDay() - epochMonday.toEpochDay()) / 7).toInt()
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }
    val weekdayLabels = (0..6).map { offset ->
        startAnchor.plusDays(offset.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onExpand).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdayLabels.forEach { label ->
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalPager(state = pagerState) { page ->
                val anchor = epochMonday.plusDays(page * 7L)
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (offset in 0..6) {
                        val date = anchor.plusDays(offset.toLong())
                        val isToday = date == today
                        val isSelected = date == selectedDate
                        val hasMark = date in markedDates
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            DayCell(
                                dayText = date.dayOfMonth.toString(),
                                isToday = isToday,
                                isSelected = isSelected,
                                hasMark = hasMark,
                                compact = true,
                                onClick = { onSelectDate(date) }
                            )
                        }
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.life_weekly_calendar_expand_hint), fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
            IconButton(onClick = onExpand, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    stringResource(R.string.life_weekly_calendar_expand),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun MonthCalendar(
    selectedDate: LocalDate,
    markedDates: Set<LocalDate>,
    onSelectDate: (LocalDate) -> Unit,
    onCollapse: () -> Unit
) {
    val today = LocalDate.now()
    val monthIndex = selectedDate.year * 12 + selectedDate.monthValue - 1
    val pagerState = rememberPagerState(
        initialPage = monthIndex,
        pageCount = { Int.MAX_VALUE }
    )
    LaunchedEffect(selectedDate) {
        val target = selectedDate.year * 12 + selectedDate.monthValue - 1
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }
    val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    Column(modifier = Modifier.fillMaxWidth()) {
        val current = YearMonth.of(pagerState.currentPage / 12, (pagerState.currentPage % 12) + 1)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onSelectDate(LocalDate.now()) }, modifier = Modifier.height(32.dp)) {
                Text(stringResource(R.string.life_weekly_calendar_today), fontSize = 12.sp)
            }
            Text(
                stringResource(R.string.life_weekly_calendar_month_header, current.year, current.monthValue),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onCollapse, modifier = Modifier.height(32.dp)) {
                Text(stringResource(R.string.life_weekly_calendar_collapse), fontSize = 12.sp)
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdayLabels.forEach { label ->
                Text(
                    label,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalPager(state = pagerState) { page ->
            val ym = YearMonth.of(page / 12, (page % 12) + 1)
            val firstDayOffset = (ym.atDay(1).dayOfWeek.value + 6) % 7
            val daysInMonth = ym.lengthOfMonth()
            val cellCount = firstDayOffset + daysInMonth
            val rowCount = (cellCount + 6) / 7
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(rowCount) { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (col in 0..6) {
                            val index = row * 7 + col
                            val day = index - firstDayOffset + 1
                            if (index in 0 until cellCount && day in 1..daysInMonth) {
                                val date = ym.atDay(day)
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    DayCell(
                                        dayText = day.toString(),
                                        isToday = date == today,
                                        isSelected = date == selectedDate,
                                        hasMark = date in markedDates,
                                        compact = false,
                                        onClick = { onSelectDate(date) }
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    dayText: String,
    isToday: Boolean,
    isSelected: Boolean,
    hasMark: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    val size = if (compact) 32.dp else 36.dp
    Column(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isSelected) ModuleLife else if (isToday) ModuleLife.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            dayText,
            fontSize = if (compact) 12.sp else 13.sp,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
        )
        if (hasMark) {
            Spacer(modifier = Modifier.height(1.dp))
            Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(if (isSelected) Color.White else ModuleLife))
        }
    }
}
