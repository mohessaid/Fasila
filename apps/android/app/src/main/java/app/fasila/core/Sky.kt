package app.fasila.core

import uniffi.fasila_ffi.MoonInfo
import uniffi.fasila_ffi.SkyInfo

/**
 * The living sky (docs/DESIGN.md). Computed by the shared Rust core
 * (`FasilaEngine.sky`): colours pinned to real prayer times, star curve and the
 * adaptive glass tint that keeps text at WCAG AA on bright skies.
 */
data class Sky(
    /** Four gradient stops, top to bottom, 0xRRGGBB. */
    val colors: List<Int>,
    val starOpacity: Float,
    /** Opacity of the #060A14 tint under glass panels. */
    val glassTint: Float,
    val isDaytime: Boolean,
    /** 0 at sunrise, 1 at Maghrib. */
    val sun: Float,
    val timeOfDay: String,
    /** 0 new, 0.25 first quarter, 0.5 full, 0.75 last quarter. */
    val moonPhase: Float,
    val moonIllumination: Float,
) {
    companion object {
        val Night = Sky(
            colors = listOf(0x010208, 0x020510, 0x040A1B, 0x07132B),
            starOpacity = 1f, glassTint = 0.36f, isDaytime = false, sun = 0f,
            timeOfDay = "night", moonPhase = 0.5f, moonIllumination = 1f,
        )

        fun of(s: SkyInfo, m: MoonInfo) = Sky(
            colors = s.colors.map { it.toInt() },
            starOpacity = s.starOpacity.toFloat(),
            glassTint = s.glassTint.toFloat(),
            isDaytime = s.isDaytime,
            sun = s.sun.toFloat(),
            timeOfDay = s.timeOfDay,
            moonPhase = m.phase.toFloat(),
            moonIllumination = m.illumination.toFloat(),
        )
    }
}
