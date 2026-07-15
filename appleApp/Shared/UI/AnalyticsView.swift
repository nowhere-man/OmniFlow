import Charts
import Foundation
import SwiftUI

struct AnalyticsView: View {
    @EnvironmentObject private var store: AppStore
    @State private var mode = RangeMode.month
    @State private var anchor = Date()
    @State private var customStart = Date()
    @State private var customEnd = Date()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HStack(spacing: 10) {
                    Menu {
                        Button("所有账本") { store.selectAnalyticsLedger(nil) }
                        ForEach(store.ledgers) { ledger in Button(ledger.name) { store.selectAnalyticsLedger(ledger.id) } }
                    } label: {
                        Image(systemName: "books.vertical").iOSLiquidGlassIconControl()
                    }
                    .iOSPlainButtonStyle()
                    .accessibilityLabel("选择统计账本")
                    ThemeSegmentedControl(selection: $mode, options: RangeMode.allCases, title: \.label)
                }
                rangeControls
                analyticsContent
            }
            .padding()
        }
        .navigationTitle("统计")
        .onAppear(perform: refresh)
        .onChange(of: mode) { _ in refresh() }
        .onChange(of: customStart) { _ in if mode == .custom { refresh() } }
        .onChange(of: customEnd) { _ in if mode == .custom { refresh() } }
        .onChange(of: store.analyticsLedgerID) { _ in refresh() }
        .onChange(of: store.analyticsRankingType) { _ in refresh() }
        .onChange(of: store.analyticsCategoryType) { _ in refresh() }
        .onChange(of: store.analyticsTagType) { _ in refresh() }
        .sheet(item: $store.analyticsStatement) { StatementTableView(initialTable: $0) }
        .sheet(
            isPresented: Binding(
                get: { store.selectedDetailRange != nil },
                set: { if !$0 { store.dismissDateDetail() } }
            ),
            onDismiss: { store.presentPendingDateDetailDestination() }
        ) {
            #if os(macOS)
            DateTransactionDetailView().environmentObject(store).frame(minWidth: 420, minHeight: 520)
            #else
            DateTransactionDetailView().environmentObject(store)
            #endif
        }
    }

    @ViewBuilder
    private var analyticsContent: some View {
        switch store.analyticsStatus {
        case .idle where store.analyticsYearStatement == nil,
             .loading where store.analyticsYearStatement == nil:
            ProgressView("正在加载统计…")
                .frame(maxWidth: .infinity)
                .padding(.vertical, 32)
        case let .failed(message) where store.analyticsYearStatement == nil:
            EmptyStateView(
                title: "统计加载失败",
                systemImage: "exclamationmark.triangle",
                detail: message,
                actionTitle: "重试",
                action: refresh
            )
        default:
            if case .loading = store.analyticsStatus {
                ProgressView().controlSize(.small).frame(maxWidth: .infinity)
            }
            AnalyticsSummaryCard(
                expense: store.analyticsExpenseMinor,
                income: store.analyticsIncomeMinor,
                previousExpense: store.analyticsPreviousExpenseMinor,
                previousIncome: store.analyticsPreviousIncomeMinor
            )
            AnalyticsTrendCard(points: store.analyticsTrend, granularity: trendGranularity)
            if mode == .month, let statement = store.analyticsYearStatement {
                YearBarsCard(statement: statement, selectedMonth: selectedMonth, onMonth: selectMonth) {
                    store.analyticsStatement = statement
                }
            }
            if store.analyticsExpenseMinor == 0 && store.analyticsIncomeMinor == 0 {
                EmptyStateView(
                    title: "当前范围还没有收支",
                    systemImage: "chart.bar",
                    detail: "新增交易后即可查看排行和分类分析",
                    actionTitle: "新增交易",
                    action: { store.startNewTransaction() }
                )
            } else {
                RankingCard()
                CategoryCard()
                TagCard()
            }
        }
    }

    @ViewBuilder
    private var rangeControls: some View {
        if mode == .custom {
            HStack(spacing: 8) {
                compactDatePicker("开始", selection: $customStart)
                compactDatePicker("结束", selection: $customEnd)
            }
        } else {
            HStack {
                Button { shift(-1) } label: { Image(systemName: "chevron.left").iOSLiquidGlassIconControl(size: 34) }
                    .iOSPlainButtonStyle()
                Spacer()
                Text(rangeLabel).font(.headline).lineLimit(1).minimumScaleFactor(0.8)
                Spacer()
                Button { shift(1) } label: { Image(systemName: "chevron.right").iOSLiquidGlassIconControl(size: 34) }
                    .iOSPlainButtonStyle()
            }
            if !range.contains(Date()) {
                Button(currentRangeTitle) { anchor = Date(); refresh() }
                    .buttonStyle(.bordered)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private func RankingCard() -> some View {
        AnalyticsRankingCard(
            items: store.analyticsRanking,
            selectedType: $store.analyticsRankingType
        )
    }

    private func CategoryCard() -> some View {
        AnalyticsCategoryCard(
            items: store.analyticsCategories,
            selectedType: $store.analyticsCategoryType
        )
    }

    private func TagCard() -> some View {
        AnalyticsTagCard(
            items: store.analyticsTags,
            total: store.analyticsTagType == .expense ? store.analyticsExpenseMinor : store.analyticsIncomeMinor,
            selectedType: $store.analyticsTagType
        )
    }

    private var range: DateInterval {
        let calendar = Calendar.current
        switch mode {
        case .week: return calendar.dateInterval(of: .weekOfYear, for: anchor) ?? DateInterval(start: anchor, duration: 7 * 86_400)
        case .month: return calendar.dateInterval(of: .month, for: anchor) ?? DateInterval(start: anchor, duration: 30 * 86_400)
        case .year: return calendar.dateInterval(of: .year, for: anchor) ?? DateInterval(start: anchor, duration: 365 * 86_400)
        case .custom:
            let start = Calendar.current.startOfDay(for: min(customStart, customEnd))
            let last = Calendar.current.startOfDay(for: max(customStart, customEnd))
            return DateInterval(start: start, end: Calendar.current.date(byAdding: .day, value: 1, to: last) ?? last.addingTimeInterval(86_400))
        }
    }

    private var selectedMonth: Int? {
        mode == .month ? Calendar.current.component(.month, from: range.start) : nil
    }

    private func selectMonth(_ month: Int) {
        let year = store.analyticsYearStatement?.year ?? Calendar.current.component(.year, from: anchor)
        anchor = Calendar.current.date(from: DateComponents(year: year, month: month, day: 1)) ?? anchor
        mode = .month
        refresh()
    }

    private func shift(_ value: Int) {
        let component: Calendar.Component = mode == .week ? .weekOfYear : mode == .year ? .year : .month
        anchor = Calendar.current.date(byAdding: component, value: value, to: anchor) ?? anchor
        refresh()
    }

    private func refresh() {
        store.observeAnalytics(
            start: range.start,
            end: range.end,
            ledgerID: store.analyticsLedgerID,
            granularity: trendGranularity
        )
    }

    private var trendGranularity: AnalyticsGranularityUI {
        switch mode {
        case .week, .month: return .day
        case .year: return .month
        case .custom: return range.duration <= 90 * 86_400 ? .day : .month
        }
    }

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

    private func compactDatePicker(_ title: String, selection: Binding<Date>) -> some View {
        DatePicker(title, selection: selection, displayedComponents: .date)
            .labelsHidden()
            .datePickerStyle(.compact)
            .frame(maxWidth: .infinity)
    }

    private func format(_ date: Date, _ pattern: String) -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = pattern
        return formatter.string(from: date)
    }
}

private struct AnalyticsSummaryCard: View {
    let expense: Int64
    let income: Int64
    let previousExpense: Int64
    let previousIncome: Int64

    var body: some View {
        AnalyticsCard {
            AnalyticsHeader(title: "收支汇总") { EmptyView() }
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 12) {
                    summaryValue("总支出", value: expense, previous: previousExpense, color: .expense)
                    summaryValue("总收入", value: income, previous: previousIncome, color: .income)
                    summaryValue("总结余", value: income - expense, previous: previousIncome - previousExpense, color: .accentColor)
                }
                VStack(spacing: 10) {
                    summaryLine("总支出", value: expense, previous: previousExpense, color: .expense)
                    summaryLine("总收入", value: income, previous: previousIncome, color: .income)
                    summaryLine("总结余", value: income - expense, previous: previousIncome - previousExpense, color: .accentColor)
                }
            }
        }
    }

    private func summaryValue(_ title: String, value: Int64, previous: Int64, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text(value.rmb).font(.headline).fontWeight(.bold).foregroundStyle(color).lineLimit(1)
            Text(changeText(value, previous: previous)).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func summaryLine(_ title: String, value: Int64, previous: Int64, color: Color) -> some View {
        HStack(spacing: 10) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Spacer()
            Text(value.rmb).font(.headline).fontWeight(.bold).foregroundStyle(color)
            Text(changeText(value, previous: previous)).font(.caption2).foregroundStyle(.secondary)
        }
    }

    private func changeText(_ value: Int64, previous: Int64) -> String {
        guard previous != 0 else { return value == 0 ? "较上期持平" : "上期无数据" }
        let percent = Int((Double(value - previous) / Double(abs(previous))) * 100)
        if percent > 0 { return "较上期 +\(percent)%" }
        if percent < 0 { return "较上期 \(percent)%" }
        return "较上期持平"
    }
}

private struct AnalyticsTrendCard: View {
    let points: [AnalyticsChartPointUI]
    let granularity: AnalyticsGranularityUI

    var body: some View {
        AnalyticsCard {
            AnalyticsHeader(title: "收支趋势") { EmptyView() }
            if points.isEmpty {
                Text("当前范围暂无趋势数据").foregroundStyle(.secondary)
            } else {
                Chart(points) { point in
                    LineMark(
                        x: .value("日期", point.start),
                        y: .value("金额", Double(point.incomeMinor) / 100)
                    )
                    .foregroundStyle(by: .value("类型", "收入"))
                    .interpolationMethod(.monotone)
                    PointMark(
                        x: .value("日期", point.start),
                        y: .value("金额", Double(point.incomeMinor) / 100)
                    )
                    .foregroundStyle(by: .value("类型", "收入"))
                    LineMark(
                        x: .value("日期", point.start),
                        y: .value("金额", Double(point.expenseMinor) / 100)
                    )
                    .foregroundStyle(by: .value("类型", "支出"))
                    .interpolationMethod(.monotone)
                    PointMark(
                        x: .value("日期", point.start),
                        y: .value("金额", Double(point.expenseMinor) / 100)
                    )
                    .foregroundStyle(by: .value("类型", "支出"))
                }
                .chartForegroundStyleScale(["收入": Color.income, "支出": Color.expense])
                .chartXAxis {
                    AxisMarks(values: .automatic(desiredCount: 6)) { value in
                        AxisGridLine()
                        AxisValueLabel {
                            if let date = value.as(Date.self) {
                                Text(axisLabel(date))
                            }
                        }
                    }
                }
                .frame(height: 220)
            }
        }
    }

    private func axisLabel(_ date: Date) -> String {
        switch granularity {
        case .month: return date.formatted(.dateTime.month(.twoDigits))
        case .day, .week: return date.formatted(.dateTime.month(.twoDigits).day(.twoDigits))
        }
    }
}

private struct YearBarsCard: View {
    let statement: StatementTableUI
    let selectedMonth: Int?
    let onMonth: (Int) -> Void
    let onStatement: () -> Void
    @State private var layout = BarLayout.diverging

    var body: some View {
        AnalyticsCard {
            AnalyticsHeader(title: "收支柱状图") {
                Picker("柱状图布局", selection: $layout) {
                    Text("上下").tag(BarLayout.diverging)
                    Text("并排").tag(BarLayout.sideBySide)
                }
                .pickerStyle(.segmented)
                .frame(width: 150)
                .frame(minHeight: 44)
            }
            Text("\(statement.year) 年").font(.headline)
            Chart {
                ForEach(statement.months) { month in
                    if layout == .diverging {
                        BarMark(x: .value("月份", month.month), y: .value("收入金额", Double(month.incomeMinor) / 100))
                            .foregroundStyle(by: .value("类型", "收入"))
                        BarMark(x: .value("月份", month.month), y: .value("支出金额（向下）", -Double(month.expenseMinor) / 100))
                            .foregroundStyle(by: .value("类型", "支出"))
                    } else {
                        BarMark(x: .value("月份", month.month), y: .value("金额", Double(month.incomeMinor) / 100))
                            .position(by: .value("类型", "收入")).foregroundStyle(by: .value("类型", "收入"))
                        BarMark(x: .value("月份", month.month), y: .value("金额", Double(month.expenseMinor) / 100))
                            .position(by: .value("类型", "支出")).foregroundStyle(by: .value("类型", "支出"))
                    }
                }
                RuleMark(y: .value("零", 0)).foregroundStyle(.secondary.opacity(0.4))
            }
            .chartForegroundStyleScale(["收入": Color.income, "支出": Color.expense])
            .chartXAxis(.hidden)
            .chartLegend(.hidden)
            .frame(height: 210)
            .chartOverlay { proxy in
                GeometryReader { geometry in
                    Rectangle()
                        .fill(.clear)
                        .contentShape(Rectangle())
                        .gesture(
                            SpatialTapGesture().onEnded { value in
                                let plotFrame = geometry[proxy.plotAreaFrame]
                                guard plotFrame.contains(value.location) else { return }
                                let plotX = value.location.x - plotFrame.origin.x
                                if let month: Int = proxy.value(atX: plotX), statement.months.contains(where: { $0.month == month }) {
                                    onMonth(month)
                                }
                            }
                        )
                        .accessibilityHidden(true)
                }
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(statement.months) { month in
                        Button("\(String(format: "%02d", month.month))月") { onMonth(month.month) }
                            .buttonStyle(.bordered)
                            .tint(selectedMonth == month.month ? Color.accentColor : Color.secondary)
                            .frame(minWidth: 44, minHeight: 44)
                    }
                }
            }
            HStack(spacing: 16) {
                Spacer()
                barLegend("收入", color: .income)
                barLegend("支出", color: .expense)
            }
            Button("查看账单表格", action: onStatement).buttonStyle(.bordered).frame(maxWidth: .infinity)
        }
    }

    private func barLegend(_ title: String, color: Color) -> some View {
        HStack(spacing: 6) {
            Circle().fill(color).frame(width: 10, height: 10)
            Text(title).font(.subheadline).foregroundStyle(.secondary)
        }
    }
}

private struct AnalyticsRankingCard: View {
    @Environment(\.colorScheme) private var colorScheme
    @EnvironmentObject private var store: AppStore
    let items: [AnalyticsRankingUI]
    @Binding var selectedType: EntryType
    @State private var expanded = false

    var body: some View {
        AnalyticsCard {
            AnalyticsHeader(title: "收支排行榜") {
                ThemeSegmentedControl(selection: $selectedType, options: EntryType.allCases, title: \.label)
                    .frame(width: 150)
                    .frame(minHeight: 44)
            }
            if items.isEmpty {
                Text(selectedType == .expense ? "暂无支出排行" : "暂无收入排行")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(Array(items.prefix(expanded ? 10 : 3).enumerated()), id: \.element.id) { index, item in
                    Button {
                        store.showTransactionDetail(item.transaction)
                    } label: {
                        HStack(spacing: 12) {
                            Text("\(index + 1).").font(.headline).frame(width: 28)
                            SVGIconView(
                                key: categoryIconAssetKey(item.iconKey),
                                size: 28,
                                tint: (AppThemeColor(rawValue: store.themeColor) ?? .lavender).cssColor(for: colorScheme)
                            )
                            VStack(alignment: .leading, spacing: 2) {
                                Text(item.displayName).fontWeight(.semibold).lineLimit(1)
                                Text(rankingSubtitle(item))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                            Spacer()
                            Text(item.amount.rmb).fontWeight(.bold).foregroundStyle(selectedType == .expense ? Color.expense : Color.income)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .padding(.vertical, 6)
                }
            }
            if items.count > 3 {
                Button(expanded ? "收起" : "展示更多") { expanded.toggle() }
                    .buttonStyle(.bordered)
                    .frame(maxWidth: .infinity)
            }
        }
        .onChange(of: selectedType) { _ in expanded = false }
    }

    private func rankingSubtitle(_ item: AnalyticsRankingUI) -> String {
        let date = item.date.formatted(.dateTime.month(.twoDigits).day(.twoDigits).hour(.twoDigits(amPM: .omitted)).minute(.twoDigits))
        return [item.note.isEmpty ? nil : item.note, date].compactMap { $0 }.joined(separator: " · ")
    }
}

private struct AnalyticsTagCard: View {
    let items: [TagAnalysisUI]
    let total: Int64
    @Binding var selectedType: EntryType

    var body: some View {
        AnalyticsCard {
            AnalyticsHeader(title: "记账标签分析") {
                ThemeSegmentedControl(selection: $selectedType, options: EntryType.allCases, title: \.label)
                    .frame(width: 150)
                    .frame(minHeight: 44)
            }
            if items.isEmpty {
                Text("当前范围暂无标签数据").foregroundStyle(.secondary)
            } else {
                ForEach(items) { item in
                    VStack(alignment: .leading, spacing: 5) {
                        HStack {
                            Text(item.name).fontWeight(.medium)
                            Spacer()
                            Text("\(item.transactionCount) 笔").foregroundStyle(.secondary)
                            Text(item.amountMinor.rmb).fontWeight(.semibold)
                        }
                        ProgressView(value: Double(item.amountMinor), total: Double(max(total, 1)))
                            .tint(.accentColor)
                    }
                    .padding(.vertical, 4)
                }
            }
        }
    }
}

private struct AnalyticsCategoryCard: View {
    @Environment(\.colorScheme) private var colorScheme
    @EnvironmentObject private var store: AppStore
    let items: [CategoryBreakdownUI]
    @Binding var selectedType: EntryType
    @State private var showSecondary = false

    var body: some View {
        AnalyticsCard {
            AnalyticsHeader(title: "收支饼图") {
                ThemeSegmentedControl(selection: $selectedType, options: EntryType.allCases, title: \.label)
                    .frame(width: 150)
                    .frame(minHeight: 44)
            }
            if items.isEmpty {
                Text(selectedType == .expense ? "暂无支出分类数据" : "暂无收入分类数据")
                    .foregroundStyle(.secondary)
            } else {
                CategoryDonut(items: items)
                Toggle("显示二级分类占比", isOn: $showSecondary)
                ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                    categoryRow(item.name, iconKey: item.iconKey, amount: item.amount, total: total, color: analyticsPalette[index % analyticsPalette.count])
                    if showSecondary {
                        ForEach(item.secondary) { secondary in
                            categoryRow(secondary.name, iconKey: secondary.iconKey, amount: secondary.amount, total: item.amount, color: analyticsPalette[index % analyticsPalette.count].opacity(0.75), secondary: true)
                        }
                    }
                }
            }
        }
    }

    private var total: Int64 { max(items.map(\.amount).reduce(0, +), 1) }

    private func categoryRow(_ name: String, iconKey: String?, amount: Int64, total: Int64, color: Color, secondary: Bool = false) -> some View {
        let ratio = Double(amount) / Double(max(total, 1))
        return VStack(alignment: .leading, spacing: 5) {
            HStack {
                SVGIconView(
                    key: categoryIconAssetKey(iconKey),
                    size: secondary ? 18 : 24,
                    tint: (AppThemeColor(rawValue: store.themeColor) ?? .lavender).cssColor(for: colorScheme)
                )
                Text(name).fontWeight(.medium)
                Spacer()
                Text(amount.rmb).fontWeight(.semibold)
                Text(ratio.formatted(.percent.precision(.fractionLength(0)))).foregroundStyle(.secondary)
            }
            ProgressView(value: ratio).tint(color)
        }
        .padding(.vertical, 4)
        .padding(.leading, secondary ? 32 : 0)
    }
}

private struct CategoryDonut: View {
    let items: [CategoryBreakdownUI]

    @ViewBuilder
    var body: some View {
        #if os(iOS)
        if #available(iOS 17.0, *) {
            Chart(items) { item in
                SectorMark(
                    angle: .value("金额", Double(item.amount) / 100),
                    innerRadius: .ratio(0.62),
                    angularInset: 1.5
                )
                .foregroundStyle(by: .value("分类", item.name))
            }
            .chartForegroundStyleScale(domain: items.map(\.name), range: analyticsPalette)
            .chartLegend(.hidden)
            .frame(height: 220)
        } else {
            fallbackCanvas
        }
        #else
        fallbackCanvas
        #endif
    }

    private var fallbackCanvas: some View {
        let total = max(items.map(\.amount).reduce(0, +), 1)
        return Canvas { context, size in
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let radius = min(size.width, size.height) * 0.32
            var start = Angle.degrees(-90)
            for (index, item) in items.enumerated() {
                let sweep = Angle.degrees(360 * Double(item.amount) / Double(total))
                var path = Path()
                path.addArc(center: center, radius: radius, startAngle: start, endAngle: start + sweep, clockwise: false)
                context.stroke(path, with: .color(analyticsPalette[index % analyticsPalette.count]), lineWidth: 34)
                start = start + sweep
            }
        }
        .frame(height: 220)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityLabel)
    }

    private var accessibilityLabel: String {
        let total = max(items.map(\.amount).reduce(0, +), 1)
        return items.map {
            let ratio = Double($0.amount) / Double(total)
            return "\($0.name)\(ratio.formatted(.percent.precision(.fractionLength(0))))"
        }.joined(separator: "；")
    }
}

private struct StatementTableView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: AppStore
    let initialTable: StatementTableUI
    @State private var filter = StatementFilter.all

    var body: some View {
        #if os(macOS)
        navigationContent.frame(minWidth: 700, minHeight: 520)
        #else
        navigationContent.presentationDetents([.large])
        #endif
    }

    private var navigationContent: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        Button { store.loadAnalyticsStatement(year: table.year - 1) } label: {
                            Label("上一年", systemImage: "chevron.left").labelStyle(.iconOnly)
                        }
                        Spacer()
                        Text("\(table.year) 年").font(.title2.bold())
                        Spacer()
                        Button { store.loadAnalyticsStatement(year: table.year + 1) } label: {
                            Label("下一年", systemImage: "chevron.right").labelStyle(.iconOnly)
                        }
                    }
                    .frame(minHeight: 44)
                    Picker("账单筛选", selection: $filter) {
                        ForEach(StatementFilter.allCases) { option in Text(option.label).tag(option) }
                    }
                    .pickerStyle(.segmented)
                    .frame(minHeight: 44)
                    StatementChart(months: table.months, filter: filter)
                    statementHeader
                    statementRow("全年", income: table.incomeMinor, expense: table.expenseMinor, emphasized: true)
                    ForEach(table.months) { month in
                        Divider()
                        statementRow("\(String(format: "%02d", month.month)) 月", income: month.incomeMinor, expense: month.expenseMinor)
                    }
                }
                .padding()
            }
            .navigationTitle("\(table.year) 年账单")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
        }
    }

    private var table: StatementTableUI { store.analyticsStatement ?? initialTable }

    private var statementHeader: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 8) {
                Text("月份").frame(width: 52, alignment: .leading)
                if filter != .expense { Text("收入").frame(maxWidth: .infinity, alignment: .trailing) }
                if filter != .income { Text("支出").frame(maxWidth: .infinity, alignment: .trailing) }
                Text("结余").frame(maxWidth: .infinity, alignment: .trailing)
            }
            Text(["月份", filter == .expense ? nil : "收入", filter == .income ? nil : "支出", "结余"].compactMap { $0 }.joined(separator: " · "))
        }
        .font(.caption.bold())
        .foregroundStyle(.secondary)
    }

    private func statementRow(_ label: String, income: Int64, expense: Int64, emphasized: Bool = false) -> some View {
        let net = income - expense
        return ViewThatFits(in: .horizontal) {
            HStack(spacing: 8) {
                Text(label).frame(width: 52, alignment: .leading)
                if filter != .expense { Text(income.rmb).foregroundStyle(Color.income).frame(maxWidth: .infinity, alignment: .trailing) }
                if filter != .income { Text(expense.rmb).foregroundStyle(Color.expense).frame(maxWidth: .infinity, alignment: .trailing) }
                Text(net.rmb).foregroundStyle(net < 0 ? Color.expense : Color.income).frame(maxWidth: .infinity, alignment: .trailing)
            }
            VStack(alignment: .leading, spacing: 6) {
                Text(label)
                HStack {
                    if filter != .expense { compactAmount("收入", value: income, color: .income) }
                    if filter != .income { compactAmount("支出", value: expense, color: .expense) }
                    compactAmount("结余", value: net, color: net < 0 ? .expense : .income)
                }
            }
        }
        .fontWeight(emphasized ? .bold : .regular)
        .monospacedDigit()
        .padding(.vertical, 4)
    }

    private func compactAmount(_ title: String, value: Int64, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.caption2).foregroundStyle(.secondary)
            Text(value.rmb).foregroundStyle(color).lineLimit(1).minimumScaleFactor(0.75)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct StatementChart: View {
    let months: [StatementMonthUI]
    let filter: StatementFilter

    var body: some View {
        Chart(months) { month in
            if filter != .expense {
                BarMark(x: .value("月份", month.month), y: .value("金额", Double(month.incomeMinor) / 100))
                    .position(by: .value("类型", "收入"))
                    .foregroundStyle(by: .value("类型", "收入"))
            }
            if filter != .income {
                BarMark(x: .value("月份", month.month), y: .value("金额", Double(month.expenseMinor) / 100))
                    .position(by: .value("类型", "支出"))
                    .foregroundStyle(by: .value("类型", "支出"))
            }
        }
        .chartForegroundStyleScale(["收入": Color.income, "支出": Color.expense])
        .chartXAxis {
            AxisMarks(values: months.map(\.month)) { value in
                AxisGridLine()
                AxisValueLabel {
                    if let month = value.as(Int.self) { Text("\(month)月") }
                }
            }
        }
        .frame(height: 210)
    }
}

private enum RangeMode: String, CaseIterable, Identifiable {
    case week, month, year, custom
    var id: String { rawValue }
    var label: String { switch self { case .week: return "周"; case .month: return "月"; case .year: return "年"; case .custom: return "范围" } }
}

private enum BarLayout { case diverging, sideBySide }

private enum StatementFilter: String, CaseIterable, Identifiable {
    case all, income, expense
    var id: String { rawValue }
    var label: String {
        switch self {
        case .all: return "全部"
        case .income: return "收入"
        case .expense: return "支出"
        }
    }
}

private struct AnalyticsHeader<Trailing: View>: View {
    let title: String
    @ViewBuilder let trailing: Trailing

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 12) {
                Text(title).font(.title2.bold())
                Spacer(minLength: 8)
                trailing
            }
            VStack(alignment: .leading, spacing: 8) {
                Text(title).font(.title2.bold())
                trailing
            }
        }
    }
}

private struct AnalyticsCard<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .liquidGlassSurface(cornerRadius: 18)
    }
}

private let analyticsPalette: [Color] = [.purple, .orange, .teal, .pink, .green, .indigo, .brown]
