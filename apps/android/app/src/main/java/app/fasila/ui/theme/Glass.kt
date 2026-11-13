package app.fasila.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fasila.core.Period
import app.fasila.core.SkyState

/**
 * Glass tokens for the current sky (docs/DESIGN.md → Glass).
 *
 * Text on glass is white 95 % / 70 % over the dark periods. Over the light periods
 * (Morning, Dhuhr, Asr) white text cannot reach 4.5:1 against the pale sky, so the
 * ink switches to the night navy at the same opacities.
 */
@Immutable
data class GlassColors(
    val fill: Color,
    val border: Color,
    val highlight: Color,
    val content: Color,
    val secondary: Color,
    val accent: Color,
    val warning: Color,
    val isLight: Boolean,
)

val LocalGlass = staticCompositionLocalOf { glassColors(SkyState.Default, solid = false) }

private val Navy = Color(0xFF0B1026)

fun glassColors(sky: SkyState, solid: Boolean): GlassColors {
    val p = sky.dominant
    val accent = lerp(Color(sky.from.accent), Color(sky.to.accent), sky.fraction)
    val ink = if (p.isLight) Navy else Color.White
    val fill = when {
        // Reduce Transparency: an opaque pane tinted by the sky.
        solid && p.isLight -> lerp(Color(p.bottom), Color.White, 0.75f)
        solid -> lerp(Color(p.top), Color.Black, 0.25f)
        p.isLight -> Color.White.copy(alpha = 0.26f)
        else -> Color.White.copy(alpha = 0.12f)
    }
    return GlassColors(
        fill = fill,
        border = Color.White.copy(alpha = if (p.isLight) 0.25f else 0.18f),
        highlight = Color.White.copy(alpha = if (p.isLight) 0.45f else 0.28f),
        content = ink.copy(alpha = 0.95f),
        secondary = ink.copy(alpha = if (p.isLight) 0.75f else 0.70f),
        accent = if (p.isLight) lerp(accent, Navy, 0.55f) else accent,
        warning = if (p.isLight) Color(0xFF8A4B00) else Color(0xFFFFC46B),
        isLight = p.isLight,
    )
}

/**
 * Frosted pane: translucent fill, 1 px border and a 1 px inner top highlight.
 *
 * The sky behind every pane is a smooth vertical gradient, so a backdrop blur would be
 * visually indistinguishable from the translucent fill; we skip the extra render pass.
 */
fun Modifier.glass(colors: GlassColors, shape: Shape): Modifier = this
    .clip(shape)
    .background(colors.fill, shape)
    .border(1.dp, colors.border, shape)
    .drawWithContent {
        drawContent()
        val inset = 18.dp.toPx()
        drawLine(
            color = colors.highlight,
            start = Offset(inset, 1.dp.toPx()),
            end = Offset(size.width - inset, 1.dp.toPx()),
            strokeWidth = 1.dp.toPx(),
        )
    }

val CardShape = RoundedCornerShape(24.dp)
val RowShape = RoundedCornerShape(16.dp)
val ChipShape = RoundedCornerShape(999.dp)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val g = LocalGlass.current
    CompositionLocalProvider(LocalContentColor provides g.content) {
        Column(modifier.glass(g, shape).padding(padding), content = content)
    }
}

fun Period.inkOn(): Color = if (isLight) Navy else Color.White
