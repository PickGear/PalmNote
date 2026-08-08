package com.palmnote.ui.navigation

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.palmnote.R
import com.palmnote.ui.asset.AddAssetScreen
import com.palmnote.ui.asset.AssetDetailScreen
import com.palmnote.ui.asset.AssetScreen
import com.palmnote.ui.bills.AddBillScreen
import com.palmnote.ui.bills.BillScreen
import com.palmnote.ui.bills.BillViewModel
import com.palmnote.ui.bills.BillDetailScreen
import com.palmnote.ui.bills.BudgetScreen
import com.palmnote.ui.bills.BillImportScreen
import com.palmnote.ui.bills.ReportScreen
import com.palmnote.ui.bills.AccountBookManageScreen
import com.palmnote.ui.dashboard.DashboardScreen
import com.palmnote.ui.search.SearchScreen
import com.palmnote.ui.settings.AboutScreen
import com.palmnote.ui.settings.PrivacyPolicyScreen
import com.palmnote.ui.settings.TermsOfServiceScreen
import com.palmnote.ui.settings.CategoryScreen
import com.palmnote.ui.settings.RecycleBinScreen
import com.palmnote.ui.settings.SettingsScreen
import com.palmnote.ui.settings.WalletEditScreen
import com.palmnote.ui.settings.WalletScreen
import com.palmnote.ui.settings.DataClearScreen
import com.palmnote.ui.settings.GeneralSettingsScreen
import com.palmnote.ui.settings.ReminderSettingsScreen
import com.palmnote.ui.settings.ManageCategoryScreen
import com.palmnote.ui.settings.DataStorageScreen
import com.palmnote.ui.settings.AppLockSettingsScreen

import com.palmnote.PalmNoteApp
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.ui.life.common.LifeNavHost
import com.palmnote.ui.backup.BackupScreen
import com.palmnote.ui.theme.*
import com.palmnote.feature.vault.vault.VaultScreen
import com.palmnote.feature.vault.vault.VaultDetailScreen
import com.palmnote.feature.vault.vault.VaultEditScreen
import com.palmnote.feature.vault.vault.VaultSettingsScreen

data class BottomNavItem(
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val activeColor: Color,
    val iconSize: Dp = 24.dp
)

private val bottomNavItems = listOf(
    BottomNavItem(R.string.nav_dashboard, Icons.Filled.Home, Icons.Outlined.Home, ModuleHome, iconSize = 26.dp),
    BottomNavItem(R.string.nav_asset, Icons.Filled.Inventory2, Icons.Outlined.Inventory2, ModuleItem, iconSize = 22.dp),
    BottomNavItem(R.string.nav_bill, Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, ModuleBill),
    BottomNavItem(R.string.nav_life, Icons.Filled.Favorite, Icons.Filled.FavoriteBorder, ModuleLife),
)

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

    NavHost(
        navController = navController,
        startDestination = MainTabs,
        modifier = Modifier.fillMaxSize(),
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
                    navController.navigate(BillImport)
                },
                onNavigateToAccountBook = {
                    navController.navigate(AccountBookManage)
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
                appNavController = navController
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
                onNavigateToEdit = { id -> navController.navigate(AddBill(billId = id)) }
            )
        }

        composable<Budget> {
            BudgetScreen(
                onNavigateBack = { navController.popBackStack() }
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

        composable<BillImport> {
            BillImportScreen(
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
                onNavigateToAbout = { navController.navigate(About) },
                onNavigateToAppLock = { navController.navigate(AppLockSettings) }
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
                viewModel = hiltViewModel()
            )
        }

        composable<Backup> {
            BackupScreen(
                onNavigateBack = { navController.popBackStack() }
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
                onNavigateToTerms = { navController.navigate(TermsOfService) }
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

        composable<TermsOfService> {
            TermsOfServiceScreen(
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
                onNavigateToGoal = { _ ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("navToTab", "life")
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

/**
 * Inner tab container — owns the bottom bar and a nested NavHost
 * for the four main tabs.  Sub-page navigation is forwarded to the
 * outer navController passed from [PalmNoteNavHost].
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
    onNavigateToAssetDetail: (Long) -> Unit,
    onNavigateToAddAsset: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToVault: () -> Unit,
    appNavController: NavHostController,
) {
    val tabNavController = rememberNavController()
    val tabBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentTabDestination = tabBackStackEntry?.destination

    var lifeChildAtHome by remember { mutableStateOf(true) }

    // 接收外层（Search 等）发来的"切到某 Tab"信号
    val mainTabsEntry by appNavController.currentBackStackEntryAsState()
    LaunchedEffect(mainTabsEntry) {
        val handle = mainTabsEntry?.savedStateHandle ?: return@LaunchedEffect
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
    val showBottomBar = lifeChildAtHome && (currentTabDestination?.hierarchy?.any {
        it.hasRoute<TabDashboard>() || it.hasRoute<TabAsset>() || it.hasRoute<TabBill>() || it.hasRoute<TabLife>()
    } ?: true)

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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(innerPadding)
            ) {
                NavHost(
                    navController = tabNavController,
                    startDestination = startTab,
                    enterTransition = { fadeIn(animationSpec = tween(200)) },
                    exitTransition = { fadeOut(animationSpec = tween(200)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(200)) },
                    popExitTransition = { fadeOut(animationSpec = tween(200)) }
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
                    onNavigateToVault = onNavigateToVault
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
                    onNavigateToAccountBook = onNavigateToAccountBook
                )
            }

            composable<TabLife> {
                LifeNavHost(onChildNavigated = { lifeChildAtHome = it })
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
