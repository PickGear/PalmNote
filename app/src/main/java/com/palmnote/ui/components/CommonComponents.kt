package com.palmnote.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray
import com.palmnote.R
import com.palmnote.ui.theme.*
import kotlinx.coroutines.delay
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import com.palmnote.domain.util.DateUtils

val categoryColorOptions = listOf(
    "#4285F4", "#34A853", "#FBBC04", "#EA4335", "#FF6D00",
    "#9C27B0", "#E91E63", "#795548", "#607D8B", "#00BCD4",
    "#FF9800", "#4CAF50", "#9E9E9E"
)

@Composable
fun ModuleCard(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = tint),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            content = content
        )
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    icon: ImageVector? = null,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = AccentOrange,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    height: Dp = 8.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .clip(RoundedCornerShape(height / 2))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(color.copy(alpha = 0.8f), color)
                    )
                )
        )
    }
}

@Composable
fun MiniStat(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = tint.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        if (actionText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(16.dp))
            GradientButton(text = actionText, onClick = onActionClick)
        }
    }
}

@Composable
fun AnimatedCard(
    modifier: Modifier = Modifier,
    index: Int = 0,
    instant: Boolean = false,
    content: @Composable () -> Unit
) {
    val visible = remember { mutableStateOf(false) }
    val animDuration = if (instant) 0 else 300

    LaunchedEffect(Unit) {
        if (!instant) delay(index * 50L)
        visible.value = true
    }

    val offsetY by animateDpAsState(
        targetValue = if (visible.value) 0.dp else 20.dp,
        animationSpec = tween(durationMillis = animDuration, easing = FastOutSlowInEasing),
        label = "card_offset"
    )

    val alpha by animateFloatAsState(
        targetValue = if (visible.value) 1f else 0f,
        animationSpec = tween(durationMillis = animDuration),
        label = "card_alpha"
    )

    Box(
        modifier = modifier
            .offset(y = offsetY)
            .graphicsLayer(alpha = alpha)
    ) {
        content()
    }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentOrange,
            contentColor = Color.White
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun MonthSelector(
    currentMonth: String,
    onMonthChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val parts = currentMonth.split("-")
    var year by remember(currentMonth) { mutableIntStateOf(parts[0].toIntOrNull() ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)) }
    var month by remember(currentMonth) { mutableIntStateOf(parts[1].toIntOrNull() ?: java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1) }

    val pattern = stringResource(R.string.date_format_display_month)
    val formatter = remember(pattern) { java.time.format.DateTimeFormatter.ofPattern(pattern) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            month--
            if (month < 1) { month = 12; year-- }
            onMonthChange("$year-${month.toString().padStart(2, '0')}")
        }) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.common_previous_month))
        }

        Text(
            text = java.time.YearMonth.of(year, month).format(formatter),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        IconButton(onClick = {
            month++
            if (month > 12) { month = 1; year++ }
            onMonthChange("$year-${month.toString().padStart(2, '0')}")
        }) {
            Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.common_next_month))
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactTopAppBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    backgroundColor: Color = MaterialTheme.colorScheme.background
) {
    CompactTopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        navigationIcon = navigationIcon,
        actions = actions,
        backgroundColor = backgroundColor
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactTopAppBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    windowInsets: WindowInsets = WindowInsets.statusBars
) {
    TopAppBar(
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = backgroundColor
        ),
        windowInsets = windowInsets
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecondaryTopAppBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    backgroundColor: Color = MaterialTheme.colorScheme.background
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        navigationIcon = navigationIcon,
        actions = actions,
        colors = colors,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecondaryTopAppBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    backgroundColor: Color = MaterialTheme.colorScheme.background
) {
    TopAppBar(
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = colors,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = BottomSheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit = {},
    dismissButton: @Composable () -> Unit = {},
    shape: Shape = DialogShape,
    containerColor: Color = MaterialTheme.colorScheme.surface
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { visible = true }

    Dialog(
        onDismissRequest = { visible = false; onDismissRequest() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(spring(dampingRatio = 0.8f, stiffness = 300f)) + fadeIn(tween(150)),
            exit = scaleOut(targetScale = 0.9f) + fadeOut(tween(100))
        ) {
            Surface(
                shape = shape,
                color = containerColor,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 24.dp)
                    .imePadding()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    if (title != null) {
                        Box(modifier = Modifier.padding(bottom = 8.dp)) { title() }
                    }
                    if (text != null) {
                        Box(modifier = Modifier.padding(bottom = 16.dp)) { text() }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dismissButton()
                        confirmButton()
                    }
                }
            }
        }
    }
}

@Composable
fun AppSaveButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    text: String = stringResource(R.string.save),
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
    ) {
        Text(text, fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

@Stable
data class CategoryItem(val name: String, val icon: ImageVector, val color: Color)

@Composable
fun CategoryPicker(
    selected: String,
    onSelected: (String) -> Unit,
    categories: List<CategoryItem>,
    rows: Int = 2,
    columns: Int = 5,
    onManageCategories: (() -> Unit)? = null,
    getDisplayName: ((String) -> String)? = null,
    modifier: Modifier = Modifier
) {
    var showPopup by remember { mutableStateOf(false) }
    val visibleCount = rows * columns - 1
    val visibleCategories = categories.take(visibleCount.coerceAtMost(categories.size))
    val hasMore = categories.size > visibleCount

    Column(modifier = modifier) {
        val chunked = visibleCategories.chunked(columns)
        val displayChunked = if (hasMore && chunked.isNotEmpty()) chunked.dropLast(1) else chunked
        displayChunked.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowItems.forEach { cat ->
                    CategoryGridItem(
                        name = getDisplayName?.invoke(cat.name) ?: cat.name,
                        icon = cat.icon,
                        color = cat.color,
                        isSelected = selected == cat.name,
                        onClick = { onSelected(cat.name) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size < columns) {
                    repeat(columns - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (hasMore) {
            val lastRowItems = chunked.last()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                lastRowItems.forEach { cat ->
                    CategoryGridItem(
                        name = getDisplayName?.invoke(cat.name) ?: cat.name,
                        icon = cat.icon,
                        color = cat.color,
                        isSelected = selected == cat.name,
                        onClick = { onSelected(cat.name) },
                        modifier = Modifier.weight(1f)
                    )
                }
                CategoryGridItem(
                    name = stringResource(R.string.common_more),
                    icon = Icons.Outlined.MoreHoriz,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    isSelected = false,
                    onClick = { showPopup = true },
                    modifier = Modifier.weight(1f)
                )
                val remaining = columns - lastRowItems.size - 1
                if (remaining > 0) {
                    repeat(remaining) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    // More popup
    if (showPopup) {
        AppDialog(
            onDismissRequest = { showPopup = false },
            title = {
                Text(
                    text = stringResource(R.string.common_select_category),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(categories.size, key = { "${categories[it].name}_$it" }) { index ->
                        val cat = categories[index]
                        val displayName = getDisplayName?.invoke(cat.name) ?: cat.name
                        val isSelected = selected == cat.name
                        CategoryGridItem(
                            name = displayName,
                            icon = cat.icon,
                            color = cat.color,
                            isSelected = isSelected,
                            onClick = {
                                onSelected(cat.name)
                                showPopup = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onManageCategories != null) {
                        TextButton(onClick = { showPopup = false; onManageCategories() }) {
                            Text(stringResource(R.string.common_manage_categories), color = AccentOrange)
                        }
                    }
                    TextButton(onClick = { showPopup = false }) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        )
    }
}

@Composable
fun CategoryGridItem(
    name: String,
    icon: ImageVector,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    if (isSelected) color.copy(alpha = 0.2f) else color.copy(alpha = 0.08f),
                    MaterialTheme.shapes.medium
                )
                .then(
                    if (isSelected) Modifier.border(2.dp, color, MaterialTheme.shapes.medium) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().basicMarquee()
        )
    }
}

// Tag presets per status
val heldTags = listOf("own", "gifted", "borrowed", "rented", "homemade", "found", "temporary")
val awayTags = listOf("lent", "rented_out", "under_repair", "inspection", "modified", "maintenance", "returned", "shipping", "exhibited")
val removedTags = listOf("sold", "transferred", "given", "donated", "refunded", "discarded", "scrapped", "lost", "stolen", "expired")

fun getTagsForStatus(status: String): List<String> = when (status) {
    "HELD" -> heldTags
    "AWAY" -> awayTags
    "REMOVED" -> removedTags
    else -> emptyList()
}

@StringRes
fun getLocalizedTagName(tag: String): Int = when (tag) {
    "自有", "own" -> R.string.tag_own
    "赠予", "gifted" -> R.string.tag_gifted
    "借入", "borrowed" -> R.string.tag_borrowed
    "租用", "rented" -> R.string.tag_rented
    "自制", "homemade" -> R.string.tag_homemade
    "拣拾", "found" -> R.string.tag_found
    "暂存", "temporary" -> R.string.tag_temporary
    "借出", "lent" -> R.string.tag_lent
    "租出", "rented_out" -> R.string.tag_rented_out
    "维修", "under_repair" -> R.string.tag_under_repair
    "送检", "inspection" -> R.string.tag_inspection
    "改装", "modified" -> R.string.tag_modified
    "保养", "maintenance" -> R.string.tag_maintenance
    "退换", "returned" -> R.string.tag_returned
    "运输", "shipping" -> R.string.tag_shipping
    "展出", "exhibited" -> R.string.tag_exhibited
    "出售", "sold" -> R.string.tag_sold
    "转让", "transferred" -> R.string.tag_transferred
    "赠送", "given" -> R.string.tag_given
    "捐赠", "donated" -> R.string.tag_donated
    "退还", "refunded" -> R.string.tag_refunded
    "丢弃", "discarded" -> R.string.tag_discarded
    "报废", "scrapped" -> R.string.tag_scrapped
    "遗失", "lost" -> R.string.tag_lost
    "失窃", "stolen" -> R.string.tag_stolen
    "过期", "expired" -> R.string.tag_expired
    else -> R.string.tag_other
}

fun parseTags(json: String): List<String> {
    if (json.isBlank()) return emptyList()
    return try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) { emptyList() }
}

@Composable
fun TagPicker(
    selectedTags: List<String>,
    status: String,
    onTagsChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = remember(status) { getTagsForStatus(status) }
    var showPopup by remember { mutableStateOf(false) }
    var customTagInput by remember { mutableStateOf("") }

    val visibleCount = 9
    val visiblePresets = presets.take(visibleCount.coerceAtMost(presets.size))
    val hasMore = presets.size > visibleCount

    Column(modifier = modifier) {
        // Visible row(s)
        if (visiblePresets.isNotEmpty()) {
            // Simple flow: show in one or two rows
            val firstRow = visiblePresets.take(5)
            val secondRow = visiblePresets.drop(5).take(4)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                firstRow.forEach { tag ->
                    TagChip(
                        tag = tag,
                        isSelected = tag in selectedTags,
                        onClick = {
                            onTagsChanged(
                                if (tag in selectedTags) selectedTags - tag
                                else selectedTags + tag
                            )
                        }
                    )
                }
                if (hasMore) {
                    FilterChip(
                        selected = false,
                        onClick = { showPopup = true },
                        label = { Text(stringResource(R.string.common_more)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            if (secondRow.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    secondRow.forEach { tag ->
                        TagChip(
                            tag = tag,
                            isSelected = tag in selectedTags,
                            onClick = {
                                onTagsChanged(
                                    if (tag in selectedTags) selectedTags - tag
                                    else selectedTags + tag
                                )
                            }
                        )
                    }
                }
            }

            // Show selected custom tags
            val customSelected = selectedTags.filter { it !in presets }
            if (customSelected.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    customSelected.forEach { tag ->
                        TagChip(
                            tag = tag,
                            isSelected = true,
                            onClick = { onTagsChanged(selectedTags - tag) }
                        )
                    }
                }
            }
        }

        // Manual input for custom tags
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = customTagInput,
                onValueChange = { customTagInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.common_custom_tag)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
            FilledTonalButton(
                onClick = {
                    val trimmed = customTagInput.trim()
                    if (trimmed.isNotEmpty() && trimmed !in selectedTags) {
                        onTagsChanged(selectedTags + trimmed)
                    }
                    customTagInput = ""
                },
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }

    // More popup
    if (showPopup) {
        AppDialog(
            onDismissRequest = { showPopup = false },
            title = {
                Text(stringResource(R.string.common_select_tag), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.chunked(5).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { tag ->
                                    TagChip(
                                        tag = tag,
                                        isSelected = tag in selectedTags,
                                        onClick = {
                                            onTagsChanged(
                                                if (tag in selectedTags) selectedTags - tag
                                                else selectedTags + tag
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPopup = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
fun TagChip(
    tag: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = when {
        tag in heldTags -> StatusHeld
        tag in awayTags -> StatusAway
        tag in removedTags -> StatusRemoved
        else -> MaterialTheme.colorScheme.primary
    }
    val tagText = if (tag in heldTags || tag in awayTags || tag in removedTags) stringResource(getLocalizedTagName(tag)) else tag
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(tagText, style = MaterialTheme.typography.labelSmall) },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.15f),
            selectedLabelColor = color
        )
    )
}

fun String.toComposeColor(fallback: Color = AccentOrange): Color = try {
    Color(android.graphics.Color.parseColor(this))
} catch (_: Exception) { fallback }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomFormSheet(
    title: String,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: () -> Unit,
    saveEnabled: Boolean = true,
    saveText: String = stringResource(R.string.save),
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AppBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        content()
        if (onDelete != null) {
            OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        }
        AppSaveButton(onClick = onSave, enabled = saveEnabled, text = saveText)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    selectedDate: Long?,
    onDateSelected: (Long) -> Unit,
    placeholder: String = stringResource(R.string.field_select_date)
) {
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }

    Box {
        OutlinedTextField(
            value = if (selectedDate != null) DateUtils.formatDisplayYearDate(context, selectedDate) else "",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
            placeholder = { Text(placeholder) },
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                }
            },
            shape = MaterialTheme.shapes.medium,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = Color.Transparent
            )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showDatePicker = true }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            tonalElevation = 0.dp,
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { onDateSelected(it) }; showDatePicker = false }) { Text(stringResource(R.string.confirm), fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold) } }
        ) { DatePicker(state = datePickerState, colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.background)) }
    }
}

@Composable
fun InlineFormContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall)
                )
            }
            content()
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CapsuleSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
    checkedTrackColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val thumbOffset by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(200)
    )
    val isDark = LocalIsDarkTheme.current

    Box(
        modifier = modifier
            .size(width = 46.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (checked) checkedTrackColor
                else if (enabled) if (isDark) Color(0xFF3A3A3C) else Color(0xFFD9D9D9)
                else Color(0xFFE8E8E8)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = { onCheckedChange?.invoke(!checked) }
            )
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = (20.dp * thumbOffset))
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

fun getCategoryName(key: String, context: android.content.Context): String = when (key) {
    "DIGITAL" -> context.getString(R.string.asset_category_digital)
    "APPLIANCE" -> context.getString(R.string.asset_category_appliance)
    "FURNITURE" -> context.getString(R.string.asset_category_furniture)
    "CLOTHING" -> context.getString(R.string.asset_category_clothing)
    "SPORTS" -> context.getString(R.string.asset_category_sports)
    "BOOKS" -> context.getString(R.string.asset_category_books)
    "COSMETICS" -> context.getString(R.string.asset_category_cosmetics)
    "FOOD" -> context.getString(R.string.asset_category_food)
    "TOOLS" -> context.getString(R.string.asset_category_tools)
    "BABY" -> context.getString(R.string.asset_category_baby)
    "PET" -> context.getString(R.string.asset_category_pet)
    "TRANSPORT" -> context.getString(R.string.asset_category_transport)
    "MEDICAL" -> context.getString(R.string.asset_category_medical)
    "STATIONERY" -> context.getString(R.string.asset_category_stationery)
    "MUSICAL" -> context.getString(R.string.asset_category_musical)
    "PHOTOGRAPHY" -> context.getString(R.string.asset_category_photography)
    "COLLECTION" -> context.getString(R.string.asset_category_collection)
    "JEWELRY" -> context.getString(R.string.asset_category_jewelry)
    "PLANTS" -> context.getString(R.string.asset_category_plants)
    else -> context.getString(R.string.asset_category_other)
}

fun getLocalizedWalletDisplayName(wallet: com.palmnote.data.db.entity.Wallet, context: android.content.Context): String {
    val typeNames = mapOf(
        "CASH" to context.getString(R.string.wallet_type_cash),
        "E_WALLET" to context.getString(R.string.wallet_type_e_wallet),
        "BANK_CARD" to context.getString(R.string.wallet_type_bank_card),
        "CREDIT_CARD" to context.getString(R.string.wallet_type_credit_card),
        "DEBIT_CARD" to context.getString(R.string.wallet_type_debit_card),
        "INVESTMENT" to context.getString(R.string.wallet_type_investment),
        "TOP_UP" to context.getString(R.string.wallet_type_top_up),
        "OTHER" to context.getString(R.string.wallet_type_other)
    )
    val localizedName = typeNames[wallet.type] ?: wallet.name
    // If the wallet name matches a known Chinese type name, use the localized version
    val allChineseNames = listOf(
        "现金", "微信", "支付宝", "储蓄卡", "信用卡", "投资账户", "充值账户", "其他", "电子钱包", "银行卡"
    )
    val allLocalizedNames = typeNames.values.toList()
    val isDefaultName = allChineseNames.any { wallet.name == it } || allLocalizedNames.any { wallet.name == it }
    return if (isDefaultName) {
        when (wallet.name) {
            "微信", "WeChat" -> context.getString(R.string.wallet_type_wechat)
            "支付宝", "Alipay" -> context.getString(R.string.wallet_type_alipay)
            "现金", "Cash" -> context.getString(R.string.wallet_type_cash)
            "储蓄卡", "Debit Card" -> context.getString(R.string.wallet_type_debit_card)
            "信用卡", "Credit Card" -> context.getString(R.string.wallet_type_credit_card)
            "投资账户", "Investment" -> context.getString(R.string.wallet_type_investment)
            "充值账户", "Top-up" -> context.getString(R.string.wallet_type_top_up)
            "电子钱包", "E-Wallet" -> context.getString(R.string.wallet_type_e_wallet)
            "银行卡", "Bank Card" -> context.getString(R.string.wallet_type_bank_card)
            "其他", "Other" -> context.getString(R.string.wallet_type_other)
            else -> localizedName
        }
    } else {
        wallet.displayName
    }
}

@Composable
fun ModuleSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit = {},
    onClear: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    )
}

val presetColorHexes = listOf(
    "#E57373", "#EF5350", "#FF7043", "#FF8C42", "#FFAB91",
    "#FFCA28", "#FFD54F", "#FBBC04", "#C0CA33", "#66BB6A",
    "#4DB6AC", "#4DD0E1", "#29B6F6", "#64B5F6", "#42A5F5",
    "#7986CB", "#BA68C8", "#CE93D8", "#F06292", "#F48FB1",
    "#FF80AB", "#34A853", "#00ACC1", "#4285F4"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(
    selectedColor: String,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var customHex by remember(selectedColor) {
        val clean = selectedColor.removePrefix("#")
        mutableStateOf(if (clean.length == 6) clean else "4285F4")
    }
    Column(modifier = modifier) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presetColorHexes.forEach { hex ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(hex.toComposeColor())
                        .clickable { onColorSelected(hex) },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedColor == hex) {
                        Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = customHex,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }.take(6)
                    customHex = filtered
                    if (filtered.length == 6) onColorSelected("#$filtered")
                },
                modifier = Modifier.width(140.dp),
                label = { Text("#") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(("#$customHex").toComposeColor())
            )
        }
    }
}

