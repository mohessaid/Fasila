//! Prayer-anchored scheduling.
//!
//! Every alarm, task and event in Fasila is expressed relative to a prayer
//! ("15 min before Fajr", "30 min after Asr until 10 min before Maghrib"), never
//! as a bare clock time. Occurrences therefore move with the sun every day, and
//! the engine can check that nothing you schedule swallows a prayer.

use crate::date::{Date, Weekday};
use crate::hijri::{HijriDate, HijriProfile};
use crate::method::Params;
use crate::prayer::{compute, Coordinates, PrayerTimes, TimeKey};

#[cfg(feature = "serde")]
use serde::{Deserialize, Serialize};

/// A point in time defined relative to a prayer-day event.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize))]
pub struct Anchor {
    pub key: TimeKey,
    /// Minutes relative to the event: negative = before, positive = after.
    pub offset_minutes: i32,
}

impl Anchor {
    pub const fn new(key: TimeKey, offset_minutes: i32) -> Self {
        Anchor {
            key,
            offset_minutes,
        }
    }
    pub const fn at(key: TimeKey) -> Self {
        Anchor {
            key,
            offset_minutes: 0,
        }
    }
    pub fn resolve(&self, times: &PrayerTimes) -> Option<i64> {
        times
            .get(self.key)
            .map(|t| t + self.offset_minutes as i64 * 60)
    }
}

/// How an item ends.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[cfg_attr(
    feature = "serde",
    derive(Serialize, Deserialize),
    serde(tag = "type", rename_all = "snake_case")
)]
pub enum End {
    /// Instantaneous (alarms, reminders).
    None,
    /// Fixed length in minutes.
    Duration { minutes: u32 },
    /// Ends at another anchor, e.g. "until 10 min before Maghrib".
    Until { anchor: Anchor },
}

/// When an item repeats. All dates are local civil dates.
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
#[cfg_attr(
    feature = "serde",
    derive(Serialize, Deserialize),
    serde(tag = "type", rename_all = "snake_case")
)]
pub enum Recurrence {
    Once {
        date: Date,
    },
    Daily,
    /// Bit mask, bit 0 = Sunday … bit 6 = Saturday.
    Weekly {
        days: u8,
    },
    /// Specific days of every Hijri month (e.g. 13, 14, 15 — Ayyām al-Bīḍ).
    HijriMonthly {
        days: Vec<u8>,
    },
    /// One Hijri date every year (e.g. 10 Muharram).
    HijriYearly {
        month: u8,
        day: u8,
    },
    /// Every day of a Hijri month (e.g. all of Ramadan = 9).
    HijriMonth {
        month: u8,
    },
}

impl Recurrence {
    pub fn weekly(days: &[Weekday]) -> Recurrence {
        Recurrence::Weekly {
            days: days.iter().fold(0, |m, d| m | d.bit()),
        }
    }

    pub fn matches(&self, date: Date, hijri: HijriDate) -> bool {
        match self {
            Recurrence::Once { date: d } => *d == date,
            Recurrence::Daily => true,
            Recurrence::Weekly { days } => days & date.weekday().bit() != 0,
            Recurrence::HijriMonthly { days } => days.contains(&hijri.day),
            Recurrence::HijriYearly { month, day } => hijri.month == *month && hijri.day == *day,
            Recurrence::HijriMonth { month } => hijri.month == *month,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[cfg_attr(
    feature = "serde",
    derive(Serialize, Deserialize),
    serde(rename_all = "snake_case")
)]
pub enum ItemKind {
    /// Rings at a moment.
    Alarm,
    /// Something to do, optionally with a duration.
    Task,
    /// A calendar block (meeting, class, appointment).
    Event,
}

/// A user's scheduled item.
#[derive(Debug, Clone, PartialEq)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize))]
pub struct Item {
    pub id: String,
    pub title: String,
    pub kind: ItemKind,
    pub start: Anchor,
    pub end: End,
    pub recurrence: Recurrence,
    #[cfg_attr(feature = "serde", serde(default = "default_true"))]
    pub enabled: bool,
    /// Extra notification this many minutes before the start.
    #[cfg_attr(feature = "serde", serde(default))]
    pub remind_before_minutes: Option<u32>,
    #[cfg_attr(feature = "serde", serde(default))]
    pub notes: String,
}

#[cfg(feature = "serde")]
fn default_true() -> bool {
    true
}

/// A concrete instance of an item on a date.
#[derive(Debug, Clone, PartialEq)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize))]
pub struct Occurrence {
    pub item_id: String,
    pub title: String,
    pub kind: ItemKind,
    pub date: Date,
    pub start: i64,
    pub end: Option<i64>,
    /// Prayers this occurrence overlaps (should be empty!).
    pub conflicts: Vec<Conflict>,
}

/// Time protected around each prayer.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize), serde(default))]
pub struct PrayerGuard {
    /// Minutes before the adhan to keep free (wudu, walking to the masjid).
    pub before_minutes: u32,
    /// Minutes after the adhan to keep free (the prayer itself, sunnah, adhkar).
    pub after_minutes: u32,
    /// Extra time protected after Dhuhr on Fridays for Jumu'ah (khutbah + prayer).
    pub jumuah_extra_minutes: u32,
}

impl Default for PrayerGuard {
    fn default() -> Self {
        PrayerGuard {
            before_minutes: 5,
            after_minutes: 20,
            jumuah_extra_minutes: 40,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize))]
pub struct Conflict {
    pub prayer: TimeKey,
    pub prayer_time: i64,
    /// The protected window that is overlapped.
    pub window_start: i64,
    pub window_end: i64,
}

/// A free slot, expressed both as an anchor and as concrete times.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize))]
pub struct Slot {
    pub anchor: Anchor,
    pub start: i64,
    pub end: i64,
}

/// Everything known about one local day.
#[derive(Debug, Clone, PartialEq)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize))]
pub struct Day {
    pub date: Date,
    pub weekday: Weekday,
    pub hijri: HijriDate,
    pub times: PrayerTimes,
    pub occasions: Vec<crate::hijri::Occasion>,
}

/// The scheduling engine: a location, calculation parameters and a Hijri profile.
#[derive(Debug, Clone)]
pub struct Engine {
    pub coords: Coordinates,
    pub params: Params,
    pub hijri: HijriProfile,
    pub guard: PrayerGuard,
}

impl Engine {
    pub fn new(coords: Coordinates, params: Params, hijri: HijriProfile) -> Self {
        Engine {
            coords,
            params,
            hijri,
            guard: PrayerGuard::default(),
        }
    }

    pub fn day(&self, date: Date) -> Day {
        let hijri = self.hijri.to_hijri(date);
        Day {
            date,
            weekday: date.weekday(),
            hijri,
            times: compute(date, self.coords, &self.params),
            occasions: crate::hijri::occasions(hijri),
        }
    }

    /// Protected windows for a day: (prayer, adhan, window start, window end).
    pub fn protected_windows(&self, day: &Day) -> Vec<Conflict> {
        let g = self.guard;
        TimeKey::PRAYERS
            .iter()
            .filter_map(|&k| {
                let t = day.times.get(k)?;
                let mut after = g.after_minutes as i64;
                if k == TimeKey::Dhuhr && day.weekday == Weekday::Friday {
                    after += g.jumuah_extra_minutes as i64;
                }
                Some(Conflict {
                    prayer: k,
                    prayer_time: t,
                    window_start: t - g.before_minutes as i64 * 60,
                    window_end: t + after * 60,
                })
            })
            .collect()
    }

    /// Prayer windows overlapped by the interval [start, end) (or the instant `start`).
    pub fn conflicts_on(&self, day: &Day, start: i64, end: Option<i64>) -> Vec<Conflict> {
        let end = end.unwrap_or(start + 1);
        self.protected_windows(day)
            .into_iter()
            .filter(|w| start < w.window_end && end > w.window_start)
            .collect()
    }

    fn resolve_item(&self, item: &Item, day: &Day) -> Option<(i64, Option<i64>)> {
        let start = item.start.resolve(&day.times)?;
        let end = match item.end {
            End::None => None,
            End::Duration { minutes } => Some(start + minutes as i64 * 60),
            End::Until { anchor } => anchor.resolve(&day.times).filter(|&e| e > start),
        };
        Some((start, end))
    }

    /// Expand items into occurrences over `days` local days starting at `from`.
    pub fn occurrences(&self, items: &[Item], from: Date, days: u32) -> Vec<Occurrence> {
        let mut out = Vec::new();
        for i in 0..days as i64 {
            let day = self.day(from.add_days(i));
            out.extend(self.occurrences_on(items, &day));
        }
        out.sort_by_key(|o| o.start);
        out
    }

    pub fn occurrences_on(&self, items: &[Item], day: &Day) -> Vec<Occurrence> {
        let mut out = Vec::new();
        for item in items.iter().filter(|i| i.enabled) {
            if !item.recurrence.matches(day.date, day.hijri) {
                continue;
            }
            if let Some((start, end)) = self.resolve_item(item, day) {
                // An alarm placed deliberately relative to a prayer is not a conflict.
                let conflicts = match item.kind {
                    ItemKind::Alarm => Vec::new(),
                    _ => self.conflicts_on(day, start, end),
                };
                out.push(Occurrence {
                    item_id: item.id.clone(),
                    title: item.title.clone(),
                    kind: item.kind,
                    date: day.date,
                    start,
                    end,
                    conflicts,
                });
            }
        }
        out.sort_by_key(|o| o.start);
        out
    }

    /// Free slots of `duration_minutes` between consecutive protected windows on a day,
    /// each starting right after a prayer's protected window ("after Asr").
    pub fn free_slots(&self, date: Date, duration_minutes: u32) -> Vec<Slot> {
        let day = self.day(date);
        let windows = self.protected_windows(&day);
        let mut slots = Vec::new();
        for pair in windows.windows(2) {
            let (a, b) = (pair[0], pair[1]);
            let start = a.window_end;
            let end = start + duration_minutes as i64 * 60;
            // Fajr's window: you cannot schedule past sunrise into nothing; still fine.
            if end <= b.window_start {
                let offset = ((start - a.prayer_time) / 60) as i32;
                slots.push(Slot {
                    anchor: Anchor::new(a.prayer, offset),
                    start,
                    end,
                });
            }
        }
        // After Isha, until midnight (end of Isha's preferred time).
        if let (Some(last), Some(mid)) = (windows.last(), day.times.midnight) {
            let start = last.window_end;
            let end = start + duration_minutes as i64 * 60;
            if end <= mid {
                slots.push(Slot {
                    anchor: Anchor::new(last.prayer, ((start - last.prayer_time) / 60) as i32),
                    start,
                    end,
                });
            }
        }
        slots
    }
}

/// Human description of an anchor in the given language ("en", "ar", "fr").
pub fn describe_anchor(a: &Anchor, lang: &str) -> String {
    let name = key_name(a.key, lang);
    let m = a.offset_minutes.unsigned_abs();
    let amount = describe_minutes(m, lang);
    match (lang, a.offset_minutes.signum()) {
        ("ar", 0) => format!("عند {name}"),
        ("ar", -1) => format!("قبل {name} بـ{amount}"),
        ("ar", _) => format!("بعد {name} بـ{amount}"),
        ("fr", 0) => format!("à {name}"),
        ("fr", -1) => format!("{amount} avant {name}"),
        ("fr", _) => format!("{amount} après {name}"),
        (_, 0) => format!("at {name}"),
        (_, -1) => format!("{amount} before {name}"),
        (_, _) => format!("{amount} after {name}"),
    }
}

pub fn describe_minutes(m: u32, lang: &str) -> String {
    let (h, mm) = (m / 60, m % 60);
    match lang {
        "ar" => match (h, mm) {
            (0, 1) => "دقيقة".into(),
            (0, 2) => "دقيقتين".into(),
            (0, 15) => "ربع ساعة".into(),
            (0, 30) => "نصف ساعة".into(),
            (0, n) if n <= 10 => format!("{n} دقائق"),
            (0, n) => format!("{n} دقيقة"),
            (1, 0) => "ساعة".into(),
            (1, 30) => "ساعة ونصف".into(),
            (2, 0) => "ساعتين".into(),
            (h, 0) => format!("{h} ساعات"),
            (h, n) => format!("{h} س و{n} د"),
        },
        "fr" => match (h, mm) {
            (0, n) => format!("{n} min"),
            (h, 0) => format!("{h} h"),
            (h, n) => format!("{h} h {n:02}"),
        },
        _ => match (h, mm) {
            (0, n) => format!("{n} min"),
            (h, 0) => format!("{h} h"),
            (h, n) => format!("{h} h {n} min"),
        },
    }
}

pub fn key_name(k: TimeKey, lang: &str) -> &'static str {
    match lang {
        "ar" => match k {
            TimeKey::Imsak => "الإمساك",
            TimeKey::Fajr => "الفجر",
            TimeKey::Sunrise => "الشروق",
            TimeKey::Dhuhr => "الظهر",
            TimeKey::Asr => "العصر",
            TimeKey::Sunset => "الغروب",
            TimeKey::Maghrib => "المغرب",
            TimeKey::Isha => "العشاء",
            TimeKey::Midnight => "منتصف الليل",
            TimeKey::LastThird => "الثلث الأخير",
        },
        "fr" => match k {
            TimeKey::Imsak => "l'Imsak",
            TimeKey::Fajr => "Fajr",
            TimeKey::Sunrise => "le lever du soleil",
            TimeKey::Dhuhr => "Dhuhr",
            TimeKey::Asr => "Asr",
            TimeKey::Sunset => "le coucher du soleil",
            TimeKey::Maghrib => "Maghrib",
            TimeKey::Isha => "Isha",
            TimeKey::Midnight => "minuit",
            TimeKey::LastThird => "le dernier tiers",
        },
        _ => match k {
            TimeKey::Imsak => "Imsak",
            TimeKey::Fajr => "Fajr",
            TimeKey::Sunrise => "Sunrise",
            TimeKey::Dhuhr => "Dhuhr",
            TimeKey::Asr => "Asr",
            TimeKey::Sunset => "Sunset",
            TimeKey::Maghrib => "Maghrib",
            TimeKey::Isha => "Isha",
            TimeKey::Midnight => "Midnight",
            TimeKey::LastThird => "Last third of the night",
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::hijri::HijriCalendar;
    use crate::method::Method;

    fn engine() -> Engine {
        Engine::new(
            Coordinates::new(36.7538, 3.0588),
            Params::for_method(Method::Algeria),
            HijriProfile::new(HijriCalendar::UmmAlQura),
        )
    }

    fn item(id: &str, kind: ItemKind, start: Anchor, end: End, rec: Recurrence) -> Item {
        Item {
            id: id.into(),
            title: id.into(),
            kind,
            start,
            end,
            recurrence: rec,
            enabled: true,
            remind_before_minutes: None,
            notes: String::new(),
        }
    }

    #[test]
    fn alarm_before_fajr_moves_with_the_sun() {
        let e = engine();
        let items = [item(
            "wake",
            ItemKind::Alarm,
            Anchor::new(TimeKey::Fajr, -15),
            End::None,
            Recurrence::Daily,
        )];
        let occ = e.occurrences(&items, Date::new(2026, 1, 1), 180);
        assert_eq!(occ.len(), 180);
        for o in &occ {
            let fajr = e.day(o.date).times.fajr.unwrap();
            assert_eq!(fajr - o.start, 15 * 60);
        }
        // Summer Fajr is much earlier than winter Fajr.
        assert!(occ[0].start % 86_400 > occ[170].start % 86_400);
    }

    #[test]
    fn meeting_overlapping_maghrib_is_flagged() {
        let e = engine();
        let items = [
            item(
                "ok",
                ItemKind::Event,
                Anchor::new(TimeKey::Asr, 30),
                End::Until {
                    anchor: Anchor::new(TimeKey::Maghrib, -10),
                },
                Recurrence::Daily,
            ),
            item(
                "bad",
                ItemKind::Event,
                Anchor::new(TimeKey::Asr, 30),
                End::Duration { minutes: 600 },
                Recurrence::Daily,
            ),
        ];
        let occ = e.occurrences(&items, Date::new(2026, 11, 13), 1);
        let ok = occ.iter().find(|o| o.item_id == "ok").unwrap();
        let bad = occ.iter().find(|o| o.item_id == "bad").unwrap();
        assert!(ok.conflicts.is_empty(), "{:?}", ok.conflicts);
        let names: Vec<_> = bad.conflicts.iter().map(|c| c.prayer).collect();
        assert_eq!(names, vec![TimeKey::Maghrib, TimeKey::Isha]);
    }

    #[test]
    fn jumuah_and_weekly() {
        let e = engine();
        let rec = Recurrence::weekly(&[Weekday::Friday]);
        let items = [item(
            "jumuah",
            ItemKind::Alarm,
            Anchor::new(TimeKey::Dhuhr, -45),
            End::None,
            rec,
        )];
        let occ = e.occurrences(&items, Date::new(2026, 11, 1), 30);
        assert!(occ.iter().all(|o| o.date.weekday() == Weekday::Friday));
        assert_eq!(occ.len(), 4);
    }

    #[test]
    fn hijri_recurrences() {
        let e = engine();
        let bid = item(
            "bid",
            ItemKind::Task,
            Anchor::new(TimeKey::Fajr, -45),
            End::None,
            Recurrence::HijriMonthly {
                days: vec![13, 14, 15],
            },
        );
        let ramadan = item(
            "suhoor",
            ItemKind::Alarm,
            Anchor::new(TimeKey::Imsak, -30),
            End::None,
            Recurrence::HijriMonth { month: 9 },
        );
        let occ = e.occurrences(&[bid, ramadan], Date::new(2027, 1, 1), 120);
        let bid_n = occ.iter().filter(|o| o.item_id == "bid").count();
        let ram_n = occ.iter().filter(|o| o.item_id == "suhoor").count();
        assert!((11..=12).contains(&bid_n), "{bid_n}");
        assert!(ram_n == 29 || ram_n == 30, "{ram_n}");
    }

    #[test]
    fn free_slots_avoid_prayers() {
        let e = engine();
        let slots = e.free_slots(Date::new(2026, 11, 13), 60);
        assert!(!slots.is_empty());
        let day = e.day(Date::new(2026, 11, 13));
        for s in slots {
            assert!(e.conflicts_on(&day, s.start, Some(s.end)).is_empty());
        }
    }

    #[test]
    fn descriptions() {
        let a = Anchor::new(TimeKey::Fajr, -15);
        assert_eq!(describe_anchor(&a, "en"), "15 min before Fajr");
        assert_eq!(describe_anchor(&a, "ar"), "قبل الفجر بـربع ساعة");
        assert_eq!(
            describe_anchor(&Anchor::new(TimeKey::Asr, 90), "fr"),
            "1 h 30 après Asr"
        );
    }
}
