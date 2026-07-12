package com.omniflow.android.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omniflow.shared.domain.model.CategoryShareGranularity
import com.omniflow.shared.domain.model.CategoryShareItem
import com.omniflow.shared.domain.model.ChartPoint
import com.omniflow.shared.domain.model.DateRange
import com.omniflow.shared.domain.model.LedgerScope
import com.omniflow.shared.domain.model.Money
import com.omniflow.shared.domain.model.PeriodCompareResult
import com.omniflow.shared.domain.model.StatementTable
import com.omniflow.shared.domain.model.TransactionType
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

private val AnalyticsIncomeColor = Color(0xFF55B6A7)
private val AnalyticsGoldColor = Color(0xFFD5A75A)
private val AnalyticsSkyColor = Color(0xFF69A9D0)
private val AnalyticsPurpleColor = Color(0xFF8D7AC4)
private val AnalyticsRoseColor = Color(0xFFD9809C)
private val AnalyticsSageColor = Color(0xFF86A878)
private val AnalyticsSlateColor = Color(0xFF78869B)

@Composable
internal fun AnalyticsScreen(
    state: AnalyticsUiState,
    onScope: (LedgerScope) -> Unit,
    onRangeMode: (AnalyticsRangeMode) -> Unit,
    onShiftRange: (Long) -> Unit,
    onCurrentRange: () -> Unit,
    onCustomRange: (DateRange) -> Unit,
    onRankingType: (TransactionType) -> Unit,
    onCategoryAnalysis: (TransactionType, CategoryShareGranularity) -> Unit,
    onCategoryDrillDown: (String?) -> Unit,
    onStatementTable: (Int) -> Unit,
    onDismissStatementTable: () -> Unit,
    onEditTransaction: (String) -> Unit,
    onAddTransaction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = Clock.System.now()
    val showCurrentRangeButton = state.rangeMode != AnalyticsRangeMode.CUSTOM &&
        (now < state.range.startInclusive || now >= state.range.endExclusive)
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            LedgerScopeMenu(state.scope, state.ledgers, onScope)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnalyticsRangeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.rangeMode == mode,
                        onClick = { onRangeMode(mode) },
                        label = {
                            Text(
                                mode.label,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (state.rangeMode == AnalyticsRangeMode.CUSTOM) {
            item { CustomRangeControls(state.range, onCustomRange) }
        } else {
            item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onShiftRange(-1) }) {
                    Icon(Icons.Default.ArrowBackIosNew, contentDescription = "上一个范围")
                }
                Text(
                    state.range.displayLabel(state.rangeMode),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    style = if (state.rangeMode == AnalyticsRangeMode.WEEK) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = { onShiftRange(1) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = "下一个范围")
                }
            }
        }
            if (showCurrentRangeButton) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        FilledTonalButton(
                            onClick = onCurrentRange,
                            shape = RoundedCornerShape(16.dp),
                        ) { Text(state.rangeMode.currentRangeLabel()) }
                    }
                }
            }
        }
        when {
            state.isLoading && state.dashboard == null -> item { LoadingBlock() }
            state.error != null && state.dashboard == null -> item { ErrorBlock(state.error) }
            state.dashboard != null -> {
                val dashboard = state.dashboard
                val hasTransactions = dashboard.summary.expenseTotal != Money.Zero || dashboard.summary.incomeTotal != Money.Zero
                if (!hasTransactions) {
                    item { AnalyticsEmptyState(onAddTransaction) }
                } else {
                    item {
                        SummaryRow(
                            dashboard.summary.expenseTotal,
                            dashboard.summary.incomeTotal,
                            dashboard.summary.netIncome,
                            dashboard.previousPeriod,
                        )
                    }
                    item {
                        AnalyticsCard("收支趋势", subtitle = "每个时间段的收入与支出") {
                            var showIncome by remember { mutableStateOf(true) }
                            var showExpense by remember { mutableStateOf(true) }
                            var selectedPointLabel by remember(dashboard.trend.points) {
                                mutableStateOf(dashboard.trend.points.lastOrNull()?.label)
                            }
                            ChartLegendSwitch(showIncome, showExpense) {
                                if (it == TransactionType.INCOME) showIncome = !showIncome else showExpense = !showExpense
                            }
                            if (dashboard.trend.points.isEmpty()) {
                                EmptyText("暂无趋势数据")
                            } else {
                                IncomeExpenseTrendChart(
                                    points = dashboard.trend.points,
                                    showIncome = showIncome,
                                    showExpense = showExpense,
                                    selectedLabel = selectedPointLabel,
                                    onSelected = { selectedPointLabel = it },
                                )
                            }
                            dashboard.trend.points.firstOrNull { it.label == selectedPointLabel }?.let { point ->
                                SelectedTrendPoint(point)
                            }
                            TextButton(onClick = {
                                val year = state.range.startInclusive
                                    .toLocalDateTime(ChinaTimeZone).year
                                onStatementTable(year)
                            }) { Text("查看账单表格") }
                        }
                    }
                    item {
                        AnalyticsCard("趋势对比", subtitle = "当前范围与历史同期的结构对比") {
                            ComparisonChart("环比", "上期", dashboard.previousPeriod)
                            ComparisonChart("同比", "去年同期", dashboard.yearOverYear)
                        }
                    }
                    item {
                    AnalyticsCard(if (state.rankingType == TransactionType.EXPENSE) "支出排行榜" else "收入排行榜") {
                        TypeSwitch(state.rankingType, onRankingType)
                        if (dashboard.ranking.isEmpty()) EmptyText("暂无排行数据")
                        val rankingColors = analyticsCategoryColors()
                        dashboard.ranking.forEachIndexed { index, item ->
                            val color = rankingColors[index % rankingColors.size]
                            Surface(
                                onClick = { onEditTransaction(item.transaction.id) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = color.copy(alpha = 0.08f),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        "${index + 1}",
                                        modifier = Modifier.width(24.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = color,
                                        textAlign = TextAlign.Center,
                                    )
                                    Surface(shape = RoundedCornerShape(13.dp), color = color.copy(alpha = 0.15f), modifier = Modifier.size(44.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            SvgIcon(categoryIconKey(item.transaction.categoryIconKey), Modifier.size(24.dp), tint = color)
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(item.transaction.categoryDisplayName, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            item.transaction.accountName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        item.transaction.amount.asRmb(),
                                        fontWeight = FontWeight.Bold,
                                        color = if (state.rankingType == TransactionType.EXPENSE) ExpenseColor else AnalyticsIncomeColor,
                                    )
                                }
                            }
                        }
                    }
                }
                    item {
                    AnalyticsCard("分类占比", subtitle = "支出或收入在各分类中的分布") {
                        TypeSwitch(state.categoryType) { type ->
                            onCategoryAnalysis(type, state.categoryGranularity)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CategoryShareGranularity.entries.forEach { granularity ->
                                FilterChip(
                                    selected = state.categoryGranularity == granularity,
                                    onClick = { onCategoryAnalysis(state.categoryType, granularity) },
                                    label = { Text(if (granularity == CategoryShareGranularity.PRIMARY) "一级分类" else "二级分类") },
                                )
                            }
                        }
                        if (state.primaryCategoryId != null) {
                            TextButton(onClick = { onCategoryDrillDown(null) }) { Text("返回一级分类") }
                        }
                        if (dashboard.categoryShares.isEmpty()) EmptyText("暂无分类数据")
                        if (dashboard.categoryShares.isNotEmpty()) {
                            CategoryDonutChart(dashboard.categoryShares)
                            CategoryShareLegend(
                                shares = dashboard.categoryShares,
                                allowDrillDown = state.categoryGranularity == CategoryShareGranularity.PRIMARY,
                                onDrillDown = onCategoryDrillDown,
                            )
                        }
                    }
                }
                    item {
                    AnalyticsCard("标签分析") {
                        if (dashboard.tagSummary.isEmpty()) EmptyText("暂无标签数据")
                        dashboard.tagSummary.forEach { item ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(item.tag.name)
                                Text("支出 ${item.expense.asRmb()}  收入 ${item.income.asRmb()}")
                            }
                        }
                    }
                }
                    item {
                    AnalyticsCard("账户资产分布") {
                        Text("净资产 ${dashboard.accountSummary.netAssets.asRmb()} · 资产 ${dashboard.accountSummary.assets.asRmb()} · 负债 ${dashboard.accountSummary.liabilities.asRmb()}")
                        if (dashboard.accountAssets.isEmpty()) EmptyText("暂无计入总资产的账户")
                        val maximum = dashboard.accountAssets.maxOfOrNull { it.balance.minor }?.coerceAtLeast(1) ?: 1
                        dashboard.accountAssets.forEach { account ->
                            AmountBar(account.accountName, account.balance, maximum)
                        }
                    }
                }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    state.statementTable?.let { table -> StatementTableSheet(table, onDismissStatementTable) }
}

@Composable
private fun AnalyticsEmptyState(onAddTransaction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("当前范围还没有收支", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("新增交易后，这里会生成趋势、分类和资产分析。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAddTransaction) { Text("新增交易") }
    }
}

@Composable
private fun CustomRangeControls(range: DateRange, onRange: (DateRange) -> Unit) {
    val context = LocalContext.current
    var start by remember(range) { mutableStateOf(range.startInclusive.toLocalDateTime(ChinaTimeZone).date) }
    var end by remember(range) {
        mutableStateOf(
            kotlinx.datetime.Instant.fromEpochMilliseconds(range.endExclusive.toEpochMilliseconds() - 1)
                .toLocalDateTime(ChinaTimeZone).date,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { showDatePicker(context, start) { start = it } },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("开始", style = MaterialTheme.typography.labelSmall)
                    Text(start.numericDate(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            FilledTonalButton(
                onClick = { showDatePicker(context, end) { end = it } },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("结束", style = MaterialTheme.typography.labelSmall)
                    Text(end.numericDate(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Button(
            onClick = { onRange(inclusiveDateRange(start, end)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) { Text("应用") }
    }
}

private fun showDatePicker(context: android.content.Context, date: LocalDate, onDate: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, day -> onDate(LocalDate(year, month + 1, day)) },
        date.year,
        date.monthNumber - 1,
        date.dayOfMonth,
    ).show()
}

private fun inclusiveDateRange(start: LocalDate, end: LocalDate): DateRange {
    val orderedStart = minOf(start, end)
    val orderedEnd = maxOf(start, end)
    val next = java.time.LocalDate.of(orderedEnd.year, orderedEnd.monthNumber, orderedEnd.dayOfMonth).plusDays(1)
    return DateRange(
        orderedStart.atStartOfDayIn(ChinaTimeZone),
        LocalDate(next.year, next.monthValue, next.dayOfMonth).atStartOfDayIn(ChinaTimeZone),
    )
}

private fun LocalDate.numericDate(): String = "$year-${monthNumber.toString().padStart(2, '0')}-${dayOfMonth.toString().padStart(2, '0')}"

private fun AnalyticsRangeMode.currentRangeLabel(): String = when (this) {
    AnalyticsRangeMode.WEEK -> "回到本周"
    AnalyticsRangeMode.MONTH -> "回到本月"
    AnalyticsRangeMode.YEAR -> "回到今年"
    AnalyticsRangeMode.CUSTOM -> ""
}

@Composable
private fun LedgerScopeMenu(scope: LedgerScope, ledgers: List<com.omniflow.shared.domain.model.Ledger>, onScope: (LedgerScope) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = when (scope) {
            LedgerScope.All -> "所有账本"
            is LedgerScope.Single -> ledgers.firstOrNull { it.id == scope.ledgerId }?.name ?: "账本"
    }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = currentLabel,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("所有账本") }, onClick = { expanded = false; onScope(LedgerScope.All) })
            ledgers.forEach { ledger ->
                DropdownMenuItem(text = { Text(ledger.name) }, onClick = { expanded = false; onScope(LedgerScope.Single(ledger.id)) })
            }
        }
    }
}

@Composable
private fun SummaryRow(expense: Money, income: Money, net: Money, comparison: PeriodCompareResult) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard("总支出", expense, comparison.expenseChange, ExpenseColor, false, Modifier.weight(1f))
        SummaryCard("总收入", income, comparison.incomeChange, AnalyticsIncomeColor, true, Modifier.weight(1f))
        SummaryCard(
            "总结余",
            net,
            comparison.netIncomeChange,
            if (net.minor >= 0) MaterialTheme.colorScheme.primary else ExpenseColor,
            true,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryCard(
    label: String,
    amount: Money,
    change: Money,
    color: Color,
    increaseIsPositive: Boolean,
    modifier: Modifier,
) {
    Card(
        modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                amount.asRmb(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                change.changeLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = change.semanticChangeColor(increaseIsPositive),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AnalyticsCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
private fun ChartLegendSwitch(
    showIncome: Boolean,
    showExpense: Boolean,
    onToggle: (TransactionType) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChartLegendItem("收入", AnalyticsIncomeColor, showIncome) { onToggle(TransactionType.INCOME) }
        ChartLegendItem("支出", ExpenseColor, showExpense) { onToggle(TransactionType.EXPENSE) }
    }
}

@Composable
private fun ChartLegendItem(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (selected) color else MaterialTheme.colorScheme.outline))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun IncomeExpenseTrendChart(
    points: List<ChartPoint>,
    showIncome: Boolean,
    showExpense: Boolean,
    selectedLabel: String?,
    onSelected: (String) -> Unit,
) {
    val maximum = points.maxOfOrNull { maxOf(abs(it.income.minor), abs(it.expense.minor)) }?.coerceAtLeast(1) ?: 1
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(points, key = { "${it.start}-${it.label}" }) { point ->
            val selected = point.label == selectedLabel
            Surface(
                onClick = { onSelected(point.label) },
                modifier = Modifier.width(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent,
            ) {
                Column(
                    Modifier.padding(horizontal = 5.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.height(72.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        val ratio = if (showIncome) point.income.minor.toFloat() / maximum else 0f
                        Box(
                            Modifier.width(22.dp)
                                .height((64f * ratio.coerceIn(0f, 1f)).coerceAtLeast(if (ratio > 0f) 4f else 0f).dp)
                                .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                                .background(AnalyticsIncomeColor),
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    Box(Modifier.height(52.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        val ratio = if (showExpense) point.expense.minor.toFloat() / maximum else 0f
                        Box(
                            Modifier.width(22.dp)
                                .height((44f * ratio.coerceIn(0f, 1f)).coerceAtLeast(if (ratio > 0f) 4f else 0f).dp)
                                .clip(RoundedCornerShape(bottomStart = 7.dp, bottomEnd = 7.dp))
                                .background(ExpenseColor),
                        )
                    }
                    Text(point.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SelectedTrendPoint(point: ChartPoint) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TrendValueChip(point.label, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        TrendValueChip("收入 ${point.income.asRmb()}", AnalyticsIncomeColor, Modifier.weight(1f))
        TrendValueChip("支出 ${point.expense.asRmb()}", ExpenseColor, Modifier.weight(1f))
    }
}

@Composable
private fun TrendValueChip(label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.12f)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ComparisonChart(title: String, previousLabel: String, comparison: PeriodCompareResult) {
    val metrics = listOf(
        ComparisonMetric("支出", comparison.current.expenseTotal, comparison.previous.expenseTotal, ExpenseColor, false),
        ComparisonMetric("收入", comparison.current.incomeTotal, comparison.previous.incomeTotal, AnalyticsIncomeColor, true),
        ComparisonMetric("结余", comparison.current.netIncome, comparison.previous.netIncome, MaterialTheme.colorScheme.primary, true),
    )
    val maximum = metrics.maxOfOrNull { maxOf(abs(it.current.minor), abs(it.previous.minor)) }?.coerceAtLeast(1) ?: 1
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiniLegend("当前", MaterialTheme.colorScheme.onSurface)
                    MiniLegend(previousLabel, MaterialTheme.colorScheme.outline)
                }
            }
            metrics.forEach { metric -> ComparisonMetricBars(metric, previousLabel, maximum) }
        }
    }
}

private data class ComparisonMetric(
    val label: String,
    val current: Money,
    val previous: Money,
    val color: Color,
    val increaseIsPositive: Boolean,
)

@Composable
private fun ComparisonMetricBars(metric: ComparisonMetric, previousLabel: String, maximum: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(metric.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(
                comparisonChangeLabel(metric.current, metric.previous),
                style = MaterialTheme.typography.labelMedium,
                color = (metric.current - metric.previous).semanticChangeColor(metric.increaseIsPositive),
                fontWeight = FontWeight.SemiBold,
            )
        }
        ComparisonBar("当前", metric.current, maximum, metric.color)
        ComparisonBar(previousLabel, metric.previous, maximum, metric.color.copy(alpha = 0.38f))
    }
}

@Composable
private fun ComparisonBar(label: String, amount: Money, maximum: Long, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.width(48.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f).height(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)) {
            val rawFraction = abs(amount.minor).toFloat() / maximum
            Box(
                Modifier.fillMaxWidth(rawFraction.coerceIn(0f, 1f).coerceAtLeast(if (amount.minor != 0L) 0.025f else 0f))
                    .height(9.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Text(
            amount.asRmb(),
            modifier = Modifier.width(96.dp),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MiniLegend(label: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CategoryDonutChart(shares: List<CategoryShareItem>) {
    val total = shares.sumOf { it.amount.minor }.coerceAtLeast(1)
    val colors = analyticsCategoryColors()
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val sliceAmounts = if (shares.size <= 6) {
        shares.map { it.amount.minor }
    } else {
        shares.take(5).map { it.amount.minor } + shares.drop(5).sumOf { it.amount.minor }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(214.dp)) {
            val strokeWidth = 34.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(strokeWidth, cap = StrokeCap.Butt),
            )
            var startAngle = -90f
            sliceAmounts.forEachIndexed { index, amount ->
                val sweep = amount.toFloat() / total * 360f
                val gap = minOf(2.2f, sweep * 0.18f)
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = startAngle + gap / 2f,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.4f),
                    useCenter = false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Butt),
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("合计", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Money(total).asRmb(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${shares.size} 个分类", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CategoryShareLegend(
    shares: List<CategoryShareItem>,
    allowDrillDown: Boolean,
    onDrillDown: (String?) -> Unit,
) {
    val total = shares.sumOf { it.amount.minor }.coerceAtLeast(1)
    val colors = analyticsCategoryColors()
    shares.forEachIndexed { index, share ->
        val color = colors[index % colors.size]
        val progress = (share.amount.minor.toFloat() / total).coerceIn(0f, 1f)
        Row(
            Modifier.fillMaxWidth().clickable(enabled = allowDrillDown) { onDrillDown(share.categoryId) }.padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(13.dp), color = color.copy(alpha = 0.14f), modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    SvgIcon(categoryIconKey(share.iconKey), Modifier.size(25.dp), tint = color)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(share.categoryName, fontWeight = FontWeight.SemiBold)
                    Text(formatPercentage(progress), color = color, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = color,
                    trackColor = color.copy(alpha = 0.12f),
                    strokeCap = StrokeCap.Round,
                )
                Text(share.amount.asRmb(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun analyticsCategoryColors(): List<Color> = listOf(
    MaterialTheme.colorScheme.primary,
    AnalyticsIncomeColor,
    AnalyticsGoldColor,
    AnalyticsSkyColor,
    ExpenseColor,
    AnalyticsPurpleColor,
    AnalyticsRoseColor,
    AnalyticsSageColor,
    AnalyticsSlateColor,
)

private fun Money.changeLabel(): String = when {
    minor > 0 -> "↑ ${asRmb()}"
    minor < 0 -> "↓ ${Money(-minor).asRmb()}"
    else -> "— 持平"
}

private fun Money.semanticChangeColor(increaseIsPositive: Boolean): Color {
    val improved = if (increaseIsPositive) minor >= 0 else minor <= 0
    return if (improved) AnalyticsIncomeColor else ExpenseColor
}

private fun comparisonChangeLabel(current: Money, previous: Money): String {
    if (previous.minor == 0L) return if (current.minor == 0L) "持平" else "新增"
    val percentage = (current.minor - previous.minor).toDouble() / abs(previous.minor.toDouble()) * 100
    val arrow = if (percentage >= 0) "↑" else "↓"
    return "$arrow${abs(percentage).roundToInt()}%"
}

private fun formatPercentage(value: Float): String {
    val tenths = (value * 1000).roundToInt()
    return "${tenths / 10}.${tenths % 10}%"
}

@Composable
private fun AmountBar(label: String, amount: Money, maximum: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(amount.asRmb())
    }
    LinearProgressIndicator(
        progress = { (amount.minor.toFloat() / maximum.toFloat()).coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TypeSwitch(selected: TransactionType, onSelected: (TransactionType) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(3.dp)) {
            TransactionType.entries.forEach { type ->
                val active = selected == type
                Surface(
                    onClick = { onSelected(type) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(13.dp),
                    color = if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
                ) {
                    Text(
                        if (type == TransactionType.EXPENSE) "支出" else "收入",
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) {
                            if (type == TransactionType.EXPENSE) ExpenseColor else AnalyticsIncomeColor
                        } else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatementTableSheet(table: StatementTable, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text("${table.year} 年账单", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("全年")
                    Text(table.total.incomeTotal.asRmb())
                    Text(table.total.expenseTotal.asRmb())
                    Text(table.total.netIncome.asRmb())
                }
            }
            items(table.months) { month ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${month.month}月")
                    Text(month.income.asRmb())
                    Text(month.expense.asRmb())
                    Text(month.netIncome.asRmb())
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable private fun LoadingBlock() = Text("加载中…", Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center)
@Composable private fun ErrorBlock(message: String) = Text(message, color = MaterialTheme.colorScheme.error)
@Composable private fun EmptyText(message: String) = Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)

private val AnalyticsRangeMode.label: String
    get() = when (this) {
        AnalyticsRangeMode.WEEK -> "周"
        AnalyticsRangeMode.MONTH -> "月"
        AnalyticsRangeMode.YEAR -> "年"
        AnalyticsRangeMode.CUSTOM -> "范围"
    }
