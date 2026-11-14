package app.fasila.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.fasila.core.Sky
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private data class Star(val x: Float, val y: Float, val r: Float, val max: Float, val period: Float, val phase: Float)

/**
 * The living sky: a 4-stop gradient from the shared core, blended through the day,
 * with 45 stars that twinkle independently and appear only after sunset.
 * Under Reduce Motion colours change instantly and stars hold still.
 */
@Composable
fun LivingSky(sky: Sky, modifier: Modifier = Modifier) {
    val reduceMotion = LocalA11y.current.reduceMotion
    val spec = if (reduceMotion) snap<Color>() else tween<Color>(2500, easing = FastOutSlowInEasing)
    val stops = sky.colors.mapIndexed { i, c ->
        animateColorAsState(Color(0xFF000000.toInt() or c), spec, label = "sky$i").value
    }
    val starAlpha by animateFloatAsState(sky.starOpacity, if (reduceMotion) snap() else tween(1500), label = "stars")
    val stars = remember {
        val r = Random(1447)
        List(45) {
            Star(r.nextFloat(), r.nextFloat() * 0.8f, 0.25f + r.nextFloat(), 0.28f + r.nextFloat() * 0.62f,
                2000f + r.nextFloat() * 4000f, r.nextFloat() * 3000f)
        }
    }
    val clock = remember { mutableLongStateOf(0L) }
    if (!reduceMotion && starAlpha > 0.01f) {
        LaunchedEffect(Unit) { while (true) withInfiniteAnimationFrameMillis { clock.longValue = it } }
    }

    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(0f to stops[0], 0.38f to stops[1], 0.72f to stops[2], 1f to stops[3]))
        if (starAlpha > 0.01f) {
            val t = clock.longValue.toFloat()
            stars.forEach { s ->
                val tw = if (reduceMotion) 1f else 0.79f + 0.21f * sin(2 * PI.toFloat() * (t + s.phase) / s.period)
                drawCircle(
                    Color.White.copy(alpha = (s.max * tw * starAlpha).coerceIn(0f, 1f)),
                    radius = s.r * density,
                    center = Offset(s.x * size.width, s.y * size.height),
                )
            }
        }
    }
}
