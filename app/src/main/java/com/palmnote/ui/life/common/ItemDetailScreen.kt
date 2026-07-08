package com.palmnote.ui.life.common

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.R
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.ui.components.AppDialog
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.ui.theme.*
import com.palmnote.ui.utils.formatTimeAgo
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    item: LifeItem?,
    template: LifeTemplate?,
    onBack: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    viewModel: ItemDetailViewModel? = null
) {
    val detailColor = try { Color(android.graphics.Color.parseColor(template?.color ?: "#7BC4A0")) } catch (_: Exception) { ModuleLife }
    val json = remember { Json { ignoreUnknownKeys = true } }

    var showDepositDialog by remember { mutableStateOf(false) }
    var depositAmount by remember { mutableStateOf("") }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var linksExpanded by remember { mutableStateOf(false) }
    var showLinkSelector by remember { mutableStateOf(false) }
    val state by viewModel?.uiState?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(ItemDetailUiState()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (showDeleteDialog) {
        AppDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.life_item_detail_delete_confirm), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.life_item_detail_delete_hint)) },
            confirmButton = { TextButton(onClick = { showDeleteDialog = false; onDelete() }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showDepositDialog && item != null) {
        DepositSheet(
            savingItemName = item.title,
            onConfirm = { amt -> viewModel?.depositAmount(amt); showDepositDialog = false },
            onDismiss = { showDepositDialog = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(item?.title ?: stringResource(R.string.life_item_detail_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back)) } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.edit)) }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) { Icon(Icons.Default.MoreVert, "\u66F4\u591A") }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            DropdownMenuItem(text = { Text("\u5220\u9664") }, onClick = { showMoreMenu = false; showDeleteDialog = true })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (item == null || template == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { Text("\u6570\u636E\u4E0D\u5B58\u5728", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                val statuses = try {
                    val config = json.decodeFromString<JsonObject>(template.statusFlowConfig)
                    val arr = config["statuses"] as? JsonArray ?: JsonArray(emptyList())
                    arr.map { (it as? JsonObject)?.let { obj -> (obj["key"] as? JsonPrimitive)?.content to (obj["label"] as? JsonPrimitive)?.content } }.filterNotNull()
                } catch (_: Exception) { emptyList() }
                statuses.forEach { (key, label) ->
                    val isActive = item.status == key
                    Box(modifier = Modifier
                        .padding(end = 6.dp)
                        .background(if (isActive) detailColor.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(8.dp))
                        .border(1.dp, if (isActive) detailColor else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .clickable {
                            if (!isActive) {
                                viewModel?.updateStatus(key ?: "ACTIVE")
                                scope.launch { snackbarHostState.showSnackbar("\u5DF2\u66F4\u65B0\u4E3A\u300C${label ?: key}\u300D") }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(label ?: key ?: "", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (isActive) detailColor else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val data = try { json.decodeFromString<JsonObject>(item.fieldsData) } catch (_: Exception) { null }
            val cur = data?.let {
                (it["current_page"] as? JsonPrimitive)?.content?.toIntOrNull()
                    ?: (it["currentPage"] as? JsonPrimitive)?.content?.toIntOrNull()
                    ?: (it["currentAmount"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
                    ?: (it["saved_amount"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
                    ?: (it["current"] as? JsonPrimitive)?.content?.toIntOrNull()
                    ?: 0
            } ?: 0
            val tot = data?.let {
                (it["total_pages"] as? JsonPrimitive)?.content?.toIntOrNull()
                    ?: (it["totalPages"] as? JsonPrimitive)?.content?.toIntOrNull()
                    ?: (it["targetAmount"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
                    ?: (it["target_amount"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
                    ?: (it["total"] as? JsonPrimitive)?.content?.toIntOrNull()
                    ?: 0
            } ?: 0
            val progress = if (tot > 0) (cur.toFloat() / tot).coerceIn(0f, 1f) else 0f

            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                Box(contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(100.dp)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = 6.dp.toPx()
                            val radius = (size.minDimension - stroke) / 2
                            drawCircle(color = detailColor.copy(alpha = 0.12f), radius = radius, center = Offset(size.width / 2, size.height / 2))
                            drawArc(color = detailColor, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false, style = Stroke(width = stroke, cap = StrokeCap.Round), topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke))
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(progress * 100).toInt()}%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = detailColor)
                        if (tot > 0) Text("$cur / $tot", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else Text("\u8FDB\u5EA6", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                val detailFields: List<FieldDef> = try {
                    val arr = json.decodeFromString<JsonArray>(template.fieldsConfig)
                    arr.map { el ->
                        val obj = el.jsonObject
                        FieldDef(
                            key = obj["key"]?.jsonPrimitive?.content ?: "",
                            label = obj["label"]?.jsonPrimitive?.content ?: "",
                            type = obj["type"]?.jsonPrimitive?.content ?: "TEXT",
                            unit = obj["unit"]?.jsonPrimitive?.content ?: "",
                            options = try { (obj["options"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList() } catch (_: Exception) { emptyList() }
                        )
                    }
                } catch (_: Exception) { emptyList() }
                val detailFieldMap: Map<String, JsonElement> = data?.entries?.associate { it.key to it.value } ?: emptyMap()
                if (detailFields.isEmpty() && data != null) {
                    data.entries.forEach { (key, value) ->
                        if (value is JsonPrimitive && value.content.isNotEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(key, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.35f))
                                Text(value.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(0.65f))
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                } else {
                    detailFields.forEach { field ->
                        val value = detailFieldMap[field.key]?.let { if (it is JsonPrimitive) it.content else "" } ?: ""
                        if (value.isNotEmpty()) {
                            FieldDisplay(field, value)
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (item.note.isNotBlank()) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text("\u5907\u6CE8", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(item.note, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { linksExpanded = !linksExpanded }) {
                    Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("\u5173\u8054\u6570\u636E", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.weight(1f))
                    val linkCount = state.links.size
                    Text(if (linkCount > 99) "99+" else "${linkCount} \u9879", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(if (linksExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                AnimatedVisibility(visible = linksExpanded, enter = expandVertically(animationSpec = tween(200)) + fadeIn(tween(200)), exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(200))) {
                    Column(modifier = Modifier.animateContentSize(tween(200))) {
                        if (state.links.isEmpty()) {
                            Text("\u6682\u65E0\u5173\u8054", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        } else {
                            state.links.forEach { link ->
                                Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("\u5173\u8054 ${link.targetType.name}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("\u70B9\u51FB\u6DFB\u52A0\u5173\u8054\u8D26\u5355\u6216\u7269\u54C1", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { showLinkSelector = true })
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("\u65F6\u95F4", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text("\u521B\u5EFA\u4E8E ${formatTimeAgo(item.createdAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.updatedAt > 0 && item.updatedAt != item.createdAt) {
                    Text("\u66F4\u65B0\u4E8E ${formatTimeAgo(item.updatedAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val isSavingPlan = template?.icon == "Savings"

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isSavingPlan) {
                    Button(
                        onClick = { showDepositDialog = true },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = detailColor)
                    ) {
                        Text("\u5B58\u5165\u4E00\u7B14", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
                OutlinedButton(
                    onClick = { viewModel?.archive(); scope.launch { snackbarHostState.showSnackbar("\u6761\u76EE\u5DF2\u5F52\u6863\uFF0C\u53EF\u5728\u56DE\u6536\u7AD9\u627E\u56DE") } },
                    modifier = Modifier.then(if (isSavingPlan) Modifier.weight(1f) else Modifier.fillMaxWidth()).height(40.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("\u5F52\u6863", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    if (showLinkSelector && item != null) {
        LinkSelectorSheet(sourceItemId = item.id, onDismiss = { showLinkSelector = false })
    }
}
