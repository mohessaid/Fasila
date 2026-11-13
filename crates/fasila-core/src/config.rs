//! The user configuration shared by every Fasila app (desktop, mobile, CLI).
//! Stored as JSON; unknown fields are ignored and missing fields take defaults,
//! so older and newer apps can read each other's files.

#[cfg(feature = "serde")]
use serde::{Deserialize, Serialize};

use crate::hijri::{HijriCalendar, HijriProfile};
use crate::method::{Method, Params};
use crate::prayer::Coordinates;
use crate::schedule::{Engine, PrayerGuard};

#[derive(Debug, Clone, PartialEq)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize), serde(default))]
pub struct Location {
    pub name: String,
    pub latitude: f64,
    pub longitude: f64,
    pub elevation_m: f64,
    /// IANA time zone, e.g. "Africa/Algiers". Rendering only; computations are in UTC.
    pub timezone: String,
    /// ISO-3166 alpha-2 country code; selects defaults and the Hijri announcement feed.
    pub country: String,
}

impl Default for Location {
    fn default() -> Self {
        // Makkah al-Mukarramah.
        Location {
            name: "Makkah".into(),
            latitude: 21.422_487,
            longitude: 39.826_206,
            elevation_m: 0.0,
            timezone: "Asia/Riyadh".into(),
            country: "SA".into(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Default)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize), serde(default))]
pub struct Config {
    pub location: Location,
    pub params: Params,
    pub hijri: HijriProfile,
    pub guard: PrayerGuard,
}

impl Config {
    /// Sensible defaults for a place, picking the method used in that country.
    pub fn for_place(
        name: &str,
        latitude: f64,
        longitude: f64,
        timezone: &str,
        country: &str,
    ) -> Config {
        let method = Method::for_country(country);
        let calendar = HijriCalendar::UmmAlQura;
        Config {
            location: Location {
                name: name.into(),
                latitude,
                longitude,
                elevation_m: 0.0,
                timezone: timezone.into(),
                country: country.to_ascii_uppercase(),
            },
            params: Params::for_method(method),
            hijri: HijriProfile::new(calendar),
            guard: PrayerGuard::default(),
        }
    }

    pub fn coordinates(&self) -> Coordinates {
        Coordinates::new(self.location.latitude, self.location.longitude)
    }

    pub fn engine(&self) -> Engine {
        let mut params = self.params.clone();
        params.elevation_m = self.location.elevation_m;
        let mut e = Engine::new(self.coordinates(), params, self.hijri.clone());
        e.guard = self.guard;
        e
    }
}

#[cfg(all(test, feature = "serde"))]
mod tests {
    use super::*;

    #[test]
    fn json_roundtrip_and_tolerance() {
        let c = Config::for_place("Algiers", 36.7538, 3.0588, "Africa/Algiers", "dz");
        let s = serde_json::to_string(&c).unwrap();
        let back: Config = serde_json::from_str(&s).unwrap();
        assert_eq!(back, c);
        // Partial documents fill in defaults.
        let partial: Config = serde_json::from_str(
            r#"{"location":{"latitude":1.0,"longitude":2.0},"future_field":1}"#,
        )
        .unwrap();
        assert_eq!(partial.location.latitude, 1.0);
        assert_eq!(partial.params.method, Method::MuslimWorldLeague);
    }
}
