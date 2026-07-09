package com.palmnote.ui.life.plan.shopping

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.palmnote.ui.life.common.LifeLazyList
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ShoppingCart
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
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.EmptyState
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.life.common.FilterChipItem
import com.palmnote.ui.life.common.SwipeableItem
import com.palmnote.ui.theme.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingKanbanScreen(templateId: Long, onBack: () -> Unit, onItemClick: (Long) -> Unit, onCreateClick: () -> Unit = {}, viewModel: ShoppingPlanViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(templateId) { viewModel.load(templateId) }

    val active = state.items.filter { it.status == "ACTIVE" || it.status == "WISHLIST" }
    val progress = state.items.filter { it.status == "ONGOING" || it.status == "IN_PROGRESS" }
    val done = state.items.filter { it.status == "COMPLETED" || it.status == "PURCHASED" }

    var deleteTarget by remember { mutableStateOf<Long?>(null) }
    if (deleteTarget != null) {
        AppDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.life_confirm), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.life_confirm_delete_shopping)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteItem(deleteTarget!!); deleteTarget = null }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    Scaffold(
        topBar = {
            SecondaryTopAppBar(
                title = { Text(stringResource(R.string.life_shopping_title), fontWeight = FontWeight.Bold, color = LifeShopping) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back)) } },
                actions = { IconButton(onClick = onCreateClick) { Icon(Icons.Default.Add, stringResource(R.string.life_new_create)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LifeShopping) }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            var showKanban by remember { mutableStateOf(true) }

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChipItem(stringResource(R.string.life_shopping_list), !showKanban, LifeShopping) { showKanban = false }
                FilterChipItem(stringResource(R.string.life_shopping_kanban), showKanban, LifeShopping) { showKanban = true }
            }

            if (state.items.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.ShoppingCart,
                    title = stringResource(R.string.life_empty_shopping),
                    subtitle = stringResource(R.string.life_empty_shopping_subtitle),
                    actionText = stringResource(R.string.life_empty_shopping_action),
                    onActionClick = onCreateClick
                )
            } else if (showKanban) {
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KanbanColumn(title = stringResource(R.string.life_shopping_tab_wishlist), count = active.size, color = LifeShopping, icon = Icons.Default.RadioButtonUnchecked, items = active, onItemClick = onItemClick, onDelete = { deleteTarget = it }, modifier = Modifier.width(180.dp))
                    KanbanColumn(title = stringResource(R.string.life_ongoing), count = progress.size, color = Color(0xFFE8A848), icon = Icons.Default.Pending, items = progress, onItemClick = onItemClick, onDelete = { deleteTarget = it }, modifier = Modifier.width(180.dp))
                    KanbanColumn(title = stringResource(R.string.life_shopping_tab_ordered), count = done.size, color = LifeRecord, icon = Icons.Default.CheckCircle, items = done, onItemClick = onItemClick, onDelete = { deleteTarget = it }, dimItems = true, modifier = Modifier.width(180.dp))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.items, key = { it.id }) { item ->
                        SwipeableItem(onDelete = { deleteTarget = item.id }) {
                            Card(modifier = Modifier.fillMaxWidth().clickable { onItemClick(item.id) }, shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val price = try { val obj = Json.decodeFromString<JsonObject>(item.fieldsData); (obj["budget"] as? JsonPrimitive)?.content ?: "" } catch (_: Exception) { "" }
                                    Column(modifier = Modifier.weight(1f)) { Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.Medium); if (price.isNotEmpty()) Text("\u00A5$price", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    Text(when (item.status) { "ACTIVE" -> stringResource(R.string.life_shopping_tab_wishlist); "ONGOING" -> stringResource(R.string.life_ongoing); else -> stringResource(R.string.life_shopping_tab_ordered) }, fontSize = 11.sp, color = when (item.status) { "ACTIVE" -> LifeShopping; "ONGOING" -> Color(0xFFE8A848); else -> LifeRecord })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KanbanColumn(title: String, count: Int, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, items: List<com.palmnote.data.db.entity.LifeItem>, onItemClick: (Long) -> Unit, onDelete: (Long) -> Unit = {}, dimItems: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
            Spacer(modifier = Modifier.width(4.dp))
            Text("$count", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
        items.forEach { item ->
            val price = try { val obj = Json.decodeFromString<JsonObject>(item.fieldsData); (obj["budget"] as? JsonPrimitive)?.content ?: (obj["price"] as? JsonPrimitive)?.content ?: "" } catch (_: Exception) { "" }
            SwipeableItem(onDelete = { onDelete(item.id) }) {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).clickable { onItemClick(item.id) }.then(if (dimItems) Modifier.alpha(0.6f) else Modifier), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(item.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (price.isNotEmpty()) Text(stringResource(R.string.life_shopping_budget, price), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

