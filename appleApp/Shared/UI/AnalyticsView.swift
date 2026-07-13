import Charts
import Foundation
import SwiftUI

struct AnalyticsView: View {
    @EnvironmentObject private var store: AppStore
    @State private var mode = RangeMode.week
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
                }
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
        .sheet(item: $store.analyticsStatement) { StatementTableView(table: $0) }
        .sheet(
            isPresented: Binding(
                get: { store.selectedDetailRange != nil },
                set: { if !$0 { store.dismissDateDetail() } }
            ),
            onDismiss: store.presentPendingTransactionDetail
        ) {
            #if os(macOS)
            DateTransactionDetailView().environmentObject(store).frame(minWidth: 420, minHeight: 520)
            #else
            DateTransactionDetailView().environmentObject(store)
            #endif
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
        store.observeAnalytics(start: range.start, end: range.end, ledgerID: store.analyticsLedgerID)
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
                        BarMark(x: .value("月份", month.month), y: .value("收入", Double(month.incomeMinor) / 100)).foregroundStyle(Color.income)
                        BarMark(x: .value("月份", month.month), y: .value("支出", -Double(month.expenseMinor) / 100)).foregroundStyle(Color.expense)
                    } else {
                        BarMark(x: .value("月份", month.month), y: .value("金额", Double(month.incomeMinor) / 100))
                            .position(by: .value("类型", "收入")).foregroundStyle(Color.income)
                        BarMark(x: .value("月份", month.month), y: .value("金额", Double(month.expenseMinor) / 100))
                            .position(by: .value("类型", "支出")).foregroundStyle(Color.expense)
                    }
                }
                RuleMark(y: .value("零", 0)).foregroundStyle(.secondary.opacity(0.4))
            }
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
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(chartAccessibilityLabel)
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

    private var chartAccessibilityLabel: String {
        statement.months.map {
            "\($0.month)月，收入\($0.incomeMinor.rmb)，支出\($0.expenseMinor.rmb)"
        }.joined(separator: "；")
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
                    HStack(spacing: 12) {
                        Text("\(index + 1).").font(.headline).frame(width: 28)
                        SVGIconView(
                            key: categoryIconAssetKey(item.iconKey),
                            size: 28,
                            tint: (AppThemeColor(rawValue: store.themeColor) ?? .lavender).cssColor(for: colorScheme)
                        )
                        Text(item.displayName).fontWeight(.semibold).lineLimit(1)
                        Spacer()
                        Text(item.amount.rmb).fontWeight(.bold).foregroundStyle(selectedType == .expense ? Color.expense : Color.income)
                    }
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

    var body: some View {
        let total = max(items.map(\.amount).reduce(0, +), 1)
        Canvas { context, size in
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
    let table: StatementTableUI

    var body: some View {
        NavigationStack {
            List {
                Section("全年") { statementRow("合计", income: table.incomeMinor, expense: table.expenseMinor) }
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
            Text(income.rmb).foregroundStyle(Color.income)
            Text(expense.rmb).foregroundStyle(Color.expense)
            Text((income - expense).rmb)
        }
    }
}

private enum RangeMode: String, CaseIterable, Identifiable {
    case week, month, year, custom
    var id: String { rawValue }
    var label: String { switch self { case .week: return "周"; case .month: return "月"; case .year: return "年"; case .custom: return "范围" } }
}

private enum BarLayout { case diverging, sideBySide }

private struct AnalyticsHeader<Trailing: View>: View {
    let title: String
    @ViewBuilder let trailing: Trailing

    var body: some View {
        HStack(spacing: 12) {
            Text(title).font(.title2.bold())
            Spacer(minLength: 8)
            trailing
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
