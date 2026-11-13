package app.fasila.core

import uniffi.fasila_ffi.DayInfo
import java.time.LocalDate

/** Keys of `fasila_core::prayer::TimeKey`. */
object Keys {
    const val IMSAK = "imsak"
    const val FAJR = "fajr"
    const val SUNRISE = "sunrise"
    const val DHUHR = "dhuhr"
    const val ASR = "asr"
    const val SUNSET = "sunset"
    const val MAGHRIB = "maghrib"
    const val ISHA = "isha"
    const val MIDNIGHT = "midnight"
    const val LAST_THIRD = "last_third"

    val ALL = listOf(IMSAK, FAJR, SUNRISE, DHUHR, ASR, SUNSET, MAGHRIB, ISHA, MIDNIGHT, LAST_THIRD)
    val PRAYERS = listOf(FAJR, DHUHR, ASR, MAGHRIB, ISHA)
    /** Rows shown on the Today list. */
    val LISTED = listOf(FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA)
    /** Keys offered in the composer's anchor chip. */
    val ANCHORS = listOf(FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA, MIDNIGHT, LAST_THIRD)
}

fun DayInfo.time(key: String): Long? = when (key) {
    Keys.IMSAK -> imsak
    Keys.FAJR -> fajr
    Keys.SUNRISE -> sunrise
    Keys.DHUHR -> dhuhr
    Keys.ASR -> asr
    Keys.SUNSET -> sunset
    Keys.MAGHRIB -> maghrib
    Keys.ISHA -> isha
    Keys.MIDNIGHT -> midnight
    Keys.LAST_THIRD -> lastThird
    else -> null
}

/** Whether the core had to estimate this time (high-latitude rule or polar fallback). */
fun DayInfo.isEstimated(key: String): Boolean = when (key) {
    Keys.FAJR, Keys.IMSAK -> fajrEstimated || polarFallback
    Keys.ISHA -> ishaEstimated || polarFallback
    else -> polarFallback
}

val DayInfo.localDate: LocalDate get() = LocalDate.parse(date)
/** 0 = Sunday … 6 = Saturday. */
val DayInfo.isFriday: Boolean get() = weekday.toInt() == 5

data class PrayerMoment(val key: String, val time: Long, val estimated: Boolean, val friday: Boolean)

fun DayInfo.prayers(): List<PrayerMoment> =
    Keys.PRAYERS.mapNotNull { k -> time(k)?.let { PrayerMoment(k, it, isEstimated(k), isFriday) } }

/** Prayers of consecutive days, in time order. */
fun timeline(days: List<DayInfo>): List<PrayerMoment> = days.flatMap { it.prayers() }.sortedBy { it.time }

fun List<PrayerMoment>.currentAt(now: Long): PrayerMoment? = lastOrNull { it.time <= now }
fun List<PrayerMoment>.nextAfter(now: Long): PrayerMoment? = firstOrNull { it.time > now }
