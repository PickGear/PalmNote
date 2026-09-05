package com.palmnote.ui.search

import androidx.compose.foundation.clickable
import com.palmnote.domain.model.BillType
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.app.R
import com.palmnote.data.db.entity.Anniversary
import com.palmnote.data.db.entity.Asset
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.Goal
import com.palmnote.data.db.entity.Moment
import com.palmnote.domain.model.toMoney
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToAsset: (Long) -> Unit = {},
    onNavigateToBill: (Long) -> Unit = {},
    onNavigateToGoal: (Long) -> Unit = {},
    onNavigateToAnniversary: (Long) -> Unit = {},
    onNavigateToMoment: (Long) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(start = 4.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back), tint = MaterialTheme.colorScheme.primary)
                    }
                    TextField(
                        value = state.query,
                        onValueChange = { viewModel.onQueryChanged(it) },
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = TextUnit.Unspecified
                        ),
                        minLines = 1,
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChanged("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        if (state.query.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.search_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else if (state.isSearching) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val sections = mutableListOf<SearchSection>()
            if (state.assets.isNotEmpty())
                sections.add(SearchSection("ASSETS", stringResource(R.string.search_section_assets), Icons.Outlined.Inventory2, InfoBlue))
            if (state.bills.isNotEmpty())
                sections.add(SearchSection("BILLS", stringResource(R.string.search_section_bills), Icons.Outlined.AccountBalanceWallet, AccentOrange))
            if (state.goals.isNotEmpty())
                sections.add(
                    SearchSection("GOALS", stringResource(R.string.search_section_goals), Icons.Outlined.Flag, MaterialTheme.colorScheme.primary)
                )
            if (state.anniversaries.isNotEmpty())
                sections.add(SearchSection("ANNIVERSARIES", stringResource(R.string.search_section_anniversaries), Icons.Outlined.FavoriteBorder, ModuleLife))
            if (state.moments.isNotEmpty())
                sections.add(SearchSection("MOMENTS", stringResource(R.string.search_section_moments), Icons.Outlined.AutoAwesome, Purple))
            if (sections.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.search_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    for (section in sections) {
                        val sectionTile = section
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    sectionTile.icon,
                                    contentDescription = null,
                                    tint = sectionTile.color,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    sectionTile.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        when (sectionTile.key) {
                            "ASSETS" -> items(state.assets, key = { "asset_${it.id}" }) { asset ->
                                AssetSearchItem(asset, onClick = { onNavigateToAsset(asset.id) })
                            }
                            "BILLS" -> items(state.bills, key = { "bill_${it.id}" }) { bill ->
                                BillSearchItem(bill, onClick = { onNavigateToBill(bill.id) })
                            }
                            "GOALS" -> items(state.goals, key = { "goal_${it.id}" }) { goal ->
                                GoalSearchItem(goal, onClick = { onNavigateToGoal(goal.id) })
                            }
                            "ANNIVERSARIES" -> items(state.anniversaries, key = { "anniv_${it.id}" }) { anniv ->
                                AnniversarySearchItem(anniv, onClick = { onNavigateToAnniversary(anniv.id) })
                            }
                            "MOMENTS" -> items(state.moments, key = { "moment_${it.id}" }) { moment ->
                                MomentSearchItem(moment, onClick = { onNavigateToMoment(moment.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class SearchSection(
    val key: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: androidx.compose.ui.graphics.Color
)

@Composable
private fun AssetSearchItem(asset: Asset, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(asset.name, fontWeight = FontWeight.Medium)
            if (asset.brand.isNotEmpty() || asset.model.isNotEmpty()) {
                Text(
                    "${asset.brand} ${asset.model}".trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (asset.serialNumber.isNotEmpty()) {
                Text(
                    asset.serialNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BillSearchItem(bill: Bill, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(bill.note.ifEmpty { bill.merchant }, fontWeight = FontWeight.Medium)
                Text(
                    DateUtils.formatDate(bill.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                CurrencyUtils.formatCurrency(androidx.compose.ui.platform.LocalContext.current, bill.amount.toMoney()),
                fontWeight = FontWeight.Bold,
                color = if (bill.type == BillType.EXPENSE) AccentOrange else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun GoalSearchItem(goal: Goal, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(goal.title, fontWeight = FontWeight.Medium)
            if (goal.description.isNotEmpty()) {
                Text(
                    goal.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AnniversarySearchItem(anniversary: Anniversary, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(anniversary.title, fontWeight = FontWeight.Medium)
            if (anniversary.personName.isNotEmpty()) {
                Text(
                    anniversary.personName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MomentSearchItem(moment: Moment, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(moment.title, fontWeight = FontWeight.Medium)
            if (moment.content.isNotEmpty()) {
                Text(
                    moment.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
