# Fasila design language

*Fāṣila* (فاصلة) — the interval between two things. The app is built around the
**gap until the next prayer**, and around keeping the rest of life inside those gaps.

## Principles

1. **Prayer first, clock second.** Every screen leads with the next prayer and the
   time remaining. Clock times are secondary text. Users schedule by anchor
   ("15 min before Fajr"), never by picking 05:12.
2. **Calm, not busy.** One focal element per screen. No ads, no streaks, no guilt.
3. **Native everywhere.** SwiftUI on Apple, Jetpack Compose on Android, Tauri on
   Windows/Linux. Same core (Rust), same tokens, platform-idiomatic controls.
4. **RTL is first-class.** Arabic is not a translation afterthought. Layouts use
   logical directions (start/end), icons with direction mirror, numerals follow
   the locale (Arabic-Indic in `ar`), the day dial runs right-to-left in RTL.
5. **Honest accuracy.** When a time is estimated (high-latitude rule, polar
   fallback), the UI says so with a small badge and an explanation.

## The living sky (signature element)

The background is a slow gradient that follows the **current prayer period**, so
the app's colour tells you where you are in the day before you read anything.
Content sits on frosted glass panes over it (glassmorphism).

| Period (from → to)   | Top        | Bottom     | Accent    |
|----------------------|------------|------------|-----------|
| Night (Isha → Fajr)  | `#0B1026`  | `#1B2350`  | `#8EA2FF` |
| Fajr (Fajr → Sunrise)| `#1E2A5A`  | `#C77D8E`  | `#FFB4A2` |
| Morning (→ Dhuhr)    | `#5FA8D3`  | `#CDE7F0`  | `#FFD166` |
| Dhuhr (→ Asr)        | `#3A86C8`  | `#A9D6F5`  | `#FFE08A` |
| Asr (→ Maghrib)      | `#D98E4A`  | `#F4D29C`  | `#FFF1C9` |
| Maghrib (→ Isha)     | `#5B2A63`  | `#E0735A`  | `#FFB38A` |

Transitions between periods cross-fade over ~20 minutes around each boundary.

## Glass

- Fill: white at 10–14 % (dark periods) / 22–28 % (light periods).
- Blur: 24–30 px backdrop blur (SwiftUI `.ultraThinMaterial`; Compose: blur on API 31+,
  translucent fill fallback below; Tauri: `backdrop-filter`).
- Border: 1 px white at 18–25 %, plus a 1 px inner top highlight.
- Radius: 24 (cards), 16 (rows), 999 (chips).
- Text on glass: white at 95 % (primary) / 70 % (secondary). Contrast ≥ 4.5:1 is
  checked against the darkest and lightest sky in each period.
- Respect *Reduce Transparency*: switch to solid tinted panes.
  Respect *Reduce Motion*: no sky animation, instant period change.

## Day dial

A 270° arc representing the day from Fajr to Isha. Prayers are dots on the arc;
the sun marker sits at "now"; the arc segment between the current and next
prayer is highlighted — that segment *is* the fasila. In the centre: next prayer
name, countdown, clock time below.

## Typography

- Latin: system UI font (SF Pro / Roboto / Segoe UI / Inter fallback).
- Arabic: system Arabic (SF Arabic / Noto Naskh/Kufi) — on desktop bundle
  *IBM Plex Sans Arabic*.
- Countdown: tabular numerals, 56–72 pt, weight 300.

## Scheduling UX

The composer is one line of text *and* a chip row that stay in sync:

```
[ Meeting 30 min after asr for 1h            ]
 ( 30 min ) ( after ▾ ) ( Asr ▾ )  ·  ( 1 h ▾ )  ·  ( Daily ▾ )
```

Typing fills the chips (via the shared Rust parser); tapping chips rewrites the
description. A live preview shows today's resolved time and, if the block
touches a prayer's protected window, a gentle amber warning with a "move to after
Asr" suggestion from `free_slots`.

## Notifications

- Adhan notification at each prayer (per-prayer toggle), optional pre-reminder.
- Items: exact alarms (Android `setExactAndAllowWhileIdle`/`setAlarmClock` for alarms,
  iOS `UNCalendarNotificationTrigger` rolling 7-day window within the 64-request limit,
  desktop background scheduler + tray).
- Re-schedule on: app open, location change, settings change, date change, timezone
  change, boot (Android), and Hijri feed update.
