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
    func iOSLiquidGlassIconControl(size: CGFloat = 38, tint: Color? = nil) -> some View {
        #if os(iOS)
        frame(width: max(size, 44), height: max(size, 44))
            .contentShape(Circle())
            .liquidGlassCircle(interactive: true, tint: tint)
        #else
        self
        #endif
    }

    @ViewBuilder
    func iOSLiquidGlassControl(cornerRadius: CGFloat = 14, tint: Color? = nil) -> some View {
        #if os(iOS)
        liquidGlassSurface(cornerRadius: cornerRadius, interactive: true, tint: tint)
        #else
        self
        #endif
    }

    @ViewBuilder
    func iOSPlainButtonStyle() -> some View {
        #if os(iOS)
        if #available(iOS 26.0, *) {
            buttonStyle(.plain)
        } else {
            buttonStyle(LegacyInteractiveButtonStyle())
        }
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

    @ViewBuilder
    func makeBody(configuration: Configuration) -> some View {
        #if os(iOS)
        if #available(iOS 26.0, *) {
            label(configuration)
                .liquidGlassSurface(cornerRadius: 17, interactive: true, tint: selected ? themeColor : nil)
        } else {
            legacyLabel(configuration)
        }
        #else
        legacyLabel(configuration)
        #endif
    }

    private func label(_ configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(selected ? selectedForeground : Color.primary)
            .padding(.horizontal, 12)
            .frame(minHeight: 44)
    }

    private func legacyLabel(_ configuration: Configuration) -> some View {
        label(configuration)
            .background(
                selected ? themeColor.opacity(configuration.isPressed ? 0.78 : 1) : Color.secondary.opacity(configuration.isPressed ? 0.16 : 0.09),
                in: Capsule()
            )
    }
}

private struct LegacyInteractiveButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.68 : 1)
    }
}

struct ThemeSegmentedControl<Option: Hashable>: View {
    @Binding var selection: Option
    let options: [Option]
    let title: (Option) -> String

    @ViewBuilder
    var body: some View {
        #if os(iOS)
        picker.frame(minHeight: 44)
        #else
        picker
        #endif
    }

    private var picker: some View {
        Picker("", selection: $selection) {
            ForEach(options, id: \.self) { option in
                Text(title(option)).tag(option)
            }
        }
        .pickerStyle(.segmented)
        .labelsHidden()
    }
}
