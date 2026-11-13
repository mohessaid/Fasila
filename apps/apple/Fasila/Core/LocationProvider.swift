import CoreLocation
import Foundation

struct ResolvedPlace: Sendable {
    var name: String
    var latitude: Double
    var longitude: Double
    var timezone: String
    var country: String
}

enum LocationError: LocalizedError {
    case denied, unavailable

    var errorDescription: String? {
        switch self {
        case .denied: return AppLanguage.current.tr("location.error.denied")
        case .unavailable: return AppLanguage.current.tr("location.error.unavailable")
        }
    }
}

/// One-shot location fix + reverse geocoding (name, country, time zone).
@MainActor
final class LocationProvider: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var authContinuation: CheckedContinuation<CLAuthorizationStatus, Never>?
    private var fixContinuation: CheckedContinuation<CLLocation, Error>?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
    }

    func currentPlace() async throws -> ResolvedPlace {
        let status = await authorize()
        guard status != .denied, status != .restricted else { throw LocationError.denied }
        let location = try await fix()
        return await Self.resolve(location)
    }

    /// Forward geocoding for a typed city name.
    static func search(_ query: String) async throws -> [ResolvedPlace] {
        let marks = try await CLGeocoder().geocodeAddressString(query)
        return marks.compactMap { mark in
            guard let loc = mark.location else { return nil }
            return place(from: mark, location: loc)
        }
    }

    private static func resolve(_ location: CLLocation) async -> ResolvedPlace {
        if let mark = try? await CLGeocoder().reverseGeocodeLocation(location).first,
           let place = place(from: mark, location: location) {
            return place
        }
        let c = location.coordinate
        return ResolvedPlace(name: String(format: "%.3f, %.3f", c.latitude, c.longitude),
                             latitude: c.latitude, longitude: c.longitude,
                             timezone: TimeZone.current.identifier, country: Locale.current.region?.identifier ?? "")
    }

    private static func place(from mark: CLPlacemark, location: CLLocation) -> ResolvedPlace? {
        let c = location.coordinate
        let name = mark.locality ?? mark.subAdministrativeArea ?? mark.administrativeArea ?? mark.name
            ?? String(format: "%.3f, %.3f", c.latitude, c.longitude)
        return ResolvedPlace(name: name, latitude: c.latitude, longitude: c.longitude,
                             timezone: (mark.timeZone ?? .current).identifier,
                             country: mark.isoCountryCode ?? Locale.current.region?.identifier ?? "")
    }

    private func authorize() async -> CLAuthorizationStatus {
        let status = manager.authorizationStatus
        guard status == .notDetermined else { return status }
        return await withCheckedContinuation { cont in
            authContinuation = cont
            manager.requestWhenInUseAuthorization()
        }
    }

    private func fix() async throws -> CLLocation {
        try await withCheckedThrowingContinuation { cont in
            fixContinuation?.resume(throwing: CancellationError())
            fixContinuation = cont
            manager.requestLocation()
        }
    }

    // MARK: CLLocationManagerDelegate (delivered on the main thread, where the manager was created)

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        MainActor.assumeIsolated {
            guard status != .notDetermined, let cont = authContinuation else { return }
            authContinuation = nil
            cont.resume(returning: status)
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let last = locations.last else { return }
        MainActor.assumeIsolated {
            fixContinuation?.resume(returning: last)
            fixContinuation = nil
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        MainActor.assumeIsolated {
            fixContinuation?.resume(throwing: LocationError.unavailable)
            fixContinuation = nil
        }
    }
}
