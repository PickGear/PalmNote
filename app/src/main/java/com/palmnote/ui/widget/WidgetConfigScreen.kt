package com.palmnote.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import com.palmnote.ui.theme.ThemePackages
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.getDisplayName
import com.palmnote.ui.components.CompactTopAppBar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first


/** 配置项列表的测试标签：列表变长后页脚文案要滚动才可见，测试需要先滚过去。 */
internal const val WIDGET_CONFIG_LIST_TAG = "widget_config_list"

/**
 * 组件配置页：账本那一段只有账单组件有（[showBooks]），配色那一段只有渲染吃强调色的组件有
 * （[showAccent]）。**不透明度与背景不在这里** —— 它们在添加/编辑面板（`WidgetPinScreen`）里
 * 按 `appWidgetId` 直接改，见 `WidgetPinViewModel.applyOpacity`。
 * 无状态入口（数据靠 Flow 注入），便于离屏渲染核对。
 */
@Composable
internal fun WidgetConfigScreen(
    /** 是否显示「显示哪个账本」这一段：只有账单组件需要。 */
    showBooks: Boolean,
    booksFlow: Flow<List<AccountBook>>,
    selectedBookFlow: Flow<Long?>,
    /** 该实例的强调色覆盖；null = 跟随应用主题包。 */
    accentThemeOverrideFlow: Flow<String?> = flowOf(null),
    /** 是否显示「配色」卡：只有渲染吃强调色的组件（账单/概览/接下来）才显示。 */
    showAccent: Boolean,
    onConfirm: (WidgetConfigResult) -> Unit,
    onCancel: () -> Unit
) {
    val books by booksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current

    // 选择先落在本地，点「完成」才写偏好——中途反悔直接返回不影响已摆的组件。
    // 播种必须用 first() 等流真正发出值：collectAsStateWithLifecycle 的初值是 null，
    // 若按「首次组合就播种」，真实值到达时 already-seeded 会把已存的账本/配色显示成默认值，
    // 用户直接点「完成」就把设置清了。
    var selected by remember { mutableStateOf<Long?>(null) }
    var accentTheme by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        selected = selectedBookFlow.first()
        accentTheme = accentThemeOverrideFlow.first()
    }

    Scaffold(
        topBar = { ConfigTopBar(showBooks = showBooks, onCancel = onCancel) },
        bottomBar = {
            ConfigConfirmBar(
                onConfirm = {
                    onConfirm(
                        WidgetConfigResult(
                            bookId = selected,
                            accentThemeId = accentTheme
                        )
                    )
                }
            )
        }
    ) { innerPadding ->
        ConfigList(
            modifier = Modifier.padding(innerPadding),
            showBooks = showBooks,
            books = books,
            selected = selected,
            onSelect = { selected = it },
            accentTheme = accentTheme,
            onAccentThemeChange = { accentTheme = it },
            showAccent = showAccent,
            context = context
        )
    }
}

/** 配置项列表：说明 + 账本（[showBooks]）+ 配色（[showAccent]）+ 预算口径提示（[showBooks]）。 */
@Suppress("LongParameterList")
@Composable
private fun ConfigList(
    modifier: Modifier,
    showBooks: Boolean,
    books: List<AccountBook>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    accentTheme: String?,
    onAccentThemeChange: (String?) -> Unit,
    showAccent: Boolean,
    context: android.content.Context
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(WIDGET_CONFIG_LIST_TAG),
        contentPadding = PaddingValues(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                stringResource(R.string.widget_config_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
        if (showBooks) {
            item { BookListCard(books = books, selected = selected, onSelect = onSelect, context = context) }
        }
        if (showAccent) {
            item {
                AccentThemeCard(selected = accentTheme, onSelect = onAccentThemeChange)
            }
        }
        if (showBooks) {
            item {
                Text(
                    stringResource(R.string.widget_config_budget_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/**
 * 配色卡：「跟随应用」在最前（null），随后是各主题包的色点。
 * 覆盖只改这一个组件的强调色，应用内主题不变。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccentThemeCard(selected: String?, onSelect: (String?) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                stringResource(R.string.widget_config_accent_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.widget_config_accent_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentChip(
                    selected = selected == null,
                    color = MaterialTheme.colorScheme.primary,
                    label = stringResource(R.string.widget_config_accent_follow),
                    onClick = { onSelect(null) }
                )
                ThemePackages.packages.forEach { pkg ->
                    val isDark = (LocalConfiguration.current.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
                    AccentChip(
                        selected = selected == pkg.id,
                        color = if (isDark) pkg.darkPrimary else pkg.lightPrimary,
                        label = null,
                        onClick = { onSelect(pkg.id) }
                    )
                }
            }
        }
    }
}

/** 配色选项：色点圆 + 选中描边；「跟随应用」带文字，主题包只有色点。 */
@Composable
private fun AccentChip(selected: Boolean, color: Color, label: String?, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            label?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

/** 顶部：标题 + 返回（返回＝放弃，组件不落桌）。 */
@Composable
private fun ConfigTopBar(showBooks: Boolean, onCancel: () -> Unit) {
    CompactTopAppBar(
        title = stringResource(
            if (showBooks) R.string.widget_config_title else R.string.widget_config_title_generic
        ),
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cancel)
                )
            }
        }
    )
}

/** 底部：完成。桌面要靠 RESULT_OK 才落桌，所以确认入口必须显眼且只有一个。 */
@Composable
private fun ConfigConfirmBar(onConfirm: () -> Unit) {
    // Scaffold 的 bottomBar 不会自动让出导航栏，底部操作区按项目惯例自己 pad
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.widget_config_confirm))
        }
    }
}

/** 账本列表：「全账本」在最前（null），随后是可见账本。 */
@Composable
internal fun BookListCard(
    books: List<AccountBook>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    context: android.content.Context
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            BookOptionRow(
                label = stringResource(R.string.account_book_all_name),
                checked = selected == null,
                onClick = { onSelect(null) }
            )
            // 隐藏账本不参与（与账本管理页一致）；ALL 伪账本有「全账本」这一行，不重复列
            books.filter { !it.isAllBooks }.forEach { book ->
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                BookOptionRow(
                    label = book.getDisplayName(context),
                    checked = selected == book.id,
                    onClick = { onSelect(book.id) }
                )
            }
        }
    }
}

/** 一个账本选项：整行可点，右侧打勾。 */
@Composable
private fun BookOptionRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (checked) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
