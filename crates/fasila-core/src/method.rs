//! Calculation methods and parameters.
//!
//! Every built-in method documents its source. Where a national authority
//! publishes additional precautionary minutes (ihtiyāṭ / temkin) they are
//! encoded in `method_adjustments`; users can always add their own on top.

#[cfg(feature = "serde")]
use serde::{Deserialize, Serialize};

/// Built-in calculation methods.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize), serde(rename_all = "snake_case"))]
pub enum Method {
    /// Muslim World League. Fajr 18°, Isha 17°.
    MuslimWorldLeague,
    /// Islamic Society of North America. Fajr 15°, Isha 15°.
    Isna,
    /// Egyptian General Authority of Survey. Fajr 19.5°, Isha 17.5°.
    Egypt,
    /// Umm al-Qura University, Makkah. Fajr 18.5°, Isha 90 min after Maghrib (120 min in Ramadan).
    UmmAlQura,
    /// University of Islamic Sciences, Karachi. Fajr 18°, Isha 18°.
    Karachi,
    /// Institute of Geophysics, University of Tehran. Fajr 17.7°, Isha 14°, Maghrib 4.5°, Jafari midnight.
    Tehran,
    /// Shia Ithna-Ashari, Leva Institute, Qum. Fajr 16°, Isha 14°, Maghrib 4°, Jafari midnight.
    Jafari,
    /// Dubai / UAE (Awqaf). Fajr 18.2°, Isha 18.2°.
    Dubai,
    /// Kuwait Ministry of Awqaf. Fajr 18°, Isha 17.5°.
    Kuwait,
    /// Qatar Ministry of Awqaf. Fajr 18°, Isha 90 min after Maghrib.
    Qatar,
    /// Majlis Ugama Islam Singapura. Fajr 20°, Isha 18°.
    Singapore,
    /// JAKIM, Malaysia. Fajr 20°, Isha 18°.
    Malaysia,
    /// Kementerian Agama, Indonesia. Fajr 20°, Isha 18°.
    Indonesia,
    /// Union des Organisations Islamiques de France. Fajr 12°, Isha 12°.
    France,
    /// Diyanet İşleri Başkanlığı, Turkey. Fajr 18°, Isha 17° with official temkin offsets.
    Turkey,
    /// Spiritual Administration of Muslims of Russia. Fajr 16°, Isha 15°.
    Russia,
    /// Moonsighting Committee Worldwide (Khalid Shaukat). Seasonally-adjusted twilight.
    MoonsightingCommittee,
    /// Algeria, Ministry of Religious Affairs and Wakfs. Fajr 18°, Isha 17°.
    Algeria,
    /// Morocco, Ministry of Habous and Islamic Affairs. Fajr 19°, Isha 17°.
    Morocco,
    /// Tunisia, Ministry of Religious Affairs. Fajr 18°, Isha 18°.
    Tunisia,
    /// User-defined parameters.
    Custom,
}

impl Method {
    pub const ALL: [Method; 20] = [
        Method::MuslimWorldLeague,
        Method::Isna,
        Method::Egypt,
        Method::UmmAlQura,
        Method::Karachi,
        Method::Tehran,
        Method::Jafari,
        Method::Dubai,
        Method::Kuwait,
        Method::Qatar,
        Method::Singapore,
        Method::Malaysia,
        Method::Indonesia,
        Method::France,
        Method::Turkey,
        Method::Russia,
        Method::MoonsightingCommittee,
        Method::Algeria,
        Method::Morocco,
        Method::Tunisia,
    ];

    pub fn id(self) -> &'static str {
        match self {
            Method::MuslimWorldLeague => "muslim_world_league",
            Method::Isna => "isna",
            Method::Egypt => "egypt",
            Method::UmmAlQura => "umm_al_qura",
            Method::Karachi => "karachi",
            Method::Tehran => "tehran",
            Method::Jafari => "jafari",
            Method::Dubai => "dubai",
            Method::Kuwait => "kuwait",
            Method::Qatar => "qatar",
            Method::Singapore => "singapore",
            Method::Malaysia => "malaysia",
            Method::Indonesia => "indonesia",
            Method::France => "france",
            Method::Turkey => "turkey",
            Method::Russia => "russia",
            Method::MoonsightingCommittee => "moonsighting_committee",
            Method::Algeria => "algeria",
            Method::Morocco => "morocco",
            Method::Tunisia => "tunisia",
            Method::Custom => "custom",
        }
    }

    pub fn from_id(id: &str) -> Option<Method> {
        Method::ALL.iter().copied().chain([Method::Custom]).find(|m| m.id() == id)
    }

    pub fn display_name(self) -> &'static str {
        match self {
            Method::MuslimWorldLeague => "Muslim World League",
            Method::Isna => "ISNA (North America)",
            Method::Egypt => "Egyptian General Authority of Survey",
            Method::UmmAlQura => "Umm al-Qura, Makkah",
            Method::Karachi => "University of Islamic Sciences, Karachi",
            Method::Tehran => "Institute of Geophysics, Tehran",
            Method::Jafari => "Shia Ithna-Ashari (Jafari)",
            Method::Dubai => "Dubai (UAE)",
            Method::Kuwait => "Kuwait",
            Method::Qatar => "Qatar",
            Method::Singapore => "Singapore (MUIS)",
            Method::Malaysia => "Malaysia (JAKIM)",
            Method::Indonesia => "Indonesia (Kemenag)",
            Method::France => "France (UOIF 12°)",
            Method::Turkey => "Turkey (Diyanet)",
            Method::Russia => "Russia",
            Method::MoonsightingCommittee => "Moonsighting Committee Worldwide",
            Method::Algeria => "Algeria (Ministry of Religious Affairs)",
            Method::Morocco => "Morocco (Habous)",
            Method::Tunisia => "Tunisia",
            Method::Custom => "Custom",
        }
    }

    /// A sensible default method for an ISO-3166 alpha-2 country code.
    pub fn for_country(code: &str) -> Method {
        match code.to_ascii_uppercase().as_str() {
            "SA" | "YE" | "BH" | "OM" => Method::UmmAlQura,
            "AE" => Method::Dubai,
            "KW" => Method::Kuwait,
            "QA" => Method::Qatar,
            "EG" | "SD" | "LY" | "SY" | "LB" | "IQ" | "JO" | "PS" => Method::Egypt,
            "DZ" => Method::Algeria,
            "MA" => Method::Morocco,
            "TN" => Method::Tunisia,
            "PK" | "IN" | "BD" | "AF" => Method::Karachi,
            "IR" => Method::Tehran,
            "TR" => Method::Turkey,
            "RU" => Method::Russia,
            "SG" => Method::Singapore,
            "MY" | "BN" => Method::Malaysia,
            "ID" => Method::Indonesia,
            "FR" => Method::France,
            "US" | "CA" => Method::Isna,
            "GB" => Method::MoonsightingCommittee,
            _ => Method::MuslimWorldLeague,
        }
    }
}

/// How Isha (or Maghrib) is determined.
#[derive(Debug, Clone, Copy, PartialEq)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize), serde(tag = "type", content = "value", rename_all = "snake_case"))]
pub enum Twilight {
    /// Sun depression angle below the horizon, in degrees.
    Angle(f64),
    /// Fixed interval in minutes after Maghrib (for Isha) or after sunset (for Maghrib).
    Minutes(f64),
}

/// Juristic school for Asr.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize), serde(rename_all = "snake_case"))]
pub enum Madhab {
    /// Shafi'i, Maliki, Hanbali, Jafari: shadow = object length + noon shadow.
    Standard,
    /// Hanafi: shadow = 2 × object length + noon shadow.
    Hanafi,
}

impl Madhab {
    pub fn shadow_factor(self) -> f64 {
        match self {
            Madhab::Standard => 1.0,
            Madhab::Hanafi => 2.0,
        }
    }
}

/// Rule applied when Fajr/Isha twilight is undefined or unreasonable (high latitudes).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize), serde(rename_all = "snake_case"))]
pub enum HighLatitudeRule {
    /// Choose automatically from latitude (seventh of night above 48°, middle of night otherwise).
    Auto,
    /// Fajr no earlier than the middle of the night; Isha no later than it.
    MiddleOfTheNight,
    /// Fajr no earlier than the last seventh of the night; Isha no later than the first seventh.
    SeventhOfTheNight,
    /// Portion of the night = angle / 60.
    TwilightAngle,
    /// No safeguard (times may be missing at high latitudes).
    None,
}

/// Rule applied when the sun does not rise or set at all (polar day/night).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize), serde(rename_all = "snake_case"))]
pub enum PolarRule {
    /// "Aqrab al-bilād": use the times of the nearest latitude (±65°) where events occur.
    NearestLatitude,
    /// Leave times unresolved; the UI must explain why.
    Unresolved,
}

/// How the end of the night (midnight) is computed.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize), serde(rename_all = "snake_case"))]
pub enum MidnightMethod {
    /// Midpoint between sunset and next sunrise.
    Standard,
    /// Midpoint between sunset and next Fajr.
    Jafari,
}

/// Rounding policy applied to output times.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize), serde(rename_all = "snake_case"))]
pub enum Rounding {
    /// Keep full second precision.
    None,
    /// Round to the nearest minute.
    Nearest,
    /// Precautionary: round prayer *starts* up and sunrise (end of Fajr) down, so the
    /// displayed time is never earlier than the true start of a prayer.
    Safe,
}

/// Per-event minute adjustments.
#[derive(Debug, Clone, Copy, PartialEq, Default)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize), serde(default))]
pub struct Adjustments {
    pub fajr: f64,
    pub sunrise: f64,
    pub dhuhr: f64,
    pub asr: f64,
    pub maghrib: f64,
    pub isha: f64,
}

impl Adjustments {
    pub const ZERO: Adjustments = Adjustments { fajr: 0.0, sunrise: 0.0, dhuhr: 0.0, asr: 0.0, maghrib: 0.0, isha: 0.0 };
}

/// Full set of calculation parameters.
#[derive(Debug, Clone, PartialEq)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize), serde(default))]
pub struct Params {
    pub method: Method,
    pub fajr_angle: f64,
    pub isha: Twilight,
    /// Isha interval used during Ramadan when `isha` is `Minutes` (Umm al-Qura: 120).
    pub isha_ramadan_minutes: Option<f64>,
    /// Maghrib definition. `Minutes(0)` = at sunset.
    pub maghrib: Twilight,
    pub madhab: Madhab,
    pub high_latitude_rule: HighLatitudeRule,
    pub polar_rule: PolarRule,
    pub midnight: MidnightMethod,
    pub rounding: Rounding,
    /// Authority-defined offsets (minutes). Part of the method; not usually edited.
    pub method_adjustments: Adjustments,
    /// User offsets (minutes) added on top of method adjustments.
    pub adjustments: Adjustments,
    /// Observer elevation in metres; lowers the apparent horizon for sunrise/sunset.
    pub elevation_m: f64,
    /// Minutes before Fajr for Imsak (fasting cut-off reminder).
    pub imsak_minutes: f64,
}

impl Default for Params {
    fn default() -> Self {
        Params::for_method(Method::MuslimWorldLeague)
    }
}

impl Params {
    pub fn for_method(method: Method) -> Params {
        let mut p = Params {
            method,
            fajr_angle: 18.0,
            isha: Twilight::Angle(17.0),
            isha_ramadan_minutes: None,
            maghrib: Twilight::Minutes(0.0),
            madhab: Madhab::Standard,
            high_latitude_rule: HighLatitudeRule::Auto,
            polar_rule: PolarRule::NearestLatitude,
            midnight: MidnightMethod::Standard,
            rounding: Rounding::Safe,
            method_adjustments: Adjustments::ZERO,
            adjustments: Adjustments::ZERO,
            elevation_m: 0.0,
            imsak_minutes: 10.0,
        };
        let (f, i) = match method {
            Method::MuslimWorldLeague | Method::Algeria => (18.0, Twilight::Angle(17.0)),
            Method::Isna => (15.0, Twilight::Angle(15.0)),
            Method::Egypt => (19.5, Twilight::Angle(17.5)),
            Method::UmmAlQura => (18.5, Twilight::Minutes(90.0)),
            Method::Karachi | Method::Tunisia => (18.0, Twilight::Angle(18.0)),
            Method::Tehran => (17.7, Twilight::Angle(14.0)),
            Method::Jafari => (16.0, Twilight::Angle(14.0)),
            Method::Dubai => (18.2, Twilight::Angle(18.2)),
            Method::Kuwait => (18.0, Twilight::Angle(17.5)),
            Method::Qatar => (18.0, Twilight::Minutes(90.0)),
            Method::Singapore | Method::Malaysia | Method::Indonesia => (20.0, Twilight::Angle(18.0)),
            Method::France => (12.0, Twilight::Angle(12.0)),
            Method::Turkey => (18.0, Twilight::Angle(17.0)),
            Method::Russia => (16.0, Twilight::Angle(15.0)),
            Method::MoonsightingCommittee => (18.0, Twilight::Angle(18.0)),
            Method::Morocco => (19.0, Twilight::Angle(17.0)),
            Method::Custom => (18.0, Twilight::Angle(17.0)),
        };
        p.fajr_angle = f;
        p.isha = i;
        match method {
            Method::UmmAlQura => p.isha_ramadan_minutes = Some(120.0),
            Method::Tehran => {
                p.maghrib = Twilight::Angle(4.5);
                p.midnight = MidnightMethod::Jafari;
            }
            Method::Jafari => {
                p.maghrib = Twilight::Angle(4.0);
                p.midnight = MidnightMethod::Jafari;
            }
            Method::Turkey => {
                p.method_adjustments =
                    Adjustments { fajr: 0.0, sunrise: -7.0, dhuhr: 5.0, asr: 4.0, maghrib: 7.0, isha: 0.0 };
            }
            Method::MoonsightingCommittee => {
                p.method_adjustments = Adjustments { dhuhr: 5.0, maghrib: 3.0, ..Adjustments::ZERO };
            }
            _ => {}
        }
        p
    }

    /// Resolve `Auto` into a concrete high-latitude rule for a latitude.
    pub fn effective_high_latitude_rule(&self, latitude: f64) -> HighLatitudeRule {
        match self.high_latitude_rule {
            HighLatitudeRule::Auto => {
                if latitude.abs() > 48.0 {
                    HighLatitudeRule::SeventhOfTheNight
                } else {
                    HighLatitudeRule::MiddleOfTheNight
                }
            }
            r => r,
        }
    }
}
