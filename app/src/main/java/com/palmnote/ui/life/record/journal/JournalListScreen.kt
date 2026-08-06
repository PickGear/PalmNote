package com.palmnote.ui.life.record.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
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
import com.palmnote.data.db.entity.LifeMoment
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.components.SwipeActionBox
import com.palmnote.ui.components.EmptyState
import com.palmnote.ui.life.common.formatRelativeTime
import com.palmnote.ui.theme.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalListScreen(onBack: () -> Unit, onItemClick: (Long) -> Unit, viewModel: JournalViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    var showSheet by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<LifeMoment?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    if (deleteTarget != null) {
        AppDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.confirm), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.life_confirm_delete_record)) },
            confirmButton = { TextButton(onClick = { deleteTarget?.let { viewModel.deleteMoment(it.id) }; deleteTarget = null }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    Scaffold(
        topBar = {
            SecondaryTopAppBar(
                title = { Text(stringResource(R.string.life_journal_title), fontWeight = FontWeight.Bold, color = LifeJournal) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back)) } },
                actions = { IconButton(onClick = { showSheet = true }) { Icon(Icons.Default.Add, stringResource(R.string.life_journal_write)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (state.isLoading) { Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LifeJournal) }; return@Scaffold }
        if (state.moments.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                EmptyState(icon = Icons.Default.AutoStories, title = stringResource(R.string.life_journal_empty_first), subtitle = stringResource(R.string.life_journal_empty_hint), actionText = stringResource(R.string.life_journal_write_one), onActionClick = { showSheet = true })
            }
            return@Scaffold
        }
        val grouped = remember(state.moments) { state.moments.groupBy { Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate() }.toSortedMap(compareByDescending { it }) }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            grouped.forEach { (date, moments) ->
                item { Text(date.format(DateTimeFormatter.ofPattern(context.getString(R.string.date_format_display_year))), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                items(moments, key = { it.id }) { moment ->
                    SwipeActionBox(onSwipeLeft = { deleteTarget = moment }) {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.width(3.dp).height(56.dp).align(Alignment.CenterStart).background(LifeJournal, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)))
                        Column(modifier = Modifier.padding(start = 15.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
                            if (moment.content.isNotEmpty()) Text(moment.content, maxLines = 5, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val moodE = when (moment.mood) { "HAPPY" -> "\uD83D\uDE04"; "GOOD" -> "\uD83D\uDE42"; "NORMAL" -> "\uD83D\uDE14"; "SAD" -> "\uD83D\uDE22"; "ANGRY" -> "\uD83D\uDE21"; else -> null }
                                if (moodE != null) Text(moodE, fontSize = 16.sp)
                                Spacer(modifier = Modifier.weight(1f))
                                Text(formatRelativeTime(context, moment.date), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
}
}
}
    }
}
    }

    if (showSheet) {
        JournalEntrySheet(
            onDismiss = { showSheet = false },
            onSave = { mood, content ->
                viewModel.saveMoment(mood, content)
                showSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JournalEntrySheet(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var mood by remember { mutableStateOf("NORMAL") }
    var content by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.life_journal_write), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.life_mood), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf("HAPPY" to "\uD83D\uDE04", "GOOD" to "\uD83D\uDE42", "NORMAL" to "\uD83D\uDE14", "SAD" to "\uD83D\uDE22", "ANGRY" to "\uD83D\uDE21").forEach { (key, emoji) ->
                    Surface(
                        modifier = Modifier.size(36.dp).minimumInteractiveComponentSize().clickable { mood = key },
                        shape = RoundedCornerShape(18.dp),
                        color = if (mood == key) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background,
                        border = if (mood == key) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                    ) { Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 18.sp) } }
    }
}
            }
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                placeholder = { Text(stringResource(R.string.life_journal_write)) },
                shape = MaterialTheme.shapes.medium,
                maxLines = 10
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { if (content.isNotBlank()) onSave(mood, content) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = LifeJournal),
                enabled = content.isNotBlank()
            ) { Text(stringResource(R.string.save), fontWeight = FontWeight.Medium) }
        }
    }