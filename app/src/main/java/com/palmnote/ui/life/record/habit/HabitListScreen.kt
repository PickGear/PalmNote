package com.palmnote.ui.life.record.habit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.R
import com.palmnote.data.db.entity.Goal
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.life.common.FilterChipItem
import com.palmnote.ui.life.common.SwipeableItem
import com.palmnote.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitListScreen(onBack: () -> Unit, onItemClick: (Long) -> Unit, onAchievementClick: () -> Unit = {}, viewModel: HabitViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    var showStats by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var createTitle by remember { mutableStateOf("") }
    var createCategory by remember { mutableStateOf("HABIT") }
    var createFrequency by remember { mutableStateOf("DAILY") }
    var createTarget by remember { mutableStateOf("1") }

    var deleteTarget by remember { mutableStateOf<Long?>(null) }
    if (deleteTarget != null) {
        AppDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.confirm), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.life_confirm_delete_habit)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteHabit(deleteTarget!!); deleteTarget = null }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.life_habit_checkin) + stringResource(R.string.life_section_title_record), fontWeight = FontWeight.Bold, color = LifeHabit) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back)) } },
                actions = {
                    IconButton(onClick = { onAchievementClick() }) { Icon(Icons.Default.EmojiEvents, stringResource(R.string.life_habit_achievement)) }
                    IconButton(onClick = { viewModel.showCreateSheet() }) { Icon(Icons.Default.Add, stringResource(R.string.life_habit_new)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LifeHabit) }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChipItem(stringResource(R.string.life_habit_filter_stats), showStats, LifeHabit) { showStats = true }
                    FilterChipItem(stringResource(R.string.life_habit_filter_timeline), !showStats, LifeHabit) { showStats = false }
                }
            }

            if (state.habits.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, null, tint = LifeHabit.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.life_empty_habit_start), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.life_empty_habit_hint), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            state.habits.forEach { habit ->
                item {
                    val streak = habit.streak
                    val totalCheckIns = habit.totalCheckInDays
                    val monthRate = if (habit.totalCount > 0) (habit.currentCount * 100 / habit.totalCount).coerceIn(0, 100) else 0
                    val todayChecked = state.checkInDates[habit.id]?.contains(LocalDate.now()) == true

                    SwipeableItem(onDelete = { deleteTarget = habit.id }) {
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = LifeHabit, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(habit.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                if (!todayChecked) {
                                    FilledTonalButton(
                                        onClick = { viewModel.checkIn(habit.id) },
                                        modifier = Modifier.height(32.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.life_habit_checkin), fontSize = 12.sp)
                                    }
                                } else {
                                    Text(stringResource(R.string.life_habit_checked), fontSize = 12.sp, color = LifeHabit)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("${monthRate}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LifeRecord); Text(stringResource(R.string.life_habit_month_rate), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LocalFireDepartment, null, tint = LifeHabit, modifier = Modifier.size(14.dp)); Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(stringResource(R.string.life_habit_streak, streak), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LifeHabit); Text(stringResource(R.string.life_habit_streak_label), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                                Row(verticalAlignment = Alignment.CenterVertically) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(stringResource(R.string.life_habit_total_checkins, totalCheckIns), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface); Text(stringResource(R.string.life_habit_total_label), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                            }
                        }
                    }
                }
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }

                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(stringResource(R.string.life_habit_heatmap), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 8.dp))
                        HeatmapGrid(habit, state.checkInDates[habit.id] ?: emptySet())
                    }
                }
            }
        }
    }

    if (state.showCreateSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissCreateSheet() },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Text(stringResource(R.string.life_habit_new), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = createTitle,
                    onValueChange = { createTitle = it },
                    label = { Text(stringResource(R.string.life_habit_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(stringResource(R.string.life_habit_category), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("HABIT" to stringResource(R.string.life_habit_category_daily), "FITNESS" to stringResource(R.string.life_habit_category_fitness), "READING" to stringResource(R.string.life_habit_category_reading)).forEach { (key, label) ->
                        FilterChip(
                            selected = createCategory == key,
                            onClick = { createCategory = key },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Text(stringResource(R.string.life_habit_frequency), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("DAILY" to stringResource(R.string.life_habit_frequency_daily), "WEEKLY" to stringResource(R.string.life_habit_frequency_weekly), "MONTHLY" to stringResource(R.string.life_habit_frequency_monthly)).forEach { (key, label) ->
                        FilterChip(
                            selected = createFrequency == key,
                            onClick = { createFrequency = key },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = createTarget,
                    onValueChange = { if (it.all { c -> c.isDigit() }) createTarget = it },
                    label = { Text(stringResource(R.string.life_habit_weekly_target)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (createTitle.isNotBlank()) {
                            viewModel.createHabit(createTitle, createCategory, createFrequency, createTarget.toIntOrNull() ?: 1)
                            createTitle = ""
                            createCategory = "HABIT"
                            createFrequency = "DAILY"
                            createTarget = "1"
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LifeHabit),
                    enabled = createTitle.isNotBlank()
                ) {
                    Text(stringResource(R.string.life_habit_create_btn), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun HeatmapGrid(habit: Goal, checkInDates: Set<LocalDate>) {
    val days = 364
    val today = LocalDate.now()
    val startDate = today.minusDays(days.toLong() - 1)
    Column {
        for (row in 0 until 7) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (col in 0 until 52) {
                    val idx = row + col * 7
                    if (idx >= days) { Box(modifier = Modifier.size(0.dp)) }
                    else {
                        val date = startDate.plusDays(idx.toLong())
                        val isCheckedIn = checkInDates.contains(date)
                        val color = when { date.isAfter(today) -> Color.Transparent; isCheckedIn -> LifeRecord; else -> LifeRecord.copy(alpha = 0.06f) }
                        Box(modifier = Modifier.size(12.dp).background(color, RoundedCornerShape(2.dp)))
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.life_habit_less), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(0.06f, 0.25f, 0.5f, 0.75f, 1f).forEach { a -> Box(modifier = Modifier.size(10.dp).background(LifeRecord.copy(alpha = a), RoundedCornerShape(2.dp))) }
            Text(stringResource(R.string.life_habit_more), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HabitListPreview() {
    com.palmnote.ui.theme.PalmNoteTheme { HabitListScreen(onBack = {}, onItemClick = {}, onAchievementClick = {}) }
}