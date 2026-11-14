import { api, newId } from "./api";
import { dict, resolveLang, type Lang } from "./i18n";
import { makeFormat } from "./format";
import { glassTint, skyColors, skyState, starOpacity } from "./sky";
import { invoke } from "@tauri-apps/api/core";
import type { City, Config, Day, Item, MethodInfo, Occurrence, Prefs, TimeKey } from "./types";
import { PRAYERS } from "./types";

const DEFAULT_PREFS: Prefs = {
  lang: "auto", numerals: "auto", clock: "24h", adhan: [true, true, true, true, true],
  pre_reminder_minutes: 10, sunrise_warning_minutes: 15, auto_hijri_feed: true, reduce_motion: false, solid_panes: false,
};

class AppState {
  ready = $state(false);
  config = $state<Config | null>(null);
  items = $state<Item[]>([]);
  prefs = $state<Prefs>(DEFAULT_PREFS);
  methods = $state<MethodInfo[]>([]);
  cities = $state<City[]>([]);
  today = $state<string>("");
  /** Yesterday, today, tomorrow. */
  days = $state<Day[]>([]);
  todayOcc = $state<Occurrence[]>([]);
  now = $state(Math.floor(Date.now() / 1000));
  tab = $state<"today" | "schedule" | "calendar" | "qibla" | "settings">("today");
  toast = $state<string | null>(null);

  lang: Lang = $derived(resolveLang(this.prefs.lang));
  t = $derived(dict(this.lang));
  dir = $derived(this.lang === "ar" ? "rtl" : "ltr");
  fmt = $derived(
    makeFormat({
      lang: this.lang, numerals: this.prefs.numerals, clock: this.prefs.clock,
      timeZone: this.config?.location.timezone || Intl.DateTimeFormat().resolvedOptions().timeZone,
    }),
  );
  todayDay = $derived(this.days[1]);
  skyState = $derived(skyState(this.days, this.now));
  skyColors = $derived(skyColors(this.skyState.progress, this.skyState.timeOfDay));
  stars = $derived(starOpacity(this.skyState.progress));
  tint = $derived(glassTint(this.skyColors));
  /** Moon phase from the Rust core, refreshed every 10 minutes. */
  moon = $state<{ phase: number; illumination: number; waxing: boolean } | null>(null);

  /** Next obligatory prayer from now, looking into tomorrow. */
  next = $derived.by(() => {
    for (const d of this.days.slice(1)) {
      for (const k of PRAYERS) {
        const at = d.times[k];
        if (at !== null && at > this.now) return { key: k, at, day: d };
      }
    }
    return null;
  });

  /** The prayer whose time we are in (Fajr until sunrise; Isha until Fajr incl. yesterday's). */
  current = $derived.by((): { key: TimeKey; at: number } | null => {
    const nx = this.next;
    if (!nx) return null;
    const all: { key: TimeKey; at: number }[] = [];
    for (const d of this.days) for (const k of PRAYERS) if (d.times[k] !== null) all.push({ key: k, at: d.times[k]! });
    const prev = all.filter((p) => p.at <= this.now).pop();
    if (!prev) return null;
    // Between sunrise and Dhuhr there is no current obligatory prayer.
    const sr = this.todayDay?.times.sunrise;
    if (prev.key === "fajr" && sr && this.now >= sr) return null;
    return prev;
  });

  private timer: ReturnType<typeof setInterval> | undefined;
  /** Debug-only clock shift from the backend (FASILA_NOW); 0 in release builds. */
  private offset = 0;

  async init() {
    this.offset = await invoke<number>("clock_offset_secs").catch(() => 0);
    this.now = Math.floor(Date.now() / 1000) + this.offset;
    const l = await api.load();
    this.config = l.config;
    this.items = l.items;
    this.prefs = { ...DEFAULT_PREFS, ...l.prefs };
    this.methods = l.methods;
    this.cities = l.cities;
    this.today = l.today ?? "";
    if (this.config) await this.refresh();
    this.ready = true;
    this.timer = setInterval(() => this.tick(), 1000);
    if (this.config && this.prefs.auto_hijri_feed) {
      api.refreshHijri().then((r) => { if (r.changed > 0) this.refresh(); }).catch(() => {});
    }
  }

  private async tick() {
    this.now = Math.floor(Date.now() / 1000) + this.offset;
    if (this.now % 600 === 0 || !this.moon) {
      invoke<{ phase: number; illumination: number; waxing: boolean }>("moon_phase", { at: this.now })
        .then((m) => (this.moon = m))
        .catch(() => {});
    }
    // Roll over to a new day (local to the configured location).
    if (this.config && this.now % 30 === 0) {
      const t = await api.today();
      if (t !== this.today) await this.refresh();
    }
  }

  async refresh() {
    if (!this.config) return;
    this.today = await api.today();
    const y = new Date(this.today + "T00:00:00Z");
    y.setUTCDate(y.getUTCDate() - 1);
    this.days = await api.days(y.toISOString().slice(0, 10), 3);
    this.todayOcc = await api.occurrences(this.today, 1);
  }

  async saveConfig(c: Config) {
    this.config = c;
    await api.saveConfig($state.snapshot(c) as Config);
    await this.refresh();
    this.flash(this.t.saved);
  }

  async saveItems(items: Item[]) {
    this.items = items;
    await api.saveItems($state.snapshot(items) as Item[]);
    this.todayOcc = await api.occurrences(this.today, 1);
  }

  async upsertItem(item: Item) {
    const it = { ...item, id: item.id || newId() };
    const i = this.items.findIndex((x) => x.id === it.id);
    const items = i >= 0 ? this.items.map((x) => (x.id === it.id ? it : x)) : [...this.items, it];
    await this.saveItems(items);
  }

  async removeItem(id: string) {
    await this.saveItems(this.items.filter((x) => x.id !== id));
  }

  async savePrefs(p: Prefs) {
    this.prefs = p;
    await api.savePrefs($state.snapshot(p) as Prefs);
  }

  keyName(k: TimeKey, date?: Day) {
    if (k === "dhuhr" && date?.weekday === "friday") return this.t.jumuah;
    return this.t.keys[k];
  }

  flash(msg: string) {
    this.toast = msg;
    setTimeout(() => (this.toast = null), 2200);
  }
}

export const app = new AppState();
