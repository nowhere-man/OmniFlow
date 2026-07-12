import SwiftUI

#if os(macOS)
@main
struct OmniFlowMacOSApp: App {
    @StateObject private var store = AppStore()

    var body: some Scene {
        WindowGroup("OmniFlow") {
            AppLockGate(enabled: store.appLockEnabled) {
                MacRootView()
                    .environmentObject(store)
                    .frame(minWidth: 920, minHeight: 620)
            }
            .preferredColorScheme(store.appearanceMode == "DARK" ? .dark : store.appearanceMode == "LIGHT" ? .light : nil)
            .appThemeTint(store.themeColor)
            .onChange(of: store.reminders) { ReminderNotificationScheduler.sync($0) }
        }
        .defaultSize(width: 1180, height: 760)
        .commands {
            CommandGroup(replacing: .newItem) {
                Button("新建交易") { store.startNewTransaction() }
                    .keyboardShortcut("n")
            }
            CommandMenu("导航") {
                Button("首页") { store.destination = .home }
                    .keyboardShortcut("1")
                Button("统计") { store.destination = .analytics }
                    .keyboardShortcut("2")
                Button("搜索交易") { store.destination = .search }
                    .keyboardShortcut("f")
                Button("管理") { store.destination = .more }
                    .keyboardShortcut("4")
            }
        }

        Settings {
            NavigationStack {
                SettingsView()
                    .environmentObject(store)
                    .appThemeTint(store.themeColor)
                    .frame(width: 580, height: 540)
            }
        }
    }
}
#else
@main
struct OmniFlowIOSApp: App {
    @StateObject private var store = AppStore()

    var body: some Scene {
        WindowGroup {
            AppLockGate(enabled: store.appLockEnabled) {
                PhoneRootView()
                    .environmentObject(store)
            }
            .onOpenURL { url in
                guard url.scheme?.lowercased() == "omniflow", url.host?.lowercased() == "add" else { return }
                store.startNewTransaction()
            }
            .preferredColorScheme(store.appearanceMode == "DARK" ? .dark : store.appearanceMode == "LIGHT" ? .light : nil)
            .appThemeTint(store.themeColor)
            .onChange(of: store.reminders) { ReminderNotificationScheduler.sync($0) }
        }
    }
}
#endif
