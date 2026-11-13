package app.fasila.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fasila.core.AppPrefs
import app.fasila.core.FasilaConfig
import app.fasila.core.FasilaRepository
import app.fasila.core.ScheduleItem
import app.fasila.core.TodayState
import app.fasila.location.City
import app.fasila.location.LocationService
import app.fasila.location.Place
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uniffi.fasila_ffi.MethodInfo

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = FasilaRepository.get(app)
    private val location = LocationService(app)

    val today: StateFlow<TodayState> = repo.today
    val config: StateFlow<FasilaConfig> = repo.config
    val items: StateFlow<List<ScheduleItem>> = repo.items
    val prefs: StateFlow<AppPrefs> = repo.prefs
    val core: StateFlow<FasilaRepository.Core> = repo.core
    val methods: List<MethodInfo> get() = repo.methods
    val cities: List<City> get() = repo.cities

    private val _locating = MutableStateFlow(false)
    val locating: StateFlow<Boolean> = _locating.asStateFlow()

    private val _locationError = MutableStateFlow(false)
    val locationError: StateFlow<Boolean> = _locationError.asStateFlow()

    fun hasLocationPermission() = location.hasPermission()

    /** Reads the device location and moves the config there. */
    fun useDeviceLocation() {
        if (_locating.value) return
        viewModelScope.launch {
            _locating.value = true
            _locationError.value = false
            val place = runCatching { location.currentPlace(repo.cities) }.getOrNull()
            if (place != null) repo.setPlace(place, fromDevice = true) else _locationError.value = true
            _locating.value = false
        }
    }

    /** On app open: follow the device if the user chose automatic location. */
    fun refreshAutoLocation() {
        val p = prefs.value
        if (p.locationSet && p.autoLocation && location.hasPermission()) {
            viewModelScope.launch {
                val place = runCatching { location.currentPlace(repo.cities) }.getOrNull() ?: return@launch
                val c = config.value
                if (LocationService.distanceKm(c.latitude, c.longitude, place.latitude, place.longitude) > 5.0 ||
                    c.timezone != place.timezone
                ) repo.setPlace(place, fromDevice = true)
            }
        }
    }

    fun setPlace(place: Place) = repo.setPlace(place, fromDevice = false)
    fun setMethod(id: String) = repo.setMethod(id)
    fun updateConfig(transform: (FasilaConfig) -> FasilaConfig) = repo.updateConfig(transform)
    fun updatePrefs(transform: (AppPrefs) -> AppPrefs) = repo.updatePrefs(transform)
    fun saveItem(item: ScheduleItem) = repo.saveItem(item)
    fun deleteItem(id: String) = repo.deleteItem(id)
    fun setItemEnabled(id: String, enabled: Boolean) = repo.setItemEnabled(id, enabled)
}
