package app.fasila.ui.qibla

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fasila.R
import app.fasila.ui.MainViewModel
import app.fasila.ui.rememberFormatter
import app.fasila.ui.theme.GlassCard
import app.fasila.ui.theme.LocalGlass
import app.fasila.ui.theme.skyPill
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Heading (degrees from true north) from the rotation-vector sensor, low-pass filtered. */
private class HeadingSensor(context: Context, private val declination: Float, private val rotation: () -> Int) : SensorEventListener {
    private val sm = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    val available get() = sensor != null
    var onHeading: (Float) -> Unit = {}
    var onAccuracy: (Int) -> Unit = {}
    private var sx = 0f
    private var sy = 0f
    private val r = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)

    fun start() { sensor?.let { sm?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) } }
    fun stop() { sm?.unregisterListener(this) }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(r, event.values)
        val (ax, ay) = when (rotation()) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(r, ax, ay, remapped)
        SensorManager.getOrientation(remapped, orientation)
        val deg = Math.toDegrees(orientation[0].toDouble()).toFloat() + declination
        // Smooth on the unit circle so 359° → 1° doesn't swing the needle around.
        val rad = Math.toRadians(deg.toDouble())
        sx = sx * 0.85f + cos(rad).toFloat() * 0.15f
        sy = sy * 0.85f + sin(rad).toFloat() * 0.15f
        onHeading(((Math.toDegrees(atan2(sy, sx).toDouble()).toFloat()) + 360f) % 360f)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = onAccuracy(accuracy)
}

@Composable
fun QiblaScreen(vm: MainViewModel, padding: PaddingValues) {
    val core by vm.core.collectAsStateWithLifecycle()
    val state by vm.today.collectAsStateWithLifecycle()
    val fmt = rememberFormatter(state.zone)
    val g = LocalGlass.current
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current

    // The bearing and distance come from the core; only the device heading is measured here.
    val qibla = remember(core) { core.engine.qibla().toFloat() }
    val distance = remember(core) { core.engine.distanceToKaabaKm() }
    val declination = remember(core) {
        GeomagneticField(core.config.latitude.toFloat(), core.config.longitude.toFloat(), 0f, System.currentTimeMillis()).declination
    }

    var heading by remember { mutableFloatStateOf(Float.NaN) }
    var accuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
    val sensor = remember(declination) { HeadingSensor(context, declination) { view.display?.rotation ?: Surface.ROTATION_0 } }
    DisposableEffect(sensor) {
        sensor.onHeading = { heading = it }
        sensor.onAccuracy = { accuracy = it }
        sensor.start()
        onDispose { sensor.stop() }
    }

    val hasHeading = !heading.isNaN()
    val diff = if (hasHeading) ((qibla - heading + 540f) % 360f) - 180f else 0f
    val aligned = hasHeading && abs(diff) < 3f
    var wasAligned by remember { mutableStateOf(false) }
    LaunchedEffect(aligned) {
        if (aligned && !wasAligned) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        wasAligned = aligned
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.tab_qibla), color = g.content, fontSize = 28.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { heading() },
        )

        val description = when {
            !sensor.available || !hasHeading -> stringResource(R.string.qibla_static, fmt.number(qibla.roundToInt()))
            aligned -> stringResource(R.string.qibla_aligned)
            diff > 0 -> stringResource(R.string.qibla_turn_right, fmt.number(abs(diff).roundToInt()))
            else -> stringResource(R.string.qibla_turn_left, fmt.number(abs(diff).roundToInt()))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clearAndSetSemantics {
                    contentDescription = description
                    liveRegion = LiveRegionMode.Polite
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f - 16.dp.toPx()
                val dialRotation = if (hasHeading) -heading else 0f
                rotate(dialRotation) {
                    drawCircle(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            listOf(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.16f), androidx.compose.ui.graphics.Color.White.copy(alpha = 0.04f)),
                            center = center.copy(y = center.y - r * 0.4f), radius = r * 1.5f,
                        ),
                        radius = r,
                    )
                    for (i in 0 until 72) {
                        val a = Math.toRadians(i * 5.0 - 90)
                        val long = i % 18 == 0
                        val inner = r - (if (long) 16.dp else 8.dp).toPx()
                        drawLine(
                            if (long) g.content else g.secondary,
                            Offset(center.x + inner * cos(a).toFloat(), center.y + inner * sin(a).toFloat()),
                            Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat()),
                            strokeWidth = (if (long) 2.dp else 1.dp).toPx(),
                        )
                    }
                    // North marker.
                    val n = Path().apply {
                        moveTo(center.x, center.y - r + 22.dp.toPx())
                        lineTo(center.x - 7.dp.toPx(), center.y - r + 36.dp.toPx())
                        lineTo(center.x + 7.dp.toPx(), center.y - r + 36.dp.toPx())
                        close()
                    }
                    drawPath(n, g.secondary)
                    // Qibla needle, fixed to the dial at the bearing from true north.
                    rotate(qibla) {
                        drawLine(
                            if (aligned) g.accent else g.content,
                            center, Offset(center.x, center.y - r + 40.dp.toPx()),
                            strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round,
                        )
                        val k = 14.dp.toPx()
                        val top = center.y - r + 40.dp.toPx() - k
                        drawRect(g.accent, topLeft = Offset(center.x - k / 2, top - k / 2), size = androidx.compose.ui.geometry.Size(k, k))
                    }
                }
                drawCircle(g.content, radius = 6.dp.toPx())
                // Fixed heading marker at the top of the screen.
                drawLine(g.accent, Offset(center.x, 0f), Offset(center.x, 14.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
        }

        Text(description, color = if (aligned) g.accent else g.content, fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.skyPill(g))

        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.qibla_bearing, fmt.number(qibla.roundToInt())), color = g.content)
            Text(stringResource(R.string.qibla_distance, fmt.number(distance.roundToInt())), color = g.secondary)
            if (!sensor.available) {
                Text(stringResource(R.string.qibla_no_sensor), color = g.warning, modifier = Modifier.padding(top = 8.dp))
            } else if (accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                Text(stringResource(R.string.qibla_calibrate), color = g.warning, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
