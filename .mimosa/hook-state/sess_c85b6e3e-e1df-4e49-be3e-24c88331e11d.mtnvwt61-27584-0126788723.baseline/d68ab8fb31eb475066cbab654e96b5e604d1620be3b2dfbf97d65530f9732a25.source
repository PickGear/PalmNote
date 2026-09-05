package com.palmnote.ui.life.common
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.data.db.entity.getDisplayDescription
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.domain.repository.LifeTemplateRepository
import com.palmnote.ui.theme.ModuleLife
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val recentSearches: List<String> = emptyList(),
    val items: List<LifeItem> = emptyList(),
    val templates: List<LifeTemplate> = emptyList(),
    val templateMap: Map<Long, LifeTemplate> = emptyMap(),
    val isLoading: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val itemRepo: LifeItemRepository,
    private val templateRepo: LifeTemplateRepository,
    private val prefs: PreferencesManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()

    private var queryJob: Job? = null

    init {
        prefs.recentSearches.onEach { rs -> _uiState.update { it.copy(recentSearches = rs) } }.launchIn(viewModelScope)
        templateRepo.getAllVisibleTemplates().onEach { tpls ->
            _uiState.update { it.copy(templateMap = tpls.associateBy { t -> t.id }) }
        }.launchIn(viewModelScope)
    }

    fun onQueryChange(q: String) {
        _uiState.update { it.copy(query = q) }
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            val text = q.trim()
            if (text.isEmpty()) {
                _uiState.update { it.copy(items = emptyList(), templates = emptyList(), isLoading = false) }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true) }
            try {
                val items = itemRepo.searchItems(text).first()
                val templates = templateRepo.searchTemplates(text).first()
                _uiState.update { it.copy(items = items, templates = templates, isLoading = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun saveQuery() {
        val text = _uiState.value.query.trim()
        if (text.isEmpty()) return
        viewModelScope.launch { prefs.addRecentSearch(text) }
    }
}

@Composable
internal fun LifeSearchContent(
    state: SearchUiState,
    viewModel: SearchViewModel,
    onItemClick: (Long) -> Unit,
    onTemplateClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.query.isBlank()) {
        RecentSearches(state = state, onPick = { q -> viewModel.onQueryChange(q); viewModel.saveQuery() }, modifier = modifier)
    } else if (state.isLoading) {
        Box(modifier = modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (state.items.isEmpty() && state.templates.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().padding(top = 48.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Default.Search,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.life_search_no_result), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier.fillMaxSize()
        ) {
            if (state.templates.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.life_search_templates)) }
                items(state.templates, key = { "tpl_${it.id}" }) { tpl ->
                    TemplateResultRow(tpl = tpl, onClick = { onTemplateClick(tpl.id) })
                }
            }
            if (state.items.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.life_search_items)) }
                items(state.items, key = { "item_${it.id}" }) { item ->
                    val tpl = state.templateMap[item.templateId]
                    ItemResultRow(item = item, templateName = tpl?.displayName(), onClick = { onItemClick(item.id) })
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentSearches(state: SearchUiState, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    if (state.recentSearches.isEmpty()) {
        Text(
            stringResource(R.string.life_search_empty_hint),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxWidth().padding(top = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    } else {
        Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(
                stringResource(R.string.life_search_recent),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.recentSearches.forEach { keyword ->
                    SuggestionChip(onClick = { onPick(keyword) }, label = { Text(keyword, fontSize = 12.sp) })
                }
            }
        }
    }
}

@Composable
private fun ItemResultRow(item: LifeItem, templateName: String?, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.medium).background(ModuleLife.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (item.status == "COMPLETED") "\u2713" else "",
                    fontSize = 14.sp,
                    color = ModuleLife
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(templateName, formatRelativeTime(LocalContext.current, item.updatedAt)).joinToString(" \u00B7 "),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TemplateResultRow(tpl: LifeTemplate, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = ModuleLife.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tpl.displayName(), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    tpl.getDisplayDescription(LocalContext.current),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}
