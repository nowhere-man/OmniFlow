import SwiftUI

struct SearchView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 10) {
                HStack {
                    Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                    TextField("关键词、分类、账户或标签", text: $store.searchText)
                        .textFieldStyle(.plain)
                        .onSubmit(perform: search)
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
                        if hasFilters { Button("清除", action: clear).buttonStyle(.plain).foregroundStyle(.tint) }
                    }
                    Picker("类型", selection: Binding(get: { store.searchType }, set: setType)) {
                        Text("全部").tag(EntryType?.none)
                        Text("支出").tag(Optional(EntryType.expense))
                        Text("收入").tag(Optional(EntryType.income))
                    }
                    .pickerStyle(.segmented)

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
                    HStack(spacing: 6) {
                        filterMenu(
                            title: selectedCategoryName(store.searchPrimaryCategoryID) ?? "所有一级分类",
                            allTitle: "所有一级分类",
                            values: primaryCategories.map { ($0.id, $0.name) },
                            onAll: { setPrimary(nil) },
                            onSelected: { setPrimary($0) }
                        )
                        filterMenu(
                            title: selectedCategoryName(store.searchSecondaryCategoryID) ?? "所有二级分类",
                            allTitle: "所有二级分类",
                            values: secondaryCategories.map { ($0.id, $0.name) },
                            onAll: { setSecondary(nil) },
                            onSelected: { setSecondary($0) }
                        )
                        filterMenu(
                            title: store.searchTagID.flatMap { id in store.searchTags.first { $0.id == id }?.name } ?? "所有标签",
                            allTitle: "所有标签",
                            values: store.searchTags.map { ($0.id, $0.name) },
                            onAll: { setTag(nil) },
                            onSelected: { setTag($0) }
                        )
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
        .navigationTitle("")
        .onAppear {
            store.prepareSearch()
        }
    }

    private var primaryCategories: [CategoryUI] {
        store.searchCategories.filter { $0.parentID == nil }
    }

    private var secondaryCategories: [CategoryUI] {
        store.searchCategories.filter { category in
            guard let parentID = category.parentID else { return false }
            return store.searchPrimaryCategoryID == nil || parentID == store.searchPrimaryCategoryID
        }
    }

    private var hasFilters: Bool {
        !store.searchText.isEmpty || store.searchLedgerID != nil || store.searchType != nil ||
            store.searchAccountID != nil || store.searchPrimaryCategoryID != nil ||
            store.searchSecondaryCategoryID != nil || store.searchTagID != nil
    }

    private func filterMenu(
        title: String,
        allTitle: String,
        values: [(String, String)],
        onAll: @escaping () -> Void,
        onSelected: @escaping (String) -> Void
    ) -> some View {
        Menu {
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
        }
        .buttonStyle(.bordered)
        .controlSize(.small)
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
                    .liquidGlassSurface(cornerRadius: 13, tint: .accentColor)
                VStack(alignment: .leading, spacing: 3) {
                    Text(item.categoryDisplayName).fontWeight(.semibold)
                    Text("\(item.ledgerName) · \(item.accountName)").font(.caption).foregroundStyle(.secondary)
                    if !item.note.isEmpty { Text(item.note).font(.caption).lineLimit(1) }
                    if !item.tagNames.isEmpty {
                        Text(item.tagNames.joined(separator: " · ")).font(.caption2).foregroundStyle(.tint).lineLimit(1)
                    }
                }
                Spacer()
                Text(item.amountMinor.rmb)
                    .fontWeight(.bold)
                    .foregroundStyle(item.type == .expense ? Color.red : Color.accentColor)
            }
            .padding(12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .liquidGlassSurface(cornerRadius: 18, interactive: true)
    }

    private func selectedCategoryName(_ id: String?) -> String? {
        id.flatMap { selected in store.searchCategories.first { $0.id == selected }?.name }
    }

    private func search() { store.search() }
    private func setType(_ value: EntryType?) { store.searchType = value; search() }
    private func setAccount(_ value: String?) { store.searchAccountID = value; search() }
    private func setPrimary(_ value: String?) { store.searchPrimaryCategoryID = value; store.searchSecondaryCategoryID = nil; search() }
    private func setSecondary(_ value: String?) { store.searchSecondaryCategoryID = value; search() }
    private func setTag(_ value: String?) { store.searchTagID = value; search() }

    private func clear() {
        store.searchText = ""
        store.searchType = nil
        store.searchAccountID = nil
        store.searchPrimaryCategoryID = nil
        store.searchSecondaryCategoryID = nil
        store.searchTagID = nil
        store.setSearchLedger(nil)
    }
}
