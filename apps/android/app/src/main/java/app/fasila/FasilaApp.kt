package app.fasila

import android.app.Application
import app.fasila.alarm.AlarmScheduler
import app.fasila.alarm.Notifications
import app.fasila.alarm.SafetyNetWorker
import app.fasila.core.FasilaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FasilaApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        SafetyNetWorker.schedule(this)
        scope.launch {
            FasilaRepository.get(this@FasilaApp)
            AlarmScheduler.rescheduleAll(this@FasilaApp)
        }
    }
}
