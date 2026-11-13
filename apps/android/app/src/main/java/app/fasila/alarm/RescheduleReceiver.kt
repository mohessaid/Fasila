package app.fasila.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.fasila.core.FasilaRepository
import app.fasila.widget.NextPrayerWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Boot, package update, clock/zone/date/locale changes and exact-alarm permission changes all
 * invalidate the registered alarms (AlarmManager forgets them at boot; times shift with the zone).
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_LOCALE_CHANGED) Notifications.createChannels(context)

        if (action == Intent.ACTION_TIMEZONE_CHANGED) {
            val prefs = FasilaRepository.get(context).prefs.value
            // A new zone usually means the user travelled: re-read the device location.
            if (prefs.locationSet && prefs.autoLocation) SafetyNetWorker.runNow(context, refreshLocation = true)
        }

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
}
