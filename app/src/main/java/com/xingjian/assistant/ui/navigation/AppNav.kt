package com.xingjian.assistant.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xingjian.assistant.di.AppContainer
import com.xingjian.assistant.ui.ai.AiScreen
import com.xingjian.assistant.ui.comms.CommsScreen
import com.xingjian.assistant.ui.game.GameScreen
import com.xingjian.assistant.ui.home.HomeScreen
import com.xingjian.assistant.ui.ledger.LedgerScreen
import com.xingjian.assistant.ui.settings.SettingsScreen
import com.xingjian.assistant.ui.theme.LocalCustomBackground

object Routes {
    const val HOME = "home"
    const val COMMS = "comms"
    const val LEDGER = "ledger"
    const val GAME = "game"
    const val AI = "ai"
    const val SETTINGS = "settings"
}

data class TabItem(val route: String, val label: String, val icon: ImageVector)

val AppTabs = listOf(
    TabItem(Routes.HOME, "首页", Icons.Filled.Home),
    TabItem(Routes.COMMS, "通讯", Icons.Filled.Call),
    TabItem(Routes.LEDGER, "记账", Icons.Filled.ShoppingCart),
    TabItem(Routes.GAME, "游戏", Icons.Filled.Star),
    TabItem(Routes.SETTINGS, "设置", Icons.Filled.Settings)
)

@Composable
fun AppNav(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            AppScaffold("首页汇总", Routes.HOME, navController) { padding ->
                HomeScreen(container, Modifier.padding(padding))
            }
        }
        composable(Routes.COMMS) {
            AppScaffold("通讯记录", Routes.COMMS, navController) { padding ->
                CommsScreen(container, Modifier.padding(padding))
            }
        }
        composable(Routes.GAME) {
            AppScaffold("游戏排行", Routes.GAME, navController) { padding ->
                GameScreen(container, Modifier.padding(padding))
            }
        }
        composable(Routes.LEDGER) {
            AppScaffold("记账中心", Routes.LEDGER, navController) { padding ->
                LedgerScreen(container, Modifier.padding(padding))
            }
        }
        composable(Routes.AI) {
            AppScaffold("AI 助手", Routes.AI, navController) { padding ->
                AiScreen(container, Modifier.padding(padding))
            }
        }
        composable(Routes.SETTINGS) {
            AppScaffold("设置", Routes.SETTINGS, navController) { padding ->
                SettingsScreen(
                    container = container,
                    modifier = Modifier.padding(padding),
                    onOpenAi = { navController.navigate(Routes.AI) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    route: String,
    navController: NavController,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val hasBackground = LocalCustomBackground.current
    val barColor = if (hasBackground) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = barColor)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = barColor) {
                AppTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = {
                            if (route != tab.route) {
                                navController.navigate(tab.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = {
                            Text(
                                text = tab.label,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Clip
                            )
                        }
                    )
                }
            }
        },
        floatingActionButton = floatingActionButton,
        containerColor = if (hasBackground) Color.Transparent else MaterialTheme.colorScheme.background,
        content = content
    )
}
