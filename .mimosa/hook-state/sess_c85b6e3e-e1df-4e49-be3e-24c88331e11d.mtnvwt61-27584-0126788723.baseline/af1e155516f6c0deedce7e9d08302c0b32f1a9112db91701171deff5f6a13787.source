package com.palmnote.ui.life.common
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.palmnote.app.R
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.data.db.entity.getDisplayDescription
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.domain.repository.LifeTemplateRepository
import com.palmnote.ui.components.AppBottomSheet
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.theme.ModuleLife
import com.palmnote.ui.theme.iconFromName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId


data class CategoryDetailUiState(
    val category: String = "",
    val templates: List<LifeTemplate> = emptyList(),
    val itemsByTemplate: Map<Long, List<LifeItem>> = emptyMap(),
    val weekNew: Int = 0,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    private val templateRepo: LifeTemplateRepository,
    private val itemRepo: LifeItemRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CategoryDetailUiState())
    val uiState: StateFlow<CategoryDetailUiState> = _uiState.asStateFlow()

    fun load(category: String) {
        val zone = ZoneId.systemDefault()
        val weekStart = LocalDate.now().minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        templateRepo.getAllVisibleTemplates()
            .flatMapLatest { templates ->
                val inCategory = templates.filter { it.category == category }
                if (inCategory.isEmpty()) {
                    flowOf(Triple(inCategory, emptyMap<Long, List<LifeItem>>(), 0))
                } else {
                    val flows: List<Flow<Pair<Long, List<LifeItem>>>> = inCategory.map { tpl ->
                        itemRepo.getActiveItemsByTemplate(tpl.id, 200).map { tpl.id to it }
                    }
                    combine(flows) { arrays ->
                        val merged = mutableMapOf<Long, List<LifeItem>>()
                        var newCount = 0
                        arrays.forEach { (id, items) ->
                            merged[id] = items
                            newCount += items.count { it.createdAt >= weekStart }
                        }
                        Triple(inCategory, merged, newCount)
                    }
                }
            }
            .onEach { (tpls, merged, weekNew) ->
                _uiState.update {
                    it.copy(
                        category = category,
                        templates = tpls,
                        itemsByTemplate = merged,
                        weekNew = weekNew,
                        isLoading = false
                    )
                }
            }
            .launchIn(viewModelScope)
    }
}

@Suppress("LongMethod", "LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    category: String,
    onBack: () -> Unit,
    onTemplateClick: (Long) -> Unit,
    onCreateClick: (Long) -> Unit,
    viewModel: CategoryDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(category) { viewModel.load(category) }
    var filter by remember { mutableStateOf("ALL") }
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SecondaryTopAppBar(
                title = {
                    Text(category, fontWeight = FontWeight.Bold, color = ModuleLife)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            if (state.templates.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        if (state.templates.size == 1) onCreateClick(state.templates.first().id) else showPicker = true
                    },
                    containerColor = MaterialTheme.colorScheme.secondary
                ) {
                    Icon(Icons.Default.Add, stringResource(R.string.life_category_new_item), tint = Color.White)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ModuleLife)
            }
        } else if (state.templates.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOff, null, tint = ModuleLife.copy(alpha = 0.3f), modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.life_category_detail_empty),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                CategoryHeader(state = state)
                Spacer(modifier = Modifier.height(12.dp))
                FilterChips(filter = filter, onSelect = { filter = it })
                Spacer(modifier = Modifier.height(12.dp))
                state.templates.forEach { tpl ->
                    val items = state.itemsByTemplate[tpl.id].orEmpty().filter { matchesFilter(it, filter) }
                    if (items.isNotEmpty() || filter == "ALL") {
                        CategorySectionHeader(tpl = tpl, count = items.size)
                        items.forEach { item ->
                            RichCard(
                                tpl = tpl,
                                item = item,
                                iconColor = ModuleLife,
                                variant = "AUTO",
                                onClick = { onTemplateClick(item.id) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    if (showPicker) {
        TemplatePickerSheet(
            templates = state.templates,
            onDismiss = { showPicker = false },
            onSelect = { showPicker = false; onCreateClick(it) }
        )
    }
}

@Composable
private fun CategoryHeader(state: CategoryDetailUiState) {
    val total = state.itemsByTemplate.values.sumOf { it.size }
    val icon = categoryIcon(state.category)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = ModuleLife.copy(alpha = 0.08f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(ModuleLife.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = ModuleLife, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(state.category, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = ModuleLife)
                Text(
                    stringResource(R.string.life_category_detail_count, total),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                stringResource(R.string.life_category_detail_week_new, state.weekNew),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = ModuleLife
            )
        }
    }
}

@Composable
private fun FilterChips(filter: String, onSelect: (String) -> Unit) {
    val chips = listOf(
        "ALL" to stringResource(R.string.life_category_filter_all),
        "DAILY" to stringResource(R.string.life_category_filter_daily),
        "WEEKLY" to stringResource(R.string.life_category_filter_weekly),
        "MONTHLY" to stringResource(R.string.life_category_filter_monthly)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { (key, label) ->
            FilterChip(
                selected = filter == key,
                onClick = { onSelect(key) },
                label = { Text(label, fontSize = 12.sp) }
            )
        }
    }
}

@Composable
private fun CategorySectionHeader(tpl: LifeTemplate, count: Int) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(ModuleLife, RoundedCornerShape(4.dp)))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
                tpl.displayName(),
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        Text(stringResource(R.string.life_category_detail_count, count), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun TemplatePickerSheet(
    templates: List<LifeTemplate>,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(stringResource(R.string.life_category_pick_template), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))
            templates.forEach { tpl ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onSelect(tpl.id) },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(iconFromName(tpl.icon), null, tint = ModuleLife, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tpl.displayName(), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(
                                tpl.getDisplayDescription(LocalContext.current),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun matchesFilter(item: LifeItem, filter: String): Boolean {
    if (filter == "ALL") return true
    val rec = item.recurring ?: "NONE"
    return when (filter) {
        "DAILY" -> rec.startsWith("DAILY") || rec == "WEEKDAY"
        "WEEKLY" -> rec.startsWith("WEEKLY")
        "MONTHLY" -> rec.startsWith("MONTHLY")
        else -> true
    }
}

@Composable
private fun categoryIcon(category: String): ImageVector = when (category) {
    stringResource(R.string.life_category_plan) -> Icons.Default.Star
    stringResource(R.string.life_category_time) -> Icons.Default.CalendarMonth
    else -> Icons.Default.AutoStories
}
