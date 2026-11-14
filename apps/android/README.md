# Fasila for Android

Native Android client (Kotlin, Jetpack Compose, Material 3). All prayer-time astronomy,
Hijri calendars, the sentence parser, conflict detection and free-slot search come from the
shared Rust core (`crates/fasila-core`) through the UniFFI bindings in `crates/fasila-ffi`.
The app never re-implements them; it stores the shared JSON documents, builds a
`FasilaEngine` and renders results.

## Requirements

- A full JDK (with `javac`) that Gradle 9.9 supports, e.g. JDK 21 or 25, and the Android SDK with platform 37 (compileSdk) and 35
- Gradle 9.9 (wrapper), AGP 9.5, Kotlin 2.4. AGP 9 compiles Kotlin itself, so there is no `kotlin-android` plugin
- Android NDK (r26+), e.g. `sdkmanager "ndk;27.2.12479018"`
- Rust (stable) with `rustup`, plus `cargo install cargo-ndk`

## Build

```sh
# 1. Native library + Kotlin bindings (from the repository root)
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/<version>"
scripts/build-android.sh            # PROFILE=debug for a faster, unoptimised .so

# 2. App
cd apps/android
./gradlew :app:assembleDebug        # or open apps/android in Android Studio
./gradlew :app:installDebug
```

`scripts/build-android.sh` writes:

- `app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64}/libfasila_ffi.so` (via `cargo ndk`)
- `app/src/generated/uniffi/uniffi/fasila_ffi/fasila_ffi.kt` (UniFFI Kotlin bindings, JNA-based),
  added to the `main` source set in `app/build.gradle.kts`

Both are build outputs and git-ignored. Re-run the script whenever `crates/fasila-ffi` changes.
`data/` at the repository root (`cities.json`, `hijri/announcements.json`) is packaged as assets.

## Layout

```
app/src/main/java/app/fasila/
  FasilaApp.kt, MainActivity.kt
  core/       FasilaRepository (config.json + items.json, engine, StateFlows), JSON views over the
              shared schema (FasilaConfig, ScheduleItem), prayer timeline, living-sky model, formatting
  location/   FusedLocationProvider → LocationManager fallback, Geocoder → nearest bundled city fallback
  alarm/      AlarmScheduler (rolling 48 h window), AlarmReceiver, RescheduleReceiver, SafetyNetWorker,
              notification channels (adhan, reminders, alarms)
  widget/     Glance "next prayer" widget
  ui/         FasilaRoot (bottom navigation, edge-to-edge), theme (living sky, glass), Today (day dial),
              Schedule (composer with synced chips), Calendar, Qibla, Settings
```

## Behaviour notes

- **Storage.** `files/config.json` is the shared `Config` document, `files/items.json` an array of
  `Item`s. Both are edited as JSON trees so unknown fields written by other Fasila apps survive.
  A config change is validated by constructing a `FasilaEngine` before it is saved.
- **Methods.** Switching method takes the method's parameters from `method_params_json` and keeps
  the user's madhab, high-latitude/polar rules, rounding, adjustments, imsak and elevation. Until the
  user picks a method, moving to another country applies `recommended_method(country)`.
- **Hijri announcements.** The bundled `hijri/announcements.json` (refreshed daily from
  `BuildConfig.HIJRI_FEED_URL`) is merged into `config.hijri.month_starts` for the location's country.
- **Time zones.** Times are rendered in `config.location.timezone`, not the device zone. A device
  location sets it from `TimeZone.getDefault()`; a city from the list uses the city's zone.
- **Digits and RTL.** Formatting uses the locale's `DecimalStyle`/`NumberFormat`, so Arabic locales
  whose default numbering system is Arabic-Indic get Arabic-Indic digits. Layouts use start/end; the
  day dial mirrors in RTL. Per-app language (English / العربية / Français) uses AppCompat locales.
- **Alarms.** Adhan, pre-prayer and item reminders use `setExactAndAllowWhileIdle`; user alarms use
  `setAlarmClock`. `USE_EXACT_ALARM` (API 33+) / `SCHEDULE_EXACT_ALARM` (31–32); without it the app
  falls back to `setAndAllowWhileIdle` and shows a banner. Alarms are re-registered on every firing,
  app open, settings/items change, boot, time/zone/date/locale change and by a daily WorkManager job.
- **Accessibility.** System animator scale 0 disables sky animation (Reduce Motion). "Solid panes"
  in Settings (also on automatically with high-contrast text) replaces glass with opaque panes.
  Over the light skies (Morning, Dhuhr, Asr) text on glass uses navy ink instead of white so it
  keeps ≥ 4.5:1 contrast.

## Play Store notes

`USE_EXACT_ALARM` is restricted to alarm/clock/calendar apps; Fasila's prayer alarms and
user alarms are its core function, but if the listing is rejected, drop it, remove `maxSdkVersion` from
`SCHEDULE_EXACT_ALARM` and rely on that user-granted permission — the code already handles both.

## Fonts

`res/font/` bundles Inter (Rasmus Andersson) and Tajawal (Boutros Fonts), both under the
SIL Open Font License 1.1. Latin text uses Inter, Arabic text uses Tajawal.
