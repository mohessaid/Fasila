package app.fasila.ui

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.fasila.R
import app.fasila.core.SkyState
import app.fasila.ui.calendar.CalendarScreen
import app.fasila.ui.qibla.QiblaScreen
import app.fasila.ui.schedule.ScheduleScreen
import app.fasila.ui.settings.SettingsScreen
import app.fasila.ui.theme.FasilaTheme
import app.fasila.ui.theme.LivingSky
import app.fasila.ui.theme.LocalA11y
import app.fasila.ui.theme.LocalGlass
import app.fasila.ui.theme.glassColors
import app.fasila.ui.theme.systemReduceMotion
import app.fasila.ui.today.TodayScreen

enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    Today("today", R.string.tab_today, Icons.Filled.Home),
    Schedule("schedule", R.string.tab_schedule, Icons.AutoMirrored.Filled.List),
    Calendar("calendar", R.string.tab_calendar, Icons.Filled.DateRange),
    Qibla("qibla", R.string.tab_qibla, Icons.Filled.LocationOn),
    Settings("settings", R.string.tab_settings, Icons.Filled.Settings),
}

@Composable
fun FasilaRoot(vm: MainViewModel) {
    val today by vm.today.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reduceMotion = remember { systemReduceMotion(context) }
    val sky = SkyState.at(today, reduceMotion)

    FasilaTheme(sky.dominant, prefs.solidPanes) {
        val glass = glassColors(sky, LocalA11y.current.reduceTransparency)
        val view = LocalView.current
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = glass.isLight
                isAppearanceLightNavigationBars = glass.isLight
            }
        }
        CompositionLocalProvider(LocalGlass provides glass) {
            Box(Modifier.fillMaxSize()) {
                LivingSky(sky)
                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                val current = backStack?.destination?.route
                Scaffold(
                    containerColor = Color.Transparent,
                    contentColor = glass.content,
                    bottomBar = {
                        NavigationBar(containerColor = glass.fill, contentColor = glass.content) {
                            Tab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = current == tab.route,
                                    onClick = {
                                        nav.navigate(tab.route) {
                                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(tab.icon, contentDescription = null) },
                                    label = { Text(stringResource(tab.label)) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = glass.content,
                                        selectedTextColor = glass.content,
                                        unselectedIconColor = glass.secondary,
                                        unselectedTextColor = glass.secondary,
                                        indicatorColor = glass.border,
                                    ),
                                )
                            }
                        }
                    },
                ) { padding ->
                    NavHost(nav, startDestination = Tab.Today.route, modifier = Modifier.fillMaxSize()) {
                        composable(Tab.Today.route) { TodayScreen(vm, padding) { nav.navigate(Tab.Settings.route) } }
                        composable(Tab.Schedule.route) { ScheduleScreen(vm, padding) }
                        composable(Tab.Calendar.route) { CalendarScreen(vm, padding) }
                        composable(Tab.Qibla.route) { QiblaScreen(vm, padding) }
                        composable(Tab.Settings.route) { SettingsScreen(vm, padding) }
                    }
                }
            }
        }
    }
}

