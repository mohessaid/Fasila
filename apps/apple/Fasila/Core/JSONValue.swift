import Foundation

/// A loosely-typed JSON tree. The config is edited through this instead of a
/// rigid Codable mirror so that fields written by newer Fasila apps (unknown to
/// this build) survive a round trip — the shared schema promises that.
enum JSONValue: Equatable {
    case null
    case bool(Bool)
    case number(Double)
    case string(String)
    case array([JSONValue])
    case object([String: JSONValue])

    init(parsing text: String) throws {
        let parsed = try JSONSerialization.jsonObject(with: Data(text.utf8), options: [.fragmentsAllowed])
        self = JSONValue(foundation: parsed)
    }

    init(foundation object: Any) {
        switch object {
        case let n as NSNumber:
            // JSONSerialization returns booleans as CFBoolean-backed NSNumbers.
            if CFGetTypeID(n) == CFBooleanGetTypeID() {
                self = .bool(n.boolValue)
            } else {
                self = .number(n.doubleValue)
            }
        case let s as String: self = .string(s)
        case let a as [Any]: self = .array(a.map(JSONValue.init(foundation:)))
        case let o as [String: Any]: self = .object(o.mapValues(JSONValue.init(foundation:)))
        default: self = .null
        }
    }

    /// Foundation representation. Integral numbers become `Int` so that serde
    /// can read them into integer fields (`5`, not `5.0`).
    var foundationObject: Any {
        switch self {
        case .null: return NSNull()
        case .bool(let b): return b
        case .number(let d):
            if d.rounded() == d, abs(d) < 9.0e15 { return Int(d) }
            return d
        case .string(let s): return s
        case .array(let a): return a.map(\.foundationObject)
        case .object(let o): return o.mapValues(\.foundationObject)
        }
    }

    func serialized() -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: foundationObject, options: [.fragmentsAllowed, .sortedKeys]) else {
            return "null"
        }
        return String(data: data, encoding: .utf8) ?? "null"
    }

    subscript(key: String) -> JSONValue? {
        get {
            if case .object(let o) = self { return o[key] }
            return nil
        }
        set {
            var o: [String: JSONValue]
            if case .object(let existing) = self { o = existing } else { o = [:] }
            o[key] = newValue
            self = .object(o)
        }
    }

    var string: String? { if case .string(let s) = self { return s }; return nil }
    var double: Double? { if case .number(let d) = self { return d }; return nil }
    var int: Int? { double.map { Int($0.rounded()) } }
    var bool: Bool? { if case .bool(let b) = self { return b }; return nil }
    var array: [JSONValue]? { if case .array(let a) = self { return a }; return nil }
}
