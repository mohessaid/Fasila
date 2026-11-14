package app.fasila.core

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import app.fasila.BuildConfig
import app.fasila.alarm.AlarmScheduler
import app.fasila.location.City
import app.fasila.location.Place
import app.fasila.widget.NextPrayerWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import uniffi.fasila_ffi.DayInfo
import uniffi.fasila_ffi.FasilaEngine
import uniffi.fasila_ffi.moonPhase
import uniffi.fasila_ffi.FasilaException
import uniffi.fasila_ffi.MethodInfo
import uniffi.fasila_ffi.OccurrenceInfo
import uniffi.fasila_ffi.SlotInfo
import uniffi.fasila_ffi.calculationMethods
import uniffi.fasila_ffi.defaultConfigJson
import uniffi.fasila_ffi.methodParamsJson
import uniffi.fasila_ffi.recommendedMethod
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Single source of truth: the shared config and items documents (JSON files in app storage),
 * the [FasilaEngine] built from them, and derived state for the UI.
 *
 * All astronomy and calendar work happens in Rust; this class only stores documents,
 * builds engines and slices results by time.
 */
class FasilaRepository private constructor(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefsStore = PrefsStore(app)
    private val configFile = AtomicFile(File(app.filesDir, "config.json"))
    private val itemsFile = AtomicFile(File(app.filesDir, "items.json"))
    private val feedFile = File(app.filesDir, "announcements.json")

    private val _prefs = MutableStateFlow(prefsStore.load())
    val prefs: StateFlow<AppPrefs> = _prefs.asStateFlow()

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<FasilaConfig> = _config.asStateFlow()

    private val _items = MutableStateFlow(loadItems())
    val items: StateFlow<List<ScheduleItem>> = _items.asStateFlow()

    @Volatile private var dayCache: Triple<FasilaEngine, LocalDate, List<DayInfo>>? = null

    /** A config together with the engine built from it. */
    data class Core(val config: FasilaConfig, val engine: FasilaEngine)

    val core: StateFlow<Core> = _config
        .map { buildCore(it) }
        .stateIn(scope, SharingStarted.Eagerly, buildCore(_config.value))

    /** Unix seconds, ticking on each wall-clock second while someone is collecting. */
    val clock: StateFlow<Long> = flow {
        while (true) {
            val ms = System.currentTimeMillis()
            emit(ms / 1000)
            delay(1000 - ms % 1000)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis() / 1000)

    val today: StateFlow<TodayState> = combine(core, clock) { c, now -> snapshot(c, now) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), snapshot(core.value, clock.value))

    val methods: List<MethodInfo> by lazy { calculationMethods() }

    // ---------------------------------------------------------------- snapshots

    /** Yesterday, today and tomorrow (local civil dates in the configured zone). */
    fun daysAround(c: Core, date: LocalDate): List<DayInfo> {
        dayCache?.let { (e, d, days) -> if (e === c.engine && d == date) return days }
        val start = date.minusDays(1)
        val days = c.engine.days(start.year, start.monthValue.toUByte(), start.dayOfMonth.toUByte(), 3u)
        dayCache = Triple(c.engine, date, days)
        return days
    }

    fun snapshot(c: Core = core.value, now: Long = System.currentTimeMillis() / 1000): TodayState {
        val date = Instant.ofEpochSecond(now).atZone(c.config.zone).toLocalDate()
        val days = daysAround(c, date)
        val tl = timeline(days)
        return TodayState(
            now = now,
            zone = c.config.zone,
            date = date,
            yesterday = days[0],
            today = days[1],
            tomorrow = days[2],
            // Between sunrise and Dhuhr no obligatory prayer is current (Fajr ends at sunrise).
            current = tl.currentAt(now)?.takeUnless { c ->
                c.key == Keys.FAJR && days[1].sunrise?.let { now >= it.toLong() } == true
            },
            next = tl.nextAfter(now),
            sky = runCatching {
                Sky.of(c.engine.sky(date.year, date.monthValue.toUByte(), date.dayOfMonth.toUByte(), now), moonPhase(now))
            }.getOrDefault(Sky.Night),
        )
    }

    fun days(start: LocalDate, count: Int): List<DayInfo> = runCatching {
        core.value.engine.days(start.year, start.monthValue.toUByte(), start.dayOfMonth.toUByte(), count.toUInt())
    }.getOrElse { emptyList() }

    fun occurrences(items: List<ScheduleItem>, start: LocalDate, days: Int): List<OccurrenceInfo> = try {
        core.value.engine.occurrences(
            items.toJson(), start.year, start.monthValue.toUByte(), start.dayOfMonth.toUByte(), days.toUInt(),
        )
    } catch (e: FasilaException) {
        Log.w(TAG, "occurrences failed", e)
        emptyList()
    }

    fun freeSlots(date: LocalDate, durationMinutes: Int): List<SlotInfo> = runCatching {
        core.value.engine.freeSlots(
            date.year, date.monthValue.toUByte(), date.dayOfMonth.toUByte(), durationMinutes.coerceAtLeast(1).toUInt(),
        )
    }.getOrElse { emptyList() }

    fun localToday(): LocalDate = Instant.now().atZone(core.value.config.zone).toLocalDate()

    // ---------------------------------------------------------------- config

    fun updateConfig(transform: (FasilaConfig) -> FasilaConfig) {
        val next = transform(_config.value)
        // Validate through the core before persisting; an invalid document is never saved.
        try {
            FasilaEngine(next.encode())
        } catch (e: FasilaException) {
            Log.w(TAG, "rejected config change", e)
            return
        }
        write(configFile, next.encode())
        _config.value = next
        onScheduleInputsChanged()
    }

    fun setMethod(methodId: String) {
        updateConfig { it.withMethod(methodId, methodParams(methodId)) }
        updatePrefs { it.copy(methodUserSet = true) }
    }

    /** Move to a new place. The method follows the country unless the user picked one. */
    fun setPlace(place: Place, fromDevice: Boolean) {
        val defaults = FasilaConfig(
            parseObject(defaultConfigJson(place.name, place.latitude, place.longitude, place.timezone, place.country))
                ?: return,
        )
        val p = _prefs.value
        updateConfig { current ->
            val base = if (!p.locationSet) defaults else FasilaConfig(current.json.with("location", defaults.json.obj("location")))
            // The method follows the country (core's `recommended_method`) until the user picks one.
            val withMethod = if (p.methodUserSet || !p.locationSet) base
            else recommendedMethod(place.country).let { m -> base.withMethod(m, methodParams(m)) }
            applyAnnouncements(withMethod)
        }
        updatePrefs { it.copy(locationSet = true, autoLocation = fromDevice) }
    }

    /** Full parameter set of a method, from the core (`method_params_json`). */
    private fun methodParams(methodId: String): JsonObject? = try {
        parseObject(methodParamsJson(methodId))
    } catch (e: FasilaException) {
        null
    }

    // ---------------------------------------------------------------- Hijri announcements feed

    private fun announcements(): JsonObject? {
        val text = runCatching { feedFile.takeIf { it.exists() }?.readText() }.getOrNull()
            ?: runCatching { app.assets.open("hijri/announcements.json").bufferedReader().use { it.readText() } }.getOrNull()
        return text?.let(::parseObject)
    }

    private fun applyAnnouncements(config: FasilaConfig): FasilaConfig {
        val region = announcements()?.obj("regions")?.obj(config.country) ?: return config
        if (region.isEmpty()) return config
        return config.withMonthStarts(region.str("calendar").ifEmpty { null }, region["month_starts"].asArray())
    }

    /** Downloads the shared feed (safety-net worker); keeps the bundled copy on failure. */
    suspend fun refreshAnnouncements() = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(BuildConfig.HIJRI_FEED_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            try {
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    if (parseObject(text)?.int("schema") == 1) feedFile.writeText(text)
                }
            } finally {
                conn.disconnect()
            }
        }.onFailure { Log.i(TAG, "Hijri feed not refreshed: ${it.message}") }
        val updated = applyAnnouncements(_config.value)
        if (updated != _config.value) updateConfig { updated }
    }

    // ---------------------------------------------------------------- cities

    val cities: List<City> by lazy {
        runCatching {
            app.assets.open("cities.json").bufferedReader().use { r ->
                FasilaJson.parseToJsonElement(r.readText()).asArray().map { e ->
                    val o = e.asObject()
                    City(o.str("name"), o.str("name_ar"), o.str("country"), o.dbl("latitude"), o.dbl("longitude"), o.str("timezone"))
                }
            }
        }.getOrElse { emptyList() }
    }

    // ---------------------------------------------------------------- items

    fun saveItem(item: ScheduleItem) {
        val withId = if (item.id.isEmpty()) item.withId(UUID.randomUUID().toString()) else item
        _items.update { list ->
            val i = list.indexOfFirst { it.id == withId.id }
            if (i >= 0) list.toMutableList().also { it[i] = withId } else list + withId
        }
        persistItems()
    }

    fun deleteItem(id: String) {
        _items.update { list -> list.filterNot { it.id == id } }
        persistItems()
    }

    fun setItemEnabled(id: String, enabled: Boolean) {
        _items.update { list -> list.map { if (it.id == id) it.withEnabled(enabled) else it } }
        persistItems()
    }

    private fun persistItems() {
        write(itemsFile, _items.value.toJson())
        onScheduleInputsChanged()
    }

    // ---------------------------------------------------------------- prefs

    fun updatePrefs(transform: (AppPrefs) -> AppPrefs) {
        val next = transform(_prefs.value)
        if (next == _prefs.value) return
        val alarmsChanged = next.adhan != _prefs.value.adhan || next.preReminderMinutes != _prefs.value.preReminderMinutes
        _prefs.value = next
        prefsStore.save(next)
        if (alarmsChanged) onScheduleInputsChanged()
    }

    val prefsStorage: PrefsStore get() = prefsStore

    // ---------------------------------------------------------------- side effects

    private fun onScheduleInputsChanged() {
        scope.launch {
            AlarmScheduler.rescheduleAll(app)
            runCatching { NextPrayerWidget.refresh(app) }
        }
    }

    // ---------------------------------------------------------------- storage

    private fun buildCore(config: FasilaConfig): Core = try {
        Core(config, FasilaEngine(config.encode()))
    } catch (e: FasilaException) {
        Log.e(TAG, "invalid stored config, using defaults", e)
        val fallback = defaultConfig()
        Core(fallback, FasilaEngine(fallback.encode()))
    }

    private fun loadConfig(): FasilaConfig =
        read(configFile)?.let(::parseObject)?.let(::FasilaConfig) ?: defaultConfig()

    /** Until a location is set: Makkah al-Mukarramah (the Today screen asks for a location). */
    private fun defaultConfig(): FasilaConfig =
        FasilaConfig(parseObject(defaultConfigJson("Makkah", 21.4225, 39.8262, "Asia/Riyadh", "SA"))!!)

    private fun loadItems(): List<ScheduleItem> =
        read(itemsFile)?.let { runCatching { FasilaJson.parseToJsonElement(it).asArray() }.getOrNull() }
            ?.map { ScheduleItem(it.asObject()) } ?: emptyList()

    private fun read(f: AtomicFile): String? =
        runCatching { if (f.baseFile.exists()) String(f.readFully(), Charsets.UTF_8) else null }.getOrNull()

    private fun write(f: AtomicFile, text: String) = synchronized(f) {
        val out = f.startWrite()
        try {
            out.write(text.toByteArray(Charsets.UTF_8))
            f.finishWrite(out)
        } catch (e: Exception) {
            f.failWrite(out)
            Log.e(TAG, "write failed", e)
        }
    }

    companion object {
        private const val TAG = "Fasila"

        @Volatile private var instance: FasilaRepository? = null

        fun get(context: Context): FasilaRepository =
            instance ?: synchronized(this) { instance ?: FasilaRepository(context).also { instance = it } }
    }
}
