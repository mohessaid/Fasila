package app.fasila.core

/**
 * The living sky (docs/DESIGN.md): one palette per prayer period, cross-faded over
 * ~20 minutes centred on each boundary.
 */
enum class Period(val top: Long, val bottom: Long, val accent: Long, val isLight: Boolean) {
    NIGHT(0xFF0B1026, 0xFF1B2350, 0xFF8EA2FF, false),
    FAJR(0xFF1E2A5A, 0xFFC77D8E, 0xFFFFB4A2, false),
    MORNING(0xFF5FA8D3, 0xFFCDE7F0, 0xFFFFD166, true),
    DHUHR(0xFF3A86C8, 0xFFA9D6F5, 0xFFFFE08A, true),
    ASR(0xFFD98E4A, 0xFFF4D29C, 0xFFFFF1C9, true),
    MAGHRIB(0xFF5B2A63, 0xFFE0735A, 0xFFFFB38A, false),
}

/** Blend [fraction] of the way from [from] to [to]; `from == to` outside transitions. */
data class SkyState(val from: Period, val to: Period, val fraction: Float) {
    /** The period that dominates right now (for text contrast decisions). */
    val dominant: Period get() = if (fraction < 0.5f) from else to

    companion object {
        val Default = SkyState(Period.NIGHT, Period.NIGHT, 0f)
        private const val HALF_FADE = 10 * 60L

        fun at(state: TodayState, reduceMotion: Boolean = false): SkyState {
            val y = state.yesterday
            val t = state.today
            val tm = state.tomorrow
            val bounds = listOfNotNull(
                y.isha?.let { it to Period.NIGHT },
                t.fajr?.let { it to Period.FAJR },
                t.sunrise?.let { it to Period.MORNING },
                t.dhuhr?.let { it to Period.DHUHR },
                t.asr?.let { it to Period.ASR },
                t.maghrib?.let { it to Period.MAGHRIB },
                t.isha?.let { it to Period.NIGHT },
                tm.fajr?.let { it to Period.FAJR },
            ).sortedBy { it.first }
            if (bounds.isEmpty()) return Default
            val now = state.now
            val i = bounds.indexOfLast { it.first <= now }
            if (i < 0) return SkyState(Period.NIGHT, Period.NIGHT, 0f)
            val (start, period) = bounds[i]
            if (reduceMotion) return SkyState(period, period, 0f)
            val prev = bounds.getOrNull(i - 1)?.second
            val next = bounds.getOrNull(i + 1)
            return when {
                prev != null && now - start < HALF_FADE ->
                    SkyState(prev, period, 0.5f + (now - start).toFloat() / (2 * HALF_FADE))
                next != null && next.first - now < HALF_FADE ->
                    SkyState(period, next.second, (now - (next.first - HALF_FADE)).toFloat() / (2 * HALF_FADE))
                else -> SkyState(period, period, 0f)
            }
        }
    }
}
