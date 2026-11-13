import type { Day } from "./types";

// The living sky (docs/DESIGN.md). Keyframes are positions in the prayer day:
// 0 = Fajr, 1 = the next Fajr (see ANCHORS below for how real times map onto it). Each keyframe is a 4-stop gradient, top → bottom.
type Stops = [string, string, string, string];

const NIGHT: Stops = ["#010208", "#020510", "#040A1B", "#07132B"];
const KEYFRAMES: { at: number; colors: Stops }[] = [
  { at: 0, colors: ["#07152B", "#163557", "#5B5A76", "#D08B83"] }, // Fajr — first light
  { at: 0.08, colors: ["#173B61", "#5A7F9B", "#D49C91", "#F5C4A2"] }, // sunrise glow
  { at: 0.18, colors: ["#0B315A", "#286B9E", "#7DB8C5", "#D5D6C5"] }, // morning
  { at: 0.52, colors: ["#0D3967", "#347DA8", "#8BC6CF", "#E3D9C8"] }, // afternoon
  { at: 0.72, colors: ["#173454", "#516D8C", "#B67A78", "#E8B38F"] }, // late Asr
  { at: 0.8, colors: ["#0E1530", "#30234A", "#75425B", "#D47E68"] }, // Maghrib
  { at: 0.9, colors: ["#050817", "#0B1230", "#171A45", "#28234D"] }, // Isha
  { at: 1, colors: NIGHT },
];

export type TimeOfDay = "night" | "dawn" | "day" | "dusk";

const hex = (c: string) => [1, 3, 5].map((i) => parseInt(c.slice(i, i + 2), 16));
function mix(a: Stops, b: Stops, t: number): Stops {
  return a.map((c, i) => {
    const x = hex(c), y = hex(b[i]);
    return "#" + x.map((v, j) => Math.round(v + (y[j] - v) * t).toString(16).padStart(2, "0")).join("");
  }) as Stops;
}

export function skyColors(progress: number, tod: TimeOfDay): Stops {
  if (tod === "night") return NIGHT;
  const p = Math.min(1, Math.max(0, progress));
  const i = KEYFRAMES.findIndex((k) => k.at >= p);
  if (i <= 0) return KEYFRAMES[0].colors;
  const a = KEYFRAMES[i - 1], b = KEYFRAMES[i];
  return mix(a.colors, b.colors, (p - a.at) / (b.at - a.at));
}

/** Stars fade out after dawn and come back after Maghrib. */
export function starOpacity(p: number): number {
  if (p < 0.08) return 0.65 - (p / 0.08) * 0.45;
  if (p < 0.18) return 0.2 - ((p - 0.08) / 0.1) * 0.2;
  if (p < 0.7) return 0;
  if (p < 0.82) return ((p - 0.7) / 0.12) * 0.85;
  return 0.85 + Math.min(0.15, ((p - 0.82) / 0.18) * 0.15);
}

export interface SkyState {
  /** 0 at Fajr → 1 at the next Fajr, pinned to the day's real prayer times. */
  progress: number;
  /** 0 at sunrise → 1 at Maghrib; the sun's place on its arc. */
  sun: number;
  isDaytime: boolean;
  timeOfDay: TimeOfDay;
}

// Canonical positions of each event in the prayer day. Real event times are mapped
// piecewise-linearly onto these, so the sky follows the actual sun in every season and
// at every latitude (a winter Isha at 18:30 still gets the night sky and the stars).
const ANCHORS: [keyof Day["times"], number][] = [
  ["fajr", 0], ["sunrise", 0.08], ["dhuhr", 0.35], ["asr", 0.62], ["maghrib", 0.8], ["isha", 0.9],
];

function warp(day: Day, nextFajr: number, now: number): number {
  const pts: [number, number][] = [];
  for (const [k, pos] of ANCHORS) {
    const t = day.times[k];
    if (typeof t === "number") pts.push([t, pos]);
  }
  pts.push([nextFajr, 1]);
  if (now <= pts[0][0]) return 0;
  for (let i = 1; i < pts.length; i++) {
    const [t0, p0] = pts[i - 1], [t1, p1] = pts[i];
    if (now < t1) return p0 + ((now - t0) / (t1 - t0)) * (p1 - p0);
  }
  return 1;
}

/** `days` = [yesterday, today, tomorrow]. */
export function skyState(days: Day[], now: number): SkyState {
  const [y, t, n] = days;
  if (!t) return { progress: 1, sun: 0, isDaytime: false, timeOfDay: "night" };
  const fajr = t.times.fajr, sunrise = t.times.sunrise, maghrib = t.times.maghrib, isha = t.times.isha;
  // The prayer day we are in: yesterday's if it is still before today's Fajr.
  const before = fajr !== null && now < fajr;
  const progress = before
    ? y ? warp(y, fajr!, now) : 0.95
    : warp(t, n?.times.fajr ?? (fajr ?? now) + 86400, now);
  const sun = sunrise && maghrib ? (now - sunrise) / (maghrib - sunrise) : -1;
  const isDaytime = sun >= 0 && sun <= 1;
  let timeOfDay: TimeOfDay = "day";
  if (fajr === null || before || (isha !== null && now >= isha)) timeOfDay = "night";
  else if (sunrise !== null && now < sunrise) timeOfDay = "dawn";
  else if (maghrib !== null && now >= maghrib) timeOfDay = "dusk";
  return { progress, sun: Math.min(1, Math.max(0, sun)), isDaytime, timeOfDay };
}

export const ACCENT = "#FF9B54";
