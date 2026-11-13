//! # fasila-core
//!
//! The single source of truth for Fasila on every platform:
//!
//! * [`prayer`] — high-precision prayer times (iterative Meeus solar model).
//! * [`method`] — calculation methods, madhab, high-latitude and polar rules.
//! * [`hijri`]  — Umm al-Qura / tabular Hijri calendar with country announcements.
//! * [`schedule`] — alarms, tasks and events anchored to prayers, not clock hours.
//! * [`parse`] — natural-language anchors in English, Arabic and French.

pub mod astro;
pub mod config;
pub mod date;
pub mod hijri;
pub mod method;
pub mod parse;
pub mod prayer;
pub mod schedule;
mod ummalqura_data;

pub use config::{Config, Location};
pub use date::{Date, Weekday};
pub use hijri::{HijriCalendar, HijriDate, HijriProfile, MonthStart};
pub use method::{Adjustments, HighLatitudeRule, Madhab, Method, MidnightMethod, Params, PolarRule, Rounding, Twilight};
pub use prayer::{compute, qibla, Coordinates, PrayerTimes, TimeKey};
pub use schedule::{Anchor, Day, End, Engine, Item, ItemKind, Occurrence, PrayerGuard, Recurrence, Slot};
