import SwiftUI

/// The living sky: a gradient that follows the current prayer period.
/// Re-evaluates once a minute (the cross-fade is ~20 min, so that is smooth),
/// and is static under Reduce Motion.
struct LivingSky<Content: View>: View {
    @Environment(FasilaStore.self) private var store
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @ViewBuilder var content: () -> Content

    var body: some View {
        TimelineView(.everyMinute) { ctx in
            let palette = store.snapshot(at: ctx.date, crossFade: !reduceMotion)?.palette ?? SkyPeriod.night.palette
            ZStack {
                palette.gradient
                    .ignoresSafeArea()
                    .animation(reduceMotion ? nil : .easeInOut(duration: 2), value: palette)
                content()
            }
            .environment(\.skyPalette, palette)
        }
    }
}
