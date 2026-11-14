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
5. **One design language.** This is a glass system: depth comes from layered
   frosted panes and state from how lit a pane is. Don't borrow markers from other
   systems (side stripes, underlines, indicator dots, outline rings, hairline dividers).
6. **Honest accuracy.** When a time is estimated (high-latitude rule, polar
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

The sky model lives in the shared core (`fasila-core::sky`, FFI `FasilaEngine.sky(date, now)`):
colour stops, star opacity and the adaptive glass tint. Android and iOS read it from there;
the desktop mirrors it in `apps/desktop/src/lib/sky.ts`.

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
appearing only after sunset (0 at Maghrib → 0.85 at Isha → 1.0 at night).

## Glass system

Everything is expressed through glass. There are three levels and nothing else:
no outlines, no side stripes, no underlines, no indicator dots, no hairline dividers.

| Level | Use | Recipe (web; native equivalents in brackets) |
|---|---|---|
| **Panel** | the frosted sheet a region sits on | blur 26 + white 7.5 %→3 % over an **adaptive** `#060A14` tint (see below); specular top edge `inset 0 1px 0 white 14 %`; 0.5 px inner rim white 7 %; soft drop shadow [`.ultraThinMaterial` dark] |
| **Pane** | secondary state (current prayer, today in the calendar), hover | white 6 %, specular edge white 8 % |
| **Lit pane** | primary state (next prayer, active tab, selected day, focused field, chosen option) | blur 18 + brightness 1.18; white 20 %→9 % with a warm accent glow from the top (`radial #FF9B54 22 %`); specular edge white 38 %; accent-tinted shadow |

**Adaptive tint (legibility).** A fixed tint fails WCAG AA on bright skies
(measured 1.6-3.1:1 on the morning sky). The panel tint opacity is derived from the
sky's brightest stop: `a = min(0.84, 0.36 + max(0, L - 0.02) * 0.8)`, where L is
relative luminance; at night it stays near 0.36. One value per sky, applied to every
panel (viewport-pinned gradients don't combine with backdrop blur in WebKitGTK).
Small text that sits directly on the sky (the next prayer's time) goes on a small
frosted pill with the same adaptive tint. The countdown has a soft veil of darker
sky behind it. Measured result: every text level is ≥ 4.5:1 on panels at sunrise,
noon, Asr and night.

**Solid panes.** A setting (and `prefers-reduced-transparency`) replaces the tint with
solid `#0E1426` and lit panes with solid `#2A3350`, no blur.

**Affordance.** Hover frosting and press feedback (`scale(0.985)`) apply only to
real controls, never to read-only rows.

- Rows inside a panel are transparent until hovered (they frost on hover).
  They are separated by space, not by lines.
- State is shown by **how lit a pane is**. The whole card lights up; never mark
  an edge.
- Editable values in the composer sentence are small frosted chips. The prayer
  anchor is the lit chip.
- The primary button is a lit accent pill with a specular edge.
- Respect *Reduce Transparency* (solid panes) and *Reduce Motion*.

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
  mirrored in the southern hemisphere (desktop and Android).
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
- Section labels: 13 pt semibold, sentence case, secondary ink. The one tracked
  uppercase line is the night lunar-day line in the hero (from Salah Rhythm).
- Copy: no em or en dashes anywhere (UI, notifications, tray). Use a period, comma
  or colon. French puts a no-break space before `:`; Arabic uses «،» and «؛».
  At most one middle dot per line.

## Scheduling UX

**Composer:** a large borderless line ("Meeting 30 min after Asr for 1 hour") above
the same item written as a sentence of editable words. Each value is a small frosted
chip that opens a native picker, and the prayer anchor is the lit chip:

> **Event** *Gym* · **10** min **before** **Maghrib** · for **60** min · **every day**

Word order follows the language (Arabic: «قبل المغرب بـ١٠ دقائق»). Typing re-fills the
words through the shared Rust parser. Below it is the resolved time for today. If
the item touches a prayer's protected time, an amber line says which prayer and
offers the nearest free slot as a link ("Move to 20 min after Asr (15:39)").

**Day strip:** one band from just before Fajr to midnight, drawn in this order:
1. **Prayer labels** across the top: name and time.
2. **Gap lengths** between the protected windows (the *fasila*).
3. **The channel**: a frosted glass groove; protected prayer windows are brighter
   frosted segments inside it.
4. **Today's items** as small lit accent capsules in the channel (amber if one
   clashes with a prayer).
5. **The draft** being composed, as a lit pane capsule that breathes gently.

A soft beam of light marks "now". Gap lengths sit below the channel.

## Notifications

- Adhan notification at each prayer (per-prayer toggle), optional pre-reminder.
- Items: exact alarms (Android `setExactAndAllowWhileIdle`/`setAlarmClock` for alarms,
  iOS `UNCalendarNotificationTrigger` rolling 7-day window within the 64-request limit,
  desktop background scheduler + tray).
- Re-schedule on: app open, location change, settings change, date change, timezone
  change, boot (Android), and Hijri feed update.
