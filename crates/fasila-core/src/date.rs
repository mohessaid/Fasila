//! Civil (proleptic Gregorian) dates and Julian Day helpers.

#[cfg(feature = "serde")]
use serde::{Deserialize, Serialize};

/// A civil date in the proleptic Gregorian calendar. Serialized as `"YYYY-MM-DD"`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub struct Date {
    pub year: i32,
    pub month: u8,
    pub day: u8,
}

/// Day of the week, ISO order is *not* used: Sunday = 0 to match most calendars.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[cfg_attr(
    feature = "serde",
    derive(Serialize, Deserialize),
    serde(rename_all = "lowercase")
)]
pub enum Weekday {
    Sunday,
    Monday,
    Tuesday,
    Wednesday,
    Thursday,
    Friday,
    Saturday,
}

impl Weekday {
    pub const ALL: [Weekday; 7] = [
        Weekday::Sunday,
        Weekday::Monday,
        Weekday::Tuesday,
        Weekday::Wednesday,
        Weekday::Thursday,
        Weekday::Friday,
        Weekday::Saturday,
    ];
    pub fn index(self) -> u8 {
        self as u8
    }
    pub fn from_index(i: u8) -> Weekday {
        Self::ALL[(i % 7) as usize]
    }
    /// Bit used in weekday masks (bit 0 = Sunday).
    pub fn bit(self) -> u8 {
        1 << self.index()
    }
}

impl Date {
    pub const fn new(year: i32, month: u8, day: u8) -> Self {
        Date { year, month, day }
    }

    pub fn is_leap_year(year: i32) -> bool {
        (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
    }

    pub fn days_in_month(year: i32, month: u8) -> u8 {
        match month {
            1 | 3 | 5 | 7 | 8 | 10 | 12 => 31,
            4 | 6 | 9 | 11 => 30,
            2 if Self::is_leap_year(year) => 29,
            2 => 28,
            _ => 0,
        }
    }

    pub fn is_valid(&self) -> bool {
        self.month >= 1
            && self.month <= 12
            && self.day >= 1
            && self.day <= Self::days_in_month(self.year, self.month)
    }

    /// Julian Day Number (integer, the day starting at noon UT of this date).
    pub fn jdn(&self) -> i64 {
        // Fliegel & Van Flandern (1968), valid for all proleptic Gregorian dates.
        let y = self.year as i64;
        let m = self.month as i64;
        let d = self.day as i64;
        let a = (14 - m) / 12;
        let yy = y + 4800 - a;
        let mm = m + 12 * a - 3;
        d + (153 * mm + 2) / 5 + 365 * yy + yy.div_euclid(4) - yy.div_euclid(100)
            + yy.div_euclid(400)
            - 32045
    }

    pub fn from_jdn(jdn: i64) -> Date {
        let a = jdn + 32044;
        let b = (4 * a + 3).div_euclid(146097);
        let c = a - (146097 * b).div_euclid(4);
        let d = (4 * c + 3).div_euclid(1461);
        let e = c - (1461 * d).div_euclid(4);
        let m = (5 * e + 2).div_euclid(153);
        let day = e - (153 * m + 2).div_euclid(5) + 1;
        let month = m + 3 - 12 * m.div_euclid(10);
        let year = 100 * b + d - 4800 + m.div_euclid(10);
        Date {
            year: year as i32,
            month: month as u8,
            day: day as u8,
        }
    }

    /// Julian Date at 0h UT of this date.
    pub fn jd_midnight(&self) -> f64 {
        self.jdn() as f64 - 0.5
    }

    pub fn add_days(&self, n: i64) -> Date {
        Date::from_jdn(self.jdn() + n)
    }

    pub fn weekday(&self) -> Weekday {
        Weekday::from_index((self.jdn() + 1).rem_euclid(7) as u8)
    }

    pub fn day_of_year(&self) -> u16 {
        (self.jdn() - Date::new(self.year, 1, 1).jdn() + 1) as u16
    }

    /// Unix timestamp (seconds) of 00:00 UTC on this date.
    pub fn unix_midnight_utc(&self) -> i64 {
        (self.jdn() - 2_440_588) * 86_400
    }

    /// The UTC civil date containing a unix timestamp.
    pub fn from_unix_utc(ts: i64) -> Date {
        Date::from_jdn(ts.div_euclid(86_400) + 2_440_588)
    }

    /// Civil date at a fixed UTC offset (minutes east of UTC).
    pub fn from_unix_with_offset(ts: i64, offset_minutes: i32) -> Date {
        Date::from_unix_utc(ts + offset_minutes as i64 * 60)
    }

    /// Parse `YYYY-MM-DD`.
    pub fn parse_iso(s: &str) -> Option<Date> {
        let mut it = s.trim().splitn(3, '-');
        let y = it.next()?.parse().ok()?;
        let m = it.next()?.parse().ok()?;
        let d = it.next()?.parse().ok()?;
        let date = Date::new(y, m, d);
        date.is_valid().then_some(date)
    }
}

impl core::fmt::Display for Date {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        write!(f, "{:04}-{:02}-{:02}", self.year, self.month, self.day)
    }
}

#[cfg(feature = "serde")]
impl Serialize for Date {
    fn serialize<S: serde::Serializer>(&self, s: S) -> Result<S::Ok, S::Error> {
        s.collect_str(self)
    }
}

#[cfg(feature = "serde")]
impl<'de> Deserialize<'de> for Date {
    fn deserialize<D: serde::Deserializer<'de>>(d: D) -> Result<Self, D::Error> {
        let s = String::deserialize(d)?;
        Date::parse_iso(&s).ok_or_else(|| {
            serde::de::Error::custom(format!("invalid date {s:?}, expected YYYY-MM-DD"))
        })
    }
}

/// Convert a unix timestamp (seconds) to a Julian Date (UT).
pub fn unix_to_jd(ts: f64) -> f64 {
    ts / 86_400.0 + 2_440_587.5
}

/// Convert a Julian Date (UT) to a unix timestamp in seconds.
pub fn jd_to_unix(jd: f64) -> f64 {
    (jd - 2_440_587.5) * 86_400.0
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn jdn_roundtrip() {
        assert_eq!(Date::new(2000, 1, 1).jdn(), 2_451_545);
        assert_eq!(Date::new(1970, 1, 1).jdn(), 2_440_588);
        for jdn in 2_300_000..2_500_000 {
            if jdn % 97 == 0 {
                assert_eq!(Date::from_jdn(jdn).jdn(), jdn);
            }
        }
    }

    #[test]
    fn weekday_known() {
        assert_eq!(Date::new(2026, 11, 13).weekday(), Weekday::Friday);
        assert_eq!(Date::new(2000, 1, 1).weekday(), Weekday::Saturday);
    }

    #[test]
    fn unix() {
        assert_eq!(Date::new(1970, 1, 2).unix_midnight_utc(), 86_400);
        assert_eq!(Date::from_unix_utc(-1), Date::new(1969, 12, 31));
    }
}
