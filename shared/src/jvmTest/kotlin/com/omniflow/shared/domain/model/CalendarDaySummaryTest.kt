package com.omniflow.shared.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalendarDaySummaryTest {
    private val summary = CalendarDaySummary(LocalDate(2026, 7, 12), Money(700), Money(1_000))

    @Test
    fun allFilterDisplaysNetAmount() {
        assertEquals(CalendarDisplayAmount(Money(300), isIncome = true), summary.displayAmount(CalendarTransactionFilter.ALL))
        assertEquals(
            CalendarDisplayAmount(Money(500), isIncome = false),
            summary.copy(expenseTotal = Money(1_500)).displayAmount(CalendarTransactionFilter.ALL),
        )
        assertNull(summary.copy(expenseTotal = Money(1_000)).displayAmount(CalendarTransactionFilter.ALL))
    }

    @Test
    fun incomeAndExpenseFiltersDisplayTheirOwnTotals() {
        assertEquals(CalendarDisplayAmount(Money(1_000), isIncome = true), summary.displayAmount(CalendarTransactionFilter.INCOME))
        assertEquals(CalendarDisplayAmount(Money(700), isIncome = false), summary.displayAmount(CalendarTransactionFilter.EXPENSE))
    }
}
