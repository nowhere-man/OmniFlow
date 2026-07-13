import SwiftUI

struct SearchView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.appThemeColor) private var themeColor
    @State private var showingAdvancedFilters = false

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 10) {
                HStack {
                    Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                    TextField("关键词、分类、账户或标签", text: $store.searchText)
                        .textFieldStyle(.plain)
                        .onSubmit { search() }
                        .onChange(of: store.searchText) { _ in store.scheduleSearch() }
                    if !store.searchText.isEmpty {
                        Button { store.searchText = ""; store.scheduleSearch() } label: { Image(systemName: "xmark.circle.fill") }
                            .buttonStyle(.plain)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(.horizontal, 14)
                .frame(minHeight: 44)
                .liquidGlassSurface(cornerRadius: 18, interactive: true)

                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text("筛选").font(.subheadline.bold())
                        Spacer()
                        if hasFilters {
                            Text("已筛选 \(activeFilterCount) 项").font(.caption).foregroundStyle(.secondary)
                            Button("清除全部", action: clear)
                                .buttonStyle(.plain)
                                .foregroundStyle(themeColor)
                                .accessibilityLabel("清除全部筛选条件")
                        }
                    }
                    ThemeSegmentedControl(
                        selection: Binding(get: { store.searchType }, set: setType),
                        options: [EntryType?.none, .some(.expense), .some(.income)]
                    ) { $0?.label ?? "全部" }

                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: 12) { filterMenus }
                        VStack(spacing: 8) { filterMenus }
                    }
                    filterTextField("一级分类", prompt: "名称包含", text: $store.searchPrimaryCategoryText)
                    filterTextField("二级分类", prompt: "名称包含", text: $store.searchSecondaryCategoryText)
                    filterTextField("标签", prompt: "名称包含", text: $store.searchTagText)

                    #if os(iOS)
                    DisclosureGroup(isExpanded: $showingAdvancedFilters) {
                        advancedFilters.padding(.top, 8)
                    } label: {
                        Label("更多条件", systemImage: "slider.horizontal.3")
                            .font(.subheadline.weight(.semibold))
                    }
                    #else
                    Divider()
                    advancedFilters
                    #endif
                }
                .padding(12)
                .liquidGlassSurface(cornerRadius: 18)

                if case let .failed(message) = store.searchStatus {
                    Text(message).foregroundStyle(.red)
                }
                if store.searchStatus == .loading {
                    ProgressView("搜索中…").frame(maxWidth: .infinity)
                }
                if store.searchStatus == .idle {
                    EmptyStateView(
                        title: "输入关键词或选择筛选条件开始搜索",
                        systemImage: "magnifyingglass",
                        detail: "全部筛选恢复默认时不会展示历史交易"
                    )
                } else if store.searchStatus == .loaded && store.searchResults.isEmpty {
                    EmptyStateView(title: "没有符合当前条件的交易", systemImage: "magnifyingglass", detail: "调整筛选条件后再试")
                } else if !store.searchResults.isEmpty {
                    resultSummary
                    ForEach(store.searchResults) { item in
                        searchResult(item)
                    }
                }
            }
            .padding()
        }
        .searchScrollDismissesKeyboard()
        .navigationTitle("搜索")
        .onAppear {
            store.prepareSearch()
        }
        .onChange(of: store.searchStartDate) { _ in if store.searchDateEnabled { search() } }
        .onChange(of: store.searchEndDate) { _ in if store.searchDateEnabled { search() } }
    }

    private var hasFilters: Bool { store.hasSearchFilters }

    private var activeFilterCount: Int {
        [
            !store.searchText.isEmpty,
            store.searchType != nil,
            store.searchLedgerID != nil,
            store.searchAccountID != nil,
            !store.searchPrimaryCategoryText.isEmpty,
            !store.searchSecondaryCategoryText.isEmpty,
            !store.searchTagText.isEmpty,
            !store.searchNoteText.isEmpty,
            !store.searchMinimumAmount.isEmpty,
            !store.searchMaximumAmount.isEmpty,
            store.searchDateEnabled,
        ].filter { $0 }.count
    }

    @ViewBuilder
    private var filterMenus: some View {
        filterMenu(
            label: "账本",
            title: store.searchLedgerID.flatMap { id in store.ledgers.first { $0.id == id }?.name } ?? "所有账本",
            allTitle: "所有账本",
            values: store.ledgers.map { ($0.id, $0.name) },
            onAll: { store.setSearchLedger(nil) },
            onSelected: { store.setSearchLedger($0) }
        )
        filterMenu(
            label: "账户",
            title: store.searchAccountID.flatMap { id in store.accounts.first { $0.id == id }?.name } ?? "所有账户",
            allTitle: "所有账户",
            values: store.accounts.map { ($0.id, $0.name) },
            onAll: { setAccount(nil) },
            onSelected: { setAccount($0) }
        )
    }

    private var advancedFilters: some View {
        VStack(alignment: .leading, spacing: 10) {
            filterTextField("备注", prompt: "备注包含", text: $store.searchNoteText)
            Text("金额范围").font(.caption).foregroundStyle(.secondary)
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 8) { amountFields }
                VStack(spacing: 8) { amountFields }
            }
            Toggle("限制日期范围", isOn: Binding(
                get: { store.searchDateEnabled },
                set: { store.searchDateEnabled = $0; search() }
            ))
            if store.searchDateEnabled {
                DatePicker("开始日期", selection: $store.searchStartDate, displayedComponents: .date)
                DatePicker("结束日期", selection: $store.searchEndDate, displayedComponents: .date)
            }
        }
    }

    @ViewBuilder
    private var amountFields: some View {
        amountField("最低", text: $store.searchMinimumAmount)
        amountField("最高", text: $store.searchMaximumAmount)
    }

    private func filterTextField(_ title: String, prompt: String, text: Binding<String>) -> some View {
        LabeledContent(title) {
            TextField(prompt, text: text)
                .textFieldStyle(.plain)
                .multilineTextAlignment(.trailing)
                .onSubmit(search)
                .onChange(of: text.wrappedValue) { _ in store.scheduleSearch() }
        }
        .frame(minHeight: 44)
    }

    private func amountField(_ title: String, text: Binding<String>) -> some View {
        HStack(spacing: 6) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text("¥").foregroundStyle(.secondary)
            TextField("0.00", text: text)
                .textFieldStyle(.plain)
                .multilineTextAlignment(.trailing)
                .decimalInputKeyboard()
                .onSubmit(search)
                .onChange(of: text.wrappedValue) { _ in store.scheduleSearch() }
        }
        .padding(.horizontal, 10)
        .frame(maxWidth: .infinity, minHeight: 44)
        .background(Color.secondary.opacity(0.07), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    @ViewBuilder
    private func filterMenu(
        label: String,
        title: String,
        allTitle: String,
        values: [(String, String)],
        onAll: @escaping () -> Void,
        onSelected: @escaping (String) -> Void
    ) -> some View {
        let menu = Menu {
            Button(allTitle, action: onAll)
            ForEach(values.indices, id: \.self) { index in
                Button(values[index].1) { onSelected(values[index].0) }
            }
        } label: {
            VStack(alignment: .leading, spacing: 3) {
                Text(label).font(.caption).foregroundStyle(.secondary)
                HStack(spacing: 4) {
                    Text(title).lineLimit(1).truncationMode(.tail)
                    Spacer(minLength: 4)
                    Image(systemName: "chevron.down").font(.caption2)
                }
                .font(.subheadline.weight(.medium))
            }
            .padding(.horizontal, 10)
            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
            .background(Color.secondary.opacity(0.07), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        }

        #if os(iOS)
        menu.buttonStyle(.plain)
        #else
        menu.buttonStyle(.bordered)
            .controlSize(.small)
        #endif
    }

    private func searchResult(_ item: TransactionUI) -> some View {
        Button { store.showTransactionDetail(item) } label: {
            HStack(spacing: 12) {
                SVGIconView(
                    key: categoryIconAssetKey(item.categoryIconKey ?? "category"),
                    size: 28,
                    tint: (AppThemeColor(rawValue: store.themeColor) ?? .lavender).cssColor(for: colorScheme)
                )
                    .frame(width: 44, height: 44)
                    .background(themeColor.opacity(0.12), in: RoundedRectangle(cornerRadius: 13, style: .continuous))
                VStack(alignment: .leading, spacing: 3) {
                    Text(item.categoryDisplayName).fontWeight(.semibold)
                    Text("\(item.ledgerName) · \(item.accountName)").font(.caption).foregroundStyle(.secondary)
                    Text(store.transactionDateTimeText(item.date)).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                    if !item.note.isEmpty { Text(item.note).font(.caption).lineLimit(1) }
                    if !item.tagNames.isEmpty {
                        Text(item.tagNames.joined(separator: " · ")).font(.caption2).foregroundStyle(themeColor).lineLimit(1)
                    }
                    if item.excluded {
                        Text("未计入收支").font(.caption2.weight(.semibold)).foregroundStyle(.secondary)
                    }
                }
                Spacer()
                Text(item.amountMinor.rmb)
                    .fontWeight(.bold).monospacedDigit()
                    .foregroundStyle(item.type == .expense ? Color.expense : Color.income)
            }
            .padding(12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .liquidGlassSurface(cornerRadius: 18, interactive: true)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(store.transactionDateTimeText(item.date))，\(item.categoryDisplayName)，\(item.type.label)，\(item.amountMinor.rmb)，\(item.accountName)\(item.excluded ? "，未计入收支" : "")")
    }

    private var resultSummary: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("共 \(store.searchResults.count) 笔匹配交易").font(.headline)
            HStack {
                Text("收入 \(store.searchIncomeMinor.rmb)").foregroundStyle(Color.income)
                Spacer()
                Text("支出 \(store.searchExpenseMinor.rmb)").foregroundStyle(Color.expense)
            }
            .font(.subheadline.weight(.semibold))
            if store.searchResults.contains(where: \.excluded) {
                Text("未计入收支的交易显示在列表中，但不参与上方汇总。")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .liquidGlassSurface(cornerRadius: 16)
    }

    private func search() { store.search() }
    private func setType(_ value: EntryType?) { store.searchType = value; search() }
    private func setAccount(_ value: String?) { store.searchAccountID = value; search() }
    private func clear() { store.clearSearch() }
}

private extension View {
    @ViewBuilder
    func decimalInputKeyboard() -> some View {
        #if os(iOS)
        keyboardType(.decimalPad)
        #else
        self
        #endif
    }

    @ViewBuilder
    func searchScrollDismissesKeyboard() -> some View {
        #if os(iOS)
        scrollDismissesKeyboard(.interactively)
        #else
        self
        #endif
    }
}
