import Foundation

/// All user-visible times are rendered in the *location's* time zone
/// (`config.location.timezone`), not the device's: travelling users see the
/// times of the place they configured.
/// Arabic without a region takes the location's country, so ICU picks the digits people
/// there use: Western in DZ/MA/TN, Arabic-Indic in EG/SA (same rule as desktop and Android).
func formattingLocale(_ base: Locale, country: String) -> Locale {
    let cc = country.uppercased()
    guard base.language.languageCode?.identifier == "ar", base.region == nil,
          cc.count == 2, cc.allSatisfy({ $0.isLetter }) else { return base }
    return Locale(identifier: "ar_\(cc)")
}

struct FasilaFormatter: Sendable {
    var locale: Locale
    var timeZone: TimeZone

    private var gregorian: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = timeZone
        c.locale = locale
        return c
    }

    func time(_ date: Date) -> String {
        date.formatted(Date.FormatStyle(date: .omitted, time: .shortened, locale: locale, calendar: gregorian, timeZone: timeZone))
    }

    func time(unix: Int64?) -> String {
        guard let unix else { return "--:--" }
        return time(Date(timeIntervalSince1970: TimeInterval(unix)))
    }

    func number(_ n: Int) -> String {
        n.formatted(.number.grouping(.never).locale(locale))
    }

    private func twoDigits(_ n: Int) -> String {
        n.formatted(.number.precision(.integerLength(2...)).grouping(.never).locale(locale))
    }

    /// "1:05:09" / "١:٠٥:٠٩" — always tabular-friendly, digits follow the locale.
    func countdown(_ interval: TimeInterval) -> String {
        let total = max(0, Int(interval.rounded(.down)))
        let h = total / 3600, m = (total % 3600) / 60, s = total % 60
        return h > 0 ? "\(number(h)):\(twoDigits(m)):\(twoDigits(s))" : "\(twoDigits(m)):\(twoDigits(s))"
    }

    /// Long Gregorian date, e.g. "Friday 13 November 2026".
    func longDate(_ d: CivilDate) -> String {
        d.noon(in: timeZone).formatted(
            Date.FormatStyle(date: .complete, time: .omitted, locale: locale, calendar: gregorian, timeZone: timeZone))
    }

    func monthYear(year: Int, month: Int) -> String {
        CivilDate(year: year, month: month, day: 1).noon(in: timeZone).formatted(
            Date.FormatStyle(locale: locale, calendar: gregorian, timeZone: timeZone).month(.wide).year())
    }

    /// Sunday-first, matching the core's weekday index (0 = Sunday).
    func veryShortWeekdaySymbols() -> [String] { gregorian.veryShortStandaloneWeekdaySymbols }
    func shortWeekdaySymbols() -> [String] { gregorian.shortStandaloneWeekdaySymbols }
    var firstWeekday: Int { gregorian.firstWeekday } // 1 = Sunday

    func shortDate(_ d: CivilDate) -> String {
        d.noon(in: timeZone).formatted(
            Date.FormatStyle(date: .abbreviated, time: .omitted, locale: locale, calendar: gregorian, timeZone: timeZone))
    }

    /// "2 Jumada al-Ula 1448" (Arabic month names in `ar`, transliterated otherwise).
    func hijri(_ day: DayInfo, lang: String) -> String {
        let name = hijriMonthName(month: day.hijriMonth, lang: lang)
        let d = number(Int(day.hijriDay)), y = number(Int(day.hijriYear))
        return lang == "ar" ? "\(d) \(name) \(y) هـ" : "\(d) \(name) \(y) AH"
    }

    /// "15 min" / "1 h 30 min" with locale digits.
    func minutes(_ m: Int) -> String {
        Duration.seconds(m * 60).formatted(.units(allowed: [.hours, .minutes], width: .abbreviated).locale(locale))
    }
}
