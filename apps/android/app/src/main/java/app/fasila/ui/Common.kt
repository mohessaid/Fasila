package app.fasila.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fasila.R
import app.fasila.core.Keys
import app.fasila.core.TimeFormatter
import app.fasila.core.coreLang
import app.fasila.ui.theme.LocalGlass
import java.time.ZoneId
import java.util.Locale
import uniffi.fasila_ffi.hijriMonthName
import uniffi.fasila_ffi.timeKeyName

@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

/** Language tag for Rust-side strings (prayer names, anchor descriptions). */
@Composable
fun lang(): String = coreLang(currentLocale())

@Composable
fun rememberFormatter(zone: ZoneId): TimeFormatter {
    val context = LocalContext.current
    val locale = currentLocale()
    val is24 = DateFormat.is24HourFormat(context)
    return remember(locale, zone, is24) { TimeFormatter(locale, zone, is24) }
}

/** Prayer/time-key display name from the core; Dhuhr becomes Jumuʿah on Fridays. */
@Composable
fun prayerName(key: String, friday: Boolean = false): String {
    if (friday && key == Keys.DHUHR) return stringResource(R.string.jumuah)
    val l = lang()
    return remember(key, l) { timeKeyName(key, l) }
}

@Composable
fun hijriMonth(month: Int): String {
    // The core ships Arabic and English month names; French uses the transliteration.
    val l = lang()
    return remember(month, l) { hijriMonthName(month.toUByte(), if (l == "ar") "ar" else "en") }
}

fun occasionRes(id: String): Int? = when (id) {
    "islamic_new_year" -> R.string.occasion_islamic_new_year
    "ashura" -> R.string.occasion_ashura
    "mawlid_an_nabi" -> R.string.occasion_mawlid
    "isra_miraj" -> R.string.occasion_isra_miraj
    "mid_shaban" -> R.string.occasion_mid_shaban
    "ramadan_start" -> R.string.occasion_ramadan_start
    "laylat_al_qadr" -> R.string.occasion_laylat_al_qadr
    "eid_al_fitr" -> R.string.occasion_eid_al_fitr
    "day_of_arafah" -> R.string.occasion_arafah
    "eid_al_adha" -> R.string.occasion_eid_al_adha
    "ayyam_al_bid" -> R.string.occasion_ayyam_al_bid
    "dhul_hijjah_first_ten" -> R.string.occasion_dhul_hijjah_ten
    else -> null
}

@Composable
fun occasionName(id: String): String = occasionRes(id)?.let { stringResource(it) } ?: id.replace('_', ' ')

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp, top = 8.dp),
        color = LocalGlass.current.secondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}
