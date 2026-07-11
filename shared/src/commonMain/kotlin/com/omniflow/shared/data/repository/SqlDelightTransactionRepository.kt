package com.omniflow.shared.data.repository

import com.omniflow.shared.db.OmniFlowDatabase
import com.omniflow.shared.domain.model.Transaction
import com.omniflow.shared.domain.model.TransactionId
import com.omniflow.shared.domain.model.TransactionType
import com.omniflow.shared.domain.repository.CategoryMemoryEntry
import com.omniflow.shared.domain.repository.ImportCommitRepository
import com.omniflow.shared.domain.repository.ImportCommitTransaction
import com.omniflow.shared.domain.repository.TransactionRepository
import com.omniflow.shared.domain.util.UuidGenerator
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

class SqlDelightTransactionRepository(
    private val database: OmniFlowDatabase,
    private val ids: UuidGenerator = UuidGenerator(),
    private val now: () -> Instant = { Clock.System.now() },
) : TransactionRepository, ImportCommitRepository {
    override suspend fun activeTransaction(transactionId: TransactionId): Transaction? =
        findActiveTransaction(transactionId)

    private fun findActiveTransaction(transactionId: TransactionId): Transaction? {
        val row = database.transactionQueries.activeTransaction(transactionId).executeAsOneOrNull() ?: return null
        return Transaction(
            id = row.id,
            ledgerId = row.ledger_id,
            accountId = row.account_id,
            categoryId = row.category_id,
            amount = com.omniflow.shared.domain.model.Money(row.amount_minor),
            type = TransactionType.valueOf(row.type),
            occurredAt = Instant.fromEpochMilliseconds(row.occurred_at),
            note = row.note,
            isExcluded = row.is_excluded != 0L,
            source = row.external_source?.let(com.omniflow.shared.domain.model.TransactionSource::valueOf),
            externalId = row.external_id,
            tagIds = database.tagQueries.tagsForTransaction(transactionId).executeAsList().toSet(),
            deletedAt = row.deleted_at?.let(Instant::fromEpochMilliseconds),
        )
    }

    override suspend fun create(transaction: Transaction) {
        val timestamp = now().toEpochMilliseconds()
        database.transaction {
            createInTransaction(transaction, timestamp)
        }
    }

    override suspend fun createAll(transactions: List<Transaction>) {
        if (transactions.isEmpty()) return
        val timestamp = now().toEpochMilliseconds()
        database.transaction {
            transactions.forEach { transaction -> createInTransaction(transaction, timestamp) }
        }
    }

    override suspend fun commit(
        sessionId: String,
        transactions: List<ImportCommitTransaction>,
        categoryMemories: List<CategoryMemoryEntry>,
    ) {
        val timestamp = now().toEpochMilliseconds()
        database.transaction {
            transactions.forEach { entry ->
                val tagIds = entry.tagNames
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .distinct()
                    .map { name ->
                        database.tagQueries.activeTagIdByName(entry.transaction.ledgerId, name)
                            .executeAsOneOrNull()
                            ?: ids.next().also { tagId ->
                                database.tagQueries.insertTag(
                                    id = tagId,
                                    ledger_id = entry.transaction.ledgerId,
                                    name = name,
                                    created_at = timestamp,
                                    updated_at = timestamp,
                                )
                            }
                    }
                    .toSet()
                createInTransaction(entry.transaction.copy(tagIds = tagIds), timestamp)
            }
            categoryMemories.forEach { memory ->
                database.categoryMemoryQueries.upsertCategoryMemory(
                    ledger_id = memory.ledgerId,
                    memory_key = memory.memoryKey,
                    category_id = memory.categoryId,
                    updated_at = timestamp,
                )
            }
            database.importSessionQueries.deleteImportPreviewItems(sessionId)
            database.importSessionQueries.deleteImportSession(sessionId)
        }
    }

    override suspend fun update(transaction: Transaction) {
        val timestamp = now().toEpochMilliseconds()
        database.transaction {
            val existing = findActiveTransaction(transaction.id) ?: error("交易不存在或已删除")
            validateUpdatedTransaction(existing, transaction)
            database.transactionQueries.updateTransaction(
                ledger_id = transaction.ledgerId,
                account_id = transaction.accountId,
                category_id = transaction.categoryId,
                amount_minor = transaction.amount.minor,
                type = transaction.type.name,
                occurred_at = transaction.occurredAt.toEpochMilliseconds(),
                note = transaction.note,
                is_excluded = if (transaction.isExcluded) 1L else 0L,
                external_source = transaction.source?.name,
                external_id = transaction.externalId,
                updated_at = timestamp,
                id = transaction.id,
            )
            replaceTags(transaction.id, transaction.tagIds)
            if (existing.accountId == transaction.accountId) {
                adjustBalance(
                    accountId = transaction.accountId,
                    delta = balanceDelta(transaction) - balanceDelta(existing),
                    timestamp = timestamp,
                )
            } else {
                adjustBalance(existing.accountId, -balanceDelta(existing), timestamp)
                adjustBalance(transaction.accountId, balanceDelta(transaction), timestamp)
            }
        }
    }

    override suspend fun archive(transactionId: TransactionId) {
        val timestamp = now().toEpochMilliseconds()
        database.transaction {
            val existing = findActiveTransaction(transactionId) ?: error("交易不存在或已删除")
            adjustBalance(existing.accountId, -balanceDelta(existing), timestamp)
            database.transactionQueries.archiveTransaction(timestamp, transactionId)
        }
    }

    private fun validateNewTransaction(transaction: Transaction) {
        require(transaction.amount.minor > 0) { "金额必须大于零" }
        require(database.ledgerQueries.activeLedgerId(transaction.ledgerId).executeAsOneOrNull() != null) {
            "账本不存在或已删除"
        }
        require(database.accountQueries.activeAccountId(transaction.accountId).executeAsOneOrNull() != null) {
            "账户不存在或已删除"
        }
        require(database.categoryQueries.activeCategoryTypeForLedger(
            id = transaction.categoryId,
            ledger_id = transaction.ledgerId,
        ).executeAsOneOrNull() == transaction.type.name) {
            "分类不存在、已删除或与交易类型不一致"
        }
        transaction.tagIds.forEach { tagId ->
            require(database.tagQueries.activeTagIdForLedger(tagId, transaction.ledgerId).executeAsOneOrNull() != null) {
                "标签不存在、已删除或不属于当前账本"
            }
        }
    }

    private fun createInTransaction(transaction: Transaction, timestamp: Long) {
        validateNewTransaction(transaction)
        database.transactionQueries.insertTransaction(
            id = transaction.id,
            ledger_id = transaction.ledgerId,
            account_id = transaction.accountId,
            category_id = transaction.categoryId,
            amount_minor = transaction.amount.minor,
            type = transaction.type.name,
            occurred_at = transaction.occurredAt.toEpochMilliseconds(),
            note = transaction.note,
            is_excluded = if (transaction.isExcluded) 1L else 0L,
            external_source = transaction.source?.name,
            external_id = transaction.externalId,
            created_at = timestamp,
            updated_at = timestamp,
        )
        replaceTags(transaction.id, transaction.tagIds)
        adjustBalance(transaction.accountId, balanceDelta(transaction), timestamp)
    }

    private fun validateUpdatedTransaction(existing: Transaction, updated: Transaction) {
        require(updated.amount.minor > 0) { "金额必须大于零" }
        require(database.ledgerQueries.activeLedgerId(updated.ledgerId).executeAsOneOrNull() != null) {
            "账本不存在或已删除"
        }
        if (updated.accountId != existing.accountId) {
            require(database.accountQueries.activeAccountId(updated.accountId).executeAsOneOrNull() != null) {
                "账户不存在或已删除"
            }
        }
        if (updated.ledgerId != existing.ledgerId ||
            updated.categoryId != existing.categoryId ||
            updated.type != existing.type
        ) {
            require(database.categoryQueries.activeCategoryTypeForLedger(
                id = updated.categoryId,
                ledger_id = updated.ledgerId,
            ).executeAsOneOrNull() == updated.type.name) {
                "分类不存在、已删除或与交易类型不一致"
            }
        }

        val tagsToValidate = if (updated.ledgerId == existing.ledgerId) {
            updated.tagIds - existing.tagIds
        } else {
            updated.tagIds
        }
        tagsToValidate.forEach { tagId ->
            require(database.tagQueries.activeTagIdForLedger(tagId, updated.ledgerId).executeAsOneOrNull() != null) {
                "标签不存在、已删除或不属于当前账本"
            }
        }
    }

    private fun replaceTags(transactionId: TransactionId, tagIds: Set<String>) {
        database.tagQueries.deleteTransactionTags(transactionId)
        tagIds.forEach { tagId -> database.tagQueries.insertTransactionTag(transactionId, tagId) }
    }

    private fun balanceDelta(transaction: Transaction): Long = when (transaction.type) {
        TransactionType.INCOME -> transaction.amount.minor
        TransactionType.EXPENSE -> -transaction.amount.minor
    }

    private fun adjustBalance(accountId: String, delta: Long, timestamp: Long) {
        if (delta == 0L) return
        database.accountQueries.updateBalance(delta, timestamp, accountId)
        val balance = database.accountQueries.accountBalance(accountId).executeAsOneOrNull()
            ?: error("关联账户不存在")
        database.accountQueries.insertAccountBalanceRecord(
            id = ids.next(),
            account_id = accountId,
            date = dayStart(timestamp),
            balance_minor = balance,
            delta_minor = delta,
            created_at = timestamp,
        )
    }

    private fun dayStart(timestamp: Long): Long = Instant.fromEpochMilliseconds(timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .atStartOfDayIn(TimeZone.currentSystemDefault())
        .toEpochMilliseconds()
}
