package app.fasila

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import app.fasila.alarm.AlarmScheduler
import app.fasila.alarm.Notifications
import app.fasila.core.FasilaRepository
import app.fasila.ui.FasilaRoot
import app.fasila.ui.MainViewModel
import kotlinx.coroutines.launch

/** AppCompatActivity so per-app language (AppCompatDelegate.setApplicationLocales) works below API 33. */
class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Status/navigation bar icon colours are switched per sky period in FasilaRoot.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { FasilaRoot(vm) }
        askNotificationsOnce()
    }

    override fun onResume() {
        super.onResume()
        Notifications.createChannels(this)
        vm.refreshAutoLocation()
        lifecycleScope.launch { AlarmScheduler.rescheduleAll(this@MainActivity) }
    }

    private fun askNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val store = FasilaRepository.get(this).prefsStorage
        if (store.notificationsAsked) return
        store.notificationsAsked = true
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
