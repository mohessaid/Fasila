package app.fasila.ui.today

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fasila.R
import app.fasila.alarm.AlarmScheduler
import app.fasila.alarm.Notifications
import app.fasila.core.Keys
import app.fasila.core.TimeFormatter
import app.fasila.core.TodayState
import app.fasila.core.isEstimated
import app.fasila.core.isFriday
import app.fasila.core.time
import app.fasila.ui.MainViewModel
import app.fasila.ui.hijriMonth
import app.fasila.ui.occasionName
import app.fasila.ui.prayerName
import app.fasila.ui.rememberFormatter
import app.fasila.ui.theme.ChipShape
import app.fasila.ui.theme.GlassCard
import app.fasila.ui.theme.LocalGlass
import app.fasila.ui.theme.RowShape
import app.fasila.ui.theme.TabularStyle
import app.fasila.ui.theme.lit
import app.fasila.ui.theme.pane

@Composable
fun TodayScreen(vm: MainViewModel, padding: PaddingValues, openSettings: () -> Unit) {
    val state by vm.today.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val locating by vm.locating.collectAsStateWithLifecycle()
    val locationError by vm.locationError.collectAsStateWithLifecycle()
    val fmt = rememberFormatter(state.zone)
    val context = LocalContext.current
    var explain by remember { mutableStateOf(false) }
    var exactOk by remember { mutableStateOf(AlarmScheduler.canScheduleExact(context)) }
    var notificationsOk by remember { mutableStateOf(Notifications.canPost(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        exactOk = AlarmScheduler.canScheduleExact(context)
        notificationsOk = Notifications.canPost(context)
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) vm.useDeviceLocation()
    }
    val requestLocation = {
        if (vm.hasLocationPermission()) vm.useDeviceLocation()
        else permissions.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Header(config.locationName, state, fmt) }

        if (!prefs.locationSet) item {
            Banner(
                title = stringResource(R.string.location_needed_title),
                body = stringResource(if (locationError) R.string.location_failed else R.string.location_needed_body),
                primary = stringResource(R.string.use_my_location) to requestLocation,
                secondary = stringResource(R.string.choose_city) to openSettings,
                busy = locating,
            )
        }
        if (!exactOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) item {
            Banner(
                title = stringResource(R.string.exact_alarms_title),
                body = stringResource(R.string.exact_alarms_body),
                primary = stringResource(R.string.allow) to {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)),
                    )
                },
            )
        }
        if (!notificationsOk) item {
            Banner(
                title = stringResource(R.string.notifications_off_title),
                body = stringResource(R.string.notifications_off_body),
                primary = stringResource(R.string.open_settings) to {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                },
            )
        }

        item { CelestialHero(state, fmt, Modifier.fillMaxWidth()) }
        item { PrayerList(state, fmt) { explain = true } }
        item { NightCard(state, fmt) }
        if (state.today.polarFallback) item {
            GlassCard { Text(stringResource(R.string.polar_note), color = LocalGlass.current.secondary) }
        }
    }

    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false },
            confirmButton = { TextButton(onClick = { explain = false }) { Text(stringResource(R.string.ok)) } },
            title = { Text(stringResource(R.string.estimated_title)) },
            text = { Text(stringResource(R.string.estimated_body)) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(place: String, state: TodayState, fmt: TimeFormatter) {
    val g = LocalGlass.current
    val d = state.today
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = g.content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                place, color = g.content, fontSize = 22.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(fmt.longDate(state.date), color = g.secondary, fontSize = 15.sp)
        Text(
            stringResource(
                R.string.hijri_date_fmt,
                fmt.number(d.hijriDay.toInt()), hijriMonth(d.hijriMonth.toInt()), fmt.number(d.hijriYear),
            ),
            color = g.secondary, fontSize = 15.sp,
        )
        if (d.occasions.isNotEmpty()) {
            Text(
                d.occasions.map { occasionName(it) }.joinToString(", "),
                color = g.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun PrayerList(state: TodayState, fmt: TimeFormatter, onExplain: () -> Unit) {
    val g = LocalGlass.current
    val day = state.today
    GlassCard(padding = 10.dp) {
        for (key in Keys.LISTED) {
            val t = day.time(key)
            val isPrayer = key != Keys.SUNRISE
            val isCurrent = isPrayer && state.current?.let { it.key == key && it.time == t } == true
            val isNext = state.next?.let { it.key == key && it.time == t } == true
            val estimated = day.isEstimated(key)
            val ink = if (isPrayer) g.content else g.secondary
            val past = t != null && t < state.now && !isCurrent
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (isNext) 64.dp else 50.dp)
                    .then(
                        when {
                            isNext -> Modifier.lit(g)
                            isCurrent -> Modifier.pane(g)
                            else -> Modifier
                        },
                    )
                    .semantics(mergeDescendants = true) {}
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        prayerName(key, day.isFriday),
                        color = if (past || !isPrayer) g.tertiary else ink,
                        fontSize = if (isNext) 19.sp else if (isPrayer) 17.sp else 14.sp,
                        fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Medium,
                    )
                    if (isNext && t != null) {
                        Text(stringResource(R.string.in_time, fmt.countdown(t - state.now)), color = g.secondary, fontSize = 12.5.sp, style = TabularStyle)
                    } else if (isCurrent) {
                        Text(stringResource(R.string.now), color = g.secondary, fontSize = 12.5.sp)
                    }
                }
                if (estimated && t != null) {
                    Spacer(Modifier.width(8.dp))
                    Row(
                        Modifier
                            .clickable(role = Role.Button, onClick = onExplain)
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = g.warning, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.estimated), color = g.warning, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (t == null) stringResource(R.string.unresolved) else fmt.time(t),
                    color = if (past || !isPrayer) g.tertiary else ink,
                    fontSize = if (isNext) 20.sp else if (isPrayer) 18.sp else 14.sp,
                    fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal,
                    style = TabularStyle,
                )
            }
        }
    }
}

@Composable
private fun NightCard(state: TodayState, fmt: TimeFormatter) {
    val g = LocalGlass.current
    GlassCard(padding = 16.dp) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(prayerName(Keys.MIDNIGHT), color = g.tertiary, fontSize = 13.sp)
                Text(fmt.time(state.today.midnight), color = g.content, style = TabularStyle)
            }
            Column(Modifier.weight(1f)) {
                Text(prayerName(Keys.LAST_THIRD), color = g.tertiary, fontSize = 13.sp)
                Text(fmt.time(state.today.lastThird), color = g.content, style = TabularStyle)
            }
            Column(Modifier.weight(1f)) {
                Text(prayerName(Keys.IMSAK), color = g.tertiary, fontSize = 13.sp)
                Text(fmt.time(state.tomorrow.imsak), color = g.content, style = TabularStyle)
            }
        }
    }
}

@Composable
private fun Banner(
    title: String,
    body: String,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>? = null,
    busy: Boolean = false,
) {
    val g = LocalGlass.current
    GlassCard {
        Text(title, color = g.content, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.size(4.dp))
        Text(body, color = g.secondary, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = g.accent, strokeWidth = 2.dp)
            secondary?.let { (label, action) -> TextButton(onClick = action) { Text(label, color = g.content) } }
            TextButton(onClick = primary.second, enabled = !busy) { Text(primary.first, color = g.accent, fontWeight = FontWeight.SemiBold) }
        }
    }
}
