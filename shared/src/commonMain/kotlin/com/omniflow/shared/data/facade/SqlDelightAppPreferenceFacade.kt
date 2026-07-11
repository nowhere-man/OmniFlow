package com.omniflow.shared.data.facade

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.omniflow.shared.db.OmniFlowDatabase
import com.omniflow.shared.domain.facade.AppPreferenceFacade
import com.omniflow.shared.domain.model.AppPreferenceKey
import com.omniflow.shared.domain.model.AppPreferences
import com.omniflow.shared.domain.model.AppearanceMode
import com.omniflow.shared.domain.model.DEFAULT_BACKUP_RETENTION
import com.omniflow.shared.domain.model.LedgerScope
import com.omniflow.shared.domain.model.SyncTarget
import com.omniflow.shared.domain.model.ThemeColor
import com.omniflow.shared.domain.model.TransactionDetailDisplayMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class SqlDelightAppPreferenceFacade(
    private val database: OmniFlowDatabase,
    private val now: () -> Instant = { Clock.System.now() },
) : AppPreferenceFacade {
    override fun observe(): Flow<Result<AppPreferences>> = database.appPreferenceQueries
        .allPreferences()
        .asFlow()
        .mapToList(Dispatchers.Default)
        .map { rows -> runCatching { decode(rows.associate { it.key to it.value_ }) } }

    override suspend fun save(preferences: AppPreferences): Result<Unit> = runCatching {
        val timestamp = now().toEpochMilliseconds()
        database.transaction {
            save(AppPreferenceKey.HomeLedgerScope, encode(preferences.homeLedgerScope), timestamp)
            save(AppPreferenceKey.AnalyticsLedgerScope, encode(preferences.analyticsLedgerScope), timestamp)
            save(AppPreferenceKey.TransactionDetailDisplayMode, preferences.transactionDetailDisplayMode.name, timestamp)
            save(AppPreferenceKey.AppearanceMode, preferences.appearanceMode.name, timestamp)
            save(AppPreferenceKey.ThemeColor, preferences.themeColor.name, timestamp)
            save(AppPreferenceKey.AppLockEnabled, preferences.appLockEnabled.toString(), timestamp)
            save(AppPreferenceKey.SyncTarget, preferences.syncTarget?.name, timestamp)
            save(AppPreferenceKey.BackupRetention, preferences.backupRetention.toString(), timestamp)
        }
    }

    private fun save(key: String, value: String?, timestamp: Long) {
        if (value == null) {
            database.appPreferenceQueries.deletePreference(key)
        } else {
            database.appPreferenceQueries.upsertPreference(key, value, timestamp)
        }
    }

    private fun decode(values: Map<String, String>) = AppPreferences(
        homeLedgerScope = decodeScope(values[AppPreferenceKey.HomeLedgerScope]),
        analyticsLedgerScope = decodeScope(values[AppPreferenceKey.AnalyticsLedgerScope]),
        transactionDetailDisplayMode = values[AppPreferenceKey.TransactionDetailDisplayMode]
            ?.let { runCatching { TransactionDetailDisplayMode.valueOf(it) }.getOrNull() }
            ?: TransactionDetailDisplayMode.LIST,
        appearanceMode = values[AppPreferenceKey.AppearanceMode]
            ?.let { runCatching { AppearanceMode.valueOf(it) }.getOrNull() }
            ?: AppearanceMode.SYSTEM,
        themeColor = values[AppPreferenceKey.ThemeColor]
            ?.let { runCatching { ThemeColor.valueOf(it) }.getOrNull() }
            ?: ThemeColor.LAVENDER,
        appLockEnabled = values[AppPreferenceKey.AppLockEnabled]?.toBooleanStrictOrNull() ?: false,
        syncTarget = values[AppPreferenceKey.SyncTarget]
            ?.let { runCatching { SyncTarget.valueOf(it) }.getOrNull() },
        backupRetention = values[AppPreferenceKey.BackupRetention]
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: DEFAULT_BACKUP_RETENTION.toInt(),
    )

    private fun encode(scope: LedgerScope): String = when (scope) {
        LedgerScope.All -> "all"
        is LedgerScope.Single -> "ledger:${scope.ledgerId}"
    }

    private fun decodeScope(value: String?): LedgerScope = value
        ?.removePrefix("ledger:")
        ?.takeIf { value.startsWith("ledger:") && it.isNotBlank() }
        ?.let(LedgerScope::Single)
        ?: LedgerScope.All
}
