import Foundation
import SwiftUI

struct TransactionEditorView: View {
    @EnvironmentObject private var store: AppStore
    @State private var type: EntryType = .expense
    @State private var ledgerID = ""
    @State private var accountID = ""
    @State private var categoryID = ""
    @State private var amount = ""
    @State private var note = ""
    @State private var date = Date()
    @State private var excluded = false
    @State private var saving = false
    @State private var message: String?
    @State private var showingDatePicker = false
    @State private var showingSecondaryEditor = false
    @State private var secondaryName = ""

    var body: some View {
        editorLayout
            .editorNavigationChrome()
            .onAppear(perform: loadDraft)
            .onChange(of: store.transactionDraftRevision) { _ in loadDraft() }
            .onChange(of: ledgerID) { store.selectResourceLedger($0.isEmpty ? nil : $0) }
            .onChange(of: type) { value in
                if let category = store.categories.first(where: { $0.id == categoryID }), category.type != value {
                    categoryID = ""
                }
            }
            .alert("新建二级分类", isPresented: $showingSecondaryEditor) {
                TextField("分类名称", text: $secondaryName)
                Button("取消", role: .cancel) { secondaryName = "" }
                Button("创建") {
                    guard let primaryID = selectedPrimaryID,
                          !secondaryName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
                    store.saveCategory(id: nil, name: secondaryName, type: type, parentID: primaryID)
                    secondaryName = ""
                }
            } message: {
                Text("创建后可在当前一级分类下选择")
            }
    }

    @ViewBuilder
    private var editorLayout: some View {
        #if os(macOS)
        HStack(spacing: 0) {
            ScrollView { editorFields.padding() }
                .frame(maxWidth: 760, maxHeight: .infinity, alignment: .topLeading)
            Divider()
            amountPanel
                .frame(width: 360)
                .frame(maxHeight: .infinity, alignment: .bottom)
        }
        #else
        ScrollView { editorFields.padding() }
            .safeAreaInset(edge: .bottom, spacing: 0) { amountPanel }
        #endif
    }

    private var editorFields: some View {
        VStack(alignment: .leading, spacing: 10) {
            TransactionTopBar(type: $type, ledgerID: $ledgerID, accountID: $accountID)
            TransactionCategoryPicker(
                categories: store.categories,
                type: type,
                selectedID: $categoryID,
                onReordered: { store.reorderPrimaryCategories(type: type, orderedIDs: $0) },
                onAddSecondary: { showingSecondaryEditor = true }
            )
            TransactionTagPicker(tags: store.tags, selectedIDs: Binding(
                get: { store.editingTagIDs },
                set: { store.editingTagIDs = $0 }
            ))
            HStack(spacing: 8) {
                Image(systemName: "note.text").foregroundStyle(.secondary)
                TextField("备注", text: $note).textFieldStyle(.plain)
                DatePicker("时间", selection: $date, displayedComponents: .hourAndMinute)
                    .labelsHidden()
                    .accessibilityLabel("交易时间")
            }
            .padding(.horizontal, 12)
            .frame(minHeight: 40)
            .liquidGlassSurface(cornerRadius: 14)
            HStack(spacing: 8) {
                Button { showingDatePicker.toggle() } label: {
                    Label(
                        Calendar.current.isDateInToday(date) ? "" : date.formatted(.dateTime.month(.twoDigits).day(.twoDigits)),
                        systemImage: "calendar"
                    )
                }
                .buttonStyle(.borderless)
                .popover(isPresented: $showingDatePicker) {
                    DatePicker("日期", selection: $date, displayedComponents: .date)
                        .datePickerStyle(.graphical)
                        .padding()
                }
                Spacer()
                Toggle("不计入统计", isOn: $excluded)
            }
        }
    }

    private var amountPanel: some View {
        TransactionAmountPanel(
            amount: $amount,
            saving: saving,
            message: message,
            onAgain: { save(again: true) },
            onDone: { save(again: false) }
        )
    }

    private func loadDraft() {
        message = nil
        saving = false
        if let item = store.editingTransaction {
            type = item.type
            ledgerID = item.ledgerID
            accountID = item.accountID
            categoryID = item.categoryID
            amount = String(Double(item.amountMinor) / 100)
            note = item.note
            date = item.date
            excluded = item.excluded
            store.selectResourceLedger(item.ledgerID)
            return
        }
        ledgerID = store.defaultLedgerID ?? store.selectedLedgerID ?? store.resourceLedgerID ?? store.ledgers.first?.id ?? ""
        accountID = store.accounts.first(where: { $0.name == "现金" })?.id ?? store.accounts.first?.id ?? ""
        categoryID = ""
        amount = ""
        note = ""
        excluded = false
        date = draftDate(store.draftTransactionDate)
        store.editingTagIDs = []
    }

    private var selectedPrimaryID: String? {
        guard let category = store.categories.first(where: { $0.id == categoryID }) else { return nil }
        return category.parentID ?? category.id
    }

    private func draftDate(_ selectedDay: Date?) -> Date {
        let calendar = Calendar.current
        let now = Date()
        var components = calendar.dateComponents([.year, .month, .day], from: selectedDay ?? now)
        let time = calendar.dateComponents([.hour, .minute], from: now)
        components.hour = time.hour
        components.minute = time.minute
        components.second = 0
        return calendar.date(from: components) ?? now
    }

    private func save(again: Bool) {
        let normalized = amount.replacingOccurrences(of: ",", with: "")
        guard let decimal = evaluateAmount(normalized),
              !ledgerID.isEmpty, !accountID.isEmpty, !categoryID.isEmpty else {
            message = "请选择账本、账户、分类并输入有效金额"
            return
        }
        saving = true
        let minor = NSDecimalNumber(decimal: decimal * 100).int64Value
        store.saveTransaction(
            id: store.editingTransaction?.id,
            ledgerID: ledgerID,
            accountID: accountID,
            categoryID: categoryID,
            amountMinor: minor,
            type: type,
            date: date,
            note: note,
            excluded: excluded,
            tagIDs: store.editingTagIDs
        ) { error in
            saving = false
            message = error
            guard error == nil else { return }
            if again {
                store.editingTransaction = nil
                amount = ""
                categoryID = ""
                note = ""
                excluded = false
                store.editingTagIDs = []
            } else {
                store.editingTransaction = nil
                store.destination = .home
            }
        }
    }
}

private struct TransactionTopBar: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.colorScheme) private var colorScheme
    @Binding var type: EntryType
    @Binding var ledgerID: String
    @Binding var accountID: String

    var body: some View {
        HStack(spacing: 12) {
            Menu {
                ForEach(store.ledgers) { ledger in Button(ledger.name) { ledgerID = ledger.id } }
            } label: {
                Image(systemName: "books.vertical")
                    .font(.title3.weight(.semibold))
                    .frame(width: 44, height: 44)
                    .liquidGlassCircle(interactive: true, tint: .accentColor)
            }
            .accessibilityLabel(store.ledgers.first { $0.id == ledgerID }?.name ?? "选择账本")

            Picker("类型", selection: $type) {
                ForEach(EntryType.allCases) { Text($0.label).tag($0) }
            }
            .pickerStyle(.segmented)
            .frame(maxWidth: 220)

            Menu {
                ForEach(store.accounts) { account in Button(account.name) { accountID = account.id } }
            } label: {
                Group {
                    if let account = store.accounts.first(where: { $0.id == accountID }) {
                        SVGIconView(
                            key: account.iconKey,
                            size: 24,
                            tint: (AppThemeColor(rawValue: store.themeColor) ?? .lavender).cssColor(for: colorScheme)
                        )
                    } else {
                        Image(systemName: "wallet.pass").font(.title3.weight(.semibold))
                    }
                }
                .frame(width: 44, height: 44)
                .liquidGlassCircle(interactive: true, tint: .accentColor)
            }
            .accessibilityLabel(store.accounts.first { $0.id == accountID }?.name ?? "选择账户")
        }
        .frame(maxWidth: .infinity)
    }
}

private struct TransactionCategoryPicker: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.colorScheme) private var colorScheme
    let categories: [CategoryUI]
    let type: EntryType
    @Binding var selectedID: String
    let onReordered: ([String]) -> Void
    let onAddSecondary: () -> Void
    @State private var orderedPrimaryCategories: [CategoryUI] = []
    @State private var draggedCategory: CategoryUI?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if primaryCategories.isEmpty {
                Text("选择账本后加载分类").font(.subheadline).foregroundStyle(.secondary)
            } else {
                LazyVGrid(columns: columns, spacing: 4) {
                    ForEach(orderedPrimaryCategories) { category in
                        CategoryTile(category: category, selected: primaryID == category.id) { selectedID = category.id }
                            .onDrag {
                                draggedCategory = category
                                return NSItemProvider(object: category.id as NSString)
                            }
                            .onDrop(
                                of: ["public.text"],
                                delegate: CategoryDropDelegate(
                                    target: category,
                                    ordered: $orderedPrimaryCategories,
                                    dragged: $draggedCategory,
                                    onReordered: onReordered
                                )
                            )
                    }
                }
            }
            if let primary = selectedPrimary {
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 8) {
                        SVGIconView(
                            key: categoryIconAssetKey(primary.iconKey),
                            size: 26,
                            tint: (AppThemeColor(rawValue: store.themeColor) ?? .lavender).cssColor(for: colorScheme)
                        )
                        Text(primary.name).font(.headline)
                        if let secondary = selectedCategory, secondary.parentID != nil {
                            Text("- \(secondary.name)").fontWeight(.medium).foregroundStyle(.secondary)
                        }
                    }
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(secondaryCategories) { category in
                                Button(category.name) { selectedID = category.id }
                                    .buttonStyle(.bordered)
                                    .tint(selectedID == category.id ? .accentColor : .secondary)
                                    .controlSize(.small)
                            }
                            Button(action: onAddSecondary) { Image(systemName: "plus") }
                                .buttonStyle(.bordered)
                                .controlSize(.small)
                        }
                    }
                }
                .padding(10)
                .liquidGlassSurface(cornerRadius: 16)
            }
        }
        .onAppear { orderedPrimaryCategories = primaryCategories }
        .onChange(of: primaryCategories) { orderedPrimaryCategories = $0 }
    }

    private var primaryCategories: [CategoryUI] { categories.filter { $0.type == type && $0.parentID == nil } }
    private var selectedCategory: CategoryUI? { categories.first { $0.id == selectedID } }
    private var primaryID: String? { selectedCategory?.parentID ?? selectedCategory?.id }
    private var selectedPrimary: CategoryUI? { primaryID.flatMap { id in primaryCategories.first { $0.id == id } } }
    private var secondaryCategories: [CategoryUI] {
        guard let primaryID else { return [] }
        return categories.filter { $0.type == type && $0.parentID == primaryID }
    }
    private var columns: [GridItem] {
        #if os(iOS)
        return Array(repeating: GridItem(.flexible(), spacing: 4), count: 5)
        #else
        return [GridItem(.adaptive(minimum: 76), spacing: 6)]
        #endif
    }
}

private struct CategoryDropDelegate: DropDelegate {
    let target: CategoryUI
    @Binding var ordered: [CategoryUI]
    @Binding var dragged: CategoryUI?
    let onReordered: ([String]) -> Void

    func dropEntered(info: DropInfo) {
        guard let dragged, dragged != target,
              let from = ordered.firstIndex(of: dragged),
              let to = ordered.firstIndex(of: target) else { return }
        withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
            ordered.move(fromOffsets: IndexSet(integer: from), toOffset: to > from ? to + 1 : to)
        }
    }

    func performDrop(info: DropInfo) -> Bool {
        dragged = nil
        onReordered(ordered.map(\.id))
        return true
    }
}

private struct CategoryTile: View {
    @Environment(\.colorScheme) private var colorScheme
    @EnvironmentObject private var store: AppStore
    let category: CategoryUI
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 5) {
                SVGIconView(
                    key: categoryIconAssetKey(category.iconKey),
                    size: 36,
                    tint: (AppThemeColor(rawValue: store.themeColor) ?? .lavender).cssColor(for: colorScheme)
                )
                Text(category.name).font(.caption.weight(selected ? .bold : .medium)).lineLimit(1)
            }
            .frame(maxWidth: .infinity, minHeight: 68)
            .foregroundStyle(.primary)
            .overlay { RoundedRectangle(cornerRadius: 14).stroke(selected ? Color.accentColor : .clear, lineWidth: 1.5) }
        }
        .buttonStyle(.plain)
        .liquidGlassSurface(cornerRadius: 14, interactive: true, tint: selected ? .accentColor : nil)
    }
}

private struct TransactionAmountPanel: View {
    @Binding var amount: String
    let saving: Bool
    let message: String?
    let onAgain: () -> Void
    let onDone: () -> Void
    private let rows = [["1", "2", "3", "+"], ["4", "5", "6", "-"], ["7", "8", "9", "再记"], [".", "0", "退格", "完成"]]

    var body: some View {
        LiquidGlassContainer(spacing: 6) {
            VStack(spacing: 7) {
                HStack {
                    Text("金额").foregroundStyle(.secondary)
                    Spacer()
                    Text("¥\(amount.isEmpty ? "0" : amount)").font(.title.bold().monospacedDigit())
                }
                if let message { Text(message).font(.caption).foregroundStyle(.red).frame(maxWidth: .infinity, alignment: .leading) }
                ForEach(rows, id: \.self) { row in
                    HStack(spacing: 6) { ForEach(row, id: \.self) { key in keypadButton(key) } }
                }
            }
            .padding(12)
        }
    }

    private func keypadButton(_ key: String) -> some View {
        let done = key == "完成"
        let again = key == "再记"
        let operation = key == "+" || key == "-"
        return Button(done && saving ? "保存中" : key) {
            if done { onDone() } else if again { onAgain() } else { press(key) }
        }
        .frame(maxWidth: .infinity, minHeight: 44)
        .foregroundStyle(done ? Color.white : Color.primary)
        .font(.body.weight(done || again || operation ? .bold : .semibold))
        .buttonStyle(.plain)
        .liquidGlassSurface(cornerRadius: 12, interactive: true, tint: done ? .accentColor : again || operation ? .accentColor.opacity(0.55) : nil)
        .disabled(saving)
    }

    private func press(_ key: String) {
        if key == "退格" { amount = String(amount.dropLast()); return }
        if key == ".", amount.split(whereSeparator: { $0 == "+" || $0 == "-" }).last?.contains(".") == true { return }
        if (key == "+" || key == "-") && (amount.isEmpty || amount.last == "+" || amount.last == "-") { return }
        amount += key
    }
}

private func evaluateAmount(_ expression: String) -> Decimal? {
    var total = Decimal.zero
    var current = ""
    var sign = Decimal(1)
    for character in expression {
        if character == "+" || character == "-" {
            guard !current.isEmpty, let value = Decimal(string: current) else { return nil }
            total += sign * value
            current = ""
            sign = character == "+" ? 1 : -1
        } else {
            current.append(character)
        }
    }
    guard !current.isEmpty, let value = Decimal(string: current) else { return nil }
    return total + sign * value
}

private struct TransactionTagPicker: View {
    let tags: [TagUI]
    @Binding var selectedIDs: Set<String>

    var body: some View {
        if !tags.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    Text("标签").font(.caption).foregroundStyle(.secondary)
                    ForEach(tags) { tag in
                        Button(tag.name) {
                            if !selectedIDs.insert(tag.id).inserted { selectedIDs.remove(tag.id) }
                        }
                        .buttonStyle(.bordered)
                        .tint(selectedIDs.contains(tag.id) ? .primary : .secondary)
                        .controlSize(.small)
                    }
                }
            }
        }
    }
}

private extension View {
    @ViewBuilder
    func editorNavigationChrome() -> some View {
        #if os(iOS)
        toolbar(.hidden, for: .navigationBar)
        #else
        self
        #endif
    }
}
