import SwiftUI
import LocalAuthentication
import UserNotifications
import Security

struct AppLockGate<Content: View>: View {
    let enabled: Bool
    @Environment(\.scenePhase) private var scenePhase
    @State private var unlocked = false
    @State private var error: String?
    @ViewBuilder let content: Content

    var body: some View {
        Group {
            if !enabled || unlocked {
                content
            } else {
                VStack(spacing: 14) {
                    Image(systemName: "lock.shield").font(.system(size: 42))
                    Text("OmniFlow 已锁定").font(.title2.bold())
                    if let error { Text(error).foregroundStyle(.secondary) }
                    Button("解锁", action: authenticate).buttonStyle(.borderedProminent)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .task { if enabled { authenticate() } }
        .onChange(of: scenePhase) { phase in
            if enabled && phase == .background { unlocked = false }
            if enabled && phase == .active && !unlocked { authenticate() }
        }
    }

    private func authenticate() {
        let context = LAContext()
        var failure: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &failure) else {
            error = failure?.localizedDescription ?? "设备未配置解锁能力"
            return
        }
        context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: "解锁 OmniFlow") { success, failure in
            DispatchQueue.main.async { unlocked = success; error = failure?.localizedDescription }
        }
    }
}

enum ReminderNotificationScheduler {
    static func sync(_ reminders: [ReminderUI]) {
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .badge, .sound]) { _, _ in }
        center.getPendingNotificationRequests { pending in
            center.removePendingNotificationRequests(withIdentifiers: pending.map(\.identifier).filter { $0.hasPrefix("omniflow-") })
            reminders.filter { !$0.paused }.forEach { reminder in
                let content = UNMutableNotificationContent()
                content.title = reminder.name
                content.body = reminder.amountMinor.map { "提醒金额 \($0.rmb)" }
                    ?? (reminder.type.contains("REPAYMENT") ? "还款提醒" : "订阅提醒")
                content.sound = .default
                if reminder.scheduleKind.contains("DAYS_AFTER_STATEMENT") {
                    daysAfterStatementDates(reminder).enumerated().forEach { index, date in
                        let components = Calendar.current.dateComponents([.year, .month, .day, .hour], from: date)
                        let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
                        center.add(UNNotificationRequest(identifier: "omniflow-\(reminder.id)-\(index)", content: content, trigger: trigger))
                    }
                    return
                }
                guard let trigger = trigger(reminder) else { return }
                center.add(UNNotificationRequest(identifier: "omniflow-\(reminder.id)", content: content, trigger: trigger))
            }
        }
    }

    private static func trigger(_ reminder: ReminderUI) -> UNNotificationTrigger? {
        let kind = reminder.scheduleKind
        var components = DateComponents(hour: 9)
        if kind.contains("DAILY") { return UNCalendarNotificationTrigger(dateMatching: components, repeats: true) }
        if kind.contains("WEEKLY") {
            components.weekday = ((reminder.dayOfWeek ?? 1) % 7) + 1
            return UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
        }
        if kind.contains("YEARLY") {
            components.month = reminder.month ?? 1
            components.day = reminder.dayOfMonth ?? 1
            return UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
        }
        components.day = reminder.dayOfMonth ?? 1
        return UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
    }

    private static func daysAfterStatementDates(_ reminder: ReminderUI) -> [Date] {
        let calendar = Calendar.current
        let now = Date()
        return (0..<12).compactMap { offset in
            guard let month = calendar.date(byAdding: .month, value: offset, to: now),
                  let range = calendar.range(of: .day, in: .month, for: month),
                  let statement = calendar.date(
                    from: DateComponents(
                        year: calendar.component(.year, from: month),
                        month: calendar.component(.month, from: month),
                        day: min(reminder.dayOfMonth ?? 1, range.count),
                        hour: 9
                    )
                  ),
                  let due = calendar.date(byAdding: .day, value: reminder.daysAfter ?? 0, to: statement),
                  due > now else { return nil }
            return due
        }
    }
}

enum KeychainPassword {
    private static let service = "com.omniflow.webdav"
    private static let account = "password"

    static func save(_ password: String) {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: account]
        SecItemDelete(query as CFDictionary)
        var value = query
        value[kSecValueData as String] = Data(password.utf8)
        SecItemAdd(value as CFDictionary, nil)
    }

    static func load() -> String {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return "" }
        return String(data: data, encoding: .utf8) ?? ""
    }
}
