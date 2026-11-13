package app.fasila.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import app.fasila.MainActivity
import app.fasila.core.FasilaRepository
import app.fasila.core.Keys
import app.fasila.core.isEstimated
import app.fasila.core.time
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps a rolling ~48 h window of exact alarms registered with AlarmManager:
 * - adhan at each enabled prayer and the optional pre-reminder → `setExactAndAllowWhileIdle`
 * - item reminders (tasks/events, remind-before) → `setExactAndAllowWhileIdle`
 * - user alarms → `setAlarmClock` (shown in the status bar, exempt from Doze)
 * Without the exact-alarm permission everything falls back to `setAndAllowWhileIdle`.
 *
 * Every firing re-runs [rescheduleAll], and so do boot, clock/zone/date/locale changes,
 * app open, settings edits and a daily WorkManager safety net.
 */
object AlarmScheduler {
    private const val TAG = "FasilaAlarms"
    const val HORIZON_SECONDS = 48 * 3600L
    private val mutex = Mutex()

    enum class Type { ADHAN, PRE_REMINDER, ITEM_REMINDER, ITEM_ALARM, ROLLOVER }

    data class Spec(
        val type: Type,
        /** Unix seconds. */
        val at: Long,
        val key: String = "",
        val title: String = "",
        val itemId: String = "",
        /** For pre-reminders and remind-before: minutes until the event. */
        val minutesBefore: Int = 0,
        val estimated: Boolean = false,
        val friday: Boolean = false,
    ) {
        val code: Int get() = "$type|$at|$key|$itemId".hashCode()
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    /** Pure planning step: what should be registered right now. */
    fun plan(context: Context, now: Long = System.currentTimeMillis() / 1000): List<Spec> {
        val repo = FasilaRepository.get(context)
        val prefs = repo.prefs.value
        val horizon = now + HORIZON_SECONDS
        val start = repo.localToday().minusDays(1)
        val specs = mutableListOf<Spec>()

        for (day in repo.days(start, 4)) {
            for (key in Keys.PRAYERS) {
                if (key !in prefs.adhan) continue
                val t = day.time(key) ?: continue
                val friday = day.weekday.toInt() == 5
                specs += Spec(Type.ADHAN, t, key, estimated = day.isEstimated(key), friday = friday)
                if (prefs.preReminderMinutes > 0) {
                    specs += Spec(Type.PRE_REMINDER, t - prefs.preReminderMinutes * 60L, key,
                        minutesBefore = prefs.preReminderMinutes, friday = friday)
                }
            }
        }

        val items = repo.items.value.filter { it.enabled }
        val byId = items.associateBy { it.id }
        for (o in repo.occurrences(items, start, 4)) {
            val item = byId[o.itemId] ?: continue
            specs += if (o.kind == "alarm") Spec(Type.ITEM_ALARM, o.start, item.startKey, o.title, o.itemId)
            else Spec(Type.ITEM_REMINDER, o.start, item.startKey, o.title, o.itemId)
            item.remindBeforeMinutes?.takeIf { it > 0 }?.let { m ->
                specs += Spec(Type.ITEM_REMINDER, o.start - m * 60L, item.startKey, o.title, o.itemId, minutesBefore = m)
            }
        }

        val upcoming = specs.filter { it.at > now && it.at <= horizon }.sortedBy { it.at }
        // Roll the window forward even if nothing fires for a while.
        return upcoming + Spec(Type.ROLLOVER, now + 12 * 3600L)
    }

    suspend fun rescheduleAll(context: Context) = mutex.withLock {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return@withLock
        val store = FasilaRepository.get(app).prefsStorage

        for (code in store.scheduledCodes) {
            code.toIntOrNull()?.let { c ->
                PendingIntent.getBroadcast(app, c, baseIntent(app, c), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
                    ?.let { am.cancel(it); it.cancel() }
            }
        }

        val exact = canScheduleExact(app)
        val specs = plan(app)
        val codes = mutableSetOf<String>()
        for (s in specs) {
            val pi = PendingIntent.getBroadcast(
                app, s.code, toIntent(app, s), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val atMs = s.at * 1000
            try {
                when {
                    s.type == Type.ROLLOVER -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
                    !exact -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
                    s.type == Type.ITEM_ALARM -> am.setAlarmClock(AlarmManager.AlarmClockInfo(atMs, showIntent(app)), pi)
                    else -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
                }
            } catch (e: SecurityException) {
                // Exact-alarm permission revoked mid-way.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
            }
            codes += s.code.toString()
        }
        store.scheduledCodes = codes
        Log.d(TAG, "scheduled ${specs.size} alarms (exact=$exact)")
    }

    private fun showIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Intent identity is (component, action, data); extras don't count, so the code goes in the data URI. */
    private fun baseIntent(context: Context, code: Int) = Intent(context, AlarmReceiver::class.java)
        .setAction(AlarmReceiver.ACTION_FIRE)
        .setData(Uri.parse("fasila://alarm/$code"))

    private fun toIntent(context: Context, s: Spec) = baseIntent(context, s.code)
        .putExtra(AlarmReceiver.EXTRA_TYPE, s.type.name)
        .putExtra(AlarmReceiver.EXTRA_AT, s.at)
        .putExtra(AlarmReceiver.EXTRA_KEY, s.key)
        .putExtra(AlarmReceiver.EXTRA_TITLE, s.title)
        .putExtra(AlarmReceiver.EXTRA_ITEM_ID, s.itemId)
        .putExtra(AlarmReceiver.EXTRA_MINUTES, s.minutesBefore)
        .putExtra(AlarmReceiver.EXTRA_ESTIMATED, s.estimated)
        .putExtra(AlarmReceiver.EXTRA_FRIDAY, s.friday)
}
