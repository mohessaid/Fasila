# Accuracy

Prayer times leave no room for error, so Fasila measures its own error and
publishes the method, the parameters and the source of every rule.

## Astronomy

`crates/fasila-core/src/astro.rs` implements the apparent solar position from
Jean Meeus, *Astronomical Algorithms* (2nd ed.): geometric mean longitude and
anomaly, equation of the centre, nutation (Δψ, Δε), aberration, true obliquity,
apparent sidereal time and ΔT (Espenak & Meeus).

Every event is solved **iteratively at its own instant**. Most prayer apps
compute the sun's declination once at noon and reuse it for Fajr and Isha, many
hours away. The sun's declination changes by up to 0.4° per day, so that shortcut
costs up to about a minute at higher latitudes. Fasila evaluates the sun at the
event's instant and repeats until the time converges (< 0.01 s).

### Verified against an independent ephemeris

`tools/verify/verify.py` takes every computed event and evaluates the sun's
actual altitude at that instant with **Astropy/ERFA**, which is accurate to the
arcsecond. It turns any altitude error into a time error. The check covers 13 cities
and 5 dates (equinoxes, solstices, today), from 34°S to 51°N:

| Event                          | Worst error |
|--------------------------------|-------------|
| Sunrise / sunset (−0.833°)     | 2.1 s       |
| Fajr / Isha (twilight angles)  | 2.0 s       |
| Dhuhr (meridian transit)       | 1.0 s       |
| Asr (shadow ratio, both madhabs)| 1.3 s      |

```
pip install astropy adhanpy
cargo build -p fasila-core --example dump
python tools/verify/verify.py target/debug/examples/dump
```

The same script cross-checks against `adhanpy`, an independent implementation of
the same methods. All differences are under a minute, except these known and
intentional ones:

* adhan adds **+1 min to Dhuhr** for MWL and Singapore and **+3 min** for Dubai.
  Fasila shows the true transit unless the authority itself publishes an offset.
* adhan does not implement Umm al-Qura's **120-minute Isha in Ramadan**. Fasila does.

## Asr

The noon shadow (*fayʾ al-zawāl*) is fixed by the sun's declination **at noon**.
The Asr altitude is `acot(k + tan|φ − δ_noon|)` with k = 1 (Standard) or 2
(Hanafi). This target stays constant through the afternoon and is solved
iteratively.

## Rounding

The default is **precautionary rounding**: prayer *starts* round up and sunrise
(the end of Fajr) rounds down. A displayed time is therefore never earlier than
the true start of a prayer. "Nearest minute" and "seconds" are available too.

## Methods

| Id | Authority | Fajr | Isha | Notes |
|----|-----------|------|------|-------|
| `muslim_world_league` | Muslim World League | 18° | 17° | |
| `isna` | Islamic Society of North America | 15° | 15° | |
| `egypt` | Egyptian General Authority of Survey | 19.5° | 17.5° | |
| `umm_al_qura` | Umm al-Qura University, Makkah | 18.5° | 90 min | 120 min in Ramadan |
| `karachi` | Univ. of Islamic Sciences, Karachi | 18° | 18° | |
| `tehran` | Institute of Geophysics, Tehran | 17.7° | 14° | Maghrib 4.5°, Jafari midnight |
| `jafari` | Leva Institute, Qum | 16° | 14° | Maghrib 4°, Jafari midnight |
| `dubai` | Dubai / UAE | 18.2° | 18.2° | |
| `kuwait` | Kuwait | 18° | 17.5° | |
| `qatar` | Qatar | 18° | 90 min | |
| `singapore` / `malaysia` / `indonesia` | MUIS / JAKIM / Kemenag | 20° | 18° | |
| `france` | UOIF | 12° | 12° | |
| `turkey` | Diyanet | 18° | 17° | Temkin: sunrise −7, Dhuhr +5, Asr +4, Maghrib +7 |
| `russia` | Spiritual Administration of Muslims of Russia | 16° | 15° | |
| `moonsighting_committee` | Moonsighting Committee Worldwide | seasonal | seasonal | Shaukat's seasonal twilight; Dhuhr +5, Maghrib +3 |
| `algeria` | Ministry of Religious Affairs and Wakfs | 18° | 17° | |
| `morocco` | Ministry of Habous | 19° | 17° | |
| `tunisia` | Ministry of Religious Affairs | 18° | 18° | |

Some national authorities add precautionary minutes (*ihtiyāṭ*) that vary by
region and are revised over time. For those, only the angles above are encoded.
**Compare the app with your local official timetable once and set per-prayer
adjustments if needed.** They are stored in your configuration and apply to
every day after that.

## High latitudes and polar regions

* **High-latitude rule** (`auto` picks one-seventh above 48°, otherwise middle of the
  night): Fajr is never earlier than *sunrise − portion × night*, and Isha never
  later than *sunset + portion × night*. The night used for Fajr is the one that
  ends at that sunrise. When the rule decides a time, the UI marks it **estimated**.
* **Polar rule** (*aqrab al-bilād*): if the sun does not rise or set, times come
  from the nearest latitude where it does, scanning toward the equator from 65°.
  The UI says so. The alternative is "unresolved", where only Dhuhr is shown.

## Hijri calendar

* **Umm al-Qura** official month starts for 1343–1500 AH (1924–2077). The table
  comes from `hijri-converter` (MIT). The tabular calendar is used outside that range.
* **Tabular** (Kuwaiti) arithmetical calendar.
* **Day adjustment** of ±2 days.
* **Official announcements** (`data/hijri/announcements.json`): per-country month
  starts announced after moon sighting. They take precedence, and the months after
  them are re-chained so that each month has 29 or 30 days. The apps pull this file
  automatically.
  **Only add entries backed by an official announcement, and cite the source.**

## Time zones

The core works only in UTC instants and civil dates, and never handles daylight
saving. Each app renders times with its platform's time-zone database for the
location's IANA zone, so DST transitions are always correct.
