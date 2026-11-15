import SwiftUI

/// Choose the place: current location (CoreLocation + reverse geocoding), the
/// bundled shared city list, or a geocoder search for anything else.
struct PlacePicker: View {
    var onDone: (() -> Void)? = nil

    @Environment(FasilaStore.self) private var store
    @State private var query = ""
    @State private var searchResults: [ResolvedPlace] = []
    @State private var busy = false
    @State private var error: String?
    @State private var locator: LocationProvider?

    private var cities: [City] {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return Array(City.all.prefix(12)) }
        return City.all.filter {
            $0.name.localizedCaseInsensitiveContains(q) || $0.nameAr.contains(q) || $0.country.caseInsensitiveCompare(q) == .orderedSame
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button {
                Task { await useCurrentLocation() }
            } label: {
                Label("place.useCurrent", systemImage: "location.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(.white.opacity(0.25))
            .disabled(busy)

            TextField("place.search", text: $query)
                .textFieldStyle(.roundedBorder)
                .autocorrectionDisabled()
                .onSubmit { Task { await search() } }

            if busy { ProgressView().frame(maxWidth: .infinity) }
            if let error {
                Text(error).font(.fasila(.footnote)).foregroundStyle(fasilaWarn)
            }

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    ForEach(cities) { city in
                        row(title: city.displayName(lang: store.lang), subtitle: city.country) {
                            store.setPlace(city)
                            onDone?()
                        }
                    }
                    ForEach(Array(searchResults.enumerated()), id: \.offset) { _, place in
                        row(title: place.name, subtitle: place.country) { choose(place) }
                    }
                }
            }
            .frame(maxHeight: 320)
        }
    }

    private func row(title: String, subtitle: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(title).onGlassPrimary()
                Spacer()
                Text(subtitle).font(.fasila(.caption)).onGlassSecondary()
            }
            .padding(.vertical, 10)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func choose(_ p: ResolvedPlace) {
        store.setPlace(name: p.name, latitude: p.latitude, longitude: p.longitude, timezone: p.timezone, country: p.country)
        onDone?()
    }

    private func useCurrentLocation() async {
        busy = true
        defer { busy = false }
        let provider = locator ?? LocationProvider()
        locator = provider
        do {
            choose(try await provider.currentPlace())
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func search() async {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty, cities.isEmpty else { return }
        busy = true
        defer { busy = false }
        do {
            searchResults = try await LocationProvider.search(q)
            error = searchResults.isEmpty ? store.language.tr("place.noResults") : nil
        } catch {
            self.error = store.language.tr("place.noResults")
        }
    }
}
