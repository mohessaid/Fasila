package app.fasila.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.fasila.R
import app.fasila.core.Keys
import app.fasila.core.TimeFormatter
import app.fasila.core.TodayState
import app.fasila.core.time
import app.fasila.ui.prayerName
import app.fasila.ui.theme.CountdownStyle
import app.fasila.ui.theme.LocalGlass
import kotlin.math.cos
import kotlin.math.sin

/**
 * The day dial (docs/DESIGN.md): a 270° arc from Fajr to Isha, prayers as dots, the sun at
 * "now", and the arc between the current and next prayer highlighted — that segment is the
 * fasila. Runs right-to-left in RTL locales.
 */
@Composable
fun DayDial(state: TodayState, fmt: TimeFormatter, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val next = state.next
    val nextName = next?.let { prayerName(it.key, it.friday) } ?: ""
    val countdown = fmt.countdown(state.countdownSeconds)
    val clock = fmt.time(next?.time)
    val description = stringResource(R.string.a11y_dial, nextName, countdown, clock)
    val day = state.today

    Box(modifier.clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { if (rtl) scaleX = -1f },
        ) {
            val stroke = 10.dp.toPx()
            val r = size.minDimension / 2f - stroke * 2f
            val topLeft = Offset(center.x - r, center.y - r)
            val arcSize = Size(r * 2f, r * 2f)
            val track = Stroke(stroke, cap = StrokeCap.Round)
            drawArc(g.secondary.copy(alpha = 0.22f), 135f, 270f, false, topLeft, arcSize, style = track)

            val fajr = day.fajr
            val isha = day.isha
            if (fajr == null || isha == null || isha <= fajr) return@Canvas
            fun frac(t: Long) = ((t - fajr).toFloat() / (isha - fajr).toFloat()).coerceIn(0f, 1f)
            fun angle(f: Float) = 135f + 270f * f
            fun point(f: Float): Offset {
                val rad = Math.toRadians(angle(f).toDouble())
                return Offset(center.x + r * cos(rad).toFloat(), center.y + r * sin(rad).toFloat())
            }

            // The fasila: from the current prayer to the next one (only within Fajr…Isha).
            val cur = state.current
            if (cur != null && next != null && cur.time >= fajr && next.time <= isha) {
                val a = frac(cur.time)
                val b = frac(next.time)
                drawArc(g.accent.copy(alpha = 0.35f), angle(a), 270f * (b - a), false, topLeft, arcSize, style = track)
                val n = frac(state.now)
                drawArc(g.accent, angle(a), 270f * (n - a).coerceAtLeast(0f), false, topLeft, arcSize, style = track)
            }

            for (k in Keys.LISTED) {
                val t = day.time(k) ?: continue
                val isPrayer = k != Keys.SUNRISE
                drawCircle(
                    color = if (isPrayer) g.content else g.secondary,
                    radius = (if (isPrayer) 5.dp else 3.5.dp).toPx(),
                    center = point(frac(t)),
                )
            }

            val now = state.now
            if (now in fajr..isha) {
                val sun = point(frac(now))
                drawCircle(g.accent.copy(alpha = 0.25f), radius = 18.dp.toPx(), center = sun)
                drawCircle(g.accent, radius = 9.dp.toPx(), center = sun)
            } else {
                // Night: a hollow moon waits at the end of the arc it will rise from / has set at.
                val moon = point(if (now > isha) 1f else 0f)
                drawCircle(g.accent, radius = 8.dp.toPx(), center = moon, style = Stroke(2.dp.toPx()))
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.next_prayer), color = g.secondary, style = MaterialTheme.typography.labelLarge)
            Text(nextName, color = g.content, style = MaterialTheme.typography.headlineSmall)
            Text(countdown, color = g.content, style = CountdownStyle, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
            Text(clock, color = g.secondary, style = MaterialTheme.typography.titleMedium)
        }
    }
}
