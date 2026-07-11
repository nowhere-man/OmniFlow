import SwiftUI

enum AppThemeColor: String, CaseIterable, Identifiable {
    case mistBlue = "MIST_BLUE"
    case sage = "SAGE"
    case lavender = "LAVENDER"
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
        let hex: UInt32
        switch (self, scheme) {
        case (.mistBlue, .dark): hex = 0x9CC3E5
        case (.mistBlue, _): hex = 0x52779A
        case (.sage, .dark): hex = 0x9BC8A8
        case (.sage, _): hex = 0x4F765B
        case (.lavender, .dark): hex = 0xC2B5E5
        case (.lavender, _): hex = 0x75679D
        case (.softCoral, .dark): hex = 0xE7AAA4
        case (.softCoral, _): hex = 0xA95850
        case (.warmAmber, .dark): hex = 0xD8B778
        case (.warmAmber, _): hex = 0x8A6532
        case (.graphite, .dark): hex = 0xF5F5F5
        case (.graphite, _): hex = 0x171717
        }
        return Color(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

struct AppThemeTintModifier: ViewModifier {
    @Environment(\.colorScheme) private var colorScheme
    let themeColor: String

    func body(content: Content) -> some View {
        content.tint((AppThemeColor(rawValue: themeColor) ?? .graphite).color(for: colorScheme))
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
            Picker("主题色", selection: Binding(get: { store.themeColor }, set: store.setThemeColor)) {
                ForEach(AppThemeColor.allCases) { theme in
                    HStack {
                        Circle().fill(theme.color(for: .light)).frame(width: 12, height: 12)
                        Text(theme.label)
                    }
                    .tag(theme.rawValue)
                }
            }
            Button("数据管理") { store.destination = .more }
        }
        .formStyle(.grouped)
        #if os(macOS)
        .frame(width: 460, height: 270)
        .padding()
        #endif
    }
}
