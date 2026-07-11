package com.omniflow.shared.domain.model

object AppPreferenceKey {
    const val DefaultLedgerId = "default_ledger_id"
    const val HomeLedgerScope = "home_ledger_scope"
    const val AnalyticsLedgerScope = "analytics_ledger_scope"
    const val TransactionDetailDisplayMode = "transaction_detail_display_mode"
    const val AppearanceMode = "appearance_mode"
    const val ThemeColor = "theme_color"
    const val AppLockEnabled = "app_lock_enabled"
    const val SyncTarget = "sync_target"
    const val BackupRetention = "backup_retention"

    internal const val InitialDataSeeded = "initial_data_seeded"
}

const val DEFAULT_BACKUP_RETENTION = 10L

enum class TransactionDetailDisplayMode { LIST, CARD }

enum class AppearanceMode { SYSTEM, LIGHT, DARK }

enum class ThemeColor { LAVENDER, MIST_BLUE, SAGE, SOFT_CORAL, WARM_AMBER, GRAPHITE }

enum class SyncTarget { ICLOUD, WEBDAV }

data class AppPreferences(
    val homeLedgerScope: LedgerScope = LedgerScope.All,
    val analyticsLedgerScope: LedgerScope = LedgerScope.All,
    val transactionDetailDisplayMode: TransactionDetailDisplayMode = TransactionDetailDisplayMode.LIST,
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    val themeColor: ThemeColor = ThemeColor.LAVENDER,
    val appLockEnabled: Boolean = false,
    val syncTarget: SyncTarget? = null,
    val backupRetention: Int = DEFAULT_BACKUP_RETENTION.toInt(),
) {
    init {
        require(backupRetention > 0) { "备份保留数必须大于零" }
    }
}
