package com.palmnote.ui.life.plan.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.palmnote.ui.life.common.LifeLazyList
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.R
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.EmptyState
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.life.common.SwipeableItem
import com.palmnote.ui.theme.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingListScreen(templateId: Long, onBack: () -> Unit, onItemClick: (Long) -> Unit, onCreateClick: () -> Unit = {}, viewModel: ReadingPlanViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(templateId) { viewModel.load(templateId) }
    var deleteTarget by remember { mutableStateOf<Long?>(null) }
    if (deleteTarget != null) {
        AppDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.life_confirm), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.life_confirm_delete_reading)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteItem(deleteTarget!!); deleteTarget = null }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    Scaffold(
        topBar = {
            SecondaryTopAppBar(
                title = { Text(stringResource(R.string.life_reading_title), fontWeight = FontWeight.Bold, color = LifeReading) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back)) } },
                actions = { IconButton(onClick = onCreateClick) { Icon(Icons.Default.Add, stringResource(R.string.life_new_create)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LifeReading) }
            return@Scaffold
        }
        if (state.items.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = stringResource(R.string.life_empty_reading),
                subtitle = stringResource(R.string.life_empty_reading_subtitle),
                actionText = stringResource(R.string.life_empty_reading_action),
                onActionClick = onCreateClick
            )
        } else {
        LifeLazyList(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.items, key = { it.id }) { item ->
                val fields = try {
                    val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                    val author = (obj["author"] as? JsonPrimitive)?.content ?: ""
                    val total = (obj["total_pages"] as? JsonPrimitive)?.content?.toIntOrNull()
                        ?: (obj["totalPages"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                    val cur = (obj["current_page"] as? JsonPrimitive)?.content?.toIntOrNull()
                        ?: (obj["currentPage"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                    Triple(author, total, cur)
                } catch (_: Exception) { Triple("", 0, 0) }
                val progress = if (fields.second > 0) (fields.third.toFloat() / fields.second).coerceIn(0f, 1f) else 0f
                val isDone = item.status == "COMPLETED"

                SwipeableItem(onDelete = { deleteTarget = item.id }) {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onItemClick(item.id) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.width(3.dp).height(72.dp).align(Alignment.CenterStart).background(LifeReading, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)))
                            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = LifeReading, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(item.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(if (isDone) stringResource(R.string.life_reading_done) else stringResource(R.string.life_reading_reading), fontSize = 10.sp, fontWeight = FontWeight.Medium, color = if (isDone) LifeRecord else LifeReading, modifier = Modifier.background((if (isDone) LifeRecord else LifeReading).copy(alpha = 0.12f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                                }
                                if (fields.first.isNotEmpty() || fields.second > 0) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        if (fields.first.isNotEmpty()) Text(fields.first, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (fields.second > 0) Text(stringResource(R.string.life_reading_page_format, fields.third.toString(), fields.second.toString()), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (fields.second > 0) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(2.dp))) {
                                        Box(modifier = Modifier.fillMaxWidth(progress).height(4.dp).background(LifeReading, RoundedCornerShape(2.dp)))
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
