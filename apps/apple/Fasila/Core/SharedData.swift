import Foundation

/// A city from the shared list `data/cities.json` (bundled as a resource).
struct City: Decodable, Identifiable, Hashable, Sendable {
    let name: String
    let nameAr: String
    let country: String
    let latitude: Double
    let longitude: Double
    let timezone: String

    var id: String { "\(country)/\(name)" }

    enum CodingKeys: String, CodingKey {
        case name, country, latitude, longitude, timezone
        case nameAr = "name_ar"
    }

    func displayName(lang: String) -> String { lang == "ar" && !nameAr.isEmpty ? nameAr : name }

    static let all: [City] = {
        guard let url = Bundle.main.url(forResource: "cities", withExtension: "json"),
              let data = try? Data(contentsOf: url) else { return [] }
        return (try? JSONDecoder().decode([City].self, from: data)) ?? []
    }()
}

/// The per-country Hijri month-start feed `data/hijri/announcements.json`.
/// A bundled copy ships with the app; `month_starts` for the configured country
/// are merged into `config.hijri.month_starts`.
enum HijriAnnouncements {
    static func monthStarts(country: String) -> [JSONValue] {
        guard let url = Bundle.main.url(forResource: "announcements", withExtension: "json"),
              let text = try? String(contentsOf: url, encoding: .utf8),
              let root = try? JSONValue(parsing: text) else { return [] }
        return root["regions"]?[country.uppercased()]?["month_starts"]?.array ?? []
    }

    /// Merge announced month starts into the config (announcements win over
    /// existing entries for the same Hijri year/month).
    static func merge(into config: inout ConfigDocument) {
        let announced = monthStarts(country: config.country)
        guard !announced.isEmpty else { return }
        func key(_ v: JSONValue) -> String { "\(v["year"]?.int ?? 0)-\(v["month"]?.int ?? 0)" }
        let announcedKeys = Set(announced.map(key))
        config.monthStarts = config.monthStarts.filter { !announcedKeys.contains(key($0)) } + announced
    }
}
