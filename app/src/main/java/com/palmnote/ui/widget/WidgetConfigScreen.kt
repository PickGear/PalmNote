package com.palmnote.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.getDisplayName
import com.palmnote.ui.components.CompactTopAppBar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** 透明度档位间隔数：两端之外再插 2 个，共 4 档（40/60/80/100%），与添加页同一套档位。 */
private const val OPACITY_STEPS = 2

/** 配置项列表的测试标签：列表变长后页脚文案要滚动才可见，测试需要先滚过去。 */
internal const val WIDGET_CONFIG_LIST_TAG = "widget_config_list"

/**
 * 组件配置页：**透明度对所有组件都显示**；账本那一段只有账单组件有（[showBooks]）。
 * 无状态入口（数据靠 Flow 注入），便于离屏渲染核对。
 */
@Composable
internal fun WidgetConfigScreen(
    /** 是否显示「显示哪个账本」这一段：只有账单组件需要。 */
    showBooks: Boolean,
    booksFlow: Flow<List<AccountBook>>,
    selectedBookFlow: Flow<Long?>,
    /** 该实例的透明度覆盖；null = 跟随全局默认。 */
    opacityOverrideFlow: Flow<Float?>,
    /** 全局默认透明度（「跟随默认」那行要显示它）。 */
    defaultOpacityFlow: Flow<Float>,
    onConfirm: (WidgetConfigResult) -> Unit,
    onCancel: () -> Unit
) {
    val books by booksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val defaultOpacity by defaultOpacityFlow.collectAsStateWithLifecycle(
        initialValue = PreferencesManager.DEFAULT_WIDGET_OPACITY
    )
    val context = LocalContext.current

    // 选择先落在本地，点「完成」才写偏好——中途反悔直接返回不影响已摆的组件。
    // 播种必须用 first() 等流真正发出值：collectAsStateWithLifecycle 的初值是 null，
    // 若按「首次组合就播种」，真实值到达时 already-seeded 会把已存的账本/透明度显示成默认值，
    // 用户直接点「完成」就把设置清了。
    var selected by remember { mutableStateOf<Long?>(null) }
    var override by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(Unit) {
        selected = selectedBookFlow.first()
        override = opacityOverrideFlow.first()
    }

    Scaffold(
        topBar = { ConfigTopBar(showBooks = showBooks, onCancel = onCancel) },
        bottomBar = {
            ConfigConfirmBar(
                onConfirm = { onConfirm(WidgetConfigResult(bookId = selected, opacityOverride = override)) }
            )
        }
    ) { innerPadding ->
        ConfigList(
            modifier = Modifier.padding(innerPadding),
            showBooks = showBooks,
            books = books,
            selected = selected,
            onSelect = { selected = it },
            override = override,
            defaultOpacity = defaultOpacity,
            onOverrideChange = { override = it },
            context = context
        )
    }
}

/** 配置项列表：说明 + （账单才有的）账本 + 透明度 + 预算口径提示。 */
@Composable
private fun ConfigList(
    modifier: Modifier,
    showBooks: Boolean,
    books: List<AccountBook>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    override: Float?,
    defaultOpacity: Float,
    onOverrideChange: (Float?) -> Unit,
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
                stringResource(
                    if (showBooks) R.string.widget_config_desc else R.string.widget_config_desc_opacity
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
        if (showBooks) {
            item { BookListCard(books = books, selected = selected, onSelect = onSelect, context = context) }
        }
        item {
            OpacityCard(override = override, defaultOpacity = defaultOpacity, onChange = onOverrideChange)
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
private fun BookListCard(
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

/**
 * 透明度：默认**跟随全局**，点「改为自定义」才覆盖。
 * 覆盖态显示实际档位，跟随态显示全局值并写明「跟随默认」。
 */
@Composable
private fun OpacityCard(
    override: Float?,
    defaultOpacity: Float,
    onChange: (Float?) -> Unit
) {
    val effective = override ?: defaultOpacity
    var value by remember(effective) { mutableFloatStateOf(effective) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            OpacityHeader(
                isOverride = override != null,
                onToggle = { onChange(if (override == null) defaultOpacity else null) }
            )
            OpacitySlider(value = value, onValueChange = { value = it }, onCommit = { onChange(it) })
            Text(
                if (override == null) {
                    stringResource(
                        R.string.widget_config_opacity_following,
                        (defaultOpacity * 100).toInt()
                    )
                } else {
                    stringResource(R.string.widget_config_opacity_only_this)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 标题行 + 「改为自定义 / 恢复跟随默认」切换。 */
@Composable
private fun OpacityHeader(isOverride: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.widget_config_opacity_label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        TextButton(contentPadding = PaddingValues(horizontal = 8.dp), onClick = onToggle) {
            Text(
                stringResource(
                    if (isOverride) {
                        R.string.widget_config_opacity_follow
                    } else {
                        R.string.widget_config_opacity_customize
                    }
                )
            )
        }
    }
}

/** 档位滑块 + 百分比。松手才提交（跟随态下拖动即就地改成自定义）。 */
@Composable
private fun OpacitySlider(value: Float, onValueChange: (Float) -> Unit, onCommit: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = { onCommit(value) },
            valueRange = PreferencesManager.MIN_WIDGET_OPACITY..1f,
            steps = OPACITY_STEPS,
            modifier = Modifier.weight(1f)
        )
        Text(
            "${(value * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End
        )
    }
}
