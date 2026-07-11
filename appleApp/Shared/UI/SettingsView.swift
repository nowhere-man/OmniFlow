import Foundation
import SwiftUI

enum AppThemeColor: String, CaseIterable, Identifiable {
    case lavender = "LAVENDER"
    case mistBlue = "MIST_BLUE"
    case sage = "SAGE"
    case softCoral = "SOFT_CORAL"
    case warmAmber = "WARM_AMBER"
    case graphite = "GRAPHITE"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .mistBlue: return "雾蓝"
        case .sage: return "鼠尾草"
        case .lavender: return "薰衣草"
        case .softCoral: return "柔珊瑚"
        case .warmAmber: return "暖琥珀"
        case .graphite: return "石墨灰"
        }
    }

    func color(for scheme: ColorScheme) -> Color {
        let hex = hexValue(for: scheme)
        return Color(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }

    func cssColor(for scheme: ColorScheme) -> String {
        String(format: "#%06X", hexValue(for: scheme))
    }

    private func hexValue(for scheme: ColorScheme) -> UInt32 {
        switch (self, scheme) {
        case (.mistBlue, .dark): return 0x9CC3E5
        case (.mistBlue, _): return 0x52779A
        case (.sage, .dark): return 0x9BC8A8
        case (.sage, _): return 0x4F765B
        case (.lavender, .dark): return 0xC2B5E5
        case (.lavender, _): return 0x75679D
        case (.softCoral, .dark): return 0xE7AAA4
        case (.softCoral, _): return 0xA95850
        case (.warmAmber, .dark): return 0xD8B778
        case (.warmAmber, _): return 0x8A6532
        case (.graphite, .dark): return 0xF5F5F5
        case (.graphite, _): return 0x171717
        }
    }
}

struct AppThemeTintModifier: ViewModifier {
    @Environment(\.colorScheme) private var colorScheme
    let themeColor: String

    func body(content: Content) -> some View {
        content.tint((AppThemeColor(rawValue: themeColor) ?? .lavender).color(for: colorScheme))
    }
}

extension View {
    func appThemeTint(_ themeColor: String) -> some View {
        modifier(AppThemeTintModifier(themeColor: themeColor))
    }
}

struct SettingsView: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        Form {
            Toggle("应用锁", isOn: Binding(get: { store.appLockEnabled }, set: store.setAppLockEnabled))
            Picker("界面外观", selection: Binding(get: { store.appearanceMode }, set: store.setAppearanceMode)) {
                Text("跟随系统").tag("SYSTEM")
                Text("浅色").tag("LIGHT")
                Text("深色").tag("DARK")
            }
            VStack(alignment: .leading, spacing: 10) {
                Text("主题色").font(.headline)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 18) {
                        ForEach(AppThemeColor.allCases) { theme in
                            Button { store.setThemeColor(theme.rawValue) } label: {
                                VStack(spacing: 6) {
                                    Circle()
                                        .fill(theme.color(for: .light))
                                        .frame(width: 36, height: 36)
                                        .overlay {
                                            Circle().stroke(store.themeColor == theme.rawValue ? Color.primary : .clear, lineWidth: 3)
                                        }
                                    Text(theme.label).font(.caption).foregroundStyle(.primary)
                                }
                                .frame(width: 58)
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(theme.label)
                        }
                    }
                }
            }
            Button("数据管理") { store.destination = .more }
        }
        .formStyle(.grouped)
        #if os(macOS)
        .frame(width: 520, height: 330)
        .padding()
        #endif
    }
}
