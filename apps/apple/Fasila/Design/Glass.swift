import SwiftUI

/*
 Glass system (docs/DESIGN.md). Three levels and nothing else; state is shown by how lit
 a pane is, never by outlines, side stripes, underlines or indicator dots.
   panel  .glass()       the frosted sheet a region sits on
   pane   .glassPane()   a quieter surface for secondary state (current prayer, today)
   lit    .glassLit()    brighter frost, strong rim light, warm glow (next / selected / active)
 Rim light (a top-bright gradient stroke) is part of glass; it is not an edge marker.
*/


private func rim(_ alpha: Double) -> LinearGradient {
    LinearGradient(stops: [
        .init(color: .white.opacity(alpha), location: 0),
        .init(color: .white.opacity(alpha * 0.25), location: 0.35),
        .init(color: .clear, location: 1),
    ], startPoint: .top, endPoint: .bottom)
}

/// Panel: frosted material over a dark tint whose strength follows the sky's brightness.
struct GlassBackground: ViewModifier {
    var radius: CGFloat = 24
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @Environment(\.skyLook) private var sky

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        content
            .background {
                if reduceTransparency {
                    shape.fill(Color(red: 14 / 255, green: 20 / 255, blue: 38 / 255))
                } else {
                    shape.fill(.ultraThinMaterial)
                        .overlay(shape.fill(fasilaNight.opacity(sky.glassTint)))
                        .overlay(shape.fill(Color.white.opacity(0.05)))
                        .environment(\.colorScheme, .dark)
                }
            }
            .overlay { if !reduceTransparency { shape.strokeBorder(rim(0.16), lineWidth: 1) } }
            .clipShape(shape)
    }
}

/// Pane: a quieter frosted surface for secondary state.
struct GlassPane: ViewModifier {
    var radius: CGFloat = 16
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        content
            .background(shape.fill(reduceTransparency ? Color(red: 28 / 255, green: 36 / 255, blue: 64 / 255) : Color.white.opacity(0.08)))
            .overlay { if !reduceTransparency { shape.strokeBorder(rim(0.1), lineWidth: 1) } }
    }
}

/// Lit pane: the whole surface lights up.
struct GlassLit: ViewModifier {
    var radius: CGFloat = 16
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        content
            .background {
                if reduceTransparency {
                    shape.fill(Color(red: 42 / 255, green: 51 / 255, blue: 80 / 255))
                } else {
                    shape.fill(LinearGradient(colors: [.white.opacity(0.22), .white.opacity(0.1)], startPoint: .top, endPoint: .bottom))
                        .overlay(shape.fill(RadialGradient(colors: [fasilaAccent.opacity(0.28), .clear],
                                                           center: .top, startRadius: 0, endRadius: 220)))
                }
            }
            .overlay { if !reduceTransparency { shape.strokeBorder(rim(0.42), lineWidth: 1) } }
    }
}

extension View {
    func glass(radius: CGFloat = 24) -> some View { modifier(GlassBackground(radius: radius)) }
    func glassPane(radius: CGFloat = 16) -> some View { modifier(GlassPane(radius: radius)) }
    func glassLit(radius: CGFloat = 16) -> some View { modifier(GlassLit(radius: radius)) }

    /// Small text that sits directly on the sky: a frosted pill tinted for the sky.
    func skyPill(_ sky: SkyLook) -> some View {
        padding(.horizontal, 14).padding(.vertical, 4)
            .background(Capsule().fill(fasilaNight.opacity(0.18 + sky.glassTint * 0.5)))
    }

    /// Text colours on glass (measured >= 4.5:1 with the adaptive tint).
    func onGlassPrimary() -> some View { foregroundStyle(fasilaInk) }
    func onGlassSecondary() -> some View { foregroundStyle(Color(red: 226 / 255, green: 232 / 255, blue: 240 / 255).opacity(0.84)) }
}

private struct SkyLookKey: EnvironmentKey {
    static let defaultValue = SkyLook.night
}

extension EnvironmentValues {
    var skyLook: SkyLook {
        get { self[SkyLookKey.self] }
        set { self[SkyLookKey.self] = newValue }
    }
}

/// A capsule chip used by the composer; `highlighted` lights it (the prayer anchor).
struct Chip<Label: View>: View {
    var highlighted = false
    @ViewBuilder var label: () -> Label

    var body: some View {
        let chip = label().padding(.horizontal, 12).padding(.vertical, 6)
        Group {
            if highlighted { chip.modifier(GlassLit(radius: 999)) } else { chip.modifier(GlassPane(radius: 999)) }
        }
        .font(.fasila(.subheadline, .medium))
        .onGlassPrimary()
    }
}
