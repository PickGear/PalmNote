package com.palmnote.ui.life.time.countdown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassBottom
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.domain.util.DateUtils
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.theme.*
import com.palmnote.ui.life.common.SwipeableItem
import com.palmnote.ui.life.common.LifeLazyList
import com.palmnote.ui.components.EmptyState
import com.palmnote.ui.life.time.common.TimeListConfig
import com.palmnote.ui.life.time.common.TimeListScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun CountdownListScreen(templateId: Long, onBack: () -> Unit, onItemClick: (Long) -> Unit, onCreateClick: () -> Unit = {}, viewModel: CountdownViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(templateId) { viewModel.load(templateId) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val config = TimeListConfig(
        title = stringResource(R.string.life_countdown_title),
        accentColor = LifeCountdown,
        emptyIcon = Icons.Default.HourglassBottom,
        emptyTitle = stringResource(R.string.life_empty_countdown),
        emptySubtitle = stringResource(R.string.life_empty_countdown_subtitle),
        emptyActionText = stringResource(R.string.life_empty_countdown_action),
        deleteConfirmText = stringResource(R.string.life_confirm_delete_countdown)
    )
    
    var showClearExpired by remember { mutableStateOf(false) }
    
    if (showClearExpired) {
        AppDialog(
            onDismissRequest = { showClearExpired = false },
            title = { Text(stringResource(R.string.countdown_clear_expired_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.countdown_clear_expired_confirm)) },
            confirmButton = { TextButton(onClick = {
                coroutineScope.launch {
                    val expiredItems = state.items.filter { item ->
                        val dateMillis = try {
                            val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                            (obj["target_date"] as? JsonPrimitive)?.content?.toLongOrNull()
                                ?: (obj["targetDate"] as? JsonPrimitive)?.content?.toLongOrNull()
                        } catch (_: Exception) { null }
                        dateMillis != null && ChronoUnit.DAYS.between(LocalDate.now(), java.time.Instant.ofEpochMilli(dateMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()) < 0
                    }
                    expiredItems.forEach { item -> viewModel.deleteItem(item.id) }
                    showClearExpired = false
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.countdown_clear_expired_success, expiredItems.size),
                        duration = SnackbarDuration.Short
                    )
                }
            }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showClearExpired = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(config.title, fontWeight = FontWeight.Bold, color = config.accentColor) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back)) } },
                actions = {
                    IconButton(onClick = onCreateClick) { Icon(Icons.Default.Add, stringResource(R.string.life_new_create)) }
                    if (state.items.isNotEmpty()) {
                        IconButton(onClick = { showClearExpired = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.countdown_clear_expired_title)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { 
                CircularProgressIndicator(color = config.accentColor) 
            }
            return@Scaffold
        }
        if (state.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = config.emptyIcon,
                    title = config.emptyTitle,
                    subtitle = config.emptySubtitle,
                    actionText = config.emptyActionText,
                    onActionClick = onCreateClick
                )
            }
        } else {
            LifeLazyList(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.items.size) { index ->
                    val item = state.items[index]
                    val dateMillis = try {
                        val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                        (obj["target_date"] as? JsonPrimitive)?.content?.toLongOrNull()
                            ?: (obj["targetDate"] as? JsonPrimitive)?.content?.toLongOrNull()
                    } catch (_: Exception) { null }
                    val days = if (dateMillis != null) {
                        ChronoUnit.DAYS.between(LocalDate.now(), DateUtils.millisToLocalDate(dateMillis))
                    } else null
                    val isExpired = days != null && days < 0

                    SwipeableItem(onDelete = { viewModel.deleteItem(item.id) }) {
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
                                            val date = DateUtils.millisToLocalDate(dateMillis)
                                            val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                            Text(if (isExpired) stringResource(R.string.life_countdown_expired_text, dateStr) else stringResource(R.string.life_countdown_active_text, dateStr), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
