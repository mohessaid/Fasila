import SwiftUI

/// The living sky: a 4-stop gradient from the shared core with 45 twinkling stars that
/// appear only after sunset. Updates the stars ten times a second (2-6 s twinkles look the
/// same, and per-frame redraws cost battery all night); static under Reduce Motion.
struct LivingSky<Content: View>: View {
    @Environment(FasilaStore.self) private var store
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @ViewBuilder var content: () -> Content

    var body: some View {
        TimelineView(.everyMinute) { ctx in
            let sky = store.snapshot(at: ctx.date)?.sky ?? .night
            ZStack {
                sky.gradient
                    .ignoresSafeArea()
                    .animation(reduceMotion ? nil : .easeInOut(duration: 2.5), value: sky)
                if sky.starOpacity > 0.01 {
                    StarField(opacity: sky.starOpacity, animate: !reduceMotion)
                        .ignoresSafeArea()
                        .allowsHitTesting(false)
                }
                content()
            }
            .environment(\.skyLook, sky)
        }
    }
}

private struct Star { let x, y, r, max, period, phase: Double }

private struct StarField: View {
    let opacity: Double
    let animate: Bool
    private static let stars: [Star] = {
        var g = SystemRandomNumberGenerator()
        return (0..<45).map { _ in
            Star(x: .random(in: 0...1, using: &g), y: .random(in: 0...0.8, using: &g), r: .random(in: 0.25...1.25, using: &g),
                 max: .random(in: 0.28...0.9, using: &g), period: .random(in: 2...6, using: &g), phase: .random(in: 0...3, using: &g))
        }
    }()

    var body: some View {
        TimelineView(.periodic(from: .now, by: animate ? 0.1 : 3600)) { ctx in
            Canvas { gc, size in
                let t = ctx.date.timeIntervalSinceReferenceDate
                for s in Self.stars {
                    let tw = animate ? 0.79 + 0.21 * sin(2 * .pi * (t + s.phase) / s.period) : 1
                    let rect = CGRect(x: s.x * size.width - s.r, y: s.y * size.height - s.r, width: s.r * 2, height: s.r * 2)
                    gc.fill(Path(ellipseIn: rect), with: .color(.white.opacity(min(1, s.max * tw * opacity))))
                }
            }
        }
    }
}
