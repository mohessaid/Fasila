package app.fasila.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fasila.R
import app.fasila.core.Keys
import app.fasila.core.isFriday
import app.fasila.core.localDate
import app.fasila.core.time
import app.fasila.ui.MainViewModel
import app.fasila.ui.currentLocale
import app.fasila.ui.hijriMonth
import app.fasila.ui.occasionName
import app.fasila.ui.prayerName
import app.fasila.ui.rememberFormatter
import app.fasila.ui.theme.GlassCard
import app.fasila.ui.theme.LocalGlass
import app.fasila.ui.theme.RowShape
import app.fasila.ui.theme.TabularStyle
import uniffi.fasila_ffi.DayInfo
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields

@Composable
fun CalendarScreen(vm: MainViewModel, padding: PaddingValues) {
    val state by vm.today.collectAsStateWithLifecycle()
    val core by vm.core.collectAsStateWithLifecycle()
    val fmt = rememberFormatter(state.zone)
    val g = LocalGlass.current
    val locale = currentLocale()

    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(state.date).toString()) }
    val month = YearMonth.parse(monthText)
    var selectedText by rememberSaveable { mutableStateOf(state.date.toString()) }
    val selected = LocalDate.parse(selectedText)

    val days: List<DayInfo> = remember(month, core) { vm.repo.days(month.atDay(1), month.lengthOfMonth()) }
    val firstDow: DayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val lead = ((month.atDay(1).dayOfWeek.value - firstDow.value) + 7) % 7

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = { monthText = month.minusMonths(1).toString() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_month), tint = g.content)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    fmt.monthYear(month.atDay(1)), color = g.content, fontSize = 22.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.semantics { heading() },
                )
                if (days.isNotEmpty()) {
                    val a = days.first()
                    val b = days.last()
                    val hijri = if (a.hijriMonth == b.hijriMonth) "${hijriMonth(a.hijriMonth.toInt())} ${fmt.number(a.hijriYear)}"
                    else "${hijriMonth(a.hijriMonth.toInt())} – ${hijriMonth(b.hijriMonth.toInt())} ${fmt.number(b.hijriYear)}"
                    Text(hijri, color = g.secondary, fontSize = 14.sp)
                }
            }
            IconButton(onClick = { monthText = month.plusMonths(1).toString() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_month), tint = g.content)
            }
        }

        GlassCard(padding = 10.dp) {
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    Text(
                        firstDow.plus(i.toLong()).getDisplayName(TextStyle.NARROW, locale),
                        color = g.secondary, fontSize = 12.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clearAndSetSemantics { },
                    )
                }
            }
            val cells: List<DayInfo?> = List(lead) { null } + days
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    for (i in 0 until 7) {
                        val d = week.getOrNull(i)
                        Box(Modifier.weight(1f).aspectRatio(0.8f).padding(2.dp)) {
                            if (d != null) {
                                val date = d.localDate
                                DayCell(
                                    d = d,
                                    isToday = date == state.date,
                                    isSelected = date == selected,
                                    gregorian = fmt.number(date.dayOfMonth),
                                    hijri = fmt.number(d.hijriDay.toInt()),
                                    a11y = "${fmt.longDate(date)}, ${fmt.number(d.hijriDay.toInt())} ${hijriMonth(d.hijriMonth.toInt())}" +
                                        d.occasions.map { occasionName(it) }.joinToString("") { ", $it" },
                                ) { selectedText = date.toString() }
                            }
                        }
                    }
                }
            }
        }

        val sel = days.firstOrNull { it.localDate == selected }
        if (sel != null) DayDetails(sel, fmt)
    }
}

@Composable
private fun DayCell(
    d: DayInfo,
    isToday: Boolean,
    isSelected: Boolean,
    gregorian: String,
    hijri: String,
    a11y: String,
    onClick: () -> Unit,
) {
    val g = LocalGlass.current
    Column(
        Modifier
            .fillMaxSize()
            .background(if (isSelected) g.border else androidx.compose.ui.graphics.Color.Transparent, RowShape)
            .then(if (isToday) Modifier.border(1.5.dp, g.accent, RowShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = a11y
                selected = isSelected
                role = Role.Button
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(gregorian, color = g.content, fontSize = 16.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
        Text(hijri, color = g.secondary, fontSize = 11.sp)
        if (d.occasions.isNotEmpty()) {
            Spacer(Modifier.size(2.dp))
            Box(Modifier.size(5.dp).background(g.accent, CircleShape))
        }
    }
}

@Composable
private fun DayDetails(d: DayInfo, fmt: app.fasila.core.TimeFormatter) {
    val g = LocalGlass.current
    GlassCard {
        Text(fmt.longDate(d.localDate), color = g.content, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Text(
            stringResource(R.string.hijri_date_fmt, fmt.number(d.hijriDay.toInt()), hijriMonth(d.hijriMonth.toInt()), fmt.number(d.hijriYear)),
            color = g.secondary, fontSize = 14.sp,
        )
        d.occasions.forEach { Text("• " + occasionName(it), color = g.accent, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.size(8.dp))
        for (k in Keys.LISTED) {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(prayerName(k, d.isFriday), color = if (k == Keys.SUNRISE) g.secondary else g.content, modifier = Modifier.weight(1f))
                Text(fmt.time(d.time(k)), color = g.content, style = TabularStyle)
            }
        }
    }
}
