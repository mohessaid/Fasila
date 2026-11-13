//! UniFFI surface of fasila-core for Swift and Kotlin.
//!
//! The mobile apps never re-implement astronomy or calendars: they call into
//! exactly the same Rust code as the desktop app and the CLI. Configuration and
//! schedule items cross the boundary as JSON (the shared schema in
//! `fasila_core::config`), results come back as typed records.

use std::sync::Arc;

use fasila_core::{self as core, hijri, schedule, Config, Date, Item, TimeKey};

uniffi::setup_scaffolding!();

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum FasilaError {
    #[error("invalid configuration: {msg}")]
    InvalidConfig { msg: String },
    #[error("invalid items: {msg}")]
    InvalidItems { msg: String },
    #[error("invalid date")]
    InvalidDate,
    #[error("could not understand: {msg}")]
    Parse { msg: String },
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct DayInfo {
    /// ISO date, `YYYY-MM-DD` (local civil date).
    pub date: String,
    /// 0 = Sunday … 6 = Saturday.
    pub weekday: u8,
    pub hijri_year: i32,
    pub hijri_month: u8,
    pub hijri_day: u8,
    /// Unix seconds (UTC). `None` only under the "unresolved" polar rule.
    pub imsak: Option<i64>,
    pub fajr: Option<i64>,
    pub sunrise: Option<i64>,
    pub dhuhr: Option<i64>,
    pub asr: Option<i64>,
    pub sunset: Option<i64>,
    pub maghrib: Option<i64>,
    pub isha: Option<i64>,
    pub midnight: Option<i64>,
    pub last_third: Option<i64>,
    pub fajr_estimated: bool,
    pub isha_estimated: bool,
    pub polar_fallback: bool,
    /// Occasion ids, e.g. `ayyam_al_bid`, `eid_al_fitr`.
    pub occasions: Vec<String>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct ConflictInfo {
    pub prayer: String,
    pub prayer_time: i64,
    pub window_start: i64,
    pub window_end: i64,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct OccurrenceInfo {
    pub item_id: String,
    pub title: String,
    /// `alarm`, `task` or `event`.
    pub kind: String,
    pub date: String,
    pub start: i64,
    pub end: Option<i64>,
    pub conflicts: Vec<ConflictInfo>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct SlotInfo {
    pub anchor_key: String,
    pub anchor_offset_minutes: i32,
    pub start: i64,
    pub end: i64,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct ParsedInfo {
    pub title: String,
    pub start_key: String,
    pub start_offset_minutes: i32,
    /// A ready-to-save `Item` JSON (id left empty for the app to fill).
    pub item_json: String,
    /// Human description in the requested language, e.g. "15 min before Fajr".
    pub description: String,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct MoonInfo {
    /// 0 = new, 0.25 = first quarter, 0.5 = full, 0.75 = last quarter.
    pub phase: f64,
    pub illumination: f64,
    pub waxing: bool,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct MethodInfo {
    pub id: String,
    pub name: String,
}

fn day_info(d: &schedule::Day) -> DayInfo {
    let t = &d.times;
    DayInfo {
        date: d.date.to_string(),
        weekday: d.weekday.index(),
        hijri_year: d.hijri.year,
        hijri_month: d.hijri.month,
        hijri_day: d.hijri.day,
        imsak: t.imsak,
        fajr: t.fajr,
        sunrise: t.sunrise,
        dhuhr: t.dhuhr,
        asr: t.asr,
        sunset: t.sunset,
        maghrib: t.maghrib,
        isha: t.isha,
        midnight: t.midnight,
        last_third: t.last_third,
        fajr_estimated: t.fajr_estimated,
        isha_estimated: t.isha_estimated,
        polar_fallback: t.polar_fallback,
        occasions: d
            .occasions
            .iter()
            .map(|o| {
                serde_json::to_value(o)
                    .unwrap()
                    .as_str()
                    .unwrap_or("")
                    .to_string()
            })
            .collect(),
    }
}

fn date(y: i32, m: u8, d: u8) -> Result<Date, FasilaError> {
    let date = Date::new(y, m, d);
    date.is_valid()
        .then_some(date)
        .ok_or(FasilaError::InvalidDate)
}

fn kind_id(k: schedule::ItemKind) -> String {
    serde_json::to_value(k)
        .unwrap()
        .as_str()
        .unwrap()
        .to_string()
}

#[derive(uniffi::Object)]
pub struct FasilaEngine {
    config: Config,
    engine: schedule::Engine,
}

#[uniffi::export]
impl FasilaEngine {
    /// Build an engine from the shared configuration JSON.
    #[uniffi::constructor]
    pub fn new(config_json: String) -> Result<Arc<Self>, FasilaError> {
        let config: Config = serde_json::from_str(&config_json)
            .map_err(|e| FasilaError::InvalidConfig { msg: e.to_string() })?;
        if !config.coordinates().is_valid() {
            return Err(FasilaError::InvalidConfig {
                msg: "coordinates out of range".into(),
            });
        }
        let engine = config.engine();
        Ok(Arc::new(FasilaEngine { config, engine }))
    }

    pub fn config_json(&self) -> String {
        serde_json::to_string(&self.config).unwrap()
    }

    pub fn day(&self, year: i32, month: u8, day: u8) -> Result<DayInfo, FasilaError> {
        Ok(day_info(&self.engine.day(date(year, month, day)?)))
    }

    /// Consecutive days starting at the given date (e.g. a month view or a widget timeline).
    pub fn days(
        &self,
        year: i32,
        month: u8,
        day: u8,
        count: u32,
    ) -> Result<Vec<DayInfo>, FasilaError> {
        let start = date(year, month, day)?;
        Ok((0..count as i64)
            .map(|i| day_info(&self.engine.day(start.add_days(i))))
            .collect())
    }

    pub fn occurrences(
        &self,
        items_json: String,
        year: i32,
        month: u8,
        day: u8,
        days: u32,
    ) -> Result<Vec<OccurrenceInfo>, FasilaError> {
        let items: Vec<Item> = serde_json::from_str(&items_json)
            .map_err(|e| FasilaError::InvalidItems { msg: e.to_string() })?;
        Ok(self
            .engine
            .occurrences(&items, date(year, month, day)?, days)
            .into_iter()
            .map(|o| OccurrenceInfo {
                item_id: o.item_id,
                title: o.title,
                kind: kind_id(o.kind),
                date: o.date.to_string(),
                start: o.start,
                end: o.end,
                conflicts: o
                    .conflicts
                    .into_iter()
                    .map(|c| ConflictInfo {
                        prayer: c.prayer.id().into(),
                        prayer_time: c.prayer_time,
                        window_start: c.window_start,
                        window_end: c.window_end,
                    })
                    .collect(),
            })
            .collect())
    }

    pub fn free_slots(
        &self,
        year: i32,
        month: u8,
        day: u8,
        duration_minutes: u32,
    ) -> Result<Vec<SlotInfo>, FasilaError> {
        Ok(self
            .engine
            .free_slots(date(year, month, day)?, duration_minutes)
            .into_iter()
            .map(|s| SlotInfo {
                anchor_key: s.anchor.key.id().into(),
                anchor_offset_minutes: s.anchor.offset_minutes,
                start: s.start,
                end: s.end,
            })
            .collect())
    }

    /// Qibla bearing in degrees from true north.
    pub fn qibla(&self) -> f64 {
        core::qibla(self.config.coordinates())
    }

    pub fn distance_to_kaaba_km(&self) -> f64 {
        core::prayer::distance_to_kaaba_km(self.config.coordinates())
    }

    pub fn hijri_to_gregorian(&self, year: i32, month: u8, day: u8) -> String {
        self.config
            .hijri
            .to_gregorian(hijri::HijriDate::new(year, month, day))
            .to_string()
    }
}

/// Parse a natural-language sentence ("15 min before fajr", "اجتماع بعد العصر بنصف ساعة").
#[uniffi::export]
pub fn parse_sentence(
    text: String,
    year: i32,
    month: u8,
    day: u8,
    lang: String,
) -> Result<ParsedInfo, FasilaError> {
    let today = date(year, month, day)?;
    let p =
        core::parse::parse(&text, today).map_err(|e| FasilaError::Parse { msg: e.to_string() })?;
    let item = Item {
        id: String::new(),
        title: p.title.clone(),
        kind: p.kind.unwrap_or(schedule::ItemKind::Alarm),
        start: p.start,
        end: p.end,
        recurrence: p.recurrence.clone().unwrap_or(schedule::Recurrence::Daily),
        enabled: true,
        remind_before_minutes: None,
        notes: String::new(),
    };
    Ok(ParsedInfo {
        title: p.title,
        start_key: p.start.key.id().into(),
        start_offset_minutes: p.start.offset_minutes,
        item_json: serde_json::to_string(&item).unwrap(),
        description: schedule::describe_anchor(&p.start, &lang),
    })
}

/// Moon phase at a unix timestamp (for the night-sky illustration).
#[uniffi::export]
pub fn moon_phase(unix: i64) -> MoonInfo {
    let p = core::moon_phase(unix);
    MoonInfo {
        phase: p.phase,
        illumination: p.illumination,
        waxing: p.waxing,
    }
}

/// "15 min before Fajr" / "قبل الفجر بربع ساعة" / "15 min avant Fajr".
#[uniffi::export]
pub fn describe_anchor(key: String, offset_minutes: i32, lang: String) -> String {
    match TimeKey::from_id(&key) {
        Some(k) => schedule::describe_anchor(&schedule::Anchor::new(k, offset_minutes), &lang),
        None => String::new(),
    }
}

#[uniffi::export]
pub fn time_key_name(key: String, lang: String) -> String {
    TimeKey::from_id(&key)
        .map(|k| schedule::key_name(k, &lang).to_string())
        .unwrap_or_default()
}

#[uniffi::export]
pub fn hijri_month_name(month: u8, lang: String) -> String {
    let i = (month.clamp(1, 12) - 1) as usize;
    match lang.as_str() {
        "ar" => hijri::MONTH_NAMES_AR[i],
        "fr" => hijri::MONTH_NAMES_FR[i],
        _ => hijri::MONTH_NAMES_EN[i],
    }
    .to_string()
}

#[uniffi::export]
pub fn calculation_methods() -> Vec<MethodInfo> {
    core::Method::ALL
        .iter()
        .map(|m| MethodInfo {
            id: m.id().into(),
            name: m.display_name().into(),
        })
        .collect()
}

/// Full parameter set (`Params` JSON) defined by a calculation method: angles, Isha interval,
/// Maghrib rule, midnight method and authority offsets. Apps merge it into the config while
/// keeping the user's own choices (madhab, high-latitude/polar rules, rounding, adjustments).
#[uniffi::export]
pub fn method_params_json(method_id: String) -> Result<String, FasilaError> {
    let m = core::Method::from_id(&method_id).ok_or(FasilaError::InvalidConfig {
        msg: format!("unknown method {method_id}"),
    })?;
    Ok(serde_json::to_string(&core::Params::for_method(m)).unwrap())
}

/// The method used by default in a country (ISO-3166 alpha-2).
#[uniffi::export]
pub fn recommended_method(country: String) -> String {
    core::Method::for_country(&country).id().to_string()
}

/// Default configuration JSON for a place (method chosen from the country).
#[uniffi::export]
pub fn default_config_json(
    name: String,
    latitude: f64,
    longitude: f64,
    timezone: String,
    country: String,
) -> String {
    serde_json::to_string(&Config::for_place(
        &name, latitude, longitude, &timezone, &country,
    ))
    .unwrap()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn end_to_end() {
        let cfg = default_config_json(
            "Algiers".into(),
            36.7538,
            3.0588,
            "Africa/Algiers".into(),
            "DZ".into(),
        );
        let e = FasilaEngine::new(cfg).unwrap();
        let d = e.day(2026, 11, 13).unwrap();
        assert!(d.fajr.unwrap() < d.dhuhr.unwrap());
        let p =
            parse_sentence("Alarm 15 min before fajr".into(), 2026, 11, 13, "ar".into()).unwrap();
        assert_eq!(p.description, "قبل الفجر بـربع ساعة");
        let occ = e
            .occurrences(format!("[{}]", p.item_json), 2026, 11, 13, 3)
            .unwrap();
        assert_eq!(occ.len(), 3);
        assert_eq!(occ[0].start, d.fajr.unwrap() - 900);
        let jafari = method_params_json("jafari".into()).unwrap();
        assert!(jafari.contains("\"fajr_angle\":16.0"), "{jafari}");
        assert_eq!(recommended_method("dz".into()), "algeria");
    }
}
