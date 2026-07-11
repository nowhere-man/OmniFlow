package com.omniflow.shared.data.repository

import com.omniflow.shared.db.OmniFlowDatabase
import com.omniflow.shared.domain.model.AccountType
import com.omniflow.shared.domain.model.AppPreferenceKey
import com.omniflow.shared.domain.model.SystemDefaults
import com.omniflow.shared.domain.repository.InitialDataRepository
import com.omniflow.shared.domain.util.UuidGenerator
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class SqlDelightInitialDataRepository(
    private val database: OmniFlowDatabase,
    private val ids: UuidGenerator = UuidGenerator(),
    private val now: () -> Instant = { Clock.System.now() },
) : InitialDataRepository {
    override suspend fun seedIfNeeded() {
        if (database.appPreferenceQueries.preference(AppPreferenceKey.InitialDataSeeded)
                .executeAsOneOrNull() != null
        ) {
            return
        }

        val timestamp = now().toEpochMilliseconds()
        database.transaction {
            if (database.appPreferenceQueries.preference(AppPreferenceKey.InitialDataSeeded)
                    .executeAsOneOrNull() != null
            ) {
                return@transaction
            }

            val ledgerId = ids.next()
            database.ledgerQueries.insertLedger(ledgerId, "我的账本", null, timestamp, timestamp)
            database.appPreferenceQueries.upsertPreference(
                key = AppPreferenceKey.DefaultLedgerId,
                value = ledgerId,
                updated_at = timestamp,
            )
            SystemDefaults.categoryTemplates.forEach { category ->
                database.categoryQueries.insertCategory(
                    id = ids.next(),
                    ledger_id = ledgerId,
                    parent_id = null,
                    name = category.name,
                    icon_key = category.iconKey,
                    type = category.type.name,
                    created_at = timestamp,
                    updated_at = timestamp,
                )
            }
            SystemDefaults.accountTemplates.forEach { account ->
                database.accountQueries.insertAccount(
                    id = ids.next(),
                    name = account.name,
                    type = AccountType.CASH.name,
                    icon_key = account.iconKey,
                    card_number = null,
                    note = null,
                    balance_minor = 0,
                    include_in_total_assets = 1L,
                    created_at = timestamp,
                    updated_at = timestamp,
                )
            }
            database.appPreferenceQueries.upsertPreference(
                key = AppPreferenceKey.InitialDataSeeded,
                value = "true",
                updated_at = timestamp,
            )
        }
    }

}
