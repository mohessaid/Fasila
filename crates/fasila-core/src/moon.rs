//! Moon phase (Jean Meeus, *Astronomical Algorithms*, ch. 47–48, principal terms).
//!
//! Used for the night-sky illustration: the elongation is accurate to a few tenths
//! of a degree, i.e. the drawn phase is correct to well under an hour.

use crate::astro::{delta_t_seconds, norm360, sin_d};
use crate::date::unix_to_jd;

#[cfg(feature = "serde")]
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize))]
pub struct MoonPhase {
    /// 0 = new moon, 0.25 = first quarter, 0.5 = full moon, 0.75 = last quarter.
    pub phase: f64,
    /// Illuminated fraction of the disc, 0..1.
    pub illumination: f64,
    /// Geocentric elongation of the Moon from the Sun, degrees [0, 360).
    pub elongation: f64,
    pub waxing: bool,
}

/// Moon phase at a unix timestamp (seconds, UTC).
pub fn moon_phase(unix: i64) -> MoonPhase {
    let jd_ut = unix_to_jd(unix as f64);
    let jd = jd_ut + delta_t_seconds(jd_ut) / 86_400.0;
    let t = (jd - 2_451_545.0) / 36_525.0;
    // Mean elongation, Sun's and Moon's mean anomalies (Meeus 47.2–47.4).
    let d = norm360(297.850_192_1 + 445_267.111_403_4 * t - 0.001_881_9 * t * t);
    let m = norm360(357.529_109_2 + 35_999.050_290_9 * t - 0.000_153_6 * t * t);
    let mp = norm360(134.963_396_4 + 477_198.867_505_5 * t + 0.008_741_4 * t * t);
    // True elongation = 180° − phase angle i (Meeus 48.4).
    let elongation = norm360(
        d + 6.289 * sin_d(mp) - 2.100 * sin_d(m)
            + 1.274 * sin_d(2.0 * d - mp)
            + 0.658 * sin_d(2.0 * d)
            + 0.214 * sin_d(2.0 * mp)
            + 0.110 * sin_d(d),
    );
    MoonPhase {
        phase: elongation / 360.0,
        illumination: (1.0 - (elongation * crate::astro::DEG).cos()) / 2.0,
        elongation,
        waxing: elongation < 180.0,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::date::Date;

    fn at(d: Date, h: i64, m: i64) -> i64 {
        d.unix_midnight_utc() + h * 3600 + m * 60
    }

    #[test]
    fn known_phases() {
        // New moon 2024-04-08 18:21 UTC (total solar eclipse).
        let p = moon_phase(at(Date::new(2024, 4, 8), 18, 21));
        assert!(p.illumination < 0.001, "{p:?}");
        assert!(p.phase < 0.003 || p.phase > 0.997);
        // Full moon 2024-04-23 23:49 UTC.
        let p = moon_phase(at(Date::new(2024, 4, 23), 23, 49));
        assert!(
            (p.phase - 0.5).abs() < 0.003 && p.illumination > 0.999,
            "{p:?}"
        );
        // First quarter 2024-04-15 19:13 UTC.
        let p = moon_phase(at(Date::new(2024, 4, 15), 19, 13));
        assert!((p.phase - 0.25).abs() < 0.003 && p.waxing, "{p:?}");
        // Last quarter 2024-05-01 11:27 UTC.
        let p = moon_phase(at(Date::new(2024, 5, 1), 11, 27));
        assert!((p.phase - 0.75).abs() < 0.003 && !p.waxing, "{p:?}");
    }
}
