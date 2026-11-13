import Foundation

/// Typed accessors over the shared configuration JSON (`fasila_core::Config`):
///
/// ```json
/// { "location": {"name","latitude","longitude","elevation_m","timezone","country"},
///   "params":   {"method","fajr_angle","isha":{"type","value"},"madhab","high_latitude_rule",
///                "polar_rule","midnight","rounding","adjustments":{"fajr",…},…},
///   "hijri":    {"calendar","day_adjustment","month_starts":[…]},
///   "guard":    {"before_minutes","after_minutes","jumuah_extra_minutes"} }
/// ```
struct ConfigDocument: Equatable {
    var root: JSONValue

    init(json: String) throws { root = try JSONValue(parsing: json) }

    var json: String { root.serialized() }

    // MARK: Location

    var locationName: String {
        get { root["location"]?["name"]?.string ?? "" }
        set { root["location"]?["name"] = .string(newValue) }
    }
    var latitude: Double { root["location"]?["latitude"]?.double ?? 0 }
    var longitude: Double { root["location"]?["longitude"]?.double ?? 0 }
    var timezoneID: String { root["location"]?["timezone"]?.string ?? TimeZone.current.identifier }
    var country: String { root["location"]?["country"]?.string ?? "" }
    var timeZone: TimeZone { TimeZone(identifier: timezoneID) ?? .current }

    /// Replace only the location, keeping the user's calculation choices.
    mutating func replaceLocation(with other: ConfigDocument) {
        root["location"] = other.root["location"]
    }

    // MARK: Calculation

    var method: String {
        get { root["params"]?["method"]?.string ?? "muslim_world_league" }
    }

    /// Switch method, taking *all* method-defined parameters (angles, Isha
    /// interval, method adjustments, rounding) from `template`, which is a config
    /// built by the Rust core for that method, while keeping user choices.
    mutating func applyMethod(_ id: String, template: ConfigDocument?) {
        let keep = ["madhab", "high_latitude_rule", "polar_rule", "rounding", "adjustments", "imsak_minutes", "elevation_m"]
        if let tParams = template?.root["params"], tParams["method"]?.string == id {
            var params = tParams
            for k in keep { params[k] = root["params"]?[k] }
            root["params"] = params
        } else {
            root["params"]?["method"] = .string(id)
        }
    }

    var madhab: String {
        get { root["params"]?["madhab"]?.string ?? "standard" }
        set { root["params"]?["madhab"] = .string(newValue) }
    }
    var highLatitudeRule: String {
        get { root["params"]?["high_latitude_rule"]?.string ?? "auto" }
        set { root["params"]?["high_latitude_rule"] = .string(newValue) }
    }
    var polarRule: String {
        get { root["params"]?["polar_rule"]?.string ?? "nearest_latitude" }
        set { root["params"]?["polar_rule"] = .string(newValue) }
    }
    var rounding: String {
        get { root["params"]?["rounding"]?.string ?? "safe" }
        set { root["params"]?["rounding"] = .string(newValue) }
    }

    /// User offsets in minutes (keys: fajr, sunrise, dhuhr, asr, maghrib, isha).
    func adjustment(_ key: PrayerKey) -> Int {
        root["params"]?["adjustments"]?[key.rawValue]?.int ?? 0
    }
    mutating func setAdjustment(_ key: PrayerKey, _ minutes: Int) {
        if root["params"]?["adjustments"] == nil { root["params"]?["adjustments"] = .object([:]) }
        root["params"]?["adjustments"]?[key.rawValue] = .number(Double(minutes))
    }

    // MARK: Hijri

    var hijriCalendar: String {
        get { root["hijri"]?["calendar"]?.string ?? "umm_al_qura" }
        set { root["hijri"]?["calendar"] = .string(newValue) }
    }
    var hijriDayAdjustment: Int {
        get { root["hijri"]?["day_adjustment"]?.int ?? 0 }
        set { root["hijri"]?["day_adjustment"] = .number(Double(newValue)) }
    }
    var monthStarts: [JSONValue] {
        get { root["hijri"]?["month_starts"]?.array ?? [] }
        set { root["hijri"]?["month_starts"] = .array(newValue) }
    }

    // MARK: Prayer guard

    var guardBefore: Int {
        get { root["guard"]?["before_minutes"]?.int ?? 5 }
        set { root["guard"]?["before_minutes"] = .number(Double(max(0, newValue))) }
    }
    var guardAfter: Int {
        get { root["guard"]?["after_minutes"]?.int ?? 20 }
        set { root["guard"]?["after_minutes"] = .number(Double(max(0, newValue))) }
    }
    var guardJumuah: Int {
        get { root["guard"]?["jumuah_extra_minutes"]?.int ?? 40 }
        set { root["guard"]?["jumuah_extra_minutes"] = .number(Double(max(0, newValue))) }
    }
}

/// Option lists for the settings pickers (ids are the Rust serde names).
enum ConfigOptions {
    static let madhabs = ["standard", "hanafi"]
    static let highLatitudeRules = ["auto", "middle_of_the_night", "seventh_of_the_night", "twilight_angle", "none"]
    static let polarRules = ["nearest_latitude", "unresolved"]
    static let roundings = ["safe", "nearest", "none"]
    static let hijriCalendars = ["umm_al_qura", "tabular"]
}
