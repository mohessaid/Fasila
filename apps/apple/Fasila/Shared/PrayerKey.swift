import Foundation

/// The named instants of a prayer day. Raw values are the Rust `TimeKey` ids.
enum PrayerKey: String, CaseIterable, Identifiable, Codable, Sendable {
    case imsak, fajr, sunrise, dhuhr, asr, sunset, maghrib, isha, midnight
    case lastThird = "last_third"

    var id: String { rawValue }

    /// The five obligatory prayers.
    static let prayers: [PrayerKey] = [.fajr, .dhuhr, .asr, .maghrib, .isha]
    /// What the main prayer list shows (sunrise marks the end of Fajr).
    static let listed: [PrayerKey] = [.fajr, .sunrise, .dhuhr, .asr, .maghrib, .isha]
    /// Secondary times shown under "More times".
    static let extra: [PrayerKey] = [.imsak, .midnight, .lastThird]
    /// Keys a user can anchor an item to in the composer.
    static let anchorable: [PrayerKey] = [.imsak, .fajr, .sunrise, .dhuhr, .asr, .maghrib, .isha, .midnight, .lastThird]

    var isPrayer: Bool { Self.prayers.contains(self) }

    /// String-catalog key for the display name, e.g. "prayer.fajr".
    var localizationKey: String { "prayer.\(rawValue)" }

    var symbol: String {
        switch self {
        case .imsak: return "moon.haze"
        case .fajr: return "sun.haze"
        case .sunrise: return "sunrise"
        case .dhuhr: return "sun.max"
        case .asr: return "sun.min"
        case .sunset: return "sunset"
        case .maghrib: return "sun.horizon"
        case .isha: return "moon.stars"
        case .midnight: return "moon"
        case .lastThird: return "sparkles"
        }
    }
}

extension DayInfo {
    func unix(_ key: PrayerKey) -> Int64? {
        switch key {
        case .imsak: return imsak
        case .fajr: return fajr
        case .sunrise: return sunrise
        case .dhuhr: return dhuhr
        case .asr: return asr
        case .sunset: return sunset
        case .maghrib: return maghrib
        case .isha: return isha
        case .midnight: return midnight
        case .lastThird: return lastThird
        }
    }

    func time(_ key: PrayerKey) -> Date? {
        unix(key).map { Date(timeIntervalSince1970: TimeInterval($0)) }
    }

    func isEstimated(_ key: PrayerKey) -> Bool {
        switch key {
        case .fajr, .imsak: return fajrEstimated || polarFallback
        case .isha: return ishaEstimated || polarFallback
        default: return polarFallback
        }
    }

    /// The civil date as components (parsed from the ISO `date` field).
    var civil: CivilDate? { CivilDate(iso: date) }
}

/// A local civil date (no time, no zone). Mirrors `fasila_core::Date`, which
/// serializes as an ISO "YYYY-MM-DD" string.
struct CivilDate: Hashable, Comparable, Codable, Sendable {
    var year: Int
    var month: Int
    var day: Int

    init(year: Int, month: Int, day: Int) {
        self.year = year; self.month = month; self.day = day
    }

    init?(iso: String) {
        let parts = iso.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        self.init(year: parts[0], month: parts[1], day: parts[2])
    }

    /// The civil date of `date` as seen in `timeZone`.
    init(_ date: Date, in timeZone: TimeZone) {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        let c = cal.dateComponents([.year, .month, .day], from: date)
        self.init(year: c.year ?? 1970, month: c.month ?? 1, day: c.day ?? 1)
    }

    var iso: String { String(format: "%04d-%02d-%02d", year, month, day) }

    func adding(days: Int) -> CivilDate {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        let base = cal.date(from: DateComponents(year: year, month: month, day: day, hour: 12))!
        let next = cal.date(byAdding: .day, value: days, to: base)!
        return CivilDate(next, in: cal.timeZone)
    }

    /// Noon of this date in `timeZone`; safe to format as a calendar date.
    func noon(in timeZone: TimeZone) -> Date {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        return cal.date(from: DateComponents(year: year, month: month, day: day, hour: 12)) ?? Date()
    }

    static func < (a: CivilDate, b: CivilDate) -> Bool {
        (a.year, a.month, a.day) < (b.year, b.month, b.day)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        try c.encode(iso)
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        let s = try c.decode(String.self)
        guard let d = CivilDate(iso: s) else {
            throw DecodingError.dataCorruptedError(in: c, debugDescription: "Expected YYYY-MM-DD, got \(s)")
        }
        self = d
    }
}

extension FasilaEngine {
    func day(_ d: CivilDate) throws -> DayInfo {
        try day(year: Int32(d.year), month: UInt8(d.month), day: UInt8(d.day))
    }

    func days(from d: CivilDate, count: Int) throws -> [DayInfo] {
        try days(year: Int32(d.year), month: UInt8(d.month), day: UInt8(d.day), count: UInt32(max(count, 0)))
    }
}
