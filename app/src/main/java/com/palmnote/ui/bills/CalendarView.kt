package com.palmnote.ui.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.ui.theme.*
import java.time.DayOfWeek
import com.palmnote.R
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

@Composable
fun CalendarView(
    yearMonth: String, // "2024-01"
    dailyData: Map<Int, Pair<Double, Double>>, // day -> (expense, income)
    selectedDay: Int?,
    onDaySelected: (Int) -> Unit,
    collapsed: Boolean = false,
    onMonthChanged: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var currentYearMonth by remember { mutableStateOf(yearMonth) }

    LaunchedEffect(yearMonth) {
        currentYearMonth = yearMonth
    }

    val parts = currentYearMonth.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return

    val calendar = Calendar.getInstance().apply {
        set(year, month - 1, 1)
    }
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sunday

    // 上个月天数（用于填充前面的空白）
    val prevCalendar = Calendar.getInstance().apply {
        set(year, month - 1, 1)
        add(Calendar.MONTH, -1)
    }
    val daysInPrevMonth = prevCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)

    val dayNames = remember {
        listOf(
            DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
        ).map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }
    val today = Calendar.getInstance()
    val todayYear = today.get(Calendar.YEAR)
    val todayMonth = today.get(Calendar.MONTH) + 1
    val todayDay = today.get(Calendar.DAY_OF_MONTH)

    val focusDay = selectedDay
    val focusRow = if (focusDay != null) (firstDayOfWeek + focusDay - 1) / 7 else 0

    Column(modifier = modifier) {
        // 月份导航
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                val newCal = Calendar.getInstance().apply {
                    set(year, month - 1, 1)
                    add(Calendar.MONTH, -1)
                }
                val newYear = newCal.get(Calendar.YEAR)
                val newMonth = newCal.get(Calendar.MONTH) + 1
                currentYearMonth = String.format("%04d-%02d", newYear, newMonth)
                onMonthChanged?.invoke(currentYearMonth)
            }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.common_previous_month))
            }

            val monthFormat = stringResource(R.string.date_format_display_month)
            val monthTitle = remember(year, month, monthFormat) {
                YearMonth.of(year, month).format(DateTimeFormatter.ofPattern(monthFormat))
            }
            Text(
                text = monthTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            IconButton(onClick = {
                val newCal = Calendar.getInstance().apply {
                    set(year, month - 1, 1)
                    add(Calendar.MONTH, 1)
                }
                val newYear = newCal.get(Calendar.YEAR)
                val newMonth = newCal.get(Calendar.MONTH) + 1
                currentYearMonth = String.format("%04d-%02d", newYear, newMonth)
                onMonthChanged?.invoke(currentYearMonth)
            }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.common_next_month))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 星期标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            dayNames.forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 日历网格 - 填充上月/下月日期
        val totalCells = firstDayOfWeek + daysInMonth
        val rows = (totalCells + 6) / 7

        val rowRange = if (collapsed) focusRow..focusRow else 0 until rows
        for (row in rowRange) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (col in 0 until 7) {
                    val index = row * 7 + col
                    val day = index - firstDayOfWeek + 1

                    when {
                        // 当月日期
                        day in 1..daysInMonth -> {
                            val dayData = dailyData[day]
                            val hasExpense = dayData != null && dayData.first > 0
                            val hasIncome = dayData != null && dayData.second > 0
                            val isToday = todayYear == year && todayMonth == month && day == todayDay
                            val isSelected = day == selectedDay

                            DayCell(
                                day = day,
                                isCurrentMonth = true,
                                isToday = isToday,
                                isSelected = isSelected,
                                hasExpense = hasExpense,
                                hasIncome = hasIncome,
                                onClick = { onDaySelected(day) }
                            )
                        }
                        // 上月日期（灰色）
                        index < firstDayOfWeek -> {
                            val prevDay = daysInPrevMonth - firstDayOfWeek + index + 1
                            val isToday = todayYear == prevCalendar.get(Calendar.YEAR) &&
                                    todayMonth == prevCalendar.get(Calendar.MONTH) + 1 &&
                                    prevDay == todayDay

                            DayCell(
                                day = prevDay,
                                isCurrentMonth = false,
                                isToday = isToday,
                                isSelected = false,
                                hasExpense = false,
                                hasIncome = false,
                                onClick = {
                                    currentYearMonth = String.format("%04d-%02d",
                                        prevCalendar.get(Calendar.YEAR),
                                        prevCalendar.get(Calendar.MONTH) + 1)
                                    onMonthChanged?.invoke(currentYearMonth)
                                }
                            )
                        }
                        // 下月日期（灰色）
                        else -> {
                            val nextDay = day - daysInMonth
                            val nextCal = Calendar.getInstance().apply {
                                set(year, month - 1, 1)
                                add(Calendar.MONTH, 1)
                            }
                            val isToday = todayYear == nextCal.get(Calendar.YEAR) &&
                                    todayMonth == nextCal.get(Calendar.MONTH) + 1 &&
                                    nextDay == todayDay

                            DayCell(
                                day = nextDay,
                                isCurrentMonth = false,
                                isToday = isToday,
                                isSelected = false,
                                hasExpense = false,
                                hasIncome = false,
                                onClick = {
                                    currentYearMonth = String.format("%04d-%02d",
                                        nextCal.get(Calendar.YEAR),
                                        nextCal.get(Calendar.MONTH) + 1)
                                    onMonthChanged?.invoke(currentYearMonth)
                                }
                            )
                        }
                    }
                }
            }
        }

        // 汇总（选日期显示当天，否则显示当月）
        val showDay = selectedDay != null && selectedDay in 1..daysInMonth
        val dayData = if (showDay) dailyData[selectedDay] else Pair(dailyData.values.sumOf { it.first }, dailyData.values.sumOf { it.second })
        Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.bill_expense),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyUtils.formatCurrency(dayData?.first ?: 0.0),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if ((dayData?.first ?: 0.0) > 0) ExpenseRed
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.bill_income),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyUtils.formatCurrency(dayData?.second ?: 0.0),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if ((dayData?.second ?: 0.0) > 0) StatusActive
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.bill_balance),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val balance = (dayData?.second ?: 0.0) - (dayData?.first ?: 0.0)
                        Text(
                            text = CurrencyUtils.formatCurrency(balance),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (balance >= 0) StatusActive else ErrorLight
                        )
                    }
                }
            }
    }
}

@Composable
private fun RowScope.DayCell(
    day: Int,
    isCurrentMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    hasExpense: Boolean,
    hasIncome: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(
                when {
                    isSelected -> AccentOrange
                    isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else -> Color.Transparent
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$day",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected -> Color.White
                    isToday -> MaterialTheme.colorScheme.primary
                    isCurrentMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            )

            // 指示点
            if (hasExpense || hasIncome) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    if (hasExpense) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) Color.White.copy(alpha = 0.8f)
                                    else ExpenseRed
                                )
                        )
                    }
                    if (hasIncome) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) Color.White.copy(alpha = 0.8f)
                                    else StatusActive
                                )
                        )
                    }
                }
            }
        }
    }
}
