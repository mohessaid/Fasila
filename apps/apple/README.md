# Fasila for iOS and macOS

The native Apple client: SwiftUI (iOS 17, macOS 14), a WidgetKit "next prayer" widget (iOS),
and no astronomy in Swift. Prayer times, the Hijri calendar, occasions, scheduling, conflict
detection, free slots, the sentence parser and the Qibla all come from `crates/fasila-core`
through the UniFFI layer in `crates/fasila-ffi`.

## Build

Requirements: macOS with Xcode 15 or later, [rustup](https://rustup.rs), and XcodeGen.

```sh
brew install xcodegen

# Rust targets (the build script also adds any that are missing)
rustup target add aarch64-apple-ios aarch64-apple-ios-sim x86_64-apple-ios \
                  aarch64-apple-darwin x86_64-apple-darwin

# 1. Build the Rust core and generate the Swift bindings
#    → apps/apple/Generated/FasilaFFI.xcframework and Generated/fasila_ffi.swift
scripts/build-apple.sh            # add --debug for a faster, unoptimised build

# 2. Generate the Xcode project
cd apps/apple
xcodegen generate

# 3. Open it, pick the Fasila-iOS or Fasila-macOS scheme, set your team, and run
open Fasila.xcodeproj
```

Run `scripts/build-apple.sh` again after any change to `fasila-core` or `fasila-ffi`.
`Generated/`, `Supporting/` (Info.plist and entitlements, written by XcodeGen) and
`Fasila.xcodeproj` are not checked in.

### Signing

- Set `DEVELOPMENT_TEAM` in `project.yml` or in Xcode.
- iOS: the app and the widget share the App Group `group.app.fasila.shared` (config and
  items live in its container so the widget can read them). Register the group for your
  team, or rename it in `project.yml` and `Fasila/Shared/SharedStorage.swift`.
- macOS: the app is sandboxed with location and outgoing-network (geocoding) entitlements.
  Its data lives in `~/Library/Containers/app.fasila/Data/Library/Application Support/Fasila`.

## Layout

```
Fasila/
  App/        FasilaApp (scene, language and RTL environment, time-change hooks), RootView, onboarding
  Shared/     code also compiled into the widget: storage, PrayerKey/CivilDate, sky periods and
              snapshot, formatting (location time zone), language
  Core/       FasilaStore (@Observable), JSON config/items models, location, notifications,
              bundled shared data (cities, Hijri announcements)
  Design/     glass panes, living sky, day dial
  Views/      Today, Schedule + Composer, Calendar, Qibla, Settings, place picker
  Resources/  Localizable.xcstrings (en, ar, fr)
FasilaWidget/ the WidgetKit extension (small and medium)
```

## Data

- `config.json` uses the shared schema (`fasila_core::config::Config`). It is edited as a JSON
  tree, so fields written by newer apps survive a round trip. First run calls
  `default_config_json` (the method follows the country), and `month_starts` for the country
  from `data/hijri/announcements.json` (bundled) are merged into `hijri.month_starts`.
- `items.json` is a `[Item]` array (`fasila_core::schedule::Item`). New items come from
  `parse_sentence(...).item_json` or from the composer chips.
- `data/cities.json` is bundled for the place picker. CoreLocation (one fix and a reverse
  geocode for the name, country and time zone) and geocoder search are the alternatives.

## Behaviour notes

- **Times** are formatted in the configured location's time zone (`location.timezone`), with
  the digits of the UI locale (Arabic-Indic in `ar`).
- **Language**: System, English, العربية or Français. The override sets `\.locale` and
  `\.layoutDirection` at the root, so Arabic is right-to-left. The day dial also runs
  right-to-left; the Qibla compass is never mirrored.
- **Notifications**: a rolling window of up to 60 `UNCalendarNotificationTrigger` requests
  (adhan per prayer, an optional pre-reminder, and items with their `remind_before_minutes`),
  which stays under the iOS limit of 64. The window is rebuilt when the app becomes active, on
  settings, item or language changes, and on day, clock or time-zone changes. To use a custom
  adhan sound, add `adhan.caf` (30 seconds or less) to the app bundle.
- **Accessibility**: Reduce Motion turns off the sky cross-fade, and Reduce Transparency
  replaces the glass with solid tinted panes. The dial is exposed as one element that reads
  the next prayer and the countdown.
