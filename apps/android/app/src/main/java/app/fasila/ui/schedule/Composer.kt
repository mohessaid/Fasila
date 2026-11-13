package app.fasila.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fasila.R
import app.fasila.core.Keys
import app.fasila.core.ScheduleItem
import app.fasila.core.TimeFormatter
import app.fasila.core.parseObject
import app.fasila.ui.MainViewModel
import app.fasila.ui.lang
import app.fasila.ui.prayerName
import app.fasila.ui.rememberFormatter
import app.fasila.ui.theme.ChipShape
import app.fasila.ui.theme.GlassCard
import app.fasila.ui.theme.LocalGlass
import app.fasila.ui.theme.TabularStyle
import kotlinx.coroutines.delay
import uniffi.fasila_ffi.FasilaException
import uniffi.fasila_ffi.OccurrenceInfo
import uniffi.fasila_ffi.SlotInfo
import uniffi.fasila_ffi.describeAnchor
import uniffi.fasila_ffi.parseSentence
import java.time.LocalDate
import kotlin.math.abs

/** Fields the user set with a chip; re-applied when the sentence is parsed again. */
private enum class Pinned { KIND, RECURRENCE, REMIND }

/**
 * One line of text and a chip row that stay in sync (docs/DESIGN.md → Scheduling UX).
 * Typing fills the chips via the shared Rust parser; tapping a chip rewrites the sentence
 * from the core's own anchor description, which the parser reads back unchanged.
 */
@Composable
fun Composer(vm: MainViewModel) {
    val g = LocalGlass.current
    val l = lang()
    val state by vm.today.collectAsStateWithLifecycle()
    val core by vm.core.collectAsStateWithLifecycle()
    val fmt = rememberFormatter(state.zone)
    val today = state.date

    var text by rememberSaveable { mutableStateOf("") }
    var synthesized by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<ScheduleItem?>(null) }
    var pinned by remember { mutableStateOf(emptyMap<Pinned, ScheduleItem>()) }
    var parseFailed by remember { mutableStateOf(false) }

    LaunchedEffect(text, l, today) {
        if (text == synthesized) return@LaunchedEffect
        if (text.isBlank()) {
            draft = null
            parseFailed = false
            return@LaunchedEffect
        }
        delay(250)
        try {
            val p = parseSentence(text, today.year, today.monthValue.toUByte(), today.dayOfMonth.toUByte(), l)
            var item = ScheduleItem(parseObject(p.itemJson) ?: return@LaunchedEffect)
            pinned[Pinned.KIND]?.let { item = item.withKind(it.kind) }
            pinned[Pinned.RECURRENCE]?.let { item = item.withRecurrence(it.recurrence) }
            pinned[Pinned.REMIND]?.let { item = item.withRemindBefore(it.remindBeforeMinutes) }
            draft = item
            parseFailed = false
        } catch (e: FasilaException) {
            draft = null
            parseFailed = true
        }
    }

    fun edit(next: ScheduleItem, pin: Pinned? = null) {
        if (pin != null) pinned = pinned + (pin to next)
        draft = next
        val t = synthesize(next, l)
        synthesized = t
        text = t
    }

    // Live preview: today's resolved time (whatever the recurrence) and prayer conflicts.
    val preview: OccurrenceInfo? = remember(draft, core, today) {
        draft?.let { d ->
            vm.repo.occurrences(listOf(d.withId("preview").withRecurrence(ScheduleItem.daily())), today, 1).firstOrNull()
        }
    }
    val suggestion: SlotInfo? = remember(preview, core) {
        val pv = preview ?: return@remember null
        val conflict = pv.conflicts.firstOrNull() ?: return@remember null
        val slots = vm.repo.freeSlots(today, draft?.durationMinutes ?: 15)
        slots.filter { it.start >= conflict.windowEnd }.minByOrNull { it.start } ?: slots.minByOrNull { abs(it.start - pv.start) }
    }

    GlassCard {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.composer_hint), color = g.secondary) },
            singleLine = true,
            shape = ChipShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = g.content,
                unfocusedTextColor = g.content,
                focusedBorderColor = g.accent,
                unfocusedBorderColor = g.border,
                cursorColor = g.accent,
            ),
        )

        val d = draft
        if (d == null) {
            if (parseFailed) {
                Text(
                    stringResource(R.string.composer_no_prayer), color = g.secondary, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            return@GlassCard
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dir = d.startOffset.let { if (it < 0) -1 else if (it > 0) 1 else 0 }
            val amount = abs(d.startOffset)
            if (dir != 0) {
                ChipMenu(
                    label = minutesLabel(amount, fmt),
                    options = AMOUNTS.map { minutesLabel(it, fmt) to it },
                    a11y = stringResource(R.string.chip_amount),
                ) { edit(d.withStart(d.startKey, dir * it)) }
            }
            ChipMenu(
                label = stringResource(DIRECTIONS.first { it.second == dir }.first),
                options = DIRECTIONS.map { stringResource(it.first) to it.second },
                a11y = stringResource(R.string.chip_direction),
            ) { newDir -> edit(d.withStart(d.startKey, newDir * (if (amount == 0) 15 else amount))) }
            ChipMenu(
                label = prayerName(d.startKey),
                options = Keys.ANCHORS.map { prayerName(it) to it },
                a11y = stringResource(R.string.chip_anchor),
            ) { edit(d.withStart(it, d.startOffset)) }
            Dot()
            ChipMenu(
                label = d.durationMinutes?.let { durationLabel(it, fmt) } ?: stringResource(R.string.no_duration),
                options = listOf(stringResource(R.string.no_duration) to 0) + DURATIONS.map { durationLabel(it, fmt) to it },
                a11y = stringResource(R.string.chip_duration),
            ) { edit(d.withDuration(it)) }
            Dot()
            ChipMenu(
                label = recurrenceLabel(d, fmt),
                options = recurrenceOptions(today),
                a11y = stringResource(R.string.chip_recurrence),
            ) { edit(d.withRecurrence(it), Pinned.RECURRENCE) }
            Dot()
            ChipMenu(
                label = stringResource(kindRes(d.kind)),
                options = ScheduleItem.KINDS.map { stringResource(kindRes(it)) to it },
                a11y = stringResource(R.string.chip_kind),
            ) { edit(d.withKind(it), Pinned.KIND) }
            ChipMenu(
                label = d.remindBeforeMinutes?.takeIf { it > 0 }?.let { stringResource(R.string.remind_before_fmt, minutesLabel(it, fmt)) }
                    ?: stringResource(R.string.no_reminder),
                options = listOf(stringResource(R.string.no_reminder) to 0) +
                    REMINDERS.map { stringResource(R.string.remind_before_fmt, minutesLabel(it, fmt)) to it },
                a11y = stringResource(R.string.chip_reminder),
            ) { edit(d.withRemindBefore(it.takeIf { m -> m > 0 }), Pinned.REMIND) }
        }

        // Preview line + conflict warning.
        val p = preview
        if (p != null) {
            val range = if (p.end != null) "${fmt.time(p.start)} – ${fmt.time(p.end)}" else fmt.time(p.start)
            Text(
                stringResource(R.string.preview_today, range),
                color = g.secondary, fontSize = 14.sp, style = TabularStyle,
                modifier = Modifier.padding(top = 12.dp),
            )
            p.conflicts.firstOrNull()?.let { c ->
                Row(
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .background(g.warning.copy(alpha = 0.14f), ChipShape)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = g.warning, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.conflict_fmt, prayerName(c.prayer), fmt.time(c.windowStart), fmt.time(c.windowEnd)),
                        color = g.content, fontSize = 13.sp, modifier = Modifier.weight(1f),
                    )
                }
                suggestion?.let { s ->
                    TextButton(onClick = { edit(d.withStart(s.anchorKey, s.anchorOffsetMinutes)) }) {
                        Text(
                            stringResource(R.string.move_to_fmt, describeAnchor(s.anchorKey, s.anchorOffsetMinutes, l)),
                            color = g.accent, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { text = ""; draft = null; pinned = emptyMap() }) {
                Text(stringResource(R.string.clear), color = g.secondary)
            }
            Button(
                onClick = {
                    val title = d.title.ifBlank { describeAnchor(d.startKey, d.startOffset, l) }
                    vm.saveItem(d.withTitle(title).withId(""))
                    text = ""
                    draft = null
                    pinned = emptyMap()
                },
                colors = ButtonDefaults.buttonColors(containerColor = g.accent, contentColor = if (g.isLight) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF0B1026)),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

/**
 * Sentence for a draft: the core's own description of the anchor plus the duration clause,
 * using the parser's vocabulary for the language ("for" / "pendant" / "لمدة").
 */
private fun synthesize(item: ScheduleItem, lang: String): String {
    val anchor = describeAnchor(item.startKey, item.startOffset, lang)
    val duration = item.durationMinutes?.let { m ->
        when (lang) {
            "ar" -> "لمدة $m دقيقة"
            "fr" -> "pendant $m min"
            else -> "for $m min"
        }
    }
    return listOfNotNull(item.title.ifBlank { null }, anchor, duration).joinToString(" ")
}

private val AMOUNTS = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)
private val DURATIONS = listOf(15, 30, 45, 60, 90, 120, 180)
private val REMINDERS = listOf(5, 10, 15, 30, 60)
private val DIRECTIONS = listOf(R.string.dir_before to -1, R.string.dir_at to 0, R.string.dir_after to 1)

fun kindRes(kind: String) = when (kind) {
    "task" -> R.string.kind_task
    "event" -> R.string.kind_event
    else -> R.string.kind_alarm
}

@Composable
private fun minutesLabel(m: Int, fmt: TimeFormatter): String = stringResource(R.string.minutes_short, fmt.number(m))

@Composable
private fun durationLabel(m: Int, fmt: TimeFormatter): String = when {
    m % 60 == 0 -> stringResource(R.string.hours_short, fmt.number(m / 60))
    m > 60 -> stringResource(R.string.hours_minutes_short, fmt.number(m / 60), fmt.number(m % 60))
    else -> stringResource(R.string.minutes_short, fmt.number(m))
}

@Composable
private fun recurrenceOptions(today: LocalDate) = listOf(
    stringResource(R.string.rec_daily) to ScheduleItem.daily(),
    stringResource(R.string.rec_once) to ScheduleItem.once(today),
    stringResource(R.string.rec_fridays) to ScheduleItem.weekly(FRIDAY_MASK),
    stringResource(R.string.rec_weekdays_mon_thu) to ScheduleItem.weekly((1 shl 1) or (1 shl 4)),
    stringResource(R.string.rec_ayyam_al_bid) to ScheduleItem.hijriMonthly(listOf(13, 14, 15)),
    stringResource(R.string.rec_ramadan) to ScheduleItem.hijriMonth(9),
)

@Composable
private fun Dot() {
    Text("·", color = LocalGlass.current.secondary)
}

@Composable
fun <T> ChipMenu(label: String, options: List<Pair<String, T>>, a11y: String, onSelect: (T) -> Unit) {
    val g = LocalGlass.current
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { open = true },
            modifier = Modifier
                .heightIn(min = 40.dp)
                .border(1.dp, g.border, ChipShape)
                .background(g.fill, ChipShape)
                .semantics {
                    role = Role.DropdownList
                    contentDescription = "$a11y: $label"
                },
            shape = ChipShape,
        ) {
            Text(label, color = g.content, fontSize = 14.sp)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = g.secondary, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, value) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelect(value) })
            }
        }
    }
}
