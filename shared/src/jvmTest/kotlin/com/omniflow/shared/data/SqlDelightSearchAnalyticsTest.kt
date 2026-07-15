package com.omniflow.shared.data

import com.omniflow.shared.data.facade.SqlDelightAnalyticsFacade
import com.omniflow.shared.data.local.createJvmDatabase
import com.omniflow.shared.data.usecase.SqlDelightSearchTransactionsUseCase
import com.omniflow.shared.domain.model.AnalyticsQuery
import com.omniflow.shared.domain.model.DateRange
import com.omniflow.shared.domain.model.LedgerScope
import com.omniflow.shared.domain.model.Money
import com.omniflow.shared.domain.model.TimeGranularity
import com.omniflow.shared.domain.model.TransactionSearchQuery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
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
        assertEquals(Money.Zero, dashboard.previousSummary.expenseTotal)
        assertEquals(listOf("expense", "expense-2"), dashboard.ranking.map { it.transactionId })
        assertEquals(listOf(Money(500), Money(300)), dashboard.ranking.map { it.amount })
        assertEquals("utensils", dashboard.ranking.first().iconKey)
        assertEquals(listOf("工作", "无标签"), dashboard.tagAnalysis.map { it.tagName })
        assertEquals(listOf(Money(500), Money(300)), dashboard.tagAnalysis.map { it.amount })
        assertEquals(listOf(1, 1), dashboard.tagAnalysis.map { it.transactionCount })
        assertEquals(listOf("food"), dashboard.categoryBreakdowns.map { it.primaryCategoryId })
        assertEquals(listOf("restaurant"), dashboard.categoryBreakdowns.single().secondaryCategories.map { it.categoryId })
        assertEquals(Money(800), dashboard.yearStatement.months.first().expense)
        assertEquals(Money(1_000), dashboard.yearStatement.months.first().income)
    }

    @Test
    fun analyticsRanksTransactionsAndLimitsRankingToTenItems() = runBlocking {
        val database = createJvmDatabase()
        database.ledgerQueries.insertLedger("ledger", "日常", null, 1, 1)
        database.accountQueries.insertAccount("account", "现金", "CASH", "banknote", null, null, 0, 1, 1, 1)
        (1..11).forEach { index ->
            database.categoryQueries.insertCategory(
                "primary-$index", "ledger", null, "分类$index", "folder", "EXPENSE", index.toLong(), 1,
            )
            insert(database, "expense-$index", "primary-$index", index * 100L, "EXPENSE", 1_000, "", excluded = false)
        }
        database.categoryQueries.insertCategory("secondary", "ledger", "primary-1", "子类", "tag", "EXPENSE", 12, 1)
        insert(database, "secondary-1", "secondary", 600, "EXPENSE", 1_100, "", excluded = false)
        insert(database, "secondary-2", "secondary", 700, "EXPENSE", 1_200, "", excluded = false)

        val dashboard = SqlDelightAnalyticsFacade(database).observeDashboard(
            AnalyticsQuery(
                scope = LedgerScope.All,
                range = DateRange(Instant.fromEpochMilliseconds(0), Instant.fromEpochMilliseconds(10_000)),
            ),
        ).first().getOrThrow()

        assertEquals(10, dashboard.ranking.size)
        assertEquals("expense-11", dashboard.ranking.first().transactionId)
        assertEquals(Money(1_100), dashboard.ranking.first().amount)
        assertEquals("分类11", dashboard.ranking.first().categoryDisplayName)
        assertEquals(true, dashboard.ranking.any { it.transactionId == "secondary-2" && it.amount == Money(700) })
        val primary = dashboard.categoryBreakdowns.first { it.primaryCategoryId == "primary-1" }
        assertEquals(Money(1_400), primary.amount)
        assertEquals(listOf("子类"), primary.secondaryCategories.map { it.categoryName })
        assertEquals(Money(1_300), primary.secondaryCategories.single().amount)
    }

    @Test
    fun analyticsMergesMatchingCategoryPathsAcrossLedgers() = runBlocking {
        val database = createJvmDatabase()
        database.ledgerQueries.insertLedger("ledger-1", "日常", null, 1, 1)
        database.ledgerQueries.insertLedger("ledger-2", "旅行", null, 2, 1)
        database.accountQueries.insertAccount("account-1", "现金", "CASH", "banknote", null, null, 0, 1, 1, 1)
        database.accountQueries.insertAccount("account-2", "银行卡", "BANK", "credit-card", null, null, 0, 2, 1, 1)
        database.categoryQueries.insertCategory("food-1", "ledger-1", null, "餐饮", "utensils", "EXPENSE", 1, 1)
        database.categoryQueries.insertCategory("restaurant-1", "ledger-1", "food-1", "餐厅", null, "EXPENSE", 2, 1)
        database.categoryQueries.insertCategory("food-2", "ledger-2", null, "餐饮", "utensils", "EXPENSE", 1, 1)
        database.categoryQueries.insertCategory("restaurant-2", "ledger-2", "food-2", "餐厅", null, "EXPENSE", 2, 1)
        insert(database, "expense-1", "restaurant-1", 500, "EXPENSE", 1_000, "", excluded = false, ledgerId = "ledger-1", accountId = "account-1")
        insert(database, "expense-2", "restaurant-2", 700, "EXPENSE", 1_100, "", excluded = false, ledgerId = "ledger-2", accountId = "account-2")

        val dashboard = SqlDelightAnalyticsFacade(database).observeDashboard(
            AnalyticsQuery(
                scope = LedgerScope.All,
                range = DateRange(Instant.fromEpochMilliseconds(0), Instant.fromEpochMilliseconds(10_000)),
            ),
        ).first().getOrThrow()

        assertEquals(listOf("expense-2", "expense-1"), dashboard.ranking.map { it.transactionId })
        assertEquals(listOf(Money(700), Money(500)), dashboard.ranking.map { it.amount })
        assertEquals(listOf("餐饮"), dashboard.categoryBreakdowns.map { it.primaryCategoryName })
        assertEquals(Money(1_200), dashboard.categoryBreakdowns.single().amount)
        assertEquals(Money(1_200), dashboard.categoryBreakdowns.single().secondaryCategories.single().amount)
    }

    @Test
    fun dashboardYearStatementKeepsMonthsAvailableThroughCurrentMonth() = runBlocking {
        val database = createJvmDatabase()
        database.ledgerQueries.insertLedger("ledger", "日常", null, 1, 1)
        database.accountQueries.insertAccount("account", "现金", "CASH", "banknote", null, null, 0, 1, 1, 1)
        database.categoryQueries.insertCategory("food", "ledger", null, "餐饮", "utensils", "EXPENSE", 1, 1)
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(zone)
        val januaryStart = LocalDate(now.year, 1, 1).atStartOfDayIn(zone)
        val februaryStart = LocalDate(now.year, 2, 1).atStartOfDayIn(zone)
        insert(database, "january", "food", 500, "EXPENSE", januaryStart.toEpochMilliseconds(), "", excluded = false)

        val dashboard = SqlDelightAnalyticsFacade(database).observeDashboard(
            AnalyticsQuery(
                scope = LedgerScope.All,
                range = DateRange(januaryStart, februaryStart),
            ),
        ).first().getOrThrow()

        assertEquals((1..now.monthNumber).toList(), dashboard.yearStatement.months.map { it.month })
        assertEquals(Money(500), dashboard.yearStatement.months.first().expense)
    }

    @Test
    fun analyticsBuildsPreviousSummaryAndContinuousTrend() = runBlocking {
        val database = createJvmDatabase()
        database.ledgerQueries.insertLedger("ledger", "日常", null, 1, 1)
        database.accountQueries.insertAccount("account", "现金", "CASH", "banknote", null, null, 0, 1, 1, 1)
        database.categoryQueries.insertCategory("food", "ledger", null, "餐饮", "utensils", "EXPENSE", 1, 1)
        val zone = TimeZone.currentSystemDefault()
        val start = LocalDate(2025, 1, 1).atStartOfDayIn(zone)
        val secondDay = LocalDate(2025, 1, 2).atStartOfDayIn(zone)
        val thirdDay = LocalDate(2025, 1, 3).atStartOfDayIn(zone)
        val end = LocalDate(2025, 1, 4).atStartOfDayIn(zone)
        val previous = LocalDate(2024, 12, 31).atStartOfDayIn(zone)
        insert(database, "previous", "food", 200, "EXPENSE", previous.toEpochMilliseconds(), "", excluded = false)
        insert(database, "first", "food", 300, "EXPENSE", start.toEpochMilliseconds(), "", excluded = false)
        insert(database, "third", "food", 500, "INCOME", thirdDay.toEpochMilliseconds(), "", excluded = false)

        val dashboard = SqlDelightAnalyticsFacade(database).observeDashboard(
            AnalyticsQuery(
                scope = LedgerScope.All,
                range = DateRange(start, end),
                trendGranularity = TimeGranularity.DAY,
            ),
        ).first().getOrThrow()

        assertEquals(Money(200), dashboard.previousSummary.expenseTotal)
        assertEquals(listOf(start, secondDay, thirdDay), dashboard.trend.points.map { it.start })
        assertEquals(listOf(Money(300), Money.Zero, Money.Zero), dashboard.trend.points.map { it.expense })
        assertEquals(listOf(Money.Zero, Money.Zero, Money(500)), dashboard.trend.points.map { it.income })
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
        ledgerId: String = "ledger",
        accountId: String = "account",
    ) {
        database.transactionQueries.insertTransaction(
            id, ledgerId, accountId, categoryId, amount, type, occurredAt, note,
            if (excluded) 1 else 0, null, null, occurredAt, occurredAt,
        )
    }
}
