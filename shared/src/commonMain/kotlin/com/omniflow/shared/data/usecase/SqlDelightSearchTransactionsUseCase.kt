package com.omniflow.shared.data.usecase

import com.omniflow.shared.db.OmniFlowDatabase
import com.omniflow.shared.db.TransactionRowsInRange
import com.omniflow.shared.domain.model.LedgerScope
import com.omniflow.shared.domain.model.Money
import com.omniflow.shared.domain.model.SearchResult
import com.omniflow.shared.domain.model.SearchTransactionItem
import com.omniflow.shared.domain.model.TransactionListItem
import com.omniflow.shared.domain.model.TransactionSearchQuery
import com.omniflow.shared.domain.model.TransactionSummary
import com.omniflow.shared.domain.model.TransactionTag
import com.omniflow.shared.domain.model.TransactionType
import com.omniflow.shared.domain.usecase.SearchTransactionsUseCase
import kotlinx.datetime.Instant

class SqlDelightSearchTransactionsUseCase(
    private val database: OmniFlowDatabase,
) : SearchTransactionsUseCase {
    override suspend fun invoke(query: TransactionSearchQuery): Result<SearchResult> = runCatching {
        if (!query.hasFilters && query.scope == LedgerScope.All) {
            return@runCatching SearchResult(emptyList(), TransactionSummary(Money.Zero, Money.Zero))
        }

        val range = query.dateRange ?: ENTIRE_RANGE
        val rows = database.transactionQueries.transactionRowsInRange(
            start_inclusive = range.startInclusive.toEpochMilliseconds(),
            end_exclusive = range.endExclusive.toEpochMilliseconds(),
            ledger_filter = query.scope.ledgerIdOrNull(),
        ).executeAsList()
        val tagsByTransaction = database.transactionQueries.transactionTagsInRange(
            start_inclusive = range.startInclusive.toEpochMilliseconds(),
            end_exclusive = range.endExclusive.toEpochMilliseconds(),
            ledger_filter = query.scope.ledgerIdOrNull(),
        ).executeAsList()
            .groupBy { it.transaction_id }
            .mapValues { (_, tags) -> tags.map { TransactionTag(it.tag_id, it.tag_name) } }
        val items = rows.asSequence()
            .map { row -> toSearchItem(row, tagsByTransaction[row.id].orEmpty()) }
            .filter { item -> matches(query, item) }
            .toList()
        SearchResult(
            items = items,
            summary = summary(items),
        )
    }

    private fun matches(query: TransactionSearchQuery, item: SearchTransactionItem): Boolean {
        val transaction = item.transaction
        if (query.type != null && transaction.type != query.type) return false
        if (query.primaryCategoryId != null && item.primaryCategoryId != query.primaryCategoryId) return false
        if (query.secondaryCategoryId != null && transaction.categoryId != query.secondaryCategoryId) return false
        if (query.tagId != null && item.tags.none { it.id == query.tagId }) return false
        if (query.accountId != null && transaction.accountId != query.accountId) return false
        if (!matchesAmount(query, transaction.amount)) return false
        return query.keyword.trim().takeIf(String::isNotEmpty)?.let { keyword ->
            val normalized = keyword.lowercase()
            listOf(
                transaction.note.orEmpty(),
                transaction.categoryName,
                item.primaryCategoryName,
                transaction.accountName,
                *item.tags.map(TransactionTag::name).toTypedArray(),
            ).any { it.lowercase().contains(normalized) }
        } ?: true
    }

    private fun matchesAmount(query: TransactionSearchQuery, amount: Money): Boolean = when {
        query.amount.exact != null -> amount == query.amount.exact
        query.amount.minimum != null && amount < query.amount.minimum -> false
        query.amount.maximum != null && amount > query.amount.maximum -> false
        else -> true
    }

    private fun summary(items: List<SearchTransactionItem>): TransactionSummary = items.asSequence()
        .filterNot { it.transaction.isExcluded }
        .fold(TransactionSummary(Money.Zero, Money.Zero)) { total, item ->
            when (item.transaction.type) {
                TransactionType.EXPENSE -> total.copy(expenseTotal = total.expenseTotal + item.transaction.amount)
                TransactionType.INCOME -> total.copy(incomeTotal = total.incomeTotal + item.transaction.amount)
            }
        }

    private fun toSearchItem(row: TransactionRowsInRange, tags: List<TransactionTag>) = SearchTransactionItem(
        transaction = TransactionListItem(
            id = row.id,
            ledgerId = row.ledger_id,
            ledgerName = row.ledger_name,
            accountId = row.account_id,
            accountName = row.account_name,
            categoryId = row.category_id,
            categoryName = row.category_name,
            primaryCategoryName = row.primary_category_name,
            categoryIconKey = row.category_icon_key,
            amount = Money(row.amount_minor),
            type = TransactionType.valueOf(row.type),
            occurredAt = Instant.fromEpochMilliseconds(row.occurred_at),
            note = row.note,
            isExcluded = row.is_excluded != 0L,
        ),
        primaryCategoryId = row.primary_category_id,
        primaryCategoryName = row.primary_category_name,
        tags = tags,
    )

    private fun LedgerScope.ledgerIdOrNull(): String? = when (this) {
        LedgerScope.All -> null
        is LedgerScope.Single -> ledgerId
    }

    private companion object {
        val ENTIRE_RANGE = com.omniflow.shared.domain.model.DateRange(
            startInclusive = Instant.fromEpochMilliseconds(0),
            endExclusive = Instant.fromEpochMilliseconds(Long.MAX_VALUE),
        )
    }
}
