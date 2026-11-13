package app.fasila.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.fasila.core.Period

/** Accessibility signals that change rendering. */
data class A11yPrefs(val reduceMotion: Boolean, val reduceTransparency: Boolean)

val LocalA11y = staticCompositionLocalOf { A11yPrefs(reduceMotion = false, reduceTransparency = false) }

fun systemReduceMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Android has no "Reduce Transparency" switch; high-contrast text is the closest system signal. */
fun systemHighContrastText(context: Context): Boolean =
    runCatching { Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1 }.getOrDefault(false)

/** Countdown: tabular numerals, light weight, 56–72 sp (docs/DESIGN.md). */
val CountdownStyle = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Light, fontFeatureSettings = "tnum", letterSpacing = 0.sp)
val TabularStyle = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun FasilaTheme(period: Period, solidPanes: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val a11y = remember(solidPanes) {
        A11yPrefs(
            reduceMotion = systemReduceMotion(context),
            reduceTransparency = solidPanes || systemHighContrastText(context),
        )
    }
    val accent = Color(period.accent)
    val scheme = if (period.isLight) {
        lightColorScheme(
            primary = Color(0xFF1B2350),
            onPrimary = Color.White,
            secondary = Color(period.top),
            tertiary = accent,
            surface = Color(0xFFF4F7FB),
            background = Color.Transparent,
        )
    } else {
        darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF0B1026),
            secondary = Color(period.bottom),
            tertiary = accent,
            surface = Color(0xFF151B3A),
            background = Color.Transparent,
        )
    }
    CompositionLocalProvider(LocalA11y provides a11y) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
