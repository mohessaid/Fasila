package app.fasila.ui

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fasila.ui.theme.lit
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import app.fasila.ui.calendar.CalendarScreen
import app.fasila.ui.qibla.QiblaScreen
import app.fasila.ui.schedule.ScheduleScreen
import app.fasila.ui.settings.SettingsScreen
import app.fasila.ui.theme.FasilaTheme
import app.fasila.ui.theme.LivingSky
import app.fasila.ui.theme.LocalA11y
import app.fasila.ui.theme.LocalGlass
import app.fasila.ui.theme.glassColors
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
    val sky = today.sky

    FasilaTheme(prefs.solidPanes) {
        val glass = glassColors(sky, LocalA11y.current.reduceTransparency)
        val view = LocalView.current
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // The sky is always dark enough under the bars for light icons.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
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
                        GlassTabBar(current) { tab ->
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
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

/** Floating glass tab bar; the active tab is a lit pill (no indicator dots or underlines). */
@Composable
private fun GlassTabBar(current: String?, onSelect: (Tab) -> Unit) {
    val g = LocalGlass.current
    val bar = RoundedCornerShape(28.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(20.dp, bar, ambientColor = Color(0xFF060A14), spotColor = Color(0xFF060A14))
            .clip(bar)
            .background(if (g.solid) g.fill else Color(0xFF0B1020).copy(alpha = 0.95f), bar)
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Tab.entries.forEach { tab ->
            val selected = current == tab.route
            Column(
                Modifier
                    .weight(1f)
                    .then(if (selected) Modifier.lit(g, RoundedCornerShape(22.dp)) else Modifier.clip(RoundedCornerShape(22.dp)))
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab) })
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val ink = if (selected) g.content else g.tertiary
                Icon(tab.icon, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
                Text(
                    stringResource(tab.label), color = ink, fontSize = 11.sp, maxLines = 1,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}
