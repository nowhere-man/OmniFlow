package com.omniflow.shared.data.facade

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.omniflow.shared.db.OmniFlowDatabase
import com.omniflow.shared.db.TransactionRowsInRange
import com.omniflow.shared.db.TransactionTagsInRange
import com.omniflow.shared.domain.facade.AnalyticsFacade
import com.omniflow.shared.domain.model.AnalyticsDashboardState
import com.omniflow.shared.domain.model.AnalyticsQuery
import com.omniflow.shared.domain.model.CategoryBreakdownItem
import com.omniflow.shared.domain.model.CategoryShareItem
import com.omniflow.shared.domain.model.ChartData
import com.omniflow.shared.domain.model.ChartPoint
import com.omniflow.shared.domain.model.DateRange
import com.omniflow.shared.domain.model.LedgerScope
import com.omniflow.shared.domain.model.Money
import com.omniflow.shared.domain.model.StatementMonth
import com.omniflow.shared.domain.model.StatementTable
import com.omniflow.shared.domain.model.TagAnalysisItem
import com.omniflow.shared.domain.model.TimeGranularity
import com.omniflow.shared.domain.model.TransactionRankingItem
import com.omniflow.shared.domain.model.TransactionSummary
import com.omniflow.shared.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class SqlDelightAnalyticsFacade(
    private val database: OmniFlowDatabase,
) : AnalyticsFacade {
    override fun observeDashboard(query: AnalyticsQuery): Flow<Result<AnalyticsDashboardState>> {
        val year = query.range.startInclusive.toLocalDateTime(CHINA_TIME_ZONE).year
        val previousRange = previousRange(query.range)
        return combine(
            observeRows(query.scope, query.range),
            observeRows(query.scope, previousRange),
            observeRows(query.scope, yearRange(year)),
            observeTags(query.scope, query.range),
        ) { currentRows, previousRows, yearRows, tagRows ->
            runCatching {
                AnalyticsDashboardState(
                    query = query,
                    summary = summary(currentRows),
                    previousSummary = summary(previousRows),
                    trend = chartData(currentRows, query.range, query.trendGranularity),
                    ranking = ranking(currentRows, query.rankingType),
                    categoryBreakdowns = categoryBreakdowns(currentRows, query.categoryShareType),
                    tagAnalysis = tagAnalysis(currentRows, tagRows, query.tagAnalysisType),
                    yearStatement = statement(yearRows, year),
                )
            }
        }
    }

    override suspend fun statementTable(scope: LedgerScope, year: Int): Result<StatementTable> = runCatching {
        statement(rows(scope, yearRange(year)), year)
    }

    override suspend fun trend(
        scope: LedgerScope,
        range: DateRange,
        granularity: TimeGranularity,
    ): Result<ChartData> = runCatching {
        chartData(rows(scope, range), range, granularity)
    }

    private fun chartData(
        rows: List<TransactionRowsInRange>,
        range: DateRange,
        granularity: TimeGranularity,
    ): ChartData {
        val totalsByStart = rows.asSequence()
            .filterNot { it.is_excluded != 0L }
            .groupBy { pointStart(Instant.fromEpochMilliseconds(it.occurred_at), granularity) }
            .mapValues { summary(it.value) }
        val points = pointStarts(range, granularity).map { start ->
            val totals = totalsByStart[start] ?: TransactionSummary(Money.Zero, Money.Zero)
            ChartPoint(start, chartLabel(start, granularity), totals.expenseTotal, totals.incomeTotal)
        }
        return ChartData(range, granularity, points)
    }

    private fun observeRows(scope: LedgerScope, range: DateRange): Flow<List<TransactionRowsInRange>> =
        database.transactionQueries.transactionRowsInRange(
            start_inclusive = range.startInclusive.toEpochMilliseconds(),
            end_exclusive = range.endExclusive.toEpochMilliseconds(),
            ledger_filter = scope.ledgerIdOrNull(),
        ).asFlow().mapToList(Dispatchers.Default)

    private fun rows(scope: LedgerScope, range: DateRange): List<TransactionRowsInRange> =
        database.transactionQueries.transactionRowsInRange(
            start_inclusive = range.startInclusive.toEpochMilliseconds(),
            end_exclusive = range.endExclusive.toEpochMilliseconds(),
            ledger_filter = scope.ledgerIdOrNull(),
        ).executeAsList()

    private fun observeTags(scope: LedgerScope, range: DateRange): Flow<List<TransactionTagsInRange>> =
        database.transactionQueries.transactionTagsInRange(
            start_inclusive = range.startInclusive.toEpochMilliseconds(),
            end_exclusive = range.endExclusive.toEpochMilliseconds(),
            ledger_filter = scope.ledgerIdOrNull(),
        ).asFlow().mapToList(Dispatchers.Default)

    private fun summary(rows: List<TransactionRowsInRange>): TransactionSummary = rows.asSequence()
        .filterNot { it.is_excluded != 0L }
        .fold(TransactionSummary(Money.Zero, Money.Zero)) { total, row ->
            when (TransactionType.valueOf(row.type)) {
                TransactionType.EXPENSE -> total.copy(expenseTotal = total.expenseTotal + Money(row.amount_minor))
                TransactionType.INCOME -> total.copy(incomeTotal = total.incomeTotal + Money(row.amount_minor))
            }
        }

    private fun ranking(rows: List<TransactionRowsInRange>, type: TransactionType): List<TransactionRankingItem> = rows
        .asSequence()
        .filterNot { it.is_excluded != 0L }
        .filter { TransactionType.valueOf(it.type) == type }
        .map { row ->
            TransactionRankingItem(
                transactionId = row.id,
                ledgerId = row.ledger_id,
                ledgerName = row.ledger_name,
                accountId = row.account_id,
                accountName = row.account_name,
                categoryId = row.category_id,
                categoryName = row.category_name,
                occurredAt = Instant.fromEpochMilliseconds(row.occurred_at),
                primaryCategoryName = row.primary_category_name,
                secondaryCategoryName = row.category_name.takeIf { row.category_id != row.primary_category_id },
                iconKey = row.primary_category_icon_key,
                note = row.note,
                type = TransactionType.valueOf(row.type),
                amount = Money(row.amount_minor),
                source = row.external_source?.let(com.omniflow.shared.domain.model.TransactionSource::valueOf),
            )
        }
        .sortedWith(compareByDescending<TransactionRankingItem> { it.amount }.thenByDescending { it.occurredAt })
        .take(RANKING_LIMIT)
        .toList()

    private fun tagAnalysis(
        rows: List<TransactionRowsInRange>,
        tagRows: List<TransactionTagsInRange>,
        type: TransactionType,
    ): List<TagAnalysisItem> {
        val tagsByTransaction = tagRows.groupBy(TransactionTagsInRange::transaction_id)
        val totals = linkedMapOf<TagKey, TagAggregate>()
        rows.asSequence()
            .filterNot { it.is_excluded != 0L }
            .filter { TransactionType.valueOf(it.type) == type }
            .forEach { row ->
                val tags = tagsByTransaction[row.id]
                    ?.distinctBy(TransactionTagsInRange::tag_id)
                    ?.map { TagKey(it.tag_id, it.tag_name) }
                    .orEmpty()
                    .ifEmpty { listOf(TagKey(UNTAGGED_ID, "无标签")) }
                tags.forEach { tag ->
                    val aggregate = totals.getOrPut(tag) { TagAggregate() }
                    aggregate.amount += Money(row.amount_minor)
                    aggregate.count += 1
                }
            }
        return totals.map { (tag, aggregate) ->
            TagAnalysisItem(tag.id, tag.name, aggregate.amount, aggregate.count)
        }.sortedByDescending(TagAnalysisItem::amount)
    }

    private fun categoryBreakdowns(
        rows: List<TransactionRowsInRange>,
        type: TransactionType,
    ): List<CategoryBreakdownItem> = rows.asSequence()
        .filterNot { it.is_excluded != 0L }
        .filter { TransactionType.valueOf(it.type) == type }
        .groupBy { PrimaryCategory(it.primary_category_name) }
        .map { (primary, entries) ->
            val secondaries = entries.asSequence()
                .filter { it.category_id != it.primary_category_id }
                .groupBy { CategoryKey(it.category_name) }
                .map { (category, categoryRows) ->
                    CategoryShareItem(
                        categoryId = categoryRows.first().category_id,
                        categoryName = category.name,
                        iconKey = categoryRows.firstNotNullOfOrNull { it.category_icon_key },
                        amount = categoryRows.fold(Money.Zero) { total, row -> total + Money(row.amount_minor) },
                    )
                }
                .sortedByDescending(CategoryShareItem::amount)
                .toList()
            CategoryBreakdownItem(
                primaryCategoryId = entries.first().primary_category_id,
                primaryCategoryName = primary.name,
                iconKey = entries.firstNotNullOfOrNull { it.primary_category_icon_key },
                amount = entries.fold(Money.Zero) { total, row -> total + Money(row.amount_minor) },
                secondaryCategories = secondaries,
            )
        }
        .sortedByDescending(CategoryBreakdownItem::amount)
        .toList()

    private fun statement(rows: List<TransactionRowsInRange>, year: Int): StatementTable {
        val entries = rows.filterNot { it.is_excluded != 0L }
        val now = Clock.System.now().toLocalDateTime(CHINA_TIME_ZONE)
        val lastMonth = if (year == now.year) now.monthNumber else 12
        val months = (1..lastMonth).map { month ->
            val totals = summary(entries.filter {
                Instant.fromEpochMilliseconds(it.occurred_at).toLocalDateTime(CHINA_TIME_ZONE).monthNumber == month
            })
            StatementMonth(month, totals.expenseTotal, totals.incomeTotal)
        }
        return StatementTable(year, months, summary(entries))
    }

    private fun yearRange(year: Int) = DateRange(
        LocalDate(year, 1, 1).atStartOfDayIn(CHINA_TIME_ZONE),
        LocalDate(year + 1, 1, 1).atStartOfDayIn(CHINA_TIME_ZONE),
    )

    private fun previousRange(range: DateRange): DateRange {
        val duration = (range.endExclusive.toEpochMilliseconds() - range.startInclusive.toEpochMilliseconds()).coerceAtLeast(0)
        return DateRange(
            Instant.fromEpochMilliseconds(range.startInclusive.toEpochMilliseconds() - duration),
            range.startInclusive,
        )
    }

    private fun pointStarts(range: DateRange, granularity: TimeGranularity): List<Instant> {
        if (range.endExclusive <= range.startInclusive) return emptyList()
        return generateSequence(pointStart(range.startInclusive, granularity)) { nextPoint(it, granularity) }
            .takeWhile { it < range.endExclusive }
            .toList()
    }

    private fun nextPoint(start: Instant, granularity: TimeGranularity): Instant {
        val date = start.toLocalDateTime(CHINA_TIME_ZONE).date
        val next = when (granularity) {
            TimeGranularity.DAY -> date.plus(1, DateTimeUnit.DAY)
            TimeGranularity.WEEK -> date.plus(1, DateTimeUnit.WEEK)
            TimeGranularity.MONTH -> date.plus(1, DateTimeUnit.MONTH)
        }
        return next.atStartOfDayIn(CHINA_TIME_ZONE)
    }

    private fun pointStart(instant: Instant, granularity: TimeGranularity): Instant {
        val dateTime = instant.toLocalDateTime(CHINA_TIME_ZONE)
        val date = when (granularity) {
            TimeGranularity.DAY -> dateTime.date
            TimeGranularity.WEEK -> dateTime.date.minus(dateTime.date.dayOfWeek.ordinal, DateTimeUnit.DAY)
            TimeGranularity.MONTH -> LocalDate(dateTime.year, dateTime.monthNumber, 1)
        }
        return date.atStartOfDayIn(CHINA_TIME_ZONE)
    }

    private fun chartLabel(start: Instant, granularity: TimeGranularity): String {
        val date = start.toLocalDateTime(CHINA_TIME_ZONE).date
        return when (granularity) {
            TimeGranularity.DAY -> date.toString()
            TimeGranularity.WEEK -> date.toString()
            TimeGranularity.MONTH -> "${date.year}-${date.monthNumber.toString().padStart(2, '0')}"
        }
    }

    private fun LedgerScope.ledgerIdOrNull(): String? = when (this) {
        LedgerScope.All -> null
        is LedgerScope.Single -> ledgerId
    }

    private data class PrimaryCategory(val name: String)
    private data class CategoryKey(val name: String)
    private data class TagKey(val id: String, val name: String)
    private data class TagAggregate(var amount: Money = Money.Zero, var count: Int = 0)

    private companion object {
        val CHINA_TIME_ZONE: TimeZone = TimeZone.currentSystemDefault()
        const val RANKING_LIMIT = 10
        const val UNTAGGED_ID = "__untagged__"
    }
}
