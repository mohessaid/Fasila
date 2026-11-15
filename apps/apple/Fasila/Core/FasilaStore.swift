import Foundation
import Observation
#if canImport(WidgetKit)
import WidgetKit
#endif

/// App-side notification preferences (not part of the shared config schema).
struct NotificationPrefs: Codable, Equatable {
    /// Adhan notification per prayer id; missing = on.
    var adhan: [String: Bool] = [:]
    /// Reminder this many minutes before each adhan (0 = off).
    var preReminderMinutes: Int = 0
    /// Notify for schedule items (alarms, tasks, events).
    var items: Bool = true

    func adhanEnabled(_ key: PrayerKey) -> Bool { adhan[key.rawValue] ?? true }

    static let storageKey = "fasila.notificationPrefs"

    static func load() -> NotificationPrefs {
        guard let data = SharedStorage.defaults.data(forKey: storageKey),
              let prefs = try? JSONDecoder().decode(NotificationPrefs.self, from: data) else { return NotificationPrefs() }
        return prefs
    }

    func save() {
        if let data = try? JSONEncoder().encode(self) { SharedStorage.defaults.set(data, forKey: Self.storageKey) }
    }
}

/// Single source of truth for the app. Owns the two JSON documents (config and
/// items), the Rust `FasilaEngine` built from the config, and derived data.
/// Every astronomical or calendrical answer comes from the engine.
@MainActor
@Observable
final class FasilaStore {
    private(set) var config: ConfigDocument?
    private(set) var items: [ScheduleItem] = []
    private(set) var engine: FasilaEngine?
    /// Bumped whenever the engine or items change so views can re-query.
    private(set) var revision = 0
    var errorMessage: String?

    var language: AppLanguage {
        didSet {
            SharedStorage.defaults.set(language.rawValue, forKey: AppLanguage.storageKey)
            didChange(reloadWidgets: true)
        }
    }

    var notificationPrefs: NotificationPrefs {
        didSet {
            notificationPrefs.save()
            scheduleNotifications()
        }
    }

    @ObservationIgnored private var dayCache: [CivilDate: DayInfo] = [:]
    let notifications = NotificationScheduler()
    @ObservationIgnored private var cachedMethods: [MethodInfo]?

    init() {
        language = AppLanguage.current
        notificationPrefs = NotificationPrefs.load()
        load()
    }

    // MARK: Derived values

    var hasLocation: Bool { engine != nil }
    var timeZone: TimeZone { config?.timeZone ?? .current }
    var lang: String { language.code }
    var formatter: FasilaFormatter { FasilaFormatter(locale: language.locale, timeZone: timeZone) }

    var methods: [MethodInfo] {
        if let m = cachedMethods { return m }
        let m = calculationMethods()
        cachedMethods = m
        return m
    }

    /// Today's civil date at the configured location.
    func today(_ now: Date = .now) -> CivilDate { CivilDate(now, in: timeZone) }

    func day(_ date: CivilDate) -> DayInfo? {
        if let cached = dayCache[date] { return cached }
        guard let engine, let info = try? engine.day(date) else { return nil }
        if dayCache.count > 120 { dayCache.removeAll(keepingCapacity: true) }
        dayCache[date] = info
        return info
    }

    func snapshot(at now: Date) -> PrayerSnapshot? {
        let t = today(now)
        let days = (-1...1).compactMap { day(t.adding(days: $0)) }
        guard days.count == 3 else { return nil }
        return PrayerSnapshot(now: now, days: days, todayIndex: 1, engine: engine)
    }

    /// Every day of a Gregorian month, in one engine call.
    func month(year: Int, month: Int) -> [DayInfo] {
        guard let engine else { return [] }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        let first = CivilDate(year: year, month: month, day: 1)
        let count = cal.range(of: .day, in: .month, for: first.noon(in: timeZone))?.count ?? 30
        return (try? engine.days(from: first, count: count)) ?? []
    }

    func occurrences(from start: CivilDate, days: Int, items: [ScheduleItem]? = nil) -> [OccurrenceInfo] {
        guard let engine else { return [] }
        let list = (items ?? self.items).filter(\.enabled)
        guard !list.isEmpty else { return [] }
        return (try? engine.occurrences(itemsJson: ScheduleItem.encodeList(list),
                                        year: Int32(start.year), month: UInt8(start.month), day: UInt8(start.day),
                                        days: UInt32(days))) ?? []
    }

    func freeSlots(on date: CivilDate, durationMinutes: Int) -> [SlotInfo] {
        guard let engine else { return [] }
        return (try? engine.freeSlots(year: Int32(date.year), month: UInt8(date.month), day: UInt8(date.day),
                                      durationMinutes: UInt32(max(1, durationMinutes)))) ?? []
    }

    var qiblaBearing: Double? { engine?.qibla() }
    var distanceToKaabaKm: Double? { engine?.distanceToKaabaKm() }

    // MARK: Loading & saving

    private func load() {
        if let json = SharedStorage.read(SharedStorage.configURL) {
            do {
                try apply(ConfigDocument(json: json), save: false)
            } catch {
                errorMessage = error.localizedDescription
            }
        }
        if let json = SharedStorage.read(SharedStorage.itemsURL) {
            items = ScheduleItem.decodeList(json)
        }
    }

    private func apply(_ doc: ConfigDocument, save: Bool) throws {
        let newEngine = try FasilaEngine(configJson: doc.json)
        engine = newEngine
        config = doc
        dayCache.removeAll()
        errorMessage = nil
        if save { try SharedStorage.write(doc.json, to: SharedStorage.configURL) }
        didChange(reloadWidgets: save)
    }

    private func saveItems() {
        do {
            try SharedStorage.write(ScheduleItem.encodeList(items), to: SharedStorage.itemsURL)
        } catch {
            errorMessage = error.localizedDescription
        }
        didChange(reloadWidgets: false)
    }

    private func didChange(reloadWidgets: Bool) {
        revision &+= 1
        scheduleNotifications()
        #if canImport(WidgetKit)
        if reloadWidgets { WidgetCenter.shared.reloadAllTimelines() }
        #endif
    }

    // MARK: Config mutations

    /// Set the place. First run takes the Rust defaults for the country
    /// (method, Hijri calendar); later moves keep the user's calculation choices.
    func setPlace(name: String, latitude: Double, longitude: Double, timezone: String, country: String) {
        do {
            let fresh = try ConfigDocument(json: defaultConfigJson(
                name: name, latitude: latitude, longitude: longitude, timezone: timezone, country: country))
            var doc = fresh
            if var existing = config {
                existing.replaceLocation(with: fresh)
                doc = existing
            }
            HijriAnnouncements.merge(into: &doc)
            try apply(doc, save: true)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func setPlace(_ city: City) {
        setPlace(name: city.displayName(lang: lang), latitude: city.latitude, longitude: city.longitude,
                 timezone: city.timezone, country: city.country)
    }

    func updateConfig(_ change: (inout ConfigDocument) -> Void) {
        guard var doc = config else { return }
        change(&doc)
        guard doc != config else { return }
        do {
            try apply(doc, save: true)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    /// Switch calculation method. The method's parameters come from the Rust core
    /// (`method_params_json`); the user's own choices are kept by `applyMethod`.
    func setMethod(_ methodID: String) {
        let template = (try? methodParamsJson(methodId: methodID))
            .flatMap { try? ConfigDocument(json: "{\"params\":\($0)}") }
        updateConfig { $0.applyMethod(methodID, template: template) }
    }

    /// The method the core recommends for the configured country.
    var suggestedMethod: String? {
        guard let config, !config.country.isEmpty else { return nil }
        return recommendedMethod(country: config.country)
    }

    // MARK: Item mutations

    func add(_ item: ScheduleItem) {
        var item = item
        if item.id.isEmpty { item.id = UUID().uuidString }
        items.append(item)
        saveItems()
    }

    func update(_ item: ScheduleItem) {
        guard let i = items.firstIndex(where: { $0.id == item.id }) else { return }
        items[i] = item
        saveItems()
    }

    func setEnabled(_ id: String, _ enabled: Bool) {
        guard let i = items.firstIndex(where: { $0.id == id }) else { return }
        items[i].enabled = enabled
        saveItems()
    }

    func delete(ids: Set<String>) {
        items.removeAll { ids.contains($0.id) }
        saveItems()
    }

    // MARK: Lifecycle hooks

    /// App came to the foreground, the day rolled over, or the clock/zone changed.
    func refresh() {
        dayCache.removeAll()
        revision &+= 1
        scheduleNotifications()
    }

    func scheduleNotifications() {
        guard engine != nil else { return }
        notifications.reschedule(notificationPlan(now: .now))
    }

    /// The next week of adhan + item notifications, earliest first; the
    /// scheduler trims it to stay under the system's pending-request limit.
    func notificationPlan(now: Date, days: Int = 7) -> [PlannedNotification] {
        let start = today(now)
        let place = config?.locationName ?? ""
        let fmt = formatter
        var plan: [PlannedNotification] = []

        for offset in 0..<days {
            guard let d = day(start.adding(days: offset)) else { continue }
            for key in PrayerKey.prayers where notificationPrefs.adhanEnabled(key) {
                guard let t = d.time(key) else { continue }
                let name = language.tr(key.localizationKey)
                if t > now {
                    plan.append(PlannedNotification(
                        id: "fasila.adhan.\(d.date).\(key.rawValue)", date: t,
                        title: language.tr("notification.adhan.title %@", name),
                        body: place.isEmpty ? fmt.time(t) : "\(place) · \(fmt.time(t))",
                        kind: .adhan))
                }
                let pre = notificationPrefs.preReminderMinutes
                if pre > 0 {
                    let pt = t.addingTimeInterval(-Double(pre * 60))
                    if pt > now {
                        plan.append(PlannedNotification(
                            id: "fasila.pre.\(d.date).\(key.rawValue)", date: pt,
                            title: language.tr("notification.pre.title %@ %@", name, fmt.minutes(pre)),
                            body: fmt.time(t), kind: .reminder))
                    }
                }
            }
        }

        if notificationPrefs.items {
            let byID = Dictionary(items.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
            for occ in occurrences(from: start, days: days) {
                let t = Date(timeIntervalSince1970: TimeInterval(occ.start))
                let item = byID[occ.itemId]
                let body = item.map { $0.start.describe(lang: lang) } ?? fmt.time(t)
                if t > now {
                    plan.append(PlannedNotification(
                        id: "fasila.item.\(occ.itemId).\(occ.date)", date: t, title: occ.title, body: body,
                        kind: occ.kind == "alarm" ? .alarm : .reminder))
                }
                if let before = item?.remindBeforeMinutes, before > 0 {
                    let rt = t.addingTimeInterval(-Double(before * 60))
                    if rt > now {
                        plan.append(PlannedNotification(
                            id: "fasila.remind.\(occ.itemId).\(occ.date)", date: rt,
                            title: occ.title, body: language.tr("notification.item.soon %@", fmt.minutes(before)),
                            kind: .reminder))
                    }
                }
            }
        }
        return plan.sorted { $0.date < $1.date }
    }
}
