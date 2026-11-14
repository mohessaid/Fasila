package app.fasila.ui.theme

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fasila.core.Sky

/*
 * Glass system (docs/DESIGN.md). Three levels and nothing else; state is shown by how
 * lit a pane is, never by outlines, side stripes, underlines or indicator dots.
 *   panel  the frosted sheet a region sits on
 *   pane   a quieter frosted surface (secondary state: current prayer, today)
 *   lit    brighter frost, strong rim light and a warm glow (next / selected / active)
 * Compose has no backdrop blur; over a smooth sky gradient a translucent fill reads the same.
 */

val Ink = Color(0xFFF8FAFC)
val Accent = Color(0xFFFF9B54)
val AccentInk = Color(0xFF060A14)
private val Night = Color(0xFF060A14)

@Immutable
data class GlassColors(
    /** Panel fill: white frost over the adaptive dark tint. */
    val fill: Color,
    /** Pane fill (secondary state, chips, fields). */
    val border: Color,
    /** Rim light at the top edge of a panel. */
    val highlight: Color,
    val content: Color,
    val secondary: Color,
    /** Faintest readable ink (labels, past times). */
    val tertiary: Color,
    val accent: Color,
    val warning: Color,
    val solid: Boolean,
) {
    /** Always false: the sky is always dark enough under glass for light ink. */
    val isLight: Boolean get() = false
}

val LocalGlass = staticCompositionLocalOf { glassColors(Sky.Night, solid = false) }

private fun over(top: Color, alpha: Float, base: Color): Color = Color(
    red = top.red * alpha + base.red * (1 - alpha),
    green = top.green * alpha + base.green * (1 - alpha),
    blue = top.blue * alpha + base.blue * (1 - alpha),
    alpha = 1f - (1f - alpha) * (1f - base.alpha),
)

fun glassColors(sky: Sky, solid: Boolean): GlassColors {
    // Tint opacity comes from the sky's brightness (core), so text stays >= 4.5:1.
    val tint = Night.copy(alpha = sky.glassTint)
    return GlassColors(
        fill = if (solid) Color(0xFF0E1426) else over(Color.White, 0.05f, tint),
        border = if (solid) Color(0xFF1C2440) else Color.White.copy(alpha = 0.08f),
        highlight = Color.White.copy(alpha = 0.16f),
        content = Ink,
        secondary = Color(0xFFE2E8F0).copy(alpha = 0.84f),
        tertiary = Color(0xFFCBD5E1).copy(alpha = 0.8f),
        accent = Accent,
        warning = Color(0xFFFFB26B),
        solid = solid,
    )
}

val CardShape = RoundedCornerShape(24.dp)
val RowShape = RoundedCornerShape(16.dp)
val ChipShape = RoundedCornerShape(999.dp)

/** Rim light: a stroke that is bright along the top edge and fades out downwards. */
private fun Modifier.rim(alpha: Float, shape: Shape): Modifier = drawWithCache {
    val brush = Brush.verticalGradient(
        0f to Color.White.copy(alpha = alpha),
        0.35f to Color.White.copy(alpha = alpha * 0.25f),
        1f to Color.Transparent,
    )
    onDrawWithContent {
        drawContent()
        val r = (shape as? RoundedCornerShape)?.topStart?.toPx(size, this) ?: 0f
        drawRoundRect(
            brush = brush,
            topLeft = Offset(0.5f, 0.5f),
            size = Size(size.width - 1f, size.height - 1f),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(1.dp.toPx()),
        )
    }
}

/** Warm accent light falling from the top centre of a lit pane. */
private fun Modifier.warmGlow(): Modifier = drawWithCache {
    val brush = Brush.radialGradient(
        listOf(Accent.copy(alpha = 0.28f), Color.Transparent),
        center = Offset(size.width / 2f, 0f),
        radius = size.width * 0.75f,
    )
    onDrawBehind { drawRect(brush) }
}

/** Panel: the frosted sheet a region sits on. */
// No elevation shadows on translucent surfaces: Compose draws them beneath the fill,
// where they show through as a dark band.
fun Modifier.glass(colors: GlassColors, shape: Shape = CardShape): Modifier = this
    .clip(shape)
    .background(colors.fill, shape)
    .then(if (colors.solid) Modifier else Modifier.rim(0.16f, shape))

/** Pane: a quieter frosted surface for secondary state. */
fun Modifier.pane(colors: GlassColors, shape: Shape = RowShape): Modifier = this
    .clip(shape)
    .background(colors.border, shape)
    .then(if (colors.solid) Modifier else Modifier.rim(0.1f, shape))

/** Lit pane: the whole surface lights up (next prayer, selected day, active tab). */
fun Modifier.lit(colors: GlassColors, shape: Shape = RowShape): Modifier = this
    .clip(shape)
    .then(
        if (colors.solid) {
            Modifier.background(Color(0xFF2A3350), shape)
        } else {
            Modifier
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.1f))), shape)
                .warmGlow()
                .rim(0.42f, shape)
        },
    )

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
