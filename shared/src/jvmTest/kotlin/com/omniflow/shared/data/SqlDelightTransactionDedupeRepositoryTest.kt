package com.omniflow.shared.data

import com.omniflow.shared.data.local.createJvmDatabase
import com.omniflow.shared.data.repository.SqlDelightTransactionDedupeRepository
import com.omniflow.shared.domain.model.Money
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class SqlDelightTransactionDedupeRepositoryTest {
    @Test
    fun treatsMerchantSuffixAsLikelyDuplicate() = runBlocking {
        val database = createJvmDatabase()
        database.ledgerQueries.insertLedger("ledger", "日常", null, 1, 1)
        database.accountQueries.insertAccount("account", "现金", "CASH", "wallet", null, null, 0, 1, 1, 1)
        database.categoryQueries.insertCategory("food", "ledger", null, "餐饮", "utensils", "EXPENSE", 1, 1)
        database.transactionQueries.insertTransaction(
            "transaction", "ledger", "account", "food", 1200, "EXPENSE", 10_000,
            "星巴克咖啡上海店", 0, null, null, 1, 1,
        )

        assertTrue(
            SqlDelightTransactionDedupeRepository(database).likelyDuplicate(
                ledgerId = "ledger",
                amount = Money(1200),
                occurredAtStart = 0,
                occurredAtEnd = 20_000,
                note = "星巴克咖啡",
            ),
        )
    }
}
