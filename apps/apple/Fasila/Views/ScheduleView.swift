import SwiftUI

struct ScheduleView: View {
    @Environment(FasilaStore.self) private var store

    var body: some View {
        LivingSky {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("tab.schedule")
                        .font(.fasila(.largeTitle, .light))
                        .onGlassPrimary()

                    ComposerView()
                        .padding(16)
                        .glass()

                    if store.items.isEmpty {
                        Text("schedule.empty")
                            .font(.fasila(.subheadline))
                            .onGlassSecondary()
                            .padding(16)
                            .frame(maxWidth: .infinity)
                            .glass(radius: 16)
                    } else {
                        let today = store.today()
                        let todays = Dictionary(store.occurrences(from: today, days: 1).map { ($0.itemId, $0) },
                                                uniquingKeysWith: { a, _ in a })
                        VStack(spacing: 4) {
                            ForEach(store.items) { item in
                                ItemRow(item: item, today: todays[item.id])
                            }
                        }
                        .padding(.horizontal, 12)
                        .glass()
                    }
                }
                .padding(16)
                .frame(maxWidth: 640)
                .frame(maxWidth: .infinity)
            }
        }
    }
}

struct ItemRow: View {
    let item: ScheduleItem
    let today: OccurrenceInfo?
    @Environment(FasilaStore.self) private var store

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: item.kind.symbol)
                .frame(width: 24)
                .padding(.top, 2)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 3) {
                Text(verbatim: item.title).font(.fasila(.body, .medium))
                Text(verbatim: summary).font(.fasila(.subheadline)).onGlassSecondary()
                if let today {
                    HStack(spacing: 6) {
                        Text(verbatim: store.formatter.time(unix: today.start))
                            .monospacedDigit()
                        if !today.conflicts.isEmpty {
                            Label("schedule.conflictBadge", systemImage: "exclamationmark.triangle.fill")
                                .foregroundStyle(fasilaWarn)
                        }
                    }
                    .font(.fasila(.caption))
                }
            }
            Spacer(minLength: 8)
            Toggle("schedule.enabled", isOn: Binding(
                get: { item.enabled },
                set: { store.setEnabled(item.id, $0) }))
                .labelsHidden()
            Menu {
                Button("schedule.delete", systemImage: "trash", role: .destructive) {
                    store.delete(ids: [item.id])
                }
            } label: {
                Image(systemName: "ellipsis.circle")
            }
            .menuIndicator(.hidden)
            .buttonStyle(.borderless)
            .fixedSize()
        }
        .onGlassPrimary()
        .padding(.vertical, 12)
        .opacity(item.enabled ? 1 : 0.6)
        .contextMenu {
            Button("schedule.delete", systemImage: "trash", role: .destructive) { store.delete(ids: [item.id]) }
        }
    }

    private var summary: String {
        var parts = [item.start.describe(lang: store.lang)]
        switch item.end {
        case .duration(let m): parts.append(store.formatter.minutes(m))
        case .until(let a): parts.append(store.language.tr("composer.until %@", a.describe(lang: store.lang)))
        case .instant: break
        }
        parts.append(RecurrenceText.describe(item.recurrence, store: store))
        return parts.joined(separator: " · ")
    }
}

@MainActor
enum RecurrenceText {
    static func describe(_ r: Recurrence, store: FasilaStore) -> String {
        let L = store.language, fmt = store.formatter, lang = store.lang
        switch r {
        case .daily:
            return L.tr("recurrence.daily")
        case .once(let d):
            return fmt.shortDate(d)
        case .weekly(let mask):
            let symbols = fmt.shortWeekdaySymbols()
            return (0..<7).filter { mask & (1 << $0) != 0 }.map { symbols[$0] }.joined(separator: ", ")
        case .hijriMonthly(let days):
            return L.tr("recurrence.hijriMonthly %@", days.map(fmt.number).joined(separator: ", "))
        case .hijriYearly(let m, let d):
            return "\(fmt.number(d)) \(hijriMonthName(month: UInt8(clamping: m), lang: lang))"
        case .hijriMonth(let m):
            return L.tr("recurrence.hijriMonth %@", hijriMonthName(month: UInt8(clamping: m), lang: lang))
        }
    }
}
