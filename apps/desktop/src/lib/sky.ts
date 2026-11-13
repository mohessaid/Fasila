import type { PrayerTimes } from "./types";

export type Period = "night" | "fajr" | "morning" | "dhuhr" | "asr" | "maghrib";

// docs/DESIGN.md — the living sky.
export const SKY: Record<Period, { top: string; bottom: string; accent: string; light: boolean }> = {
  night: { top: "#0B1026", bottom: "#1B2350", accent: "#8EA2FF", light: false },
  fajr: { top: "#1E2A5A", bottom: "#C77D8E", accent: "#FFB4A2", light: false },
  morning: { top: "#5FA8D3", bottom: "#CDE7F0", accent: "#FFD166", light: true },
  dhuhr: { top: "#3A86C8", bottom: "#A9D6F5", accent: "#FFE08A", light: true },
  asr: { top: "#D98E4A", bottom: "#F4D29C", accent: "#FFF1C9", light: true },
  maghrib: { top: "#5B2A63", bottom: "#E0735A", accent: "#FFB38A", light: false },
};

export function periodAt(t: PrayerTimes | undefined, now: number): Period {
  if (!t) return "night";
  const ge = (x: number | null) => x !== null && now >= x;
  if (ge(t.isha)) return "night";
  if (ge(t.maghrib)) return "maghrib";
  if (ge(t.asr)) return "asr";
  if (ge(t.dhuhr)) return "dhuhr";
  if (ge(t.sunrise)) return "morning";
  if (ge(t.fajr)) return "fajr";
  return "night";
}
