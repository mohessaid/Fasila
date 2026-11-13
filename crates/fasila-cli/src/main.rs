//! `fasila` — the agent/script-friendly interface to fasila-core.
//!
//! Every command prints JSON by default (stable keys, unix timestamps + ISO strings),
//! or a human table with `--text`. Exit code 2 = bad input, 3 = unparseable sentence.

use clap::{Parser, Subcommand};
use fasila_core::{
    hijri, method::Method, parse, schedule, Config, Date, HijriDate, Item, Madhab, TimeKey,
};
use serde_json::{json, Value};

#[derive(Parser)]
#[command(name = "fasila", version, about = "Prayer times, Hijri calendar and prayer-anchored schedules")]
struct Cli {
    /// Config JSON file (same schema as the apps). Overridden by --lat/--lon/--method.
    #[arg(long, global = true, env = "FASILA_CONFIG")]
    config: Option<String>,
    #[arg(long, global = true, allow_hyphen_values = true)]
    lat: Option<f64>,
    #[arg(long, global = true, allow_hyphen_values = true)]
    lon: Option<f64>,
    /// Calculation method id (see `fasila methods`).
    #[arg(long, global = true)]
    method: Option<String>,
    /// ISO country code (chooses the default method).
    #[arg(long, global = true)]
    country: Option<String>,
    #[arg(long, global = true)]
    hanafi: bool,
    /// UTC offset in minutes used only for display with --text and for "today".
    #[arg(long, global = true, default_value_t = 0, allow_hyphen_values = true)]
    utc_offset: i32,
    /// Human-readable output instead of JSON.
    #[arg(long, global = true)]
    text: bool,
    #[command(subcommand)]
    cmd: Cmd,
}

#[derive(Subcommand)]
enum Cmd {
    /// Prayer times for one or more days.
    Times {
        #[arg(long)]
        date: Option<String>,
        #[arg(long, default_value_t = 1)]
        days: u32,
    },
    /// The next prayer from now.
    Next,
    /// Convert dates (Gregorian → Hijri, or Hijri → Gregorian with --from-hijri).
    Hijri {
        date: Option<String>,
        #[arg(long)]
        from_hijri: bool,
    },
    /// Qibla bearing and distance to the Kaaba.
    Qibla,
    /// Parse a natural-language sentence into an anchored item.
    Parse { sentence: Vec<String>, #[arg(long, default_value = "en")] lang: String },
    /// Expand an items JSON file into occurrences with prayer conflicts.
    Schedule {
        items: String,
        #[arg(long)]
        date: Option<String>,
        #[arg(long, default_value_t = 7)]
        days: u32,
    },
    /// Free slots of a given length that do not touch any prayer.
    Slots {
        #[arg(long, default_value_t = 60)]
        minutes: u32,
        #[arg(long)]
        date: Option<String>,
    },
    /// List calculation methods.
    Methods,
}

fn fail(code: i32, msg: impl std::fmt::Display) -> ! {
    eprintln!("{}", json!({ "error": msg.to_string() }));
    std::process::exit(code)
}

fn now() -> i64 {
    std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_secs() as i64
}

fn iso(ts: i64) -> String {
    let d = Date::from_unix_utc(ts);
    let s = ts.rem_euclid(86_400);
    format!("{d}T{:02}:{:02}:{:02}Z", s / 3600, s % 3600 / 60, s % 60)
}

fn hm(ts: i64, off: i32) -> String {
    let s = (ts + off as i64 * 60).rem_euclid(86_400);
    format!("{:02}:{:02}", s / 3600, s % 3600 / 60)
}

fn load_config(cli: &Cli) -> Config {
    let mut cfg = match &cli.config {
        Some(p) => {
            let s = std::fs::read_to_string(p).unwrap_or_else(|e| fail(2, format!("{p}: {e}")));
            serde_json::from_str(&s).unwrap_or_else(|e| fail(2, format!("{p}: {e}")))
        }
        None => Config::default(),
    };
    if let (Some(lat), Some(lon)) = (cli.lat, cli.lon) {
        let country = cli.country.clone().unwrap_or_default();
        cfg = Config::for_place("", lat, lon, "", &country);
    }
    if let Some(m) = &cli.method {
        let method = Method::from_id(m).unwrap_or_else(|| fail(2, format!("unknown method {m}")));
        let keep = cfg.params.clone();
        cfg.params = fasila_core::Params::for_method(method);
        cfg.params.adjustments = keep.adjustments;
    }
    if cli.hanafi {
        cfg.params.madhab = Madhab::Hanafi;
    }
    if !cfg.coordinates().is_valid() {
        fail(2, "invalid coordinates");
    }
    cfg
}

fn date_arg(s: &Option<String>, off: i32) -> Date {
    match s {
        Some(s) => Date::parse_iso(s).unwrap_or_else(|| fail(2, format!("bad date {s}"))),
        None => Date::from_unix_with_offset(now(), off),
    }
}

fn day_json(d: &schedule::Day) -> Value {
    let mut times = serde_json::Map::new();
    for k in TimeKey::ALL {
        times.insert(k.id().into(), d.times.get(k).map(|t| json!({ "unix": t, "utc": iso(t) })).unwrap_or(Value::Null));
    }
    json!({
        "date": d.date.to_string(),
        "weekday": d.weekday,
        "hijri": { "year": d.hijri.year, "month": d.hijri.month, "day": d.hijri.day,
                   "month_name": hijri::MONTH_NAMES_EN[d.hijri.month as usize - 1],
                   "month_name_ar": hijri::MONTH_NAMES_AR[d.hijri.month as usize - 1] },
        "times": times,
        "fajr_estimated": d.times.fajr_estimated,
        "isha_estimated": d.times.isha_estimated,
        "polar_fallback": d.times.polar_fallback,
        "occasions": d.occasions,
    })
}

fn out(cli: &Cli, v: Value, text: impl FnOnce() -> String) {
    if cli.text {
        println!("{}", text());
    } else {
        println!("{}", serde_json::to_string_pretty(&v).unwrap());
    }
}

fn main() {
    let cli = Cli::parse();
    let off = cli.utc_offset;
    match &cli.cmd {
        Cmd::Methods => {
            let v: Vec<Value> = Method::ALL.iter().map(|m| json!({ "id": m.id(), "name": m.display_name() })).collect();
            out(&cli, json!(v), || Method::ALL.iter().map(|m| format!("{:24} {}", m.id(), m.display_name())).collect::<Vec<_>>().join("\n"));
        }
        Cmd::Parse { sentence, lang } => {
            let s = sentence.join(" ");
            let today = Date::from_unix_with_offset(now(), off);
            match parse::parse(&s, today) {
                Ok(p) => {
                    let desc = schedule::describe_anchor(&p.start, lang);
                    out(&cli, json!({ "parsed": p, "description": desc }), || desc.clone());
                }
                Err(e) => fail(3, e),
            }
        }
        Cmd::Times { date, days } => {
            let cfg = load_config(&cli);
            let e = cfg.engine();
            let d0 = date_arg(date, off);
            let ds: Vec<schedule::Day> = (0..*days as i64).map(|i| e.day(d0.add_days(i))).collect();
            out(&cli, json!({ "method": cfg.params.method.id(), "location": cfg.location, "days": ds.iter().map(day_json).collect::<Vec<_>>() }), || {
                let mut s = format!("{:10}  {:10}  {:>5} {:>5} {:>5} {:>5} {:>5} {:>5}\n", "date", "hijri", "fajr", "rise", "dhuhr", "asr", "magh", "isha");
                for d in &ds {
                    let f = |k| d.times.get(k).map(|t| hm(t, off)).unwrap_or("--:--".into());
                    s += &format!("{}  {}  {} {} {} {} {} {}\n", d.date, d.hijri, f(TimeKey::Fajr), f(TimeKey::Sunrise), f(TimeKey::Dhuhr), f(TimeKey::Asr), f(TimeKey::Maghrib), f(TimeKey::Isha));
                }
                s
            });
        }
        Cmd::Next => {
            let cfg = load_config(&cli);
            let e = cfg.engine();
            let n = now();
            let today = Date::from_unix_with_offset(n, off);
            let found = (0..2).find_map(|i| e.day(today.add_days(i)).times.next_prayer(n));
            let (k, t) = found.unwrap_or_else(|| fail(2, "no upcoming prayer"));
            out(&cli, json!({ "prayer": k.id(), "unix": t, "utc": iso(t), "seconds_until": t - n }), || {
                format!("{} at {} (in {} min)", schedule::key_name(k, "en"), hm(t, off), (t - n) / 60)
            });
        }
        Cmd::Hijri { date, from_hijri } => {
            let cfg = load_config(&cli);
            if *from_hijri {
                let s = date.clone().unwrap_or_else(|| fail(2, "give a Hijri date YYYY-MM-DD"));
                let parts: Vec<i32> = s.split('-').filter_map(|x| x.parse().ok()).collect();
                if parts.len() != 3 || !(1..=12).contains(&parts[1]) || !(1..=30).contains(&parts[2]) {
                    fail(2, "bad Hijri date, expected YYYY-MM-DD")
                }
                let g = cfg.hijri.to_gregorian(HijriDate::new(parts[0], parts[1] as u8, parts[2] as u8));
                out(&cli, json!({ "gregorian": g.to_string() }), || g.to_string());
            } else {
                let d = date_arg(date, off);
                let h = cfg.hijri.to_hijri(d);
                let name = hijri::MONTH_NAMES_EN[h.month as usize - 1];
                out(&cli, json!({ "gregorian": d.to_string(), "hijri": h, "month_name": name, "month_name_ar": hijri::MONTH_NAMES_AR[h.month as usize - 1], "occasions": hijri::occasions(h) }), || format!("{} {} {} AH", h.day, name, h.year));
            }
        }
        Cmd::Qibla => {
            let cfg = load_config(&cli);
            let b = fasila_core::qibla(cfg.coordinates());
            let km = fasila_core::prayer::distance_to_kaaba_km(cfg.coordinates());
            out(&cli, json!({ "bearing_degrees": b, "distance_km": km }), || format!("{b:.1}° from true north, {km:.0} km"));
        }
        Cmd::Schedule { items, date, days } => {
            let cfg = load_config(&cli);
            let s = std::fs::read_to_string(items).unwrap_or_else(|e| fail(2, format!("{items}: {e}")));
            let items: Vec<Item> = serde_json::from_str(&s).unwrap_or_else(|e| fail(2, format!("items: {e}")));
            let occ = cfg.engine().occurrences(&items, date_arg(date, off), *days);
            out(&cli, json!(occ), || {
                occ.iter().map(|o| format!("{} {} {}{}", o.date, hm(o.start, off), o.title, if o.conflicts.is_empty() { String::new() } else { format!("  ⚠ overlaps {}", o.conflicts.iter().map(|c| c.prayer.id()).collect::<Vec<_>>().join(", ")) })).collect::<Vec<_>>().join("\n")
            });
        }
        Cmd::Slots { minutes, date } => {
            let cfg = load_config(&cli);
            let slots = cfg.engine().free_slots(date_arg(date, off), *minutes);
            out(&cli, json!(slots), || slots.iter().map(|s| format!("{}–{}  ({})", hm(s.start, off), hm(s.end, off), schedule::describe_anchor(&s.anchor, "en"))).collect::<Vec<_>>().join("\n"));
        }
    }
}
