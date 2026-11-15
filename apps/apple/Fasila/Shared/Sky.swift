import SwiftUI

/// Palette constants shared by the app and the widget.
let fasilaInk = Color(red: 248 / 255, green: 250 / 255, blue: 252 / 255)
let fasilaAccent = Color(red: 1, green: 155 / 255, blue: 84 / 255)
let fasilaNight = Color(red: 6 / 255, green: 10 / 255, blue: 20 / 255)

struct RGB: Equatable, Sendable {
    var r: Double, g: Double, b: Double

    init(hex: UInt32) {
        r = Double((hex >> 16) & 0xFF) / 255
        g = Double((hex >> 8) & 0xFF) / 255
        b = Double(hex & 0xFF) / 255
    }

    init(r: Double, g: Double, b: Double) { self.r = r; self.g = g; self.b = b }

    func mix(_ o: RGB, _ t: Double) -> RGB {
        RGB(r: r + (o.r - r) * t, g: g + (o.g - g) * t, b: b + (o.b - b) * t)
    }

    var color: Color { Color(red: r, green: g, blue: b) }
}

/// The living sky (docs/DESIGN.md), computed by the shared Rust core
/// (`FasilaEngine.sky`): colours pinned to real prayer times, the star curve and the
/// adaptive glass tint that keeps text at WCAG AA on bright skies. Same model as Android.
struct SkyLook: Equatable, Sendable {
    /// Four gradient stops, top to bottom.
    var stops: [RGB]
    var starOpacity: Double
    /// Opacity of the #060A14 tint under glass panels (0.36 at night, up to 0.84).
    var glassTint: Double
    var isDaytime: Bool
    /// 0 at sunrise, 1 at Maghrib.
    var sun: Double
    /// 0 new, 0.25 first quarter, 0.5 full, 0.75 last quarter.
    var moonPhase: Double
    var moonIllumination: Double

    static let night = SkyLook(
        stops: [0x010208, 0x020510, 0x040A1B, 0x07132B].map { RGB(hex: $0) },
        starOpacity: 1, glassTint: 0.36, isDaytime: false, sun: 0, moonPhase: 0.5, moonIllumination: 1)

    init(stops: [RGB], starOpacity: Double, glassTint: Double, isDaytime: Bool, sun: Double,
         moonPhase: Double, moonIllumination: Double) {
        self.stops = stops; self.starOpacity = starOpacity; self.glassTint = glassTint
        self.isDaytime = isDaytime; self.sun = sun; self.moonPhase = moonPhase; self.moonIllumination = moonIllumination
    }

    init(_ s: SkyInfo, moon: MoonInfo) {
        self.init(stops: s.colors.map { RGB(hex: $0) }, starOpacity: s.starOpacity, glassTint: s.glassTint,
                  isDaytime: s.isDaytime, sun: s.sun, moonPhase: moon.phase, moonIllumination: moon.illumination)
    }

    /// The one accent of the palette (warm lamp light).
    var accent: RGB { RGB(hex: 0xFF9B54) }

    var gradient: LinearGradient {
        let c = stops.count == 4 ? stops : SkyLook.night.stops
        return LinearGradient(stops: [
            .init(color: c[0].color, location: 0), .init(color: c[1].color, location: 0.38),
            .init(color: c[2].color, location: 0.72), .init(color: c[3].color, location: 1),
        ], startPoint: .top, endPoint: .bottom)
    }
}

/// A prayer instant on a concrete day.
struct PrayerMoment: Hashable, Sendable {
    let key: PrayerKey
    let date: Date
}

/// Where "now" sits in the prayer day: previous / next prayer and sky period.
struct PrayerSnapshot {
    let now: Date
    /// The local day `now` falls on (in the location's time zone).
    let today: DayInfo
    /// The next local day (its Hijri date begins at today's Maghrib).
    let tomorrow: DayInfo?
    private(set) var previous: PrayerMoment?
    let next: PrayerMoment?
    /// Sky colours, stars, glass tint and moon for `now`, from the core.
    let sky: SkyLook

    /// The gap until the next prayer: the fasila itself.
    var remaining: TimeInterval { next.map { $0.date.timeIntervalSince(now) } ?? 0 }

    /// Fraction of the current gap that has elapsed (0…1).
    var progress: Double {
        guard let p = previous, let n = next else { return 0 }
        let total = n.date.timeIntervalSince(p.date)
        return total > 0 ? min(1, max(0, now.timeIntervalSince(p.date) / total)) : 0
    }

    /// - Parameters:
    ///   - days: consecutive days that include yesterday, today and tomorrow.
    ///   - todayIndex: index of today's local date in `days`.
    ///   - engine: the core engine, for the sky; `nil` falls back to the night sky.
    init?(now: Date, days: [DayInfo], todayIndex: Int, engine: FasilaEngine?) {
        guard days.indices.contains(todayIndex) else { return nil }
        self.now = now
        self.today = days[todayIndex]
        self.tomorrow = days.indices.contains(todayIndex + 1) ? days[todayIndex + 1] : nil

        let prayers: [PrayerMoment] = days.flatMap { d in
            PrayerKey.prayers.compactMap { k in d.time(k).map { PrayerMoment(key: k, date: $0) } }
        }.sorted { $0.date < $1.date }
        previous = prayers.last { $0.date <= now }
        next = prayers.first { $0.date > now }

        // Between sunrise and Dhuhr no obligatory prayer is current (Fajr ends at sunrise).
        if previous?.key == .fajr, let sr = today.time(.sunrise), now >= sr { previous = nil }

        if let engine, let d = CivilDate(iso: today.date),
           let info = try? engine.sky(year: Int32(d.year), month: UInt8(d.month), day: UInt8(d.day),
                                      now: Int64(now.timeIntervalSince1970)) {
            sky = SkyLook(info, moon: moonPhase(unix: Int64(now.timeIntervalSince1970)))
        } else {
            sky = .night
        }
    }
}
