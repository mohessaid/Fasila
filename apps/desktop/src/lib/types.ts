// Mirrors the serde shapes of fasila-core (crates/fasila-core/src).
export type TimeKey =
  | "imsak" | "fajr" | "sunrise" | "dhuhr" | "asr"
  | "sunset" | "maghrib" | "isha" | "midnight" | "last_third";
export const PRAYERS: TimeKey[] = ["fajr", "dhuhr", "asr", "maghrib", "isha"];
export const ANCHOR_KEYS: TimeKey[] = ["imsak", "fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha", "midnight", "last_third"];

export interface PrayerTimes {
  date: string;
  imsak: number | null; fajr: number | null; sunrise: number | null; dhuhr: number | null;
  asr: number | null; sunset: number | null; maghrib: number | null; isha: number | null;
  midnight: number | null; last_third: number | null;
  fajr_estimated: boolean; isha_estimated: boolean; polar_fallback: boolean;
}
export interface HijriDate { year: number; month: number; day: number }
export interface Day {
  date: string;
  weekday: string;
  hijri: HijriDate;
  times: PrayerTimes;
  occasions: string[];
}
export interface Anchor { key: TimeKey; offset_minutes: number }
export type End =
  | { type: "none" }
  | { type: "duration"; minutes: number }
  | { type: "until"; anchor: Anchor };
export type Recurrence =
  | { type: "once"; date: string }
  | { type: "daily" }
  | { type: "weekly"; days: number }
  | { type: "hijri_monthly"; days: number[] }
  | { type: "hijri_yearly"; month: number; day: number }
  | { type: "hijri_month"; month: number };
export type ItemKind = "alarm" | "task" | "event";
export interface Item {
  id: string; title: string; kind: ItemKind; start: Anchor; end: End;
  recurrence: Recurrence; enabled: boolean; remind_before_minutes: number | null; notes: string;
}
export interface Conflict { prayer: TimeKey; prayer_time: number; window_start: number; window_end: number }
export interface Occurrence {
  item_id: string; title: string; kind: ItemKind; date: string;
  start: number; end: number | null; conflicts: Conflict[];
}
export interface Slot { anchor: Anchor; start: number; end: number }

export type Twilight = { type: "angle"; value: number } | { type: "minutes"; value: number };
export interface Adjustments { fajr: number; sunrise: number; dhuhr: number; asr: number; maghrib: number; isha: number }
export interface Params {
  method: string; fajr_angle: number; isha: Twilight; isha_ramadan_minutes: number | null;
  maghrib: Twilight; madhab: "standard" | "hanafi";
  high_latitude_rule: "auto" | "middle_of_the_night" | "seventh_of_the_night" | "twilight_angle" | "none";
  polar_rule: "nearest_latitude" | "unresolved";
  midnight: "standard" | "jafari"; rounding: "none" | "nearest" | "safe";
  method_adjustments: Adjustments; adjustments: Adjustments; elevation_m: number; imsak_minutes: number;
}
export interface MonthStart { year: number; month: number; starts_on: string }
export interface HijriProfile { calendar: "umm_al_qura" | "tabular"; day_adjustment: number; month_starts: MonthStart[] }
export interface Location { name: string; latitude: number; longitude: number; elevation_m: number; timezone: string; country: string }
export interface Guard { before_minutes: number; after_minutes: number; jumuah_extra_minutes: number }
export interface Config { location: Location; params: Params; hijri: HijriProfile; guard: Guard }
export interface Prefs {
  lang: "auto" | "en" | "ar" | "fr"; numerals: "auto" | "latn" | "arab"; clock: "24h" | "12h";
  adhan: boolean[]; pre_reminder_minutes: number; sunrise_warning_minutes: number;
  auto_hijri_feed: boolean; reduce_motion: boolean;
}
export interface City { name: string; name_ar: string; country: string; latitude: number; longitude: number; timezone: string }
export interface MethodInfo { id: string; name: string }
export interface Parsed { title: string; start: Anchor; end: End; recurrence: Recurrence | null; kind: ItemKind | null }
