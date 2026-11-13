//! Natural-language anchors: "wake up 15 min before fajr", "اجتماع بعد العصر بنصف ساعة لمدة ساعة",
//! "réunion 30 min après asr jusqu'à 10 min avant maghrib", "asr+30", "every friday 45 min before jumuah".
//!
//! The parser is deliberately rule-based and deterministic: the same sentence yields the
//! same schedule on every platform, and it never guesses a prayer that wasn't mentioned.

use crate::date::{Date, Weekday};
use crate::prayer::TimeKey;
use crate::schedule::{Anchor, End, ItemKind, Recurrence};

#[cfg(feature = "serde")]
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq)]
#[cfg_attr(feature = "serde", derive(Serialize, Deserialize))]
pub struct Parsed {
    pub title: String,
    pub start: Anchor,
    pub end: End,
    /// `None` when the sentence did not say; the app picks its default.
    pub recurrence: Option<Recurrence>,
    pub kind: Option<ItemKind>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
#[cfg_attr(
    feature = "serde",
    derive(Serialize, Deserialize),
    serde(rename_all = "snake_case")
)]
pub enum ParseError {
    /// No prayer was mentioned — Fasila schedules relative to prayers only.
    NoPrayer,
    Empty,
}

impl core::fmt::Display for ParseError {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        match self {
            ParseError::NoPrayer => write!(f, "no prayer mentioned (e.g. \"15 min before fajr\")"),
            ParseError::Empty => write!(f, "empty input"),
        }
    }
}

/// Normalize: lowercase, ASCII digits, strip Arabic diacritics/tatweel, unify letter variants.
pub fn normalize(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for c in s.chars() {
        let c = match c {
            '٠'..='٩' => char::from(b'0' + (c as u32 - '٠' as u32) as u8),
            '۰'..='۹' => char::from(b'0' + (c as u32 - '۰' as u32) as u8),
            'أ' | 'إ' | 'آ' | 'ٱ' => 'ا',
            'ة' => 'ه',
            'ى' => 'ي',
            'ـ' => continue,
            '\u{064B}'..='\u{0652}' | '\u{0670}' => continue,
            '’' | '‘' | '`' => '\'',
            'é' | 'è' | 'ê' => 'e',
            'à' | 'â' => 'a',
            'ô' => 'o',
            'û' | 'ù' => 'u',
            'î' | 'ï' => 'i',
            'ç' => 'c',
            '،' => ',',
            _ => c,
        };
        for l in c.to_lowercase() {
            out.push(l);
        }
    }
    out
}

fn is_word_char(c: char) -> bool {
    c.is_alphanumeric() || c == '\''
}

/// Find `needle` in `hay` (both normalized). Latin needles must sit on word boundaries;
/// Arabic needles may carry clitic prefixes (ال، ب، ل، و، ف) so we only check the end.
fn find_word(hay: &str, needle: &str, from: usize) -> Option<usize> {
    let latin = needle.chars().next().is_some_and(|c| c.is_ascii());
    let mut start = from;
    while let Some(pos) = hay.get(start..)?.find(needle) {
        let abs = start + pos;
        let before_ok = !latin
            || hay[..abs]
                .chars()
                .next_back()
                .is_none_or(|c| !is_word_char(c));
        let after = &hay[abs + needle.len()..];
        let after_ok = after
            .chars()
            .next()
            .is_none_or(|c| !is_word_char(c) || (!latin && !c.is_alphabetic()))
            || (!latin && !after.starts_with(|c: char| c.is_alphabetic()))
            || (latin && after.starts_with(|c: char| c.is_ascii_digit()));
        if before_ok && after_ok {
            return Some(abs);
        }
        start = abs + needle.len();
    }
    None
}

const PRAYER_WORDS: &[(&str, TimeKey)] = &[
    // Jumu'ah is Dhuhr on Friday (handled specially below).
    ("صلاه الجمعه", TimeKey::Dhuhr),
    ("jumu'ah", TimeKey::Dhuhr),
    ("jumuah", TimeKey::Dhuhr),
    ("jumua", TimeKey::Dhuhr),
    ("jummah", TimeKey::Dhuhr),
    ("jumah", TimeKey::Dhuhr),
    ("joumouaa", TimeKey::Dhuhr),
    ("friday prayer", TimeKey::Dhuhr),
    ("last third", TimeKey::LastThird),
    ("dernier tiers", TimeKey::LastThird),
    ("ثلث الليل", TimeKey::LastThird),
    ("الثلث الاخير", TimeKey::LastThird),
    ("tahajjud", TimeKey::LastThird),
    ("qiyam", TimeKey::LastThird),
    ("تهجد", TimeKey::LastThird),
    ("قيام", TimeKey::LastThird),
    ("منتصف الليل", TimeKey::Midnight),
    ("midnight", TimeKey::Midnight),
    ("minuit", TimeKey::Midnight),
    ("imsak", TimeKey::Imsak),
    ("امساك", TimeKey::Imsak),
    ("suhoor", TimeKey::Imsak),
    ("sahur", TimeKey::Imsak),
    ("سحور", TimeKey::Imsak),
    ("lever du soleil", TimeKey::Sunrise),
    ("coucher du soleil", TimeKey::Sunset),
    ("sunrise", TimeKey::Sunrise),
    ("shuruq", TimeKey::Sunrise),
    ("shurooq", TimeKey::Sunrise),
    ("chourouk", TimeKey::Sunrise),
    ("chorouk", TimeKey::Sunrise),
    ("شروق", TimeKey::Sunrise),
    ("sunset", TimeKey::Sunset),
    ("غروب", TimeKey::Sunset),
    ("fadjr", TimeKey::Fajr),
    ("fajr", TimeKey::Fajr),
    ("fajer", TimeKey::Fajr),
    ("fadjer", TimeKey::Fajr),
    ("sobh", TimeKey::Fajr),
    ("subh", TimeKey::Fajr),
    ("فجر", TimeKey::Fajr),
    ("صبح", TimeKey::Fajr),
    ("dhuhr", TimeKey::Dhuhr),
    ("dhuhur", TimeKey::Dhuhr),
    ("duhr", TimeKey::Dhuhr),
    ("zuhr", TimeKey::Dhuhr),
    ("zohr", TimeKey::Dhuhr),
    ("dhohr", TimeKey::Dhuhr),
    ("dohr", TimeKey::Dhuhr),
    ("thuhr", TimeKey::Dhuhr),
    ("ظهر", TimeKey::Dhuhr),
    ("asr", TimeKey::Asr),
    ("aser", TimeKey::Asr),
    ("عصر", TimeKey::Asr),
    ("maghrib", TimeKey::Maghrib),
    ("maghreb", TimeKey::Maghrib),
    ("magrib", TimeKey::Maghrib),
    ("مغرب", TimeKey::Maghrib),
    ("isha'a", TimeKey::Isha),
    ("ishaa", TimeKey::Isha),
    ("isha", TimeKey::Isha),
    ("icha", TimeKey::Isha),
    ("esha", TimeKey::Isha),
    ("عشاء", TimeKey::Isha),
    ("عشا", TimeKey::Isha),
];

const JUMUAH_WORDS: &[&str] = &[
    "صلاه الجمعه",
    "jumu'ah",
    "jumuah",
    "jumua",
    "jummah",
    "jumah",
    "joumouaa",
    "friday prayer",
];

struct Mention {
    pos: usize,
    len: usize,
    key: TimeKey,
    jumuah: bool,
}

fn prayer_mentions(t: &str) -> Vec<Mention> {
    let mut found: Vec<Mention> = Vec::new();
    for &(w, key) in PRAYER_WORDS {
        let mut from = 0;
        while let Some(pos) = find_word(t, w, from) {
            let overlaps = found
                .iter()
                .any(|m| pos < m.pos + m.len && m.pos < pos + w.len());
            if !overlaps {
                found.push(Mention {
                    pos,
                    len: w.len(),
                    key,
                    jumuah: JUMUAH_WORDS.contains(&w),
                });
            }
            from = pos + w.len();
        }
    }
    found.sort_by_key(|m| m.pos);
    found
}

const BEFORE: &[&str] = &["before", "prior to", "ahead of", "avant", "قبل"];
const AFTER: &[&str] = &["after", "past", "apres", "بعد"];
const UNTIL: &[&str] = &[
    "until",
    "till",
    "jusqu'a",
    "jusqu'au",
    "jusqua",
    "حتي",
    "الي",
    "لغايه",
    "to",
    "->",
    "-",
];
const DURATION: &[&str] = &["for", "lasting", "pendant", "durant", "لمده", "مده"];

fn word_numbers(lang_word: &str) -> Option<u32> {
    Some(match lang_word {
        "one" | "an" | "a" | "un" | "une" | "واحد" | "واحده" => 1,
        "two" | "deux" | "اثنين" => 2,
        "three" | "trois" | "ثلاث" | "ثلاثه" => 3,
        "four" | "quatre" | "اربع" | "اربعه" => 4,
        "five" | "cinq" | "خمس" | "خمسه" => 5,
        "ten" | "dix" | "عشر" | "عشره" => 10,
        "fifteen" | "quinze" => 15,
        "twenty" | "vingt" | "عشرين" => 20,
        "thirty" | "trente" | "ثلاثين" => 30,
        "forty" | "quarante" | "اربعين" => 40,
        "forty-five" | "forty five" | "خمس واربعين" => 45,
        _ => return None,
    })
}

/// Extract a total number of minutes from a fragment. Returns `None` if nothing found.
fn amount_minutes(s: &str) -> Option<u32> {
    let s = s.trim();
    // Fixed expressions first (longest first).
    const FIXED: &[(&str, u32)] = &[
        ("ساعه ونصف", 90),
        ("ساعه وربع", 75),
        ("ساعه الا ربع", 45),
        ("ساعتين", 120),
        ("ساعتان", 120),
        ("نصف ساعه", 30),
        ("نص ساعه", 30),
        ("ربع ساعه", 15),
        ("دقيقتين", 2),
        ("دقيقتان", 2),
        ("half an hour", 30),
        ("half hour", 30),
        ("quarter of an hour", 15),
        ("quarter hour", 15),
        ("an hour and a half", 90),
        ("hour and a half", 90),
        ("demi-heure", 30),
        ("demi heure", 30),
        ("quart d'heure", 15),
        ("une heure et demie", 90),
        ("heure et demie", 90),
    ];
    for &(w, m) in FIXED {
        if s.contains(w) {
            return Some(m);
        }
    }
    let mut total: Option<u32> = None;
    let mut add = |m: u32| total = Some(total.unwrap_or(0) + m);
    let chars: Vec<char> = s.chars().collect();
    let mut i = 0;
    let mut saw_number = false;
    while i < chars.len() {
        if chars[i].is_ascii_digit() {
            let st = i;
            while i < chars.len() && (chars[i].is_ascii_digit() || chars[i] == '.') {
                i += 1;
            }
            let n: f64 = chars[st..i]
                .iter()
                .collect::<String>()
                .parse()
                .unwrap_or(0.0);
            saw_number = true;
            // Unit right after (skipping spaces / Arabic "ب" / "-").
            let rest: String = chars[i..].iter().collect::<String>();
            let r = rest.trim_start();
            let unit_hours = [
                "hours",
                "hour",
                "hrs",
                "hr",
                "heures",
                "heure",
                "ساعات",
                "ساعه",
                "h",
            ];
            let unit_min = [
                "minutes",
                "minute",
                "mins",
                "min",
                "mn",
                "m",
                "دقائق",
                "دقيقه",
                "د",
            ];
            if let Some(u) = unit_hours
                .iter()
                .find(|u| r.starts_with(**u) && !r[u.len()..].starts_with(char::is_alphabetic))
            {
                add((n * 60.0).round() as u32);
                i += rest.len() - r.len() + u.chars().count();
                // "1h30"
                let tail: String = chars[i..]
                    .iter()
                    .take_while(|c| c.is_ascii_digit())
                    .collect();
                if !tail.is_empty() {
                    add(tail.parse().unwrap_or(0));
                    i += tail.len();
                }
            } else if let Some(u) = unit_min
                .iter()
                .find(|u| r.starts_with(**u) && !r[u.len()..].starts_with(char::is_alphabetic))
            {
                add(n.round() as u32);
                i += rest.len() - r.len() + u.chars().count();
            } else {
                add(n.round() as u32); // bare number: minutes
            }
            continue;
        }
        i += 1;
    }
    if total.is_some() {
        return total;
    }
    // Number words + unit ("ten minutes", "an hour", "خمس دقائق", "ساعه").
    let words: Vec<&str> = s
        .split(|c: char| c.is_whitespace() || c == ',')
        .filter(|w| !w.is_empty())
        .collect();
    for (idx, w) in words.iter().enumerate() {
        let w = w.trim_start_matches('ب');
        let next = words.get(idx + 1).copied().unwrap_or("");
        if let Some(n) = word_numbers(w) {
            if next.starts_with("hour") || next.starts_with("heure") || next.starts_with("ساع") {
                return Some(n * 60);
            }
            if next.starts_with("min") || next.starts_with("دقي") || next.starts_with("دقا") {
                return Some(n);
            }
        }
        if !saw_number && (w == "ساعه" || w == "hour" || w == "heure") {
            return Some(60);
        }
    }
    None
}

fn find_any(t: &str, words: &[&str], from: usize, to: usize) -> Option<(usize, usize)> {
    let seg = t.get(from..to)?;
    words
        .iter()
        .filter_map(|w| find_word(seg, w, 0).map(|p| (from + p, w.len())))
        .min_by_key(|x| x.0)
}

/// Parse the anchor within `t[lo..hi]` around the prayer mention `m`.
fn anchor_in(t: &str, lo: usize, hi: usize, m: &Mention) -> (Anchor, usize, usize) {
    let seg_lo = lo;
    let seg_hi = hi;
    let before = find_any(t, BEFORE, seg_lo, seg_hi);
    let after = find_any(t, AFTER, seg_lo, seg_hi);
    let mut span_lo = m.pos;
    let mut span_hi = m.pos + m.len;
    let (sign, dir_pos) = match (before, after) {
        (Some(b), Some(a)) => {
            // Choose the one closest to the prayer mention.
            if b.0.abs_diff(m.pos) <= a.0.abs_diff(m.pos) {
                (-1, Some(b))
            } else {
                (1, Some(a))
            }
        }
        (Some(b), None) => (-1, Some(b)),
        (None, Some(a)) => (1, Some(a)),
        (None, None) => (0, None),
    };
    if let Some((p, l)) = dir_pos {
        span_lo = span_lo.min(p);
        span_hi = span_hi.max(p + l);
    }
    // Symbolic "asr+30" / "fajr - 15".
    let tail = &t[m.pos + m.len..seg_hi];
    let tail_trim = tail.trim_start();
    if sign == 0 && (tail_trim.starts_with('+') || tail_trim.starts_with('-')) {
        let s = if tail_trim.starts_with('-') { -1 } else { 1 };
        // Digits, then an optional unit glued or after one space ("+30", "- 1h30", "+ 45 min").
        let body = &tail_trim[1..];
        let lead = body.len() - body.trim_start().len();
        let body = body.trim_start();
        let digits = body
            .find(|c: char| !(c.is_ascii_digit() || c == '.' || c == 'h'))
            .unwrap_or(body.len());
        let mut num_len = digits;
        let rest = &body[digits..];
        for u in [
            "minutes",
            "minute",
            "mins",
            "min",
            "mn",
            "دقائق",
            "دقيقه",
            "hours",
            "hour",
            "heures",
            "heure",
            "ساعات",
            "ساعه",
        ] {
            let r = rest.trim_start();
            if r.starts_with(u) {
                num_len = digits + (rest.len() - r.len()) + u.len();
                break;
            }
        }
        let num_part = &body[..num_len];
        if let Some(mins) = amount_minutes(num_part).filter(|_| digits > 0) {
            let consumed = (tail.len() - tail_trim.len()) + 1 + lead + num_len;
            return (
                Anchor::new(m.key, s * mins as i32),
                m.pos,
                (m.pos + m.len + consumed).min(seg_hi),
            );
        }
    }
    if sign == 0 {
        return (Anchor::at(m.key), span_lo, span_hi);
    }
    // Amount: look at the text around the direction word and prayer, within the segment.
    let mins = amount_minutes(&t[seg_lo..seg_hi]).unwrap_or(0);
    if mins > 0 {
        // Extend the span to cover the amount (numbers/unit words near the anchor).
        let region = &t[seg_lo..seg_hi];
        if let Some(first_digit) = region.find(|c: char| c.is_ascii_digit()) {
            span_lo = span_lo.min(seg_lo + first_digit);
            let after_digits = region[first_digit..]
                .find(|c: char| !(c.is_alphanumeric() || c == ' ' || c == '.'))
                .map(|x| seg_lo + first_digit + x)
                .unwrap_or(seg_hi);
            span_hi = span_hi.max(after_digits.min(seg_hi));
        }
    }
    (Anchor::new(m.key, sign * mins as i32), span_lo, span_hi)
}

fn weekday_words() -> [(&'static [&'static str], Weekday); 7] {
    [
        (&["sunday", "sundays", "dimanche", "الاحد"], Weekday::Sunday),
        (
            &["monday", "mondays", "lundi", "الاثنين", "الاتنين"],
            Weekday::Monday,
        ),
        (
            &["tuesday", "tuesdays", "mardi", "الثلاثاء"],
            Weekday::Tuesday,
        ),
        (
            &["wednesday", "wednesdays", "mercredi", "الاربعاء"],
            Weekday::Wednesday,
        ),
        (
            &["thursday", "thursdays", "jeudi", "الخميس"],
            Weekday::Thursday,
        ),
        (
            &["friday", "fridays", "vendredi", "الجمعه"],
            Weekday::Friday,
        ),
        (
            &["saturday", "saturdays", "samedi", "السبت"],
            Weekday::Saturday,
        ),
    ]
}

/// Parse a sentence. `today` resolves "today"/"tomorrow".
pub fn parse(input: &str, today: Date) -> Result<Parsed, ParseError> {
    let t = normalize(input);
    if t.trim().is_empty() {
        return Err(ParseError::Empty);
    }
    let mentions = prayer_mentions(&t);
    let first = mentions.first().ok_or(ParseError::NoPrayer)?;

    // Remove the duration clause ("for 1 hour", "لمدة ساعة") before reading anchors.
    let mut dur_minutes = None;
    let mut dur_span = None;
    if let Some((p, l)) = find_any(&t, DURATION, 0, t.len()) {
        let rest = &t[p + l..];
        // Duration text runs until the next prayer mention or connector.
        let stop = mentions
            .iter()
            .map(|m| m.pos)
            .filter(|&mp| mp > p)
            .min()
            .unwrap_or(t.len());
        let stop = [BEFORE, AFTER, UNTIL]
            .iter()
            .filter_map(|ws| find_any(&t, ws, p + l, stop).map(|x| x.0))
            .min()
            .unwrap_or(stop);
        if let Some(m) = amount_minutes(&rest[..stop - (p + l)]) {
            dur_minutes = Some(m);
            dur_span = Some((p, stop));
        }
    }
    let masked: String = match dur_span {
        Some((a, b)) => t
            .char_indices()
            .map(|(i, c)| {
                if i >= a && i < b && !c.is_whitespace() {
                    '·'
                } else {
                    c
                }
            })
            .collect(),
        None => t.clone(),
    };
    // '·' is 2 bytes in UTF-8; keep byte offsets aligned by masking char-for-char with same width.
    let masked = if masked.len() == t.len() {
        masked
    } else {
        match dur_span {
            Some((a, b)) => {
                let mut s = t.clone();
                let filler: String = t[a..b]
                    .chars()
                    .map(|c| {
                        if c.is_whitespace() { c } else { ' ' }
                            .to_string()
                            .repeat(c.len_utf8())
                    })
                    .collect();
                s.replace_range(a..b, &filler);
                s
            }
            None => t.clone(),
        }
    };

    // Split into start / end segments at an "until" connector followed by a prayer.
    let second = mentions.get(1);
    let mut split = None;
    if let Some(sec) = second {
        if let Some((p, l)) = find_any(&masked, UNTIL, first.pos + first.len, sec.pos) {
            split = Some((p, l));
        } else if let Some((p, l)) = find_any(&masked, &["from", "de", "من"], 0, first.pos) {
            let _ = (p, l);
        }
    }
    let (start_hi, end_seg) = match split {
        Some((p, l)) => (p, Some((p + l, masked.len()))),
        None => (masked.len(), None),
    };
    let (start, s_lo, s_hi) = anchor_in(&masked, 0, start_hi, first);
    let mut spans = vec![(s_lo, s_hi)];
    let end = match (end_seg, second) {
        (Some((lo, hi)), Some(sec)) => {
            let (a, e_lo, e_hi) = anchor_in(&masked, lo, hi, sec);
            spans.push((e_lo, e_hi));
            End::Until { anchor: a }
        }
        _ => match dur_minutes {
            Some(m) => End::Duration { minutes: m },
            None => End::None,
        },
    };
    if let Some(span) = dur_span {
        spans.push(span);
    }
    for m in &mentions {
        spans.push((m.pos, m.pos + m.len));
    }

    // Recurrence.
    let mut recurrence = None;
    let mut mask = 0u8;
    for (words, wd) in weekday_words() {
        if words.iter().any(|w| find_word(&t, w, 0).is_some()) {
            // "صلاة الجمعة" already implies Friday; the bare word also means Friday.
            mask |= wd.bit();
        }
    }
    if mentions.iter().any(|m| m.jumuah) {
        mask |= Weekday::Friday.bit();
    }
    let daily = [
        "every day",
        "everyday",
        "daily",
        "each day",
        "chaque jour",
        "tous les jours",
        "quotidien",
        "يوميا",
        "كل يوم",
    ];
    let tomorrow = ["tomorrow", "demain", "غدا", "بكره", "غدوه"];
    let todayw = ["today", "aujourd'hui", "اليوم"];
    if daily.iter().any(|w| find_word(&t, w, 0).is_some()) {
        recurrence = Some(Recurrence::Daily);
    } else if mask != 0 {
        recurrence = Some(Recurrence::Weekly { days: mask });
    } else if tomorrow.iter().any(|w| find_word(&t, w, 0).is_some()) {
        recurrence = Some(Recurrence::Once {
            date: today.add_days(1),
        });
    } else if todayw.iter().any(|w| find_word(&t, w, 0).is_some()) {
        recurrence = Some(Recurrence::Once { date: today });
    } else if ["ramadan", "رمضان"]
        .iter()
        .any(|w| find_word(&t, w, 0).is_some())
    {
        recurrence = Some(Recurrence::HijriMonth { month: 9 });
    } else if ["ayyam al-bid", "white days", "الايام البيض", "ايام البيض"]
        .iter()
        .any(|w| t.contains(w))
    {
        recurrence = Some(Recurrence::HijriMonthly {
            days: vec![13, 14, 15],
        });
    }

    // Kind.
    let alarm = [
        "alarm",
        "wake",
        "remind",
        "reminder",
        "reveil",
        "rappel",
        "منبه",
        "نبهني",
        "ذكرني",
        "تذكير",
        "ايقظني",
        "صحيني",
    ];
    let event = [
        "meeting",
        "call",
        "class",
        "lecture",
        "appointment",
        "event",
        "reunion",
        "rendez-vous",
        "cours",
        "اجتماع",
        "موعد",
        "درس",
        "محاضره",
        "مكالمه",
    ];
    let task = [
        "task",
        "todo",
        "tache",
        "مهمه",
        "read",
        "study",
        "اقرا",
        "مراجعه",
    ];
    let kind = if alarm.iter().any(|w| find_word(&t, w, 0).is_some()) {
        Some(ItemKind::Alarm)
    } else if event.iter().any(|w| find_word(&t, w, 0).is_some()) {
        Some(ItemKind::Event)
    } else if task.iter().any(|w| find_word(&t, w, 0).is_some()) {
        Some(ItemKind::Task)
    } else if end != End::None {
        Some(ItemKind::Event)
    } else {
        None
    };

    // Title: what remains outside the anchor span, minus filler words.
    let title = title_from(&t, &spans);
    Ok(Parsed {
        title,
        start,
        end,
        recurrence,
        kind,
    })
}

fn title_from(t: &str, spans: &[(usize, usize)]) -> String {
    // Blank every recognised span; the title is the first remaining run of words.
    let blanked: String = t
        .char_indices()
        .map(|(i, c)| {
            if spans.iter().any(|&(a, b)| i >= a && i < b) {
                '\u{1}'
            } else {
                c
            }
        })
        .collect();
    let runs: Vec<&str> = blanked.split('\u{1}').collect();
    const FILLER: &[&str] = &[
        "at",
        "on",
        "the",
        "a",
        "an",
        "every",
        "each",
        "day",
        "daily",
        "everyday",
        "today",
        "tomorrow",
        "and",
        "from",
        "prayer",
        "de",
        "du",
        "la",
        "le",
        "a",
        "chaque",
        "jour",
        "tous",
        "les",
        "jours",
        "demain",
        "aujourd'hui",
        "priere",
        "pour",
        "عند",
        "في",
        "كل",
        "يوم",
        "يوميا",
        "غدا",
        "اليوم",
        "صلاه",
        "من",
        "و",
        "ال",
        "بـ",
        "ب",
        "لي",
        "before",
        "after",
        "avant",
        "apres",
        "قبل",
        "بعد",
        "min",
        "mins",
        "minutes",
        "minute",
        "دقيقه",
        "دقائق",
        "until",
        "till",
        "حتي",
        "الي",
        "jusqu'a",
        "jusqua",
        "for",
        "لمده",
        "pendant",
        "·",
    ];
    let wd: Vec<&str> = weekday_words()
        .iter()
        .flat_map(|(w, _)| w.iter().copied())
        .collect();
    let clean = |s: &str| -> String {
        s.split(|c: char| c.is_whitespace() || c == ',' || c == '+' || c == '·')
            .filter(|w| !w.is_empty())
            .filter(|w| {
                !FILLER.contains(w) && !wd.contains(w) && !w.chars().all(|c| c.is_ascii_digit())
            })
            .collect::<Vec<_>>()
            .join(" ")
    };
    let title = runs
        .iter()
        .map(|r| clean(r))
        .find(|r| !r.is_empty())
        .unwrap_or_default();
    // Capitalize first Latin letter for display.
    let mut cs = title.chars();
    match cs.next() {
        Some(f) if f.is_ascii_lowercase() => f.to_ascii_uppercase().to_string() + cs.as_str(),
        Some(f) => f.to_string() + cs.as_str(),
        None => String::new(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const D: Date = Date::new(2026, 11, 13);

    fn p(s: &str) -> Parsed {
        parse(s, D).unwrap_or_else(|e| panic!("{s}: {e}"))
    }

    #[test]
    fn english() {
        let r = p("Alarm 15 minutes before fajr");
        assert_eq!(r.start, Anchor::new(TimeKey::Fajr, -15));
        assert_eq!(r.kind, Some(ItemKind::Alarm));
        assert_eq!(r.title, "Alarm");

        let r = p("Meeting 30 min after asr for 1 hour");
        assert_eq!(r.start, Anchor::new(TimeKey::Asr, 30));
        assert_eq!(r.end, End::Duration { minutes: 60 });
        assert_eq!(r.kind, Some(ItemKind::Event));
        assert_eq!(r.title, "Meeting");

        let r = p("study 1h30 after isha every day");
        assert_eq!(r.start, Anchor::new(TimeKey::Isha, 90));
        assert_eq!(r.recurrence, Some(Recurrence::Daily));
        assert_eq!(r.title, "Study");

        let r = p("asr+30 gym until 10 min before maghrib");
        assert_eq!(r.start, Anchor::new(TimeKey::Asr, 30));
        assert_eq!(
            r.end,
            End::Until {
                anchor: Anchor::new(TimeKey::Maghrib, -10)
            }
        );
        assert_eq!(r.title, "Gym");

        let r = p("leave home 45 min before jumuah");
        assert_eq!(r.start, Anchor::new(TimeKey::Dhuhr, -45));
        assert_eq!(
            r.recurrence,
            Some(Recurrence::Weekly {
                days: Weekday::Friday.bit()
            })
        );

        let r = p("wake me half an hour before fadjr tomorrow");
        assert_eq!(r.start, Anchor::new(TimeKey::Fajr, -30));
        assert_eq!(
            r.recurrence,
            Some(Recurrence::Once {
                date: D.add_days(1)
            })
        );

        let r = p("tahajjud");
        assert_eq!(r.start, Anchor::at(TimeKey::LastThird));

        assert_eq!(parse("meeting at 3pm", D), Err(ParseError::NoPrayer));
    }

    #[test]
    fn arabic() {
        let r = p("اجتماع بعد العصر بنصف ساعة لمدة ساعة");
        assert_eq!(r.start, Anchor::new(TimeKey::Asr, 30));
        assert_eq!(r.end, End::Duration { minutes: 60 });
        assert_eq!(r.kind, Some(ItemKind::Event));
        assert_eq!(r.title, "اجتماع");

        let r = p("نبهني قبل الفجر بـ١٥ دقيقة");
        assert_eq!(r.start, Anchor::new(TimeKey::Fajr, -15));
        assert_eq!(r.kind, Some(ItemKind::Alarm));

        let r = p("درس بعد المغرب حتى العشاء كل يوم");
        assert_eq!(r.start, Anchor::at(TimeKey::Maghrib));
        assert_eq!(
            r.end,
            End::Until {
                anchor: Anchor::at(TimeKey::Isha)
            }
        );
        assert_eq!(r.recurrence, Some(Recurrence::Daily));
        assert_eq!(r.title, "درس");

        let r = p("قراءة القرآن قبل الظهر بربع ساعة يوم الجمعة");
        assert_eq!(r.start, Anchor::new(TimeKey::Dhuhr, -15));
        assert_eq!(
            r.recurrence,
            Some(Recurrence::Weekly {
                days: Weekday::Friday.bit()
            })
        );

        let r = p("السحور قبل الإمساك بساعة");
        assert_eq!(r.start, Anchor::new(TimeKey::Imsak, -60));
    }

    #[test]
    fn french() {
        let r = p("Réunion 30 min après asr jusqu'à 10 min avant maghrib");
        assert_eq!(r.start, Anchor::new(TimeKey::Asr, 30));
        assert_eq!(
            r.end,
            End::Until {
                anchor: Anchor::new(TimeKey::Maghrib, -10)
            }
        );
        assert_eq!(r.title, "Reunion");

        let r = p("réveil un quart d'heure avant sobh chaque jour");
        assert_eq!(r.start, Anchor::new(TimeKey::Fajr, -15));
        assert_eq!(r.kind, Some(ItemKind::Alarm));
        assert_eq!(r.recurrence, Some(Recurrence::Daily));
    }
}
