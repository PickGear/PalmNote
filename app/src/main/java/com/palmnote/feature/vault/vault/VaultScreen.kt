package com.palmnote.feature.vault.vault

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File
import com.palmnote.R
import com.palmnote.feature.vault.VaultEntry
import com.palmnote.feature.vault.VaultLockManager.LockState
import com.palmnote.ui.components.CompactTopAppBar
import com.palmnote.ui.components.ModuleSearchBar
import com.palmnote.ui.theme.vaultTint
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 密码本列表页：搜索/分类筛选 + 条目列表。
 */
@Composable
fun VaultScreen(
    viewModel: VaultViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToEdit: () -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val autoLockMode by viewModel.autoLockMode.collectAsStateWithLifecycle()
    val autoLockTimeoutMinutes by viewModel.autoLockTimeoutMinutes.collectAsStateWithLifecycle()
    val clipboardClearSeconds by viewModel.clipboardClearSeconds.collectAsStateWithLifecycle()
    val cardIdentity by viewModel.cardIdentity.collectAsStateWithLifecycle()
    var showForgotPinConfirm by remember { mutableStateOf(false) }
    VaultLockOnBackground(
        lock = viewModel::lock,
        requireAuth = { state.requireAuth },
        autoLockMode = autoLockMode,
        autoLockTimeoutMinutes = autoLockTimeoutMinutes
    )
    // 冷启动 DataStore 未就绪时锁状态可能误判，先空白等确认，避免闪现错误页面
    if (!state.lockSettled) { Box(modifier = Modifier.fillMaxSize()); return }
    if (state.lockState != LockState.UNLOCKED) {
        VaultLockGate(
            lockState = state.lockState,
            error = state.pinError,
            lockoutRemainingMs = state.lockoutRemainingMs,
            biometricEnabled = state.biometricEnabled,
            onBiometricUnlock = viewModel::unlockBiometric,
            onSetup = viewModel::setupPin,
            onUnlock = viewModel::unlock,
            onSkip = viewModel::setupNoLock,
            onForgotPin = { showForgotPinConfirm = true },
            onPinTyped = viewModel::clearPinError
        )
        if (showForgotPinConfirm) {
            VaultForgotPinDialog(
                onConfirm = {
                    showForgotPinConfirm = false
                    viewModel.resetForVaultLockout()
                },
                onDismiss = { showForgotPinConfirm = false }
            )
        }
        return
    }

    VaultListContent(
        viewModel = viewModel,
        state = state,
        onEntryClick = onNavigateToDetail,
        onAdd = onNavigateToEdit,
        onBack = onNavigateBack,
        onSettings = onNavigateToSettings,
        noLockBannerDismissed = state.noLockBannerDismissed,
        clipboardClearSeconds = clipboardClearSeconds,
        cardConfig = VaultCardConfig(identity = cardIdentity)
    )
}
/**
 * 自动锁门挂载（进程级，与全 App 一致）。选择权交给用户：
 *  - immediate：进入后台即锁定；
 *  - system：默认，跟随系统（仅系统锁屏，离开后台/前台切换时不锁）；
 *  - timeout：进入后台停留超时后锁定。
 * 全 App 用 ProcessLifecycleOwner（而非 NavBackStackEntry），Vault 在锁节点内调用。
 * requireAuth 关闭时不做好安全保护。
 */
@Composable
fun VaultLockOnBackground(
    lock: () -> Unit,
    requireAuth: () -> Boolean,
    autoLockMode: String = com.palmnote.data.datastore.PreferencesManager.AUTO_LOCK_MODE_SYSTEM,
    autoLockTimeoutMinutes: Int = com.palmnote.data.datastore.PreferencesManager.DEFAULT_AUTO_LOCK_TIMEOUT_MINUTES
) {
    val context = LocalContext.current
    val latestLock by rememberUpdatedState(lock)
    val latestRequireAuth by rememberUpdatedState(requireAuth)
    var backgroundedAt by remember { mutableLongStateOf(0L) }
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (latestRequireAuth()) {
                        backgroundedAt = System.currentTimeMillis()
                    }
                }
                Lifecycle.Event.ON_START -> {
                    val ts = backgroundedAt
                    backgroundedAt = 0L
                    if (latestRequireAuth() && com.palmnote.data.lock.AutoLockHelper.shouldLock(context, autoLockMode, ts, autoLockTimeoutMinutes * 60_000L)) {
                        latestLock()
                    }
                }
                else -> {}
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        onDispose { ProcessLifecycleOwner.get().lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun VaultListContent(
    viewModel: VaultViewModel,
    state: VaultUiState,
    onEntryClick: (Long) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    clipboardClearSeconds: Int,
    cardConfig: VaultCardConfig,
    noLockBannerDismissed: Boolean
) {
    var searchExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 搜索展开时返回键收起搜索（与物品/账单等页面一致）
    BackHandler(enabled = searchExpanded) {
        searchExpanded = false
        viewModel.onQueryChange("")
    }

    Scaffold(
        topBar = {
            VaultTopBar(
                searchExpanded = searchExpanded,
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                onSearchToggle = { searchExpanded = !searchExpanded },
                onSearchCollapse = {
                    searchExpanded = false
                    viewModel.onQueryChange("")
                },
                onBack = onBack,
                onSettings = onSettings
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = vaultTint(),
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.vault_add)
                )
            }
        }
    ) { padding ->
        VaultListBody(
            viewModel = viewModel,
            state = state,
            onEntryClick = onEntryClick,
            onSettings = onSettings,
            noLockBannerDismissed = noLockBannerDismissed,
            clipboardClearSeconds = clipboardClearSeconds,
            cardConfig = cardConfig,
            snackbarHostState = snackbarHostState,
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
private fun VaultListBody(
    viewModel: VaultViewModel,
    state: VaultUiState,
    onEntryClick: (Long) -> Unit,
    onSettings: () -> Unit,
    noLockBannerDismissed: Boolean,
    clipboardClearSeconds: Int,
    cardConfig: VaultCardConfig,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (state.isNoLockMode && !noLockBannerDismissed) {
            NoLockBanner(
                onClick = onSettings,
                onDismiss = viewModel::dismissNoLockBanner
            )
        }
        CategoryFilterRow(
            categories = state.categories,
            selected = state.category,
            onSelect = viewModel::onCategorySelect
        )
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.entries.isEmpty()) {
            VaultEmptyState(query = state.query)
        } else {
            VaultEntryList(
                entries = state.entries,
                cardConfig = cardConfig,
                onEntryClick = onEntryClick,
                onCopy = viewModel::copyPassword,
                clipboardClearSeconds = clipboardClearSeconds,
                snackbarHostState = snackbarHostState
            )
        }
    }
}

@Composable
private fun VaultTopBar(
    searchExpanded: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    onSearchCollapse: () -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit
) {
    CompactTopAppBar(
        title = {
            if (searchExpanded) {
                ModuleSearchBar(
                    query = query,
                    onQueryChange = onQueryChange,
                    onClear = { onQueryChange("") },
                    placeholder = stringResource(R.string.vault_search_hint),
                    autoFocus = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(stringResource(R.string.vault_title))
            }
        },
        navigationIcon = {
            if (!searchExpanded) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.settings_navigate_back)
                    )
                }
            }
        },
        actions = {
            if (searchExpanded) {
                TextButton(
                    onClick = onSearchCollapse,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(stringResource(R.string.cancel), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                IconButton(onClick = onSearchToggle) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.vault_search)
                    )
                }
                IconButton(onClick = onSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.vault_settings)
                    )
                }
            }
        }
    )
}

@Composable
private fun VaultEntryList(
    entries: List<VaultEntry>,
    cardConfig: VaultCardConfig,
    onEntryClick: (Long) -> Unit,
    onCopy: (VaultEntry) -> Boolean,
    clipboardClearSeconds: Int,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showCopied: () -> Unit = {
        scope.launch {
            val msg = if (clipboardClearSeconds > 0)
                context.getString(R.string.vault_copied_autoclear, clipboardClearSeconds)
            else context.getString(R.string.vault_copied)
            snackbarHostState.showSnackbar(msg)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(entries, key = { it.id }) { entry ->
            VaultEntryCard(
                entry = entry,
                cardConfig = cardConfig,
                onClick = { onEntryClick(entry.id) },
                onCopy = { if (onCopy(entry)) showCopied() }
            )
        }
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Surface(
                // 无分类可选时，筛选器置灰不可点
                onClick = { if (categories.isNotEmpty()) expanded = true },
                shape = CircleShape,
                color = if (selected != null) vaultTint().copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = if (selected == null) {
                        stringResource(R.string.vault_all_categories)
                    } else {
                        selected
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected != null) vaultTint() else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vault_all_categories)) },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    }
                )
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category) },
                        onClick = {
                            onSelect(category)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultEntryCard(
    entry: VaultEntry,
    cardConfig: VaultCardConfig = VaultCardConfig(),
    onClick: () -> Unit,
    onCopy: () -> Unit
) {
    val cardColor = categoryColor(entry.category)
    val identityText = resolveIdentity(entry, cardConfig)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(12.dp)
        ) {
            CardHeader(entry, identityText, cardColor, onCopy)
            CardFooter(entry)
        }
    }
}

@Composable
private fun CardHeader(
    entry: VaultEntry,
    identityText: String,
    cardColor: androidx.compose.ui.graphics.Color,
    onCopyClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CardAvatar(entry, cardColor)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            CardTitleRow(entry, cardColor)
            if (identityText.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = identityText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onCopyClick, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = stringResource(R.string.vault_copy_password),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun CardAvatar(entry: VaultEntry, tintColor: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(tintColor.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        if (entry.avatarPath.isNotBlank()) {
            AsyncImage(
                model = File(entry.avatarPath),
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = entryIcon(entry.url),
                contentDescription = null,
                tint = tintColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CardTitleRow(entry: VaultEntry, tintColor: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (entry.category.isNotBlank() && entry.category != "其他") {
            Spacer(Modifier.width(6.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = tintColor.copy(alpha = 0.1f)
            ) {
                Text(
                    text = entry.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = tintColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun CardFooter(entry: VaultEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 52.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (entry.url.isNotBlank()) {
            Text(
                text = try { java.net.URL(entry.url).host } catch (_: Exception) { entry.url },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = stringResource(
                R.string.vault_updated_at,
                com.palmnote.domain.util.DateUtils.formatDate(entry.updatedAt)
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun VaultEmptyState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.LockOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(if (query.isBlank()) R.string.vault_empty else R.string.vault_no_result),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (query.isBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.vault_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

internal fun entryIcon(url: String): ImageVector {
    val normalized = url.lowercase(Locale.ROOT)
    return ENTRY_ICON_MAP.firstOrNull { (keywords) ->
        keywords.any(normalized::contains)
    }?.second ?: Icons.Outlined.Key
}

private val ENTRY_ICON_MAP: List<Pair<List<String>, ImageVector>> = listOf(
    listOf(
        "github", "gitlab", "bitbucket", "stackoverflow", "developer",
        "aws", "azure"
    ) to Icons.Outlined.Code,
    listOf(
        "google", "gmail", "youtube", "chrome", "android"
    ) to Icons.Outlined.Android,
    listOf(
        "mail", "outlook", "office", "icloud", "ymail"
    ) to Icons.Outlined.Mail,
    listOf(
        "steam", "epic", "uplay", "origin", "riot", "xbox",
        "playstation", "nintendo"
    ) to Icons.Outlined.SportsEsports,
    listOf(
        "facebook", "meta", "instagram", "twitter", "tiktok", "snapchat",
        "telegram", "whatsapp", "reddit"
    ) to Icons.AutoMirrored.Outlined.Chat,
    listOf(
        "amazon", "ebay", "aliexpress", "taobao", "jd.com", "pinduoduo"
    ) to Icons.Outlined.ShoppingCart,
    listOf(
        "weixin", "wechat", "qq", "linkedin", "zoom", "slack", "teams", "discord"
    ) to Icons.Outlined.Forum,
    listOf(
        "bank", "alipay", "pay", "applepay"
    ) to Icons.Outlined.AccountBalance
)

data class VaultCardConfig(
    val identity: String = "email_first"
)

private fun resolveIdentity(
    entry: VaultEntry,
    config: VaultCardConfig
): String = when (config.identity) {
    "username_first" -> entry.username.ifEmpty { entry.email }
    else -> entry.email.ifEmpty { entry.username }
}

private fun categoryColor(name: String): Color {
    val hash = name.hashCode()
    val hue = (hash % 360).let { if (it < 0) it + 360 else it }.toFloat()
    return androidx.compose.ui.graphics.Color.hsl(hue, 0.55f, 0.45f)
}

/** 无锁模式提示条：提示开启密码本锁定，保护私密数据。 */
@Composable
private fun NoLockBanner(onClick: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier
                .clickable { onClick() }
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.LockOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.vault_no_lock_banner),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
