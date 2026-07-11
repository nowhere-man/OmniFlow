package com.omniflow.shared.data.facade

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.omniflow.shared.db.OmniFlowDatabase
import com.omniflow.shared.db.TransactionRowsInRange
import com.omniflow.shared.domain.facade.AnalyticsFacade
import com.omniflow.shared.domain.model.AnalyticsDashboardState
import com.omniflow.shared.domain.model.AnalyticsQuery
import com.omniflow.shared.domain.model.AccountAssetItem
import com.omniflow.shared.domain.model.AccountSummary
import com.omniflow.shared.domain.model.CategoryShareGranularity
import com.omniflow.shared.domain.model.CategoryShareItem
import com.omniflow.shared.domain.model.ChartData
import com.omniflow.shared.domain.model.ChartPoint
import com.omniflow.shared.domain.model.DateRange
import com.omniflow.shared.domain.model.LedgerScope
import com.omniflow.shared.domain.model.Money
import com.omniflow.shared.domain.model.PeriodCompareResult
import com.omniflow.shared.domain.model.StatementMonth
import com.omniflow.shared.domain.model.StatementTable
import com.omniflow.shared.domain.model.TagSummaryItem
import com.omniflow.shared.domain.model.TimeGranularity
import com.omniflow.shared.domain.model.TransactionListItem
import com.omniflow.shared.domain.model.TransactionRankingItem
import com.omniflow.shared.domain.model.TransactionSummary
import com.omniflow.shared.domain.model.TransactionTag
import com.omniflow.shared.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

class SqlDelightAnalyticsFacade(
    private val database: OmniFlowDatabase,
) : AnalyticsFacade {
    override fun observeDashboard(query: AnalyticsQuery): Flow<Result<AnalyticsDashboardState>> = combine(
        observeRows(query.scope, query.range),
        observeTags(query.scope, query.range),
        observeAccounts(),
    ) { rows, tags, accounts -> DashboardInputs(rows, tags, accounts) }.map { input ->
        runCatching { dashboard(query, input.rows, input.tags, input.accounts) }
    }

    override suspend fun statementTable(scope: LedgerScope, year: Int): Result<StatementTable> = runCatching {
        val range = DateRange(
            startInclusive = LocalDate(year, 1, 1).atStartOfDayIn(CHINA_TIME_ZONE),
            endExclusive = LocalDate(year + 1, 1, 1).atStartOfDayIn(CHINA_TIME_ZONE),
        )
        val rows = rows(scope, range)
        val entries = rows.filterNot { it.is_excluded != 0L }
        val months = (1..12).map { month ->
            val monthRows = entries.filter {
                Instant.fromEpochMilliseconds(it.occurred_at)
                    .toLocalDateTime(CHINA_TIME_ZONE)
                    .monthNumber == month
            }
            val summary = summary(monthRows)
            StatementMonth(month, summary.expenseTotal, summary.incomeTotal)
        }
        StatementTable(year, months, summary(entries))
    }

    override suspend fun trend(
        scope: LedgerScope,
        range: DateRange,
        granularity: TimeGranularity,
    ): Result<ChartData> = runCatching {
        trend(rows(scope, range), range, granularity)
    }

    private fun dashboard(
        query: AnalyticsQuery,
        currentRows: List<TransactionRowsInRange>,
        tagsByTransaction: Map<String, List<TransactionTag>>,
        accounts: AccountAnalytics,
    ): AnalyticsDashboardState {
        val currentSummary = summary(currentRows)
        val previousRange = previousRange(query.range)
        val previousSummary = summary(rows(query.scope, previousRange))
        val previousYearSummary = summary(rows(query.scope, previousYearRange(query.range)))
        return AnalyticsDashboardState(
            query = query,
            summary = currentSummary,
            previousPeriod = PeriodCompareResult(currentSummary, previousSummary),
            yearOverYear = PeriodCompareResult(currentSummary, previousYearSummary),
            trend = trend(currentRows, query.range, preferredGranularity(query.range)),
            ranking = ranking(currentRows, query.rankingType, tagsByTransaction),
            categoryShares = categoryShares(
                currentRows,
                query.categoryShareType,
                query.categoryShareGranularity,
                query.primaryCategoryId,
            ),
            tagSummary = tagSummary(currentRows, tagsByTransaction),
            accountSummary = accounts.summary,
            accountAssets = accounts.assets,
        )
    }

    private fun observeRows(scope: LedgerScope, range: DateRange): Flow<List<TransactionRowsInRange>> = database.transactionQueries
        .transactionRowsInRange(
            start_inclusive = range.startInclusive.toEpochMilliseconds(),
            end_exclusive = range.endExclusive.toEpochMilliseconds(),
            ledger_filter = scope.ledgerIdOrNull(),
        )
        .asFlow()
        .mapToList(Dispatchers.Default)

    private fun observeTags(scope: LedgerScope, range: DateRange): Flow<Map<String, List<TransactionTag>>> = database.transactionQueries
        .transactionTagsInRange(
            start_inclusive = range.startInclusive.toEpochMilliseconds(),
            end_exclusive = range.endExclusive.toEpochMilliseconds(),
            ledger_filter = scope.ledgerIdOrNull(),
        )
        .asFlow()
        .mapToList(Dispatchers.Default)
        .map { rows -> rows.groupBy { it.transaction_id }.mapValues { (_, tags) ->
            tags.map { TransactionTag(it.tag_id, it.tag_name) }
        } }

    private fun observeAccounts(): Flow<AccountAnalytics> = combine(
        database.accountQueries.accountSummary().asFlow().mapToList(Dispatchers.Default),
        database.accountQueries.activeAccounts().asFlow().mapToList(Dispatchers.Default),
    ) { summaryRows, accounts ->
        val summary = summaryRows.single()
        AccountAnalytics(
            summary = AccountSummary(Money(summary.assets_minor), Money(summary.liabilities_minor)),
            assets = accounts.asSequence()
                .filter { it.include_in_total_assets != 0L && it.balance_minor > 0 }
                .map { AccountAssetItem(it.id, it.name, it.icon_key, Money(it.balance_minor)) }
                .sortedByDescending(AccountAssetItem::balance)
                .toList(),
        )
    }

    private fun rows(scope: LedgerScope, range: DateRange): List<TransactionRowsInRange> = database.transactionQueries
        .transactionRowsInRange(
            start_inclusive = range.startInclusive.toEpochMilliseconds(),
            end_exclusive = range.endExclusive.toEpochMilliseconds(),
            ledger_filter = scope.ledgerIdOrNull(),
        )
        .executeAsList()

    private fun summary(rows: List<TransactionRowsInRange>): TransactionSummary = rows.asSequence()
        .filterNot { it.is_excluded != 0L }
        .fold(TransactionSummary(Money.Zero, Money.Zero)) { total, row ->
            when (TransactionType.valueOf(row.type)) {
                TransactionType.EXPENSE -> total.copy(expenseTotal = total.expenseTotal + Money(row.amount_minor))
                TransactionType.INCOME -> total.copy(incomeTotal = total.incomeTotal + Money(row.amount_minor))
            }
        }

    private fun trend(
        rows: List<TransactionRowsInRange>,
        range: DateRange,
        granularity: TimeGranularity,
    ): ChartData {
        val points = rows.asSequence()
            .filterNot { it.is_excluded != 0L }
            .groupBy { pointStart(Instant.fromEpochMilliseconds(it.occurred_at), granularity) }
            .map { (start, entries) ->
                val totals = summary(entries)
                ChartPoint(start, chartLabel(start, granularity), totals.expenseTotal, totals.incomeTotal)
            }
            .sortedBy(ChartPoint::start)
            .toList()
        return ChartData(range, granularity, points)
    }

    private fun ranking(
        rows: List<TransactionRowsInRange>,
        type: TransactionType,
        tagsByTransaction: Map<String, List<TransactionTag>>,
    ): List<TransactionRankingItem> = rows.asSequence()
        .filterNot { it.is_excluded != 0L }
        .filter { TransactionType.valueOf(it.type) == type }
        .sortedByDescending { it.amount_minor }
        .take(RANKING_LIMIT)
        .map { row -> TransactionRankingItem(toListItem(row), tagsByTransaction[row.id].orEmpty()) }
        .toList()

    private fun categoryShares(
        rows: List<TransactionRowsInRange>,
        type: TransactionType,
        granularity: CategoryShareGranularity,
        primaryCategoryId: String?,
    ): List<CategoryShareItem> = rows.asSequence()
        .filterNot { it.is_excluded != 0L }
        .filter { TransactionType.valueOf(it.type) == type }
        .filter { granularity != CategoryShareGranularity.SECONDARY || primaryCategoryId == null || it.primary_category_id == primaryCategoryId }
        .groupBy { row ->
            when (granularity) {
                CategoryShareGranularity.PRIMARY -> CategoryKey(
                    row.primary_category_id,
                    row.primary_category_name,
                    row.primary_category_icon_key,
                )
                CategoryShareGranularity.SECONDARY -> CategoryKey(
                    row.category_id,
                    row.category_name,
                    row.category_icon_key,
                )
            }
        }
        .map { (category, entries) ->
            CategoryShareItem(
                categoryId = category.id,
                categoryName = category.name,
                iconKey = category.iconKey,
                amount = entries.fold(Money.Zero) { total, row -> total + Money(row.amount_minor) },
            )
        }
        .sortedByDescending(CategoryShareItem::amount)
        .toList()

    private fun tagSummary(
        rows: List<TransactionRowsInRange>,
        tagsByTransaction: Map<String, List<TransactionTag>>,
    ): List<TagSummaryItem> {
        val totals = mutableMapOf<String, TagTotals>()
        rows.asSequence()
            .filterNot { it.is_excluded != 0L }
            .forEach { row ->
                tagsByTransaction[row.id].orEmpty().forEach { tag ->
                    val existing = totals[tag.id] ?: TagTotals(tag, Money.Zero, Money.Zero)
                    totals[tag.id] = when (TransactionType.valueOf(row.type)) {
                        TransactionType.EXPENSE -> existing.copy(expense = existing.expense + Money(row.amount_minor))
                        TransactionType.INCOME -> existing.copy(income = existing.income + Money(row.amount_minor))
                    }
                }
            }
        return totals.values
            .map { TagSummaryItem(it.tag, it.expense, it.income) }
            .sortedByDescending { it.expense + it.income }
    }

    private fun previousRange(range: DateRange): DateRange {
        val duration = range.endExclusive.toEpochMilliseconds() - range.startInclusive.toEpochMilliseconds()
        return DateRange(
            startInclusive = Instant.fromEpochMilliseconds(range.startInclusive.toEpochMilliseconds() - duration),
            endExclusive = range.startInclusive,
        )
    }

    private fun previousYearRange(range: DateRange): DateRange = DateRange(
        startInclusive = range.startInclusive.toLocalDateTime(CHINA_TIME_ZONE).date
            .minus(1, DateTimeUnit.YEAR)
            .atStartOfDayIn(CHINA_TIME_ZONE),
        endExclusive = range.endExclusive.toLocalDateTime(CHINA_TIME_ZONE).date
            .minus(1, DateTimeUnit.YEAR)
            .atStartOfDayIn(CHINA_TIME_ZONE),
    )

    private fun preferredGranularity(range: DateRange): TimeGranularity {
        val days = (range.endExclusive.toEpochMilliseconds() - range.startInclusive.toEpochMilliseconds()) / DAY_MILLISECONDS
        return when {
            days <= 62 -> TimeGranularity.DAY
            days <= 730 -> TimeGranularity.WEEK
            else -> TimeGranularity.MONTH
        }
    }

    private fun pointStart(instant: Instant, granularity: TimeGranularity): Instant {
        val dateTime = instant.toLocalDateTime(CHINA_TIME_ZONE)
        val date = when (granularity) {
            TimeGranularity.DAY -> dateTime.date
            TimeGranularity.WEEK -> dateTime.date.minus(
                dateTime.date.dayOfWeek.ordinal,
                DateTimeUnit.DAY,
            )
            TimeGranularity.MONTH -> LocalDate(dateTime.year, dateTime.monthNumber, 1)
        }
        return date.atStartOfDayIn(CHINA_TIME_ZONE)
    }

    private fun chartLabel(start: Instant, granularity: TimeGranularity): String {
        val date = start.toLocalDateTime(CHINA_TIME_ZONE).date
        return when (granularity) {
            TimeGranularity.DAY -> date.toString()
            TimeGranularity.WEEK -> "${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"
            TimeGranularity.MONTH -> "${date.year}-${date.monthNumber.toString().padStart(2, '0')}"
        }
    }

    private fun toListItem(row: TransactionRowsInRange) = TransactionListItem(
        id = row.id,
        ledgerId = row.ledger_id,
        ledgerName = row.ledger_name,
        accountId = row.account_id,
        accountName = row.account_name,
        categoryId = row.category_id,
        categoryName = row.category_name,
        primaryCategoryName = row.primary_category_name,
        categoryIconKey = row.primary_category_icon_key,
        amount = Money(row.amount_minor),
        type = TransactionType.valueOf(row.type),
        occurredAt = Instant.fromEpochMilliseconds(row.occurred_at),
        note = row.note,
        isExcluded = row.is_excluded != 0L,
        source = row.external_source?.let(com.omniflow.shared.domain.model.TransactionSource::valueOf),
    )

    private fun LedgerScope.ledgerIdOrNull(): String? = when (this) {
        LedgerScope.All -> null
        is LedgerScope.Single -> ledgerId
    }

    private data class CategoryKey(val id: String, val name: String, val iconKey: String?)
    private data class TagTotals(val tag: TransactionTag, val expense: Money, val income: Money)
    private data class DashboardInputs(
        val rows: List<TransactionRowsInRange>,
        val tags: Map<String, List<TransactionTag>>,
        val accounts: AccountAnalytics,
    )
    private data class AccountAnalytics(val summary: AccountSummary, val assets: List<AccountAssetItem>)

    private companion object {
        val CHINA_TIME_ZONE: TimeZone = TimeZone.currentSystemDefault()
        const val DAY_MILLISECONDS = 24 * 60 * 60 * 1000L
        const val RANKING_LIMIT = 10
    }
}
