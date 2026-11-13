//! Dump unrounded prayer times as JSON lines. Used by `tools/verify` to compare
//! against independent references (Astropy/ERFA ephemeris and the adhan library).
//!
//! usage: dump <method> <madhab:standard|hanafi> <lat> <lon> <YYYY-MM-DD> [days]
use fasila_core::*;

fn main() {
    let a: Vec<String> = std::env::args().collect();
    let method = Method::from_id(&a[1]).expect("method");
    let mut p = Params::for_method(method);
    p.madhab = if a[2] == "hanafi" { Madhab::Hanafi } else { Madhab::Standard };
    p.rounding = Rounding::None;
    let c = Coordinates::new(a[3].parse().unwrap(), a[4].parse().unwrap());
    let d0 = Date::parse_iso(&a[5]).unwrap();
    let days: i64 = a.get(6).map(|s| s.parse().unwrap()).unwrap_or(1);
    for i in 0..days {
        let t = compute(d0.add_days(i), c, &p);
        println!("{}", serde_json::to_string(&t).unwrap());
    }
}
