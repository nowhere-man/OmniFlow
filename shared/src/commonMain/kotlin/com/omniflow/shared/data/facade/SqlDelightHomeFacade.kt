package com.omniflow.shared.data.facade

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.omniflow.shared.db.OmniFlowDatabase
import com.omniflow.shared.db.TransactionRowsInRange
import com.omniflow.shared.domain.facade.HomeFacade
import com.omniflow.shared.domain.model.CalendarDaySummary
import com.omniflow.shared.domain.model.CalendarTransactionFilter
import com.omniflow.shared.domain.model.DateRange
import com.omniflow.shared.domain.model.DayTransactionGroup
import com.omniflow.shared.domain.model.HomeQuery
import com.omniflow.shared.domain.model.HomeState
import com.omniflow.shared.domain.model.LedgerScope
import com.omniflow.shared.domain.model.Money
import com.omniflow.shared.domain.model.TransactionDetailQuery
import com.omniflow.shared.domain.model.TransactionDetailState
import com.omniflow.shared.domain.model.TransactionListItem
import com.omniflow.shared.domain.model.TransactionSummary
import com.omniflow.shared.domain.model.TransactionSource
import com.omniflow.shared.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class SqlDelightHomeFacade(
    private val database: OmniFlowDatabase,
) : HomeFacade {
    override fun observeHome(query: HomeQuery): Flow<Result<HomeState>> = observeRows(query.scope, query.month)
        .map { rows -> runCatching { homeState(query, rows) } }

    override fun observeTransactionDetails(query: TransactionDetailQuery): Flow<Result<TransactionDetailState>> =
        observeRows(query.scope, query.date).map { rows -> runCatching {
            val items = rows.map(::toListItem).filter { query.type == null || it.type == query.type }
            TransactionDetailState(
                scope = query.scope,
                date = query.date,
                type = query.type,
                summary = items.asSequence()
                    .filterNot(TransactionListItem::isExcluded)
                    .fold(TransactionSummary(Money.Zero, Money.Zero)) { total, item ->
                        when (item.type) {
                            TransactionType.EXPENSE -> total.copy(expenseTotal = total.expenseTotal + item.amount)
                            TransactionType.INCOME -> total.copy(incomeTotal = total.incomeTotal + item.amount)
                        }
                    },
                items = items,
            )
        } }

    private fun observeRows(scope: LedgerScope, range: DateRange): Flow<List<TransactionRowsInRange>> =
        database.transactionQueries.transactionRowsInRange(
            start_inclusive = range.startInclusive.toEpochMilliseconds(),
            end_exclusive = range.endExclusive.toEpochMilliseconds(),
            ledger_filter = scope.ledgerIdOrNull(),
        ).asFlow().mapToList(Dispatchers.Default)

    private fun homeState(query: HomeQuery, rows: List<TransactionRowsInRange>): HomeState {
        val items = rows.map(::toListItem)
        return HomeState(
            scope = query.scope,
            month = query.month,
            summary = summary(query.month, query.scope),
            calendar = calendar(items, query.calendarFilter),
            groups = groups(items),
        )
    }

    private fun summary(range: DateRange, scope: LedgerScope): TransactionSummary {
        val result = database.transactionQueries.summaryInRange(
            start_inclusive = range.startInclusive.toEpochMilliseconds(),
            end_exclusive = range.endExclusive.toEpochMilliseconds(),
            ledger_filter = scope.ledgerIdOrNull(),
        ).executeAsOne()
        return TransactionSummary(Money(result.expense_minor), Money(result.income_minor))
    }

    private fun calendar(
        items: List<TransactionListItem>,
        filter: CalendarTransactionFilter,
    ): List<CalendarDaySummary> = items
        .asSequence()
        .filterNot(TransactionListItem::isExcluded)
        .filter { filter == CalendarTransactionFilter.ALL || it.type.name == filter.name }
        .groupBy { it.occurredAt.toLocalDateTime(TimeZone.currentSystemDefault()).date }
        .map { (date, entries) ->
            CalendarDaySummary(
                date = date,
                expenseTotal = entries.filter { it.type == TransactionType.EXPENSE }
                    .fold(Money.Zero) { total, item -> total + item.amount },
                incomeTotal = entries.filter { it.type == TransactionType.INCOME }
                    .fold(Money.Zero) { total, item -> total + item.amount },
            )
        }
        .sortedByDescending(CalendarDaySummary::date)
        .toList()

    private fun groups(items: List<TransactionListItem>): List<DayTransactionGroup> = items
        .filterNot(TransactionListItem::isExcluded)
        .groupBy { it.occurredAt.toLocalDateTime(TimeZone.currentSystemDefault()).date }
        .map { (date, entries) ->
            DayTransactionGroup(
                date = date,
                items = entries,
                expenseTotal = entries.filter { it.type == TransactionType.EXPENSE }
                    .fold(Money.Zero) { total, item -> total + item.amount },
                incomeTotal = entries.filter { it.type == TransactionType.INCOME }
                    .fold(Money.Zero) { total, item -> total + item.amount },
            )
        }
        .sortedByDescending(DayTransactionGroup::date)

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
        source = row.external_source?.let(TransactionSource::valueOf),
    )

    private fun LedgerScope.ledgerIdOrNull(): String? = when (this) {
        LedgerScope.All -> null
        is LedgerScope.Single -> ledgerId
    }
}
