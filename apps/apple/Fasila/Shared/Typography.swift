import SwiftUI

/// Inter for Latin, Tajawal for Arabic (docs/DESIGN.md), scaled with Dynamic Type via
/// `relativeTo:`. The TTFs are shared with Android (apps/android/app/src/main/res/font).
extension Font {
    static func fasila(_ style: Font.TextStyle, _ weight: Font.Weight = .regular) -> Font {
        let arabic = (AppLanguage.current.code == "ar")
        return .custom(postScriptName(weight, arabic: arabic), size: baseSize(style), relativeTo: style)
    }

    /// Fixed-size variant (countdowns, canvas labels).
    static func fasila(size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        .custom(postScriptName(weight, arabic: (AppLanguage.current.code == "ar")), fixedSize: size)
    }

    private static func postScriptName(_ w: Font.Weight, arabic: Bool) -> String {
        if arabic {
            switch w {
            case .medium: return "Tajawal-Medium"
            case .semibold, .bold, .heavy, .black: return "Tajawal-Bold"
            default: return "Tajawal-Regular"
            }
        }
        switch w {
        case .ultraLight, .thin, .light: return "Inter-Light"
        case .medium: return "Inter-Medium"
        case .semibold: return "Inter-SemiBold"
        case .bold, .heavy, .black: return "Inter-Bold"
        default: return "Inter-Regular"
        }
    }

    /// Apple's default point sizes for each text style (Large content size).
    private static func baseSize(_ s: Font.TextStyle) -> CGFloat {
        switch s {
        case .largeTitle: return 34
        case .title: return 28
        case .title2: return 22
        case .title3: return 20
        case .headline, .body: return 17
        case .callout: return 16
        case .subheadline: return 15
        case .footnote: return 13
        case .caption: return 12
        case .caption2: return 11
        @unknown default: return 17
        }
    }
}
