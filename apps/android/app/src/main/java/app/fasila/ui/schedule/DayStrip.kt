package app.fasila.ui.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fasila.R
import app.fasila.core.TimeFormatter
import app.fasila.core.TodayState
import app.fasila.core.isFriday
import app.fasila.ui.prayerName
import app.fasila.ui.theme.Accent
import app.fasila.ui.theme.Ink
import app.fasila.ui.theme.LocalGlass
import uniffi.fasila_ffi.ConflictInfo
import uniffi.fasila_ffi.OccurrenceInfo

private val Warn = Color(0xFFFFB26B)

/**
 * One day from just before Fajr to midnight (docs/DESIGN.md, Day strip): a frosted
 * channel, the time kept free around each prayer as brighter frosted segments, today's
 * items as lit capsules, the draft being composed as a lit pane, the free gaps (the
 * fasila) labelled below, and a soft beam of light for "now". Scrolls sideways on phones.
 */
@Composable
fun DayStrip(
    state: TodayState,
    windows: List<ConflictInfo>,
    items: List<OccurrenceInfo>,
    draft: OccurrenceInfo?,
    fmt: TimeFormatter,
    modifier: Modifier = Modifier,
) {
    val g = LocalGlass.current
    val day = state.today
    val start = (day.fajr ?: day.sunrise ?: return) - 20 * 60
    val end = day.midnight ?: ((day.isha ?: start) + 3 * 3600)
    if (end <= start) return
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val measurer = rememberTextMeasurer()
    val base = LocalTextStyle.current
    val nameStyle = base.merge(TextStyle(color = g.content, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
    val timeStyle = base.merge(TextStyle(color = g.secondary, fontSize = 11.sp, fontFeatureSettings = "tnum"))
    val gapStyle = base.merge(TextStyle(color = g.secondary, fontSize = 11.sp, fontFeatureSettings = "tnum"))
    val names = windows.associate { it.prayer to prayerName(it.prayer, day.isFriday) }
    val minutes = stringResource(R.string.unit_min_short)
    val hours = stringResource(R.string.unit_h_short)
    val description = stringResource(R.string.free_time_today)

    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val stripWidth = 760.dp
    // Open scrolled to "now".
    LaunchedEffect(state.date) {
        val f = ((state.now - start).toFloat() / (end - start)).coerceIn(0f, 1f)
        // Scroll offset counts from the start edge (the right edge in RTL), and the strip is
        // drawn mirrored in RTL, so "now" is always f of the way from the start.
        val px = with(density) { stripWidth.toPx() } * f
        scroll.scrollTo((px - 160 * density.density).toInt().coerceAtLeast(0))
    }

    Box(modifier.fillMaxWidth().horizontalScroll(scroll).semantics { contentDescription = description }) {
        Canvas(Modifier.width(stripWidth).height(118.dp)) {
            val pad = 24.dp.toPx()
            val w = size.width - 2 * pad
            fun x(t: Long): Float {
                val f = ((t - start).toFloat() / (end - start)).coerceIn(0f, 1f)
                return pad + (if (rtl) 1 - f else f) * w
            }
            fun span(a: Long, b: Long): Pair<Float, Float> = if (rtl) x(b) to x(a) else x(a) to x(b)
            val top = 48.dp.toPx()
            val h = 36.dp.toPx()

            // Channel.
            drawRoundRect(Color.White.copy(alpha = 0.05f), Offset(0f, top), Size(size.width, h), CornerRadius(14.dp.toPx()))
            drawRoundRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent), startY = top, endY = top + h),
                Offset(0f, top), Size(size.width, h), CornerRadius(14.dp.toPx()), style = Stroke(1.dp.toPx()),
            )
            // Protected windows, prayer names and times.
            windows.forEach { wdw ->
                val (a, b) = span(wdw.windowStart, wdw.windowEnd)
                val inset = 4.dp.toPx()
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.1f)), startY = top, endY = top + h),
                    Offset(a, top + inset), Size((b - a).coerceAtLeast(6f), h - 2 * inset), CornerRadius(10.dp.toPx()),
                )
                val cx = x(wdw.prayerTime)
                label(measurer.measure(names[wdw.prayer] ?: "", nameStyle), cx, 2.dp.toPx())
                label(measurer.measure(fmt.time(wdw.prayerTime), timeStyle), cx, 19.dp.toPx())
            }
            // Free gaps between windows (longer than 45 min), labelled below the channel.
            windows.zipWithNext().forEach { (p, q) ->
                val gap = (q.windowStart - p.windowEnd) / 60
                if (gap > 45) {
                    val text = if (gap >= 60) "${fmt.number(gap / 60)} $hours ${fmt.number(gap % 60)} $minutes" else "${fmt.number(gap)} $minutes"
                    label(measurer.measure(text, gapStyle), (x(p.windowEnd) + x(q.windowStart)) / 2, top + h + 8.dp.toPx())
                }
            }
            // Today's items: lit accent capsules.
            items.forEach { o ->
                val (a, b) = span(o.start, o.end ?: (o.start + 5 * 60))
                capsule(a, b, top + h / 2 - 7.dp.toPx(), 14.dp.toPx(), if (o.conflicts.isNotEmpty()) Warn else Accent)
            }
            // The draft: a lit pane capsule.
            draft?.let { d ->
                val (a, b) = span(d.start, d.end ?: (d.start + 5 * 60))
                val y = top + 6.dp.toPx()
                val hh = h - 12.dp.toPx()
                val wd = (b - a).coerceAtLeast(10.dp.toPx())
                drawRoundRect(Color.White.copy(alpha = 0.24f), Offset(a, y), Size(wd, hh), CornerRadius(10.dp.toPx()))
                drawRoundRect(
                    Brush.radialGradient(listOf((if (d.conflicts.isNotEmpty()) Warn else Accent).copy(alpha = 0.5f), Color.Transparent), center = Offset(a + wd / 2, y), radius = wd.coerceAtLeast(hh)),
                    Offset(a, y), Size(wd, hh), CornerRadius(10.dp.toPx()),
                )
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), startY = y, endY = y + hh),
                    Offset(a, y), Size(wd, hh), CornerRadius(10.dp.toPx()), style = Stroke(1.dp.toPx()),
                )
            }
            // Now: a soft beam of light.
            if (state.now in start..end) {
                val nx = x(state.now)
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = 0.95f), Color.Transparent), startY = top - 6.dp.toPx(), endY = top + h + 6.dp.toPx()),
                    Offset(nx - 1.dp.toPx(), top - 6.dp.toPx()), Size(2.dp.toPx(), h + 12.dp.toPx()),
                )
            }
        }
    }
}

private fun DrawScope.label(layout: androidx.compose.ui.text.TextLayoutResult, cx: Float, y: Float) {
    val x = (cx - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
    drawText(layout, topLeft = Offset(x, y))
}

private fun DrawScope.capsule(a: Float, b: Float, y: Float, h: Float, color: Color) {
    val w = (b - a).coerceAtLeast(8.dp.toPx())
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f).compositeOver(color), color), startY = y, endY = y + h),
        Offset(a, y), Size(w, h), CornerRadius(h / 2),
    )
}

private fun Color.compositeOver(bg: Color): Color = Color(
    red = red * alpha + bg.red * (1 - alpha),
    green = green * alpha + bg.green * (1 - alpha),
    blue = blue * alpha + bg.blue * (1 - alpha),
)
