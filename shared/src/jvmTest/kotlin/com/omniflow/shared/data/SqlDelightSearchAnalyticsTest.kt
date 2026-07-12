package com.omniflow.shared.data

import com.omniflow.shared.data.facade.SqlDelightAnalyticsFacade
import com.omniflow.shared.data.local.createJvmDatabase
import com.omniflow.shared.data.usecase.SqlDelightSearchTransactionsUseCase
import com.omniflow.shared.domain.model.AnalyticsQuery
import com.omniflow.shared.domain.model.DateRange
import com.omniflow.shared.domain.model.LedgerScope
import com.omniflow.shared.domain.model.Money
import com.omniflow.shared.domain.model.TransactionSearchQuery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlDelightSearchAnalyticsTest {
    @Test
    fun searchAndAnalyticsUseDocumentedFiltersAndExclusions() = runBlocking {
        val database = createJvmDatabase()
        database.ledgerQueries.insertLedger("ledger", "日常", null, 1, 1)
        database.accountQueries.insertAccount("account", "现金", "CASH", "banknote", null, null, 1_200, 1, 1, 1)
        database.categoryQueries.insertCategory("food", "ledger", null, "餐饮", "utensils", "EXPENSE", 1, 1)
        database.categoryQueries.insertCategory("restaurant", "ledger", "food", "餐厅", null, "EXPENSE", 1, 1)
        database.categoryQueries.insertCategory("salary", "ledger", null, "工资", "banknote", "INCOME", 1, 1)
        database.tagQueries.insertTag("work", "ledger", "工作", 1, 1)
        insert(database, "expense", "restaurant", 500, "EXPENSE", 1_000, "团队午餐", excluded = false)
        insert(database, "expense-2", "restaurant", 300, "EXPENSE", 1_500, "晚餐", excluded = false)
        insert(database, "excluded", "food", 200, "EXPENSE", 2_000, "报销", excluded = true)
        insert(database, "income", "salary", 1_000, "INCOME", 3_000, "七月工资", excluded = false)
        database.tagQueries.insertTransactionTag("expense", "work")

        val search = SqlDelightSearchTransactionsUseCase(database)(TransactionSearchQuery(keyword = "工作")).getOrThrow()
        assertEquals(listOf("expense"), search.items.map { it.transaction.id })
        assertEquals(Money(500), search.summary.expenseTotal)
        assertEquals("utensils", search.items.single().transaction.categoryIconKey)

        val textSearch = SqlDelightSearchTransactionsUseCase(database)(
            TransactionSearchQuery(
                primaryCategoryText = "餐饮",
                secondaryCategoryText = "餐厅",
                noteText = "晚餐",
            ),
        ).getOrThrow()
        assertEquals(listOf("expense-2"), textSearch.items.map { it.transaction.id })

        val dashboard = SqlDelightAnalyticsFacade(database).observeDashboard(
            AnalyticsQuery(
                scope = LedgerScope.All,
                range = DateRange(Instant.fromEpochMilliseconds(0), Instant.fromEpochMilliseconds(10_000)),
            ),
        ).first().getOrThrow()
        assertEquals(Money(800), dashboard.summary.expenseTotal)
        assertEquals(Money(1_000), dashboard.summary.incomeTotal)
        assertEquals(listOf("餐饮-餐厅"), dashboard.ranking.map { it.categoryDisplayName })
        assertEquals(Money(800), dashboard.ranking.single().amount)
        assertEquals("utensils", dashboard.ranking.single().iconKey)
        assertEquals(listOf("food"), dashboard.categoryBreakdowns.map { it.primaryCategoryId })
        assertEquals(listOf("restaurant"), dashboard.categoryBreakdowns.single().secondaryCategories.map { it.categoryId })
        assertEquals(Money(800), dashboard.yearStatement.months.first().expense)
        assertEquals(Money(1_000), dashboard.yearStatement.months.first().income)
    }

    private fun insert(
        database: com.omniflow.shared.db.OmniFlowDatabase,
        id: String,
        categoryId: String,
        amount: Long,
        type: String,
        occurredAt: Long,
        note: String,
        excluded: Boolean,
    ) {
        database.transactionQueries.insertTransaction(
            id, "ledger", "account", categoryId, amount, type, occurredAt, note,
            if (excluded) 1 else 0, null, null, occurredAt, occurredAt,
        )
    }
}
