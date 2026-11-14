//! The living sky (docs/DESIGN.md), shared by every native app.
//!
//! Position in the prayer day (`progress`, 0 at Fajr -> 1 at the next Fajr) is pinned to
//! the day's real prayer times, so colours and stars follow the actual sun in every
//! season and at every latitude. The desktop app mirrors this in `apps/desktop/src/lib/sky.ts`.

use crate::prayer::PrayerTimes;

#[cfg(feature = "serde")]
use serde::{Deserialize, Serialize};

/// Four gradient stops, top -> bottom, as 0xRRGGBB.
pub type Stops = [u32; 4];

const NIGHT: Stops = [0x010208, 0x020510, 0x040A1B, 0x07132B];
const KEYFRAMES: [(f64, Stops); 8] = [
    (0.0, [0x07152B, 0x163557, 0x5B5A76, 0xD08B83]), // Fajr, first light
    (0.08, [0x173B61, 0x5A7F9B, 0xD49C91, 0xF5C4A2]), // sunrise glow
    (0.18, [0x0B315A, 0x286B9E, 0x7DB8C5, 0xD5D6C5]), // morning
    (0.52, [0x0D3967, 0x347DA8, 0x8BC6CF, 0xE3D9C8]), // afternoon
    (0.72, [0x173454, 0x516D8C, 0xB67A78, 0xE8B38F]), // late Asr
    (0.8, [0x0E1530, 0x30234A, 0x75425B, 0xD47E68]), // Maghrib
    (0.9, [0x050817, 0x0B1230, 0x171A45, 0x28234D]), // Isha
    (1.0, NIGHT),
];

/// Where each event sits in the canonical prayer day.
type EventOf = fn(&PrayerTimes) -> Option<i64>;

const ANCHORS: [(EventOf, f64); 6] = [
    (|t| t.fajr, 0.0),
    (|t| t.sunrise, 0.08),
    (|t| t.dhuhr, 0.35),
    (|t| t.asr, 0.62),
    (|t| t.maghrib, 0.8),
    (|t| t.isha, 0.9),
];

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[cfg_attr(
    feature = "serde",
    derive(Serialize, Deserialize),
    serde(rename_all = "snake_case")
)]
pub enum TimeOfDay {
    Night,
    Dawn,
    Day,
    Dusk,
}

#[derive(Debug, Clone, PartialEq)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize))]
pub struct Sky {
    /// 0 at Fajr -> 1 at the next Fajr, pinned to the day's real prayer times.
    pub progress: f64,
    /// 0 at sunrise -> 1 at Maghrib: the sun's place on its arc (clamped).
    pub sun: f64,
    pub is_daytime: bool,
    pub time_of_day: TimeOfDay,
    pub colors: Stops,
    /// Opacity of the star field, 0..1 (stars only after sunset).
    pub star_opacity: f64,
    /// Opacity of the dark `#060A14` tint under glass panels, so text keeps WCAG AA
    /// however bright the sky is. Apply one value to every panel.
    pub glass_tint: f64,
}

fn warp(day: &PrayerTimes, next_fajr: i64, now: i64) -> f64 {
    let mut pts: Vec<(i64, f64)> = ANCHORS
        .iter()
        .filter_map(|(f, p)| f(day).map(|t| (t, *p)))
        .collect();
    pts.push((next_fajr, 1.0));
    if now <= pts[0].0 {
        return 0.0;
    }
    for w in pts.windows(2) {
        let ((t0, p0), (t1, p1)) = (w[0], w[1]);
        if now < t1 {
            return p0 + (now - t0) as f64 / (t1 - t0).max(1) as f64 * (p1 - p0);
        }
    }
    1.0
}

fn channel(c: u32, shift: u32) -> f64 {
    ((c >> shift) & 0xFF) as f64
}

fn mix(a: Stops, b: Stops, t: f64) -> Stops {
    let mut out = [0u32; 4];
    for i in 0..4 {
        let mut c = 0u32;
        for shift in [16, 8, 0] {
            let v = channel(a[i], shift) + (channel(b[i], shift) - channel(a[i], shift)) * t;
            c |= (v.round().clamp(0.0, 255.0) as u32) << shift;
        }
        out[i] = c;
    }
    out
}

/// Sky colours at a position in the prayer day.
pub fn colors(progress: f64, tod: TimeOfDay) -> Stops {
    if tod == TimeOfDay::Night {
        return NIGHT;
    }
    let p = progress.clamp(0.0, 1.0);
    let i = KEYFRAMES
        .iter()
        .position(|(at, _)| *at >= p)
        .unwrap_or(KEYFRAMES.len() - 1);
    if i == 0 {
        return KEYFRAMES[0].1;
    }
    let (a, b) = (KEYFRAMES[i - 1], KEYFRAMES[i]);
    mix(a.1, b.1, (p - a.0) / (b.0 - a.0))
}

/// Stars fade out from Fajr to just after sunrise and appear only after sunset.
pub fn star_opacity(p: f64) -> f64 {
    if p < 0.08 {
        0.65 - p / 0.08 * 0.45
    } else if p < 0.18 {
        0.2 - (p - 0.08) / 0.1 * 0.2
    } else if p < 0.8 {
        0.0
    } else if p < 0.9 {
        (p - 0.8) / 0.1 * 0.85
    } else {
        0.85 + ((p - 0.9) / 0.1 * 0.15).min(0.15)
    }
}

/// WCAG relative luminance of 0xRRGGBB.
pub fn luminance(c: u32) -> f64 {
    let f = |v: f64| {
        let x = v / 255.0;
        if x <= 0.039_28 {
            x / 12.92
        } else {
            ((x + 0.055) / 1.055).powf(2.4)
        }
    };
    0.2126 * f(channel(c, 16)) + 0.7152 * f(channel(c, 8)) + 0.0722 * f(channel(c, 0))
}

/// Tint opacity for glass panels: set by the sky's brightest lower stops (measured: a
/// fixed 0.34 tint fails AA on morning skies, 1.6-3.1:1).
pub fn glass_tint(stops: Stops) -> f64 {
    let a = |l: f64| (0.36 + (l - 0.02).max(0.0) * 0.8).min(0.84);
    stops[1..]
        .iter()
        .map(|c| a(luminance(*c)))
        .fold(0.0, f64::max)
}

/// The sky at `now`, from yesterday's, today's and tomorrow's prayer times.
pub fn sky_at(
    yesterday: &PrayerTimes,
    today: &PrayerTimes,
    tomorrow: &PrayerTimes,
    now: i64,
) -> Sky {
    let fajr = today.fajr;
    let before = fajr.is_some_and(|f| now < f);
    let progress = match (before, fajr) {
        (true, Some(f)) => warp(yesterday, f, now),
        (false, Some(f)) => warp(today, tomorrow.fajr.unwrap_or(f + 86_400), now),
        _ => 0.95,
    };
    let sun = match (today.sunrise, today.maghrib) {
        (Some(sr), Some(mg)) if mg > sr => (now - sr) as f64 / (mg - sr) as f64,
        _ => -1.0,
    };
    let is_daytime = (0.0..=1.0).contains(&sun);
    let time_of_day = if fajr.is_none() || before || today.isha.is_some_and(|i| now >= i) {
        TimeOfDay::Night
    } else if today.sunrise.is_some_and(|s| now < s) {
        TimeOfDay::Dawn
    } else if today.maghrib.is_some_and(|m| now >= m) {
        TimeOfDay::Dusk
    } else {
        TimeOfDay::Day
    };
    let colors = colors(progress, time_of_day);
    Sky {
        progress,
        sun: sun.clamp(0.0, 1.0),
        is_daytime,
        time_of_day,
        colors,
        star_opacity: star_opacity(progress),
        glass_tint: glass_tint(colors),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{compute, Coordinates, Date, Method, Params};

    fn days() -> [PrayerTimes; 3] {
        let c = Coordinates::new(36.7538, 3.0588);
        let p = Params::for_method(Method::Algeria);
        let d = Date::new(2026, 11, 14);
        [
            compute(d.add_days(-1), c, &p),
            compute(d, c, &p),
            compute(d.add_days(1), c, &p),
        ]
    }

    #[test]
    fn pinned_to_prayer_times() {
        let [y, t, n] = days();
        let at = |ts| sky_at(&y, &t, &n, ts);
        assert!((at(t.fajr.unwrap()).progress).abs() < 1e-9);
        assert!((at(t.maghrib.unwrap()).progress - 0.8).abs() < 1e-9);
        assert_eq!(at(t.isha.unwrap() + 60).time_of_day, TimeOfDay::Night);
        assert_eq!(at(t.dhuhr.unwrap()).time_of_day, TimeOfDay::Day);
        assert!(at(t.dhuhr.unwrap()).is_daytime);
        // No stars by day, stars after Isha.
        assert_eq!(at(t.dhuhr.unwrap()).star_opacity, 0.0);
        assert!(at(t.isha.unwrap() + 3600).star_opacity > 0.8);
    }

    #[test]
    fn tint_darkens_on_bright_skies() {
        let [y, t, n] = days();
        let night = sky_at(&y, &t, &n, t.isha.unwrap() + 3600).glass_tint;
        let morning = sky_at(&y, &t, &n, t.sunrise.unwrap() + 5400).glass_tint;
        assert!(
            night < 0.4 && morning > 0.7,
            "night {night} morning {morning}"
        );
    }
}
