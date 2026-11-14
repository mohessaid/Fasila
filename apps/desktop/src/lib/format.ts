import type { Lang } from "./i18n";

export interface FormatOpts { lang: Lang; numerals: "auto" | "latn" | "arab"; clock: "24h" | "12h"; timeZone: string }

function locale(o: FormatOpts): string {
  const base = o.lang === "ar" ? "ar" : o.lang === "fr" ? "fr-FR" : "en-GB";
  const nu = o.numerals === "auto" ? (o.lang === "ar" ? "arab" : "latn") : o.numerals;
  return `${base}-u-nu-${nu}`;
}

function safeTz(tz: string): string | undefined {
  try {
    new Intl.DateTimeFormat("en", { timeZone: tz });
    return tz;
  } catch {
    return undefined;
  }
}

export function makeFormat(o: FormatOpts) {
  const loc = locale(o);
  const timeZone = safeTz(o.timeZone);
  const time = new Intl.DateTimeFormat(loc, { hour: "2-digit", minute: "2-digit", hour12: o.clock === "12h", timeZone });
  const num = new Intl.NumberFormat(loc, { useGrouping: false });
  const num2 = new Intl.NumberFormat(loc, { minimumIntegerDigits: 2 });
  const dayMonth = new Intl.DateTimeFormat(loc, { day: "numeric", month: "long", timeZone: "UTC" });
  const longDate = new Intl.DateTimeFormat(loc, { weekday: "long", day: "numeric", month: "long", year: "numeric", timeZone: "UTC" });
  const monthYear = new Intl.DateTimeFormat(loc, { month: "long", year: "numeric", timeZone: "UTC" });
  const isoToDate = (iso: string) => new Date(iso + "T00:00:00Z");
  const arabDigits = loc.endsWith("-arab");
  return {
    /** Localize ASCII digits inside a string produced by the Rust core. */
    digits: (s: string) => (arabDigits ? s.replace(/[0-9]/g, (d) => "٠١٢٣٤٥٦٧٨٩"[+d]) : s),
    time: (ts: number | null | undefined) => (ts == null ? "--:--" : time.format(new Date(ts * 1000))),
    num: (n: number) => num.format(n),
    /** Countdown "1:05:09" or "05:09". */
    countdown: (secs: number) => {
      const s = Math.max(0, Math.floor(secs));
      const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), r = s % 60;
      return h > 0 ? `${num.format(h)}:${num2.format(m)}:${num2.format(r)}` : `${num2.format(m)}:${num2.format(r)}`;
    },
    /** Compact duration: "1 h 20 min". */
    duration: (mins: number, h: string, m: string) => {
      const hh = Math.floor(mins / 60), mm = mins % 60;
      return [hh ? `${num.format(hh)} ${h}` : "", mm || !hh ? `${num.format(mm)} ${m}` : ""].filter(Boolean).join(" ");
    },
    dayMonth: (iso: string) => dayMonth.format(isoToDate(iso)),
    longDate: (iso: string) => longDate.format(isoToDate(iso)),
    monthYear: (iso: string) => monthYear.format(isoToDate(iso)),
  };
}
export type Fmt = ReturnType<typeof makeFormat>;

export function addDays(iso: string, n: number): string {
  const d = new Date(iso + "T00:00:00Z");
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}
