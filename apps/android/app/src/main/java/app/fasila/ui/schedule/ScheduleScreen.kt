package app.fasila.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fasila.R
import app.fasila.core.ScheduleItem
import app.fasila.core.TimeFormatter
import app.fasila.ui.MainViewModel
import app.fasila.ui.SectionTitle
import app.fasila.ui.lang
import app.fasila.ui.rememberFormatter
import app.fasila.ui.theme.GlassCard
import app.fasila.ui.theme.LocalGlass
import app.fasila.ui.theme.RowShape
import uniffi.fasila_ffi.OccurrenceInfo
import uniffi.fasila_ffi.describeAnchor
import java.time.LocalDate

@Composable
fun ScheduleScreen(vm: MainViewModel, padding: PaddingValues) {
    val items by vm.items.collectAsStateWithLifecycle()
    val state by vm.today.collectAsStateWithLifecycle()
    val core by vm.core.collectAsStateWithLifecycle()
    val fmt = rememberFormatter(state.zone)
    val g = LocalGlass.current
    // Next occurrence of each item within a week (anchors move with the prayers each day).
    val upcoming: Map<String, OccurrenceInfo> = remember(items, core, state.date, state.now / 60) {
        vm.repo.occurrences(items.filter { it.enabled }, state.date, 8)
            .filter { (it.end ?: it.start) >= state.now }
            .groupBy { it.itemId }
            .mapValues { (_, v) -> v.minBy { it.start } }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.tab_schedule), color = g.content, fontSize = 28.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp).semantics { heading() },
            )
        }
        item { Composer(vm) }
        if (items.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.schedule_empty), color = g.secondary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                )
            }
        } else {
            item { SectionTitle(stringResource(R.string.your_schedule)) }
            items(items, key = { it.id }) { item ->
                ItemRow(item, upcoming[item.id], state.date, fmt,
                    onToggle = { vm.setItemEnabled(item.id, it) },
                    onDelete = { vm.deleteItem(item.id) })
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: ScheduleItem,
    next: OccurrenceInfo?,
    today: LocalDate,
    fmt: TimeFormatter,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val g = LocalGlass.current
    val l = lang()
    GlassCard(shape = RowShape, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.title, color = if (item.enabled) g.content else g.secondary,
                    fontSize = 17.sp, fontWeight = FontWeight.Medium,
                )
                Text(
                    "${stringResource(kindRes(item.kind))} · ${describeAnchor(item.startKey, item.startOffset, l)} · ${recurrenceLabel(item, fmt)}",
                    color = g.secondary, fontSize = 13.sp,
                )
                if (next != null) {
                    val date = runCatching { LocalDate.parse(next.date) }.getOrNull()
                    val day = when (date) {
                        today -> stringResource(R.string.today)
                        today.plusDays(1) -> stringResource(R.string.tomorrow)
                        null -> ""
                        else -> fmt.mediumDate(date)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Text(stringResource(R.string.next_at, day, fmt.time(next.start)), color = g.content, fontSize = 13.sp)
                        if (next.conflicts.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.Warning, contentDescription = stringResource(R.string.conflict_short), tint = g.warning, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Switch(
                checked = item.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = g.accent),
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete), tint = g.secondary)
            }
        }
    }
}
