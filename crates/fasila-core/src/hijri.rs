//! Hijri calendar.
//!
//! The Hijri calendar is not one calendar: Saudi Arabia uses Umm al-Qura,
//! many countries announce each month after a local moon sighting, and those
//! announcements can differ by a day or two. Fasila models this explicitly:
//!
//! * a **base calendar** (Umm al-Qura table, or the arithmetical tabular calendar),
//! * a **day adjustment** chosen by the user or the region (±N days),
//! * a list of **authoritative month starts** ([`MonthStart`]) published by a
//!   country's religious authority, delivered through the update feed. These
//!   always win over the base calendar, and every following month is re-chained
//!   from them (29 or 30 days, never anything else).

use crate::date::Date;
use crate::ummalqura_data as uaq;

#[cfg(feature = "serde")]
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize))]
pub struct HijriDate {
    pub year: i32,
    pub month: u8,
    pub day: u8,
}

impl HijriDate {
    pub const fn new(year: i32, month: u8, day: u8) -> Self {
        HijriDate { year, month, day }
    }
    /// Months elapsed since 1 Muharram 1 AH.
    pub fn month_index(&self) -> i64 {
        (self.year as i64 - 1) * 12 + (self.month as i64 - 1)
    }
    fn from_month_index(idx: i64, day: u8) -> HijriDate {
        HijriDate { year: (idx.div_euclid(12) + 1) as i32, month: (idx.rem_euclid(12) + 1) as u8, day }
    }
}

impl core::fmt::Display for HijriDate {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        write!(f, "{:04}-{:02}-{:02}", self.year, self.month, self.day)
    }
}

/// Base Hijri calendar.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize), serde(rename_all = "snake_case"))]
pub enum HijriCalendar {
    /// Official Umm al-Qura calendar (Saudi Arabia), 1343–1500 AH, tabular outside that range.
    UmmAlQura,
    /// Arithmetical (civil, "Kuwaiti") calendar: 30-year cycle with leap years 2,5,7,10,13,16,18,21,24,26,29.
    Tabular,
}

/// An authoritative month start announced by a religious authority.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize))]
pub struct MonthStart {
    pub year: i32,
    pub month: u8,
    /// Gregorian date of the 1st of that Hijri month.
    pub starts_on: Date,
}

/// A region's Hijri configuration.
#[derive(Debug, Clone, PartialEq, Eq, Default)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize), serde(default))]
pub struct HijriProfile {
    pub calendar: HijriCalendarOpt,
    /// Shift in days. +1 means the Hijri date is one day *ahead* of the base calendar.
    pub day_adjustment: i32,
    /// Authoritative month starts (any order).
    pub month_starts: Vec<MonthStart>,
}

/// Wrapper so `HijriProfile` can derive `Default`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize), serde(transparent))]
pub struct HijriCalendarOpt(pub HijriCalendar);
impl Default for HijriCalendarOpt {
    fn default() -> Self {
        HijriCalendarOpt(HijriCalendar::UmmAlQura)
    }
}

const TABULAR_EPOCH_JDN: i64 = 1_948_440; // 1 Muharram 1 AH (civil epoch, Friday 16 July 622 Julian)

fn tabular_month_start(idx: i64) -> i64 {
    let year = idx.div_euclid(12) + 1;
    let month = idx.rem_euclid(12) + 1;
    TABULAR_EPOCH_JDN - 1
        + (year - 1) * 354
        + (3 + 11 * year).div_euclid(30)
        + ((29.5 * (month - 1) as f64).ceil() as i64)
        + 1
}

fn tabular_from_jdn(jdn: i64) -> HijriDate {
    // Find the month index whose start is <= jdn.
    let approx = ((jdn - TABULAR_EPOCH_JDN) as f64 / 29.530_588).floor() as i64;
    let mut idx = approx - 1;
    while tabular_month_start(idx + 1) <= jdn {
        idx += 1;
    }
    while tabular_month_start(idx) > jdn {
        idx -= 1;
    }
    HijriDate::from_month_index(idx, (jdn - tabular_month_start(idx) + 1) as u8)
}

fn uaq_range(idx: i64) -> bool {
    let first = (uaq::FIRST_YEAR as i64 - 1) * 12;
    idx >= first && idx - first + 1 < uaq::MONTH_STARTS.len() as i64
}

impl HijriCalendar {
    /// JDN of the first day of the month with the given month index.
    pub fn month_start_jdn(self, idx: i64) -> i64 {
        match self {
            HijriCalendar::UmmAlQura if uaq_range(idx) => {
                uaq::MONTH_STARTS[(idx - uaq::MONTH_OFFSET as i64) as usize] as i64 + 2_400_000
            }
            _ => tabular_month_start(idx),
        }
    }

    pub fn from_jdn(self, jdn: i64) -> HijriDate {
        if self == HijriCalendar::UmmAlQura {
            let first = uaq::MONTH_STARTS[0] as i64 + 2_400_000;
            let last = *uaq::MONTH_STARTS.last().unwrap() as i64 + 2_400_000;
            if jdn >= first && jdn < last {
                // Binary search over the table.
                let rjd = (jdn - 2_400_000) as u32;
                let pos = match uaq::MONTH_STARTS.binary_search(&rjd) {
                    Ok(i) => i,
                    Err(i) => i - 1,
                };
                let idx = pos as i64 + uaq::MONTH_OFFSET as i64;
                return HijriDate::from_month_index(idx, (rjd - uaq::MONTH_STARTS[pos] + 1) as u8);
            }
        }
        tabular_from_jdn(jdn)
    }

    pub fn month_length(self, year: i32, month: u8) -> u8 {
        let idx = HijriDate::new(year, month, 1).month_index();
        (self.month_start_jdn(idx + 1) - self.month_start_jdn(idx)) as u8
    }
}

impl HijriProfile {
    pub fn new(calendar: HijriCalendar) -> Self {
        HijriProfile { calendar: HijriCalendarOpt(calendar), day_adjustment: 0, month_starts: Vec::new() }
    }

    fn base(&self) -> HijriCalendar {
        self.calendar.0
    }

    /// Base calendar month start including the day adjustment.
    fn adjusted_base_start(&self, idx: i64) -> i64 {
        self.base().month_start_jdn(idx) - self.day_adjustment as i64
    }

    fn override_for(&self, idx: i64) -> Option<i64> {
        self.month_starts
            .iter()
            .find(|m| HijriDate::new(m.year, m.month, 1).month_index() == idx)
            .map(|m| m.starts_on.jdn())
    }

    fn has_override_in(&self, lo: i64, hi: i64) -> bool {
        self.month_starts.iter().any(|m| {
            let i = HijriDate::new(m.year, m.month, 1).month_index();
            i >= lo && i <= hi
        })
    }

    /// Effective JDN on which month `idx` starts, honouring announced month starts.
    pub fn month_start_jdn(&self, idx: i64) -> i64 {
        if let Some(s) = self.override_for(idx) {
            return s;
        }
        let forward = self.chained_start(idx);
        // If only the *next* month was announced, keep this month within 29–30 days of it.
        match self.override_for(idx + 1) {
            Some(next) => forward.clamp(next - 30, next - 29),
            None => forward,
        }
    }

    /// Start of month `idx` chained forward from the latest announcement before it.
    fn chained_start(&self, idx: i64) -> i64 {
        let anchor = self
            .month_starts
            .iter()
            .map(|m| (HijriDate::new(m.year, m.month, 1).month_index(), m.starts_on.jdn()))
            .filter(|(i, _)| *i < idx)
            .max_by_key(|(i, _)| *i);
        let Some((mut cur_idx, mut cur_start)) = anchor else {
            return self.adjusted_base_start(idx);
        };
        // Every month is 29 or 30 days. Once the chain re-joins the base calendar it stays
        // joined (until the next announcement, which is handled by the anchor search).
        while cur_idx < idx {
            let next_idx = cur_idx + 1;
            let base = self.adjusted_base_start(next_idx);
            let next_start = base.clamp(cur_start + 29, cur_start + 30);
            if next_start == base && !self.has_override_in(next_idx + 1, idx) {
                return self.adjusted_base_start(idx);
            }
            cur_idx = next_idx;
            cur_start = next_start;
        }
        cur_start
    }

    /// Convert a Gregorian date into a Hijri date under this profile.
    pub fn to_hijri(&self, date: Date) -> HijriDate {
        let jdn = date.jdn();
        let guess = self.base().from_jdn(jdn + self.day_adjustment as i64);
        let mut idx = guess.month_index();
        // Announcements move month starts by at most a couple of days; walk to the right month.
        for _ in 0..4 {
            if self.month_start_jdn(idx) > jdn {
                idx -= 1;
            } else if self.month_start_jdn(idx + 1) <= jdn {
                idx += 1;
            } else {
                break;
            }
        }
        HijriDate::from_month_index(idx, (jdn - self.month_start_jdn(idx) + 1) as u8)
    }

    /// Convert a Hijri date to Gregorian under this profile.
    pub fn to_gregorian(&self, h: HijriDate) -> Date {
        Date::from_jdn(self.month_start_jdn(h.month_index()) + h.day as i64 - 1)
    }

    pub fn month_length(&self, year: i32, month: u8) -> u8 {
        let idx = HijriDate::new(year, month, 1).month_index();
        (self.month_start_jdn(idx + 1) - self.month_start_jdn(idx)) as u8
    }
}

/// Notable days in the Hijri year (for reminders and calendar badges).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize), serde(rename_all = "snake_case"))]
pub enum Occasion {
    IslamicNewYear,
    Ashura,
    MawlidAnNabi,
    IsraMiraj,
    MidShaban,
    RamadanStart,
    LaylatAlQadr,
    EidAlFitr,
    DayOfArafah,
    EidAlAdha,
    AyyamAlBid,
    DhulHijjahFirstTen,
}

/// Occasions falling on a given Hijri date (convention: most widely observed dates).
pub fn occasions(h: HijriDate) -> Vec<Occasion> {
    let mut v = Vec::new();
    match (h.month, h.day) {
        (1, 1) => v.push(Occasion::IslamicNewYear),
        (1, 10) => v.push(Occasion::Ashura),
        (3, 12) => v.push(Occasion::MawlidAnNabi),
        (7, 27) => v.push(Occasion::IsraMiraj),
        (8, 15) => v.push(Occasion::MidShaban),
        (9, 1) => v.push(Occasion::RamadanStart),
        (9, 27) => v.push(Occasion::LaylatAlQadr),
        (10, 1) => v.push(Occasion::EidAlFitr),
        (12, 9) => v.push(Occasion::DayOfArafah),
        (12, 10) => v.push(Occasion::EidAlAdha),
        _ => {}
    }
    if h.month == 12 && h.day <= 9 {
        v.push(Occasion::DhulHijjahFirstTen);
    }
    // Ayyam al-Bid (13–15) — except 13 Dhu al-Hijjah (Tashreeq, fasting forbidden).
    if (13..=15).contains(&h.day) && !(h.month == 12 && h.day == 13) {
        v.push(Occasion::AyyamAlBid);
    }
    v
}

pub const MONTH_NAMES_EN: [&str; 12] = [
    "Muharram",
    "Safar",
    "Rabi al-Awwal",
    "Rabi al-Thani",
    "Jumada al-Ula",
    "Jumada al-Akhirah",
    "Rajab",
    "Shaban",
    "Ramadan",
    "Shawwal",
    "Dhu al-Qadah",
    "Dhu al-Hijjah",
];

pub const MONTH_NAMES_AR: [&str; 12] = [
    "محرم",
    "صفر",
    "ربيع الأول",
    "ربيع الآخر",
    "جمادى الأولى",
    "جمادى الآخرة",
    "رجب",
    "شعبان",
    "رمضان",
    "شوال",
    "ذو القعدة",
    "ذو الحجة",
];

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn umm_al_qura_known_dates() {
        let p = HijriProfile::new(HijriCalendar::UmmAlQura);
        // 1 Muharram 1446 = 7 July 2024 (Umm al-Qura).
        assert_eq!(p.to_hijri(Date::new(2024, 7, 7)), HijriDate::new(1446, 1, 1));
        // 1 Ramadan 1445 = 11 March 2024.
        assert_eq!(p.to_gregorian(HijriDate::new(1445, 9, 1)), Date::new(2024, 3, 11));
        // 1 Shawwal 1445 (Eid al-Fitr) = 10 April 2024.
        assert_eq!(p.to_gregorian(HijriDate::new(1445, 10, 1)), Date::new(2024, 4, 10));
        // 10 Dhu al-Hijjah 1445 (Eid al-Adha) = 16 June 2024.
        assert_eq!(p.to_gregorian(HijriDate::new(1445, 12, 10)), Date::new(2024, 6, 16));
    }

    #[test]
    fn umm_al_qura_roundtrip() {
        let p = HijriProfile::new(HijriCalendar::UmmAlQura);
        let start = Date::new(1930, 1, 1).jdn();
        for jdn in (start..start + 50_000).step_by(7) {
            let d = Date::from_jdn(jdn);
            let h = p.to_hijri(d);
            assert!(h.day >= 1 && h.day <= 30);
            assert_eq!(p.to_gregorian(h), d, "{d} -> {h}");
        }
    }

    #[test]
    fn tabular_roundtrip_and_lengths() {
        let p = HijriProfile::new(HijriCalendar::Tabular);
        for jdn in (2_400_000..2_500_000).step_by(13) {
            let d = Date::from_jdn(jdn);
            assert_eq!(p.to_gregorian(p.to_hijri(d)), d);
        }
        // Tabular epoch: 1/1/1 AH = 16 July 622 (Julian) = JDN 1948440.
        assert_eq!(HijriCalendar::Tabular.from_jdn(1_948_440), HijriDate::new(1, 1, 1));
        for m in 1..=12 {
            let l = p.month_length(1447, m);
            assert!(l == 29 || l == 30);
        }
    }

    #[test]
    fn day_adjustment() {
        let mut p = HijriProfile::new(HijriCalendar::UmmAlQura);
        p.day_adjustment = -1;
        assert_eq!(p.to_hijri(Date::new(2024, 3, 12)), HijriDate::new(1445, 9, 1));
    }

    #[test]
    fn announced_month_start_overrides_and_rechains() {
        let mut p = HijriProfile::new(HijriCalendar::UmmAlQura);
        // Suppose a country announced 1 Ramadan 1445 on 12 March 2024 (one day after Umm al-Qura).
        p.month_starts.push(MonthStart { year: 1445, month: 9, starts_on: Date::new(2024, 3, 12) });
        assert_eq!(p.to_hijri(Date::new(2024, 3, 11)), HijriDate::new(1445, 8, 30));
        assert_eq!(p.to_hijri(Date::new(2024, 3, 12)), HijriDate::new(1445, 9, 1));
        // Ramadan cannot exceed 30 days: Shawwal starts by 11 April at the latest.
        let shawwal = p.to_gregorian(HijriDate::new(1445, 10, 1));
        assert!(shawwal == Date::new(2024, 4, 10) || shawwal == Date::new(2024, 4, 11));
        // Then the announcement of Shawwal wins.
        p.month_starts.push(MonthStart { year: 1445, month: 10, starts_on: Date::new(2024, 4, 10) });
        assert_eq!(p.to_hijri(Date::new(2024, 4, 9)), HijriDate::new(1445, 9, 29));
        assert_eq!(p.to_hijri(Date::new(2024, 4, 10)), HijriDate::new(1445, 10, 1));
        // Every month has 29 or 30 days around the announcements.
        for m in 6..=12 {
            let l = p.month_length(1445, m);
            assert!(l == 29 || l == 30, "month {m} has {l} days");
        }
        // Roundtrip still holds.
        for jdn in Date::new(2024, 1, 1).jdn()..Date::new(2025, 1, 1).jdn() {
            let d = Date::from_jdn(jdn);
            assert_eq!(p.to_gregorian(p.to_hijri(d)), d);
        }
    }
}
