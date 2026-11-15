import SwiftUI
import WidgetKit

/// "Next prayer" widget. Reads the app's config from the App Group container
/// and asks the same Rust engine for times; one timeline entry per prayer /
/// sky-period boundary so the name, sky and countdown target stay correct.
@main
struct FasilaWidgetBundle: WidgetBundle {
    var body: some Widget {
        NextPrayerWidget()
    }
}

struct NextPrayerWidget: Widget {
    let kind = "app.fasila.next-prayer"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: NextPrayerProvider()) { entry in
            NextPrayerWidgetView(entry: entry)
        }
        .configurationDisplayName("widget.name")
        .description("widget.description")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct NextPrayerEntry: TimelineEntry {
    let date: Date
    let place: String
    let timeZone: TimeZone
    let snapshot: PrayerSnapshot?
    var country: String = ""
}

struct NextPrayerProvider: TimelineProvider {
    func placeholder(in context: Context) -> NextPrayerEntry {
        NextPrayerEntry(date: .now, place: "Makkah", timeZone: .current, snapshot: nil)
    }

    func getSnapshot(in context: Context, completion: @escaping (NextPrayerEntry) -> Void) {
        completion(entries(from: .now, limit: 1).first ?? placeholder(in: context))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<NextPrayerEntry>) -> Void) {
        let list = entries(from: .now, limit: 16)
        let refresh = list.last.map { $0.date.addingTimeInterval(60) } ?? Date().addingTimeInterval(3600)
        completion(Timeline(entries: list.isEmpty ? [placeholder(in: context)] : list, policy: .after(refresh)))
    }

    private func entries(from now: Date, limit: Int) -> [NextPrayerEntry] {
        guard let json = SharedStorage.read(SharedStorage.configURL),
              let engine = try? FasilaEngine(configJson: json) else { return [] }
        let location = Self.location(json)
        let tz = TimeZone(identifier: location.timezone) ?? .current
        let start = CivilDate(now, in: tz).adding(days: -1)
        guard let days = try? engine.days(from: start, count: 4) else { return [] }

        let boundaries = days
            .flatMap { d in [PrayerKey.fajr, .sunrise, .dhuhr, .asr, .maghrib, .isha].compactMap { d.time($0) } }
            .filter { $0 > now && $0 < now.addingTimeInterval(36 * 3600) }
            .sorted()
        return ([now] + boundaries).prefix(limit).compactMap { date in
            let iso = CivilDate(date, in: tz).iso
            guard let i = days.firstIndex(where: { $0.date == iso }), i > 0, i + 1 < days.count else { return nil }
            let snap = PrayerSnapshot(now: date, days: Array(days[(i - 1)...(i + 1)]), todayIndex: 1, engine: engine)
            return NextPrayerEntry(date: date, place: location.name, timeZone: tz, snapshot: snap, country: location.country)
        }
    }

    private static func location(_ json: String) -> (name: String, timezone: String, country: String) {
        let root = (try? JSONSerialization.jsonObject(with: Data(json.utf8))) as? [String: Any]
        let loc = root?["location"] as? [String: Any]
        return (loc?["name"] as? String ?? "", loc?["timezone"] as? String ?? TimeZone.current.identifier,
                loc?["country"] as? String ?? "")
    }
}

struct NextPrayerWidgetView: View {
    let entry: NextPrayerEntry
    @Environment(\.widgetFamily) private var family

    private var language: AppLanguage { .current }
    private var formatter: FasilaFormatter { FasilaFormatter(locale: formattingLocale(language.locale, country: entry.country), timeZone: entry.timeZone) }
    private var sky: SkyLook { entry.snapshot?.sky ?? .night }

    var body: some View {
        Group {
            if let snap = entry.snapshot, let next = snap.next {
                if family == .systemMedium {
                    HStack(spacing: 16) {
                        nextBlock(snap: snap, next: next)
                        Divider().overlay(Color.white.opacity(0.3))
                        list(snap: snap)
                    }
                } else {
                    nextBlock(snap: snap, next: next)
                }
            } else {
                Text("widget.openApp")
                    .font(.fasila(.footnote))
                    .multilineTextAlignment(.center)
            }
        }
        .foregroundStyle(Color.white.opacity(0.95))
        .environment(\.locale, language.locale)
        .environment(\.layoutDirection, language.layoutDirection)
        // The live sky, darkened by the adaptive glass tint so white text stays legible.
        .containerBackground(for: .widget) {
            sky.gradient.overlay(fasilaNight.opacity(sky.glassTint * 0.7))
        }
    }

    private func nextBlock(snap: PrayerSnapshot, next: PrayerMoment) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                Image(systemName: next.key.symbol)
                Text(LocalizedStringKey(next.key.localizationKey)).font(.fasila(.headline, .semibold))
            }
            Text(timerInterval: snap.now...next.date, countsDown: true)
                .font(.fasila(size: 30, .light))
                .monospacedDigit()
                .minimumScaleFactor(0.6)
                .lineLimit(1)
            Text(verbatim: formatter.time(next.date))
                .font(.fasila(.subheadline))
                .opacity(0.75)
            Spacer(minLength: 0)
            Text(verbatim: entry.place)
                .font(.fasila(.caption2))
                .opacity(0.7)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func list(snap: PrayerSnapshot) -> some View {
        VStack(spacing: 3) {
            ForEach(PrayerKey.prayers) { key in
                HStack {
                    Text(LocalizedStringKey(key.localizationKey))
                    Spacer()
                    Text(verbatim: formatter.time(unix: snap.today.unix(key))).monospacedDigit()
                }
                .font(.fasila(.caption, snap.next?.key == key ? .bold : .regular))
                .opacity(snap.next?.key == key ? 1 : 0.8)
            }
        }
        .frame(maxWidth: .infinity)
    }
}
