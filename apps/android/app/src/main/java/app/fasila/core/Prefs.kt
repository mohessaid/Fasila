package app.fasila.core

import android.content.Context
import android.content.SharedPreferences

/** App-level preferences that are not part of the shared config document. */
data class AppPrefs(
    /** Prayer keys with adhan notifications on. */
    val adhan: Set<String> = Keys.PRAYERS.toSet(),
    /** Minutes before each prayer for a pre-reminder; 0 = off. */
    val preReminderMinutes: Int = 0,
    /** Solid panes instead of translucent glass (Reduce Transparency). */
    val solidPanes: Boolean = false,
    /** True once the user picked a method explicitly; otherwise it follows the country. */
    val methodUserSet: Boolean = false,
    /** True once a location was set (from the device or the city list). */
    val locationSet: Boolean = false,
    /** Location follows the device (re-read on time-zone change). */
    val autoLocation: Boolean = true,
)

class PrefsStore(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("fasila", Context.MODE_PRIVATE)

    fun load() = AppPrefs(
        adhan = sp.getStringSet("adhan", null) ?: Keys.PRAYERS.toSet(),
        preReminderMinutes = sp.getInt("pre_reminder", 0),
        solidPanes = sp.getBoolean("solid_panes", false),
        methodUserSet = sp.getBoolean("method_user_set", false),
        locationSet = sp.getBoolean("location_set", false),
        autoLocation = sp.getBoolean("auto_location", true),
    )

    fun save(p: AppPrefs) {
        sp.edit()
            .putStringSet("adhan", p.adhan)
            .putInt("pre_reminder", p.preReminderMinutes)
            .putBoolean("solid_panes", p.solidPanes)
            .putBoolean("method_user_set", p.methodUserSet)
            .putBoolean("location_set", p.locationSet)
            .putBoolean("auto_location", p.autoLocation)
            .apply()
    }

    /** Request codes of the PendingIntents currently registered with AlarmManager. */
    var scheduledCodes: Set<String>
        get() = sp.getStringSet("scheduled_codes", emptySet()) ?: emptySet()
        set(v) { sp.edit().putStringSet("scheduled_codes", v).apply() }

    var notificationsAsked: Boolean
        get() = sp.getBoolean("notifications_asked", false)
        set(v) { sp.edit().putBoolean("notifications_asked", v).apply() }
}
