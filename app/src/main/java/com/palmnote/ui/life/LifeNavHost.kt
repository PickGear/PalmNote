package com.palmnote.ui.life

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute

/**
 * 生活页内部嵌套 NavHost。
 *
 * - 页面内路由：Home / Detail / List / DayRead（点格子→详情，点日历某天→当日回读）。
 * - 跨模块路由：通过 [tabNavController] 切到底部其它 Tab（记账/资产/首页），
 *   通过 [appNavController] 跳到外层子页（如搜索）。
 *
 * [onChildNavigated] 用于通知外层 MainTabs：是否处于首页（决定底部导航栏显隐）。
 */
@Composable
fun LifeNavHost(
    tabNavController: NavHostController,
    appNavController: NavHostController,
    onChildNavigated: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val atHome = backStack?.destination?.hasRoute<LifeHome>() == true

    LaunchedEffect(atHome) {
        onChildNavigated(atHome)
    }

    NavHost(
        navController = navController,
        startDestination = LifeHome,
        modifier = Modifier,
        enterTransition = { fadeIn(animationSpec = tween(200)) },
        exitTransition = { fadeOut(animationSpec = tween(200)) },
        popEnterTransition = { fadeIn(animationSpec = tween(200)) },
        popExitTransition = { fadeOut(animationSpec = tween(200)) }
    ) {
        composable<LifeHome> {
            val lifeVm: LifeHomeViewModel = hiltViewModel()
            val defaultView by lifeVm.defaultView.collectAsStateWithLifecycle()
            LifeScreen(
                onOpenDetail = { title, iconKey, accentHex, heroLabel, heroValue ->
                    navController.navigate(
                        LifeDetail(
                            title = title,
                            iconKey = iconKey,
                            accentHex = accentHex,
                            heroLabel = heroLabel,
                            heroValue = heroValue
                        )
                    )
                },
                onOpenList = { title, subtitle ->
                    navController.navigate(LifeList(title = title, subtitle = subtitle))
                },
                onOpenDay = { dayLabel ->
                    navController.navigate(LifeDayRead(dayLabel = dayLabel))
                },
                onCrossTab = { route ->
                    tabNavController.navigate(route) {
                        popUpTo(tabNavController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onOpenStats = { navController.navigate(LifeStats) },
                defaultView = defaultView,
                onSetDefaultView = { lifeVm.setDefaultView(it) }
            )
        }

        composable<LifeDetail> { entry ->
            val d = entry.toRoute<LifeDetail>()
            LifeDetailScreen(
                title = d.title,
                iconKey = d.iconKey,
                accentHex = d.accentHex,
                heroLabel = d.heroLabel,
                heroValue = d.heroValue,
                onBack = { navController.popBackStack() }
            )
        }

        composable<LifeList> { entry ->
            val l = entry.toRoute<LifeList>()
            LifeListScreen(
                title = l.title,
                subtitle = l.subtitle,
                onBack = { navController.popBackStack() }
            )
        }

        composable<LifeDayRead> { entry ->
            val r = entry.toRoute<LifeDayRead>()
            LifeDayReadScreen(
                dayLabel = r.dayLabel,
                onBack = { navController.popBackStack() }
            )
        }

        composable<LifeStats> {
            LifeStatsScreen(onBack = { navController.popBackStack() })
        }
    }
}
