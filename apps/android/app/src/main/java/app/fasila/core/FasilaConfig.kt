package app.fasila.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.ZoneId

/** Read-mostly view over the shared configuration document (see `fasila_core::config::Config`). */
data class FasilaConfig(val json: JsonObject) {
    private val location get() = json.obj("location")
    private val params get() = json.obj("params")

    val locationName: String get() = location.str("name")
    val latitude: Double get() = location.dbl("latitude")
    val longitude: Double get() = location.dbl("longitude")
    val timezone: String get() = location.str("timezone")
    val country: String get() = location.str("country")

    /** Times are rendered in the configured location's zone, not the device's. */
    val zone: ZoneId get() = runCatching { ZoneId.of(timezone) }.getOrElse { ZoneId.systemDefault() }

    val method: String get() = params.str("method", "muslim_world_league")
    val madhab: String get() = params.str("madhab", "standard")
    val highLatitudeRule: String get() = params.str("high_latitude_rule", "auto")
    val rounding: String get() = params.str("rounding", "safe")
    fun adjustment(key: String): Int = params.obj("adjustments").dbl(key).toInt()
    val hijriDayAdjustment: Int get() = json.obj("hijri").int("day_adjustment")
    val hijriCalendar: String get() = json.obj("hijri").str("calendar", "umm_al_qura")

    fun set(path: String, value: JsonElement) = FasilaConfig(json.withPath(path, value))
    fun set(path: String, value: String) = set(path, JsonPrimitive(value))
    fun set(path: String, value: Number) = set(path, JsonPrimitive(value))

    fun encode(): String = json.toString()

    /**
     * Switch calculation method. Method-specific parameters (angles, Isha interval, Maghrib,
     * midnight rule, authority offsets) come from the core (`method_params_json`); the user's
     * own choices (madhab, high-latitude/polar rules, rounding, personal offsets, imsak,
     * elevation) are kept from the current params.
     */
    fun withMethod(methodId: String, methodParams: JsonObject?): FasilaConfig {
        if (methodParams == null) return set("params.method", methodId)
        var p: JsonObject = methodParams
        for (k in USER_PARAM_KEYS) params[k]?.let { p = p.with(k, it) }
        return FasilaConfig(json.with("params", p.with("method", methodId)))
    }

    /** Merge authoritative month starts (announcement feed) for this country into the Hijri profile. */
    fun withMonthStarts(calendar: String?, starts: JsonArray): FasilaConfig {
        val hijri = json.obj("hijri")
        val key = { e: JsonElement -> e.asObject().let { it.int("year") to it.int("month") } }
        val incoming = starts.associateBy(key)
        val merged = hijri["month_starts"].asArray().filter { key(it) !in incoming } + incoming.values
        var h = hijri.with("month_starts", JsonArray(merged))
        if (calendar != null) h = h.with("calendar", calendar)
        return FasilaConfig(json.with("hijri", h))
    }

    companion object {
        val USER_PARAM_KEYS = listOf("madhab", "high_latitude_rule", "polar_rule", "rounding", "adjustments", "elevation_m", "imsak_minutes")
        val ADJUSTABLE = listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha")
        val MADHABS = listOf("standard", "hanafi")
        val HIGH_LATITUDE_RULES = listOf("auto", "middle_of_the_night", "seventh_of_the_night", "twilight_angle", "none")
        val ROUNDINGS = listOf("safe", "nearest", "none")
    }
}
