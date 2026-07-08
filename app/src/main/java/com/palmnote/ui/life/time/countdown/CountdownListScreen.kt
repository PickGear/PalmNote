package com.palmnote.ui.life.time.countdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.palmnote.ui.life.common.LifeLazyList
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.R
import com.palmnote.ui.life.common.EmptyState
import com.palmnote.ui.life.common.SwipeableItem
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.theme.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownListScreen(templateId: Long, onBack: () -> Unit, onItemClick: (Long) -> Unit, onCreateClick: () -> Unit = {}, viewModel: CountdownViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(templateId) { viewModel.load(templateId) }
    var deleteTarget by remember { mutableStateOf<Long?>(null) }
    if (deleteTarget != null) {
        AppDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.confirm), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.life_confirm_delete_countdown)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteItem(deleteTarget!!); deleteTarget = null }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.life_countdown_title), fontWeight = FontWeight.Bold, color = LifeCountdown) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back)) } },
                actions = { IconButton(onClick = onCreateClick) { Icon(Icons.Default.Add, stringResource(R.string.life_new_create)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LifeCountdown) }
            return@Scaffold
        }
        if (state.items.isEmpty()) {
            EmptyState(
                icon = Icons.Default.HourglassBottom,
                title = stringResource(R.string.life_empty_countdown),
                subtitle = stringResource(R.string.life_empty_countdown_subtitle),
                actionLabel = stringResource(R.string.life_empty_countdown_action),
                onAction = onCreateClick
            )
        } else {
        LifeLazyList(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.items, key = { it.id }) { item ->
                val dateMillis = try {
                    val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                    (obj["target_date"] as? JsonPrimitive)?.content?.toLongOrNull()
                        ?: (obj["targetDate"] as? JsonPrimitive)?.content?.toLongOrNull()
                } catch (_: Exception) { null }
                val days = if (dateMillis != null) {
                    ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.ofEpochDay(dateMillis / 86400000L))
                } else null
                val isExpired = days != null && days < 0

                SwipeableItem(onDelete = { deleteTarget = item.id }) {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onItemClick(item.id) }
                            .then(if (isExpired) Modifier.alpha(0.6f) else Modifier),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.width(3.dp).height(72.dp).align(Alignment.CenterStart).background(if (isExpired) Color(0xFFE8A848) else LifeCountdown, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)))
                            Row(modifier = Modifier.padding(start = 15.dp, end = 14.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(50.dp)) {
                                Text("${days ?: "--"}", fontSize = if (days != null && days >= 0) 36.sp else 28.sp, fontWeight = FontWeight.Bold, color = if (isExpired) Color(0xFFE8A848) else LifeCountdown)
                                Text(if (isExpired) stringResource(R.string.life_countdown_expired) else stringResource(R.string.life_countdown_day), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (dateMillis != null) {
                                    val date = LocalDate.ofEpochDay(dateMillis / 86400000L)
                                    Text(if (isExpired) stringResource(R.string.life_countdown_expired_text, date.toString()) else stringResource(R.string.life_countdown_active_text, date.toString()), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        }
                    }
                }
            }
        }
        }
    }
}
