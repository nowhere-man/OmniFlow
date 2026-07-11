package com.omniflow.shared.data.repository

import com.omniflow.shared.db.OmniFlowDatabase
import com.omniflow.shared.domain.model.LedgerId
import com.omniflow.shared.domain.model.Money
import com.omniflow.shared.domain.repository.TransactionDedupeRepository

class SqlDelightTransactionDedupeRepository(
    private val database: OmniFlowDatabase,
) : TransactionDedupeRepository {
    override suspend fun hasExternalId(source: String, externalId: String): Boolean = database.transactionQueries
        .activeTransactionByExternalId(source, externalId)
        .executeAsOneOrNull() != null

    override suspend fun likelyDuplicate(
        ledgerId: LedgerId,
        amount: Money,
        occurredAtStart: Long,
        occurredAtEnd: Long,
        note: String?,
    ): Boolean {
        val normalizedNote = normalize(note)
        return database.transactionQueries.possibleDuplicates(
            ledger_id = ledgerId,
            amount_minor = amount.minor,
            occurred_at_start = occurredAtStart,
            occurred_at_end = occurredAtEnd,
        ).executeAsList().any { candidate -> notesMatch(normalizedNote, normalize(candidate.note)) }
    }

    private fun normalize(value: String?): String = value.orEmpty()
        .lowercase()
        .filterNot(Char::isWhitespace)

    private fun notesMatch(first: String, second: String): Boolean = when {
        first == second -> true
        minOf(first.length, second.length) < 4 -> false
        else -> first.contains(second) || second.contains(first)
    }
}
