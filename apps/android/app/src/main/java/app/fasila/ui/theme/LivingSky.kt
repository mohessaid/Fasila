package app.fasila.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.fasila.core.Period
import app.fasila.core.SkyState
import kotlin.random.Random

/**
 * The living sky: a vertical gradient for the current prayer period, cross-fading across
 * boundaries, with a very slow "breathing" drift and faint stars at night.
 * Under Reduce Motion there is no drift and period changes are instant.
 */
@Composable
fun LivingSky(sky: SkyState, modifier: Modifier = Modifier) {
    val reduceMotion = LocalA11y.current.reduceMotion
    val spec = if (reduceMotion) snap<Color>() else tween<Color>(durationMillis = 1200, easing = LinearEasing)
    val top by animateColorAsState(lerp(Color(sky.from.top), Color(sky.to.top), sky.fraction), spec, label = "skyTop")
    val bottom by animateColorAsState(lerp(Color(sky.from.bottom), Color(sky.to.bottom), sky.fraction), spec, label = "skyBottom")
    val nightness = (if (sky.from == Period.NIGHT) 1f - sky.fraction else 0f) + (if (sky.to == Period.NIGHT) sky.fraction else 0f)

    val drift = if (reduceMotion) {
        0f
    } else {
        val t = rememberInfiniteTransition(label = "skyDrift")
        val v by t.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 24_000, easing = LinearEasing), RepeatMode.Reverse),
            label = "drift",
        )
        v
    }
    val stars = remember {
        val r = Random(1447)
        List(70) { Triple(r.nextFloat(), r.nextFloat() * 0.65f, 0.6f + r.nextFloat() * 1.4f) }
    }

    Canvas(modifier.fillMaxSize()) {
        val h = size.height
        drawRect(
            Brush.verticalGradient(
                0f to top,
                (0.55f + 0.08f * drift) to lerp(top, bottom, 0.55f),
                1f to bottom,
                startY = 0f,
                endY = h,
            ),
        )
        if (nightness > 0.01f) {
            stars.forEach { (x, y, r) ->
                drawCircle(Color.White.copy(alpha = 0.55f * nightness), radius = r * density, center = Offset(x * size.width, y * h))
            }
        }
    }
}
