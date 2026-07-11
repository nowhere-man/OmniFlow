import SwiftUI

struct LiquidGlassContainer<Content: View>: View {
    let spacing: CGFloat
    @ViewBuilder let content: Content

    init(spacing: CGFloat = 8, @ViewBuilder content: () -> Content) {
        self.spacing = spacing
        self.content = content()
    }

    @ViewBuilder
    var body: some View {
        #if os(iOS)
        if #available(iOS 26.0, *) {
            GlassEffectContainer(spacing: spacing) { content }
        } else {
            content
        }
        #else
        content
        #endif
    }
}

extension View {
    @ViewBuilder
    func liquidGlassSurface(cornerRadius: CGFloat = 16, interactive: Bool = false, tint: Color? = nil) -> some View {
        #if os(iOS)
        if #available(iOS 26.0, *) {
            if let tint, interactive {
                glassEffect(.regular.tint(tint).interactive(), in: .rect(cornerRadius: cornerRadius))
            } else if let tint {
                glassEffect(.regular.tint(tint), in: .rect(cornerRadius: cornerRadius))
            } else if interactive {
                glassEffect(.regular.interactive(), in: .rect(cornerRadius: cornerRadius))
            } else {
                glassEffect(.regular, in: .rect(cornerRadius: cornerRadius))
            }
        } else {
            background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
                .background(tint?.opacity(0.16) ?? .clear, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        }
        #else
        background(.regularMaterial, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        #endif
    }

    @ViewBuilder
    func liquidGlassCircle(interactive: Bool = false, tint: Color? = nil) -> some View {
        #if os(iOS)
        if #available(iOS 26.0, *) {
            if let tint, interactive {
                glassEffect(.regular.tint(tint).interactive(), in: .circle)
            } else if let tint {
                glassEffect(.regular.tint(tint), in: .circle)
            } else if interactive {
                glassEffect(.regular.interactive(), in: .circle)
            } else {
                glassEffect(.regular, in: .circle)
            }
        } else {
            background(tint ?? Color.clear, in: Circle())
                .background(.ultraThinMaterial, in: Circle())
        }
        #else
        background(tint ?? Color.clear, in: Circle())
        #endif
    }
}
