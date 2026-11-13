import Foundation
import SwiftUI

/// The UI language. `system` follows the device; the others override it in-app.
/// The resolved code ("en", "ar", "fr") is also what the Rust core expects for
/// `describe_anchor`, `time_key_name` and the parser.
enum AppLanguage: String, CaseIterable, Identifiable, Sendable {
    case system, en, ar, fr

    var id: String { rawValue }

    static let storageKey = "fasila.language"

    static var current: AppLanguage {
        AppLanguage(rawValue: SharedStorage.defaults.string(forKey: storageKey) ?? "") ?? .system
    }

    /// Two-letter code understood by fasila-core.
    var code: String {
        switch self {
        case .en, .ar, .fr:
            return rawValue
        case .system:
            let preferred = Locale.preferredLanguages.first.map { Locale.Language(identifier: $0).languageCode?.identifier ?? "en" } ?? "en"
            return ["en", "ar", "fr"].contains(preferred) ? preferred : "en"
        }
    }

    var locale: Locale {
        switch self {
        case .system: return .autoupdatingCurrent
        default: return Locale(identifier: rawValue)
        }
    }

    var layoutDirection: LayoutDirection {
        Locale.Language(identifier: code).characterDirection == .rightToLeft ? .rightToLeft : .leftToRight
    }

    /// Native name, shown in the language picker regardless of the UI language.
    var nativeName: String {
        switch self {
        case .system: return AppLanguage.current.tr("language.system")
        case .en: return "English"
        case .ar: return "العربية"
        case .fr: return "Français"
        }
    }

    /// Bundle holding the strings for this language (falls back to main).
    var bundle: Bundle {
        if self != .system,
           let path = Bundle.main.path(forResource: code, ofType: "lproj"),
           let b = Bundle(path: path) {
            return b
        }
        return .main
    }

    /// Localized lookup for code that is not inside a SwiftUI `Text` (notifications,
    /// accessibility strings built in code).
    func tr(_ key: String) -> String {
        bundle.localizedString(forKey: key, value: nil, table: nil)
    }

    func tr(_ key: String, _ args: CVarArg...) -> String {
        String(format: tr(key), locale: locale, arguments: args)
    }
}

extension LocalizedStringKey {
    /// A key built at runtime, e.g. `.key("prayer.\(id)")`.
    ///
    /// `LocalizedStringKey("prayer.\(id)")` would treat the literal as a
    /// *localized interpolation* (key "prayer.%@"); going through a `String`
    /// parameter keeps the whole string as the lookup key.
    static func key(_ value: String) -> LocalizedStringKey { LocalizedStringKey(value) }
}
