import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var store: AppStore
    @State private var showingMonthPicker = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HStack {
                    ledgerPicker
                    Spacer()
                    monthHeader
                    Spacer()
                    Button { store.destination = .search } label: { Image(systemName: "magnifyingglass") }
                }
                MonthlyBalanceCard(expense: store.expenseMinor, income: store.incomeMinor)
                HStack { Spacer(); CalendarFilterPicker() }
                HomeCalendarView()
                HStack {
                    Text("明细").font(.title2.bold())
                    Spacer()
                    Button(store.transactionDisplayMode.label, action: store.toggleTransactionDisplayMode)
                }
                if store.loading {
                    ProgressView().frame(maxWidth: .infinity)
                } else if store.ledgers.isEmpty {
                    EmptyStateView(title: "还没有账本", systemImage: "books.vertical", detail: "创建账本后即可开始记录交易", actionTitle: "前往更多") {
                        store.destination = .more
                    }
                } else if store.transactions.isEmpty {
                    EmptyStateView(title: "本月暂无交易", systemImage: "calendar", detail: "从第一笔交易开始记录资金流向", actionTitle: "新增交易") {
                        store.startNewTransaction(ledgerID: store.selectedLedgerID)
                    }
                } else {
                    TransactionGroupsView(items: store.transactions, displayMode: store.transactionDisplayMode) { store.showTransactionDetail($0) }
                }
                if let error = store.error { Text(error).foregroundStyle(.red) }
            }
            .padding()
        }
        .navigationTitle("首页")
        .sheet(
            isPresented: Binding(
                get: { store.selectedDate != nil },
                set: { if !$0 { store.dismissDateDetail() } }
            )
        ) {
            #if os(macOS)
            DateTransactionDetailView()
                .environmentObject(store)
                .frame(minWidth: 420, minHeight: 520)
            #else
            DateTransactionDetailView()
                .environmentObject(store)
            #endif
        }
    }

    private var ledgerPicker: some View {
        Menu {
            Button("所有账本") { store.selectLedger(nil) }
            ForEach(store.ledgers) { ledger in Button(ledger.name) { store.selectLedger(ledger.id) } }
        } label: {
            Image(systemName: "books.vertical")
                .font(.title3.weight(.semibold))
        }
        .accessibilityLabel(store.selectedLedgerID.flatMap { id in store.ledgers.first { $0.id == id }?.name } ?? "所有账本")
    }

    private var monthHeader: some View {
        HStack(spacing: 4) {
            Button { store.shiftMonth(-1) } label: { Image(systemName: "chevron.left") }
            Button(store.selectedMonth.formatted(.dateTime.year().month())) { showingMonthPicker.toggle() }
                .font(.headline)
                .buttonStyle(.plain)
                .popover(isPresented: $showingMonthPicker) {
                    DatePicker(
                        "月份",
                        selection: Binding(get: { store.selectedMonth }, set: store.selectMonth),
                        displayedComponents: [.date]
                    )
                    .datePickerStyle(.graphical)
                    .padding()
                }
            Button { store.shiftMonth(1) } label: { Image(systemName: "chevron.right") }
        }
    }
}

private struct HomeCalendarView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.appThemeColor) private var themeColor
    @Environment(\.appThemeSelectionForeground) private var selectedForeground
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 6), count: 7)

    var body: some View {
        VStack(spacing: 12) {
            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(Calendar.current.veryShortStandaloneWeekdaySymbols, id: \.self) { Text($0).font(.caption).foregroundStyle(.secondary) }
                ForEach(0..<leadingBlankCount, id: \.self) { _ in Color.clear.frame(height: 56) }
                ForEach(1...dayCount, id: \.self) { day in
                    let date = date(day)
                    let summary = summaries[Calendar.current.startOfDay(for: date)]
                    let isToday = Calendar.current.isDateInToday(date)
                    Button { store.showDate(date) } label: {
                        VStack(spacing: 2) {
                            Text("\(day)")
                                .fontWeight(isToday ? .bold : .medium)
                                .foregroundStyle(isToday ? selectedForeground : Color.primary)
                                .frame(width: 28, height: 28)
                                .background(isToday ? themeColor : .clear, in: Circle())
                            if let display = summary?.displayAmount(filter: store.calendarFilter) {
                                Text("\(display.income ? "+" : "-")\(compact(display.amount))")
                                    .foregroundStyle(display.income ? themeColor : Color.red)
                            }
                        }
                        .font(.caption2)
                        .frame(maxWidth: .infinity, minHeight: 56)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(.vertical, 4)
    }

    private var interval: DateInterval { Calendar.current.dateInterval(of: .month, for: store.selectedMonth) ?? DateInterval(start: store.selectedMonth, duration: 30 * 86_400) }
    private var dayCount: Int { Calendar.current.range(of: .day, in: .month, for: interval.start)?.count ?? 30 }
    private var leadingBlankCount: Int {
        let weekday = Calendar.current.component(.weekday, from: interval.start)
        return (weekday - Calendar.current.firstWeekday + 7) % 7
    }
    private var summaries: [Date: CalendarDayUI] { Dictionary(uniqueKeysWithValues: store.calendarDays.map { (Calendar.current.startOfDay(for: $0.date), $0) }) }
    private func date(_ day: Int) -> Date { Calendar.current.date(byAdding: .day, value: day - 1, to: interval.start) ?? interval.start }
    private func compact(_ minor: Int64) -> String { minor >= 10_000 ? "\(minor / 10_000)百" : "\(minor / 100)" }
}

private struct DateTransactionDetailView: View {
    @EnvironmentObject private var store: AppStore
    @State private var displayMode: TransactionDisplayMode = .card

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HStack(spacing: 18) {
                        Text("支出 \(store.dateDetailExpenseMinor.wholeRmb)")
                        Text("收入 \(store.dateDetailIncomeMinor.wholeRmb)")
                        Spacer()
                        Button(displayMode.label) {
                            displayMode = displayMode == .card ? .list : .card
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                    }
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.secondary)
                    if store.dateDetailTransactions.isEmpty {
                        EmptyStateView(title: "当天暂无明细", systemImage: "calendar", detail: "这一天没有记录")
                    } else {
                        TransactionCollectionView(items: store.dateDetailTransactions, displayMode: displayMode) { item in
                            store.showTransactionDetail(item)
                            store.dismissDateDetail()
                        }
                    }
                }
                .padding()
            }
            .navigationTitle(store.selectedDate?.formatted(date: .abbreviated, time: .omitted) ?? "日期明细")
            .toolbar {
                Button("关闭", action: store.dismissDateDetail)
            }
        }
    }

}

private struct TransactionGroupsView: View {
    let items: [TransactionUI]
    let displayMode: TransactionDisplayMode
    let onEdit: (TransactionUI) -> Void

    var body: some View {
        LazyVStack(alignment: .leading, spacing: 16) {
            ForEach(grouped) { group in
                HStack {
                    Text(group.date.formatted(date: .abbreviated, time: .omitted)).font(.headline)
                    Spacer()
                    Text(group.netMinor.wholeRmb)
                        .foregroundStyle(.secondary)
                }
                TransactionCollectionView(items: group.items, displayMode: displayMode, onEdit: onEdit)
            }
        }
    }

    private var grouped: [DayGroup] {
        Dictionary(grouping: items) { Calendar.current.startOfDay(for: $0.date) }
            .map { DayGroup(date: $0.key, items: $0.value.sorted { $0.date > $1.date }) }
            .sorted { $0.date > $1.date }
    }
}

private struct DayGroup: Identifiable {
    let date: Date
    let items: [TransactionUI]
    var id: Date { date }
    var netMinor: Int64 {
        items.reduce(0) { total, item in total + (item.type == .income ? item.amountMinor : -item.amountMinor) }
    }
}

private struct TransactionCollectionView: View {
    let items: [TransactionUI]
    let displayMode: TransactionDisplayMode
    let onEdit: (TransactionUI) -> Void

    var body: some View {
        if displayMode == .list {
            VStack(spacing: 8) { ForEach(items) { TransactionRow(item: $0, onEdit: onEdit) } }
        } else {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 210), spacing: 10)], spacing: 10) {
                ForEach(items) { TransactionRow(item: $0, onEdit: onEdit) }
            }
        }
    }
}

private struct TransactionRow: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.colorScheme) private var colorScheme
    let item: TransactionUI
    let onEdit: (TransactionUI) -> Void

    var body: some View {
        Button { onEdit(item) } label: {
            HStack {
                SVGIconView(
                    key: categoryIconAssetKey(item.categoryIconKey ?? (item.type == .expense ? "shopping-bag" : "banknote")),
                    size: 28,
                    tint: (AppThemeColor(rawValue: store.themeColor) ?? .lavender).cssColor(for: colorScheme)
                )
                VStack(alignment: .leading) {
                    Text(item.categoryDisplayName).fontWeight(.medium)
                    Text("\(item.accountName) · \(item.note)").font(.caption).foregroundStyle(.secondary).lineLimit(2)
                }
                Spacer()
                VStack(alignment: .trailing) {
                    Text((item.type == .expense ? -item.amountMinor : item.amountMinor).rmb)
                    Text(item.date.formatted(date: .omitted, time: .shortened)).font(.caption).foregroundStyle(.secondary)
                }
            }
            .padding(12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .liquidGlassSurface(cornerRadius: 14, interactive: true)
    }
}

struct EmptyStateView: View {
    let title: String
    let systemImage: String
    let detail: String
    let actionTitle: String?
    let action: (() -> Void)?

    init(title: String, systemImage: String, detail: String, actionTitle: String? = nil, action: (() -> Void)? = nil) {
        self.title = title
        self.systemImage = systemImage
        self.detail = detail
        self.actionTitle = actionTitle
        self.action = action
    }

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: systemImage).font(.system(size: 34)).foregroundStyle(.secondary)
            Text(title).font(.headline)
            Text(detail).font(.subheadline).foregroundStyle(.secondary)
            if let actionTitle, let action {
                Button(actionTitle, action: action).buttonStyle(.borderedProminent)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 180)
    }
}

struct SummaryCard: View {
    let title: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.headline)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .liquidGlassSurface(cornerRadius: 16)
    }
}

private struct CalendarFilterPicker: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.appThemeColor) private var themeColor
    @Environment(\.appThemeSelectionForeground) private var selectedForeground

    var body: some View {
        LiquidGlassContainer(spacing: 4) {
            HStack(spacing: 4) {
                filterButton("ALL", "list.bullet", "全部")
                filterButton("INCOME", "plus", "收入")
                filterButton("EXPENSE", "minus", "支出")
            }
        }
    }

    private func filterButton(_ value: String, _ systemImage: String, _ label: String) -> some View {
        Button { store.setCalendarFilter(value) } label: {
            Image(systemName: systemImage)
                .frame(width: 34, height: 34)
                .background(store.calendarFilter == value ? themeColor : Color.clear, in: Circle())
                .liquidGlassCircle(interactive: true)
        }
        .buttonStyle(.plain)
        .foregroundStyle(store.calendarFilter == value ? selectedForeground : Color.secondary)
        .accessibilityLabel(label)
        .accessibilityAddTraits(store.calendarFilter == value ? .isSelected : [])
    }
}

private struct MonthlyBalanceCard: View {
    let expense: Int64
    let income: Int64

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("本月结余").font(.caption.weight(.medium))
            Text((income - expense).rmb).font(.title.bold())
            HStack {
                Text("支出 \(expense.rmb)")
                Spacer()
                Text("收入 \(income.rmb)")
            }
            .font(.subheadline)
        }
        .foregroundStyle(.primary)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .liquidGlassSurface(cornerRadius: 18)
    }
}
