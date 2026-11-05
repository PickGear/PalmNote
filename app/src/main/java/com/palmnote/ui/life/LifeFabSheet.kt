package com.palmnote.ui.life

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.palmnote.app.R
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.data.db.entity.getDisplayName
import com.palmnote.domain.util.BuiltinTemplates
import com.palmnote.ui.theme.LifePlan
import com.palmnote.ui.theme.LifeRecord
import com.palmnote.ui.theme.LifeTime
import com.palmnote.ui.theme.ModuleLife
import com.palmnote.ui.theme.Spacing
import com.palmnote.ui.theme.TypeScale
import com.palmnote.ui.theme.lifePlanTint
import com.palmnote.ui.theme.lifeRecordTint
import com.palmnote.ui.theme.lifeTimeTint
import java.time.LocalDate

private const val CAT_PLAN = BuiltinTemplates.PLAN_CATEGORY
private const val CAT_TIME = BuiltinTemplates.TIME_CATEGORY
private const val CAT_RECORD = BuiltinTemplates.RECORD_CATEGORY

/** 「最近使用」行最多直带的模板数：再多就该用搜索而不是扫胶囊。 */
private const val FAB_RECENT_LIMIT = 3

/**
 * 生活页 FAB 的「新建记录」面板（2026-11-06 重设计）：标题升格 + 分类改分段控件
 * （与预算页周期条同语言，选中段 = 类别 tint 底）+ 模板卡图标位圆形化。
 * 取数与过滤逻辑不变：最近使用 → 搜索 → 分类 → 三列网格。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FabSheet(
    templates: List<LifeTemplate>,
    lastLogByTemplate: Map<Long, Long>,
    onDismiss: () -> Unit,
    onTemplateClick: (LifeTemplate) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .navigationBarsPadding()
                .padding(bottom = Spacing.sm)
        ) {
            Text(
                stringResource(R.string.life_select_type_to_create),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(14.dp))
            // §5.0：系统型模板（专注等）不进创建选择器，只在此处过滤，不动 ViewModel 源头
            val creatable = remember(templates) { templates.filterNot { it.isSpecial } }
            var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
            var search by remember { mutableStateOf("") }
            val categories = remember(creatable) { creatable.map { it.category }.distinct() }
            // 有效分类 = 选中值仍存在；数据变化后消失则收敛回「全部」
            val effectiveCategory = selectedCategory?.takeIf { it in categories }
            val shown = remember(creatable, effectiveCategory, search) {
                filterCreatable(creatable, effectiveCategory, search)
            }
            // 最近使用（≤3）：长按 FAB 直达的显性版，把高频模板摆到第一层；全新用户整行隐藏
            val today = remember { LocalDate.now() }
            val recent = remember(creatable, lastLogByTemplate) {
                recentTemplates(creatable, lastLogByTemplate)
            }
            FabRecentSection(
                recent = recent,
                lastLogByTemplate = lastLogByTemplate,
                today = today,
                onTemplateClick = onTemplateClick
            )
            FabSheetHeader(
                search = search,
                onSearchChange = { search = it },
                categories = categories,
                selectedCategory = effectiveCategory,
                onSelectCategory = { selectedCategory = it }
            )
            Spacer(Modifier.height(12.dp))
            if (shown.isEmpty()) {
                LifeEmptyState(
                    icon = Icons.Outlined.Search,
                    title = stringResource(R.string.life_search_no_result),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                FabSheetGrid(shown, lastLogByTemplate, today, onTemplateClick)
            }
        }
    }
}

/** 最近使用的模板（按上次记录时间倒序取前 [FAB_RECENT_LIMIT] 个）。 */
private fun recentTemplates(creatable: List<LifeTemplate>, lastLogByTemplate: Map<Long, Long>): List<LifeTemplate> =
    creatable.mapNotNull { tpl -> lastLogByTemplate[tpl.id]?.let { tpl to it } }
        .sortedByDescending { it.second }
        .take(FAB_RECENT_LIMIT)
        .map { it.first }

/** 「上次记录」短文案（最近胶囊与网格卡共用）：今天 / N 天前 / 从未。 */
@Composable
private fun lastLoggedShort(lastMs: Long?, today: LocalDate): String =
    when (val d = relativeDaysSince(lastMs, today)) {
        null -> stringResource(R.string.life_tpl_last_short_never)
        0L -> stringResource(R.string.life_tpl_last_short_today)
        else -> stringResource(R.string.life_tpl_last_short_days, d)
    }

/** 最近使用的模板胶囊行（图标 tint 圆点 + 名称 + 上次短文案），单击直达表单；空列表整行隐藏。 */
@Composable
private fun FabRecentSection(
    recent: List<LifeTemplate>,
    lastLogByTemplate: Map<Long, Long>,
    today: LocalDate,
    onTemplateClick: (LifeTemplate) -> Unit
) {
    if (recent.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        recent.forEach { tpl ->
            val accent = identityColor(tpl.color)
            Surface(
                onClick = { onTemplateClick(tpl) },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(start = 5.dp, end = 9.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(accent.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(iconFor(tpl.icon), null, tint = accent, modifier = Modifier.size(12.dp))
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(
                        tpl.getDisplayName(LocalContext.current),
                        fontSize = TypeScale.bodyS,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        lastLoggedShort(lastLogByTemplate[tpl.id], today),
                        fontSize = TypeScale.labelS,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

/** 创建面板的筛选：先按分类，再按模板名子串。 */
private fun filterCreatable(
    creatable: List<LifeTemplate>,
    category: String?,
    search: String
): List<LifeTemplate> {
    val byCat = if (category == null) creatable else creatable.filter { it.category == category }
    val query = search.trim()
    return if (query.isEmpty()) byCat else byCat.filter { it.name.contains(query, ignoreCase = true) }
}

/**
 * 创建面板的筛选头（搜索框常驻 + 分类分段控件）。
 *
 * 分类原是「四张描边小卡」，与 app 的分段控件语言（预算页周期条）不一致且偏重；
 * 2026-11-06 重设计改成分段控件：整条胶囊容器、选中段 = 类别 tint 底 + 类别色文字，
 * 波纹关闭（同预算页：选中态由底色表达，波纹会残留在相邻段上）。
 */
@Composable
private fun FabSheetHeader(
    search: String,
    onSearchChange: (String) -> Unit,
    categories: List<String>,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit
) {
    FabSearchField(search = search, onSearchChange = onSearchChange)
    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        SegmentedItem(
            label = stringResource(R.string.life_category_filter_all),
            icon = Icons.Filled.Apps,
            accent = ModuleLife,
            tint = ModuleLife.copy(alpha = 0.12f),
            selected = selectedCategory == null,
            onClick = { onSelectCategory(null) },
            modifier = Modifier.weight(1f)
        )
        categories.forEach { cat ->
            val (accent, tint) = fabCategoryVisual(cat)
            SegmentedItem(
                label = categoryChipLabel(cat),
                icon = fabCategoryIcon(cat),
                accent = accent,
                tint = tint,
                selected = selectedCategory == cat,
                onClick = { onSelectCategory(cat) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 分段控件的单元：图标 + 名称同行，选中 = 类别 tint 底 + 类别色；波纹关闭（同预算页周期条）。 */
@Composable
private fun SegmentedItem(
    label: String,
    icon: ImageVector,
    accent: Color,
    tint: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) tint else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            null,
            tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            fontSize = TypeScale.labelS,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 创建面板的模板搜索框：常驻第一层，不自动弹键盘。 */
@Composable
private fun FabSearchField(search: String, onSearchChange: (String) -> Unit) {
    BasicTextField(
        value = search,
        onValueChange = onSearchChange,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Search,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box {
                    if (search.isEmpty()) {
                        Text(
                            stringResource(R.string.life_tpl_search_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    }
                    inner()
                }
            }
        }
    )
}

/** 分类 → (身份色, tint 底)：已知三分类走主题真源色与暗色适配 tint，自定义分类退回模块色。 */
@Composable
private fun fabCategoryVisual(category: String): Pair<Color, Color> = when (category) {
    CAT_PLAN -> LifePlan to lifePlanTint()
    CAT_TIME -> LifeTime to lifeTimeTint()
    CAT_RECORD -> LifeRecord to lifeRecordTint()
    else -> ModuleLife to ModuleLife.copy(alpha = 0.12f)
}

private fun fabCategoryIcon(category: String): ImageVector = when (category) {
    CAT_PLAN -> Icons.Filled.Star
    CAT_TIME -> Icons.Filled.CalendarMonth
    CAT_RECORD -> Icons.Filled.AutoStories
    else -> Icons.AutoMirrored.Filled.Label
}

/** 创建面板的模板网格（三列，一屏 9 个；最高 420dp 后内部滚动）。 */
@Composable
private fun FabSheetGrid(
    shown: List<LifeTemplate>,
    lastLogByTemplate: Map<Long, Long>,
    today: LocalDate,
    onTemplateClick: (LifeTemplate) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(shown, key = { it.id }) { tpl ->
            FabSheetTemplateCard(tpl, lastLogByTemplate[tpl.id], today) { onTemplateClick(tpl) }
        }
    }
}

@Composable
private fun FabSheetTemplateCard(tpl: LifeTemplate, lastMs: Long?, today: LocalDate, onClick: () -> Unit) {
    val accent = identityColor(tpl.color)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 图标位圆形化：与顶部摘要胶囊、最近使用行同一形状语言
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accent.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(iconFor(tpl.icon), null, tint = accent, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.height(7.dp))
            Text(
                tpl.getDisplayName(LocalContext.current),
                fontSize = TypeScale.bodyS,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 「上次记录」短文案：回答此刻"这个该不该现在记一笔"。
            Text(
                lastLoggedShort(lastMs, today),
                fontSize = TypeScale.labelS,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
