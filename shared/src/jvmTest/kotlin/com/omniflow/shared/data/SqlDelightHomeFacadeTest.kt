package com.omniflow.shared.data

import com.omniflow.shared.data.facade.SqlDelightHomeFacade
import com.omniflow.shared.data.local.createJvmDatabase
import com.omniflow.shared.domain.model.CalendarTransactionFilter
import com.omniflow.shared.domain.model.DateRange
import com.omniflow.shared.domain.model.HomeQuery
import com.omniflow.shared.domain.model.LedgerScope
import com.omniflow.shared.domain.model.Money
import com.omniflow.shared.domain.model.TransactionDetailQuery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlDelightHomeFacadeTest {
    @Test
    fun calendarFilterDoesNotChangeMonthSummaryOrDetailGroups() = runBlocking {
        val database = createJvmDatabase()
        seed(database)
        val facade = SqlDelightHomeFacade(database)
        val month = DateRange(
            startInclusive = Instant.fromEpochMilliseconds(1_000),
            endExclusive = Instant.fromEpochMilliseconds(4_000),
        )

        val state = facade.observeHome(
            HomeQuery(
                scope = LedgerScope.All,
                month = month,
                calendarFilter = CalendarTransactionFilter.EXPENSE,
            ),
        ).first().getOrThrow()

        assertEquals(Money(500), state.summary.expenseTotal)
        assertEquals(Money(800), state.summary.incomeTotal)
        assertEquals(2, state.groups.single().items.size)
        assertEquals(Money(500), state.calendar.single().expenseTotal)
        assertEquals(Money.Zero, state.calendar.single().incomeTotal)
    }

    @Test
    fun dateDetailUsesItsOwnRange() = runBlocking {
        val database = createJvmDatabase()
        seed(database)
        val facade = SqlDelightHomeFacade(database)

        val state = facade.observeTransactionDetails(
            TransactionDetailQuery(
                scope = LedgerScope.Single("ledger"),
                date = DateRange(
                    startInclusive = Instant.fromEpochMilliseconds(1_000),
                    endExclusive = Instant.fromEpochMilliseconds(2_000),
                ),
            ),
        ).first().getOrThrow()

        assertEquals(1, state.items.size)
        assertEquals(Money(500), state.summary.expenseTotal)
        assertEquals(Money.Zero, state.summary.incomeTotal)
    }

    @Test
    fun excludedTransactionsDoNotChangeDayGroupTotals() = runBlocking {
        val database = createJvmDatabase()
        seed(database)
        database.transactionQueries.insertTransaction(
            "excluded", "ledger", "account", "expense", 300, "EXPENSE", 1_600,
            "不计入统计", 1, null, null, 1, 1,
        )

        val state = SqlDelightHomeFacade(database).observeHome(
            HomeQuery(
                scope = LedgerScope.All,
                month = DateRange(Instant.fromEpochMilliseconds(1_000), Instant.fromEpochMilliseconds(4_000)),
                calendarFilter = CalendarTransactionFilter.ALL,
            ),
        ).first().getOrThrow()

        assertEquals(Money(500), state.groups.single().expenseTotal)
        assertEquals(setOf("expense-transaction", "income-transaction"), state.groups.single().items.map { it.id }.toSet())
    }

    private fun seed(database: com.omniflow.shared.db.OmniFlowDatabase) {
        database.ledgerQueries.insertLedger("ledger", "账本", null, 1, 1)
        database.accountQueries.insertAccount(
            id = "account",
            name = "现金",
            type = "CASH",
            icon_key = "banknote",
            card_number = null,
            note = null,
            balance_minor = 0,
            include_in_total_assets = 1,
            created_at = 1,
            updated_at = 1,
        )
        database.categoryQueries.insertCategory("expense", "ledger", null, "餐饮", "utensils", "EXPENSE", 1, 1)
        database.categoryQueries.insertCategory("income", "ledger", null, "工资", "banknote", "INCOME", 1, 1)
        database.transactionQueries.insertTransaction(
            id = "expense-transaction",
            ledger_id = "ledger",
            account_id = "account",
            category_id = "expense",
            amount_minor = 500,
            type = "EXPENSE",
            occurred_at = 1_500,
            note = "午餐",
            is_excluded = 0,
            external_source = null,
            external_id = null,
            created_at = 1,
            updated_at = 1,
        )
        database.transactionQueries.insertTransaction(
            id = "income-transaction",
            ledger_id = "ledger",
            account_id = "account",
            category_id = "income",
            amount_minor = 800,
            type = "INCOME",
            occurred_at = 2_500,
            note = "工资",
            is_excluded = 0,
            external_source = null,
            external_id = null,
            created_at = 1,
            updated_at = 1,
        )
    }
}
