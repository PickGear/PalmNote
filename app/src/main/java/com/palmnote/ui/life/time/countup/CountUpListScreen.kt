package com.palmnote.ui.life.time.countup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.palmnote.ui.life.common.LifeLazyList
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.PalmNoteApp
import com.palmnote.R
import com.palmnote.domain.util.DateUtils
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.ui.components.EmptyState
import com.palmnote.ui.life.common.SwipeableItem
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.theme.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountUpListScreen(templateId: Long, onBack: () -> Unit, onItemClick: (Long) -> Unit, onCreateClick: () -> Unit = {}, viewModel: CountUpViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(templateId) { viewModel.load(templateId) }
    var deleteTarget by remember { mutableStateOf<Long?>(null) }
    if (deleteTarget != null) {
        AppDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.confirm), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.life_confirm_delete_countup)) },
            confirmButton = { TextButton(onClick = { deleteTarget?.let { viewModel.deleteItem(it) }; deleteTarget = null }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    Scaffold(
        topBar = {
            SecondaryTopAppBar(
                title = { Text(stringResource(R.string.life_countup_title), fontWeight = FontWeight.Bold, color = LifeCountUp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back)) } },
                actions = { IconButton(onClick = onCreateClick) { Icon(Icons.Default.Add, stringResource(R.string.life_new_create)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LifeCountUp) }
            return@Scaffold
        }
        if (state.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    title = stringResource(R.string.life_empty_countup),
                    subtitle = stringResource(R.string.life_empty_countup_subtitle),
                    actionText = stringResource(R.string.life_empty_countup_action),
                    onActionClick = onCreateClick
                )
            }
        } else {
        LifeLazyList(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.items, key = { it.id }) { item ->
                val startDate = try {
                    val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                    (obj["start_date"] as? JsonPrimitive)?.content?.toLongOrNull()
                        ?: (obj["startDate"] as? JsonPrimitive)?.content?.toLongOrNull()
                } catch (_: Exception) { null }
                val days = if (startDate != null) {
                    ChronoUnit.DAYS.between(DateUtils.millisToLocalDate(startDate), LocalDate.now())
                } else 0L
                val milestones = listOf(100L, 200L, 365L, 500L, 750L, 1000L)
                val nextMilestone = milestones.firstOrNull { it > days }

                SwipeableItem(onDelete = { deleteTarget = item.id }) {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onItemClick(item.id) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = LifeCountUp, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(item.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(stringResource(R.string.life_countup_days, days), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = LifeCountUp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                milestones.forEach { target ->
                                    val reached = days >= target
                                    Box(
                                        modifier = Modifier.size(28.dp).background(
                                            if (reached) LifeCountUp else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                            RoundedCornerShape(14.dp)
                                        ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("$target", fontSize = 9.sp, color = if (reached) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (nextMilestone != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(stringResource(R.string.life_countup_milestone, nextMilestone - days), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
        }
    }
}