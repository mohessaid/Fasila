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
   the locale (Arabic-Indic in `ar`), and the sun's path runs right-to-left in RTL.
5. **Honest accuracy.** When a time is estimated (high-latitude rule, polar
   fallback), the UI says so with a small badge and an explanation.

## Palette

Always dark, lit by a warm lamp-light accent.

| Token | Value |
|-------|-------|
| background | `#060A14` |
| foreground | `#F8FAFC` |
| secondary / muted text | `#E2E8F0` / `#CBD5E1` |
| accent (primary) | `#FF9B54` (text on it: `#060A14`) |
| success / pending / destructive | `#7BE0B5` / `#FFB26B` / `#EF476F` |

## The living sky (signature element)

The background is a 4-stop vertical gradient that blends continuously through the
prayer day. Position in the day (`progress`, 0 at Fajr → 1 at the next Fajr) is
**pinned to the real prayer times**: Fajr → 0, sunrise → 0.08, Dhuhr → 0.35,
Asr → 0.62, Maghrib → 0.8, Isha → 0.9. Between those points time is linearly
interpolated, so a winter Isha at 18:30 is as dark as a summer Isha at 22:00.

| progress | stops (top → bottom) |
|---|---|
| 0.00 Fajr | `#07152B #163557 #5B5A76 #D08B83` |
| 0.08 sunrise | `#173B61 #5A7F9B #D49C91 #F5C4A2` |
| 0.18 morning | `#0B315A #286B9E #7DB8C5 #D5D6C5` |
| 0.52 afternoon | `#0D3967 #347DA8 #8BC6CF #E3D9C8` |
| 0.72 late Asr | `#173454 #516D8C #B67A78 #E8B38F` |
| 0.80 Maghrib | `#0E1530 #30234A #75425B #D47E68` |
| 0.90 Isha | `#050817 #0B1230 #171A45 #28234D` |
| night (Isha → Fajr) | `#010208 #020510 #040A1B #07132B` |

**Stars:** 45, at random positions in the upper 80 % of the sky, each 0.5–2.5 px,
twinkling between 58 % and 100 % of its own maximum opacity (0.28–0.9) over 2–6 s.
The whole field fades by `progress`: 0.65 → 0.2 by sunrise, gone by 0.18,
returning from 0.7 to 0.85 by Maghrib and 1.0 at night.

## Glass

- Fill: white at 3.5 % over a dark blur (iOS `BlurView`/`.ultraThinMaterial` dark,
  intensity ≈ 38; web `backdrop-filter: blur(26px)` plus a 34 % `#060A14` tint).
- Border: 0.5 px, white at 10 %.
- **Highlight:** a single 1 px line along the top edge, inset 18 px from each side,
  white at 16 %.
- Shadow: `#020617`, y 10, blur 24, 18 %.
- Radius: 24 (cards), 16 (rows), 999 (chips).
- Respect *Reduce Transparency* (solid panes) and *Reduce Motion* (no twinkle, glow
  or shimmer).

## Celestial scene (Today hero)

A 268-unit-high scene above the countdown:

- **Horizon:** two mountain silhouettes (far and near), white fading out at
  68–80 % of their height. They are 12 %/18 % opaque by day and 20 %/26 % at night.
- **Sun's path:** a half-ellipse from sunrise (start edge) to Maghrib (end edge),
  inset 24, height 144, drawn as a faint dotted line. Dhuhr and Asr are dots on it
  with small labels. The next prayer's dot uses the accent.
- **Sun** (sunrise ≤ now ≤ Maghrib): a 92-unit body at `(now − sunrise)/(Maghrib − sunrise)`
  along the arc. Its glow pulses (opacity 0.4 ↔ 0.8, scale +15 %, 2.5 s). The disc
  is a radial gradient from foreground to accent.
- **Moon** (otherwise): centred near the top, **drawn in the real phase** computed
  by the core (`moon_phase`), with three faint craters and a slow shimmer. It is
  mirrored in the southern hemisphere.
- **Under the scene:** "*Asr is next*", the countdown, "at 15:19". At night there is
  also a line in the accent colour, *LUNAR DAY 5 · 26 % LIT*, or *AYYĀM AL-BĪḌ*
  on the 13th–15th. The lunar day is the **Hijri day under the user's country
  profile**, advancing at Maghrib.
- In RTL the sun's path is mirrored so it reads in the reading direction. The moon
  is not mirrored.

## Typography

- Latin: Inter (400/500/600/700).
- Arabic: Tajawal (400/500/700/800).
- Countdown: tabular numerals, 44–64 pt, weight 300.
- Section labels: 12 pt semibold, uppercase, letter-spacing 2. Arabic labels have
  no case and no tracking.

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
