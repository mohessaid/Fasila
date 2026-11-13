package app.fasila.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.fasila.MainActivity
import app.fasila.R
import app.fasila.core.FasilaRepository
import app.fasila.core.Keys
import app.fasila.core.SkyState
import app.fasila.core.TimeFormatter
import app.fasila.core.coreLang
import app.fasila.core.currentLocale
import app.fasila.core.time
import uniffi.fasila_ffi.timeKeyName

/**
 * Home-screen widget: next prayer and its time over the current sky colour. It shows clock
 * times rather than a countdown (widgets can't tick cheaply); it is refreshed at every
 * prayer by the alarm receiver and by the daily worker.
 */
class NextPrayerWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = FasilaRepository.get(context)
        val state = repo.snapshot()
        val fmt = TimeFormatter.from(context, state.zone)
        val lang = coreLang(context.currentLocale())
        val sky = SkyState.at(state).dominant
        val next = state.next
        val nextName = when {
            next == null -> ""
            next.friday && next.key == Keys.DHUHR -> context.getString(R.string.jumuah)
            else -> timeKeyName(next.key, lang)
        }
        val ink = if (sky.isLight) Color(0xFF0B1026) else Color.White
        val model = WidgetModel(
            place = repo.config.value.locationName,
            label = context.getString(R.string.next_prayer),
            nextName = nextName,
            nextTime = fmt.time(next?.time),
            prayers = Keys.PRAYERS.map { k ->
                Triple(timeKeyName(k, lang), fmt.time(state.today.time(k)), k == next?.key)
            },
            background = Color(sky.top),
            ink = ink,
            description = context.getString(R.string.a11y_next_prayer, nextName, fmt.time(next?.time)),
        )
        provideContent { Content(model) }
    }

    private data class WidgetModel(
        val place: String,
        val label: String,
        val nextName: String,
        val nextTime: String,
        val prayers: List<Triple<String, String, Boolean>>,
        val background: Color,
        val ink: Color,
        val description: String,
    )

    @Composable
    private fun Content(m: WidgetModel) {
        val ink = ColorProvider(m.ink)
        val wide = LocalSize.current.width >= 250.dp
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(m.background)
                .cornerRadius(24.dp)
                .padding(16.dp)
                .semantics { contentDescription = m.description }
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(m.label + " · " + m.place, style = TextStyle(color = ink, fontSize = 12.sp), maxLines = 1)
            Text(m.nextName, style = TextStyle(color = ink, fontSize = 20.sp, fontWeight = FontWeight.Medium))
            Text(m.nextTime, style = TextStyle(color = ink, fontSize = 34.sp))
            if (wide) {
                Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    m.prayers.forEach { (name, time, isNext) ->
                        Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            val weight = if (isNext) FontWeight.Bold else FontWeight.Normal
                            Text(name, style = TextStyle(color = ink, fontSize = 11.sp, fontWeight = weight, textAlign = TextAlign.Center), maxLines = 1)
                            Text(time, style = TextStyle(color = ink, fontSize = 12.sp, fontWeight = weight, textAlign = TextAlign.Center), maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) = NextPrayerWidget().updateAll(context)
    }
}

class NextPrayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextPrayerWidget()
}
