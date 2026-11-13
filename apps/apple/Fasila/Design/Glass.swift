import SwiftUI

/// Frosted glass pane over the living sky (docs/DESIGN.md "Glass").
struct GlassBackground: ViewModifier {
    var radius: CGFloat = 24
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @Environment(\.skyPalette) private var palette

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        let fillOpacity = 0.12 + 0.13 * palette.lightness // 12 % dark … 25 % light
        content
            .background {
                if reduceTransparency {
                    shape.fill(palette.top.mix(RGB(r: 0, g: 0, b: 0), 0.35).color)
                } else {
                    shape.fill(.ultraThinMaterial)
                        .overlay(shape.fill(Color.white.opacity(fillOpacity)))
                        .environment(\.colorScheme, .dark)
                }
            }
            .overlay {
                // 1 px border at ~22 %, brighter along the top edge (inner highlight).
                shape.strokeBorder(
                    LinearGradient(colors: [Color.white.opacity(reduceTransparency ? 0.22 : 0.4), Color.white.opacity(0.18)],
                                   startPoint: .top, endPoint: .center),
                    lineWidth: 1)
            }
            .clipShape(shape)
    }
}

extension View {
    func glass(radius: CGFloat = 24) -> some View { modifier(GlassBackground(radius: radius)) }

    /// Text colours on glass: 95 % primary, 70 % secondary.
    func onGlassPrimary() -> some View { foregroundStyle(Color.white.opacity(0.95)) }
    func onGlassSecondary() -> some View { foregroundStyle(Color.white.opacity(0.7)) }
}

private struct SkyPaletteKey: EnvironmentKey {
    static let defaultValue = SkyPeriod.night.palette
}

extension EnvironmentValues {
    var skyPalette: SkyPalette {
        get { self[SkyPaletteKey.self] }
        set { self[SkyPaletteKey.self] = newValue }
    }
}

/// A rounded capsule chip used by the composer and occasion badges.
struct Chip<Label: View>: View {
    var highlighted = false
    @ViewBuilder var label: () -> Label
    @Environment(\.skyPalette) private var palette

    var body: some View {
        label()
            .font(.subheadline.weight(.medium))
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(Capsule().fill(highlighted ? palette.accent.color.opacity(0.35) : Color.white.opacity(0.14)))
            .overlay(Capsule().strokeBorder(Color.white.opacity(0.25), lineWidth: 1))
            .onGlassPrimary()
    }
}
