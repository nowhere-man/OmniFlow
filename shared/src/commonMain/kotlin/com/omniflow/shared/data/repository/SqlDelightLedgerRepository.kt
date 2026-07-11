package com.omniflow.shared.data.repository

import com.omniflow.shared.db.OmniFlowDatabase
import com.omniflow.shared.domain.model.AppPreferenceKey
import com.omniflow.shared.domain.model.Ledger
import com.omniflow.shared.domain.model.LedgerId
import com.omniflow.shared.domain.model.SystemDefaults
import com.omniflow.shared.domain.repository.LedgerRepository
import com.omniflow.shared.domain.util.UuidGenerator
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

class SqlDelightLedgerRepository(
    private val database: OmniFlowDatabase,
    private val ids: UuidGenerator = UuidGenerator(),
    private val now: () -> Instant = { Clock.System.now() },
) : LedgerRepository {
    override suspend fun activeLedgers(): List<Ledger> = database.ledgerQueries.activeLedgers()
        .executeAsList()
        .map { Ledger(id = it.id, name = it.name, coverKey = it.cover_key) }

    override suspend fun create(ledger: Ledger) {
        require(ledger.name.isNotBlank()) { "账本名称不能为空" }
        val timestamp = now().toEpochMilliseconds()
        database.transaction {
            database.ledgerQueries.insertLedger(
                id = ledger.id,
                name = ledger.name.trim(),
                cover_key = ledger.coverKey,
                created_at = timestamp,
                updated_at = timestamp,
            )
            SystemDefaults.categoryTemplates.forEach { category ->
                database.categoryQueries.insertCategory(
                    id = ids.next(),
                    ledger_id = ledger.id,
                    parent_id = null,
                    name = category.name,
                    icon_key = category.iconKey,
                    type = category.type.name,
                    created_at = timestamp,
                    updated_at = timestamp,
                )
            }
        }
    }

    override suspend fun update(ledger: Ledger) {
        require(ledger.name.isNotBlank()) { "账本名称不能为空" }
        require(database.ledgerQueries.activeLedgerId(ledger.id).executeAsOneOrNull() != null) {
            "账本不存在或已删除"
        }
        database.ledgerQueries.updateLedger(
            name = ledger.name.trim(),
            cover_key = ledger.coverKey,
            updated_at = now().toEpochMilliseconds(),
            id = ledger.id,
        )
    }

    override suspend fun archive(ledgerId: LedgerId) {
        val timestamp = now().toEpochMilliseconds()
        database.transaction {
            database.transactionQueries.accountBalanceDeltasForLedger(ledgerId).executeAsList().forEach { row ->
                val delta = -row.balance_delta
                if (delta != 0L) {
                    database.accountQueries.updateBalance(delta, timestamp, row.account_id)
                    val balance = database.accountQueries.accountBalance(row.account_id).executeAsOne()
                    database.accountQueries.insertAccountBalanceRecord(
                        id = ids.next(),
                        account_id = row.account_id,
                        date = dayStart(timestamp),
                        balance_minor = balance,
                        delta_minor = delta,
                        created_at = timestamp,
                    )
                }
            }
            database.ledgerQueries.archiveLedger(timestamp, ledgerId)
            database.ledgerQueries.archiveCategoriesForLedger(timestamp, ledgerId)
            database.ledgerQueries.archiveTagsForLedger(timestamp, ledgerId)
            database.ledgerQueries.archiveRulesForLedger(timestamp, ledgerId)
            database.ledgerQueries.archiveTransactionsForLedger(timestamp, ledgerId)
            if (database.appPreferenceQueries.preference(AppPreferenceKey.DefaultLedgerId)
                    .executeAsOneOrNull() == ledgerId
            ) {
                database.appPreferenceQueries.deletePreference(AppPreferenceKey.DefaultLedgerId)
            }
        }
    }

    override suspend fun defaultLedgerId(): LedgerId? = database.appPreferenceQueries
        .preference(AppPreferenceKey.DefaultLedgerId)
        .executeAsOneOrNull()

    override suspend fun setDefaultLedgerId(ledgerId: LedgerId?) {
        if (ledgerId == null) {
            database.appPreferenceQueries.deletePreference(AppPreferenceKey.DefaultLedgerId)
            return
        }

        require(database.ledgerQueries.activeLedgerId(ledgerId).executeAsOneOrNull() != null) {
            "默认账本不存在或已删除"
        }
        database.appPreferenceQueries.upsertPreference(
            key = AppPreferenceKey.DefaultLedgerId,
            value = ledgerId,
            updated_at = now().toEpochMilliseconds(),
        )
    }

    private fun dayStart(timestamp: Long): Long = Instant.fromEpochMilliseconds(timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .atStartOfDayIn(TimeZone.currentSystemDefault())
        .toEpochMilliseconds()
}
