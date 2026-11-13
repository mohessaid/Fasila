import SwiftUI

struct SettingsView: View {
    @Environment(FasilaStore.self) private var store
    @State private var choosingPlace = false

    var body: some View {
        LivingSky {
            if let config = store.config {
                form(config)
            }
        }
        .sheet(isPresented: $choosingPlace) {
            NavigationStack {
                LivingSky {
                    PlacePicker { choosingPlace = false }
                        .padding(16)
                }
                .navigationTitle(Text("settings.location"))
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("common.cancel") { choosingPlace = false }
                    }
                }
            }
            .environment(store)
            .environment(\.locale, store.language.locale)
            .environment(\.layoutDirection, store.language.layoutDirection)
            #if os(macOS)
            .frame(minWidth: 460, minHeight: 560)
            #endif
        }
    }

    private func form(_ config: ConfigDocument) -> some View {
        let fmt = store.formatter
        return Form {
            // MARK: Location
            Section("settings.location") {
                LabeledContent("settings.place") {
                    Text(verbatim: config.locationName)
                }
                LabeledContent("settings.coordinates") {
                    Text(verbatim: String(format: "%.4f, %.4f", config.latitude, config.longitude)).monospacedDigit()
                }
                LabeledContent("settings.timezone") { Text(verbatim: config.timezoneID) }
                Button("settings.changeLocation") { choosingPlace = true }
            }

            // MARK: Calculation
            Section {
                Picker("settings.method", selection: Binding(get: { config.method }, set: { store.setMethod($0) })) {
                    ForEach(store.methods, id: \.id) { m in Text(verbatim: m.name).tag(m.id) }
                }
                if let recommended = store.suggestedMethod, recommended != config.method {
                    Button("settings.useRecommended") { store.setMethod(recommended) }
                }
                Picker("settings.madhab", selection: configBinding(\.madhab)) {
                    ForEach(ConfigOptions.madhabs, id: \.self) { Text(LocalizedStringKey.key("madhab.\($0)")).tag($0) }
                }
                Picker("settings.highLatitude", selection: configBinding(\.highLatitudeRule)) {
                    ForEach(ConfigOptions.highLatitudeRules, id: \.self) { Text(LocalizedStringKey.key("highLat.\($0)")).tag($0) }
                }
                Picker("settings.polar", selection: configBinding(\.polarRule)) {
                    ForEach(ConfigOptions.polarRules, id: \.self) { Text(LocalizedStringKey.key("polar.\($0)")).tag($0) }
                }
                Picker("settings.rounding", selection: configBinding(\.rounding)) {
                    ForEach(ConfigOptions.roundings, id: \.self) { Text(LocalizedStringKey.key("rounding.\($0)")).tag($0) }
                }
            } header: {
                Text("settings.calculation")
            }

            // MARK: Adjustments
            Section {
                ForEach(PrayerKey.listed) { key in
                    Stepper(value: Binding(get: { config.adjustment(key) },
                                           set: { v in store.updateConfig { $0.setAdjustment(key, v) } }),
                            in: -30...30) {
                        LabeledContent {
                            Text(verbatim: signed(config.adjustment(key), fmt: fmt)).monospacedDigit()
                        } label: {
                            Text(LocalizedStringKey(key.localizationKey))
                        }
                    }
                }
            } header: {
                Text("settings.adjustments")
            } footer: {
                Text("settings.adjustments.footer")
            }

            // MARK: Hijri
            Section("settings.hijri") {
                Picker("settings.hijriCalendar", selection: configBinding(\.hijriCalendar)) {
                    ForEach(ConfigOptions.hijriCalendars, id: \.self) { Text(LocalizedStringKey.key("hijriCalendar.\($0)")).tag($0) }
                }
                Stepper(value: configBinding(\.hijriDayAdjustment), in: -2...2) {
                    LabeledContent("settings.hijriAdjustment") {
                        Text(verbatim: signed(config.hijriDayAdjustment, fmt: fmt)).monospacedDigit()
                    }
                }
                if let today = store.day(store.today()) {
                    LabeledContent("settings.hijriToday") { Text(verbatim: fmt.hijri(today, lang: store.lang)) }
                }
            }

            // MARK: Protected time around prayers
            Section {
                Stepper(value: configBinding(\.guardBefore), in: 0...60, step: 5) {
                    LabeledContent("settings.guardBefore") { Text(verbatim: fmt.minutes(config.guardBefore)) }
                }
                Stepper(value: configBinding(\.guardAfter), in: 0...90, step: 5) {
                    LabeledContent("settings.guardAfter") { Text(verbatim: fmt.minutes(config.guardAfter)) }
                }
                Stepper(value: configBinding(\.guardJumuah), in: 0...120, step: 5) {
                    LabeledContent("settings.guardJumuah") { Text(verbatim: fmt.minutes(config.guardJumuah)) }
                }
            } header: {
                Text("settings.guard")
            } footer: {
                Text("settings.guard.footer")
            }

            // MARK: Notifications
            Section("settings.notifications") {
                ForEach(PrayerKey.prayers) { key in
                    Toggle(isOn: Binding(
                        get: { store.notificationPrefs.adhanEnabled(key) },
                        set: { store.notificationPrefs.adhan[key.rawValue] = $0 })) {
                        Text(LocalizedStringKey(key.localizationKey))
                    }
                }
                Picker("settings.preReminder", selection: Binding(
                    get: { store.notificationPrefs.preReminderMinutes },
                    set: { store.notificationPrefs.preReminderMinutes = $0 })) {
                    Text("settings.off").tag(0)
                    ForEach([5, 10, 15, 20, 30], id: \.self) { Text(verbatim: fmt.minutes($0)).tag($0) }
                }
                Toggle("settings.itemNotifications", isOn: Binding(
                    get: { store.notificationPrefs.items },
                    set: { store.notificationPrefs.items = $0 }))
            }

            // MARK: Language
            Section("settings.language") {
                Picker("settings.language", selection: Binding(get: { store.language }, set: { store.language = $0 })) {
                    ForEach(AppLanguage.allCases) { Text(verbatim: $0.nativeName).tag($0) }
                }
            }

            if let error = store.errorMessage {
                Section { Text(verbatim: error).foregroundStyle(.orange).font(.footnote) }
            }
        }
        .formStyle(.grouped)
        .scrollContentBackground(.hidden)
        .frame(maxWidth: 700)
        .frame(maxWidth: .infinity)
    }

    private func configBinding<T: Equatable>(_ keyPath: WritableKeyPath<ConfigDocument, T>) -> Binding<T> {
        Binding(
            get: { store.config?[keyPath: keyPath] ?? ConfigDocumentDefaults.value(keyPath) },
            set: { v in store.updateConfig { $0[keyPath: keyPath] = v } })
    }

    private func signed(_ v: Int, fmt: FasilaFormatter) -> String {
        v > 0 ? "+\(fmt.number(v))" : fmt.number(v)
    }
}

/// Fallback for bindings when no config is loaded (never shown: the form
/// only renders with a config).
private enum ConfigDocumentDefaults {
    static func value<T>(_ keyPath: WritableKeyPath<ConfigDocument, T>) -> T {
        // An empty document yields every accessor's default.
        let empty = (try? ConfigDocument(json: "{}"))!
        return empty[keyPath: keyPath]
    }
}
