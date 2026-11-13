package app.fasila.alarm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.fasila.core.FasilaRepository
import app.fasila.location.LocationService
import app.fasila.widget.NextPrayerWidget
import java.util.concurrent.TimeUnit

/**
 * Daily safety net: refreshes the Hijri announcement feed, optionally re-reads the device
 * location, re-registers alarms (in case an OEM task killer dropped them) and updates widgets.
 */
class SafetyNetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = FasilaRepository.get(applicationContext)
        val prefs = repo.prefs.value
        if (inputData.getBoolean(KEY_REFRESH_LOCATION, false) || (prefs.locationSet && prefs.autoLocation)) {
            val loc = LocationService(applicationContext)
            if (loc.hasPermission()) {
                loc.currentPlace(repo.cities)?.let { place ->
                    val c = repo.config.value
                    val moved = LocationService.distanceKm(c.latitude, c.longitude, place.latitude, place.longitude) > 5.0
                    if (moved || c.timezone != place.timezone) repo.setPlace(place, fromDevice = true)
                }
            }
        }
        repo.refreshAnnouncements()
        AlarmScheduler.rescheduleAll(applicationContext)
        runCatching { NextPrayerWidget.refresh(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val KEY_REFRESH_LOCATION = "refresh_location"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<SafetyNetWorker>(24, TimeUnit.HOURS, 2, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("fasila-daily", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runNow(context: Context, refreshLocation: Boolean) {
            val req = OneTimeWorkRequestBuilder<SafetyNetWorker>()
                .setInputData(workDataOf(KEY_REFRESH_LOCATION to refreshLocation))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("fasila-now", ExistingWorkPolicy.REPLACE, req)
        }
    }
}
