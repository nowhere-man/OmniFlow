import SwiftUI

struct SearchView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.appThemeColor) private var themeColor

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 10) {
                HStack {
                    Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                    TextField("关键词、分类、账户或标签", text: $store.searchText)
                        .textFieldStyle(.plain)
                        .onSubmit { search() }
                    if !store.searchText.isEmpty {
                        Button { store.searchText = ""; search() } label: { Image(systemName: "xmark.circle.fill") }
                            .buttonStyle(.plain)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(.horizontal, 14)
                .frame(minHeight: 44)
                .liquidGlassSurface(cornerRadius: 18, interactive: true)

                VStack(spacing: 8) {
                    HStack {
                        Text("筛选").font(.subheadline.bold())
                        Spacer()
                        if hasFilters { Button("清除", action: clear).buttonStyle(.plain).foregroundStyle(themeColor) }
                    }
                    ThemeSegmentedControl(
                        selection: Binding(get: { store.searchType }, set: setType),
                        options: [EntryType?.none, .some(.expense), .some(.income)]
                    ) { $0?.label ?? "全部" }

                    HStack(spacing: 8) {
                        filterMenu(
                            title: store.searchLedgerID.flatMap { id in store.ledgers.first { $0.id == id }?.name } ?? "所有账本",
                            allTitle: "所有账本",
                            values: store.ledgers.map { ($0.id, $0.name) },
                            onAll: { store.setSearchLedger(nil) },
                            onSelected: { store.setSearchLedger($0) }
                        )
                        filterMenu(
                            title: store.searchAccountID.flatMap { id in store.accounts.first { $0.id == id }?.name } ?? "所有账户",
                            allTitle: "所有账户",
                            values: store.accounts.map { ($0.id, $0.name) },
                            onAll: { setAccount(nil) },
                            onSelected: { setAccount($0) }
                        )
                    }
                    filterTextField("一级分类", text: $store.searchPrimaryCategoryText)
                    filterTextField("二级分类", text: $store.searchSecondaryCategoryText)
                    filterTextField("标签", text: $store.searchTagText)
                    filterTextField("备注", text: $store.searchNoteText)
                    HStack(spacing: 8) {
                        filterTextField("最低金额", text: $store.searchMinimumAmount)
                        filterTextField("最高金额", text: $store.searchMaximumAmount)
                    }
                    HStack(spacing: 8) {
                        Toggle("日期", isOn: Binding(
                            get: { store.searchDateEnabled },
                            set: { store.searchDateEnabled = $0; search() }
                        ))
                        if store.searchDateEnabled {
                            DatePicker("开始", selection: $store.searchStartDate, displayedComponents: .date).labelsHidden()
                            DatePicker("结束", selection: $store.searchEndDate, displayedComponents: .date).labelsHidden()
                        }
                    }
                }
                .padding(12)
                .liquidGlassSurface(cornerRadius: 18)

                if let error = store.error { Text(error).foregroundStyle(.red) }
                if store.searchResults.isEmpty {
                    EmptyStateView(
                        title: hasFilters ? "没有符合条件的交易" : "还没有可搜索的交易",
                        systemImage: "magnifyingglass",
                        detail: hasFilters ? "调整筛选条件后再试" : "交易会显示在这里"
                    )
                } else {
                    LiquidGlassContainer(spacing: 12) {
                        HStack(spacing: 12) {
                            SummaryCard(title: "收入", value: store.searchIncomeMinor.rmb)
                            SummaryCard(title: "支出", value: store.searchExpenseMinor.rmb)
                        }
                    }
                    ForEach(store.searchResults) { item in
                        searchResult(item)
                    }
                }
            }
            .padding()
        }
        #if os(macOS)
        .navigationTitle("搜索")
        #else
        .navigationTitle("")
        #endif
        .onAppear {
            store.prepareSearch()
        }
        .onChange(of: store.searchStartDate) { _ in if store.searchDateEnabled { search() } }
        .onChange(of: store.searchEndDate) { _ in if store.searchDateEnabled { search() } }
    }

    private var hasFilters: Bool {
        !store.searchText.isEmpty || store.searchLedgerID != nil || store.searchType != nil ||
            store.searchAccountID != nil || !store.searchPrimaryCategoryText.isEmpty ||
            !store.searchSecondaryCategoryText.isEmpty || !store.searchTagText.isEmpty ||
            !store.searchNoteText.isEmpty || !store.searchMinimumAmount.isEmpty ||
            !store.searchMaximumAmount.isEmpty || store.searchDateEnabled
    }

    private func filterTextField(_ title: String, text: Binding<String>) -> some View {
        TextField(title, text: text)
            .textFieldStyle(.plain)
            .padding(.horizontal, 12)
            .frame(maxWidth: .infinity, minHeight: 38)
            .iOSLiquidGlassControl(cornerRadius: 12)
            .onSubmit(search)
    }

    @ViewBuilder
    private func filterMenu(
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
            HStack(spacing: 3) {
                Text(title).lineLimit(1).minimumScaleFactor(0.72)
                Image(systemName: "chevron.down").font(.caption2)
            }
            .font(.caption.weight(.medium))
            .frame(maxWidth: .infinity, minHeight: 34)
            .iOSLiquidGlassControl(cornerRadius: 12)
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
                    .liquidGlassSurface(cornerRadius: 13)
                VStack(alignment: .leading, spacing: 3) {
                    Text(item.categoryDisplayName).fontWeight(.semibold)
                    Text("\(item.ledgerName) · \(item.accountName)").font(.caption).foregroundStyle(.secondary)
                    if !item.note.isEmpty { Text(item.note).font(.caption).lineLimit(1) }
                    if !item.tagNames.isEmpty {
                        Text(item.tagNames.joined(separator: " · ")).font(.caption2).foregroundStyle(themeColor).lineLimit(1)
                    }
                }
                Spacer()
                Text(item.amountMinor.rmb)
                    .fontWeight(.bold)
                    .foregroundStyle(item.type == .expense ? Color.expense : themeColor)
            }
            .padding(12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .liquidGlassSurface(cornerRadius: 18, interactive: true)
    }

    private func search() { store.search() }
    private func setType(_ value: EntryType?) { store.searchType = value; search() }
    private func setAccount(_ value: String?) { store.searchAccountID = value; search() }
    private func clear() {
        store.searchText = ""
        store.searchPrimaryCategoryText = ""
        store.searchSecondaryCategoryText = ""
        store.searchTagText = ""
        store.searchNoteText = ""
        store.searchMinimumAmount = ""
        store.searchMaximumAmount = ""
        store.searchDateEnabled = false
        store.searchType = nil
        store.searchAccountID = nil
        store.searchPrimaryCategoryID = nil
        store.searchSecondaryCategoryID = nil
        store.searchTagID = nil
        store.setSearchLedger(nil)
    }
}
