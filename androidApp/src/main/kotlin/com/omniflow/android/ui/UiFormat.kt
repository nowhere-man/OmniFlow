package com.omniflow.android.ui

import androidx.compose.ui.graphics.Color
import com.omniflow.shared.domain.model.DateRange
import com.omniflow.shared.domain.model.Money
import java.time.LocalDate as JavaLocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime

internal val ExpenseColor = Color(0xFFE87970)

internal fun Money.asRmb(): String {
    val absolute = kotlin.math.abs(minor)
    val prefix = if (minor < 0) "-" else ""
    return "$prefix¥${absolute / 100}.${(absolute % 100).toString().padStart(2, '0')}"
}

internal fun Money.asCompactRmb(): String = "¥${minor / 100}"

internal fun LocalDate.displayName(): String {
    val day = JavaLocalDate.of(year, monthNumber, dayOfMonth)
    return "${monthNumber}月${dayOfMonth}日 ${day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)}"
}

internal fun Instant.displayTime(): String = toLocalDateTime(ChinaTimeZone).time.toString().take(5)

internal fun DateRange.displayLabel(mode: AnalyticsRangeMode): String {
    val start = startInclusive.toLocalDateTime(ChinaTimeZone).date
    val end = Instant.fromEpochMilliseconds(endExclusive.toEpochMilliseconds() - 1)
        .toLocalDateTime(ChinaTimeZone).date
    return when (mode) {
        AnalyticsRangeMode.WEEK -> "${start.monthDay()} 至 ${end.monthDay()}"
        AnalyticsRangeMode.MONTH -> "${start.year}-${start.monthNumber.twoDigits()}"
        AnalyticsRangeMode.YEAR -> start.year.toString()
        AnalyticsRangeMode.CUSTOM -> "${start.fullDate()} 至 ${end.fullDate()}"
    }
}

private fun LocalDate.monthDay(): String = "${monthNumber.twoDigits()}-${dayOfMonth.twoDigits()}"

private fun LocalDate.fullDate(): String = "$year-${monthNumber.twoDigits()}-${dayOfMonth.twoDigits()}"

private fun Int.twoDigits(): String = toString().padStart(2, '0')
