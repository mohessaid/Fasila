import SwiftUI

/// The chip state of the composer. Text → chips goes through the shared Rust
/// parser (`parse_sentence`); chips → text uses the core's `describe_anchor`.
struct ComposerDraft: Equatable {
    enum Direction: String, CaseIterable, Identifiable {
        case before, at, after
        var id: String { rawValue }
    }

    var title = ""
    var kind: ItemKind = .alarm
    var key: PrayerKey = .fajr
    var direction: Direction = .before
    var minutes = 15
    var end: ItemEnd = .instant
    var recurrence: Recurrence = .daily

    init() {}

    init(item: ScheduleItem) {
        title = item.title
        kind = item.kind
        end = item.end
        recurrence = item.recurrence
        setAnchor(key: item.start.key, offset: item.start.offsetMinutes)
    }

    mutating func setAnchor(key: String, offset: Int) {
        self.key = PrayerKey(rawValue: key) ?? .fajr
        direction = offset == 0 ? .at : (offset < 0 ? .before : .after)
        if offset != 0 { minutes = abs(offset) }
    }

    var anchor: Anchor {
        let offset: Int
        switch direction {
        case .before: offset = -minutes
        case .at: offset = 0
        case .after: offset = minutes
        }
        return Anchor(key: key.rawValue, offsetMinutes: offset)
    }

    func item(id: String, fallbackTitle: String) -> ScheduleItem {
        let t = title.trimmingCharacters(in: .whitespaces)
        return ScheduleItem(id: id, title: t.isEmpty ? fallbackTitle : t, kind: kind, start: anchor, end: end,
                            recurrence: recurrence)
    }
}

struct ComposerView: View {
    @Environment(FasilaStore.self) private var store
    @State private var text = ""
    @State private var draft = ComposerDraft()
    @State private var parseFailed = false
    /// Set when the text is being rewritten from the chips, so it is not re-parsed.
    @State private var rewriting = false

    private static let offsetChoices = [5, 10, 15, 20, 30, 45, 60, 90, 120]
    private static let durationChoices = [15, 30, 45, 60, 90, 120, 180]

    var body: some View {
        let fmt = store.formatter
        VStack(alignment: .leading, spacing: 12) {
            TextField("composer.placeholder", text: $text)
                .textFieldStyle(.plain)
                .font(.title3)
                .onGlassPrimary()
                .padding(12)
                .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color.white.opacity(0.12)))
                .onSubmit(add)
                .onChange(of: text) { _, newValue in parse(newValue) }

            if parseFailed {
                Text("composer.parseHint").font(.caption).foregroundStyle(.orange)
            }

            chips(fmt: fmt)

            preview(fmt: fmt)

            HStack {
                Spacer()
                Button(action: add) {
                    Label("composer.add", systemImage: "plus")
                }
                .buttonStyle(.borderedProminent)
                .tint(.white.opacity(0.3))
            }
        }
    }

    // MARK: Chips

    @ViewBuilder
    private func chips(fmt: FasilaFormatter) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                Menu {
                    Picker("composer.kind", selection: binding(\.kind)) {
                        ForEach(ItemKind.allCases) { k in
                            Label(LocalizedStringKey.key("kind.\(k.rawValue)"), systemImage: k.symbol).tag(k)
                        }
                    }
                } label: {
                    Chip { Label(LocalizedStringKey.key("kind.\(draft.kind.rawValue)"), systemImage: draft.kind.symbol) }
                }

                if draft.direction != .at {
                    Menu {
                        Picker("composer.offset", selection: binding(\.minutes)) {
                            ForEach(Self.offsetChoices, id: \.self) { m in Text(verbatim: fmt.minutes(m)).tag(m) }
                        }
                    } label: {
                        Chip { Text(verbatim: fmt.minutes(draft.minutes)) }
                    }
                }

                Menu {
                    Picker("composer.direction", selection: binding(\.direction)) {
                        ForEach(ComposerDraft.Direction.allCases) { d in
                            Text(LocalizedStringKey.key("direction.\(d.rawValue)")).tag(d)
                        }
                    }
                } label: {
                    Chip { Text(LocalizedStringKey.key("direction.\(draft.direction.rawValue)")) }
                }

                Menu {
                    Picker("composer.prayer", selection: binding(\.key)) {
                        ForEach(PrayerKey.anchorable) { k in Text(LocalizedStringKey(k.localizationKey)).tag(k) }
                    }
                } label: {
                    Chip(highlighted: true) { Text(LocalizedStringKey(draft.key.localizationKey)) }
                }

                Text(verbatim: "·").onGlassSecondary()

                Menu {
                    Button("composer.noDuration") { applyChip { $0.end = .instant } }
                    ForEach(Self.durationChoices, id: \.self) { m in
                        Button(fmt.minutes(m)) { applyChip { $0.end = .duration(minutes: m) } }
                    }
                } label: {
                    Chip { Text(verbatim: durationLabel(fmt: fmt)) }
                }

                Text(verbatim: "·").onGlassSecondary()

                Menu {
                    Button("recurrence.daily") { applyChip { $0.recurrence = .daily } }
                    Button("recurrence.onceToday") { applyChip { $0.recurrence = .once(date: store.today()) } }
                    Button("recurrence.onceTomorrow") { applyChip { $0.recurrence = .once(date: store.today().adding(days: 1)) } }
                    Button("recurrence.weekdays") { applyChip { $0.recurrence = .weekly(days: 0b0011111) } } // Sun–Thu
                    Button("recurrence.friday") { applyChip { $0.recurrence = .weekly(days: 1 << 5) } }
                    Button("recurrence.ayyamAlBid") { applyChip { $0.recurrence = .hijriMonthly(days: [13, 14, 15]) } }
                    Button("recurrence.ramadan") { applyChip { $0.recurrence = .hijriMonth(month: 9) } }
                } label: {
                    Chip { Text(verbatim: RecurrenceText.describe(draft.recurrence, store: store)) }
                }
            }
            .padding(.vertical, 2)
        }
    }

    private func durationLabel(fmt: FasilaFormatter) -> String {
        switch draft.end {
        case .instant: return store.language.tr("composer.noDuration")
        case .duration(let m): return fmt.minutes(m)
        case .until(let a): return store.language.tr("composer.until %@", a.describe(lang: store.lang))
        }
    }

    // MARK: Preview + conflicts

    @ViewBuilder
    private func preview(fmt: FasilaFormatter) -> some View {
        let today = store.today()
        let item = draft.item(id: "draft", fallbackTitle: store.language.tr("kind.\(draft.kind.rawValue)"))
        if let occ = store.occurrences(from: today, days: 31, items: [item]).first {
            VStack(alignment: .leading, spacing: 8) {
                Label {
                    Text(verbatim: previewText(occ, today: today, fmt: fmt))
                } icon: {
                    Image(systemName: "clock")
                }
                .font(.subheadline)
                .onGlassSecondary()

                if let conflict = occ.conflicts.first {
                    let prayer = store.language.tr("prayer.\(conflict.prayer)")
                    VStack(alignment: .leading, spacing: 6) {
                        Label {
                            Text(verbatim: store.language.tr("composer.conflict %@", prayer))
                        } icon: {
                            Image(systemName: "exclamationmark.triangle.fill")
                        }
                        if let slot = suggestion(for: occ) {
                            Button {
                                applyChip { $0.setAnchor(key: slot.anchorKey, offset: Int(slot.anchorOffsetMinutes)) }
                            } label: {
                                Text(verbatim: store.language.tr(
                                    "composer.moveTo %@",
                                    describeAnchor(key: slot.anchorKey, offsetMinutes: slot.anchorOffsetMinutes, lang: store.lang)))
                                    .underline()
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .font(.subheadline)
                    .foregroundStyle(Color(red: 1, green: 0.8, blue: 0.4))
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color.orange.opacity(0.18)))
                }
            }
        }
    }

    private func previewText(_ occ: OccurrenceInfo, today: CivilDate, fmt: FasilaFormatter) -> String {
        let time = fmt.time(unix: occ.start)
        guard let date = CivilDate(iso: occ.date), date != today else {
            return store.language.tr("composer.previewToday %@", time)
        }
        return "\(fmt.shortDate(date)), \(time)"
    }

    /// The first free slot (from the core) at or after the conflicting start.
    private func suggestion(for occ: OccurrenceInfo) -> SlotInfo? {
        guard let date = CivilDate(iso: occ.date) else { return nil }
        let minutes = occ.end.map { Int(($0 - occ.start) / 60) } ?? 1
        let slots = store.freeSlots(on: date, durationMinutes: max(1, minutes))
        return slots.first { $0.start >= occ.start } ?? slots.last
    }

    // MARK: Sync

    private func binding<T>(_ keyPath: WritableKeyPath<ComposerDraft, T>) -> Binding<T> {
        Binding(get: { draft[keyPath: keyPath] }, set: { v in applyChip { $0[keyPath: keyPath] = v } })
    }

    /// Apply a chip change and rewrite the sentence from the chips.
    private func applyChip(_ change: (inout ComposerDraft) -> Void) {
        change(&draft)
        var parts = [draft.title, draft.anchor.describe(lang: store.lang)]
        if let m = draft.end.durationMinutes {
            parts.append(store.language.tr("composer.for %@", store.formatter.minutes(m)))
        }
        let sentence = parts.filter { !$0.isEmpty }.joined(separator: " ")
        if sentence != text {
            rewriting = true
            text = sentence
        }
        parseFailed = false
    }

    private func parse(_ value: String) {
        if rewriting {
            rewriting = false
            return
        }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            parseFailed = false
            return
        }
        let today = store.today()
        do {
            let parsed = try parseSentence(text: trimmed, year: Int32(today.year), month: UInt8(today.month),
                                           day: UInt8(today.day), lang: store.lang)
            if let item = ScheduleItem(json: parsed.itemJson) {
                draft = ComposerDraft(item: item)
            }
            parseFailed = false
        } catch {
            // Keep the last good chips; the user is probably mid-sentence.
            draft.title = trimmed
            parseFailed = trimmed.count > 12
        }
    }

    private func add() {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty || !draft.title.isEmpty else { return }
        store.add(draft.item(id: UUID().uuidString, fallbackTitle: store.language.tr("kind.\(draft.kind.rawValue)")))
        rewriting = !text.isEmpty
        text = ""
        draft = ComposerDraft()
        parseFailed = false
    }
}
