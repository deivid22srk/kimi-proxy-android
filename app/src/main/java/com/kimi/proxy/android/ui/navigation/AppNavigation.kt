package com.kimi.proxy.android.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kimi.proxy.android.KimiProxyApp
import com.kimi.proxy.android.R
import com.kimi.proxy.android.ui.LocalAppContainer
import com.kimi.proxy.android.ui.screens.AccountsScreen
import com.kimi.proxy.android.ui.screens.HomeScreen
import com.kimi.proxy.android.ui.screens.LoginScreen
import com.kimi.proxy.android.ui.screens.SettingsScreen
import com.kimi.proxy.android.ui.theme.KimiProxyTheme
import com.kimi.proxy.android.ui.theme.ThemeMode

sealed class Dest(val route: String, val label: Int, val icon: ImageVector) {
    data object Home : Dest("home", R.string.nav_home, Icons.Outlined.Home)
    data object Login : Dest("login", R.string.nav_login, Icons.Outlined.Login)
    data object Accounts : Dest("accounts", R.string.nav_accounts, Icons.Outlined.AccountCircle)
    data object Settings : Dest("settings", R.string.nav_settings, Icons.Outlined.Settings)
}

private val destinations = listOf(Dest.Home, Dest.Login, Dest.Accounts, Dest.Settings)

@Composable
fun KimiProxyApp() {
    val container = remember { KimiProxyApp.get().container }
    val themeMode by container.repository.themeMode.collectAsState(initial = "system")
    val dynamicColor by container.repository.dynamicColor.collectAsState(initial = true)

    val resolved = when (themeMode) {
        "light" -> ThemeMode.LIGHT
        "dark" -> ThemeMode.DARK
        else -> ThemeMode.SYSTEM
    }

    KimiProxyTheme(themeMode = resolved, dynamicColor = dynamicColor) {
        androidx.compose.material3.Surface {
            AppScaffold()
        }
    }
}

@Composable
private fun AppScaffold() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { d ->
                    val selected = current?.hierarchy?.any { it.route == d.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(d.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(d.icon, contentDescription = null) },
                        label = { Text(stringResource(d.label)) }
                    )
                }
            }
        }
    ) { inner ->
        NavHost(
            navController = navController,
            startDestination = Dest.Home.route,
            modifier = Modifier.padding(inner)
        ) {
            composable(Dest.Home.route) { HomeScreen(onNavigate = { navController.navigate(it) }) }
            composable(Dest.Login.route) {
                LoginScreen(onCaptured = { navController.navigate(Dest.Accounts.route) })
            }
            composable(Dest.Accounts.route) { AccountsScreen() }
            composable(Dest.Settings.route) { SettingsScreen() }
        }
    }
}
