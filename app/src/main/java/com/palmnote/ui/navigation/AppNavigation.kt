package com.palmnote.ui.navigation

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
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
import androidx.navigation.NavType
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
import com.palmnote.ui.components.simpleViewModel
import com.palmnote.ui.life.common.LifeNavHost
import com.palmnote.ui.backup.BackupScreen
import com.palmnote.ui.theme.*

object Route {
    const val MainTabs = "main_tabs"
    const val Dashboard = "dashboard"
    const val Asset = "asset"
    const val Bill = "bill"
    const val Life = "life"
    const val Settings = "settings"
    const val AssetDetail = "asset_detail/{assetId}"
    const val AddAsset = "add_asset?assetId={assetId}"
    const val AddBill = "add_bill?billId={billId}&selectedDate={selectedDate}"
    const val BillDetail = "bill_detail/{billId}"
    const val Budget = "budget"
    const val Report = "report?selectedBookId={selectedBookId}&bookName={bookName}"
    const val BillImport = "bill_import"
    const val Category = "category?type={type}"
    const val About = "about"
    const val PrivacyPolicy = "privacy_policy"
    const val TermsOfService = "terms_of_service"
    const val RecycleBin = "recycle_bin"
    const val Wallet = "wallet"
    const val WalletEdit = "wallet_edit?walletId={walletId}"
    const val DataClear = "data_clear"
    const val Search = "search"
    const val AccountBookManage = "account_book_manage"
    const val Backup = "backup"
    const val GeneralSettings = "general_settings"
    const val ReminderSettings = "reminder_settings"
    const val ManageCategory = "manage_category"
    const val DataStorage = "data_storage"
    const val AppLockSettings = "app_lock_settings"
}

data class BottomNavItem(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val activeColor: Color,
    val iconSize: Dp = 24.dp
)

private val bottomNavItems = listOf(
    BottomNavItem(Route.Dashboard, R.string.nav_dashboard, Icons.Filled.Home, Icons.Outlined.Home, ModuleHome, iconSize = 26.dp),
    BottomNavItem(Route.Asset, R.string.nav_asset, Icons.Filled.Inventory2, Icons.Outlined.Inventory2, ModuleItem, iconSize = 22.dp),
    BottomNavItem(Route.Bill, R.string.nav_bill, Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, ModuleBill),
    BottomNavItem(Route.Life, R.string.nav_life, Icons.Filled.Favorite, Icons.Filled.FavoriteBorder, ModuleLife),
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
    val startDest = PalmNoteApp.cachedStartPage

    NavHost(
        navController = navController,
        startDestination = Route.MainTabs,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { fadeIn(animationSpec = tween(200)) },
        exitTransition = { fadeOut(animationSpec = tween(200)) },
        popEnterTransition = { fadeIn(animationSpec = tween(200)) },
        popExitTransition = { fadeOut(animationSpec = tween(200)) }
    ) {
        // ── Main tabs container ──────────────────────────────────
        composable(Route.MainTabs) {
            MainTabs(
                startTab = startDest,
                onNavigateToAddBill = { date ->
                    navController.navigate("add_bill?selectedDate=$date")
                },
                onNavigateToBillDetail = { billId ->
                    navController.navigate("bill_detail/$billId")
                },
                onNavigateToBudget = {
                    navController.navigate(Route.Budget)
                },
                onNavigateToReport = { selectedBookId, bookName ->
                    navController.navigate("report?selectedBookId=$selectedBookId&bookName=${Uri.encode(bookName)}")
                },
                onNavigateToImportCsv = {
                    navController.navigate(Route.BillImport)
                },
                onNavigateToAccountBook = {
                    navController.navigate(Route.AccountBookManage)
                },
                onNavigateToAssetDetail = { assetId ->
                    navController.navigate("asset_detail/$assetId")
                },
                onNavigateToAddAsset = {
                    navController.navigate(Route.AddAsset)
                },
                onNavigateToSettings = {
                    navController.navigate(Route.Settings)
                },
                onNavigateToSearch = {
                    navController.navigate(Route.Search)
                },
                appNavController = navController
            )
        }

        // ── Sub-pages (outside MainTabs, no bottom bar) ──────────

        composable(
            Route.AssetDetail,
            arguments = listOf(navArgument("assetId") { type = NavType.LongType })
        ) { backStackEntry ->
            val assetId = backStackEntry.arguments?.getLong("assetId") ?: 0L
            AssetDetailScreen(
                assetId = assetId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id ->
                    navController.navigate("add_asset?assetId=$id")
                }
            )
        }

        composable(
            Route.AddAsset,
            arguments = listOf(navArgument("assetId") { type = NavType.LongType; defaultValue = -1L })
        ) { backStackEntry ->
            val assetId = backStackEntry.arguments?.getLong("assetId").takeIf { it != -1L }
            AddAssetScreen(
                assetId = assetId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCategory = { categoryType ->
                    navController.navigate("category?type=$categoryType")
                }
            )
        }

        composable(
            Route.AddBill,
            arguments = listOf(
                navArgument("billId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("selectedDate") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStackEntry ->
            val billId = backStackEntry.arguments?.getLong("billId").takeIf { it != -1L }
            val selectedDate = backStackEntry.arguments?.getLong("selectedDate").takeIf { it != -1L }
            AddBillScreen(
                billId = billId,
                selectedDate = selectedDate,
                onBillDateSaved = { date ->
                    // previousBackStackEntry is MainTabs — write date there
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("savedBillDate", date)
                },
                onNavigateBack = { navController.popBackStack() },
                onNavigateToWallet = { navController.navigate(Route.Wallet) },
                onNavigateToCategory = { categoryType ->
                    navController.navigate("category?type=$categoryType")
                }
            )
        }

        composable(
            Route.BillDetail,
            arguments = listOf(navArgument("billId") { type = NavType.LongType })
        ) { backStackEntry ->
            val billId = backStackEntry.arguments?.getLong("billId") ?: 0L
            val allWallets by PalmNoteApp.container.walletRepository.getAllWallets().collectAsState(initial = emptyList())
            val walletNames = allWallets.associate { it.id to it.name }
            BillDetailScreen(
                billId = billId,
                walletNames = walletNames,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id -> navController.navigate("add_bill?billId=$id") }
            )
        }

        composable(Route.Budget) {
            BudgetScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            Route.Report,
            arguments = listOf(
                navArgument("selectedBookId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("bookName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val selectedBookId = backStackEntry.arguments?.getLong("selectedBookId") ?: -1L
            val bookName = backStackEntry.arguments?.getString("bookName") ?: stringResource(R.string.report_all_books)
            ReportScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddBill = { navController.navigate("add_bill") },
                selectedBookId = selectedBookId,
                bookName = bookName
            )
        }

        composable(Route.BillImport) {
            BillImportScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Route.Settings) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToGeneral = { navController.navigate(Route.GeneralSettings) },
                onNavigateToReminder = { navController.navigate(Route.ReminderSettings) },
                onNavigateToManageCategory = { navController.navigate(Route.ManageCategory) },
                onNavigateToDataStorage = { navController.navigate(Route.DataStorage) },
                onNavigateToAbout = { navController.navigate(Route.About) },
                onNavigateToAppLock = { navController.navigate(Route.AppLockSettings) }
            )
        }

        composable(Route.GeneralSettings) {
            GeneralSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = simpleViewModel { PalmNoteApp.container.settingsViewModel() }
            )
        }

        composable(Route.ReminderSettings) {
            ReminderSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = simpleViewModel { PalmNoteApp.container.settingsViewModel() }
            )
        }

        composable(Route.ManageCategory) {
            ManageCategoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToWallet = { navController.navigate(Route.Wallet) },
                onNavigateToAccountBook = { navController.navigate(Route.AccountBookManage) },
                onNavigateToCategory = { navController.navigate(Route.Category) }
            )
        }

        composable(Route.DataStorage) {
            DataStorageScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRecycleBin = { navController.navigate(Route.RecycleBin) },
                onNavigateToDataClear = { navController.navigate(Route.DataClear) },
                onNavigateToBackup = { navController.navigate(Route.Backup) },
                viewModel = simpleViewModel { PalmNoteApp.container.settingsViewModel() }
            )
        }

        composable(Route.Backup) {
            BackupScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            Route.Category,
            arguments = listOf(navArgument("type") { type = NavType.StringType; defaultValue = "ASSET" })
        ) { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: "ASSET"
            CategoryScreen(
                onNavigateBack = { navController.popBackStack() },
                initialType = type
            )
        }

        composable(Route.AccountBookManage) {
            AccountBookManageScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Route.About) {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPrivacy = { navController.navigate(Route.PrivacyPolicy) },
                onNavigateToTerms = { navController.navigate(Route.TermsOfService) }
            )
        }

        composable(Route.AppLockSettings) {
            AppLockSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = simpleViewModel { PalmNoteApp.container.settingsViewModel() }
            )
        }

        composable(Route.PrivacyPolicy) {
            PrivacyPolicyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Route.TermsOfService) {
            TermsOfServiceScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Route.RecycleBin) {
            RecycleBinScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Route.Wallet) {
            WalletScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddWallet = { navController.navigate("wallet_edit?walletId=0") },
                onNavigateToEditWallet = { walletId ->
                    navController.navigate("wallet_edit?walletId=$walletId")
                }
            )
        }

        composable(
            Route.WalletEdit,
            arguments = listOf(navArgument("walletId") { type = NavType.LongType; defaultValue = 0L })
        ) { backStackEntry ->
            val walletId = backStackEntry.arguments?.getLong("walletId")?.takeIf { it != 0L }
            WalletEditScreen(
                walletId = walletId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Route.DataClear) {
            DataClearScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Route.Search) {
            SearchScreen(
                onNavigateToAsset = { assetId ->
                    navController.navigate("asset_detail/$assetId")
                },
                onNavigateToBill = { billId ->
                    navController.navigate("bill_detail/$billId")
                },
                onNavigateToGoal = { goalId ->
                    navController.navigate(Route.Life)
                },
                onNavigateToAnniversary = { anniversaryId ->
                    navController.navigate(Route.Life)
                },
                onNavigateToMoment = { momentId ->
                    navController.navigate(Route.Life)
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
    startTab: String,
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
    appNavController: NavHostController,
) {
    val tabNavController = rememberNavController()
    val tabBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentTabDestination = tabBackStackEntry?.destination

    var lifeChildAtHome by remember { mutableStateOf(true) }
    val showBottomBar = lifeChildAtHome && (currentTabDestination?.route?.let { route ->
        bottomNavItems.any { item -> item.route == route }
    } ?: true)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            if (showBottomBar) {
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
                            bottomNavItems.forEach { item ->
                                val selected = currentTabDestination?.hierarchy?.any { it.route == item.route } == true
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) {
                                            tabNavController.navigate(item.route) {
                                                popUpTo(tabNavController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                            lifeChildAtHome = true
                                        }
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
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = tabNavController,
            startDestination = startTab,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) },
            popEnterTransition = { fadeIn(animationSpec = tween(200)) },
            popExitTransition = { fadeOut(animationSpec = tween(200)) }
        ) {
            composable(Route.Dashboard) {
                DashboardScreen(
                    // Simple navigation — keeps Dashboard in back stack
                    // (matches original behavior)
                    onNavigateToAsset = {
                        tabNavController.navigate(Route.Asset)
                    },
                    onNavigateToBill = {
                        tabNavController.navigate(Route.Bill)
                    },
                    onNavigateToLife = {
                        tabNavController.navigate(Route.Life)
                    },
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToSearch = onNavigateToSearch
                )
            }

            composable(Route.Asset) {
                AssetScreen(
                    onNavigateToDetail = { assetId ->
                        onNavigateToAssetDetail(assetId)
                    },
                    onNavigateToAdd = {
                        onNavigateToAddAsset()
                    }
                )
            }

            composable(Route.Bill) {
                val billViewModel: BillViewModel = simpleViewModel { PalmNoteApp.container.billViewModel() }

                // Observe date saved from AddBillScreen.
                // onBillDateSaved reads from the outer navController's
                // savedStateHandle (MainTabs entry), not the inner one.
                val savedBillDate by appNavController.currentBackStackEntry
                    ?.savedStateHandle
                    ?.getStateFlow<Long?>("savedBillDate", null)
                    ?.collectAsStateWithLifecycle()
                    ?: mutableStateOf(null)
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

            composable(Route.Life) {
                LifeNavHost(onChildNavigated = { lifeChildAtHome = it })
            }
        }
    }
}
