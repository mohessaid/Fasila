package app.fasila.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import app.fasila.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Accessibility signals that change rendering. */
data class A11yPrefs(val reduceMotion: Boolean, val reduceTransparency: Boolean)

val LocalA11y = staticCompositionLocalOf { A11yPrefs(reduceMotion = false, reduceTransparency = false) }

fun systemReduceMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Android has no "Reduce Transparency" switch; high-contrast text is the closest system signal. */
fun systemHighContrastText(context: Context): Boolean =
    runCatching { Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1 }.getOrDefault(false)

val Inter = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)
val Tajawal = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.SemiBold),
    Font(R.font.tajawal_bold, FontWeight.Bold),
)

/** Countdown: tabular numerals, light weight (docs/DESIGN.md). */
val CountdownStyle = TextStyle(fontFamily = Inter, fontSize = 58.sp, fontWeight = FontWeight.Light, fontFeatureSettings = "tnum", letterSpacing = (-1).sp)
val TabularStyle = TextStyle(fontFamily = Inter, fontFeatureSettings = "tnum")

@Composable
fun FasilaTheme(solidPanes: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val a11y = remember(solidPanes) {
        A11yPrefs(
            reduceMotion = systemReduceMotion(context),
            reduceTransparency = solidPanes || systemHighContrastText(context),
        )
    }
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    val family = if (arabic) Tajawal else Inter
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
    val scheme = darkColorScheme(
        primary = Accent,
        onPrimary = AccentInk,
        secondary = Color(0xFF1C2440),
        tertiary = Accent,
        surface = Color(0xFF0E1426),
        onSurface = Ink,
        surfaceContainer = Color(0xFF141B33),
        surfaceContainerHigh = Color(0xFF1A2240),
        background = Color.Transparent,
    )
    CompositionLocalProvider(LocalA11y provides a11y) {
        MaterialTheme(colorScheme = scheme, typography = typography) {
            ProvideTextStyle(TextStyle(fontFamily = family), content)
        }
    }
}
