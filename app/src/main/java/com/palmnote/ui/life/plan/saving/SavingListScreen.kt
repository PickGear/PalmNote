package com.palmnote.ui.life.plan.saving

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.PalmNoteApp
import com.palmnote.R
import com.palmnote.ui.components.simpleViewModel
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.components.ModuleSearchBar
import com.palmnote.ui.life.common.DeleteConfirmSheet
import com.palmnote.ui.life.common.FilterChipItem
import com.palmnote.ui.life.common.SwipeableItem
import com.palmnote.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingListScreen(templateId: Long, onBack: () -> Unit, onItemClick: (Long) -> Unit, onCreateClick: () -> Unit = {}, viewModel: SavingPlanViewModel = simpleViewModel { PalmNoteApp.container.savingPlanViewModel() }) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(templateId) { viewModel.load(templateId) }
    var filter by remember { mutableStateOf(0) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var lastDeletedId by remember { mutableStateOf<Long?>(null) }
    var showDeleteSheet by remember { mutableStateOf(false) }
    var pendingDeleteItem by remember { mutableStateOf<LifeItem?>(null) }

    val filtered = when (filter) {
        1 -> state.items.filter { it.status != "COMPLETED" }
        2 -> state.items.filter { it.status == "COMPLETED" }
        else -> state.items
    }.filter { searchQuery.isBlank() || it.title.contains(searchQuery) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SecondaryTopAppBar(
                title = { Text(stringResource(R.string.life_saving_title), fontWeight = FontWeight.Bold, color = LifeSaving) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back)) } },
                actions = {
                    if (showSearch) {
                        ModuleSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onClear = { searchQuery = "" },
                            placeholder = stringResource(R.string.search),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showSearch = false; searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                        }
                    } else {
                        IconButton(onClick = { showSearch = true }) { Icon(Icons.Default.Search, stringResource(R.string.search)) }
                        IconButton(onClick = onCreateClick) { Icon(Icons.Default.Add, stringResource(R.string.life_new_create)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LifeSaving) }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChipItem(stringResource(R.string.life_saving_filter_all, state.items.size), filter == 0, LifeSaving) { filter = 0 }
                FilterChipItem(stringResource(R.string.life_saving_filter_active), filter == 1, LifeSaving) { filter = 1 }
                FilterChipItem(stringResource(R.string.life_saving_filter_done), filter == 2, LifeSaving) { filter = 2 }
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.life_saving_no_match), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filtered, key = { it.id }) { item ->
                        SwipeableItem(onDelete = {
                            pendingDeleteItem = item
                            showDeleteSheet = true
                        }) {
                        val fields = try {
                            val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                            val target = (obj["target_amount"] as? JsonPrimitive)?.content ?: (obj["targetAmount"] as? JsonPrimitive)?.content ?: ""
                            val saved = (obj["saved_amount"] as? JsonPrimitive)?.content ?: (obj["currentAmount"] as? JsonPrimitive)?.content ?: ""
                            Pair(target, saved)
                        } catch (_: Exception) { Pair("", "") }
                        val targetVal = fields.first.toDoubleOrNull() ?: 0.0
                        val savedVal = fields.second.toDoubleOrNull() ?: 0.0
                        val progress = if (targetVal > 0) (savedVal / targetVal).toFloat().coerceIn(0f, 1f) else 0f
                        val isDone = item.status == "COMPLETED" || progress >= 1f

                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onItemClick(item.id) }.then(if (isDone) Modifier.alpha(0.7f) else Modifier),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.width(3.dp).height(72.dp).align(Alignment.CenterStart).background(if (isDone) LifeRecord else LifeSaving, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)))
                                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Savings, null, tint = if (isDone) LifeRecord else LifeSaving, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(item.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Text(if (isDone) stringResource(R.string.life_saving_filter_done) else stringResource(R.string.life_saving_filter_active), fontSize = 10.sp, fontWeight = FontWeight.Medium, color = if (isDone) LifeRecord else LifeSaving, modifier = Modifier.background((if (isDone) LifeRecord else LifeSaving).copy(alpha = 0.12f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (savedVal > 0) Text("\u00A5${"%,.0f".format(savedVal)}", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                                        if (targetVal > 0) { Spacer(modifier = Modifier.width(4.dp)); Text("/ \u00A5${"%,.0f".format(targetVal)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(2.dp))) {
                                        Box(modifier = Modifier.fillMaxWidth(if (isDone) 1f else progress).height(4.dp).background(if (isDone) LifeRecord else LifeSaving, RoundedCornerShape(2.dp)))
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

    if (showDeleteSheet && pendingDeleteItem != null) {
        val item = pendingDeleteItem!!
        val deletedMsg = stringResource(R.string.life_saving_deleted)
        val undoLabel = stringResource(R.string.life_saving_undo)
        DeleteConfirmSheet(
            itemSummary = item.title,
            onDelete = {
                showDeleteSheet = false
                lastDeletedId = item.id
                viewModel.deleteItem(item.id)
                scope.launch {
                    val result = snackbarHostState.showSnackbar(deletedMsg, actionLabel = undoLabel, duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) {
                        lastDeletedId?.let { viewModel.restoreItem(it); lastDeletedId = null }
                    }
                }
            },
            onDismiss = { showDeleteSheet = false; pendingDeleteItem = null }
        )
    }
}