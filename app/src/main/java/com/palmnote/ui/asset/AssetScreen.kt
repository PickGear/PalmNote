package com.palmnote.ui.asset

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.palmnote.PalmNoteApp
import com.palmnote.ui.components.simpleViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import com.palmnote.R
import com.palmnote.data.db.entity.Asset
import com.palmnote.data.db.entity.getWarrantyStatusText
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*

fun getCategoryIcon(category: String): CategoryItem {
    return assetCategoryItems.find { it.name == category } ?: assetCategoryItems.last()
}

private val statusColorMap = mapOf("HELD" to StatusHeld, "AWAY" to StatusAway, "REMOVED" to StatusRemoved)
fun getStatusColor(status: String): Color = statusColorMap[status] ?: StatusHeld

@Composable
fun getStatusText(status: String): String = when (status) {
    "HELD" -> stringResource(R.string.asset_held)
    "AWAY" -> stringResource(R.string.asset_away)
    "REMOVED" -> stringResource(R.string.asset_removed)
    else -> stringResource(R.string.asset_held)
}

@Composable
fun getAcquisitionText(type: String): String = when (type) {
    "PURCHASE" -> stringResource(R.string.acquisition_purchase)
    "GIFT" -> stringResource(R.string.acquisition_gift)
    "LOTTERY" -> stringResource(R.string.acquisition_lottery)
    "PRIZE" -> stringResource(R.string.acquisition_prize)
    "INHERITANCE" -> stringResource(R.string.acquisition_inheritance)
    "OTHER" -> stringResource(R.string.acquisition_other)
    else -> type
}

@Composable
fun getAcquisitionColor(type: String): Color = when (type) {
    "PURCHASE" -> AccentOrange
    "GIFT" -> Purple
    "LOTTERY" -> DeepOrange
    "PRIZE" -> Amber
    "INHERITANCE" -> Brown
    "OTHER" -> ModuleSettings
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun getCostText(costMode: String, purchasePrice: Double, useCount: Int, daysOwned: Int): String = when (costMode) {
    "PER_USE" -> if (useCount > 0) stringResource(R.string.asset_cost_per_use_price, String.format("%.1f", purchasePrice / useCount)) else stringResource(R.string.asset_cost_per_use_none)
    else -> stringResource(R.string.asset_cost_daily_price, String.format("%.1f", purchasePrice / daysOwned))
}

@Composable
fun AssetScreen(
    onNavigateToDetail: (Long) -> Unit = {},
    onNavigateToAdd: () -> Unit = {},
    viewModel: AssetViewModel = simpleViewModel { PalmNoteApp.container.assetViewModel() }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var showSearch by remember { mutableStateOf(false) }
    BackHandler(enabled = showSearch) { showSearch = false; viewModel.setSearchQuery("") }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .exclude(WindowInsets.navigationBars),
        topBar = {
            CompactTopAppBar(
                title = {
                    if (showSearch) {
                        ModuleSearchBar(
                            query = state.searchQuery,
                            onQueryChange = { viewModel.setSearchQuery(it) },
                            onClear = { viewModel.setSearchQuery("") },
                            placeholder = stringResource(R.string.search),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    focusManager.clearFocus()
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = stringResource(R.string.settings_items),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = ModuleItem
                            )
                        }
                    }
                },
                actions = {
                    if (showSearch) {
                        TextButton(onClick = { showSearch = false; viewModel.setSearchQuery("") }, modifier = Modifier.padding(end = 4.dp)) {
                            Text(stringResource(R.string.cancel), style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.search), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAdd,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = Color.White,
                shape = MaterialTheme.shapes.large,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.asset_add), fontWeight = FontWeight.Medium) }
            )
        }
    ) { padding ->
        val isGridView = state.isGridView
        val listState = rememberLazyListState()
        var showScrollToTop by remember { mutableStateOf(false) }
        val scrolledPast by remember {
            derivedStateOf { listState.firstVisibleItemIndex > 1 || listState.firstVisibleItemScrollOffset > 100 }
        }
        LaunchedEffect(listState.isScrollInProgress, scrolledPast) {
            if (listState.isScrollInProgress && scrolledPast) {
                showScrollToTop = true
            } else if (scrolledPast) {
                kotlinx.coroutines.delay(600)
                showScrollToTop = false
            } else {
                showScrollToTop = false
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
            // Summary Cards (animated hide during search, outside LazyColumn for smooth animation)
            AnimatedVisibility(
                visible = !showSearch,
                enter = fadeIn(tween(120)) + expandVertically(tween(120)),
                exit = fadeOut(tween(300)) + shrinkVertically(tween(300))
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ModuleCard(
                            tint = assetTint(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.asset_held),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = CurrencyUtils.formatCompact(state.heldValue),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.asset_count, state.heldCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        ModuleCard(
                            tint = billTint(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.asset_away),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.asset_count, state.awayCount),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = AccentOrange
                            )
                            Text(
                                text = "${stringResource(R.string.asset_removed)} ${stringResource(R.string.asset_count, state.removedCount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            val assetIndexMap = remember(state.filteredAssets) {
                state.filteredAssets.withIndex().associate { (i, a) -> a.id to i }
            }
            // Filter Bar (fixed outside LazyColumn)
            Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
            // Status + Category Filter Dropdowns + View Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status filter dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        var statusExpanded by remember { mutableStateOf(false) }
                        var statusBoxHeight by remember { mutableStateOf(0) }
                        val statusFilters = listOf(
                            AssetFilter.ALL to stringResource(R.string.asset_all),
                            AssetFilter.HELD to stringResource(R.string.asset_held),
                            AssetFilter.AWAY to stringResource(R.string.asset_away),
                            AssetFilter.REMOVED to stringResource(R.string.asset_removed)
                        )
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                                .clip(MaterialTheme.shapes.medium)
                                .onGloballyPositioned { statusBoxHeight = it.size.height }
                                .clickable { statusExpanded = true },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = statusFilters.find { it.first == state.selectedStatus }?.second ?: stringResource(R.string.asset_status_filter),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (state.selectedStatus != AssetFilter.ALL) FontWeight.Medium else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = if (statusExpanded) stringResource(R.string.bill_collapse) else stringResource(R.string.bill_expand),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(if (statusExpanded) 180f else 0f),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (statusExpanded) {
                            Popup(
                                onDismissRequest = { statusExpanded = false },
                                alignment = Alignment.TopEnd,
                                offset = IntOffset(0, statusBoxHeight),
                                properties = PopupProperties(focusable = true)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    Column(Modifier.wrapContentWidth()) {
                                        statusFilters.forEach { (filter, label) ->
                                            Text(
                                                text = label,
                                                modifier = Modifier
                                                    .clickable { viewModel.setStatusFilter(filter); statusExpanded = false }
                                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Category filter dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        var catExpanded by remember { mutableStateOf(false) }
                        var catBoxHeight by remember { mutableStateOf(0) }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                                .clip(MaterialTheme.shapes.medium)
                                .onGloballyPositioned { catBoxHeight = it.size.height }
                                .clickable { catExpanded = true },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = state.selectedCategory?.let { com.palmnote.ui.components.getCategoryName(it, androidx.compose.ui.platform.LocalContext.current) } ?: stringResource(R.string.bill_category),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (state.selectedCategory != null) FontWeight.Medium else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = if (catExpanded) stringResource(R.string.bill_collapse) else stringResource(R.string.bill_expand),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(if (catExpanded) 180f else 0f),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (catExpanded) {
                            Popup(
                                onDismissRequest = { catExpanded = false },
                                alignment = Alignment.TopEnd,
                                offset = IntOffset(0, catBoxHeight),
                                properties = PopupProperties(focusable = true)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    Column(Modifier.wrapContentWidth()) {
                                        Text(
                                            text = stringResource(R.string.asset_all_categories),
                                            modifier = Modifier
                                                .clickable { viewModel.setCategoryFilter(null); catExpanded = false }
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        state.categoryDistribution.forEach { cat ->
                                            Text(
                                                text = "${com.palmnote.ui.components.getCategoryName(cat.category, androidx.compose.ui.platform.LocalContext.current)} (${cat.count})",
                                                modifier = Modifier
                                                    .clickable { viewModel.setCategoryFilter(cat.category); catExpanded = false }
                                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Sort filter dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        var sortExpanded by remember { mutableStateOf(false) }
                        var sortBoxHeight by remember { mutableStateOf(0) }
                        val sortOptions = listOf(
                            SortOption.RECENT to stringResource(R.string.asset_sort_recent),
                            SortOption.NAME to stringResource(R.string.asset_sort_name),
                            SortOption.PRICE to stringResource(R.string.asset_sort_price),
                            SortOption.DAILY_COST to stringResource(R.string.asset_sort_daily_cost),
                            SortOption.DATE to stringResource(R.string.asset_sort_date)
                        )
                        val isDefault = state.selectedSort == SortOption.RECENT
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                                .clip(MaterialTheme.shapes.medium)
                                .onGloballyPositioned { sortBoxHeight = it.size.height }
                                .clickable { sortExpanded = true },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (!isDefault) {
                                        "${sortOptions.find { it.first == state.selectedSort }?.second ?: ""}${if (state.sortAscending) " ↑" else " ↓"}"
                                    } else stringResource(R.string.asset_sort),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (!isDefault) FontWeight.Medium else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = if (sortExpanded) stringResource(R.string.bill_collapse) else stringResource(R.string.bill_expand),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(if (sortExpanded) 180f else 0f),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (sortExpanded) {
                            Popup(
                                onDismissRequest = { sortExpanded = false },
                                alignment = Alignment.TopEnd,
                                offset = IntOffset(0, sortBoxHeight),
                                properties = PopupProperties(focusable = true)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    Column(Modifier.wrapContentWidth()) {
                                        sortOptions.forEach { (option, label) ->
                                            val isActive = option == state.selectedSort
                                            Text(
                                                text = if (isActive) {
                                                    "${label} ${if (state.sortAscending) "↑" else "↓"}"
                                                } else label,
                                                modifier = Modifier
                                                    .clickable { viewModel.setSortFilter(option); sortExpanded = false }
                                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // View toggle button
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { viewModel.toggleViewMode() },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.Apps,
                            contentDescription = if (isGridView) stringResource(R.string.asset_list_view) else stringResource(R.string.asset_grid_view),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .nestedScroll(object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            focusManager.clearFocus()
                            return Offset.Zero
                        }
                    })
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            focusManager.clearFocus()
                        }
                    },
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

            // Asset List
            if (state.filteredAssets.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Inventory2,
                        title = if (state.searchQuery.isNotEmpty()) stringResource(R.string.asset_not_found) else stringResource(R.string.asset_no_items),
                        subtitle = if (state.searchQuery.isNotEmpty()) stringResource(R.string.asset_try_other_keywords) else stringResource(R.string.asset_add_first_item),
                        tint = InfoBlue
                    )
                }
            } else if (isGridView) {
                item {
                    var rowIndex by remember { mutableIntStateOf(0) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.filteredAssets.chunked(2).forEach { rowAssets ->
                            val currentRow = rowIndex++
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowAssets.forEachIndexed { _, asset ->
                                    GridAssetCard(
                                        modifier = Modifier.weight(1f),
                                        asset = asset,
                                        onClick = { onNavigateToDetail(asset.id) },
                                        animIndex = currentRow
                                    )
                                }
                                if (rowAssets.size < 2) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            } else {
                items(state.filteredAssets, key = { it.id }) { asset ->
                    AnimatedCard(index = (assetIndexMap[asset.id] ?: 0).coerceAtMost(10), instant = remember(asset.id) { listState.isScrollInProgress }) {
                        EnhancedAssetCard(
                            asset = asset,
                            onClick = { onNavigateToDetail(asset.id) }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
            }
            }
            val scope = rememberCoroutineScope()
            AnimatedVisibility(
                visible = showScrollToTop,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 100.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.large)
                        .clickable { scope.launch { listState.animateScrollToItem(0) } },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.asset_back_to_top),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EnhancedAssetCard(
    asset: Asset,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val statusColor = getStatusColor(asset.status)
    val statusText = getStatusText(asset.status)
    val acquisitionText = getAcquisitionText(asset.acquisitionType)

    val acquisitionColor = getAcquisitionColor(asset.acquisitionType)
    val catInfo = getCategoryIcon(asset.category)
    val daysOwned = DateUtils.getDaysSince(asset.effectiveDate).coerceAtLeast(1)

    ModuleCard(
        tint = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail / Category Icon + Quantity badge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(catInfo.color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    val firstImage = asset.images.toImageList().firstOrNull()
                    if (firstImage != null) {
                        AsyncImage(
                            model = firstImage,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = catInfo.icon,
                            contentDescription = null,
                            tint = catInfo.color,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                if (asset.quantity > 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = AccentOrange.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "×${asset.quantity}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentOrange,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Content
            Column(modifier = Modifier.weight(1f)) {
                // Row 1: Name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = asset.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (asset.warrantyExpireDate != null) {
                            StatusChip(
                                text = asset.getWarrantyStatusText(context),
                            color = ModuleItem
                            )
                        }
                        StatusChip(text = statusText, color = statusColor)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Row 2: Acquisition type + Category + Days Used
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = acquisitionColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = acquisitionText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = acquisitionColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = com.palmnote.ui.components.getCategoryName(asset.category, LocalContext.current),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.asset_days_used, daysOwned),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Row 3: Cost info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val costText = getCostText(asset.costMode, asset.purchasePrice, asset.useCount, daysOwned)

                    Text(
                        text = costText,
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentOrange,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = CurrencyUtils.formatCurrency(asset.purchasePrice),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Row 4: Location + Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (asset.location.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = asset.location,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Text(
text = DateUtils.formatDisplayYearDate(context, asset.effectiveDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun GridAssetCard(
    modifier: Modifier = Modifier,
    asset: Asset,
    onClick: () -> Unit,
    animIndex: Int = 0
) {
    val context = LocalContext.current
    val statusColor = getStatusColor(asset.status)
    val statusText = getStatusText(asset.status)
    val acquisitionText = getAcquisitionText(asset.acquisitionType)
    val acquisitionColor = getAcquisitionColor(asset.acquisitionType)
    val catInfo = getCategoryIcon(asset.category)
    val daysOwned = DateUtils.getDaysSince(asset.effectiveDate).coerceAtLeast(1)
    val costText = getCostText(asset.costMode, asset.purchasePrice, asset.useCount, daysOwned)
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(animIndex * 60L)
        animProgress.animateTo(1f, animationSpec = tween(300, easing = FastOutSlowInEasing))
    }

    Surface(
        modifier = modifier
            .graphicsLayer {
                alpha = animProgress.value
                translationY = (1f - animProgress.value) * 12.dp.toPx()
            }
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Image/Avatar
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(catInfo.color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    val firstImage = asset.images.toImageList().firstOrNull()
                    if (firstImage != null) {
                        AsyncImage(
                            model = firstImage,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = catInfo.icon,
                            contentDescription = null,
                            tint = catInfo.color,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Content
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = asset.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        if (asset.warrantyExpireDate != null) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                text = if (asset.isWarrantyValid) stringResource(R.string.asset_warranty_valid) else stringResource(R.string.asset_warranty_expired),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                            }
                        }
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = statusColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = statusText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = statusColor,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (asset.quantity > 1) {
                            Text(
                                text = "×${asset.quantity}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentOrange,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        Text(
                            text = stringResource(R.string.asset_used_days, daysOwned),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = acquisitionColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = acquisitionText,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = acquisitionColor,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = com.palmnote.ui.components.getCategoryName(asset.category, LocalContext.current),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = costText,
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentOrange,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = DateUtils.formatDisplayYearDate(context, asset.effectiveDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = CurrencyUtils.formatCurrency(asset.purchasePrice),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
