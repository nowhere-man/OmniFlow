package com.omniflow.shared.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

enum class CalendarTransactionFilter { ALL, INCOME, EXPENSE }

data class DateRange(
    val startInclusive: Instant,
    val endExclusive: Instant,
)

data class HomeQuery(
    val scope: LedgerScope,
    val month: DateRange,
    val calendarFilter: CalendarTransactionFilter = CalendarTransactionFilter.ALL,
)

data class TransactionDetailQuery(
    val scope: LedgerScope,
    val date: DateRange,
    val type: TransactionType? = null,
)

data class TransactionListItem(
    val id: TransactionId,
    val ledgerId: LedgerId,
    val ledgerName: String,
    val accountId: AccountId,
    val accountName: String,
    val categoryId: CategoryId,
    val categoryName: String,
    val primaryCategoryName: String,
    val categoryIconKey: String?,
    val amount: Money,
    val type: TransactionType,
    val occurredAt: Instant,
    val note: String?,
    val isExcluded: Boolean,
    val source: TransactionSource?,
) {
    val categoryDisplayName: String
        get() = if (primaryCategoryName == categoryName) categoryName else "$primaryCategoryName-$categoryName"
}

data class DayTransactionGroup(
    val date: LocalDate,
    val items: List<TransactionListItem>,
    val expenseTotal: Money,
    val incomeTotal: Money,
)

data class CalendarDaySummary(
    val date: LocalDate,
    val expenseTotal: Money,
    val incomeTotal: Money,
)

data class CalendarDisplayAmount(
    val amount: Money,
    val isIncome: Boolean,
)

fun CalendarDaySummary.displayAmount(filter: CalendarTransactionFilter): CalendarDisplayAmount? {
    val display = when (filter) {
        CalendarTransactionFilter.INCOME -> CalendarDisplayAmount(incomeTotal, isIncome = true)
        CalendarTransactionFilter.EXPENSE -> CalendarDisplayAmount(expenseTotal, isIncome = false)
        CalendarTransactionFilter.ALL -> if (incomeTotal >= expenseTotal) {
            CalendarDisplayAmount(incomeTotal - expenseTotal, isIncome = true)
        } else {
            CalendarDisplayAmount(expenseTotal - incomeTotal, isIncome = false)
        }
    }
    return display.takeUnless { it.amount == Money.Zero }
}

data class TransactionSummary(
    val expenseTotal: Money,
    val incomeTotal: Money,
) {
    val netIncome: Money get() = incomeTotal - expenseTotal
}

data class HomeState(
    val scope: LedgerScope,
    val month: DateRange,
    val summary: TransactionSummary,
    val calendar: List<CalendarDaySummary>,
    val groups: List<DayTransactionGroup>,
)

data class TransactionDetailState(
    val scope: LedgerScope,
    val date: DateRange,
    val type: TransactionType?,
    val summary: TransactionSummary,
    val items: List<TransactionListItem>,
)
