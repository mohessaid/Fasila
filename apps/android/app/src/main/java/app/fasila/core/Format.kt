package app.fasila.core

import android.content.Context
import android.text.format.DateFormat
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Locale

/** Language tag understood by the Rust core (`en`, `ar`, `fr`). */
fun coreLang(locale: Locale): String = when (locale.language) {
    "ar" -> "ar"
    "fr" -> "fr"
    else -> "en"
}

fun Context.currentLocale(): Locale = resources.configuration.locales[0] ?: Locale.getDefault()

/**
 * Formats instants in the *configured location's* time zone, with the locale's digits
 * (Arabic-Indic in locales whose default numbering system uses them) and the user's
 * 12/24-hour preference.
 */
class TimeFormatter(val locale: Locale, val zone: ZoneId, is24Hour: Boolean) {
    private val digits = DecimalStyle.of(locale)
    private val clock: DateTimeFormatter = DateTimeFormatter
        .ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm"), locale)
        .withDecimalStyle(digits)
        .withZone(zone)
    private val longDate: DateTimeFormatter = DateTimeFormatter
        .ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
        .withDecimalStyle(digits)
    private val monthYear: DateTimeFormatter = DateTimeFormatter
        .ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMMyyyy"), locale)
        .withDecimalStyle(digits)
    private val mediumDate: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withDecimalStyle(digits)
    private val integers: NumberFormat = NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false }

    fun time(epochSeconds: Long?): String = epochSeconds?.let { clock.format(Instant.ofEpochSecond(it)) } ?: "—"
    fun longDate(date: LocalDate): String = longDate.format(date)
    fun mediumDate(date: LocalDate): String = mediumDate.format(date)
    fun monthYear(date: LocalDate): String = monthYear.format(date)
    fun number(n: Number): String = integers.format(n)

    /** `H:MM:SS` (or `MM:SS` under an hour) with tabular, locale-appropriate digits. */
    fun countdown(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(locale, "%d:%02d:%02d", h, m, sec)
        else String.format(locale, "%02d:%02d", m, sec)
    }

    companion object {
        fun from(context: Context, zone: ZoneId): TimeFormatter =
            TimeFormatter(context.currentLocale(), zone, DateFormat.is24HourFormat(context))
    }
}
