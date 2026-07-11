import Charts
import SwiftUI

struct AnalyticsView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.appThemeColor) private var themeColor
    @State private var mode = RangeMode.week
    @State private var anchor = Date()
    @State private var customStart = Date()
    @State private var customEnd = Date()
    @State private var showIncome = true
    @State private var showExpense = true

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HStack(spacing: 10) {
                    Menu {
                        Button("所有账本") { store.selectAnalyticsLedger(nil) }
                        ForEach(store.ledgers) { ledger in Button(ledger.name) { store.selectAnalyticsLedger(ledger.id) } }
                    } label: {
                        Image(systemName: "books.vertical")
                    }
                    .accessibilityLabel("选择统计账本")
                    Picker("范围", selection: $mode) {
                        ForEach(RangeMode.allCases) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)
                }
                if mode == .custom {
                    HStack(spacing: 8) {
                        compactDatePicker("开始", selection: $customStart)
                        compactDatePicker("结束", selection: $customEnd)
                        Button("应用", action: refresh).buttonStyle(.borderedProminent)
                    }
                } else {
                    HStack {
                        Button { shift(-1) } label: { Image(systemName: "chevron.left") }
                        Spacer()
                        Text(rangeLabel)
                            .font(.headline)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                        Spacer()
                        Button { shift(1) } label: { Image(systemName: "chevron.right") }
                    }
                    if !range.contains(Date()) {
                        Button(currentRangeTitle) { anchor = Date(); refresh() }
                            .buttonStyle(.bordered)
                            .frame(maxWidth: .infinity, alignment: .center)
                    }
                }
                if store.analyticsExpenseMinor == 0 && store.analyticsIncomeMinor == 0 {
                    EmptyStateView(title: "当前范围还没有收支", systemImage: "chart.bar", detail: "新增交易后即可查看趋势、分类和资产分析", actionTitle: "新增交易") {
                        store.startNewTransaction()
                    }
                } else {
                    LiquidGlassContainer(spacing: 12) {
                        HStack(spacing: 12) {
                            SummaryCard(title: "总支出", value: store.analyticsExpenseMinor.rmb)
                            SummaryCard(title: "总收入", value: store.analyticsIncomeMinor.rmb)
                            SummaryCard(title: "总结余", value: (store.analyticsIncomeMinor - store.analyticsExpenseMinor).rmb)
                        }
                    }
                    Button("查看年度账单") { store.loadAnalyticsStatement(year: Calendar.current.component(.year, from: anchor)) }
                    VStack(alignment: .leading, spacing: 10) {
                    Picker("排行榜", selection: $store.analyticsRankingType) {
                        ForEach(EntryType.allCases) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    HStack {
                        Picker("分类收支", selection: $store.analyticsCategoryType) {
                            ForEach(EntryType.allCases) { Text($0.label).tag($0) }
                        }
                        Picker("分类层级", selection: $store.analyticsCategoryGranularity) {
                            ForEach(AppleCategoryGranularity.allCases) { Text($0.label).tag($0) }
                        }
                    }
                }
                    Group {
                    AnalyticsCard(title: "收支趋势") {
                        HStack {
                            Toggle("收入", isOn: $showIncome).toggleStyle(.button)
                            Toggle("支出", isOn: $showExpense).toggleStyle(.button)
                        }
                        TrendChart(points: store.analyticsPoints, showIncome: showIncome, showExpense: showExpense)
                    }
                    AnalyticsCard(title: "同环比") {
                        ComparisonPanel(
                            title: "上一周期",
                            currentExpense: store.analyticsExpenseMinor,
                            currentIncome: store.analyticsIncomeMinor,
                            previousExpense: store.analyticsPreviousExpenseMinor,
                            previousIncome: store.analyticsPreviousIncomeMinor
                        )
                        ComparisonPanel(
                            title: "去年同期",
                            currentExpense: store.analyticsExpenseMinor,
                            currentIncome: store.analyticsIncomeMinor,
                            previousExpense: store.analyticsYearPreviousExpenseMinor,
                            previousIncome: store.analyticsYearPreviousIncomeMinor
                        )
                    }
                    AnalyticsCard(title: "收支排行榜") {
                        ForEach(store.analyticsRanking) { item in
                            Button { store.showTransactionDetail(item) } label: {
                                HStack(spacing: 10) {
                                    SVGIconView(
                                        key: categoryIconAssetKey(item.categoryIconKey ?? "category"),
                                        size: 24,
                                        tint: (AppThemeColor(rawValue: store.themeColor) ?? .lavender).cssColor(for: colorScheme)
                                    )
                                    Text(item.categoryDisplayName)
                                    Spacer()
                                    Text(item.amountMinor.rmb).fontWeight(.semibold)
                                }
                            }
                            .buttonStyle(.plain)
                            Divider()
                        }
                    }
                    AnalyticsCard(title: "分类占比") {
                        if store.analyticsPrimaryCategoryID != nil {
                            Button("返回一级分类") {
                                store.analyticsPrimaryCategoryID = nil
                                store.analyticsCategoryGranularity = .primary
                            }
                        }
                        let total = max(store.analyticsCategories.map(\.amount).reduce(0, +), 1)
                        DonutChart(items: store.analyticsCategories)
                            .frame(height: 190)
                        ForEach(store.analyticsCategories.indices, id: \.self) { index in
                            let item = store.analyticsCategories[index]
                            if store.analyticsCategoryGranularity == .primary {
                                Button {
                                    store.analyticsPrimaryCategoryID = item.id
                                    store.analyticsCategoryGranularity = .secondary
                                } label: {
                                    CategoryShareRow(item: item, maximum: total, color: analyticsPalette[index % analyticsPalette.count])
                                }
                                .buttonStyle(.plain)
                            } else {
                                CategoryShareRow(item: item, maximum: total, color: analyticsPalette[index % analyticsPalette.count])
                            }
                        }
                    }
                }
                    Group {
                    AnalyticsCard(title: "标签分析") {
                        ForEach(store.analyticsTags) { tag in
                            HStack { Text(tag.name); Spacer(); Text("支出 \(tag.expense.rmb) · 收入 \(tag.income.rmb)") }
                        }
                    }
                    AnalyticsCard(title: "全局实时资产") {
                        let summary = store.analyticsAccountSummary
                        Text("不随当前账本和统计时间范围变化").font(.caption).foregroundStyle(.secondary)
                        Text("净资产 \((summary.assets - summary.liabilities).rmb) · 资产 \(summary.assets.rmb) · 负债 \(summary.liabilities.rmb)")
                        ForEach(store.analyticsAccountAssets) { account in
                            HStack { Text(account.name); Spacer(); Text(account.balance.rmb) }
                        }
                    }
                    }
                }
            }
            .padding()
        }
        .navigationTitle("统计")
        .onAppear(perform: refresh)
        .onChange(of: mode) { _ in refresh() }
        .onChange(of: store.analyticsLedgerID) { _ in refresh() }
        .onChange(of: store.analyticsRankingType) { _ in refresh() }
        .onChange(of: store.analyticsCategoryType) { _ in store.analyticsPrimaryCategoryID = nil; refresh() }
        .onChange(of: store.analyticsCategoryGranularity) { value in
            if value == .primary { store.analyticsPrimaryCategoryID = nil }
            refresh()
        }
        .sheet(item: $store.analyticsStatement) { StatementTableView(table: $0) }
    }

    private var range: DateInterval {
        let calendar = Calendar.current
        switch mode {
        case .week: return calendar.dateInterval(of: .weekOfYear, for: anchor) ?? DateInterval(start: anchor, duration: 7 * 86_400)
        case .month: return calendar.dateInterval(of: .month, for: anchor) ?? DateInterval(start: anchor, duration: 30 * 86_400)
        case .year: return calendar.dateInterval(of: .year, for: anchor) ?? DateInterval(start: anchor, duration: 365 * 86_400)
        case .custom:
            let start = min(customStart, customEnd)
            return DateInterval(start: start, end: Calendar.current.date(byAdding: .day, value: 1, to: max(customStart, customEnd)) ?? max(customStart, customEnd))
        }
    }

    private func shift(_ value: Int) {
        let component: Calendar.Component = mode == .week ? .weekOfYear : mode == .year ? .year : .month
        anchor = Calendar.current.date(byAdding: component, value: value, to: anchor) ?? anchor
        refresh()
    }

    private func refresh() { store.observeAnalytics(start: range.start, end: range.end, ledgerID: store.analyticsLedgerID) }

    private var rangeLabel: String {
        let end = Calendar.current.date(byAdding: .day, value: -1, to: range.end) ?? range.end
        switch mode {
        case .week: return "\(format(range.start, "MM-dd")) 至 \(format(end, "MM-dd"))"
        case .month: return format(range.start, "yyyy-MM")
        case .year: return format(range.start, "yyyy")
        case .custom: return "\(format(range.start, "yyyy-MM-dd")) 至 \(format(end, "yyyy-MM-dd"))"
        }
    }

    private var currentRangeTitle: String {
        switch mode { case .week: return "回到本周"; case .month: return "回到本月"; case .year: return "回到今年"; case .custom: return "" }
    }

    private func compactDatePicker(_ label: String, selection: Binding<Date>) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            DatePicker(label, selection: selection, displayedComponents: .date).labelsHidden()
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .liquidGlassSurface(cornerRadius: 12, interactive: true)
    }

    private func format(_ date: Date, _ pattern: String) -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = pattern
        return formatter.string(from: date)
    }
}

private struct StatementTableView: View {
    let table: StatementTableUI

    var body: some View {
        NavigationStack {
            List {
                Section("全年") {
                    statementRow("合计", income: table.incomeMinor, expense: table.expenseMinor)
                }
                Section("月份") {
                    ForEach(table.months) { month in
                        statementRow("\(month.month) 月", income: month.incomeMinor, expense: month.expenseMinor)
                    }
                }
            }
            .navigationTitle("\(table.year) 年账单")
        }
        .frame(minWidth: 420, minHeight: 520)
    }

    private func statementRow(_ label: String, income: Int64, expense: Int64) -> some View {
        HStack {
            Text(label)
            Spacer()
            Text(income.rmb).foregroundStyle(.secondary)
            Text(expense.rmb).foregroundStyle(.secondary)
            Text((income - expense).rmb)
        }
    }
}

private enum RangeMode: String, CaseIterable, Identifiable {
    case week, month, year, custom
    var id: String { rawValue }
    var label: String { switch self { case .week: return "周"; case .month: return "月"; case .year: return "年"; case .custom: return "范围" } }
}

private struct AnalyticsCard<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title).font(.title2.bold())
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .liquidGlassSurface(cornerRadius: 18)
    }
}

private let analyticsPalette: [Color] = [.purple, .orange, .teal, .pink, .green, .indigo, .brown]

private struct TrendChart: View {
    @Environment(\.appThemeColor) private var themeColor
    let points: [AnalyticsPointUI]
    let showIncome: Bool
    let showExpense: Bool

    var body: some View {
        Chart {
            ForEach(points) { point in
                if showIncome {
                    BarMark(
                        x: .value("日期", point.label),
                        y: .value("收入", Double(point.income) / 100)
                    )
                    .foregroundStyle(themeColor)
                }
                if showExpense {
                    BarMark(
                        x: .value("日期", point.label),
                        y: .value("支出", -Double(point.expense) / 100)
                    )
                    .foregroundStyle(Color.red.opacity(0.78))
                }
            }
            RuleMark(y: .value("零", 0.0)).foregroundStyle(Color.secondary.opacity(0.5))
        }
        .chartLegend(.hidden)
        .frame(height: 220)
        HStack(spacing: 16) {
            chartLegend("收入", color: themeColor)
            chartLegend("支出", color: .red)
        }
    }

    private func chartLegend(_ label: String, color: Color) -> some View {
        HStack(spacing: 5) {
            Circle().fill(color).frame(width: 8, height: 8)
            Text(label).font(.caption).foregroundStyle(.secondary)
        }
    }
}

private struct ComparisonMetric: Identifiable {
    let label: String
    let current: Int64
    let previous: Int64
    let expense: Bool
    var id: String { label }
}

private struct ComparisonPanel: View {
    let title: String
    let currentExpense: Int64
    let currentIncome: Int64
    let previousExpense: Int64
    let previousIncome: Int64

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(title).font(.headline)
                Spacer()
                chartLegend("当前", color: .primary)
                chartLegend(title, color: .secondary)
            }
            ForEach(metrics) { metric in ComparisonMetricRow(metric: metric) }
        }
        .padding(12)
        .liquidGlassSurface(cornerRadius: 16)
    }

    private var metrics: [ComparisonMetric] {
        [
            ComparisonMetric(label: "支出", current: currentExpense, previous: previousExpense, expense: true),
            ComparisonMetric(label: "收入", current: currentIncome, previous: previousIncome, expense: false),
            ComparisonMetric(
                label: "结余",
                current: currentIncome - currentExpense,
                previous: previousIncome - previousExpense,
                expense: false
            ),
        ]
    }

    private func chartLegend(_ label: String, color: Color) -> some View {
        HStack(spacing: 4) {
            Capsule().fill(color).frame(width: 12, height: 5)
            Text(label).font(.caption2).foregroundStyle(.secondary)
        }
    }
}

private struct ComparisonMetricRow: View {
    @Environment(\.appThemeColor) private var themeColor
    let metric: ComparisonMetric

    var body: some View {
        let maximum = max(max(abs(metric.current), abs(metric.previous)), 1)
        let change = metric.current - metric.previous
        VStack(alignment: .leading, spacing: 5) {
            HStack {
                Text(metric.label).font(.subheadline.weight(.semibold))
                Spacer()
                Text("\(change >= 0 ? "+" : "")\(change.rmb)")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(favorable(change) ? themeColor : Color.red)
            }
            comparisonBar(value: metric.current, maximum: maximum, color: .primary)
            comparisonBar(value: metric.previous, maximum: maximum, color: .secondary)
        }
    }

    private func comparisonBar(value: Int64, maximum: Int64, color: Color) -> some View {
        HStack(spacing: 8) {
            GeometryReader { proxy in
                Capsule().fill(.quaternary)
                    .overlay(alignment: .leading) {
                        Capsule().fill(color).frame(width: proxy.size.width * CGFloat(abs(value)) / CGFloat(maximum))
                    }
            }
            .frame(height: 7)
            Text(value.rmb).font(.caption2).frame(width: 78, alignment: .trailing)
        }
    }

    private func favorable(_ change: Int64) -> Bool { metric.expense ? change <= 0 : change >= 0 }
}

private struct DonutChart: View {
    let items: [CategoryShareUI]

    var body: some View {
        let total = max(items.map(\.amount).reduce(0, +), 1)
        Canvas { context, size in
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let radius = min(size.width, size.height) * 0.34
            var start = Angle.degrees(-90)
            for (index, item) in items.enumerated() {
                let sweep = Angle.degrees(360 * Double(item.amount) / Double(total))
                let end = start + sweep
                var path = Path()
                path.addArc(center: center, radius: radius, startAngle: start, endAngle: end, clockwise: false)
                context.stroke(
                    path,
                    with: .color(analyticsPalette[index % analyticsPalette.count]),
                    style: StrokeStyle(lineWidth: 28, lineCap: .butt)
                )
                start = end
            }
        }
        .overlay {
            VStack(spacing: 2) {
                Text("合计").font(.caption).foregroundStyle(.secondary)
                Text(total.rmb).font(.headline.bold())
            }
        }
    }
}

private struct CategoryShareRow: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.colorScheme) private var colorScheme
    let item: CategoryShareUI
    let maximum: Int64
    let color: Color

    var body: some View {
        HStack(spacing: 10) {
            SVGIconView(
                key: categoryIconAssetKey(item.iconKey ?? "category"),
                size: 24,
                tint: (AppThemeColor(rawValue: store.themeColor) ?? .lavender).cssColor(for: colorScheme)
            )
            VStack(alignment: .leading, spacing: 5) {
                HStack { Text(item.name); Spacer(); Text(item.amount.rmb).font(.caption) }
                GeometryReader { proxy in
                    Capsule().fill(.quaternary)
                        .overlay(alignment: .leading) {
                            Capsule().fill(color).frame(width: proxy.size.width * CGFloat(item.amount) / CGFloat(maximum))
                        }
                }
                .frame(height: 8)
            }
        }
    }
}
