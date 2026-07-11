package com.omniflow.shared.data

import com.omniflow.shared.data.local.createJvmDatabase
import com.omniflow.shared.data.repository.SqlDelightLedgerRepository
import com.omniflow.shared.data.repository.SqlDelightTransactionRepository
import com.omniflow.shared.domain.model.Money
import com.omniflow.shared.domain.model.Transaction
import com.omniflow.shared.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlDelightLedgerRepositoryTest {
    @Test
    fun deletingLedgerReversesItsTransactionsFromAccountBalance() = runBlocking {
        val database = createJvmDatabase()
        database.ledgerQueries.insertLedger("ledger", "日常", null, 1, 1)
        database.accountQueries.insertAccount("account", "现金", "CASH", "wallet", null, null, 0, 1, 1, 1)
        database.categoryQueries.insertCategory("expense", "ledger", null, "餐饮", "utensils", "EXPENSE", 1, 1)
        SqlDelightTransactionRepository(database).create(
            Transaction(
                id = "transaction",
                ledgerId = "ledger",
                accountId = "account",
                categoryId = "expense",
                amount = Money(500),
                type = TransactionType.EXPENSE,
                occurredAt = Instant.fromEpochMilliseconds(1_000),
                note = null,
                isExcluded = false,
                source = null,
                externalId = null,
            ),
        )

        SqlDelightLedgerRepository(database).archive("ledger")

        assertEquals(0L, database.accountQueries.accountBalance("account").executeAsOne())
        assertEquals(0, database.transactionQueries.transactionsForLedger("ledger").executeAsList().size)
    }
}
