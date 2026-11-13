//! Solar astronomy (Jean Meeus, *Astronomical Algorithms*, 2nd ed.).
//!
//! Precision target: apparent solar position better than ~0.01°, which keeps
//! every computed prayer event within a few seconds of a full ephemeris.
//! Unlike the classic "compute declination once at noon" approach, every event
//! is solved iteratively: the sun's position is re-evaluated at the instant of
//! the event itself until it converges.

use core::f64::consts::PI;

pub const DEG: f64 = PI / 180.0;
/// Sidereal rotation rate, degrees per day of UT.
const SIDEREAL_RATE: f64 = 360.985_647_366_29;

#[inline]
pub fn sin_d(x: f64) -> f64 {
    (x * DEG).sin()
}
#[inline]
pub fn cos_d(x: f64) -> f64 {
    (x * DEG).cos()
}
#[inline]
pub fn tan_d(x: f64) -> f64 {
    (x * DEG).tan()
}
#[inline]
pub fn asin_d(x: f64) -> f64 {
    x.clamp(-1.0, 1.0).asin() / DEG
}
#[inline]
pub fn acos_d(x: f64) -> f64 {
    x.clamp(-1.0, 1.0).acos() / DEG
}
#[inline]
pub fn atan2_d(y: f64, x: f64) -> f64 {
    y.atan2(x) / DEG
}

/// Normalize an angle into [0, 360).
#[inline]
pub fn norm360(x: f64) -> f64 {
    x.rem_euclid(360.0)
}
/// Normalize an angle into [-180, 180).
#[inline]
pub fn norm180(x: f64) -> f64 {
    (x + 180.0).rem_euclid(360.0) - 180.0
}

/// ΔT = TT − UT in seconds (Espenak & Meeus polynomial approximations, 2005–2150).
pub fn delta_t_seconds(jd_ut: f64) -> f64 {
    let year = 2000.0 + (jd_ut - 2_451_545.0) / 365.25;
    if year < 2005.0 {
        // Close enough for historical dates; the effect on prayer times is < 1 s.
        let t = year - 2000.0;
        63.86 + 0.3345 * t - 0.060374 * t * t
    } else if year < 2050.0 {
        let t = year - 2000.0;
        62.92 + 0.32217 * t + 0.005589 * t * t
    } else if year < 2150.0 {
        -20.0 + 32.0 * ((year - 1820.0) / 100.0).powi(2) - 0.5628 * (2150.0 - year)
    } else {
        let u = (year - 1820.0) / 100.0;
        -20.0 + 32.0 * u * u
    }
}

/// Apparent geocentric equatorial coordinates of the Sun.
#[derive(Debug, Clone, Copy)]
pub struct SolarPosition {
    /// Apparent right ascension, degrees [0, 360).
    pub right_ascension: f64,
    /// Apparent declination, degrees.
    pub declination: f64,
    /// Apparent Greenwich sidereal time, degrees [0, 360).
    pub sidereal_time: f64,
    /// Equation of time in minutes (apparent − mean solar time).
    pub equation_of_time: f64,
}

/// Compute the apparent solar position for a Julian Date in UT.
pub fn solar_position(jd_ut: f64) -> SolarPosition {
    let jd_tt = jd_ut + delta_t_seconds(jd_ut) / 86_400.0;
    let t = (jd_tt - 2_451_545.0) / 36_525.0;

    // Geometric mean longitude and mean anomaly (Meeus 25.2, 25.3).
    let l0 = norm360(280.466_46 + t * (36_000.769_83 + t * 0.000_303_2));
    let m = norm360(357.529_11 + t * (35_999.050_29 - t * 0.000_153_7));
    let e = 0.016_708_634 - t * (0.000_042_037 + t * 0.000_000_126_7);

    // Equation of the centre.
    let c = (1.914_602 - t * (0.004_817 + t * 0.000_014)) * sin_d(m)
        + (0.019_993 - t * 0.000_101) * sin_d(2.0 * m)
        + 0.000_289 * sin_d(3.0 * m);
    let true_long = l0 + c;

    // Nutation (Meeus ch. 22, abridged — accurate to ~0.5″).
    let omega = norm360(125.044_52 - 1_934.136_261 * t + 0.002_070_8 * t * t);
    let l_moon = norm360(218.316_5 + 481_267.881_3 * t);
    let d_psi = (-17.20 * sin_d(omega) - 1.32 * sin_d(2.0 * l0) - 0.23 * sin_d(2.0 * l_moon)
        + 0.21 * sin_d(2.0 * omega))
        / 3600.0;
    let d_eps = (9.20 * cos_d(omega) + 0.57 * cos_d(2.0 * l0) + 0.10 * cos_d(2.0 * l_moon)
        - 0.09 * cos_d(2.0 * omega))
        / 3600.0;

    // Apparent longitude: nutation + aberration (−20.4898″ / R).
    let lambda = true_long + d_psi - 20.4898 / 3600.0;

    // Obliquity of the ecliptic (Meeus 22.2) + nutation in obliquity.
    let eps0 =
        23.0 + 26.0 / 60.0 + (21.448 - t * (46.815_0 + t * (0.000_59 - t * 0.001_813))) / 3600.0;
    let eps = eps0 + d_eps;

    let ra = norm360(atan2_d(cos_d(eps) * sin_d(lambda), cos_d(lambda)));
    let dec = asin_d(sin_d(eps) * sin_d(lambda));

    // Greenwich mean sidereal time (Meeus 12.4) in UT, plus equation of the equinoxes.
    let tu = (jd_ut - 2_451_545.0) / 36_525.0;
    let gmst = norm360(
        280.460_618_37
            + 360.985_647_366_29 * (jd_ut - 2_451_545.0)
            + tu * tu * (0.000_387_933 - tu / 38_710_000.0),
    );
    let gast = norm360(gmst + d_psi * cos_d(eps));

    // Equation of time (Meeus 28.1), via mean longitude.
    let eot_deg = norm180(l0 - 0.005_718_3 - ra + d_psi * cos_d(eps));
    let _ = e;

    SolarPosition {
        right_ascension: ra,
        declination: dec,
        sidereal_time: gast,
        equation_of_time: eot_deg * 4.0,
    }
}

/// Local hour angle of the sun (degrees, [-180, 180)) at `jd_ut` for an observer at `lon` (east positive).
pub fn hour_angle(jd_ut: f64, lon: f64) -> (f64, SolarPosition) {
    let p = solar_position(jd_ut);
    (norm180(p.sidereal_time + lon - p.right_ascension), p)
}

/// Solar altitude in degrees (geometric, no refraction) at `jd_ut`.
pub fn solar_altitude(jd_ut: f64, lat: f64, lon: f64) -> f64 {
    let (h, p) = hour_angle(jd_ut, lon);
    asin_d(sin_d(lat) * sin_d(p.declination) + cos_d(lat) * cos_d(p.declination) * cos_d(h))
}

/// Find the instant (JD UT) of solar transit (upper culmination) nearest to `jd_guess`.
pub fn solar_transit(jd_guess: f64, lon: f64) -> f64 {
    let mut jd = jd_guess;
    for _ in 0..8 {
        let (h, _) = hour_angle(jd, lon);
        let step = h / SIDEREAL_RATE;
        jd -= step;
        if step.abs() < 1e-7 {
            break;
        }
    }
    jd
}

/// Which side of the meridian an event happens on.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Side {
    Morning,
    Evening,
}

/// Find the instant (JD UT) when the sun's geometric altitude equals `altitude_fn(declination)`
/// on the given side of the transit `jd_transit`. Returns `None` when the sun never reaches
/// that altitude that day (polar day/night or very high latitudes).
pub fn time_for_altitude<F>(
    jd_transit: f64,
    lat: f64,
    lon: f64,
    side: Side,
    altitude_fn: F,
) -> Option<f64>
where
    F: Fn(f64) -> f64,
{
    let sign = match side {
        Side::Morning => -1.0,
        Side::Evening => 1.0,
    };
    let mut jd = jd_transit;
    let mut last_h0 = None;
    for _ in 0..12 {
        let (h, p) = hour_angle(jd, lon);
        let alt = altitude_fn(p.declination);
        let cos_h0 =
            (sin_d(alt) - sin_d(lat) * sin_d(p.declination)) / (cos_d(lat) * cos_d(p.declination));
        if !(-1.0..=1.0).contains(&cos_h0) {
            // Event does not happen with the declination at this instant. If we have already
            // converged close by, the event is marginal; otherwise it really does not occur.
            return None;
        }
        let h0 = acos_d(cos_h0);
        let target = sign * h0;
        let diff = norm180(target - h);
        jd += diff / SIDEREAL_RATE;
        if diff.abs() < 1e-6 {
            last_h0 = Some(h0);
            break;
        }
        last_h0 = Some(h0);
    }
    last_h0.map(|_| jd)
}

/// Great-circle initial bearing (degrees from true north, clockwise) from one point to another.
pub fn initial_bearing(lat1: f64, lon1: f64, lat2: f64, lon2: f64) -> f64 {
    let dlon = lon2 - lon1;
    norm360(atan2_d(
        sin_d(dlon),
        cos_d(lat1) * tan_d(lat2) - sin_d(lat1) * cos_d(dlon),
    ))
}

/// Great-circle distance in kilometres on a spherical Earth (haversine, R = 6371.0088 km).
pub fn distance_km(lat1: f64, lon1: f64, lat2: f64, lon2: f64) -> f64 {
    let dlat = (lat2 - lat1) * DEG;
    let dlon = (lon2 - lon1) * DEG;
    let a = (dlat / 2.0).sin().powi(2) + cos_d(lat1) * cos_d(lat2) * (dlon / 2.0).sin().powi(2);
    2.0 * 6_371.008_8 * a.sqrt().asin()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn meeus_example_25a() {
        // Meeus example 25.a: 1992 Oct 13.0 TD → apparent RA 198.38083°, dec −7.78507°.
        let jd_td = 2_448_908.5;
        let jd_ut = jd_td - delta_t_seconds(jd_td) / 86_400.0;
        // ΔT in 1992 was ~59 s; our polynomial gives a similar value.
        let p = solar_position(jd_ut);
        assert!(
            (p.right_ascension - 198.380_83).abs() < 0.005,
            "ra {}",
            p.right_ascension
        );
        assert!(
            (p.declination - -7.785_07).abs() < 0.005,
            "dec {}",
            p.declination
        );
    }

    #[test]
    fn equation_of_time_example_28a() {
        // Meeus example 28.a: 1992 Oct 13.0 TD, E = 13m 42.6s.
        let p = solar_position(2_448_908.5);
        assert!(
            (p.equation_of_time - 13.71).abs() < 0.05,
            "eot {}",
            p.equation_of_time
        );
    }

    #[test]
    fn qibla_from_new_york() {
        let b = initial_bearing(40.7128, -74.0060, 21.422_487, 39.826_206);
        assert!((b - 58.48).abs() < 0.1, "bearing {b}");
    }
}
