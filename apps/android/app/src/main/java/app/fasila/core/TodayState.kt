package app.fasila.core

import uniffi.fasila_ffi.DayInfo
import java.time.LocalDate
import java.time.ZoneId

/** Everything the Today screen, the widget and the alarms need for one instant. */
data class TodayState(
    val now: Long,
    val zone: ZoneId,
    val date: LocalDate,
    val yesterday: DayInfo,
    val today: DayInfo,
    val tomorrow: DayInfo,
    val current: PrayerMoment?,
    val next: PrayerMoment?,
    val sky: Sky = Sky.Night,
) {
    val countdownSeconds: Long get() = next?.let { it.time - now } ?: 0
    val days: List<DayInfo> get() = listOf(yesterday, today, tomorrow)
}
