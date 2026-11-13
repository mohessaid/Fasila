package app.fasila.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.fasila.R
import app.fasila.core.FasilaRepository
import app.fasila.core.Keys
import app.fasila.core.TimeFormatter
import app.fasila.core.coreLang
import app.fasila.core.currentLocale
import app.fasila.widget.NextPrayerWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uniffi.fasila_ffi.timeKeyName

/** Posts the notification for a fired alarm, then rolls the alarm window forward. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DISMISS -> {
                Notifications.cancel(context, intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
                return
            }
            ACTION_FIRE -> Unit
            else -> return
        }
        val type = runCatching { AlarmScheduler.Type.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: "") }.getOrNull()
        val at = intent.getLongExtra(EXTRA_AT, 0)
        val now = System.currentTimeMillis() / 1000
        // Inexact fallbacks or a powered-off device can deliver very late; a stale adhan is noise.
        val stale = now - at > STALE_SECONDS
        if (type != null && type != AlarmScheduler.Type.ROLLOVER && !stale) notify(context, type, intent)

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                AlarmScheduler.rescheduleAll(context)
                runCatching { NextPrayerWidget.refresh(context) }
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(context: Context, type: AlarmScheduler.Type, intent: Intent) {
        val repo = FasilaRepository.get(context)
        val locale = context.currentLocale()
        val lang = coreLang(locale)
        val fmt = TimeFormatter.from(context, repo.config.value.zone)
        val key = intent.getStringExtra(EXTRA_KEY) ?: ""
        val at = intent.getLongExtra(EXTRA_AT, 0)
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val friday = intent.getBooleanExtra(EXTRA_FRIDAY, false)
        val prayerName = if (friday && key == Keys.DHUHR) context.getString(R.string.jumuah) else timeKeyName(key, lang)
        val id = intent.data?.lastPathSegment?.toIntOrNull() ?: at.toInt()

        when (type) {
            AlarmScheduler.Type.ADHAN -> {
                val text = buildString {
                    append(fmt.time(at))
                    if (intent.getBooleanExtra(EXTRA_ESTIMATED, false)) append(" · ").append(context.getString(R.string.estimated))
                }
                Notifications.post(context, id, Notifications.CHANNEL_ADHAN, context.getString(R.string.notif_adhan_title, prayerName), text)
            }
            AlarmScheduler.Type.PRE_REMINDER -> Notifications.post(
                context, id, Notifications.CHANNEL_REMINDERS,
                context.getString(R.string.notif_pre_title, prayerName, fmt.number(minutes)),
                fmt.time(at + minutes * 60L),
            )
            AlarmScheduler.Type.ITEM_ALARM -> Notifications.post(
                context, id, Notifications.CHANNEL_ALARMS, title, fmt.time(at), insistent = true,
            )
            AlarmScheduler.Type.ITEM_REMINDER -> Notifications.post(
                context, id, Notifications.CHANNEL_REMINDERS, title,
                if (minutes > 0) context.getString(R.string.notif_starts_in, fmt.number(minutes), fmt.time(at + minutes * 60L))
                else fmt.time(at),
            )
            AlarmScheduler.Type.ROLLOVER -> Unit
        }
    }

    companion object {
        const val ACTION_FIRE = "app.fasila.action.FIRE"
        const val ACTION_DISMISS = "app.fasila.action.DISMISS"
        const val EXTRA_TYPE = "type"
        const val EXTRA_AT = "at"
        const val EXTRA_KEY = "key"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_ESTIMATED = "estimated"
        const val EXTRA_FRIDAY = "friday"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        private const val STALE_SECONDS = 30 * 60L
    }
}
