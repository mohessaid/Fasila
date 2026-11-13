import SwiftUI

/// The six periods of the living sky (docs/DESIGN.md).
enum SkyPeriod: String, CaseIterable, Sendable {
    case night, fajr, morning, dhuhr, asr, maghrib

    var palette: SkyPalette {
        switch self {
        case .night: return SkyPalette(top: 0x0B1026, bottom: 0x1B2350, accent: 0x8EA2FF)
        case .fajr: return SkyPalette(top: 0x1E2A5A, bottom: 0xC77D8E, accent: 0xFFB4A2)
        case .morning: return SkyPalette(top: 0x5FA8D3, bottom: 0xCDE7F0, accent: 0xFFD166)
        case .dhuhr: return SkyPalette(top: 0x3A86C8, bottom: 0xA9D6F5, accent: 0xFFE08A)
        case .asr: return SkyPalette(top: 0xD98E4A, bottom: 0xF4D29C, accent: 0xFFF1C9)
        case .maghrib: return SkyPalette(top: 0x5B2A63, bottom: 0xE0735A, accent: 0xFFB38A)
        }
    }

    var isLight: Bool { self == .morning || self == .dhuhr || self == .asr }

    /// The prayer-day key that starts this period.
    var startKey: PrayerKey {
        switch self {
        case .night: return .isha
        case .fajr: return .fajr
        case .morning: return .sunrise
        case .dhuhr: return .dhuhr
        case .asr: return .asr
        case .maghrib: return .maghrib
        }
    }
}

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

struct SkyPalette: Equatable, Sendable {
    var top: RGB
    var bottom: RGB
    var accent: RGB
    /// 0 = dark sky, 1 = light sky; drives glass opacity.
    var lightness: Double

    init(top: UInt32, bottom: UInt32, accent: UInt32) {
        self.top = RGB(hex: top); self.bottom = RGB(hex: bottom); self.accent = RGB(hex: accent)
        lightness = [0x5FA8D3, 0x3A86C8, 0xD98E4A].contains(top) ? 1 : 0
    }

    init(top: RGB, bottom: RGB, accent: RGB, lightness: Double) {
        self.top = top; self.bottom = bottom; self.accent = accent; self.lightness = lightness
    }

    func mix(_ o: SkyPalette, _ t: Double) -> SkyPalette {
        SkyPalette(top: top.mix(o.top, t), bottom: bottom.mix(o.bottom, t), accent: accent.mix(o.accent, t),
                   lightness: lightness + (o.lightness - lightness) * t)
    }

    var gradient: LinearGradient {
        LinearGradient(colors: [top.color, bottom.color], startPoint: .top, endPoint: .bottom)
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
    let previous: PrayerMoment?
    let next: PrayerMoment?
    let period: SkyPeriod
    /// Palette with the ~20 minute cross-fade around period boundaries applied.
    let palette: SkyPalette

    /// The gap until the next prayer — the fasila itself.
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
    ///   - crossFade: false under Reduce Motion (instant period change).
    init?(now: Date, days: [DayInfo], todayIndex: Int, crossFade: Bool = true) {
        guard days.indices.contains(todayIndex) else { return nil }
        self.now = now
        self.today = days[todayIndex]

        let prayers: [PrayerMoment] = days.flatMap { d in
            PrayerKey.prayers.compactMap { k in d.time(k).map { PrayerMoment(key: k, date: $0) } }
        }.sorted { $0.date < $1.date }
        previous = prayers.last { $0.date <= now }
        next = prayers.first { $0.date > now }

        let boundaries: [(SkyPeriod, Date)] = days.flatMap { d in
            SkyPeriod.allCases.compactMap { p in d.time(p.startKey).map { (p, $0) } }
        }.sorted { $0.1 < $1.1 }
        let curIdx = boundaries.lastIndex { $0.1 <= now }
        let current = curIdx.map { boundaries[$0].0 } ?? .night
        period = current

        var pal = current.palette
        if crossFade, let i = curIdx {
            let half: TimeInterval = 10 * 60
            let elapsed = now.timeIntervalSince(boundaries[i].1)
            if elapsed < half, i > 0 {
                let t = 0.5 + elapsed / (2 * half)
                pal = boundaries[i - 1].0.palette.mix(current.palette, t)
            } else if i + 1 < boundaries.count {
                let remaining = boundaries[i + 1].1.timeIntervalSince(now)
                if remaining < half {
                    let t = 0.5 - remaining / (2 * half)
                    pal = current.palette.mix(boundaries[i + 1].0.palette, t)
                }
            }
        }
        palette = pal
    }
}
