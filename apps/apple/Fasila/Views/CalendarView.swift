import SwiftUI

/// Month grid with Gregorian and Hijri dates and occasions. All Hijri data
/// comes from the engine (`days`), which applies the user's Hijri profile.
struct CalendarView: View {
    @Environment(FasilaStore.self) private var store
    @State private var shown: CivilDate?
    @State private var selected: DayInfo?

    private var month: CivilDate {
        shown ?? { let t = store.today(); return CivilDate(year: t.year, month: t.month, day: 1) }()
    }

    var body: some View {
        let fmt = store.formatter
        let days = store.month(year: month.year, month: month.month)
        LivingSky {
            ScrollView {
                VStack(spacing: 16) {
                    header(days: days, fmt: fmt)
                    grid(days: days, fmt: fmt)
                        .padding(12)
                        .glass()
                    occasionsList(days: days, fmt: fmt)
                }
                .padding(16)
                .frame(maxWidth: 640)
                .frame(maxWidth: .infinity)
            }
        }
        .sheet(item: Binding(get: { selected.map(IdentifiedDay.init) }, set: { selected = $0?.day })) { wrapped in
            DayDetailView(day: wrapped.day)
                .environment(store)
                .environment(\.locale, store.language.locale)
                .environment(\.layoutDirection, store.language.layoutDirection)
        }
    }

    private func header(days: [DayInfo], fmt: FasilaFormatter) -> some View {
        HStack {
            Button { move(-1) } label: { Image(systemName: "chevron.backward") }
                .accessibilityLabel(Text("calendar.previous"))
            Spacer()
            VStack(spacing: 2) {
                Text(verbatim: fmt.monthYear(year: month.year, month: month.month))
                    .font(.fasila(.title2, .light))
                Text(verbatim: hijriRange(days, fmt: fmt))
                    .font(.fasila(.subheadline))
                    .onGlassSecondary()
            }
            Spacer()
            Button { move(1) } label: { Image(systemName: "chevron.forward") }
                .accessibilityLabel(Text("calendar.next"))
        }
        .buttonStyle(.plain)
        .font(.fasila(.title3))
        .onGlassPrimary()
    }

    private func hijriRange(_ days: [DayInfo], fmt: FasilaFormatter) -> String {
        guard let first = days.first, let last = days.last else { return "" }
        let name = { (d: DayInfo) in hijriMonthName(month: d.hijriMonth, lang: store.lang) }
        if first.hijriMonth == last.hijriMonth {
            return "\(name(first)) \(fmt.number(Int(first.hijriYear)))"
        }
        let firstPart = first.hijriYear == last.hijriYear ? name(first) : "\(name(first)) \(fmt.number(Int(first.hijriYear)))"
        return store.language.tr("range %@ %@", firstPart, "\(name(last)) \(fmt.number(Int(last.hijriYear)))")
    }

    private func grid(days: [DayInfo], fmt: FasilaFormatter) -> some View {
        let first = fmt.firstWeekday - 1 // 0 = Sunday
        let symbols = fmt.veryShortWeekdaySymbols()
        let ordered = (0..<7).map { symbols[($0 + first) % 7] }
        let leading = days.first.map { (Int($0.weekday) - first + 7) % 7 } ?? 0
        let today = store.today().iso
        let columns = Array(repeating: GridItem(.flexible(), spacing: 4), count: 7)

        return LazyVGrid(columns: columns, spacing: 6) {
            ForEach(0..<7, id: \.self) { i in
                Text(verbatim: ordered[i]).font(.fasila(.caption, .semibold)).onGlassSecondary()
            }
            ForEach(0..<leading, id: \.self) { i in
                Color.clear.frame(height: 48).id("blank-\(i)")
            }
            ForEach(days, id: \.date) { d in
                DayCell(day: d, isToday: d.date == today, fmt: fmt, lang: store.lang)
                    .onTapGesture { selected = d }
                    .accessibilityAddTraits(.isButton)
            }
        }
    }

    @ViewBuilder
    private func occasionsList(days: [DayInfo], fmt: FasilaFormatter) -> some View {
        let marked = days.filter { !$0.occasions.isEmpty }
        if !marked.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                ForEach(marked, id: \.date) { d in
                    HStack(alignment: .firstTextBaseline) {
                        Text(verbatim: d.civil.map(fmt.shortDate) ?? d.date)
                            .font(.fasila(.subheadline, .semibold))
                            .frame(minWidth: 90, alignment: .leading)
                        VStack(alignment: .leading) {
                            ForEach(d.occasions, id: \.self) { o in Text(LocalizedStringKey.key("occasion.\(o)")) }
                        }
                        .font(.fasila(.subheadline))
                    }
                    .onGlassPrimary()
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .glass()
        }
    }

    private func move(_ delta: Int) {
        var m = month.month + delta, y = month.year
        if m < 1 { m = 12; y -= 1 }
        if m > 12 { m = 1; y += 1 }
        shown = CivilDate(year: y, month: m, day: 1)
    }
}

private struct IdentifiedDay: Identifiable {
    let day: DayInfo
    var id: String { day.date }
}

private struct DayCell: View {
    let day: DayInfo
    let isToday: Bool
    let fmt: FasilaFormatter
    let lang: String
    @Environment(\.skyLook) private var sky

    var body: some View {
        VStack(spacing: 2) {
            Text(verbatim: fmt.number(day.civil?.day ?? 0))
                .font(.fasila(.body, isToday ? .bold : .regular))
            Text(verbatim: day.hijriDay == 1
                 ? hijriMonthName(month: day.hijriMonth, lang: lang)
                 : fmt.number(Int(day.hijriDay)))
                .font(.fasila(.caption2))
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .onGlassSecondary()
            Circle()
                .fill(day.occasions.isEmpty ? Color.clear : sky.accent.color)
                .frame(width: 5, height: 5)
        }
        .onGlassPrimary()
        .frame(maxWidth: .infinity, minHeight: 48)
        .modifier(TodayCell(isToday: isToday))
        .contentShape(Rectangle())
    }
}

struct DayDetailView: View {
    let day: DayInfo
    @Environment(FasilaStore.self) private var store
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let fmt = store.formatter
        NavigationStack {
            List {
                Section {
                    Text(verbatim: fmt.hijri(day, lang: store.lang))
                    ForEach(day.occasions, id: \.self) { o in Text(LocalizedStringKey.key("occasion.\(o)")) }
                }
                Section {
                    ForEach(PrayerKey.listed + PrayerKey.extra) { key in
                        HStack {
                            Text(LocalizedStringKey(key.localizationKey))
                            if day.isEstimated(key) {
                                Text("badge.estimated").font(.fasila(.caption2)).foregroundStyle(fasilaWarn)
                            }
                            Spacer()
                            Text(verbatim: fmt.time(unix: day.unix(key))).monospacedDigit()
                        }
                    }
                }
            }
            .navigationTitle(Text(verbatim: day.civil.map(fmt.longDate) ?? day.date))
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("common.done") { dismiss() }
                }
            }
        }
        #if os(macOS)
        .frame(minWidth: 380, minHeight: 480)
        #endif
    }
}

/// Today is a lit pane; other days are plain.
private struct TodayCell: ViewModifier {
    let isToday: Bool
    func body(content: Content) -> some View {
        if isToday { content.glassLit(radius: 12) } else { content }
    }
}
