package com.omniflow.shared.data

import com.omniflow.shared.data.local.createJvmDatabase
import com.omniflow.shared.data.sync.SqlDelightBackupStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SqlDelightBackupStoreTest {
    @Test
    fun restoresCompleteBusinessDataAndPreferences() = runBlocking {
        val database = createJvmDatabase()
        database.ledgerQueries.insertLedger("ledger", "日常", null, 1, 1)
        database.accountQueries.insertAccount("account", "现金", "CASH", "banknote", null, null, 500, 1, 1, 1)
        database.categoryQueries.insertCategory("category", "ledger", null, "餐饮", "utensils", "EXPENSE", 1, 1)
        database.categoryQueries.updatePrimaryCategorySortOrder(3, 2, "category", "ledger", "EXPENSE")
        database.tagQueries.insertTag("tag", "ledger", "工作", 1, 1)
        database.transactionQueries.insertTransaction(
            "transaction", "ledger", "account", "category", 100, "EXPENSE", 1_000,
            "午餐", 0, null, null, 1, 1,
        )
        database.tagQueries.insertTransactionTag("transaction", "tag")
        database.reminderQueries.insertReminder(
            "reminder", "SUBSCRIPTION", "会员", null, "MONTHLY", "10,,,", 0, 1, 1,
        )
        database.appPreferenceQueries.upsertPreference("appearance_mode", "DARK", 1)
        val store = SqlDelightBackupStore(database)
        val backup = store.create("device", "backup", 2)

        database.backupQueries.clearTransactionTags()
        database.backupQueries.clearTransactions()
        database.backupQueries.clearTags()
        database.backupQueries.clearCategories()
        database.backupQueries.clearAccounts()
        database.backupQueries.clearLedgers()
        database.backupQueries.clearReminders()
        database.backupQueries.clearAppPreferences()

        store.restore(backup)

        assertEquals("日常", database.ledgerQueries.activeLedgers().executeAsOne().name)
        assertEquals(500L, database.accountQueries.activeAccounts().executeAsOne().balance_minor)
        assertEquals("tag", database.tagQueries.tagsForTransaction("transaction").executeAsOne())
        assertEquals("会员", database.reminderQueries.activeReminders().executeAsOne().name)
        assertEquals("DARK", database.appPreferenceQueries.preference("appearance_mode").executeAsOne())
        assertEquals(3L, database.categoryQueries.activeCategory("category").executeAsOne().sort_order)
    }

    @Test
    fun rejectsIncompleteBackupWithoutClearingLocalData() = runBlocking {
        val database = createJvmDatabase()
        database.ledgerQueries.insertLedger("ledger", "日常", null, 1, 1)
        val store = SqlDelightBackupStore(database)

        assertFailsWith<IllegalArgumentException> {
            store.restore(
                com.omniflow.shared.domain.model.BackupRecord(
                    deviceId = "device",
                    backupId = "broken",
                    createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(1),
                    payload = JsonObject(mapOf("version" to JsonPrimitive(1))).toString(),
                ),
            )
        }

        assertEquals("日常", database.ledgerQueries.activeLedgers().executeAsOne().name)
    }
}
