package com.palmnote.ui.life.time.anniversary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.palmnote.ui.life.common.LifeLazyList
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
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
import com.palmnote.ui.life.common.EmptyState
import com.palmnote.ui.life.common.SwipeableItem
import com.palmnote.ui.theme.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnniversaryListScreen(templateId: Long, onBack: () -> Unit, onItemClick: (Long) -> Unit, onCreateClick: () -> Unit = {}, viewModel: AnniversaryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(templateId) { viewModel.load(templateId) }
    var deleteTarget by remember { mutableStateOf<Long?>(null) }
    if (deleteTarget != null) {
        AppDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.confirm), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.life_confirm_delete_anniversary)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteItem(deleteTarget!!); deleteTarget = null }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.life_anniversary_title), fontWeight = FontWeight.Bold, color = LifeAnniversary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back)) } },
                actions = { IconButton(onClick = onCreateClick) { Icon(Icons.Default.Add, stringResource(R.string.life_new_create)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LifeAnniversary) }
            return@Scaffold
        }
        if (state.items.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Favorite,
                title = stringResource(R.string.life_empty_anniversary),
                subtitle = stringResource(R.string.life_empty_anniversary_subtitle),
                actionLabel = stringResource(R.string.life_empty_anniversary_action),
                onAction = onCreateClick
            )
        } else {
        LifeLazyList(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.items, key = { it.id }) { item ->
                val dateMillis = try {
                    val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                    (obj["date"] as? JsonPrimitive)?.content?.toLongOrNull()
                        ?: (obj["target_date"] as? JsonPrimitive)?.content?.toLongOrNull()
                } catch (_: Exception) { null }
                val days = if (dateMillis != null) {
                    ChronoUnit.DAYS.between(LocalDate.ofEpochDay(dateMillis / 86400000L), LocalDate.now())
                } else 0L
                val dateText = if (dateMillis != null) {
                    LocalDate.ofEpochDay(dateMillis / 86400000L).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                } else ""

                SwipeableItem(onDelete = { deleteTarget = item.id }) {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onItemClick(item.id) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.width(3.dp).height(60.dp).align(Alignment.CenterStart).background(LifeAnniversary, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)))
                            Row(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Favorite, null, tint = LifeAnniversary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (dateText.isNotEmpty()) Text(stringResource(R.string.life_anniversary_days_ago, days.toInt()), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("$days", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = LifeAnniversary)
                            }
                        }
                    }
                }
            }
        }
        }
    }
}