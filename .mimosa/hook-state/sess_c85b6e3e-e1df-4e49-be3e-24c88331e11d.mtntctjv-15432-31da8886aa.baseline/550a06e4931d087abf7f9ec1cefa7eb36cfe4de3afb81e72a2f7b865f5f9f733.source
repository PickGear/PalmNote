package com.palmnote.ui.life
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.palmnote.app.R
import com.palmnote.domain.repository.FocusRecordRepository
import com.palmnote.domain.repository.GoalRepository
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.theme.LifePlan
import com.palmnote.ui.theme.LifeRecord
import com.palmnote.ui.theme.LifeTime
import com.palmnote.ui.theme.ModuleLife
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale


data class LifeStatsDay(val date: LocalDate, val count: Int)

data class LifeStatsUiState(
    val todayTodos: Int = 0,
    val habitCompletionRate: Int = 0,
    val todayFocusMinutes: Int = 0,
    val maxStreak: Int = 0,
    val weekDays: List<LifeStatsDay> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class LifeStatsViewModel @Inject constructor(
    private val itemRepo: LifeItemRepository,
    private val goalRepo: GoalRepository,
    private val focusRepo: FocusRecordRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(LifeStatsUiState())
    val uiState: StateFlow<LifeStatsUiState> = _uiState.asStateFlow()

    init {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val tomorrow = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val weekStart = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val daySlots = (6 downTo 0).map { today.minusDays(it.toLong()) }

        val scheduledFlow = itemRepo.getScheduledBetween(weekStart, tomorrow).map { items ->
            val grouped = items.groupBy {
                it.dueDate?.let { ts -> Instant.ofEpochMilli(ts).atZone(zone).toLocalDate() }
            }
            daySlots.map { day -> LifeStatsDay(day, grouped[day]?.size ?: 0) }
        }
        val habitFlow = goalRepo.getHabitGoals().map { habits ->
            if (habits.isEmpty()) 0 else habits.count { it.isCompleted } * 100 / habits.size
        }
        val focusFlow = flow { emit(focusRepo.getTodayTotalMinutes(todayStart, tomorrow)) }
        val streakFlow = goalRepo.getTotalStreak().map { it ?: 0 }

        combine(scheduledFlow, habitFlow, focusFlow, streakFlow) { days, rate, minutes, streak ->
            _uiState.update {
                it.copy(
                    todayTodos = days.lastOrNull()?.count ?: 0,
                    habitCompletionRate = rate,
                    todayFocusMinutes = minutes,
                    maxStreak = streak,
                    weekDays = days,
                    isLoading = false
                )
            }
        }.launchIn(viewModelScope)
    }
}

@Suppress("LongMethod", "LongParameterList")
@Composable
fun LifeStatsScreen(
    onBack: () -> Unit,
    viewModel: LifeStatsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            SecondaryTopAppBar(
                title = {
                    Text(stringResource(R.string.life_home_stats), fontWeight = FontWeight.Bold, color = ModuleLife)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ModuleLife)
                }
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatsCard(
                    title = stringResource(R.string.life_stats_today_todo),
                    value = "${state.todayTodos}",
                    icon = Icons.Default.Checklist,
                    color = LifePlan,
                    modifier = Modifier.weight(1f)
                )
                StatsCard(
                    title = stringResource(R.string.life_stats_habit_rate),
                    value = stringResource(R.string.life_stats_percent, state.habitCompletionRate),
                    icon = Icons.Default.LocalFireDepartment,
                    color = LifeRecord,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatsCard(
                    title = stringResource(R.string.life_stats_focus_minutes),
                    value = "${state.todayFocusMinutes}",
                    icon = Icons.Default.Timer,
                    color = LifeTime,
                    modifier = Modifier.weight(1f)
                )
                StatsCard(
                    title = stringResource(R.string.life_stats_max_streak),
                    value = if (state.maxStreak > 0) stringResource(R.string.life_stats_days, state.maxStreak) else "--",
                    icon = Icons.Default.LocalFireDepartment,
                    color = LifePlan,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            WeekChart(days = state.weekDays)
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatsCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clip(MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier.size(28.dp).background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun WeekChart(days: List<LifeStatsDay>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.life_stats_week_chart_title),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = ModuleLife
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (days.isEmpty()) {
                Text(stringResource(R.string.life_stats_empty), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            val max = days.maxOf { it.count }.coerceAtLeast(1)
            Row(
                modifier = Modifier.fillMaxWidth().height(96.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEach { day ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val fraction = day.count.toFloat() / max
                        val minBar = if (day.count > 0) 6.dp.value else 3.dp.value
                        val barHeight = (80 * fraction).coerceAtLeast(minBar)
                        if (day.count > 0) {
                            Text("${day.count}", fontSize = 10.sp, color = ModuleLife, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(2.dp))
                        } else {
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(barHeight.dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (day.date == LocalDate.now()) ModuleLife else ModuleLife.copy(alpha = 0.35f))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val label = day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
