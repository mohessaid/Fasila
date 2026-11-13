import Foundation

/// Mirror of `fasila_core::schedule::Item` (serde JSON shape):
///
/// ```json
/// {"id":"…","title":"Meeting","kind":"event",
///  "start":{"key":"asr","offset_minutes":30},
///  "end":{"type":"duration","minutes":60},
///  "recurrence":{"type":"daily"},
///  "enabled":true,"remind_before_minutes":null,"notes":""}
/// ```
struct Anchor: Codable, Hashable, Sendable {
    var key: String
    var offsetMinutes: Int

    enum CodingKeys: String, CodingKey {
        case key
        case offsetMinutes = "offset_minutes"
    }

    var prayerKey: PrayerKey? { PrayerKey(rawValue: key) }

    func describe(lang: String) -> String {
        describeAnchor(key: key, offsetMinutes: Int32(offsetMinutes), lang: lang)
    }
}

enum ItemEnd: Codable, Hashable, Sendable {
    /// Instantaneous (JSON `"none"`).
    case instant
    case duration(minutes: Int)
    case until(anchor: Anchor)

    private enum CodingKeys: String, CodingKey { case type, minutes, anchor }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        switch try c.decode(String.self, forKey: .type) {
        case "duration": self = .duration(minutes: try c.decode(Int.self, forKey: .minutes))
        case "until": self = .until(anchor: try c.decode(Anchor.self, forKey: .anchor))
        default: self = .instant
        }
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .instant: try c.encode("none", forKey: .type)
        case .duration(let m):
            try c.encode("duration", forKey: .type)
            try c.encode(m, forKey: .minutes)
        case .until(let a):
            try c.encode("until", forKey: .type)
            try c.encode(a, forKey: .anchor)
        }
    }

    var durationMinutes: Int? {
        if case .duration(let m) = self { return m }
        return nil
    }
}

enum Recurrence: Codable, Hashable, Sendable {
    case once(date: CivilDate)
    case daily
    /// Bit 0 = Sunday … bit 6 = Saturday.
    case weekly(days: Int)
    case hijriMonthly(days: [Int])
    case hijriYearly(month: Int, day: Int)
    case hijriMonth(month: Int)

    private enum CodingKeys: String, CodingKey { case type, date, days, month, day }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        switch try c.decode(String.self, forKey: .type) {
        case "once": self = .once(date: try c.decode(CivilDate.self, forKey: .date))
        case "weekly": self = .weekly(days: try c.decode(Int.self, forKey: .days))
        case "hijri_monthly": self = .hijriMonthly(days: try c.decode([Int].self, forKey: .days))
        case "hijri_yearly":
            self = .hijriYearly(month: try c.decode(Int.self, forKey: .month), day: try c.decode(Int.self, forKey: .day))
        case "hijri_month": self = .hijriMonth(month: try c.decode(Int.self, forKey: .month))
        default: self = .daily
        }
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .once(let d):
            try c.encode("once", forKey: .type)
            try c.encode(d, forKey: .date)
        case .daily:
            try c.encode("daily", forKey: .type)
        case .weekly(let days):
            try c.encode("weekly", forKey: .type)
            try c.encode(days, forKey: .days)
        case .hijriMonthly(let days):
            try c.encode("hijri_monthly", forKey: .type)
            try c.encode(days, forKey: .days)
        case .hijriYearly(let m, let d):
            try c.encode("hijri_yearly", forKey: .type)
            try c.encode(m, forKey: .month)
            try c.encode(d, forKey: .day)
        case .hijriMonth(let m):
            try c.encode("hijri_month", forKey: .type)
            try c.encode(m, forKey: .month)
        }
    }
}

enum ItemKind: String, Codable, CaseIterable, Identifiable, Sendable {
    case alarm, task, event
    var id: String { rawValue }
    var symbol: String {
        switch self {
        case .alarm: return "alarm"
        case .task: return "checklist"
        case .event: return "calendar"
        }
    }
}

struct ScheduleItem: Codable, Identifiable, Hashable, Sendable {
    var id: String
    var title: String
    var kind: ItemKind
    var start: Anchor
    var end: ItemEnd
    var recurrence: Recurrence
    var enabled: Bool
    var remindBeforeMinutes: Int?
    var notes: String

    enum CodingKeys: String, CodingKey {
        case id, title, kind, start, end, recurrence, enabled, notes
        case remindBeforeMinutes = "remind_before_minutes"
    }

    init(id: String = UUID().uuidString, title: String, kind: ItemKind, start: Anchor,
         end: ItemEnd = .instant, recurrence: Recurrence = .daily, enabled: Bool = true,
         remindBeforeMinutes: Int? = nil, notes: String = "") {
        self.id = id; self.title = title; self.kind = kind; self.start = start; self.end = end
        self.recurrence = recurrence; self.enabled = enabled
        self.remindBeforeMinutes = remindBeforeMinutes; self.notes = notes
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(String.self, forKey: .id) ?? ""
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        kind = (try? c.decode(ItemKind.self, forKey: .kind)) ?? .alarm
        start = try c.decode(Anchor.self, forKey: .start)
        end = (try? c.decode(ItemEnd.self, forKey: .end)) ?? .instant
        recurrence = (try? c.decode(Recurrence.self, forKey: .recurrence)) ?? .daily
        enabled = try c.decodeIfPresent(Bool.self, forKey: .enabled) ?? true
        remindBeforeMinutes = try c.decodeIfPresent(Int.self, forKey: .remindBeforeMinutes)
        notes = try c.decodeIfPresent(String.self, forKey: .notes) ?? ""
    }

    static func decodeList(_ json: String) -> [ScheduleItem] {
        (try? JSONDecoder().decode([ScheduleItem].self, from: Data(json.utf8))) ?? []
    }

    static func encodeList(_ items: [ScheduleItem]) -> String {
        let enc = JSONEncoder()
        enc.outputFormatting = [.sortedKeys]
        return (try? enc.encode(items)).flatMap { String(data: $0, encoding: .utf8) } ?? "[]"
    }

    init?(json: String) {
        guard let item = try? JSONDecoder().decode(ScheduleItem.self, from: Data(json.utf8)) else { return nil }
        self = item
    }
}
