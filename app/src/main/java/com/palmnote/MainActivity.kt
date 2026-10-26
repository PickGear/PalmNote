package com.palmnote

import android.os.Bundle
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.compose.setContent
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.lock.AppLockManager
import com.palmnote.ui.lock.AppLockScreen
import com.palmnote.ui.lock.AppLockState
import com.palmnote.ui.navigation.PalmNoteNavHost
import com.palmnote.ui.onboarding.OnboardingScreen
import com.palmnote.ui.theme.PalmNoteTheme
import com.palmnote.ui.theme.WallpaperBackground
import com.palmnote.ui.widget.WidgetDeepLink
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

private data class WallpaperPrefs(
    val style: String,
    val opacity: Float,
    val blur: Float,
    val customUri: String,
    val customColor: String
)

/** 回到前台补刷桌面组件的节流间隔：切来切去不必每次都跑一遍全量查询。 */
private const val WIDGET_REFRESH_MIN_INTERVAL_MS = 5 * 60 * 1000L

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @javax.inject.Inject
    lateinit var appLockManager: com.palmnote.data.lock.AppLockManager

    @javax.inject.Inject
    lateinit var preferencesManager: com.palmnote.data.datastore.PreferencesManager

    @javax.inject.Inject
    lateinit var demoDataSeeder: com.palmnote.data.DemoDataSeeder

    @javax.inject.Inject
    lateinit var vaultLockManager: com.palmnote.feature.vault.VaultLockManager

    private var appBackgroundedAt = 0L
    private var cachedAutoLockMode = com.palmnote.data.datastore.PreferencesManager.AUTO_LOCK_MODE_SYSTEM
    private var cachedAutoLockTimeoutMinutes = com.palmnote.data.datastore.PreferencesManager.DEFAULT_AUTO_LOCK_TIMEOUT_MINUTES

    /** 上次「回到前台补刷桌面组件」的时间戳，用于节流。 */
    private var lastWidgetRefreshAt = 0L

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

    /**
     * 回到前台补刷一次桌面组件（节流 5 分钟）。
     *
     * 组件平时靠 30 分钟周期更新，所以**装上带界面改版的包后，桌面会一直显示旧界面**——
     * 周期没到就不会重画，用户会以为新版本没生效。打开应用就补刷一次，装完包一眼就能看到。
     */
    private fun maybeRefreshWidgetsOnForeground() {
        val now = System.currentTimeMillis()
        if (now - lastWidgetRefreshAt < WIDGET_REFRESH_MIN_INTERVAL_MS) return
        lastWidgetRefreshAt = now
        com.palmnote.ui.widget.WidgetUpdateHelper.refreshAllWidgets()
    }

    /**
     * 让启动/窗口资源（values-night 里的 splash_background、windowBackground、状态栏）跟随
     * **应用内**深浅色设置，而不是只听系统 uiMode。
     *
     * 只决定「首帧资源怎么解析」：运行中的配色仍由 Compose 的 PalmNoteTheme 管，所以这里
     * 不需要（也不会）触发 Activity 重建。模式已经一致时不重复设置。
     */
    private fun applyNightModeFromPrefs() {
        val app = application as? PalmNoteApp ?: return
        val wanted = when (app.preferencesManager.themeModeForSplash()) {
            "DARK" -> AppCompatDelegate.MODE_NIGHT_YES
            "LIGHT" -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != wanted) {
            AppCompatDelegate.setDefaultNightMode(wanted)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在 installSplashScreen()/super.onCreate() 之前：让 values-night 资源（开屏底色、
        // windowBackground、状态栏）跟随应用内深浅色，而不是只听系统 uiMode。否则系统浅色 +
        // 应用内选深色的用户，每次冷启动都会看到 1s 级白闪——正是 5e9d297/8d14f71 想消除的跳色。
        applyNightModeFromPrefs()
        // 必须在 super.onCreate() 之前：install 之后系统才会接管启动画面（Android 12+ 原生，
        // 低版本由 androidx 兼容库模拟）。保持条件见 AppStartupState：首页首帧数据到齐
        // 或超时（1000ms，从进程启动算）就放行 —— 不再出现「页面已经在、物品分布卡几秒后才蹦出来」。
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { AppStartupState.shouldHoldSplash() }
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

        // 回到前台补刷一次桌面组件：组件平时靠 30 分钟周期更新，装上带改版的包后
        // 桌面会一直显示旧界面（周期没到就不会重画），用户以为没生效。
        // 这里节流到 5 分钟一次，避免每次切回来都跑一遍全量查询。
        maybeRefreshWidgetsOnForeground()

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
            // 首帧前先取一份同步偏好快照给 initialValue 用：否则 Flow 第一帧还没到，
            // 只能用「无壁纸」的默认值 → 顶栏先亮后暗闪一下（见 PreferencesManager.themeSnapshot）。
            val initialTheme = remember { preferencesManager.themeSnapshot() }
            val preferences by remember {
                val theme = combine(
                    preferencesManager.themeMode,
                    preferencesManager.themeColor
                ) { mode, color -> mode to color }
                val wallpaper = combine(
                    preferencesManager.wallpaperStyle,
                    preferencesManager.wallpaperOpacity,
                    preferencesManager.wallpaperBlur,
                    preferencesManager.wallpaperCustomUri,
                    preferencesManager.wallpaperCustomColor
                ) { style, opacity, blur, uri, color -> WallpaperPrefs(style, opacity, blur, uri, color) }
                combine(theme, wallpaper) { t, w -> t to w }
            }.collectAsStateWithLifecycle(
                initialValue = (initialTheme.themeMode to initialTheme.themeColor) to
                    WallpaperPrefs(
                        initialTheme.wallpaperStyle,
                        initialTheme.wallpaperOpacity,
                        initialTheme.wallpaperBlur,
                        initialTheme.wallpaperCustomUri,
                        initialTheme.wallpaperCustomColor
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
            val onboardingCompleted by preferencesManager.onboardingCompleted.collectAsStateWithLifecycle(initialValue = null)
            val scope = rememberCoroutineScope()

            // 引导页 / 锁屏期间根本没有「首页数据」可等：立即放行启动画面，
            // 否则新用户第一次启动会白等满 AppStartupState 的上限（1000ms）。
            // onboardingCompleted 的初值是 null（DataStore 还没读完），读到 false 才放行——
            // 所以首启的开屏停留 ≈ DataStore 首次读取的耗时（几十毫秒），肉眼几乎不可见。
            LaunchedEffect(onboardingCompleted, lockState) {
                if (onboardingCompleted == false || lockState !is AppLockState.Unlocked) {
                    AppStartupState.disableHold()
                }
            }

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
                wallpaperCustomUri = wallpaper.customUri,
                wallpaperCustomColor = wallpaper.customColor
            ) {
                WallpaperBackground(modifier = Modifier.fillMaxSize()) {
                    if (onboardingCompleted == null) {
                        // Still loading onboarding state - show nothing
                    } else if (onboardingCompleted == false) {
                        OnboardingScreen(
                            onFinish = { demoEnabled ->
                                scope.launch {
                                    // 演示模式的选择在引导里一次定音（策略与设置页 setDemoModeEnabled 相同）：
                                    // 先落开关（播种器要读它），再播种/清理；此后不再有任何事后弹窗。
                                    preferencesManager.setLifeDemoMode(demoEnabled)
                                    if (demoEnabled) {
                                        demoDataSeeder.ensureSeeded(preferencesManager)
                                    } else {
                                        demoDataSeeder.clearAll(preferencesManager)
                                    }
                                    preferencesManager.setOnboardingCompleted(true)
                                }
                            }
                        )
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
        // 键与 tab 值都取自 WidgetDeepLink：生产端（组件 / 提醒通知）与这里必须完全一致
        val tab = intent?.getStringExtra(WidgetDeepLink.KEY_TAB) ?: return
        intent.getStringExtra(WidgetDeepLink.KEY_ITEM_ID)?.toLongOrNull()?.let {
            PalmNoteApp.pendingLifeDetailItemId = it
        }
        intent.getStringExtra(WidgetDeepLink.KEY_LIST_MODE)?.let {
            PalmNoteApp.pendingLifeListMode = it
        }
        PalmNoteApp.cachedStartPage = when (tab) {
            WidgetDeepLink.TAB_BILL, WidgetDeepLink.TAB_ADD_BILL,
            WidgetDeepLink.TAB_REPORT, WidgetDeepLink.TAB_BUDGET -> "bill"
            WidgetDeepLink.TAB_ASSET -> "asset"
            WidgetDeepLink.TAB_LIFE -> "life"
            else -> "dashboard"
        }
        if (tab == WidgetDeepLink.TAB_ADD_BILL) {
            PalmNoteApp.pendingNavigation = WidgetDeepLink.TAB_ADD_BILL
        }
        if (tab == WidgetDeepLink.TAB_REPORT) {
            // 小组件预算横幅 → 报表页：与 add_bill 同走 pendingNavigation（报表非 tab，是外层路由）
            PalmNoteApp.pendingNavigation = WidgetDeepLink.TAB_REPORT
        }
        if (tab == WidgetDeepLink.TAB_BUDGET) {
            // 小组件预算卡 → 预算页（外层路由）
            PalmNoteApp.pendingNavigation = WidgetDeepLink.TAB_BUDGET
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lockObserver)
    }
}
