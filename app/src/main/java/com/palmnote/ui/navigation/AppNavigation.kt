package com.palmnote.ui.navigation

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.datastore.preferences.core.stringPreferencesKey
import com.palmnote.data.datastore.dataStore
import kotlinx.coroutines.flow.map
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
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
import com.palmnote.ui.settings.WalletScreen
import com.palmnote.ui.settings.DataClearScreen
import com.palmnote.ui.settings.GeneralSettingsScreen
import com.palmnote.ui.settings.ReminderSettingsScreen
import com.palmnote.ui.settings.ManageCategoryScreen
import com.palmnote.ui.settings.DataStorageScreen

import com.palmnote.PalmNoteApp
import com.palmnote.ui.components.simpleViewModel
import com.palmnote.ui.life.common.LifeNavHost
import com.palmnote.ui.backup.BackupScreen
import com.palmnote.ui.theme.*

object Route {
    const val Dashboard = "dashboard"
    const val Asset = "asset"
    const val Bill = "bill"
    const val Life = "life"
    const val Settings = "settings"
    const val AssetDetail = "asset_detail/{assetId}"
    const val AddAsset = "add_asset?assetId={assetId}"
    const val AddBill = "add_bill?billId={billId}"
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
    const val DataClear = "data_clear"
    const val Search = "search"
    const val AccountBookManage = "account_book_manage"
    const val Backup = "backup"
    const val GeneralSettings = "general_settings"
    const val ReminderSettings = "reminder_settings"
    const val ManageCategory = "manage_category"
    const val DataStorage = "data_storage"
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PalmNoteNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    var lifeChildAtHome by remember { mutableStateOf(true) }
    val showBottomBar = lifeChildAtHome && (currentDestination?.route?.let { route ->
        bottomNavItems.any { item -> item.route == route }
     } ?: true)

    val context = LocalContext.current
    val startDest = PalmNoteApp.cachedStartPage

        Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .exclude(WindowInsets.navigationBars),
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
                                val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
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
            navController = navController,
            startDestination = startDest,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(animationSpec = tween(80))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(80))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(80))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(80))
            }
        ) {
            composable(Route.Dashboard) {
                DashboardScreen(
                    onNavigateToAsset = { navController.navigate(Route.Asset) },
                    onNavigateToBill = { navController.navigate(Route.Bill) },
                    onNavigateToLife = { navController.navigate(Route.Life) },
                    onNavigateToSettings = { navController.navigate(Route.Settings) },
                    onNavigateToSearch = { navController.navigate(Route.Search) }
                )
            }

            composable(Route.Asset) {
                AssetScreen(
                    onNavigateToDetail = { assetId ->
                        navController.navigate("asset_detail/$assetId")
                    },
                    onNavigateToAdd = {
                        navController.navigate(Route.AddAsset)
                    }
                )
            }

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

            composable(Route.Bill) {
                BillScreen(
                    onNavigateToAdd = {
                        navController.navigate(Route.AddBill)
                    },
                    onNavigateToDetail = { billId ->
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
                    }
                )
            }

            composable(
                Route.AddBill,
                arguments = listOf(navArgument("billId") { type = NavType.LongType; defaultValue = -1L })
            ) { backStackEntry ->
                val billId = backStackEntry.arguments?.getLong("billId").takeIf { it != -1L }
                AddBillScreen(
                    billId = billId,
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
                BillDetailScreen(
                    billId = billId,
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
                    onNavigateToAddBill = { navController.navigate(Route.AddBill) },
                    selectedBookId = selectedBookId,
                    bookName = bookName
                )
            }

            composable(Route.BillImport) {
                BillImportScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Route.Life) {
                LifeNavHost(onChildNavigated = { lifeChildAtHome = it })
            }

            composable(Route.Settings) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToGeneral = { navController.navigate(Route.GeneralSettings) },
                    onNavigateToReminder = { navController.navigate(Route.ReminderSettings) },
                    onNavigateToManageCategory = { navController.navigate(Route.ManageCategory) },
                    onNavigateToDataStorage = { navController.navigate(Route.DataStorage) },
                    onNavigateToAbout = { navController.navigate(Route.About) }
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
                        // 目前跳转到Life页面，后续可以添加Goal详情路由
                        navController.navigate(Route.Life)
                    },
                    onNavigateToAnniversary = { anniversaryId ->
                        // 目前跳转到Life页面，后续可以添加Anniversary详情路由
                        navController.navigate(Route.Life)
                    },
                    onNavigateToMoment = { momentId ->
                        // 目前跳转到Life页面，后续可以添加Moment详情路由
                        navController.navigate(Route.Life)
                    },
                    onBack = { navController.popBackStack() }
                )
            }


        }
    }
}
