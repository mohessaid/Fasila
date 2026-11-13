import Foundation

/// Where Fasila keeps its two JSON documents (the shared config schema from
/// `fasila_core::config` and the list of schedule items).
///
/// On iOS the files live in the App Group container so the widget extension can
/// read them; elsewhere (macOS, or when the group is not provisioned) they live
/// in the app's Application Support directory.
enum SharedStorage {
    static let appGroup = "group.app.fasila.shared"

    static var directory: URL {
        let fm = FileManager.default
        #if os(iOS)
        if let group = fm.containerURL(forSecurityApplicationGroupIdentifier: appGroup) {
            return group
                .appendingPathComponent("Library", isDirectory: true)
                .appendingPathComponent("Application Support", isDirectory: true)
                .appendingPathComponent("Fasila", isDirectory: true)
        }
        #endif
        let base = (try? fm.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true))
            ?? fm.temporaryDirectory
        return base.appendingPathComponent("Fasila", isDirectory: true)
    }

    static var configURL: URL { directory.appendingPathComponent("config.json") }
    static var itemsURL: URL { directory.appendingPathComponent("items.json") }

    static func read(_ url: URL) -> String? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func write(_ text: String, to url: URL) throws {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try Data(text.utf8).write(to: url, options: .atomic)
    }

    /// Preferences that are app-side only (not part of the shared config schema).
    static var defaults: UserDefaults {
        #if os(iOS)
        return UserDefaults(suiteName: appGroup) ?? .standard
        #else
        return .standard
        #endif
    }
}
