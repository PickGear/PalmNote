package com.palmnote.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.palmnote.PalmNoteApp
import com.palmnote.app.BuildConfig
import com.palmnote.app.R
import com.palmnote.feature.vault.vault.VaultDetailScreen
import com.palmnote.feature.vault.vault.VaultEditScreen
import com.palmnote.feature.vault.vault.VaultScreen
import com.palmnote.feature.vault.vault.VaultSettingsScreen
import com.palmnote.ui.asset.AddAssetScreen
import com.palmnote.ui.asset.AssetDetailScreen
import com.palmnote.ui.asset.AssetScreen
import com.palmnote.ui.backup.BackupScreen
import com.palmnote.ui.bills.AccountBookManageScreen
import com.palmnote.ui.bills.AddBillScreen
import com.palmnote.ui.bills.BillDetailScreen
import com.palmnote.ui.bills.BillExportScreen
import com.palmnote.ui.bills.BillImportHubScreen
import com.palmnote.ui.bills.BillImportScreen
import com.palmnote.ui.bills.BillScreen
import com.palmnote.ui.bills.BillViewModel
import com.palmnote.ui.bills.BudgetScreen
import com.palmnote.ui.bills.ReimbursementScreen
import com.palmnote.ui.bills.ReportScreen
import com.palmnote.ui.dashboard.DashboardScreen
import com.palmnote.ui.life.LifeNavHost
import com.palmnote.ui.search.SearchScreen
import com.palmnote.ui.settings.AboutScreen
import com.palmnote.ui.settings.AppLockSettingsScreen
import com.palmnote.ui.settings.CategoryScreen
import com.palmnote.ui.settings.DataClearScreen
import com.palmnote.ui.settings.DataExchangeScreen
import com.palmnote.ui.settings.DataStorageScreen
import com.palmnote.ui.settings.GeneralSettingsScreen
import com.palmnote.ui.settings.LicenseScreen
import com.palmnote.ui.settings.ManageCategoryScreen
import com.palmnote.ui.settings.PrivacyPolicyScreen
import com.palmnote.ui.settings.RecycleBinScreen
import com.palmnote.ui.settings.ReminderSettingsScreen
import com.palmnote.ui.settings.SettingsScreen
import com.palmnote.ui.settings.VersionHistoryScreen
import com.palmnote.ui.settings.WalletEditScreen
import com.palmnote.ui.settings.WalletScreen
import com.palmnote.ui.settings.WidgetPinScreen
import com.palmnote.ui.theme.*
import com.palmnote.ui.widget.WidgetPinViewModel

data class BottomNavItem(
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val activeColor: Color,
    val iconSize: Dp = 24.dp
)

// README 截图用 `-PhideDemoBanner=true` 构建时隐藏「示例模式」状态条（见 build.gradle.kts）。
// 发布包必须是 false —— 状态条是产品刻意保留的状态指示（本次会话内可关，重启复现），别把它关掉。

private val bottomNavItems = listOf(
    BottomNavItem(R.string.nav_dashboard, Icons.Filled.Home, Icons.Outlined.Home, ModuleHome, iconSize = 26.dp),
    BottomNavItem(R.string.nav_asset, Icons.Filled.Inventory2, Icons.Outlined.Inventory2, ModuleItem, iconSize = 22.dp),
    BottomNavItem(R.string.nav_bill, Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, ModuleBill),
    BottomNavItem(R.string.nav_life, Icons.Filled.Favorite, Icons.Filled.FavoriteBorder, ModuleLife),
)

/**
 * 「示例模式」横幅槽位：读共享 VM 的开关 / 关闭态 / 当前 tab，可见时渲染 [AppBanner]。
 *
 * 从 [PalmNoteNavHost] 抽成独立函数：三个可见性条件（开关 && 未关闭 && 未隐藏构建）
 * 的复杂度留在小函数里，主函数保持在 detekt 的圈复杂度阈值内。
 * 文案随 tab 切换：生活 tab 用「互斥」口径（[DemoModeViewModel.lifeTabSelected]，
 * 由 MainTabs 上报），其余页面用通用口径。
 */
@Composable
private fun DemoModeBannerSlot(demoModeVm: DemoModeViewModel) {
    val demoModeOn by demoModeVm.demoModeOn.collectAsStateWithLifecycle()
    if (demoModeOn && !demoModeVm.bannerDismissed && !BuildConfig.HIDE_DEMO_BANNER) {
        com.palmnote.ui.components.AppBanner(
            text = stringResource(
                if (demoModeVm.lifeTabSelected) {
                    R.string.life_demo_banner_life_tab
                } else {
                    R.string.life_demo_banner
                }
            ),
            // 用主题主色：与引导页示例页同一语义同一色，且跟随用户选的主题包
            accent = MaterialTheme.colorScheme.primary,
            onClose = demoModeVm::onBannerDismissed
        )
    }
}

/**
 * Outer NavHost — manages all routes.
 * MainTabs (tabs + bottom bar) is a single composable here,
 * sub-pages (AddBill, Settings, etc.) live at this level.
 * Navigating to a sub-page replaces MainTabs entirely, so the
 * bottom bar disappears without any layout shift.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PalmNoteNavHost() {
    val navController = rememberNavController()
    val startTab: Any = when (PalmNoteApp.cachedStartPage) {
        "asset" -> TabAsset
        "bill" -> TabBill
        "life" -> TabLife
        else -> TabDashboard
    }

    // 处理小组件跳转
    val pendingNav = PalmNoteApp.pendingNavigation
    if (pendingNav == "add_bill") {
        PalmNoteApp.pendingNavigation = null
        LaunchedEffect(Unit) {
            navController.navigate(AddBill())
        }
    }
    if (pendingNav == "report") {
        PalmNoteApp.pendingNavigation = null
        LaunchedEffect(Unit) {
            navController.navigate(Report())
        }
    }
    if (pendingNav == "budget") {
        PalmNoteApp.pendingNavigation = null
        LaunchedEffect(Unit) {
            navController.navigate(Budget)
        }
    }

    // 小组件点事件 → 深链生活记录详情：经 MainTabs 的 savedStateHandle 传给 LifeNavHost
    PalmNoteApp.pendingLifeDetailItemId?.let { itemId ->
        PalmNoteApp.pendingLifeDetailItemId = null
        navController.currentBackStackEntry?.savedStateHandle?.apply {
            set("navToTab", "life")
            set("lifeDetailItemId", itemId)
        }
    }
    // 小组件页脚 → 深链完整清单
    PalmNoteApp.pendingLifeListMode?.let { mode ->
        PalmNoteApp.pendingLifeListMode = null
        navController.currentBackStackEntry?.savedStateHandle?.apply {
            set("navToTab", "life")
            set("lifeListMode", mode)
        }
    }

    // 「示例模式」横幅的共享状态：在根壳层获取 → activity 作用域单实例；
    // MainTabs（composable 目的地内）经由参数拿到**同一实例**，用它上报当前 tab。
    val demoModeVm: DemoModeViewModel = hiltViewModel()

    // 「示例模式」横幅挂在 NavHost 之上的固定槽位（布局流里，纪律见 AppBanner KDoc）：
    // 主界面四个 tab 与全部二级页（设置 / 详情 / 搜索……）都可见 —— 此前挂在 MainTabs
    // 壳层里，跳到二级页时整个 MainTabs 被替换，横幅跟着消失（用户指出）。
    // statusBarsPadding 由本层统一消费：MainTabs 与各二级页自己的 statusBarsPadding /
    // Scaffold inset 由此归零，不会双重避让。引导页与锁屏在 NavHost 之外，不受影响。
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        DemoModeBannerSlot(demoModeVm)
        NavHost(
            navController = navController,
            startDestination = MainTabs,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) },
            popEnterTransition = { fadeIn(animationSpec = tween(200)) },
            popExitTransition = { fadeOut(animationSpec = tween(200)) }
        ) {
            // ── Main tabs container ──────────────────────────────────
            composable<MainTabs> {
                MainTabs(
                    startTab = startTab,
                    onNavigateToAddBill = { date ->
                        navController.navigate(AddBill(selectedDate = date))
                    },
                    onNavigateToBillDetail = { billId ->
                        navController.navigate(BillDetail(billId))
                    },
                    onNavigateToBudget = {
                        navController.navigate(Budget)
                    },
                    onNavigateToReport = { selectedBookId, bookName ->
                        navController.navigate(Report(selectedBookId, bookName))
                    },
                    onNavigateToImportCsv = {
                        navController.navigate(BillImportHub)
                    },
                    onNavigateToAccountBook = {
                        navController.navigate(AccountBookManage)
                    },
                    onNavigateToReimbursement = {
                        navController.navigate(Reimbursement)
                    },
                    onNavigateToAssetDetail = { assetId ->
                        navController.navigate(AssetDetail(assetId))
                    },
                    onNavigateToAddAsset = {
                        navController.navigate(AddAsset())
                    },
                    onNavigateToSettings = {
                        navController.navigate(Settings)
                    },
                    onNavigateToSearch = {
                        navController.navigate(Search)
                    },
                    onNavigateToVault = {
                        navController.navigate(Vault)
                    },
                    appNavController = navController,
                    demoModeVm = demoModeVm
                )
            }

            // ── Sub-pages (outside MainTabs, no bottom bar) ──────────

            composable<AssetDetail> { backStackEntry ->
                val assetDetail = backStackEntry.toRoute<AssetDetail>()
                AssetDetailScreen(
                    assetId = assetDetail.assetId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { id ->
                        navController.navigate(AddAsset(id))
                    }
                )
            }

            composable<AddAsset> { backStackEntry ->
                val addAsset = backStackEntry.toRoute<AddAsset>()
                AddAssetScreen(
                    assetId = addAsset.assetId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCategory = { categoryType ->
                        navController.navigate(Category(categoryType))
                    }
                )
            }

            composable<AddBill> { backStackEntry ->
                val addBill = backStackEntry.toRoute<AddBill>()
                AddBillScreen(
                    billId = addBill.billId,
                    selectedDate = addBill.selectedDate,
                    onBillDateSaved = { date ->
                        // previousBackStackEntry is MainTabs — write date there
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("savedBillDate", date)
                    },
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToWallet = { navController.navigate(Wallet) },
                    onNavigateToCategory = { categoryType ->
                        navController.navigate(Category(categoryType))
                    }
                )
            }

            composable<BillDetail> { backStackEntry ->
                val billDetail = backStackEntry.toRoute<BillDetail>()
                BillDetailScreen(
                    billId = billDetail.billId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { id -> navController.navigate(AddBill(billId = id)) },
                    onNavigateToBill = { id ->
                        // 详情页之间是「同一笔报销业务的两面」互查（支出 ↔ 关联收入 ↔ 该收入涵盖的支出）。
                        // 用替换而非堆叠：否则来回点几次导航栈会无限加深（支出→收入→支出 可成环），
                        // 返回键要按很多次才能退出。inclusive 只弹掉 BillDetail 层，上级页面（搜索/报销）保留。
                        navController.navigate(BillDetail(id)) {
                            popUpTo<BillDetail> { inclusive = true }
                        }
                    }
                )
            }

            composable<Budget> {
                BudgetScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<Reimbursement> {
                ReimbursementScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { billId -> navController.navigate(BillDetail(billId)) }
                )
            }

            composable<Report> { backStackEntry ->
                val report = backStackEntry.toRoute<Report>()
                val bookName = report.bookName.ifEmpty { stringResource(R.string.report_all_books) }
                ReportScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAddBill = { navController.navigate(AddBill()) },
                    selectedBookId = report.selectedBookId,
                    bookName = bookName
                )
            }

            composable<BillImportHub> {
                BillImportHubScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSelectImport = { navController.navigate(BillImport) },
                    onSelectExport = { navController.navigate(BillExport) }
                )
            }

            composable<BillImport> {
                BillImportScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<BillExport> {
                BillExportScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<Settings> {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToGeneral = { navController.navigate(GeneralSettings) },
                    onNavigateToReminder = { navController.navigate(ReminderSettings) },
                    onNavigateToManageCategory = { navController.navigate(ManageCategory) },
                    onNavigateToDataStorage = { navController.navigate(DataStorage) },
                    onNavigateToWidget = { navController.navigate(WidgetSettings) },
                    onNavigateToAbout = { navController.navigate(About) },
                    onNavigateToAppLock = { navController.navigate(AppLockSettings) }
                )
            }

            composable<WidgetSettings> {
                // 不透明度按组件各自设置（在该组件的配置页里，或在添加页面板里挑好、落桌时写进新实例），
                // 落桌收尾交给 ViewModel（配置按 appWidgetId 存，只有它拿得到 DataStore）
                val widgetViewModel: WidgetPinViewModel = hiltViewModel()
                WidgetPinScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onApplyOpacity = widgetViewModel::applyOpacity,
                    onApplyBackground = widgetViewModel::applyBackground,
                    onApplyBlur = widgetViewModel::applyBlur,
                    books = widgetViewModel.books,
                    onApplyBook = widgetViewModel::applyBook,
                    onApplyAccent = widgetViewModel::applyAccent,
                    // 已落桌实例的编辑：进面板前读该实例的配置回填，「保存」时五项整体写回
                    loadInstance = widgetViewModel::loadInstance,
                    onSaveInstance = widgetViewModel::saveInstance
                )
            }

            composable<GeneralSettings> {
                GeneralSettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = hiltViewModel()
                )
            }

            composable<ReminderSettings> {
                ReminderSettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = hiltViewModel()
                )
            }

            composable<ManageCategory> {
                ManageCategoryScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToWallet = { navController.navigate(Wallet) },
                    onNavigateToAccountBook = { navController.navigate(AccountBookManage) },
                    onNavigateToCategory = { navController.navigate(Category()) }
                )
            }

            composable<DataStorage> {
                DataStorageScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToRecycleBin = { navController.navigate(RecycleBin) },
                    onNavigateToDataClear = { navController.navigate(DataClear) },
                    onNavigateToBackup = { navController.navigate(Backup) },
                    onNavigateToDataExchange = { navController.navigate(DataExchange) },
                    viewModel = hiltViewModel()
                )
            }

            composable<Backup> {
                BackupScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<DataExchange> {
                DataExchangeScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToBackup = { navController.navigate(Backup) }
                )
            }

            composable<Category> { backStackEntry ->
                val category = backStackEntry.toRoute<Category>()
                CategoryScreen(
                    onNavigateBack = { navController.popBackStack() },
                    initialType = category.type
                )
            }

            composable<AccountBookManage> {
                AccountBookManageScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<About> {
                AboutScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPrivacy = { navController.navigate(PrivacyPolicy) },
                    onNavigateToLicense = { navController.navigate(OpenSourceLicense) },
                    onNavigateToVersionHistory = { navController.navigate(VersionHistory) }
                )
            }

            composable<AppLockSettings> {
                AppLockSettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = hiltViewModel()
                )
            }

            composable<Vault> {
                VaultScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { entryId ->
                        navController.navigate(VaultDetail(entryId))
                    },
                    onNavigateToEdit = {
                        navController.navigate(VaultEdit())
                    },
                    onNavigateToSettings = {
                        navController.navigate(VaultSettings)
                    }
                )
            }

            composable<VaultDetail> {
                VaultDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { entryId ->
                        navController.navigate(VaultEdit(entryId))
                    }
                )
            }

            composable<VaultEdit> {
                VaultEditScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<VaultSettings> {
                VaultSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<PrivacyPolicy> {
                PrivacyPolicyScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<OpenSourceLicense> {
                LicenseScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<VersionHistory> {
                VersionHistoryScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<RecycleBin> {
                RecycleBinScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<Wallet> {
                WalletScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAddWallet = { navController.navigate(WalletEdit()) },
                    onNavigateToEditWallet = { walletId ->
                        navController.navigate(WalletEdit(walletId))
                    }
                )
            }

            composable<WalletEdit> { backStackEntry ->
                val walletEdit = backStackEntry.toRoute<WalletEdit>()
                WalletEditScreen(
                    walletId = walletEdit.walletId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<DataClear> {
                DataClearScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<Search> {
                SearchScreen(
                    onNavigateToAsset = { assetId ->
                        navController.navigate(AssetDetail(assetId))
                    },
                    onNavigateToBill = { billId ->
                        navController.navigate(BillDetail(billId))
                    },
                    onNavigateToGoal = { itemId ->
                        // 目标已是生活模板下的条目：切到生活 Tab 并直接打开该条目的详情页
                        navController.previousBackStackEntry?.savedStateHandle?.apply {
                            set("navToTab", "life")
                            set("lifeDetailItemId", itemId)
                        }
                        navController.popBackStack()
                    },
                    onNavigateToAnniversary = { _ ->
                        navController.previousBackStackEntry?.savedStateHandle?.set("navToTab", "life")
                        navController.popBackStack()
                    },
                    onNavigateToMoment = { _ ->
                        navController.previousBackStackEntry?.savedStateHandle?.set("navToTab", "life")
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

/**
 * Inner tab container — owns the bottom bar and a nested NavHost
 * for the four main tabs.  Sub-page navigation is forwarded to the
 * outer navController passed from [PalmNoteNavHost].
 *
 * [demoModeVm] 由 [PalmNoteNavHost] 传入（activity 作用域单实例，与根壳层横幅共享）：
 * MainTabs 只负责把「当前是否停在生活 tab」上报到 [DemoModeViewModel.lifeTabSelected]，
 * 横幅本体与文案切换都在根壳层渲染。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTabs(
    startTab: Any,
    onNavigateToAddBill: (Long) -> Unit,
    onNavigateToBillDetail: (Long) -> Unit,
    onNavigateToBudget: () -> Unit,
    onNavigateToReport: (Long, String) -> Unit,
    onNavigateToImportCsv: () -> Unit,
    onNavigateToAccountBook: () -> Unit,
    onNavigateToReimbursement: () -> Unit,
    onNavigateToAssetDetail: (Long) -> Unit,
    onNavigateToAddAsset: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToVault: () -> Unit,
    appNavController: NavHostController,
    demoModeVm: DemoModeViewModel,
) {
    val tabNavController = rememberNavController()
    val tabBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentTabDestination = tabBackStackEntry?.destination

    var lifeChildAtHome by remember { mutableStateOf(true) }

    // 接收外层（Search / 小组件深链）发来的"切到某 Tab"信号
    val mainTabsEntry by appNavController.currentBackStackEntryAsState()
    var pendingLifeDetailId by remember { mutableStateOf<Long?>(null) }
    var pendingLifeListTarget by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(mainTabsEntry) {
        val handle = mainTabsEntry?.savedStateHandle ?: return@LaunchedEffect
        handle.get<Long>("lifeDetailItemId")?.let {
            handle.remove<Long>("lifeDetailItemId")
            pendingLifeDetailId = it
        }
        handle.get<String>("lifeListMode")?.let {
            handle.remove<String>("lifeListMode")
            pendingLifeListTarget = it
        }
        val target = handle.get<String>("navToTab") ?: return@LaunchedEffect
        handle.remove<String>("navToTab")
        val route: Any = when (target) {
            "life" -> TabLife
            "bill" -> TabBill
            "asset" -> TabAsset
            else -> TabDashboard
        }
        tabNavController.navigate(route) {
            popUpTo(tabNavController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        lifeChildAtHome = true
    }
    val showBottomBar = lifeChildAtHome && (
        currentTabDestination?.hierarchy?.any {
            it.hasRoute<TabDashboard>() || it.hasRoute<TabAsset>() || it.hasRoute<TabBill>() || it.hasRoute<TabLife>()
        } ?: true
        )

    // 平板/折叠屏宽屏适配：宽度 Expanded 时用 NavigationRail 替代底部导航
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    val isWide = activity != null &&
        calculateWindowSizeClass(activity).widthSizeClass == WindowWidthSizeClass.Expanded
    val isTabSelected: (Int) -> Boolean = { index ->
        currentTabDestination?.hierarchy?.any {
            when (index) {
                0 -> it.hasRoute<TabDashboard>()
                1 -> it.hasRoute<TabAsset>()
                2 -> it.hasRoute<TabBill>()
                else -> it.hasRoute<TabLife>()
            }
        } == true
    }

    // 上报当前 tab 给根壳层横幅：生活 tab 用「互斥」口径文案，其余 tab 与
    // 二级页用通用口径。DisposableEffect 只 key 在 Unit 上 —— 复位仅发生在
    // MainTabs 离开组合（跳二级页）时；若随 tab 变化，切换瞬间会先复位再上报，
    // 横幅文案闪一帧通用口径。
    LaunchedEffect(currentTabDestination) {
        demoModeVm.onLifeTabSelected(isTabSelected(3))
    }
    DisposableEffect(Unit) {
        onDispose { demoModeVm.onLifeTabSelected(false) }
    }
    val onTabClick: (Int) -> Unit = { index ->
        val target: Any = when (index) {
            0 -> TabDashboard
            1 -> TabAsset
            2 -> TabBill
            else -> TabLife
        }
        tabNavController.navigate(target) {
            popUpTo(tabNavController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        lifeChildAtHome = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            if (!isWide && showBottomBar) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(
                                WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            bottomNavItems.forEachIndexed { index, item ->
                                TabNavItem(
                                    item = item,
                                    selected = isTabSelected(index),
                                    onClick = { onTabClick(index) }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (isWide && showBottomBar) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    Spacer(Modifier.height(8.dp))
                    bottomNavItems.forEachIndexed { index, item ->
                        NavigationRailItem(
                            selected = isTabSelected(index),
                            onClick = { onTabClick(index) },
                            icon = {
                                Icon(
                                    imageVector = if (isTabSelected(index)) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = stringResource(item.labelRes)
                                )
                            },
                            label = { Text(stringResource(item.labelRes)) }
                        )
                    }
                }
            }
            // 壳层统一避让状态栏并消费 inset：各 tab 页面自己的 statusBarsPadding
            // 会基于「已消费」的窗口 insets 计算，自动归零 —— 否则横幅下面会叠出
            // 一整段「页面又避让了一次」的双重空白（真机截图实测过）。
            // （横幅上提到根壳层后，状态栏消费改由 PalmNoteNavHost 顶部的 Column 完成，
            // 语义不变：本层的 statusBarsPadding 在其下自动归零，保留作防御。）
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(innerPadding)
                    .statusBarsPadding()
            ) {
                // 「示例模式」状态条已上提到根壳层（PalmNoteNavHost 的 Column）：
                // 跳到二级页（设置 / 详情 / 搜索……）时横幅不再随 MainTabs 一起消失。
                // MainTabs 只负责上报当前 tab（见上方 LaunchedEffect），横幅本体
                // 与文案切换都在根壳层渲染。
                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = tabNavController,
                        startDestination = startTab,
                        // 转场必须显式给 None：不传参数时库默认是 fadeIn/fadeOut(tween(700))，
                        // 会叠在各页自己的入场动画上把它掩盖掉（正是上一版误删参数后发生的事）。
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None }
                    ) {
                        composable<TabDashboard> {
                            DashboardScreen(
                                // Simple navigation — keeps Dashboard in back stack
                                // (matches original behavior)
                                onNavigateToAsset = {
                                    tabNavController.navigate(TabAsset)
                                },
                                onNavigateToBill = {
                                    tabNavController.navigate(TabBill)
                                },
                                onNavigateToLife = {
                                    tabNavController.navigate(TabLife)
                                },
                                onNavigateToSettings = onNavigateToSettings,
                                onNavigateToSearch = onNavigateToSearch,
                                onNavigateToVault = onNavigateToVault,
                                onNavigateToAddBill = { onNavigateToAddBill(System.currentTimeMillis()) },
                                onNavigateToAddAsset = onNavigateToAddAsset
                            )
                        }

                        composable<TabAsset> {
                            AssetScreen(
                                onNavigateToDetail = { assetId ->
                                    onNavigateToAssetDetail(assetId)
                                },
                                onNavigateToAdd = {
                                    onNavigateToAddAsset()
                                }
                            )
                        }

                        composable<TabBill> {
                            val billViewModel: BillViewModel = hiltViewModel()

                            // Observe date saved from AddBillScreen.
                            // onBillDateSaved reads from the outer navController's
                            // savedStateHandle (MainTabs entry), not the inner one.
                            val savedBillDate by appNavController.currentBackStackEntry
                                ?.savedStateHandle
                                ?.getStateFlow<Long?>("savedBillDate", null)
                                ?.collectAsStateWithLifecycle()
                                ?: remember { mutableStateOf(null) }
                            LaunchedEffect(savedBillDate) {
                                savedBillDate?.let { date ->
                                    billViewModel.syncDateFromSaved(date)
                                    appNavController.currentBackStackEntry
                                        ?.savedStateHandle
                                        ?.remove<Long>("savedBillDate")
                                }
                            }

                            BillScreen(
                                viewModel = billViewModel,
                                onNavigateToAdd = { date ->
                                    onNavigateToAddBill(date)
                                },
                                onNavigateToDetail = { billId ->
                                    onNavigateToBillDetail(billId)
                                },
                                onNavigateToBudget = onNavigateToBudget,
                                onNavigateToReport = { selectedBookId, bookName ->
                                    onNavigateToReport(selectedBookId, bookName)
                                },
                                onNavigateToImportCsv = onNavigateToImportCsv,
                                onNavigateToAccountBook = onNavigateToAccountBook,
                                onNavigateToReimbursement = onNavigateToReimbursement
                            )
                        }

                        composable<TabLife> {
                            LifeNavHost(
                                onChildNavigated = { lifeChildAtHome = it },
                                pendingDetailItemId = pendingLifeDetailId,
                                onPendingDetailConsumed = { pendingLifeDetailId = null },
                                pendingListMode = pendingLifeListTarget,
                                onPendingListConsumed = { pendingLifeListTarget = null }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabNavItem(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
            .align(Alignment.CenterVertically)
            .padding(top = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(26.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                contentDescription = stringResource(item.labelRes),
                modifier = Modifier.size(item.iconSize),
                tint = if (selected) item.activeColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(item.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) item.activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
