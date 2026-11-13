package app.fasila.ui.schedule

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.fasila.R
import app.fasila.core.ScheduleItem
import app.fasila.core.TimeFormatter
import app.fasila.core.asArray
import app.fasila.core.int
import app.fasila.core.obj
import app.fasila.core.str
import app.fasila.ui.currentLocale
import app.fasila.ui.hijriMonth
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

/** Sunday = bit 0 … Saturday = bit 6 (core convention) → java.time.DayOfWeek. */
fun weekdayOfBit(bit: Int): DayOfWeek = if (bit == 0) DayOfWeek.SUNDAY else DayOfWeek.of(bit)

fun bitOf(day: DayOfWeek): Int = day.value % 7

const val FRIDAY_MASK = 1 shl 5

/** `Date` is an ISO string; older documents used `{year, month, day}`. */
fun parseCoreDate(r: JsonObject): LocalDate? {
    (r["date"] as? JsonPrimitive)?.takeIf { it.isString }?.let { return runCatching { LocalDate.parse(it.content) }.getOrNull() }
    val o = r.obj("date")
    return runCatching { LocalDate.of(o.int("year"), o.int("month"), o.int("day")) }.getOrNull()
}

@Composable
fun recurrenceLabel(item: ScheduleItem, fmt: TimeFormatter): String {
    val r = item.recurrence
    val locale = currentLocale()
    return when (r.str("type")) {
        "once" -> parseCoreDate(r)?.let { stringResource(R.string.rec_once_on, fmt.mediumDate(it)) } ?: stringResource(R.string.rec_once)
        "weekly" -> {
            val mask = r.int("days")
            when (mask) {
                FRIDAY_MASK -> stringResource(R.string.rec_fridays)
                0x7F -> stringResource(R.string.rec_daily)
                else -> stringResource(
                    R.string.rec_weekly,
                    (0..6).filter { mask and (1 shl it) != 0 }
                        .joinToString(", ") { weekdayOfBit(it).getDisplayName(TextStyle.SHORT, locale) },
                )
            }
        }
        "hijri_monthly" -> {
            val days = r["days"].asArray().mapNotNull { (it as? JsonPrimitive)?.intOrNull }
            if (days == listOf(13, 14, 15)) stringResource(R.string.rec_ayyam_al_bid)
            else stringResource(R.string.rec_hijri_monthly, days.joinToString(", ") { fmt.number(it) })
        }
        "hijri_yearly" -> stringResource(R.string.rec_hijri_yearly, fmt.number(r.int("day")), hijriMonth(r.int("month")))
        "hijri_month" -> stringResource(R.string.rec_hijri_month, hijriMonth(r.int("month")))
        else -> stringResource(R.string.rec_daily)
    }
}
