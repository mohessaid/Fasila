import SwiftUI

struct TodayView: View {
    @Environment(FasilaStore.self) private var store
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        LivingSky {
            TimelineView(.periodic(from: .now, by: 1)) { ctx in
                if let snap = store.snapshot(at: ctx.date, crossFade: !reduceMotion) {
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

                DayDial(day: day, snapshot: snap, formatter: fmt, lang: store.language)
                    .frame(maxWidth: 380)
                    .padding(8)
                    .frame(maxWidth: .infinity)
                    .glass()
                    .accessibilityLabel(dialLabel(snap, fmt: fmt))

                if !day.occasions.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            ForEach(day.occasions, id: \.self) { id in
                                Chip(highlighted: true) { Text(LocalizedStringKey.key("occasion.\(id)")) }
                            }
                        }
                    }
                }

                VStack(spacing: 0) {
                    ForEach(PrayerKey.listed) { key in
                        PrayerRow(key: key, day: day, snapshot: snap, formatter: fmt)
                        if key != PrayerKey.listed.last { Divider().overlay(Color.white.opacity(0.15)) }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 4)
                .glass()

                VStack(spacing: 0) {
                    ForEach(PrayerKey.extra) { key in
                        PrayerRow(key: key, day: day, snapshot: snap, formatter: fmt, secondary: true)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 4)
                .glass()

                if day.polarFallback || day.fajrEstimated || day.ishaEstimated {
                    Label(LocalizedStringKey(day.polarFallback ? "estimated.polar" : "estimated.highLatitude"), systemImage: "info.circle")
                        .font(.footnote)
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
                .font(.headline)
                .onGlassPrimary()
            if let civil = day.civil {
                Text(fmt.longDate(civil))
                    .font(.subheadline)
                    .onGlassSecondary()
            }
            Text(fmt.hijri(day, lang: store.lang))
                .font(.subheadline)
                .onGlassSecondary()
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

    @Environment(\.skyPalette) private var palette
    @State private var showInfo = false

    private var time: Date? { day.time(key) }
    private var isNext: Bool { snapshot.next?.key == key && snapshot.next?.date == time }
    private var isCurrent: Bool { snapshot.previous?.key == key && snapshot.previous?.date == time }

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: key.symbol)
                .frame(width: 24)
                .foregroundStyle(isNext ? palette.accent.color : .white.opacity(0.8))
                .accessibilityHidden(true)
            Text(LocalizedStringKey(key.localizationKey))
                .font(secondary ? .subheadline : .body.weight(isCurrent || isNext ? .semibold : .regular))
            if day.isEstimated(key) {
                Button { showInfo = true } label: {
                    Text("badge.estimated")
                        .font(.caption2.weight(.semibold))
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(Capsule().fill(Color.orange.opacity(0.35)))
                }
                .buttonStyle(.plain)
                .popover(isPresented: $showInfo) {
                    Text(LocalizedStringKey(day.polarFallback ? "estimated.polar" : "estimated.highLatitude"))
                        .font(.footnote)
                        .padding()
                        .frame(maxWidth: 300)
                        .presentationCompactAdaptation(.popover)
                }
            }
            Spacer(minLength: 8)
            Text(formatter.time(unix: day.unix(key)))
                .monospacedDigit()
                .font(secondary ? .subheadline : .body)
        }
        .onGlassPrimary()
        .opacity(secondary ? 0.85 : 1)
        .padding(.vertical, secondary ? 8 : 12)
        .padding(.horizontal, 8)
        .background {
            if isCurrent {
                RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color.white.opacity(0.12))
            }
        }
        .accessibilityElement(children: .combine)
    }
}
