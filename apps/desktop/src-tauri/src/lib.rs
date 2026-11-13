//! Fasila desktop (Windows / Linux) — Tauri shell around fasila-core.
//!
//! * Commands expose the core engine to the web UI.
//! * A background thread fires adhan and schedule notifications and keeps the
//!   tray tooltip showing the next prayer, even when the window is hidden.

use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use chrono::{TimeZone, Utc};
use fasila_core::{
    hijri::feed::Feed, parse, schedule, Anchor, Config, Date, Item, ItemKind, Method, TimeKey,
};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use tauri::menu::{Menu, MenuItem};
use tauri::tray::{MouseButton, TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Manager, State};
use tauri_plugin_notification::NotificationExt;

const FEED_URL: &str = "https://raw.githubusercontent.com/mohessaid/fasila/main/data/hijri/announcements.json";
const BUNDLED_FEED: &str = include_str!("../../../../data/hijri/announcements.json");
const CITIES: &str = include_str!("../../../../data/cities.json");

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct Prefs {
    /// "auto", "en", "ar" or "fr".
    pub lang: String,
    /// "auto", "latn" (0-9) or "arab" (٠-٩).
    pub numerals: String,
    /// "24h" or "12h".
    pub clock: String,
    /// Adhan notification per prayer (fajr, dhuhr, asr, maghrib, isha).
    pub adhan: [bool; 5],
    /// Reminder N minutes before each prayer (0 = off).
    pub pre_reminder_minutes: u32,
    /// Warn N minutes before sunrise that Fajr is ending (0 = off).
    pub sunrise_warning_minutes: u32,
    pub auto_hijri_feed: bool,
    pub reduce_motion: bool,
}

impl Default for Prefs {
    fn default() -> Self {
        Prefs {
            lang: "auto".into(),
            numerals: "auto".into(),
            clock: "24h".into(),
            adhan: [true; 5],
            pre_reminder_minutes: 10,
            sunrise_warning_minutes: 15,
            auto_hijri_feed: true,
            reduce_motion: false,
        }
    }
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(default)]
pub struct Store {
    pub config: Option<Config>,
    pub items: Vec<Item>,
    pub prefs: Prefs,
}

pub struct AppState {
    store: Mutex<Store>,
    path: PathBuf,
}

impl AppState {
    fn save(&self, s: &Store) -> Result<(), String> {
        if let Some(dir) = self.path.parent() {
            std::fs::create_dir_all(dir).map_err(|e| e.to_string())?;
        }
        // Write atomically so a crash never leaves a half-written file.
        let tmp = self.path.with_extension("json.tmp");
        std::fs::write(&tmp, serde_json::to_vec_pretty(s).unwrap()).map_err(|e| e.to_string())?;
        std::fs::rename(&tmp, &self.path).map_err(|e| e.to_string())
    }
}

type Shared<'a> = State<'a, Arc<AppState>>;

fn tz_of(cfg: &Config) -> Option<chrono_tz::Tz> {
    cfg.location.timezone.parse().ok()
}

/// The local civil date at the configured location.
fn local_today(cfg: &Config) -> Date {
    let now = Utc::now();
    let offset_secs = match tz_of(cfg) {
        Some(tz) => {
            use chrono::Offset;
            tz.offset_from_utc_datetime(&now.naive_utc()).fix().local_minus_utc()
        }
        None => chrono::Local::now().offset().local_minus_utc(),
    };
    Date::from_unix_with_offset(now.timestamp(), offset_secs / 60)
}

fn local_hm(cfg: &Config, ts: i64) -> String {
    let dt = Utc.timestamp_opt(ts, 0).single().unwrap_or_default();
    match tz_of(cfg) {
        Some(tz) => dt.with_timezone(&tz).format("%H:%M").to_string(),
        None => dt.with_timezone(&chrono::Local).format("%H:%M").to_string(),
    }
}

fn parse_date(s: &str) -> Result<Date, String> {
    Date::parse_iso(s).ok_or_else(|| format!("invalid date {s}"))
}

fn config_of(s: &Store) -> Result<Config, String> {
    s.config.clone().ok_or_else(|| "not configured".to_string())
}

#[tauri::command]
fn load(state: Shared) -> Value {
    let s = state.store.lock().unwrap();
    let methods: Vec<Value> = Method::ALL.iter().map(|m| json!({ "id": m.id(), "name": m.display_name() })).collect();
    let today = s.config.as_ref().map(|c| local_today(c).to_string());
    json!({
        "config": s.config,
        "items": s.items,
        "prefs": s.prefs,
        "methods": methods,
        "cities": serde_json::from_str::<Value>(CITIES).unwrap_or(Value::Null),
        "today": today,
    })
}

#[tauri::command]
fn default_config(name: String, latitude: f64, longitude: f64, timezone: String, country: String) -> Config {
    Config::for_place(&name, latitude, longitude, &timezone, &country)
}

#[tauri::command]
fn method_params(method: String) -> Result<fasila_core::Params, String> {
    Method::from_id(&method).map(fasila_core::Params::for_method).ok_or_else(|| "unknown method".into())
}

#[tauri::command]
fn save_config(state: Shared, config: Config) -> Result<String, String> {
    if !config.coordinates().is_valid() {
        return Err("coordinates out of range".into());
    }
    let mut s = state.store.lock().unwrap();
    s.config = Some(config);
    state.save(&s)?;
    Ok(local_today(s.config.as_ref().unwrap()).to_string())
}

#[tauri::command]
fn save_items(state: Shared, items: Vec<Item>) -> Result<(), String> {
    let mut s = state.store.lock().unwrap();
    s.items = items;
    state.save(&s)
}

#[tauri::command]
fn save_prefs(state: Shared, prefs: Prefs) -> Result<(), String> {
    let mut s = state.store.lock().unwrap();
    s.prefs = prefs;
    state.save(&s)
}

#[tauri::command]
fn today(state: Shared) -> Result<String, String> {
    let s = state.store.lock().unwrap();
    Ok(local_today(&config_of(&s)?).to_string())
}

#[tauri::command]
fn days(state: Shared, start: String, count: u32) -> Result<Vec<schedule::Day>, String> {
    let cfg = config_of(&state.store.lock().unwrap())?;
    let e = cfg.engine();
    let d0 = parse_date(&start)?;
    Ok((0..count.min(400) as i64).map(|i| e.day(d0.add_days(i))).collect())
}

#[tauri::command]
fn occurrences(state: Shared, start: String, count: u32) -> Result<Vec<schedule::Occurrence>, String> {
    let s = state.store.lock().unwrap();
    let cfg = config_of(&s)?;
    Ok(cfg.engine().occurrences(&s.items, parse_date(&start)?, count.min(62)))
}

/// Resolve a draft item for a date (live preview in the composer).
#[tauri::command]
fn preview(state: Shared, item: Item, date: String) -> Result<Vec<schedule::Occurrence>, String> {
    let cfg = config_of(&state.store.lock().unwrap())?;
    let mut item = item;
    item.enabled = true;
    item.recurrence = schedule::Recurrence::Daily;
    Ok(cfg.engine().occurrences(&[item], parse_date(&date)?, 1))
}

#[tauri::command]
fn parse_text(text: String, lang: String, today: String) -> Result<Value, String> {
    let today = parse_date(&today)?;
    let p = parse::parse(&text, today).map_err(|e| match e {
        parse::ParseError::NoPrayer => "no_prayer".to_string(),
        parse::ParseError::Empty => "empty".to_string(),
    })?;
    let description = schedule::describe_anchor(&p.start, &lang);
    Ok(json!({ "parsed": p, "description": description }))
}

#[tauri::command]
fn describe(anchor: Anchor, lang: String) -> String {
    schedule::describe_anchor(&anchor, &lang)
}

#[tauri::command]
fn free_slots(state: Shared, date: String, minutes: u32) -> Result<Vec<schedule::Slot>, String> {
    let cfg = config_of(&state.store.lock().unwrap())?;
    Ok(cfg.engine().free_slots(parse_date(&date)?, minutes))
}

#[tauri::command]
fn qibla(state: Shared) -> Result<Value, String> {
    let cfg = config_of(&state.store.lock().unwrap())?;
    Ok(json!({
        "bearing": fasila_core::qibla(cfg.coordinates()),
        "distance_km": fasila_core::prayer::distance_to_kaaba_km(cfg.coordinates()),
    }))
}

fn fetch_feed() -> Result<Feed, String> {
    let body = ureq::get(FEED_URL)
        .config()
        .timeout_global(Some(Duration::from_secs(10)))
        .build()
        .call()
        .map_err(|e| e.to_string())?
        .body_mut()
        .read_to_string()
        .map_err(|e| e.to_string())?;
    serde_json::from_str(&body).map_err(|e| e.to_string())
}

/// Pull the latest country Hijri announcements (falls back to the bundled copy offline).
#[tauri::command]
fn refresh_hijri(state: Shared) -> Result<Value, String> {
    let (feed, online) = match fetch_feed() {
        Ok(f) => (f, true),
        Err(_) => (serde_json::from_str(BUNDLED_FEED).map_err(|e| e.to_string())?, false),
    };
    let mut s = state.store.lock().unwrap();
    let Some(cfg) = s.config.as_mut() else { return Err("not configured".into()) };
    let country = cfg.location.country.clone();
    let changed = feed.apply(&country, &mut cfg.hijri);
    let source = feed.regions.get(&country.to_ascii_uppercase()).map(|r| r.source.clone());
    state.save(&s)?;
    Ok(json!({ "changed": changed, "online": online, "updated": feed.updated, "source": source }))
}

#[tauri::command]
fn test_notification(app: AppHandle, title: String, body: String) -> Result<(), String> {
    app.notification().builder().title(title).body(body).show().map_err(|e| e.to_string())
}

// ---------------------------------------------------------------------------
// Background notifier
// ---------------------------------------------------------------------------

fn lang_of(p: &Prefs) -> String {
    match p.lang.as_str() {
        "ar" | "fr" | "en" => p.lang.clone(),
        _ => {
            let l = std::env::var("LANG").unwrap_or_default();
            if l.starts_with("ar") {
                "ar".into()
            } else if l.starts_with("fr") {
                "fr".into()
            } else {
                "en".into()
            }
        }
    }
}

struct Alert {
    at: i64,
    title: String,
    body: String,
}

fn t(lang: &str, en: &str, ar: &str, fr: &str) -> String {
    match lang {
        "ar" => ar,
        "fr" => fr,
        _ => en,
    }
    .to_string()
}

fn alerts_for(store: &Store, cfg: &Config, date: Date) -> Vec<Alert> {
    let lang = lang_of(&store.prefs);
    let e = cfg.engine();
    let day = e.day(date);
    let mut out = Vec::new();
    for (i, k) in TimeKey::PRAYERS.iter().enumerate() {
        let Some(at) = day.times.get(*k) else { continue };
        let jumuah = *k == TimeKey::Dhuhr && day.weekday == fasila_core::Weekday::Friday;
        let name = if jumuah { t(&lang, "Jumu'ah", "الجمعة", "Joumou'a") } else { schedule::key_name(*k, &lang).to_string() };
        if store.prefs.adhan[i] {
            out.push(Alert {
                at,
                title: t(&lang, &format!("{name} — it's time to pray"), &format!("حان الآن موعد صلاة {name}"), &format!("{name} — c'est l'heure de la prière")),
                body: local_hm(cfg, at),
            });
        }
        let pre = store.prefs.pre_reminder_minutes as i64;
        if pre > 0 && store.prefs.adhan[i] {
            out.push(Alert {
                at: at - pre * 60,
                title: t(&lang, &format!("{name} in {pre} min"), &format!("{name} بعد {pre} دقيقة"), &format!("{name} dans {pre} min")),
                body: t(&lang, "Prepare for prayer (wudu).", "استعد للصلاة (الوضوء).", "Préparez-vous (ablutions)."),
            });
        }
    }
    let sw = store.prefs.sunrise_warning_minutes as i64;
    if let (true, Some(sr)) = (sw > 0, day.times.sunrise) {
        out.push(Alert {
            at: sr - sw * 60,
            title: t(&lang, &format!("Fajr ends in {sw} min"), &format!("ينتهي وقت الفجر بعد {sw} دقيقة"), &format!("Fajr se termine dans {sw} min")),
            body: t(&lang, "Sunrise is approaching.", "اقترب الشروق.", "Le lever du soleil approche."),
        });
    }
    for o in e.occurrences_on(&store.items, &day) {
        let when = match o.end {
            Some(end) => format!("{}–{}", local_hm(cfg, o.start), local_hm(cfg, end)),
            None => local_hm(cfg, o.start),
        };
        let icon = match o.kind {
            ItemKind::Alarm => "⏰",
            ItemKind::Task => "✓",
            ItemKind::Event => "◷",
        };
        out.push(Alert { at: o.start, title: format!("{icon} {}", o.title), body: when.clone() });
        if let Some(item) = store.items.iter().find(|i| i.id == o.item_id) {
            if let Some(m) = item.remind_before_minutes.filter(|m| *m > 0) {
                out.push(Alert {
                    at: o.start - m as i64 * 60,
                    title: t(&lang, &format!("Soon: {}", o.title), &format!("قريبًا: {}", o.title), &format!("Bientôt : {}", o.title)),
                    body: when,
                });
            }
        }
    }
    out
}

fn next_prayer_label(store: &Store, cfg: &Config) -> Option<String> {
    let lang = lang_of(&store.prefs);
    let e = cfg.engine();
    let now = Utc::now().timestamp();
    let today = local_today(cfg);
    let (k, at) = (0..2).find_map(|i| e.day(today.add_days(i)).times.next_prayer(now))?;
    let mins = (at - now + 59) / 60;
    let (h, m) = (mins / 60, mins % 60);
    let left = if h > 0 { format!("{h}h {m:02}m") } else { format!("{m}m") };
    Some(format!("Fasila — {} {} ({})", schedule::key_name(k, &lang), local_hm(cfg, at), left))
}

fn spawn_notifier(app: AppHandle, state: Arc<AppState>) {
    std::thread::spawn(move || {
        let mut last = Utc::now().timestamp();
        loop {
            std::thread::sleep(Duration::from_secs(10));
            let now = Utc::now().timestamp();
            let (alerts, label) = {
                let s = state.store.lock().unwrap();
                let Some(cfg) = s.config.clone() else {
                    last = now;
                    continue;
                };
                let today = local_today(&cfg);
                // Yesterday covers Isha/midnight items that fall after civil midnight.
                let mut alerts = Vec::new();
                for d in [today.add_days(-1), today, today.add_days(1)] {
                    alerts.extend(alerts_for(&s, &cfg, d));
                }
                (alerts, next_prayer_label(&s, &cfg))
            };
            // After sleep/suspend `now - last` is large; never replay more than 2 minutes of alerts.
            let from = last.max(now - 120);
            for a in alerts.into_iter().filter(|a| a.at > from && a.at <= now) {
                let _ = app.notification().builder().title(&a.title).body(&a.body).show();
            }
            if let (Some(label), Some(tray)) = (label, app.tray_by_id("main")) {
                let _ = tray.set_tooltip(Some(&label));
            }
            last = now;
        }
    });
}

fn show_main(app: &AppHandle) {
    if let Some(w) = app.get_webview_window("main") {
        let _ = w.show();
        let _ = w.unminimize();
        let _ = w.set_focus();
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| show_main(app)))
        .plugin(tauri_plugin_notification::init())
        .plugin(tauri_plugin_autostart::init(tauri_plugin_autostart::MacosLauncher::LaunchAgent, Some(vec!["--hidden"])))
        .setup(|app| {
            let dir = app.path().app_data_dir().expect("app data dir");
            let path = dir.join("fasila.json");
            let store: Store = std::fs::read(&path)
                .ok()
                .and_then(|b| serde_json::from_slice(&b).ok())
                .unwrap_or_default();
            let state = Arc::new(AppState { store: Mutex::new(store), path });
            app.manage(state.clone());

            let show = MenuItem::with_id(app, "show", "Show Fasila", true, None::<&str>)?;
            let quit = MenuItem::with_id(app, "quit", "Quit", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&show, &quit])?;
            TrayIconBuilder::with_id("main")
                .icon(app.default_window_icon().cloned().expect("icon"))
                .tooltip("Fasila")
                .menu(&menu)
                .show_menu_on_left_click(false)
                .on_menu_event(|app, ev| match ev.id().as_ref() {
                    "show" => show_main(app),
                    "quit" => app.exit(0),
                    _ => {}
                })
                .on_tray_icon_event(|tray, ev| {
                    if let TrayIconEvent::Click { button: MouseButton::Left, .. } = ev {
                        show_main(tray.app_handle());
                    }
                })
                .build(app)?;

            if std::env::args().any(|a| a == "--hidden") {
                if let Some(w) = app.get_webview_window("main") {
                    let _ = w.hide();
                }
            }
            spawn_notifier(app.handle().clone(), state);
            Ok(())
        })
        .on_window_event(|window, event| {
            // Closing the window keeps Fasila in the tray so adhan notifications still fire.
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                let _ = window.hide();
                api.prevent_close();
            }
        })
        .invoke_handler(tauri::generate_handler![
            load,
            default_config,
            method_params,
            save_config,
            save_items,
            save_prefs,
            today,
            days,
            occurrences,
            preview,
            parse_text,
            describe,
            free_slots,
            qibla,
            refresh_hijri,
            test_notification
        ])
        .run(tauri::generate_context!())
        .expect("error while running Fasila");
}
