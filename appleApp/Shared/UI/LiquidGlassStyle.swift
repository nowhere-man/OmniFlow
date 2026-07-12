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
        #elseif os(macOS)
        #if compiler(>=6.2)
        if #available(macOS 26.0, *) {
            GlassEffectContainer(spacing: spacing) { content }
        } else {
            content
        }
        #else
        content
        #endif
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
            if let tint {
                background(tint, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            } else {
                background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            }
        }
        #elseif os(macOS)
        #if compiler(>=6.2)
        if #available(macOS 26.0, *) {
            if let tint, interactive {
                glassEffect(.regular.tint(tint).interactive(), in: .rect(cornerRadius: cornerRadius))
            } else if let tint {
                glassEffect(.regular.tint(tint), in: .rect(cornerRadius: cornerRadius))
            } else if interactive {
                glassEffect(.regular.interactive(), in: .rect(cornerRadius: cornerRadius))
            } else {
                glassEffect(.regular, in: .rect(cornerRadius: cornerRadius))
            }
        } else if let tint {
            background(tint, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        } else {
            background(.regularMaterial, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        }
        #else
        if let tint {
            background(tint, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        } else {
            background(.regularMaterial, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        }
        #endif
        #else
        self
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
        #elseif os(macOS)
        #if compiler(>=6.2)
        if #available(macOS 26.0, *) {
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
        }
        #else
        background(tint ?? Color.clear, in: Circle())
        #endif
        #else
        self
        #endif
    }

    @ViewBuilder
    func platformPopoverAdaptation() -> some View {
        #if os(iOS)
        if #available(iOS 16.4, *) {
            presentationCompactAdaptation(.popover)
        } else {
            self
        }
        #else
        self
        #endif
    }
}

struct SelectablePillButtonStyle: ButtonStyle {
    @Environment(\.appThemeColor) private var themeColor
    @Environment(\.appThemeSelectionForeground) private var selectedForeground
    let selected: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(selected ? selectedForeground : Color.primary)
            .padding(.horizontal, 12)
            .frame(minHeight: 34)
            .background(
                selected ? themeColor.opacity(configuration.isPressed ? 0.78 : 1) : Color.secondary.opacity(configuration.isPressed ? 0.16 : 0.09),
                in: Capsule()
            )
    }
}

struct ThemeSegmentedControl<Option: Hashable>: View {
    @Environment(\.appThemeColor) private var themeColor
    @Environment(\.appThemeSelectionForeground) private var selectedForeground
    @Binding var selection: Option
    let options: [Option]
    let title: (Option) -> String

    @ViewBuilder
    var body: some View {
        #if os(macOS)
        Picker("", selection: $selection) {
            ForEach(options, id: \.self) { option in Text(title(option)).tag(option) }
        }
        .pickerStyle(.segmented)
        .labelsHidden()
        #else
        HStack(spacing: 4) {
            ForEach(options, id: \.self) { option in
                let selected = selection == option
                Button {
                    withAnimation(.easeInOut(duration: 0.16)) { selection = option }
                } label: {
                    Text(title(option))
                        .font(.subheadline.weight(selected ? .bold : .medium))
                        .foregroundStyle(selected ? selectedForeground : Color.secondary)
                        .frame(maxWidth: .infinity, minHeight: 36)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .background(selected ? themeColor : Color.clear, in: RoundedRectangle(cornerRadius: 13, style: .continuous))
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
        .padding(3)
        .background(Color.secondary.opacity(0.09), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        #endif
    }
}
