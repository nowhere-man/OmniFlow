package com.omniflow.shared.data

import com.omniflow.shared.data.local.createJvmDatabase
import com.omniflow.shared.data.repository.SqlDelightInitialDataRepository
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlDelightInitialDataRepositoryTest {
    @Test
    fun seedsInitialLedgerCategoriesAndAccountsOnlyOnce() = runBlocking {
        val database = createJvmDatabase()
        val repository = SqlDelightInitialDataRepository(database) {
            Instant.fromEpochMilliseconds(1_000)
        }

        repository.seedIfNeeded()
        repository.seedIfNeeded()

        val ledger = database.ledgerQueries.activeLedgers().executeAsOne()
        assertEquals(1, database.ledgerQueries.activeLedgers().executeAsList().size)
        assertEquals(16, database.categoryQueries.activeCategoriesForLedger(
            ledger.id,
        ).executeAsList().size)
        assertEquals(7, database.accountQueries.activeAccounts().executeAsList().size)
        assertEquals(ledger.id, database.appPreferenceQueries.preference("default_ledger_id").executeAsOne())
    }
}
