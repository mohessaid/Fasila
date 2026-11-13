import { invoke } from "@tauri-apps/api/core";
import type { Anchor, City, Config, Day, Item, MethodInfo, Occurrence, Params, Parsed, Prefs, Slot } from "./types";

export interface Loaded {
  config: Config | null; items: Item[]; prefs: Prefs; methods: MethodInfo[]; cities: City[]; today: string | null;
}

export const api = {
  load: () => invoke<Loaded>("load"),
  defaultConfig: (name: string, latitude: number, longitude: number, timezone: string, country: string) =>
    invoke<Config>("default_config", { name, latitude, longitude, timezone, country }),
  methodParams: (method: string) => invoke<Params>("method_params", { method }),
  saveConfig: (config: Config) => invoke<string>("save_config", { config }),
  saveItems: (items: Item[]) => invoke<void>("save_items", { items }),
  savePrefs: (prefs: Prefs) => invoke<void>("save_prefs", { prefs }),
  today: () => invoke<string>("today"),
  days: (start: string, count: number) => invoke<Day[]>("days", { start, count }),
  occurrences: (start: string, count: number) => invoke<Occurrence[]>("occurrences", { start, count }),
  preview: (item: Item, date: string) => invoke<Occurrence[]>("preview", { item, date }),
  parse: (text: string, lang: string, today: string) =>
    invoke<{ parsed: Parsed; description: string }>("parse_text", { text, lang, today }),
  describe: (anchor: Anchor, lang: string) => invoke<string>("describe", { anchor, lang }),
  freeSlots: (date: string, minutes: number) => invoke<Slot[]>("free_slots", { date, minutes }),
  qibla: () => invoke<{ bearing: number; distance_km: number }>("qibla"),
  refreshHijri: () =>
    invoke<{ changed: number; online: boolean; updated: string; source: string | null }>("refresh_hijri"),
  testNotification: (title: string, body: string) => invoke<void>("test_notification", { title, body }),
  autostart: {
    enable: () => invoke("plugin:autostart|enable"),
    disable: () => invoke("plugin:autostart|disable"),
    isEnabled: () => invoke<boolean>("plugin:autostart|is_enabled"),
  },
};

export const newId = () =>
  (crypto as Crypto & { randomUUID?: () => string }).randomUUID?.() ?? Math.random().toString(36).slice(2);
