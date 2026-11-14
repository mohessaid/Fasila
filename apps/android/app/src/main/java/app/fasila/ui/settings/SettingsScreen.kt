package app.fasila.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fasila.R
import app.fasila.core.FasilaConfig
import app.fasila.core.Keys
import app.fasila.core.TimeFormatter
import app.fasila.ui.MainViewModel
import app.fasila.ui.SectionTitle
import app.fasila.ui.currentLocale
import app.fasila.ui.prayerName
import app.fasila.ui.rememberFormatter
import app.fasila.ui.schedule.ChipMenu
import app.fasila.ui.theme.ChipShape
import app.fasila.ui.theme.GlassCard
import app.fasila.ui.theme.LocalGlass

@Composable
fun SettingsScreen(vm: MainViewModel, padding: PaddingValues) {
    val config by vm.config.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val state by vm.today.collectAsStateWithLifecycle()
    val locating by vm.locating.collectAsStateWithLifecycle()
    val fmt = rememberFormatter(state.zone)
    val g = LocalGlass.current
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) vm.useDeviceLocation()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.tab_settings), color = g.content, fontSize = 28.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )

        // ------------------------------------------------------------ location
        GlassCard {
            SectionTitle(stringResource(R.string.section_location), Modifier.padding(bottom = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, null, tint = g.content)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(config.locationName, color = g.content, fontSize = 17.sp)
                    Text("${config.country} · ${config.timezone}", color = g.secondary, fontSize = 13.sp)
                }
                if (locating) CircularProgressIndicator(color = g.accent, strokeWidth = 2.dp, modifier = Modifier.widthIn(max = 20.dp))
            }
            TextButton(onClick = {
                if (vm.hasLocationPermission()) vm.useDeviceLocation()
                else permissions.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }) { Text(stringResource(R.string.use_my_location), color = g.accent) }
            ToggleRow(stringResource(R.string.auto_location), prefs.autoLocation) { on -> vm.updatePrefs { it.copy(autoLocation = on) } }
            CitySearch(vm)
        }

        // ------------------------------------------------------------ calculation
        GlassCard {
            SectionTitle(stringResource(R.string.section_calculation), Modifier.padding(bottom = 4.dp))
            LabeledMenu(
                stringResource(R.string.method),
                vm.methods.firstOrNull { it.id == config.method }?.name ?: config.method,
                vm.methods.map { it.name to it.id },
            ) { vm.setMethod(it) }
            LabeledMenu(
                stringResource(R.string.madhab),
                stringResource(madhabRes(config.madhab)),
                FasilaConfig.MADHABS.map { stringResource(madhabRes(it)) to it },
            ) { v -> vm.updateConfig { it.set("params.madhab", v) } }
            LabeledMenu(
                stringResource(R.string.high_latitude_rule),
                stringResource(highLatRes(config.highLatitudeRule)),
                FasilaConfig.HIGH_LATITUDE_RULES.map { stringResource(highLatRes(it)) to it },
            ) { v -> vm.updateConfig { it.set("params.high_latitude_rule", v) } }
            LabeledMenu(
                stringResource(R.string.rounding),
                stringResource(roundingRes(config.rounding)),
                FasilaConfig.ROUNDINGS.map { stringResource(roundingRes(it)) to it },
            ) { v -> vm.updateConfig { it.set("params.rounding", v) } }
        }

        // ------------------------------------------------------------ adjustments
        GlassCard {
            SectionTitle(stringResource(R.string.section_adjustments), Modifier.padding(bottom = 4.dp))
            FasilaConfig.ADJUSTABLE.forEach { key ->
                Stepper(prayerName(key), config.adjustment(key), -30..30, fmt, suffix = R.string.minutes_short) { v ->
                    vm.updateConfig { it.set("params.adjustments.$key", v.toDouble()) }
                }
            }
        }

        // ------------------------------------------------------------ hijri
        GlassCard {
            SectionTitle(stringResource(R.string.section_hijri), Modifier.padding(bottom = 4.dp))
            Stepper(stringResource(R.string.hijri_adjustment), config.hijriDayAdjustment, -2..2, fmt, suffix = R.string.days_short) { v ->
                vm.updateConfig { it.set("hijri.day_adjustment", v) }
            }
            Text(stringResource(R.string.hijri_adjustment_help), color = g.secondary, fontSize = 13.sp)
        }

        // ------------------------------------------------------------ notifications
        GlassCard {
            SectionTitle(stringResource(R.string.section_notifications), Modifier.padding(bottom = 4.dp))
            Keys.PRAYERS.forEach { key ->
                ToggleRow(stringResource(R.string.adhan_for, prayerName(key)), key in prefs.adhan) { on ->
                    vm.updatePrefs { it.copy(adhan = if (on) it.adhan + key else it.adhan - key) }
                }
            }
            LabeledMenu(
                stringResource(R.string.pre_reminder),
                prefs.preReminderMinutes.takeIf { it > 0 }?.let { stringResource(R.string.minutes_short, fmt.number(it)) }
                    ?: stringResource(R.string.off),
                listOf(0, 5, 10, 15, 20, 30).map { m ->
                    (if (m == 0) stringResource(R.string.off) else stringResource(R.string.minutes_short, fmt.number(m))) to m
                },
            ) { m -> vm.updatePrefs { it.copy(preReminderMinutes = m) } }
        }

        // ------------------------------------------------------------ display
        GlassCard {
            SectionTitle(stringResource(R.string.section_display), Modifier.padding(bottom = 4.dp))
            val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',').substringBefore('-')
            val languages = listOf(
                stringResource(R.string.language_system) to "",
                "English" to "en",
                "العربية" to "ar",
                "Français" to "fr",
            )
            LabeledMenu(
                stringResource(R.string.language),
                languages.firstOrNull { it.second == current }?.first ?: languages.first().first,
                languages,
            ) { tag -> AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) }
            val numeralOptions = listOf(
                stringResource(R.string.numerals_auto) to "auto",
                "0 1 2 3" to "latn",
                "٠ ١ ٢ ٣" to "arab",
            )
            LabeledMenu(
                stringResource(R.string.numerals),
                numeralOptions.firstOrNull { it.second == prefs.numerals }?.first ?: numeralOptions.first().first,
                numeralOptions,
            ) { v -> vm.updatePrefs { it.copy(numerals = v) } }
            ToggleRow(stringResource(R.string.solid_panes), prefs.solidPanes) { on -> vm.updatePrefs { it.copy(solidPanes = on) } }
            Text(stringResource(R.string.solid_panes_help), color = g.secondary, fontSize = 13.sp)
        }
        Text(
            stringResource(R.string.about_core),
            color = g.secondary, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun CitySearch(vm: MainViewModel) {
    val g = LocalGlass.current
    val locale = currentLocale()
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) {
        val q = query.trim()
        if (q.length < 2) emptyList()
        else vm.cities.filter { it.name.contains(q, ignoreCase = true) || it.nameAr.contains(q) }.take(8)
    }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true,
        shape = ChipShape,
        placeholder = { Text(stringResource(R.string.search_city), color = g.secondary) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = g.content,
            unfocusedTextColor = g.content,
            focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.16f),
            unfocusedContainerColor = g.border,
            cursorColor = g.accent,
        ),
    )
    results.forEach { city ->
        Text(
            city.displayName(locale),
            color = g.content,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(role = Role.Button) {
                    vm.setPlace(city.toPlace(locale))
                    query = ""
                }
                .padding(horizontal = 8.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun <T> LabeledMenu(label: String, value: String, options: List<Pair<String, T>>, onSelect: (T) -> Unit) {
    val g = LocalGlass.current
    // Label above the menu: long Arabic/French values never squeeze the label.
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = g.secondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
        ChipMenu(value, options, label, onSelect)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val g = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = g.content, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = g.accent))
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, fmt: TimeFormatter, suffix: Int, onChange: (Int) -> Unit) {
    val g = LocalGlass.current
    val signed = (if (value > 0) "+" else if (value < 0) "−" else "") + fmt.number(kotlin.math.abs(value))
    val shown = stringResource(suffix, signed)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) { stateDescription = shown },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = g.content, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) {
            Text("−", color = g.content, fontSize = 22.sp, modifier = Modifier.semantics { contentDescription = "−" })
        }
        Text(shown, color = g.content, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 64.dp))
        IconButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) {
            Icon(Icons.Filled.Add, contentDescription = "+", tint = g.content)
        }
    }
}

private fun madhabRes(id: String) = if (id == "hanafi") R.string.madhab_hanafi else R.string.madhab_standard

private fun highLatRes(id: String) = when (id) {
    "middle_of_the_night" -> R.string.hlr_middle
    "seventh_of_the_night" -> R.string.hlr_seventh
    "twilight_angle" -> R.string.hlr_angle
    "none" -> R.string.hlr_none
    else -> R.string.hlr_auto
}

private fun roundingRes(id: String) = when (id) {
    "nearest" -> R.string.rounding_nearest
    "none" -> R.string.rounding_none
    else -> R.string.rounding_safe
}
