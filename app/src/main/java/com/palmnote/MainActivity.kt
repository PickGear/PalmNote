package com.palmnote

import android.os.Bundle
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.lock.AppLockManager
import com.palmnote.ui.lock.AppLockScreen
import com.palmnote.ui.lock.AppLockState
import com.palmnote.ui.navigation.PalmNoteNavHost
import com.palmnote.ui.theme.PalmNoteTheme
import com.palmnote.ui.theme.WallpaperBackground
import androidx.compose.ui.res.stringResource
import com.palmnote.app.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

private data class WallpaperPrefs(
    val style: String,
    val opacity: Float,
    val blur: Float,
    val customUri: String
)

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @javax.inject.Inject
    lateinit var appLockManager: com.palmnote.data.lock.AppLockManager

    @javax.inject.Inject
    lateinit var preferencesManager: com.palmnote.data.datastore.PreferencesManager

    @javax.inject.Inject
    lateinit var vaultLockManager: com.palmnote.feature.vault.VaultLockManager

    private var appBackgroundedAt = 0L
    private var cachedAutoLockMode = com.palmnote.data.datastore.PreferencesManager.AUTO_LOCK_MODE_SYSTEM
    private var cachedAutoLockTimeoutMinutes = com.palmnote.data.datastore.PreferencesManager.DEFAULT_AUTO_LOCK_TIMEOUT_MINUTES

    // 密码本自动锁定（与页面级 VaultLockOnBackground 一致，但覆盖所有页面）：
    // 离开密码本页切后台时同样按规则回锁，避免内存 DK 跨后台长期驻留
    private var vaultBackgroundedAt = 0L
    private var vaultRequireAuth = true

    // 自动锁定规则（选择权交给用户）：immediate=切后台立即锁 / system=跟随系统锁屏（默认） / timeout=锁屏+超时。
    // 冷启动仍立即锁（安全兜底）。
    private val lockObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> {
                if (appLockManager.isLockEnabled() && appLockManager.hasPin()) {
                    appBackgroundedAt = System.currentTimeMillis()
                }
                if (vaultRequireAuth) {
                    vaultBackgroundedAt = System.currentTimeMillis()
                }
            }
            Lifecycle.Event.ON_START -> {
                val backgroundedAt = appBackgroundedAt
                appBackgroundedAt = 0L
                val vaultTs = vaultBackgroundedAt
                vaultBackgroundedAt = 0L
                val timeoutMs = cachedAutoLockTimeoutMinutes * 60_000L
                if (com.palmnote.data.lock.AutoLockHelper.shouldLock(this, cachedAutoLockMode, backgroundedAt, timeoutMs)) {
                    appLockManager.lock()
                }
                if (vaultRequireAuth && com.palmnote.data.lock.AutoLockHelper.shouldLock(this, cachedAutoLockMode, vaultTs, timeoutMs)) {
                    vaultLockManager.lock()
                }
            }
            else -> {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 手机（<600dp 最小宽度）锁竖屏；平板保持横屏适配（NavigationRail）
        if (resources.configuration.smallestScreenWidthDp < 600) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(lockObserver)

        // 缓存自动锁定模式与超时，供回锁判断使用
        lifecycleScope.launch {
            preferencesManager.autoLockMode.collect { cachedAutoLockMode = it }
        }
        lifecycleScope.launch {
            preferencesManager.autoLockTimeoutMinutes.collect { cachedAutoLockTimeoutMinutes = it }
        }
        lifecycleScope.launch {
            preferencesManager.vaultRequireAuth.collect { vaultRequireAuth = it }
        }

        // 处理小组件跳转：设置目标 Tab
        handleWidgetIntent(intent)

        // 冷启动/进程被杀恢复（非配置变更）时锁定；旋转（配置变更）不锁。
        // 用 isChangingConfigurations() 而非 savedInstanceState==null：进程被杀后从最近任务恢复
        // 时 savedInstanceState 可能非 null（系统恢复了 UI 状态），但应重新上锁。
        // 注意：AppLockManager 在 init 时读到的 DataStore 快照可能仍是空（冷启动竞态），
        // 因此这里等 DataStore 首次发射后再刷新缓存并决定是否上锁，避免锁被绕过。
        if (!isChangingConfigurations()) {
            lifecycleScope.launch {
                preferencesManager.appLockEnabledFlow.first()
                preferencesManager.encryptedPinFlow.first()
                appLockManager.refreshFromStore()
                if (appLockManager.isLockEnabled() && appLockManager.hasPin()) {
                    appLockManager.lock()
                }
            }
        }

        setContent {
            val preferences by remember {
                val theme = combine(
                    preferencesManager.themeMode,
                    preferencesManager.themeColor
                ) { mode, color -> mode to color }
                val wallpaper = combine(
                    preferencesManager.wallpaperStyle,
                    preferencesManager.wallpaperOpacity,
                    preferencesManager.wallpaperBlur,
                    preferencesManager.wallpaperCustomUri
                ) { style, opacity, blur, uri -> WallpaperPrefs(style, opacity, blur, uri) }
                combine(theme, wallpaper) { t, w -> t to w }
            }.collectAsStateWithLifecycle(
                initialValue = ("SYSTEM" to PreferencesManager.DEFAULT_THEME_COLOR) to
                    WallpaperPrefs(
                        PreferencesManager.DEFAULT_WALLPAPER_STYLE,
                        PreferencesManager.DEFAULT_WALLPAPER_OPACITY,
                        PreferencesManager.DEFAULT_WALLPAPER_BLUR,
                        ""
                    )
            )
            val (themePrefs, wallpaper) = preferences
            val (mode, themeColorId) = themePrefs
            val isDarkTheme = when (mode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }
            val themeColor = com.palmnote.ui.theme.ThemePackages.getById(themeColorId).let {
                if (isDarkTheme) it.darkPrimary else it.lightPrimary
            }
            val lockState by appLockManager.lockState.collectAsStateWithLifecycle()
            val privacyAgreed by preferencesManager.privacyAgreed.collectAsStateWithLifecycle(initialValue = null)
            val showPrivacyDialog = privacyAgreed == false
            val scope = rememberCoroutineScope()

            // 应用锁启用时禁止截图/录屏，防止最近任务缩略图泄露财务数据；
            // 实时跟随开关变化（运行中开启/关闭都立即生效）
            val appLockEnabled by appLockManager.appLockEnabledFlow().collectAsStateWithLifecycle(initialValue = appLockManager.isLockEnabled())
            LaunchedEffect(appLockEnabled) {
                if (appLockEnabled) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            PalmNoteTheme(
                darkTheme = isDarkTheme,
                themeColor = themeColor,
                wallpaperStyle = wallpaper.style,
                wallpaperOpacity = wallpaper.opacity,
                wallpaperBlur = wallpaper.blur,
                wallpaperCustomUri = wallpaper.customUri
            ) {
                WallpaperBackground(modifier = Modifier.fillMaxSize()) {
                    if (privacyAgreed == null) {
                        // Still loading privacy state - show nothing
                    } else if (privacyAgreed == false) {
                        var showPolicy by rememberSaveable { mutableStateOf(false) }
                        var showTerms by rememberSaveable { mutableStateOf(false) }

                        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .systemBarsPadding()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(Modifier.height(48.dp))

                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Spa,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(Modifier.height(20.dp))

                                Text(
                                    stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Spacer(Modifier.height(24.dp))

                                Text(
                                    stringResource(R.string.privacy_dialog_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Spacer(Modifier.height(12.dp))

                                Text(
                                    stringResource(R.string.privacy_dialog_text),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Start
                                )

                                Spacer(Modifier.height(24.dp))

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                Spacer(Modifier.height(20.dp))

                                Text(
                                    stringResource(R.string.privacy_dialog_view_links),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Start
                                )

                                Spacer(Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = { showPolicy = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.about_privacy_policy))
                                }

                                Spacer(Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = { showTerms = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.about_terms_of_service))
                                }

                                Spacer(Modifier.height(8.dp))

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                Spacer(Modifier.height(24.dp))

                                Button(
                                    onClick = {
                                        scope.launch { preferencesManager.setPrivacyAgreed(true) }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Text(
                                        stringResource(R.string.privacy_dialog_agree),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }

                                Spacer(Modifier.height(12.dp))

                                TextButton(
                                    onClick = { finishAffinity() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        stringResource(R.string.privacy_dialog_disagree),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(Modifier.height(24.dp))
                            }
                        }

                        if (showPolicy) {
                            FullDocumentOverlay(
                                title = stringResource(R.string.about_privacy_policy),
                                lines = com.palmnote.ui.settings.getPrivacyPolicyLines(),
                                onBack = { showPolicy = false }
                            )
                        }

                        if (showTerms) {
                            FullDocumentOverlay(
                                title = stringResource(R.string.about_terms_of_service),
                                lines = com.palmnote.ui.settings.getTermsOfServiceLines(),
                                onBack = { showTerms = false }
                            )
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= 33) {
                            val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                            // rememberSaveable：旋转/重建不重复弹权限请求
                            var hasRequestedNotification by rememberSaveable { mutableStateOf(false) }
                            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                            LaunchedEffect(lifecycleOwner) {
                                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                                    if (!hasRequestedNotification) {
                                        hasRequestedNotification = true
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            AnimatedContent(
                                targetState = lockState,
                                transitionSpec = {
                                    // 进入锁屏立即覆盖，不播放动画，避免旧内容短暂可见
                                    if (targetState is AppLockState.Locked || targetState is AppLockState.NeedSetup) {
                                        EnterTransition.None togetherWith ExitTransition.None
                                    } else {
                                        (fadeIn(tween(240)) + scaleIn(initialScale = 0.97f, animationSpec = tween(240))) togetherWith
                                            fadeOut(tween(160))
                                    }
                                },
                                label = "appLockTransition"
                            ) { ls ->
                                when (ls) {
                                    is AppLockState.Locked, is AppLockState.NeedSetup -> {
                                        AppLockScreen(
                                            appLockManager = appLockManager,
                                            onUnlocked = { appLockManager.unlock() }
                                        )
                                    }
                                    is AppLockState.Unlocked -> {
                                        PalmNoteNavHost()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        val tab = intent?.getStringExtra("WIDGET_TAB") ?: return
        PalmNoteApp.cachedStartPage = when (tab) {
            "bill", "add_bill" -> "bill"
            "asset" -> "asset"
            "life" -> "life"
            "vault" -> "vault"
            else -> "dashboard"
        }
        if (tab == "add_bill") {
            PalmNoteApp.pendingNavigation = "add_bill"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lockObserver)
    }
}

@Composable
private fun FullDocumentOverlay(title: String, lines: List<String>, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                lines.forEach { line ->
                    val isZh = java.util.Locale.getDefault().language == "zh"
                    val isHeading = if (isZh) {
                        listOf("一、", "二、", "三、", "四、", "五、", "六、", "七、", "八、", "九、", "十").any { line.startsWith(it) }
                    } else {
                        line.matches(Regex("^\\d+\\..*"))
                    } || line.endsWith(title)
                    Text(
                        text = line,
                        style = if (isHeading) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
