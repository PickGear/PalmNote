package com.palmnote.ui.life.plan.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.R
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.life.common.EmptyState
import com.palmnote.ui.life.common.FilterChipItem
import com.palmnote.ui.life.common.SwipeableItem
import com.palmnote.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    templateId: Long = 0,
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    onCreateClick: () -> Unit = {},
    viewModel: TodoViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(templateId) { if (templateId > 0) viewModel.load(templateId) }

    val q1 = state.items.filter { getTodoPriority(it) == "HIGH_URGENT" }
    val q2 = state.items.filter { getTodoPriority(it) == "HIGH" }
    val q3 = state.items.filter { getTodoPriority(it) == "URGENT" }
    val q4 = state.items.filter { getTodoPriority(it) == "NONE" || getTodoPriority(it) == "LOW" }

    var viewMode by remember { mutableStateOf("quad") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val calendarDevMsg = stringResource(R.string.life_todo_calendar_dev)
    LaunchedEffect(viewMode) {
        if (viewMode == "calendar") snackbarHostState.showSnackbar(calendarDevMsg)
    }

    var deleteTarget by remember { mutableStateOf<Long?>(null) }
    if (deleteTarget != null) {
        AppDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.life_confirm), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.life_confirm_delete_todo)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteItem(deleteTarget!!); deleteTarget = null }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.life_todo_title), fontWeight = FontWeight.Bold, color = LifeTodo) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back)) } },
                actions = { IconButton(onClick = onCreateClick) { Icon(Icons.Default.Add, stringResource(R.string.life_new_create)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LifeTodo) }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChipItem(stringResource(R.string.life_todo_filter_list), viewMode == "list", LifeTodo) { viewMode = "list" }
                FilterChipItem(stringResource(R.string.life_todo_filter_quad), viewMode == "quad", LifeTodo) { viewMode = "quad" }
                FilterChipItem(stringResource(R.string.life_todo_filter_calendar), viewMode == "calendar", LifeTodo) { viewMode = "calendar" }
            }
            if (state.items.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.CheckBox,
                    title = stringResource(R.string.life_empty_todo),
                    subtitle = stringResource(R.string.life_empty_todo_subtitle),
                    actionLabel = stringResource(R.string.life_empty_todo_action),
                    onAction = onCreateClick
                )
            } else {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                when (viewMode) {
                    "list" -> state.items.forEach { item ->
                        val isDone = item.status == "COMPLETED"
                        SwipeableItem(onDelete = { deleteTarget = item.id }) {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (isDone) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank, null, tint = if (isDone) LifeRecord else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp).clickable { viewModel.toggleComplete(item) })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None, modifier = Modifier.weight(1f).clickable { onItemClick(item.id) })
                                Text(getTodoPriority(item), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    "quad" -> {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            QuadBox(stringResource(R.string.life_todo_quad_important_urgent), Color(0xFFFF6B6B), Color(0x1AFF6B6B), q1, viewModel, onItemClick, onDelete = { deleteTarget = it }, modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(6.dp))
                            QuadBox(stringResource(R.string.life_todo_quad_important), Color(0xFFE8A848), Color(0x1AE8A848), q2, viewModel, onItemClick, onDelete = { deleteTarget = it }, modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            QuadBox(stringResource(R.string.life_todo_quad_urgent), Color(0xFF4285F4), Color(0x1A4285F4), q3, viewModel, onItemClick, onDelete = { deleteTarget = it }, modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(6.dp))
                            QuadBox(stringResource(R.string.life_todo_quad_normal), MaterialTheme.colorScheme.onSurfaceVariant, Color(0x0A808080), q4, viewModel, onItemClick, onDelete = { deleteTarget = it }, modifier = Modifier.weight(1f))
                        }
                    }
                    "calendar" -> { }
                }
            }
            }
        }
    }
}

@Composable
private fun QuadBox(
    title: String,
    titleColor: Color,
    bgColor: Color,
    items: List<LifeItem>,
    viewModel: TodoViewModel,
    onItemClick: (Long) -> Unit,
    onDelete: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.background(bgColor, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = titleColor)
        Spacer(modifier = Modifier.height(6.dp))
        if (items.isEmpty()) {
            Text(stringResource(R.string.life_todo_empty), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
        items.forEach { item ->
            val isDone = item.status == "COMPLETED"
            SwipeableItem(onDelete = { onDelete(item.id) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onItemClick(item.id) }.padding(vertical = 4.dp)
                ) {
                    Icon(
                        if (isDone) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        null,
                        tint = if (isDone) LifeRecord else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp).clickable { viewModel.toggleComplete(item) }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        item.title,
                        fontSize = 12.sp,
                        color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}