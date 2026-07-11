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

    func selectionForeground(for scheme: ColorScheme) -> Color {
        self == .graphite && scheme == .dark ? .black : .white
    }

    func selectionCSSColor(for scheme: ColorScheme) -> String {
        self == .graphite && scheme == .dark ? "#000000" : "#FFFFFF"
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

struct AppThemeColorKey: EnvironmentKey {
    static let defaultValue = Color(red: 0.46, green: 0.40, blue: 0.62)
}

struct AppThemeSelectionForegroundKey: EnvironmentKey {
    static let defaultValue = Color.white
}

extension EnvironmentValues {
    var appThemeColor: Color {
        get { self[AppThemeColorKey.self] }
        set { self[AppThemeColorKey.self] = newValue }
    }

    var appThemeSelectionForeground: Color {
        get { self[AppThemeSelectionForegroundKey.self] }
        set { self[AppThemeSelectionForegroundKey.self] = newValue }
    }
}

struct AppThemeTintModifier: ViewModifier {
    @Environment(\.colorScheme) private var colorScheme
    let themeColor: String

    func body(content: Content) -> some View {
        let theme = AppThemeColor(rawValue: themeColor) ?? .lavender
        content
            .tint(theme.color(for: colorScheme))
            .environment(\.appThemeColor, theme.color(for: colorScheme))
            .environment(\.appThemeSelectionForeground, theme.selectionForeground(for: colorScheme))
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
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                SettingsSection(title: "安全") {
                    SettingsRow(systemImage: "lock.shield", title: "应用锁", detail: "打开应用时验证设备密码或生物识别") {
                        Toggle("", isOn: Binding(get: { store.appLockEnabled }, set: store.setAppLockEnabled))
                            .labelsHidden()
                    }
                }

                SettingsSection(title: "外观") {
                    VStack(alignment: .leading, spacing: 16) {
                        SettingsRow(systemImage: "circle.lefthalf.filled", title: "界面外观", detail: "跟随系统或固定显示模式") {
                            EmptyView()
                        }
                        Picker("界面外观", selection: Binding(get: { store.appearanceMode }, set: store.setAppearanceMode)) {
                            Text("跟随系统").tag("SYSTEM")
                            Text("浅色").tag("LIGHT")
                            Text("深色").tag("DARK")
                        }
                        .pickerStyle(.segmented)
                    }
                    Divider()
                    VStack(alignment: .leading, spacing: 14) {
                        Label("主题色", systemImage: "paintpalette").font(.headline)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 16) {
                                ForEach(AppThemeColor.allCases) { theme in
                                    Button { store.setThemeColor(theme.rawValue) } label: {
                                        VStack(spacing: 7) {
                                            Circle()
                                                .fill(theme.color(for: .light))
                                                .frame(width: 32, height: 32)
                                                .overlay {
                                                    Circle().stroke(store.themeColor == theme.rawValue ? Color.primary : .clear, lineWidth: 2.5)
                                                }
                                                .padding(3)
                                            Text(theme.label)
                                                .font(.caption)
                                                .foregroundStyle(.primary)
                                                .lineLimit(1)
                                        }
                                        .frame(width: 62)
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityLabel(theme.label)
                                    .accessibilityAddTraits(store.themeColor == theme.rawValue ? .isSelected : [])
                                }
                            }
                        }
                    }
                }

                SettingsSection(title: "数据") {
                    NavigationLink { DataManagementView() } label: {
                        SettingsRow(systemImage: "externaldrive.badge.icloud", title: "数据管理", detail: "iCloud、WebDAV、备份与恢复") {
                            Image(systemName: "chevron.right").foregroundStyle(.tertiary)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(20)
        }
        .navigationTitle("设置")
        #if os(macOS)
        .frame(width: 560, height: 500)
        #endif
    }
}

private struct SettingsSection<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
            VStack(alignment: .leading, spacing: 14) { content }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .liquidGlassSurface(cornerRadius: 18)
        }
    }
}

private struct SettingsRow<Accessory: View>: View {
    let systemImage: String
    let title: String
    let detail: String
    @ViewBuilder let accessory: Accessory

    var body: some View {
        HStack(spacing: 13) {
            Image(systemName: systemImage)
                .font(.body.weight(.semibold))
                .foregroundStyle(.tint)
                .frame(width: 26)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).fontWeight(.medium)
                Text(detail).font(.caption).foregroundStyle(.secondary)
            }
            Spacer(minLength: 12)
            accessory
        }
        .frame(minHeight: 48)
        .contentShape(Rectangle())
    }
}
