import SwiftUI
#if os(iOS)
import UIKit
#endif

@main
struct FasilaApp: App {
    @State private var store = FasilaStore()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(store)
                // In-app language override: string lookup, number/date formatting
                // and layout direction all follow it (Arabic → RTL).
                .environment(\.locale, store.language.locale)
                .environment(\.layoutDirection, store.language.layoutDirection)
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active { store.refresh() }
                }
                .onReceive(NotificationCenter.default.publisher(for: .NSSystemTimeZoneDidChange)) { _ in store.refresh() }
                .onReceive(NotificationCenter.default.publisher(for: .NSCalendarDayChanged)) { _ in store.refresh() }
                #if os(iOS)
                .onReceive(NotificationCenter.default.publisher(for: UIApplication.significantTimeChangeNotification)) { _ in
                    store.refresh()
                }
                #else
                .onReceive(NotificationCenter.default.publisher(for: .NSSystemClockDidChange)) { _ in store.refresh() }
                #endif
        }
        #if os(macOS)
        .defaultSize(width: 980, height: 760)
        #endif
    }
}

struct RootView: View {
    @Environment(FasilaStore.self) private var store
    @SceneStorage("fasila.tab") private var tab: AppTab = .today

    enum AppTab: String { case today, schedule, calendar, qibla, settings }

    var body: some View {
        if store.hasLocation {
            TabView(selection: $tab) {
                TodayView()
                    .tabItem { Label("tab.today", systemImage: "sun.max") }
                    .tag(AppTab.today)
                ScheduleView()
                    .tabItem { Label("tab.schedule", systemImage: "list.bullet.clipboard") }
                    .tag(AppTab.schedule)
                CalendarView()
                    .tabItem { Label("tab.calendar", systemImage: "calendar") }
                    .tag(AppTab.calendar)
                QiblaView()
                    .tabItem { Label("tab.qibla", systemImage: "location.north.line") }
                    .tag(AppTab.qibla)
                SettingsView()
                    .tabItem { Label("tab.settings", systemImage: "gearshape") }
                    .tag(AppTab.settings)
            }
            .tint(.white)
        } else {
            OnboardingView()
        }
    }
}

/// First run: pick a place (current location or the shared city list).
struct OnboardingView: View {
    var body: some View {
        LivingSky {
            VStack(spacing: 20) {
                VStack(spacing: 8) {
                    Text("app.name")
                        .font(.system(size: 44, weight: .light, design: .rounded))
                        .onGlassPrimary()
                    Text("onboarding.tagline")
                        .multilineTextAlignment(.center)
                        .onGlassSecondary()
                }
                .padding(.top, 40)
                PlacePicker()
                    .padding(16)
                    .glass()
                    .frame(maxWidth: 520)
            }
            .padding(16)
        }
    }
}
