package com.omniflow.shared.data

import com.omniflow.shared.data.facade.SqlDelightManagementFacade
import com.omniflow.shared.data.local.createJvmDatabase
import com.omniflow.shared.data.repository.SqlDelightLedgerRepository
import com.omniflow.shared.data.repository.SqlDelightCategoryRepository
import com.omniflow.shared.data.repository.SqlDelightAccountRepository
import com.omniflow.shared.data.repository.SqlDelightRuleRepository
import com.omniflow.shared.domain.model.Rule
import com.omniflow.shared.domain.model.Category
import com.omniflow.shared.domain.model.Account
import com.omniflow.shared.domain.model.AccountType
import com.omniflow.shared.domain.model.Money
import com.omniflow.shared.domain.model.RuleActionType
import com.omniflow.shared.domain.model.RuleConditionType
import com.omniflow.shared.domain.model.TransactionType
import com.omniflow.shared.domain.usecase.CreateRuleUseCase
import com.omniflow.shared.domain.usecase.ReorderRulesUseCase
import com.omniflow.shared.domain.usecase.ReorderPrimaryCategoriesUseCase
import com.omniflow.shared.domain.usecase.DeleteLedgerUseCase
import com.omniflow.shared.domain.usecase.SetDefaultLedgerUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqlDelightManagementFacadeTest {
    @Test
    fun persistsPrimaryCategoryOrder() = runBlocking {
        val database = createJvmDatabase()
        database.ledgerQueries.insertLedger("ledger", "日常", null, 1, 1)
        val categories = SqlDelightCategoryRepository(database)
        val facade = SqlDelightManagementFacade(database)
        categories.create(Category("food", "ledger", null, "餐饮", "utensils", TransactionType.EXPENSE))
        categories.create(Category("travel", "ledger", null, "出行", "bus", TransactionType.EXPENSE))

        ReorderPrimaryCategoriesUseCase(categories)(
            "ledger",
            TransactionType.EXPENSE,
            listOf("travel", "food"),
        ).getOrThrow()

        assertEquals(
            listOf("travel", "food"),
            facade.observeCategories("ledger").first().getOrThrow().map(Category::id),
        )
    }

    @Test
    fun exposesDefaultLedgerAndLedgerScopedRules() = runBlocking {
        val database = createJvmDatabase()
        database.ledgerQueries.insertLedger("ledger", "日常", null, 1, 1)
        val ledgers = SqlDelightLedgerRepository(database)
        val rules = SqlDelightRuleRepository(database)
        val facade = SqlDelightManagementFacade(database)

        assertNull(facade.observeDefaultLedgerId().first().getOrThrow())
        SetDefaultLedgerUseCase(ledgers)("ledger").getOrThrow()
        assertEquals("ledger", facade.observeDefaultLedgerId().first().getOrThrow())

        CreateRuleUseCase(rules)(
            Rule(
                id = "rule",
                ledgerId = "ledger",
                name = "餐饮",
                conditionType = RuleConditionType.NOTE_CONTAINS,
                conditionValue = "餐厅",
                actionType = RuleActionType.SET_EXCLUDED,
                actionValue = "true",
                priority = 1,
            ),
        ).getOrThrow()
        CreateRuleUseCase(rules)(
            Rule("rule-2", "ledger", "工资", RuleConditionType.NOTE_CONTAINS, "工资", RuleActionType.SET_EXCLUDED, "true", 2),
        ).getOrThrow()
        ReorderRulesUseCase(rules)("ledger", listOf("rule-2", "rule")).getOrThrow()
        assertEquals(listOf("rule-2", "rule"), facade.observeRules("ledger").first().getOrThrow().map(Rule::id))

        DeleteLedgerUseCase(ledgers)("ledger").getOrThrow()
        assertNull(facade.observeDefaultLedgerId().first().getOrThrow())
        assertEquals(emptyList(), facade.observeRules("ledger").first().getOrThrow())
    }

    @Test
    fun updatingAccountPersistsProfileAndBalanceTogether() = runBlocking {
        val database = createJvmDatabase()
        val accounts = SqlDelightAccountRepository(database)
        accounts.create(Account("account", "现金", AccountType.CASH, "wallet", balance = Money(100), includeInTotalAssets = true))

        accounts.update(Account("account", "备用金", AccountType.CASH, "banknote", balance = Money(350), includeInTotalAssets = true))

        val account = accounts.activeAccounts().single()
        assertEquals("备用金", account.name)
        assertEquals(Money(350), account.balance)
        assertEquals(2, database.backupQueries.allAccountBalanceRecordsForBackup().executeAsList().count { it.account_id == "account" })
    }

    @Test
    fun deletingCategoryArchivesRulesThatReferenceIt() = runBlocking {
        val database = createJvmDatabase()
        database.ledgerQueries.insertLedger("ledger", "日常", null, 1, 1)
        val categories = SqlDelightCategoryRepository(database)
        val rules = SqlDelightRuleRepository(database)
        categories.create(Category("food", "ledger", null, "餐饮", "utensils", TransactionType.EXPENSE))
        rules.create(
            Rule("rule", "ledger", "餐饮", RuleConditionType.NOTE_CONTAINS, "餐厅", RuleActionType.SET_CATEGORY, "food", 0),
        )

        categories.archive("food")

        assertEquals(emptyList(), rules.activeRules("ledger"))
    }
}
