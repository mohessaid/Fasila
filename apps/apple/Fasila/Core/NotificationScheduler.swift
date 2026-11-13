import Foundation
import UserNotifications

struct PlannedNotification: Sendable, Hashable {
    enum Kind: Sendable { case adhan, alarm, reminder }
    let id: String
    let date: Date
    let title: String
    let body: String
    let kind: Kind
}

/// Keeps a rolling window of local notifications (adhan + items).
///
/// iOS keeps at most 64 pending requests per app, so only the earliest
/// `maxPending` planned notifications are registered; the window rolls forward
/// whenever the app is opened, settings change, or the date/time/zone changes.
@MainActor
final class NotificationScheduler {
    static let maxPending = 60
    static let prefix = "fasila."

    private let center = UNUserNotificationCenter.current()
    private var pending: Task<Void, Never>?
    private var lastApplied: [PlannedNotification] = []

    @discardableResult
    func requestAuthorization() async -> Bool {
        (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    /// Debounced: rapid settings changes collapse into one reschedule.
    func reschedule(_ plan: [PlannedNotification]) {
        let window = Array(plan.prefix(Self.maxPending))
        pending?.cancel()
        pending = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard !Task.isCancelled, let self else { return }
            await self.apply(window)
        }
    }

    private func apply(_ window: [PlannedNotification]) async {
        let settings = await center.notificationSettings()
        switch settings.authorizationStatus {
        case .notDetermined:
            guard await requestAuthorization() else { return }
        case .denied:
            return
        default:
            break
        }
        guard window != lastApplied else { return }

        let existing = await center.pendingNotificationRequests()
            .map(\.identifier)
            .filter { $0.hasPrefix(Self.prefix) }
        center.removePendingNotificationRequests(withIdentifiers: existing)

        let calendar = Calendar.current
        for n in window {
            if Task.isCancelled { return }
            let content = UNMutableNotificationContent()
            content.title = n.title
            content.body = n.body
            content.sound = sound(for: n.kind)
            content.threadIdentifier = n.kind == .adhan ? "adhan" : "items"
            if n.kind != .reminder { content.interruptionLevel = .timeSensitive }
            let comps = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: n.date)
            let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
            let request = UNNotificationRequest(identifier: n.id, content: content, trigger: trigger)
            try? await center.add(request)
        }
        lastApplied = window
    }

    private func sound(for kind: PlannedNotification.Kind) -> UNNotificationSound {
        // Ship an `adhan.caf` (< 30 s) in the bundle to get an adhan sound.
        if kind == .adhan, Bundle.main.url(forResource: "adhan", withExtension: "caf") != nil {
            return UNNotificationSound(named: UNNotificationSoundName("adhan.caf"))
        }
        return .default
    }
}
