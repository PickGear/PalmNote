package com.palmnote.ui.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.app.BuildConfig
import com.palmnote.app.R
import com.palmnote.ui.components.CompactTopAppBar
import com.palmnote.ui.components.ModuleCard

/** 一个更新分区：如「新增 / 变更 / 修复 / 安全」及其条目列表。 */
private data class ChangelogSection(val title: String, val items: List<String>)

/** 一个版本的完整记录：版本号 + 日期 + 全部分区。 */
private data class VersionEntry(val version: String, val date: String, val sections: List<ChangelogSection>) {
    val itemCount: Int get() = sections.sumOf { it.items.size }
}

/** 版本头行：如「1.4.0 (2026-09-17)」。 */
private val VERSION_HEADER = Regex("""^(\d+(?:\.\d+){1,2}) \((\d{4}-\d{2}-\d{2})\)\s*$""")

/** 分区标签着色：语义色取中段亮度，深浅主题下都可读；未知分区回退中性灰。 */
private val SECTION_TINT_ADDED = Color(0xFF639922)
private val SECTION_TINT_CHANGED = Color(0xFFBA7517)
private val SECTION_TINT_FIXED = Color(0xFF378ADD)
private val SECTION_TINT_SECURITY = Color(0xFFE24B4A)
private val SECTION_TINT_OTHER = Color(0xFF888780)

private fun sectionTint(title: String): Color = when (title) {
    "新增" -> SECTION_TINT_ADDED
    "变更" -> SECTION_TINT_CHANGED
    "修复" -> SECTION_TINT_FIXED
    "安全" -> SECTION_TINT_SECURITY
    else -> SECTION_TINT_OTHER
}

/**
 * 解析随包 changelog.txt 为结构化版本列表。
 *
 * 格式约定（由 gen_changelog_asset.py 生成）：版本头「x.y.z (yyyy-MM-dd)」、
 * 下划分隔线、分区标题行（新增/变更/…）、「- 」条目行；其余行（双语头部等）忽略。
 * 返回空列表表示解析失败，调用方回退到原文展示。
 */
private fun parseChangelog(text: String): List<VersionEntry> {
    val entries = mutableListOf<VersionEntry>()
    var version: String? = null
    var date = ""
    val sections = mutableListOf<ChangelogSection>()
    var currentTitle = ""
    val currentItems = mutableListOf<String>()

    fun flushSection() {
        if (currentTitle.isNotEmpty() && currentItems.isNotEmpty()) {
            sections.add(ChangelogSection(currentTitle, currentItems.toList()))
        }
        currentItems.clear()
    }

    fun flushVersion() {
        flushSection()
        val v = version ?: return
        entries.add(VersionEntry(v, date, sections.toList()))
        sections.clear()
    }

    text.lineSequence().forEach { raw ->
        val line = raw.trim()
        val header = VERSION_HEADER.matchEntire(line)
        when {
            header != null -> {
                flushVersion()
                version = header.groupValues[1]
                date = header.groupValues[2]
                currentTitle = ""
            }
            line.isEmpty() || line.all { it == '-' } -> Unit
            line.startsWith("- ") -> {
                if (currentTitle.isNotEmpty()) currentItems.add(line.removePrefix("- ").trim())
            }
            version != null -> {
                flushSection()
                currentTitle = line
            }
        }
    }
    flushVersion()
    return entries
}

/** 从 assets/changelog.txt 读取版本历史原文；读取失败返回 null。.txt 后缀是必需的：aapt 不打包无扩展名 assets。 */
internal fun loadChangelogText(context: Context): String? =
    runCatching { context.assets.open("changelog.txt").bufferedReader().use { it.readText() } }.getOrNull()

/**
 * 应用内「版本历史」页：解析随包 changelog.txt，以**时间轴**呈现。
 *
 * - 左侧时间轴：当前运行版本的节点高亮，其余为灰点；竖线串起全部版本
 * - 最新版本默认展开；其余折叠为一行（版本 + 日期 + 条数），点击展开/收起
 * - 卡片用全应用统一的 [ModuleCard]，与设置区其他页面同一设计语言
 * - 解析失败时回退为原文整段展示，保证任何情况下都有内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionHistoryScreen(onNavigateBack: () -> Unit = {}) {
    val context = LocalContext.current
    val rawText = remember { loadChangelogText(context) }
    val entries = remember(rawText) { rawText?.let { parseChangelog(it) } ?: emptyList() }

    if (rawText == null || entries.isEmpty()) {
        DocumentScreen(
            title = stringResource(R.string.about_version_history),
            lines = emptyList(),
            isHeading = { false },
            onNavigateBack = onNavigateBack,
            verbatimText = rawText ?: stringResource(R.string.version_history_load_failed)
        )
        return
    }

    BackHandler(onBack = onNavigateBack)
    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.about_version_history),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_navigate_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            itemsIndexed(entries, key = { _, e -> e.version }) { index, entry ->
                TimelineVersionBlock(
                    entry = entry,
                    isCurrent = entry.version == BuildConfig.VERSION_NAME,
                    defaultExpanded = index == 0,
                    isLast = index == entries.lastIndex
                )
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

/**
 * 时间轴上的单个版本块：左侧轨道（圆点 + 竖线）串起版本顺序，右侧为内容。
 * 展开态 = 完整卡片（版本徽章 + 日期 + 当前版本标签 + 分区条目）；折叠态 = 一行摘要。
 */
@Composable
private fun TimelineVersionBlock(entry: VersionEntry, isCurrent: Boolean, defaultExpanded: Boolean, isLast: Boolean) {
    var expanded by rememberSaveable(entry.version) { mutableStateOf(defaultExpanded) }
    val dotColor = if (isCurrent || expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val railColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // ── 时间轴轨道：节点圆点 + 向下延伸的竖线（最后一个版本不再延伸）──
        Box(modifier = Modifier.width(28.dp).fillMaxHeight()) {
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(railColor)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }

        Column(modifier = Modifier.weight(1f).padding(bottom = 14.dp, start = 2.dp)) {
            // 头行：点击任意处展开/收起
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.version,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isCurrent) {
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                        Text(
                            text = stringResource(R.string.version_history_current),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (!expanded) {
                    Text(
                        text = stringResource(R.string.version_history_item_count, entry.itemCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (expanded) {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    entry.sections.forEach { section ->
                        Spacer(Modifier.height(10.dp))
                        SectionChip(section.title)
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            section.items.forEach { item ->
                                Row {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 7.dp)
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(sectionTint(section.title).copy(alpha = 0.7f))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = item,
                                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

/** 分区标签：语义色浅底胶囊（新增=绿 / 变更=琥珀 / 修复=蓝 / 安全=红）。 */
@Composable
private fun SectionChip(title: String) {
    val tint = sectionTint(title)
    Surface(shape = RoundedCornerShape(8.dp), color = tint.copy(alpha = 0.15f)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
