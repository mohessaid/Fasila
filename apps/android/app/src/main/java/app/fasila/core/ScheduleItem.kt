package app.fasila.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate

/** View over one schedule item document (`fasila_core::schedule::Item`). */
data class ScheduleItem(val json: JsonObject) {
    val id: String get() = json.str("id")
    val title: String get() = json.str("title")
    /** `alarm`, `task` or `event`. */
    val kind: String get() = json.str("kind", "alarm")
    val startKey: String get() = json.obj("start").str("key", "fajr")
    val startOffset: Int get() = json.obj("start").int("offset_minutes")
    val enabled: Boolean get() = json.bool("enabled", true)
    val remindBeforeMinutes: Int? get() = json.intOrNull("remind_before_minutes")

    private val end get() = json.obj("end")
    /** `none`, `duration` or `until`. */
    val endType: String get() = end.str("type", "none")
    val durationMinutes: Int? get() = if (endType == "duration") end.int("minutes") else null
    val untilKey: String? get() = if (endType == "until") end.obj("anchor").str("key") else null
    val untilOffset: Int get() = end.obj("anchor").int("offset_minutes")

    val recurrence: JsonObject get() = json.obj("recurrence")
    /** `once`, `daily`, `weekly`, `hijri_monthly`, `hijri_yearly`, `hijri_month`. */
    val recurrenceType: String get() = recurrence.str("type", "daily")
    /** Bit 0 = Sunday … bit 6 = Saturday. */
    val weeklyMask: Int get() = recurrence.int("days")

    fun withId(id: String) = ScheduleItem(json.with("id", id))
    fun withTitle(title: String) = ScheduleItem(json.with("title", title))
    fun withKind(kind: String) = ScheduleItem(json.with("kind", kind))
    fun withEnabled(enabled: Boolean) = ScheduleItem(json.with("enabled", enabled))
    fun withRemindBefore(minutes: Int?) = ScheduleItem(json.with("remind_before_minutes", minutes))
    fun withStart(key: String, offset: Int) =
        ScheduleItem(json.with("start", jsonObjectOf("key" to key, "offset_minutes" to offset)))

    fun withDuration(minutes: Int?) = ScheduleItem(
        json.with(
            "end",
            if (minutes == null || minutes <= 0) jsonObjectOf("type" to "none")
            else jsonObjectOf("type" to "duration", "minutes" to minutes),
        ),
    )

    fun withRecurrence(r: JsonObject) = ScheduleItem(json.with("recurrence", r))

    companion object {
        val KINDS = listOf("alarm", "task", "event")

        fun daily() = jsonObjectOf("type" to "daily")
        fun weekly(mask: Int) = jsonObjectOf("type" to "weekly", "days" to mask)
        /** `Date` serialises as an ISO `YYYY-MM-DD` string. */
        fun once(date: LocalDate) = jsonObjectOf("type" to "once", "date" to date.toString())
        fun hijriMonthly(days: List<Int>) =
            jsonObjectOf("type" to "hijri_monthly", "days" to JsonArray(days.map { JsonPrimitive(it) }))
        fun hijriMonth(month: Int) = jsonObjectOf("type" to "hijri_month", "month" to month)
    }
}

fun List<ScheduleItem>.toJson(): String = JsonArray(map { it.json }).toString()
