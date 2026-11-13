import SwiftUI

/// The day dial: a 270° arc from Fajr to Isha. Prayers are dots on the arc, the
/// sun marker sits at "now", and the segment between the previous and next
/// prayer — the fasila — is highlighted. The arc runs right-to-left in RTL.
struct DayDial: View {
    let day: DayInfo
    let snapshot: PrayerSnapshot
    let formatter: FasilaFormatter
    let lang: AppLanguage

    @Environment(\.skyPalette) private var palette

    private static let startAngle = 135.0 // degrees, SwiftUI coordinates (y down)
    private static let sweep = 270.0

    var body: some View {
        ZStack {
            Canvas { ctx, size in draw(in: &ctx, size: size) }
                .flipsForRightToLeftLayoutDirection(true)
                .accessibilityHidden(true)
            centre
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityElement(children: .combine)
    }

    private var centre: some View {
        VStack(spacing: 4) {
            if let next = snapshot.next {
                Text(LocalizedStringKey(next.key.localizationKey))
                    .font(.title3.weight(.semibold))
                    .onGlassPrimary()
                Text(formatter.countdown(snapshot.remaining))
                    .font(.system(size: 56, weight: .light, design: .rounded))
                    .monospacedDigit()
                    .minimumScaleFactor(0.5)
                    .lineLimit(1)
                    .foregroundStyle(.white)
                    .contentTransition(.numericText(countsDown: true))
                Text(formatter.time(next.date))
                    .font(.subheadline)
                    .onGlassSecondary()
            }
        }
        .padding(48)
    }

    private func fraction(_ date: Date, from start: Date, to end: Date) -> Double {
        let total = end.timeIntervalSince(start)
        guard total > 0 else { return 0 }
        return min(1, max(0, date.timeIntervalSince(start) / total))
    }

    private func point(_ f: Double, center: CGPoint, radius: CGFloat) -> CGPoint {
        let a = Angle.degrees(Self.startAngle + Self.sweep * f).radians
        return CGPoint(x: center.x + radius * CGFloat(cos(a)), y: center.y + radius * CGFloat(sin(a)))
    }

    private func arc(_ from: Double, _ to: Double, center: CGPoint, radius: CGFloat) -> Path {
        Path { p in
            p.addArc(center: center, radius: radius,
                     startAngle: .degrees(Self.startAngle + Self.sweep * from),
                     endAngle: .degrees(Self.startAngle + Self.sweep * to),
                     clockwise: false)
        }
    }

    private func draw(in ctx: inout GraphicsContext, size: CGSize) {
        guard let fajr = day.time(.fajr), let isha = day.time(.isha) else { return }
        let lineWidth: CGFloat = 10
        let radius = min(size.width, size.height) / 2 - lineWidth * 1.5
        let center = CGPoint(x: size.width / 2, y: size.height / 2)
        let stroke = StrokeStyle(lineWidth: lineWidth, lineCap: .round)

        ctx.stroke(arc(0, 1, center: center, radius: radius), with: .color(.white.opacity(0.22)), style: stroke)

        // The fasila: previous prayer → next prayer, when both are on today's arc.
        if let prev = snapshot.previous, let next = snapshot.next, prev.date >= fajr, next.date <= isha {
            let a = fraction(prev.date, from: fajr, to: isha)
            let b = fraction(next.date, from: fajr, to: isha)
            ctx.stroke(arc(a, b, center: center, radius: radius), with: .color(palette.accent.color.opacity(0.9)), style: stroke)
        }

        for key in PrayerKey.listed {
            guard let t = day.time(key) else { continue }
            let p = point(fraction(t, from: fajr, to: isha), center: center, radius: radius)
            let r: CGFloat = key == snapshot.next?.key ? 7 : 5
            let dot = Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: 2 * r, height: 2 * r))
            ctx.fill(dot, with: .color(key == .sunrise ? .white.opacity(0.6) : .white))
        }

        // Sun at "now" during the day; a moon at the arc's end at night.
        let now = snapshot.now
        let isDay = now >= fajr && now <= isha
        let f = isDay ? fraction(now, from: fajr, to: isha) : (now < fajr ? 0 : 1)
        let p = point(f, center: center, radius: radius)
        let r: CGFloat = 11
        let marker = Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: 2 * r, height: 2 * r))
        if isDay {
            ctx.drawLayer { layer in
                layer.addFilter(.shadow(color: palette.accent.color, radius: 8))
                layer.fill(marker, with: .color(palette.accent.color))
            }
        } else {
            ctx.stroke(marker, with: .color(palette.accent.color), lineWidth: 2)
        }
    }
}
