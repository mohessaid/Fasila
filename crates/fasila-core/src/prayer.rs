//! Prayer time computation.

use crate::astro::{self, Side};
use crate::date::{jd_to_unix, Date};
use crate::hijri::HijriCalendar;
use crate::method::{
    HighLatitudeRule, Method, MidnightMethod, Params, PolarRule, Rounding, Twilight,
};

#[cfg(feature = "serde")]
use serde::{Deserialize, Serialize};

/// Geographic coordinates (degrees, east and north positive).
#[derive(Debug, Clone, Copy, PartialEq)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize))]
pub struct Coordinates {
    pub latitude: f64,
    pub longitude: f64,
}

impl Coordinates {
    pub const fn new(latitude: f64, longitude: f64) -> Self {
        Coordinates {
            latitude,
            longitude,
        }
    }
    pub fn is_valid(&self) -> bool {
        self.latitude.is_finite()
            && self.longitude.is_finite()
            && (-90.0..=90.0).contains(&self.latitude)
            && (-180.0..=180.0).contains(&self.longitude)
    }
}

/// Every named instant of a prayer day. Prayer-anchored schedules refer to these.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, PartialOrd, Ord)]
#[cfg_attr(
    feature = "serde",
    derive(Serialize, Deserialize),
    serde(rename_all = "snake_case")
)]
pub enum TimeKey {
    Imsak,
    Fajr,
    Sunrise,
    Dhuhr,
    Asr,
    Sunset,
    Maghrib,
    Isha,
    Midnight,
    LastThird,
}

impl TimeKey {
    pub const ALL: [TimeKey; 10] = [
        TimeKey::Imsak,
        TimeKey::Fajr,
        TimeKey::Sunrise,
        TimeKey::Dhuhr,
        TimeKey::Asr,
        TimeKey::Sunset,
        TimeKey::Maghrib,
        TimeKey::Isha,
        TimeKey::Midnight,
        TimeKey::LastThird,
    ];
    /// The five obligatory prayers.
    pub const PRAYERS: [TimeKey; 5] = [
        TimeKey::Fajr,
        TimeKey::Dhuhr,
        TimeKey::Asr,
        TimeKey::Maghrib,
        TimeKey::Isha,
    ];

    pub fn is_prayer(self) -> bool {
        Self::PRAYERS.contains(&self)
    }

    pub fn id(self) -> &'static str {
        match self {
            TimeKey::Imsak => "imsak",
            TimeKey::Fajr => "fajr",
            TimeKey::Sunrise => "sunrise",
            TimeKey::Dhuhr => "dhuhr",
            TimeKey::Asr => "asr",
            TimeKey::Sunset => "sunset",
            TimeKey::Maghrib => "maghrib",
            TimeKey::Isha => "isha",
            TimeKey::Midnight => "midnight",
            TimeKey::LastThird => "last_third",
        }
    }

    pub fn from_id(s: &str) -> Option<TimeKey> {
        TimeKey::ALL.iter().copied().find(|k| k.id() == s)
    }
}

/// Computed times for one civil date, as unix timestamps (UTC seconds).
#[derive(Debug, Clone, PartialEq)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize))]
pub struct PrayerTimes {
    pub date: Date,
    pub imsak: Option<i64>,
    pub fajr: Option<i64>,
    pub sunrise: Option<i64>,
    pub dhuhr: Option<i64>,
    pub asr: Option<i64>,
    pub sunset: Option<i64>,
    pub maghrib: Option<i64>,
    pub isha: Option<i64>,
    /// End of the preferred time for Isha.
    pub midnight: Option<i64>,
    /// Start of the last third of the night (Tahajjud / Qiyam).
    pub last_third: Option<i64>,
    /// Fajr was set by the high-latitude rule rather than by the twilight angle.
    pub fajr_estimated: bool,
    /// Isha was set by the high-latitude rule rather than by the twilight angle.
    pub isha_estimated: bool,
    /// Sun does not rise/set here today; times use the nearest latitude where it does.
    pub polar_fallback: bool,
}

impl PrayerTimes {
    pub fn get(&self, key: TimeKey) -> Option<i64> {
        match key {
            TimeKey::Imsak => self.imsak,
            TimeKey::Fajr => self.fajr,
            TimeKey::Sunrise => self.sunrise,
            TimeKey::Dhuhr => self.dhuhr,
            TimeKey::Asr => self.asr,
            TimeKey::Sunset => self.sunset,
            TimeKey::Maghrib => self.maghrib,
            TimeKey::Isha => self.isha,
            TimeKey::Midnight => self.midnight,
            TimeKey::LastThird => self.last_third,
        }
    }

    /// The obligatory prayer whose time is current at `now`, if any (Fajr ends at sunrise).
    pub fn current_prayer(&self, now: i64) -> Option<TimeKey> {
        let after = |t: Option<i64>| t.is_some_and(|t| now >= t);
        if after(self.isha) {
            Some(TimeKey::Isha)
        } else if after(self.maghrib) {
            Some(TimeKey::Maghrib)
        } else if after(self.asr) {
            Some(TimeKey::Asr)
        } else if after(self.dhuhr) {
            Some(TimeKey::Dhuhr)
        } else if after(self.sunrise) {
            None
        } else if after(self.fajr) {
            Some(TimeKey::Fajr)
        } else {
            None
        }
    }

    /// The next obligatory prayer today strictly after `now`.
    pub fn next_prayer(&self, now: i64) -> Option<(TimeKey, i64)> {
        TimeKey::PRAYERS
            .iter()
            .filter_map(|&k| self.get(k).map(|t| (k, t)))
            .find(|&(_, t)| t > now)
    }
}

/// Unrounded solar events for a day, as JD (UT).
#[derive(Debug, Clone, Copy)]
struct SunDay {
    transit: f64,
    sunrise: f64,
    sunset: f64,
    /// Latitude actually used (differs from the observer's under the polar rule).
    lat: f64,
    polar: bool,
}

fn horizon(params: &Params) -> f64 {
    // Refraction (34′) + solar semi-diameter (16′), plus horizon dip for elevation.
    -(0.833_3 + 0.034_7 * params.elevation_m.max(0.0).sqrt())
}

fn sun_day(date: Date, c: Coordinates, params: &Params) -> Option<SunDay> {
    let guess = date.jd_midnight() + 0.5 - c.longitude / 360.0;
    let transit = astro::solar_transit(guess, c.longitude);
    let h = horizon(params);
    let try_lat = |lat: f64| {
        let rise = astro::time_for_altitude(transit, lat, c.longitude, Side::Morning, |_| h)?;
        let set = astro::time_for_altitude(transit, lat, c.longitude, Side::Evening, |_| h)?;
        Some((rise, set))
    };
    if let Some((sunrise, sunset)) = try_lat(c.latitude) {
        return Some(SunDay {
            transit,
            sunrise,
            sunset,
            lat: c.latitude,
            polar: false,
        });
    }
    if params.polar_rule == PolarRule::Unresolved {
        return None;
    }
    // Aqrab al-bilād: walk toward the equator from 65° until the sun rises and sets.
    let sign = c.latitude.signum();
    let mut lat = 65.0_f64.min(c.latitude.abs());
    while lat > 0.0 {
        if let Some((sunrise, sunset)) = try_lat(sign * lat) {
            return Some(SunDay {
                transit,
                sunrise,
                sunset,
                lat: sign * lat,
                polar: true,
            });
        }
        lat -= 0.5;
    }
    None
}

fn twilight(sd: &SunDay, c: Coordinates, angle: f64, side: Side) -> Option<f64> {
    astro::time_for_altitude(sd.transit, sd.lat, c.longitude, side, |_| -angle)
}

fn night_portion(rule: HighLatitudeRule, angle: f64) -> Option<f64> {
    match rule {
        HighLatitudeRule::MiddleOfTheNight => Some(0.5),
        HighLatitudeRule::SeventhOfTheNight => Some(1.0 / 7.0),
        HighLatitudeRule::TwilightAngle => Some(angle / 60.0),
        HighLatitudeRule::None | HighLatitudeRule::Auto => None,
    }
}

fn days_since_solstice(date: Date, lat: f64) -> f64 {
    let doy = date.day_of_year() as i32;
    let days_in_year = if Date::is_leap_year(date.year) {
        366
    } else {
        365
    };
    if lat >= 0.0 {
        let mut d = doy + 10;
        if d >= days_in_year {
            d -= days_in_year;
        }
        d as f64
    } else {
        let southern = if Date::is_leap_year(date.year) {
            173
        } else {
            172
        };
        let mut d = doy - southern;
        if d < 0 {
            d += days_in_year;
        }
        d as f64
    }
}

fn seasonal(a: f64, b: f64, c: f64, d: f64, dyy: f64) -> f64 {
    if dyy < 91.0 {
        a + (b - a) / 91.0 * dyy
    } else if dyy < 137.0 {
        b + (c - b) / 46.0 * (dyy - 91.0)
    } else if dyy < 183.0 {
        c + (d - c) / 46.0 * (dyy - 137.0)
    } else if dyy < 229.0 {
        d + (c - d) / 46.0 * (dyy - 183.0)
    } else if dyy < 275.0 {
        c + (b - c) / 46.0 * (dyy - 229.0)
    } else {
        b + (a - b) / 91.0 * (dyy - 275.0)
    }
}

/// Moonsighting Committee seasonal Fajr: minutes before sunrise.
fn mc_fajr_minutes(date: Date, lat: f64) -> f64 {
    let l = lat.abs();
    let dyy = days_since_solstice(date, lat);
    seasonal(
        75.0 + 28.65 / 55.0 * l,
        75.0 + 19.44 / 55.0 * l,
        75.0 + 32.74 / 55.0 * l,
        75.0 + 48.10 / 55.0 * l,
        dyy,
    )
}

/// Moonsighting Committee seasonal Isha (general shafaq): minutes after sunset.
fn mc_isha_minutes(date: Date, lat: f64) -> f64 {
    let l = lat.abs();
    let dyy = days_since_solstice(date, lat);
    seasonal(
        75.0 + 25.60 / 55.0 * l,
        75.0 + 2.050 / 55.0 * l,
        75.0 - 9.21 / 55.0 * l,
        75.0 + 6.14 / 55.0 * l,
        dyy,
    )
}

const MIN: f64 = 1.0 / 1440.0;

/// Fajr (JD) for `date`, with its high-latitude safeguards. `prev_sunset` bounds the night.
fn fajr_jd(date: Date, sd: &SunDay, prev_sunset: f64, c: Coordinates, p: &Params) -> (f64, bool) {
    let night = sd.sunrise - prev_sunset;
    let angle_based = twilight(sd, c, p.fajr_angle, Side::Morning);
    if p.method == Method::MoonsightingCommittee {
        let safe = if sd.lat.abs() >= 55.0 {
            sd.sunrise - night / 7.0
        } else {
            sd.sunrise - mc_fajr_minutes(date, sd.lat) * MIN
        };
        return match angle_based {
            Some(f) if f >= safe => (f, false),
            _ => (safe, true),
        };
    }
    let rule = p.effective_high_latitude_rule(sd.lat);
    match (angle_based, night_portion(rule, p.fajr_angle)) {
        (Some(f), Some(portion)) => {
            let safe = sd.sunrise - portion * night;
            if f < safe {
                (safe, true)
            } else {
                (f, false)
            }
        }
        (Some(f), None) => (f, false),
        (None, Some(portion)) => (sd.sunrise - portion * night, true),
        (None, None) => (f64::NAN, true),
    }
}

fn is_umm_al_qura_ramadan(date: Date) -> bool {
    HijriCalendar::UmmAlQura.from_jdn(date.jdn()).month == 9
}

fn to_unix(jd: f64) -> f64 {
    jd_to_unix(jd)
}

#[derive(Clone, Copy)]
enum Dir {
    Up,
    Down,
}

fn round(ts: f64, rounding: Rounding, dir: Dir) -> i64 {
    match rounding {
        Rounding::None => ts.round() as i64,
        Rounding::Nearest => ((ts / 60.0).round() * 60.0) as i64,
        Rounding::Safe => match dir {
            Dir::Up => ((ts / 60.0).ceil() * 60.0) as i64,
            Dir::Down => ((ts / 60.0).floor() * 60.0) as i64,
        },
    }
}

/// Compute prayer times for a civil date at a location.
///
/// `date` is the *local* civil date; all results are UTC unix timestamps so the
/// caller can render them in any time zone (DST transitions are handled by the
/// platform's time-zone database, never by this crate).
pub fn compute(date: Date, coords: Coordinates, params: &Params) -> PrayerTimes {
    let empty = PrayerTimes {
        date,
        imsak: None,
        fajr: None,
        sunrise: None,
        dhuhr: None,
        asr: None,
        sunset: None,
        maghrib: None,
        isha: None,
        midnight: None,
        last_third: None,
        fajr_estimated: false,
        isha_estimated: false,
        polar_fallback: false,
    };
    if !coords.is_valid() {
        return empty;
    }
    let (Some(today), Some(yesterday), Some(tomorrow)) = (
        sun_day(date, coords, params),
        sun_day(date.add_days(-1), coords, params),
        sun_day(date.add_days(1), coords, params),
    ) else {
        // Polar rule "unresolved": only Dhuhr (solar noon) is defined.
        let guess = date.jd_midnight() + 0.5 - coords.longitude / 360.0;
        let transit = astro::solar_transit(guess, coords.longitude);
        let adj = (params.method_adjustments.dhuhr + params.adjustments.dhuhr) * 60.0;
        return PrayerTimes {
            dhuhr: Some(round(to_unix(transit) + adj, params.rounding, Dir::Up)),
            polar_fallback: true,
            ..empty
        };
    };

    let lat = today.lat;
    let c = coords;

    // Fajr (today and tomorrow — tomorrow's bounds tonight's midnight/last third).
    let (fajr, fajr_estimated) = fajr_jd(date, &today, yesterday.sunset, c, params);
    let (fajr_next, _) = fajr_jd(date.add_days(1), &tomorrow, today.sunset, c, params);

    // Asr: shadow length = factor × object + the shadow at zawāl (solar noon).
    // The noon shadow is fixed by the declination *at noon*, so the target altitude is constant.
    let factor = params.madhab.shadow_factor();
    let noon_dec = astro::solar_position(today.transit).declination;
    let asr_alt = (1.0 / (factor + astro::tan_d((lat - noon_dec).abs()))).atan() / astro::DEG;
    let asr = astro::time_for_altitude(today.transit, lat, c.longitude, Side::Evening, |_| asr_alt);

    // Maghrib.
    let maghrib = match params.maghrib {
        Twilight::Minutes(m) => Some(today.sunset + m * MIN),
        Twilight::Angle(a) => twilight(&today, c, a, Side::Evening),
    };

    // Isha.
    let night = tomorrow.sunrise - today.sunset;
    let (isha, isha_estimated) = match params.isha {
        Twilight::Minutes(m) => {
            let m = match params.isha_ramadan_minutes {
                Some(r) if is_umm_al_qura_ramadan(date) => r,
                _ => m,
            };
            (maghrib.map(|mg| mg + m * MIN), false)
        }
        Twilight::Angle(angle) => {
            let angle_based = twilight(&today, c, angle, Side::Evening);
            if params.method == Method::MoonsightingCommittee {
                let safe = if lat.abs() >= 55.0 {
                    today.sunset + night / 7.0
                } else {
                    today.sunset + mc_isha_minutes(date, lat) * MIN
                };
                match angle_based {
                    Some(i) if i <= safe => (Some(i), false),
                    _ => (Some(safe), true),
                }
            } else {
                let rule = params.effective_high_latitude_rule(lat);
                match (angle_based, night_portion(rule, angle)) {
                    (Some(i), Some(portion)) => {
                        let safe = today.sunset + portion * night;
                        if i > safe {
                            (Some(safe), true)
                        } else {
                            (Some(i), false)
                        }
                    }
                    (Some(i), None) => (Some(i), false),
                    (None, Some(portion)) => (Some(today.sunset + portion * night), true),
                    (None, None) => (None, true),
                }
            }
        }
    };

    let midnight = match params.midnight {
        MidnightMethod::Standard => today.sunset + night / 2.0,
        MidnightMethod::Jafari => today.sunset + (fajr_next - today.sunset) / 2.0,
    };
    let last_third = today.sunset + (fajr_next - today.sunset) * 2.0 / 3.0;

    let ma = &params.method_adjustments;
    let ua = &params.adjustments;
    let r = params.rounding;
    let fin = |jd: f64, adj_min: f64, dir: Dir| -> Option<i64> {
        jd.is_finite()
            .then(|| round(to_unix(jd) + adj_min * 60.0, r, dir))
    };

    let fajr_t = fin(fajr, ma.fajr + ua.fajr, Dir::Up);
    let imsak_raw = fajr - params.imsak_minutes * MIN;
    PrayerTimes {
        date,
        imsak: fin(imsak_raw, ma.fajr + ua.fajr, Dir::Down),
        fajr: fajr_t,
        sunrise: fin(today.sunrise, ma.sunrise + ua.sunrise, Dir::Down),
        dhuhr: fin(today.transit, ma.dhuhr + ua.dhuhr, Dir::Up),
        asr: asr.and_then(|a| fin(a, ma.asr + ua.asr, Dir::Up)),
        sunset: fin(today.sunset, 0.0, Dir::Up),
        maghrib: maghrib.and_then(|m| fin(m, ma.maghrib + ua.maghrib, Dir::Up)),
        isha: isha.and_then(|i| fin(i, ma.isha + ua.isha, Dir::Up)),
        midnight: fin(midnight, 0.0, Dir::Down),
        last_third: fin(last_third, 0.0, Dir::Up),
        fajr_estimated,
        isha_estimated,
        polar_fallback: today.polar,
    }
}

/// Qibla: initial great-circle bearing from `coords` to the Kaaba, degrees from true north.
pub fn qibla(coords: Coordinates) -> f64 {
    astro::initial_bearing(
        coords.latitude,
        coords.longitude,
        KAABA.latitude,
        KAABA.longitude,
    )
}

/// Distance to the Kaaba in kilometres.
pub fn distance_to_kaaba_km(coords: Coordinates) -> f64 {
    astro::distance_km(
        coords.latitude,
        coords.longitude,
        KAABA.latitude,
        KAABA.longitude,
    )
}

pub const KAABA: Coordinates = Coordinates::new(21.422_487, 39.826_206);

#[cfg(test)]
mod tests {
    use super::*;
    use crate::method::Madhab;

    fn hm(ts: i64, offset_h: f64) -> String {
        let local = ts + (offset_h * 3600.0) as i64;
        let s = local.rem_euclid(86_400);
        format!("{:02}:{:02}", s / 3600, (s % 3600) / 60)
    }

    #[test]
    fn makkah_umm_al_qura() {
        // Makkah, 2024-06-21 (UTC+3). Regression values; every event was verified against the
        // Astropy/ERFA solar ephemeris to within ±2 s by `tools/verify/verify.py`.
        let mut p = Params::for_method(Method::UmmAlQura);
        p.rounding = Rounding::Nearest;
        let t = compute(Date::new(2024, 6, 21), KAABA, &p);
        assert_eq!(hm(t.dhuhr.unwrap(), 3.0), "12:23");
        assert_eq!(hm(t.sunrise.unwrap(), 3.0), "05:39");
        assert_eq!(hm(t.maghrib.unwrap(), 3.0), "19:06");
        assert_eq!(hm(t.isha.unwrap(), 3.0), "20:36");
        assert_eq!(hm(t.asr.unwrap(), 3.0), "15:42");
        assert_eq!(hm(t.fajr.unwrap(), 3.0), "04:11");
    }

    #[test]
    fn umm_al_qura_ramadan_isha_120() {
        let p = Params::for_method(Method::UmmAlQura);
        // 2024-03-20 is in Ramadan 1445.
        let t = compute(Date::new(2024, 3, 20), KAABA, &p);
        let gap = t.isha.unwrap() - t.maghrib.unwrap();
        assert!((7140..=7260).contains(&gap), "gap {gap}");
    }

    #[test]
    fn hanafi_asr_is_later() {
        let c = Coordinates::new(24.8607, 67.0011);
        let mut p = Params::for_method(Method::Karachi);
        let std = compute(Date::new(2025, 1, 15), c, &p).asr.unwrap();
        p.madhab = Madhab::Hanafi;
        let han = compute(Date::new(2025, 1, 15), c, &p).asr.unwrap();
        assert!(han - std > 30 * 60);
    }

    #[test]
    fn ordering_invariants_worldwide() {
        let mut d = Date::new(2026, 1, 1);
        let places = [
            (21.42, 39.83),
            (36.75, 3.06),
            (51.51, -0.13),
            (59.33, 18.07),
            (-33.87, 151.21),
            (40.71, -74.0),
            (64.15, -21.94),
            (-54.8, -68.3),
            (1.35, 103.82),
        ];
        let p = Params::default();
        for _ in 0..73 {
            for &(lat, lon) in &places {
                let t = compute(d, Coordinates::new(lat, lon), &p);
                let seq = [
                    t.fajr, t.sunrise, t.dhuhr, t.asr, t.maghrib, t.isha, t.midnight,
                ];
                let seq: Vec<i64> = seq
                    .iter()
                    .map(|x| x.unwrap_or_else(|| panic!("{d} {lat} {:?}", t)))
                    .collect();
                for w in seq.windows(2) {
                    assert!(w[0] <= w[1], "order broken at {d} ({lat},{lon}): {:?}", t);
                }
            }
            d = d.add_days(5);
        }
    }

    #[test]
    fn polar_fallback_resolves() {
        // Tromsø in December: polar night.
        let t = compute(
            Date::new(2025, 12, 21),
            Coordinates::new(69.65, 18.96),
            &Params::default(),
        );
        assert!(t.polar_fallback);
        assert!(t.fajr.is_some() && t.isha.is_some() && t.sunrise.is_some());
        let p = Params {
            polar_rule: PolarRule::Unresolved,
            ..Params::default()
        };
        let t = compute(Date::new(2025, 12, 21), Coordinates::new(69.65, 18.96), &p);
        assert!(t.fajr.is_none() && t.dhuhr.is_some());
    }

    #[test]
    fn qibla_known() {
        // Great-circle initial bearing. Algiers → 105.41°, London → 118.99°.
        assert!((qibla(Coordinates::new(36.7538, 3.0588)) - 105.41).abs() < 0.02);
        assert!((qibla(Coordinates::new(51.5074, -0.1278)) - 118.99).abs() < 0.2);
    }
}
