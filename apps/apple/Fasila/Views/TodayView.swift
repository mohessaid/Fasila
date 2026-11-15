import SwiftUI

struct TodayView: View {
    @Environment(FasilaStore.self) private var store

    var body: some View {
        LivingSky {
            TimelineView(.periodic(from: .now, by: 1)) { ctx in
                if let snap = store.snapshot(at: ctx.date) {
                    content(snap)
                } else {
                    ContentUnavailableView("today.unavailable", systemImage: "exclamationmark.triangle",
                                           description: Text(store.errorMessage ?? ""))
                        .onGlassPrimary()
                }
            }
        }
    }

    private func content(_ snap: PrayerSnapshot) -> some View {
        let day = snap.today
        let fmt = store.formatter
        return ScrollView {
            VStack(spacing: 16) {
                header(day, fmt: fmt)

                // The scene sits directly on the sky; no card around it.
                CelestialHero(snapshot: snap, formatter: fmt, language: store.language,
                              latitude: store.config?.latitude ?? 0)
                    .frame(maxWidth: 560)
                    .frame(maxWidth: .infinity)
                    .accessibilityLabel(dialLabel(snap, fmt: fmt))

                // One panel: the next prayer is a lit row, the current one a quiet pane.
                VStack(spacing: 2) {
                    ForEach(PrayerKey.listed) { key in
                        PrayerRow(key: key, day: day, snapshot: snap, formatter: fmt)
                    }
                    HStack {
                        ForEach(PrayerKey.extra) { key in
                            VStack(spacing: 3) {
                                Text(LocalizedStringKey(key.localizationKey)).font(.fasila(.caption)).onGlassSecondary()
                                Text(verbatim: fmt.time(unix: day.unix(key))).font(.fasila(.caption, .medium)).monospacedDigit()
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                    .padding(.top, 12)
                }
                .padding(10)
                .glass()

                if day.polarFallback || day.fajrEstimated || day.ishaEstimated {
                    Label(LocalizedStringKey(day.polarFallback ? "estimated.polar" : "estimated.highLatitude"), systemImage: "info.circle")
                        .font(.fasila(.footnote))
                        .onGlassSecondary()
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .glass(radius: 16)
                }
            }
            .padding(16)
            .frame(maxWidth: 640)
            .frame(maxWidth: .infinity)
        }
    }

    private func header(_ day: DayInfo, fmt: FasilaFormatter) -> some View {
        VStack(spacing: 4) {
            Text(store.config?.locationName ?? "")
                .font(.fasila(.headline, .semibold))
                .onGlassPrimary()
            if let civil = day.civil {
                Text(fmt.longDate(civil))
                    .font(.fasila(.subheadline))
                    .onGlassSecondary()
            }
            Text(fmt.hijri(day, lang: store.lang))
                .font(.fasila(.subheadline))
                .onGlassSecondary()
            if !day.occasions.isEmpty {
                Text(day.occasions.map { store.language.tr("occasion.\($0)") }.joined(separator: ", "))
                    .font(.fasila(.footnote, .medium))
                    .foregroundStyle(fasilaAccent)
                    .padding(.top, 2)
            }
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }

    private func dialLabel(_ snap: PrayerSnapshot, fmt: FasilaFormatter) -> String {
        guard let next = snap.next else { return "" }
        let name = store.language.tr(next.key.localizationKey)
        return store.language.tr("a11y.next %@ %@ %@", name, fmt.countdown(snap.remaining), fmt.time(next.date))
    }
}

struct PrayerRow: View {
    let key: PrayerKey
    let day: DayInfo
    let snapshot: PrayerSnapshot
    let formatter: FasilaFormatter
    var secondary = false

    @Environment(FasilaStore.self) private var store
    @State private var showInfo = false

    private var time: Date? { day.time(key) }
    private var isNext: Bool { snapshot.next?.key == key && snapshot.next?.date == time }
    private var isCurrent: Bool { snapshot.previous?.key == key && snapshot.previous?.date == time }

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: key.symbol)
                .frame(width: 24)
                .foregroundStyle(isNext ? fasilaAccent : fasilaInk.opacity(0.8))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 1) {
                Text(LocalizedStringKey(key.localizationKey))
                    .font(secondary ? .fasila(.subheadline) : (isNext ? .fasila(.title3, .semibold) : .fasila(.body, .medium)))
                if isNext, let t = time {
                    Text(verbatim: store.language.tr("today.in %@", formatter.countdown(t.timeIntervalSince(snapshot.now))))
                        .font(.fasila(.caption)).monospacedDigit().onGlassSecondary()
                } else if isCurrent {
                    Text("today.now").font(.fasila(.caption)).onGlassSecondary()
                }
            }
            if day.isEstimated(key) {
                Button { showInfo = true } label: {
                    Text("badge.estimated")
                        .font(.fasila(.caption2, .semibold))
                        .foregroundStyle(fasilaWarn)
                }
                .buttonStyle(.plain)
                .popover(isPresented: $showInfo) {
                    Text(LocalizedStringKey(day.polarFallback ? "estimated.polar" : "estimated.highLatitude"))
                        .font(.fasila(.footnote))
                        .padding()
                        .frame(maxWidth: 300)
                        .presentationCompactAdaptation(.popover)
                }
            }
            Spacer(minLength: 8)
            Text(formatter.time(unix: day.unix(key)))
                .monospacedDigit()
                .font(secondary ? .fasila(.subheadline) : (isNext ? .fasila(.title3, .semibold) : .fasila(.body)))
        }
        .onGlassPrimary()
        .opacity(secondary ? 0.85 : 1)
        .padding(.vertical, secondary ? 8 : (isNext ? 14 : 11))
        .padding(.horizontal, 12)
        .modifier(RowState(isNext: isNext, isCurrent: isCurrent))
        .accessibilityElement(children: .combine)
    }
}

/// Lit pane for the next prayer, quiet pane for the current one, plain otherwise.
private struct RowState: ViewModifier {
    let isNext: Bool
    let isCurrent: Bool
    func body(content: Content) -> some View {
        if isNext { content.glassLit() } else if isCurrent { content.glassPane() } else { content }
    }
}
