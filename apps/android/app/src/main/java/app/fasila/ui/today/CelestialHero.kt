package app.fasila.ui.today

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fasila.R
import app.fasila.core.Keys
import app.fasila.core.TimeFormatter
import app.fasila.core.TodayState
import app.fasila.core.isFriday
import app.fasila.core.time
import app.fasila.ui.prayerName
import app.fasila.ui.theme.Accent
import app.fasila.ui.theme.ChipShape
import app.fasila.ui.theme.CountdownStyle
import app.fasila.ui.theme.Ink
import app.fasila.ui.theme.LocalA11y
import app.fasila.ui.theme.LocalGlass
import app.fasila.ui.theme.TabularStyle

private val Night = Color(0xFF060A14)
private val Muted = Color(0xFFCBD5E1)

/**
 * Today's hero (docs/DESIGN.md, Celestial scene): two mountain layers, the sun on its
 * path from sunrise to Maghrib with Dhuhr and Asr marked, and at night the moon in its
 * real, computed phase. Below it: the next prayer, the countdown and its time.
 */
@Composable
fun CelestialHero(state: TodayState, fmt: TimeFormatter, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    val sky = state.sky
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val reduceMotion = LocalA11y.current.reduceMotion
    val day = state.today
    val next = state.next
    val nextName = next?.let { prayerName(it.key, it.friday) } ?: ""
    val countdown = fmt.countdown(state.countdownSeconds)
    val clock = fmt.time(next?.time)
    val description = stringResource(R.string.a11y_dial, nextName, countdown, clock)

    val sunPos by animateFloatAsState(sky.sun, tween(if (reduceMotion) 0 else 1200), label = "sun")
    val day01 by animateFloatAsState(if (sky.isDaytime) 1f else 0f, tween(if (reduceMotion) 0 else 1200), label = "day")
    val pulse = if (reduceMotion) 0.6f else {
        val t = rememberInfiniteTransition(label = "glow")
        t.animateFloat(0.4f, 0.8f, infiniteRepeatable(tween(2500), RepeatMode.Reverse), label = "pulse").value
    }
    val measurer = rememberTextMeasurer()
    val labelStyle = LocalTextStyle.current.merge(TextStyle(color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Medium))
    val markers = listOfNotNull(
        day.time(Keys.DHUHR)?.let { Keys.DHUHR to it },
        day.time(Keys.ASR)?.let { Keys.ASR to it },
    )
    val sunrise = day.time(Keys.SUNRISE)
    val maghrib = day.time(Keys.MAGHRIB)
    val markerNames = markers.associate { (k, _) -> k to prayerName(k, day.isFriday) }

    // After Maghrib the Hijri day has begun.
    val hijriDay = when {
        maghrib != null && state.now >= maghrib -> state.tomorrow.hijriDay.toInt()
        else -> day.hijriDay.toInt()
    }
    val whiteDays = hijriDay in 13..15

    Column(modifier.clearAndSetSemantics { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(600f / 268f)) {
            val s = size.width / 600f
            fun arc(p: Float): Offset {
                val a = Math.PI - p * Math.PI
                val x = (24 + 276 + Math.cos(a) * 276).toFloat()
                val y = (204 - Math.sin(a) * 144).toFloat()
                return Offset((if (rtl) 600 - x else x) * s, y * s)
            }
            // Sun's path, dotted.
            val path = Path().apply {
                (0..48).forEach { i -> val p = arc(i / 48f); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            }
            drawPath(path, Color.White.copy(alpha = if (sky.isDaytime) 0.24f else 0.1f),
                style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx()))))
            if (sunrise != null && maghrib != null && maghrib > sunrise) {
                listOf(0f, 1f).forEach { drawCircle(Color.White.copy(alpha = 0.5f), 2.5f * s, arc(it)) }
                markers.forEach { (k, t) ->
                    val p = arc(((t - sunrise).toFloat() / (maghrib - sunrise)).coerceIn(0f, 1f))
                    val isNext = next?.key == k && next.time == t
                    drawCircle(if (isNext) Accent else Color.White.copy(alpha = 0.6f), 3.5f * s, p)
                    val layout = measurer.measure(markerNames[k] ?: "", labelStyle)
                    drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - 14 * s - layout.size.height))
                }
            }
            mountains(s, sky.isDaytime)
            // Sun.
            if (day01 > 0.01f) {
                val c = arc(sunPos)
                val r = 92 * s
                drawCircle(
                    Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.3f * pulse * day01),
                        0.32f to Accent.copy(alpha = 0.18f * pulse * day01),
                        1f to Color.Transparent, center = c, radius = 0.49f * r * (1 + 0.15f * pulse),
                    ),
                    0.49f * r * (1 + 0.15f * pulse), c,
                )
                drawCircle(
                    Brush.radialGradient(
                        0f to Ink.copy(alpha = day01), 0.44f to Ink.copy(alpha = 0.96f * day01), 0.78f to Accent.copy(alpha = 0.96f * day01),
                        1f to Accent.copy(alpha = day01),
                        center = Offset(c.x - 0.08f * r, c.y - 0.1f * r), radius = 0.36f * r,
                    ),
                    0.235f * r, c,
                )
            }
            // Moon, in its real computed phase.
            if (day01 < 0.99f) moon(Offset(300 * s, 118 * s), 92 * s, sky.moonPhase, 1f - day01)
        }

        Text(
            stringResource(R.string.is_next, nextName),
            style = LocalTextStyle.current.merge(TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, shadow = SoftShadow)),
            color = g.content,
        )
        Box(
            Modifier.drawBehind {
                // A soft veil of darker sky behind the countdown keeps it legible on bright skies.
                drawOval(
                    Brush.radialGradient(
                        listOf(Night.copy(alpha = sky.glassTint * 0.5f), Night.copy(alpha = sky.glassTint * 0.2f), Color.Transparent),
                    ),
                    topLeft = Offset(-size.width * 0.35f, -size.height * 0.3f),
                    size = androidx.compose.ui.geometry.Size(size.width * 1.7f, size.height * 1.6f),
                )
            },
        ) {
            Text(countdown, style = CountdownStyle.copy(shadow = SoftShadow), color = g.content)
        }
        Text(
            stringResource(R.string.at_time, clock),
            style = TabularStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
            color = g.content,
            modifier = Modifier
                .padding(top = 4.dp)
                .background(Night.copy(alpha = 0.18f + sky.glassTint * 0.5f), ChipShape)
                .padding(horizontal = 14.dp, vertical = 4.dp),
        )
        if (!sky.isDaytime) {
            val pct = fmt.number(Math.round(sky.moonIllumination * 100))
            Text(
                if (whiteDays) stringResource(R.string.white_days_title)
                else stringResource(R.string.lunar_day_line, fmt.number(hijriDay), pct),
                color = Accent,
                style = LocalTextStyle.current.merge(TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)),
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

private val SoftShadow = Shadow(Color(0x73060A14), Offset(0f, 1f), 18f)

private fun DrawScope.mountains(s: Float, day: Boolean) {
    val w = 600f
    fun layer(points: List<Pair<Float, Float>>, top: Float) {
        val p = Path().apply {
            moveTo(0f, 268 * s)
            points.forEach { (x, y) -> lineTo(x * w * s, y * s) }
            lineTo(w * s, 268 * s)
            close()
        }
        drawPath(p, Brush.verticalGradient(
            0f to Ink.copy(alpha = top), 0.68f to Ink.copy(alpha = top * 0.32f), 0.8f to Color.Transparent,
            startY = 190 * s, endY = 268 * s,
        ))
    }
    layer(listOf(0f to 238f, 0.1f to 214f, 0.18f to 228f, 0.3f to 192f, 0.43f to 226f, 0.55f to 204f, 0.68f to 231f, 0.8f to 200f, 1f to 226f), if (day) 0.12f else 0.2f)
    layer(listOf(0f to 252f, 0.11f to 236f, 0.21f to 246f, 0.33f to 218f, 0.45f to 247f, 0.56f to 224f, 0.68f to 248f, 0.8f to 214f, 1f to 239f), if (day) 0.18f else 0.26f)
}

/** Moon disc with the lit region for `phase` (0 new, 0.5 full), craters and glow. */
private fun DrawScope.moon(c: Offset, body: Float, phase: Float, alpha: Float) {
    val r = 0.28f * body
    drawCircle(
        Brush.radialGradient(0f to Ink.copy(alpha = 0.32f * alpha), 0.45f to Muted.copy(alpha = 0.12f * alpha), 1f to Color.Transparent, center = c, radius = 0.4f * body),
        0.4f * body, c,
    )
    drawCircle(Night.copy(alpha = 0.72f * alpha), r, c)
    if (phase > 0.005f && phase < 0.995f) {
        val lit = Path()
        val top = Offset(c.x, c.y - r)
        lit.moveTo(top.x, top.y)
        // Outer limb: right half while waxing, left half while waning.
        val waxing = phase < 0.5f
        val outer = Rect(c.x - r, c.y - r, c.x + r, c.y + r)
        lit.arcTo(outer, -90f, if (waxing) 180f else -180f, false)
        // Terminator: an ellipse whose half-width depends on the phase.
        val e = if (waxing) phase else phase - 0.5f
        val rx = if (e < 0.25f) r * (1 - e / 0.25f) else r * ((e - 0.25f) / 0.25f)
        val bulgesOut = e >= 0.25f
        val term = Rect(c.x - rx, c.y - r, c.x + rx, c.y + r)
        val sweep = if (waxing) { if (bulgesOut) 180f else -180f } else { if (bulgesOut) -180f else 180f }
        lit.arcTo(term, 90f, sweep, false)
        lit.close()
        drawPath(lit, Brush.linearGradient(listOf(Ink, Color(0xFFE2E8F0), Muted), start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r)), alpha = alpha)
    }
    listOf(Triple(-0.36f, -0.22f, 0.11f), Triple(0.2f, 0.28f, 0.08f), Triple(0.38f, -0.15f, 0.055f)).forEach { (dx, dy, rr) ->
        drawCircle(Muted.copy(alpha = 0.16f * alpha), rr * r, Offset(c.x + dx * r, c.y + dy * r))
    }
}
