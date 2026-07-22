package com.palmnote.ui.life.record.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.PalmNoteApp
import com.palmnote.ui.components.simpleViewModel
import com.palmnote.R
import com.palmnote.data.db.entity.FocusRecord
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusTimerScreen(onBack: () -> Unit, viewModel: FocusViewModel = simpleViewModel { PalmNoteApp.container.focusViewModel() }) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val todayRecords by viewModel.todayRecords.collectAsStateWithLifecycle(initialValue = emptyList())
    LaunchedEffect(Unit) { viewModel.load() }
    val focusColor = LifeFocus

    var selectedMinutes by remember { mutableStateOf(25) }
    var remainingSeconds by remember { mutableStateOf(25 * 60) }
    var isRunning by remember { mutableStateOf(false) }
    var startTimeMillis by remember { mutableStateOf(0L) }
    var taskLabel by remember { mutableStateOf("") }
    var showGiveUpDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (showGiveUpDialog) {
        AppDialog(
            onDismissRequest = { showGiveUpDialog = false },
            title = { Text(stringResource(R.string.life_focus_giveup_confirm), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.life_focus_giveup_hint)) },
            confirmButton = { TextButton(onClick = { showGiveUpDialog = false; isRunning = false; remainingSeconds = selectedMinutes * 60 }) { Text(stringResource(R.string.life_focus_giveup), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showGiveUpDialog = false }) { Text(stringResource(R.string.life_focus_continue)) } }
        )
    }

    LaunchedEffect(isRunning) {
        while (isRunning && remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds--
        }
        if (remainingSeconds == 0 && isRunning) {
            isRunning = false
            viewModel.saveRecord(selectedMinutes, true, startTimeMillis)
        }
    }

    val progress = if (selectedMinutes > 0) {
        (selectedMinutes * 60 - remainingSeconds).toFloat() / (selectedMinutes * 60)
    } else 0f
    val todaySummary = stringResource(R.string.life_focus_today_summary, state.todayMinutes, state.totalMinutes)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SecondaryTopAppBar(
                title = { Text(stringResource(R.string.life_focus_title), fontWeight = FontWeight.Bold, color = focusColor) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back)) } },
                actions = { IconButton(onClick = { scope.launch { snackbarHostState.showSnackbar(todaySummary) } }) { Icon(Icons.Default.BarChart, stringResource(R.string.life_focus_stats)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                Spacer(modifier = Modifier.height(24.dp))

                Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val s = 8.dp.toPx()
                        val r = (size.minDimension - s) / 2
                        drawCircle(color = focusColor.copy(alpha = 0.12f), radius = r, center = Offset(size.width / 2, size.height / 2))
                        drawArc(color = focusColor, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false, style = Stroke(width = s, cap = StrokeCap.Round), topLeft = Offset(s / 2, s / 2), size = Size(size.width - s, size.height - s))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatTime(remainingSeconds), fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                        Text("/ ${formatTime(selectedMinutes * 60)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                if (taskLabel.isNotEmpty()) {
                    Text(taskLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 25, 45, 60).forEach { mins ->
                        Box(
                            modifier = Modifier
                                .background(if (mins == selectedMinutes) focusColor.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(16.dp))
                                .border(1.dp, if (mins == selectedMinutes) focusColor else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .clickable {
                            val prevMinutes = selectedMinutes
                            selectedMinutes = mins
                            remainingSeconds = (remainingSeconds * mins / prevMinutes).coerceAtMost(mins * 60)
                        }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("${mins}m", fontSize = 12.sp, color = if (mins == selectedMinutes) focusColor else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showGiveUpDialog = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.life_focus_giveup), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            if (!isRunning && remainingSeconds == selectedMinutes * 60) {
                                startTimeMillis = System.currentTimeMillis()
                            }
                            isRunning = !isRunning
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = focusColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isRunning) "\u23F8" else "\u25B6", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isRunning) stringResource(R.string.life_focus_pause) else stringResource(R.string.life_focus_start), fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            if (isRunning) {
                                isRunning = false
                                val elapsed = (selectedMinutes * 60 - remainingSeconds + 59) / 60
                                if (elapsed > 0) viewModel.saveRecord(elapsed, true, startTimeMillis)
                            }
                            remainingSeconds = selectedMinutes * 60
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.life_focus_complete), color = LifeRecord, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = focusColor, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.life_focus_today), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.life_focus_today_minutes, state.todayMinutes), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            items(todayRecords, key = { it.id }) { record: FocusRecord ->
                val start = Instant.ofEpochMilli(record.startTime).atZone(ZoneId.systemDefault())
                val end: java.time.ZonedDateTime? = record.endTime?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val endStr = if (end != null) " - ${end.format(DateTimeFormatter.ofPattern("HH:mm"))}" else ""
                        Text("${start.format(DateTimeFormatter.ofPattern("HH:mm"))}$endStr", fontSize = 13.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, null, tint = LifeRecord, modifier = Modifier.size(14.dp))
                            Text("${record.durationMinutes}m", color = LifeRecord, fontSize = 13.sp)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.life_focus_today_minutes, state.todayMinutes), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
