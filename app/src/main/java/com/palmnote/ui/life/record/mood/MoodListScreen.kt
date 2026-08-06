package com.palmnote.ui.life.record.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.data.db.entity.MoodDiary
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.components.SwipeActionBox
import com.palmnote.ui.components.EmptyState
import com.palmnote.ui.life.common.formatRelativeTime
import com.palmnote.ui.theme.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodListScreen(onBack: () -> Unit, viewModel: MoodViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    var deleteTarget by remember { mutableStateOf<MoodDiary?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    if (deleteTarget != null) {
        AppDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.confirm), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.life_confirm_delete_record)) },
            confirmButton = { TextButton(onClick = { deleteTarget?.let { viewModel.deleteMood(it.id) }; deleteTarget = null }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    Scaffold(
        topBar = {
            SecondaryTopAppBar(
                title = { Text(stringResource(R.string.life_mood_record), fontWeight = FontWeight.Bold, color = LifeMoodColor) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back)) } },
                actions = { IconButton(onClick = { viewModel.showSheet() }) { Icon(Icons.Default.Add, stringResource(R.string.life_mood_record_action)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LifeMoodColor) }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (state.diaries.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Default.Mood,
                            title = stringResource(R.string.life_mood_empty),
                            subtitle = stringResource(R.string.life_mood_empty_subtitle),
                            actionText = stringResource(R.string.life_mood_record_action),
                            onActionClick = { viewModel.showSheet() }
                        )
                    }
                }
            } else {
                item {
                    MoodCalendarView(diaries = state.diaries)
                    Spacer(modifier = Modifier.height(8.dp))
                    MoodTrendChart(diaries = state.diaries)
                    Spacer(modifier = Modifier.height(8.dp))
                    FactorAnalysisChart(diaries = state.diaries)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                items(state.diaries.sortedByDescending { it.date }, key = { it.id }) { diary ->
                    val moodEmoji = when (diary.mood) { "HAPPY" -> "\uD83D\uDE04"; "GOOD" -> "\uD83D\uDE42"; "NORMAL" -> "\uD83D\uDE14"; "SAD" -> "\uD83D\uDE22"; "ANGRY" -> "\uD83D\uDE21"; else -> "\uD83D\uDE04" }
                    val moodColor = when (diary.mood) { "HAPPY" -> LifeMoodHappy; "GOOD" -> LifeMoodNormal; "NORMAL" -> LifeMoodUpset; "SAD" -> LifeMoodSad; "ANGRY" -> LifeMoodAngry; else -> LifeMoodHappy }
                    SwipeActionBox(onSwipeLeft = { deleteTarget = diary }) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.width(3.dp).height(56.dp).align(Alignment.CenterStart).background(moodColor, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)))
                            Row(modifier = Modifier.padding(start = 15.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
                                Box(modifier = Modifier.size(36.dp).background(moodColor.copy(alpha = 0.15f), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Text(moodEmoji, fontSize = 18.sp) }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    if (diary.content.isNotEmpty()) Text(diary.content, maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                                    Text(formatRelativeTime(context, diary.date), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    }
                }
            }
        }

        if (state.showSheet) {
            QuickMoodSheet(
                onDismiss = { viewModel.dismissSheet() },
                onSave = { mood, content, factors -> viewModel.saveMood(mood, content, factors) }
            )
        }
    }
}