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
        .commands {
            CommandMenu("记账") {
                Button("新建交易") { store.startNewTransaction() }
                    .keyboardShortcut("n")
                Button("搜索交易") { store.destination = .search }
                    .keyboardShortcut("f")
            }
        }

        Settings {
            SettingsView()
                .environmentObject(store)
                .appThemeTint(store.themeColor)
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
            .preferredColorScheme(store.appearanceMode == "DARK" ? .dark : store.appearanceMode == "LIGHT" ? .light : nil)
            .appThemeTint(store.themeColor)
            .onChange(of: store.reminders) { ReminderNotificationScheduler.sync($0) }
        }
    }
}
#endif
