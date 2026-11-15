import CoreLocation
import SwiftUI

/// Device heading (iOS only — Macs have no magnetometer).
@MainActor
@Observable
final class HeadingProvider: NSObject, CLLocationManagerDelegate {
    /// Degrees clockwise from true north (magnetic if true north is unavailable).
    var heading: Double?
    var accuracy: Double?

    private let manager = CLLocationManager()

    override init() {
        super.init()
        manager.delegate = self
    }

    static var isAvailable: Bool {
        #if os(iOS)
        return CLLocationManager.headingAvailable()
        #else
        return false
        #endif
    }

    func start() {
        #if os(iOS)
        guard Self.isAvailable else { return }
        manager.headingFilter = 1
        if manager.authorizationStatus == .notDetermined { manager.requestWhenInUseAuthorization() }
        // True heading needs a location fix.
        manager.startUpdatingLocation()
        manager.startUpdatingHeading()
        #endif
    }

    func stop() {
        #if os(iOS)
        manager.stopUpdatingHeading()
        manager.stopUpdatingLocation()
        #endif
    }

    #if os(iOS)
    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        let value = newHeading.trueHeading >= 0 ? newHeading.trueHeading : newHeading.magneticHeading
        let acc = newHeading.headingAccuracy
        MainActor.assumeIsolated {
            heading = value
            accuracy = acc
        }
    }

    nonisolated func locationManagerShouldDisplayHeadingCalibration(_ manager: CLLocationManager) -> Bool { true }
    #endif
}

struct QiblaView: View {
    @Environment(FasilaStore.self) private var store
    @State private var compass = HeadingProvider()

    var body: some View {
        let bearing = store.qiblaBearing ?? 0
        let heading = compass.heading ?? 0
        let relative = bearing - heading
        let aligned = compass.heading != nil && abs(angleDifference(bearing, heading)) < 3

        LivingSky {
            VStack(spacing: 24) {
                Text("tab.qibla")
                    .font(.fasila(.largeTitle, .light))
                    .onGlassPrimary()

                ZStack {
                    // Compass rose turns with the device so north stays north.
                    CompassRose()
                        .rotationEffect(.degrees(-heading))
                    // Needle points at the Kaaba.
                    Image(systemName: "location.north.fill")
                        .font(.system(size: 56))
                        .foregroundStyle(aligned ? fasilaAccent : fasilaInk)
                        .offset(y: -110)
                        .rotationEffect(.degrees(relative))
                    Image(systemName: "building.columns.fill") // centre marker
                        .font(.fasila(.title))
                        .onGlassSecondary()
                }
                .frame(width: 300, height: 300)
                .padding(24)
                .glass()
                .animation(.easeOut(duration: 0.2), value: heading)
                .environment(\.layoutDirection, .leftToRight) // geography never mirrors
                .sensoryFeedback(.success, trigger: aligned) { _, new in new }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(verbatim: store.language.tr("qibla.a11y %@", store.formatter.number(Int(bearing.rounded())))))

                VStack(spacing: 6) {
                    Text(verbatim: store.language.tr("qibla.bearing %@", "\(store.formatter.number(Int(bearing.rounded())))°"))
                        .font(.fasila(.title3))
                    if let km = store.distanceToKaabaKm {
                        Text(verbatim: store.language.tr("qibla.distance %@", km.formatted(.number.precision(.fractionLength(0)).locale(store.language.locale))))
                            .font(.fasila(.subheadline))
                            .onGlassSecondary()
                    }
                    if !HeadingProvider.isAvailable {
                        Text("qibla.noCompass")
                            .font(.fasila(.footnote))
                            .multilineTextAlignment(.center)
                            .onGlassSecondary()
                    } else if let acc = compass.accuracy, acc < 0 || acc > 20 {
                        Text("qibla.calibrate")
                            .font(.fasila(.footnote))
                            .foregroundStyle(fasilaWarn)
                    }
                }
                .onGlassPrimary()
                // Text that would otherwise sit on the bright sky goes on a glass panel.
                .padding(.horizontal, 20).padding(.vertical, 14)
                .glass(radius: 20)
            }
            .padding(16)
        }
        .onAppear { compass.start() }
        .onDisappear { compass.stop() }
    }

    private func angleDifference(_ a: Double, _ b: Double) -> Double {
        var d = (a - b).truncatingRemainder(dividingBy: 360)
        if d > 180 { d -= 360 }
        if d < -180 { d += 360 }
        return d
    }
}

private struct CompassRose: View {
    var body: some View {
        ZStack {
            // A frosted dial (no outline ring), lit from above.
            Circle().fill(RadialGradient(colors: [.white.opacity(0.16), .white.opacity(0.04)],
                                         center: UnitPoint(x: 0.5, y: 0.3), startRadius: 0, endRadius: 220))
            ForEach(0..<72, id: \.self) { i in
                Rectangle()
                    .fill(Color.white.opacity(i % 18 == 0 ? 0.9 : 0.35))
                    .frame(width: i % 18 == 0 ? 3 : 1, height: i % 18 == 0 ? 14 : 7)
                    .offset(y: -140)
                    .rotationEffect(.degrees(Double(i) * 5))
            }
            Text(verbatim: "N")
                .font(.fasila(.headline, .semibold))
                .foregroundStyle(.red)
                .offset(y: -115)
        }
    }
}
