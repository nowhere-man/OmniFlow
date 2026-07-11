package com.omniflow.shared.data

import com.omniflow.shared.data.facade.SqlDelightAppPreferenceFacade
import com.omniflow.shared.data.local.createJvmDatabase
import com.omniflow.shared.domain.facade.AppPreferenceFacade
import com.omniflow.shared.domain.model.AppPreferences
import com.omniflow.shared.domain.model.AppearanceMode
import com.omniflow.shared.domain.model.LedgerScope
import com.omniflow.shared.domain.model.SyncTarget
import com.omniflow.shared.domain.model.ThemeColor
import com.omniflow.shared.domain.model.TransactionDetailDisplayMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlDelightAppPreferenceFacadeTest {
    @Test
    fun persistsRestorablePreferencesAsOneState() = runBlocking {
        val facade: AppPreferenceFacade = SqlDelightAppPreferenceFacade(createJvmDatabase())
        assertEquals(AppPreferences(), facade.observe().first().getOrThrow())

        val expected = AppPreferences(
            homeLedgerScope = LedgerScope.Single("home-ledger"),
            analyticsLedgerScope = LedgerScope.Single("analytics-ledger"),
            transactionDetailDisplayMode = TransactionDetailDisplayMode.CARD,
            appearanceMode = AppearanceMode.DARK,
            themeColor = ThemeColor.LAVENDER,
            appLockEnabled = true,
            syncTarget = SyncTarget.WEBDAV,
            backupRetention = 3,
        )

        facade.save(expected).getOrThrow()

        assertEquals(expected, facade.observe().first().getOrThrow())
    }
}
