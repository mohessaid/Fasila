import SwiftUI

/// Today's hero (docs/DESIGN.md, Celestial scene), ported from the verified Android
/// version: two mountain layers, the sun on its path from sunrise to Maghrib with Dhuhr
/// and Asr marked, and at night the moon in its real, computed phase (mirrored in the
/// southern hemisphere). Below: the next prayer, the countdown and its time.
struct CelestialHero: View {
    let snapshot: PrayerSnapshot
    let formatter: FasilaFormatter
    let language: AppLanguage
    let latitude: Double

    @Environment(\.layoutDirection) private var layoutDirection

    private var sky: SkyLook { snapshot.sky }
    private var day: DayInfo { snapshot.today }

    var body: some View {
        VStack(spacing: 2) {
            Canvas { ctx, size in draw(in: &ctx, size: size) }
                .aspectRatio(600.0 / 268.0, contentMode: .fit)
                .accessibilityHidden(true)
            if let next = snapshot.next {
                Text(language.tr("today.isNext %@", language.tr(next.key.localizationKey)))
                    .font(.fasila(.title2, .semibold))
                    .shadow(color: fasilaNight.opacity(0.45), radius: 9, y: 1)
                Text(verbatim: formatter.countdown(snapshot.remaining))
                    .font(.fasila(size: 58, .light))
                    .monospacedDigit()
                    .shadow(color: fasilaNight.opacity(0.45), radius: 9, y: 1)
                    .background {
                        // A soft veil of darker sky keeps the countdown legible on bright skies.
                        Ellipse()
                            .fill(RadialGradient(colors: [fasilaNight.opacity(sky.glassTint * 0.5), fasilaNight.opacity(sky.glassTint * 0.2), .clear],
                                                 center: .center, startRadius: 0, endRadius: 170))
                            .frame(width: 380, height: 150)
                            .blur(radius: 12)
                    }
                Text(language.tr("today.at %@", formatter.time(next.date)))
                    .font(.fasila(.subheadline, .medium))
                    .monospacedDigit()
                    .skyPill(sky)
                    .padding(.top, 4)
            }
            if !sky.isDaytime {
                Text(lunarLine)
                    .font(.fasila(.caption, .semibold))
                    .tracking(2)
                    .foregroundStyle(fasilaAccent)
                    .padding(.top, 10)
            }
        }
        .onGlassPrimary()
        .accessibilityElement(children: .combine)
    }

    /// After Maghrib the Hijri day has begun.
    private var hijriDay: Int {
        if let mg = day.time(.maghrib), snapshot.now >= mg, let t = snapshot.tomorrow { return Int(t.hijriDay) }
        return Int(day.hijriDay)
    }

    private var lunarLine: String {
        if (13...15).contains(hijriDay) { return language.tr("today.whiteDays") }
        let pct = formatter.number(Int((sky.moonIllumination * 100).rounded()))
        return language.tr("today.lunarDay %@ %@", formatter.number(hijriDay), pct)
    }

    // MARK: Drawing (in a 600 x 268 design space)

    private func draw(in ctx: inout GraphicsContext, size: CGSize) {
        let s = size.width / 600
        let rtl = layoutDirection == .rightToLeft
        func arc(_ p: Double) -> CGPoint {
            let a = Double.pi - p * Double.pi
            let x = 24 + 276 + cos(a) * 276
            let y = 204 - sin(a) * 144
            return CGPoint(x: (rtl ? 600 - x : x) * s, y: y * s)
        }
        // Sun's path, dotted.
        var path = Path()
        for i in 0...48 {
            let p = arc(Double(i) / 48)
            if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
        }
        ctx.stroke(path, with: .color(.white.opacity(sky.isDaytime ? 0.24 : 0.1)),
                   style: StrokeStyle(lineWidth: 1.2, lineCap: .round, dash: [2, 6]))
        if let sr = day.time(.sunrise), let mg = day.time(.maghrib), mg > sr {
            for p in [0.0, 1.0] { dot(&ctx, arc(p), 2.5 * s, .white.opacity(0.5)) }
            for key in [PrayerKey.dhuhr, .asr] {
                guard let t = day.time(key) else { continue }
                let p = arc(min(1, max(0, t.timeIntervalSince(sr) / mg.timeIntervalSince(sr))))
                let isNext = snapshot.next?.key == key && snapshot.next?.date == t
                dot(&ctx, p, 3.5 * s, isNext ? fasilaAccent : .white.opacity(0.6))
                let label = ctx.resolve(Text(language.tr(key.localizationKey)).font(.fasila(size: 11, .medium)).foregroundColor(fasilaInk))
                ctx.draw(label, at: CGPoint(x: p.x, y: p.y - 14 * s), anchor: .bottom)
            }
        }
        mountains(&ctx, s: s)
        if sky.isDaytime {
            sun(&ctx, at: arc(sky.sun), body: 92 * s)
        } else {
            let c = CGPoint(x: 300 * s, y: 118 * s)
            var mc = ctx
            if latitude < 0 {
                // Seen from the southern hemisphere the moon is mirrored.
                mc.translateBy(x: c.x * 2, y: 0)
                mc.scaleBy(x: -1, y: 1)
            }
            moon(&mc, at: c, body: 92 * s, phase: sky.moonPhase)
        }
    }

    private func dot(_ ctx: inout GraphicsContext, _ p: CGPoint, _ r: Double, _ color: Color) {
        ctx.fill(Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: 2 * r, height: 2 * r)), with: .color(color))
    }

    private func mountains(_ ctx: inout GraphicsContext, s: Double) {
        func layer(_ pts: [(Double, Double)], _ top: Double) {
            var p = Path()
            p.move(to: CGPoint(x: 0, y: 268 * s))
            for (x, y) in pts { p.addLine(to: CGPoint(x: x * 600 * s, y: y * s)) }
            p.addLine(to: CGPoint(x: 600 * s, y: 268 * s))
            p.closeSubpath()
            ctx.fill(p, with: .linearGradient(
                Gradient(stops: [.init(color: fasilaInk.opacity(top), location: 0),
                                 .init(color: fasilaInk.opacity(top * 0.32), location: 0.68),
                                 .init(color: .clear, location: 0.8)]),
                startPoint: CGPoint(x: 0, y: 190 * s), endPoint: CGPoint(x: 0, y: 268 * s)))
        }
        let day = sky.isDaytime
        layer([(0, 238), (0.1, 214), (0.18, 228), (0.3, 192), (0.43, 226), (0.55, 204), (0.68, 231), (0.8, 200), (1, 226)], day ? 0.12 : 0.2)
        layer([(0, 252), (0.11, 236), (0.21, 246), (0.33, 218), (0.45, 247), (0.56, 224), (0.68, 248), (0.8, 214), (1, 239)], day ? 0.18 : 0.26)
    }

    private func sun(_ ctx: inout GraphicsContext, at c: CGPoint, body r: Double) {
        let glow = 0.49 * r * 1.09
        ctx.fill(Path(ellipseIn: CGRect(x: c.x - glow, y: c.y - glow, width: 2 * glow, height: 2 * glow)),
                 with: .radialGradient(Gradient(stops: [.init(color: .white.opacity(0.18), location: 0),
                                                        .init(color: fasilaAccent.opacity(0.11), location: 0.32),
                                                        .init(color: .clear, location: 1)]),
                                       center: c, startRadius: 0, endRadius: glow))
        let d = 0.235 * r
        ctx.fill(Path(ellipseIn: CGRect(x: c.x - d, y: c.y - d, width: 2 * d, height: 2 * d)),
                 with: .radialGradient(Gradient(stops: [.init(color: fasilaInk, location: 0),
                                                        .init(color: fasilaInk.opacity(0.96), location: 0.44),
                                                        .init(color: fasilaAccent.opacity(0.96), location: 0.78),
                                                        .init(color: fasilaAccent, location: 1)]),
                                       center: CGPoint(x: c.x - 0.08 * r, y: c.y - 0.1 * r), startRadius: 0, endRadius: 0.36 * r))
    }

    /// Moon disc with the lit region for `phase` (0 new, 0.5 full), craters and glow.
    private func moon(_ ctx: inout GraphicsContext, at c: CGPoint, body: Double, phase: Double) {
        let r = 0.28 * body
        let glow = 0.4 * body
        let muted = Color(red: 203 / 255, green: 213 / 255, blue: 225 / 255)
        ctx.fill(Path(ellipseIn: CGRect(x: c.x - glow, y: c.y - glow, width: 2 * glow, height: 2 * glow)),
                 with: .radialGradient(Gradient(stops: [.init(color: fasilaInk.opacity(0.32), location: 0),
                                                        .init(color: muted.opacity(0.12), location: 0.45),
                                                        .init(color: .clear, location: 1)]),
                                       center: c, startRadius: 0, endRadius: glow))
        ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)), with: .color(fasilaNight.opacity(0.72)))
        if phase > 0.005 && phase < 0.995 {
            let waxing = phase < 0.5
            let e = waxing ? phase : phase - 0.5
            let rx = e < 0.25 ? r * (1 - e / 0.25) : r * ((e - 0.25) / 0.25)
            // Gibbous: the terminator bulges away from the lit limb; crescent: towards it.
            let gibbous = waxing ? e >= 0.25 : e < 0.25
            let bulgeRight = waxing != gibbous
            var lit = Path()
            // Outer limb: right half while waxing, left half while waning (top to bottom).
            lit.addArc(center: c, radius: r, startAngle: .degrees(-90), endAngle: .degrees(90), clockwise: !waxing)
            // Terminator from bottom back to top: half an ellipse of half-width rx.
            let steps = 32
            for i in 0...steps {
                let t = Double(i) / Double(steps) // 0 at bottom, 1 at top
                let a = Double.pi / 2 - t * Double.pi // +90deg (bottom) -> -90deg (top)
                let x = c.x + (bulgeRight ? 1 : -1) * rx * cos(a)
                let y = c.y + r * sin(a)
                lit.addLine(to: CGPoint(x: x, y: y))
            }
            lit.closeSubpath()
            ctx.fill(lit, with: .linearGradient(
                Gradient(colors: [fasilaInk, Color(red: 226 / 255, green: 232 / 255, blue: 240 / 255), muted]),
                startPoint: CGPoint(x: c.x - r, y: c.y - r), endPoint: CGPoint(x: c.x + r, y: c.y + r)))
        }
        for (dx, dy, rr) in [(-0.36, -0.22, 0.11), (0.2, 0.28, 0.08), (0.38, -0.15, 0.055)] {
            dot(&ctx, CGPoint(x: c.x + dx * r, y: c.y + dy * r), rr * r, muted.opacity(0.16))
        }
    }
}
