import Foundation
import SwiftUI

struct TransactionEditorView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
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
            .transactionEditorNavigationChrome(
                title: store.editingTransaction == nil ? "新建交易" : "编辑交易",
                onCancel: dismiss.callAsFunction
            )
            .onAppear(perform: loadDraft)
            .onChange(of: store.transactionDraftRevision) { _ in loadDraft() }
            .onChange(of: ledgerID) { value in
                if store.resourceLedgerID != value {
                    categoryID = ""
                    store.clearTransactionTagsForLedgerChange()
                }
                store.selectResourceLedger(value.isEmpty ? nil : value)
            }
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
        VStack(spacing: 0) {
            ScrollView { editorFields.padding(.horizontal, 12).padding(.top, 10) }
            Divider()
            amountPanel
                .padding(.horizontal, 10)
                .padding(.vertical, 8)
                .background(.bar)
        }
        #endif
    }

    private var editorFields: some View {
        VStack(alignment: .leading, spacing: 8) {
            TransactionTopBar(type: $type, ledgerID: $ledgerID, accountID: $accountID)
            TransactionCategoryPicker(
                categories: store.categories,
                type: type,
                selectedID: $categoryID,
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
            .frame(minHeight: 44)
            .liquidGlassSurface(cornerRadius: 14)
            HStack(spacing: 8) {
                Button { showingDatePicker.toggle() } label: {
                    Label(
                        Calendar.current.isDateInToday(date) ? "" : date.formatted(.dateTime.month(.twoDigits).day(.twoDigits)),
                        systemImage: "calendar"
                    )
                }
                .buttonStyle(.borderless)
                .frame(minHeight: 44)
                .accessibilityLabel(Calendar.current.isDateInToday(date) ? "交易日期：今天" : "交易日期：\(date.formatted(date: .abbreviated, time: .omitted))")
                .popover(isPresented: $showingDatePicker) {
                    DatePicker("日期", selection: $date, displayedComponents: .date)
                        .datePickerStyle(.graphical)
                        .padding()
                        .platformPopoverAdaptation()
                }
            }
            Toggle("不计入收支", isOn: $excluded)
                .padding(.horizontal, 12)
                .frame(minHeight: 44)
                .liquidGlassSurface(cornerRadius: 14)
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
            amount = NSDecimalNumber(decimal: Decimal(item.amountMinor) / 100).stringValue
            note = item.note
            date = item.date
            excluded = item.excluded
            store.selectResourceLedger(item.ledgerID)
            return
        }
        ledgerID = store.draftTransactionLedgerID ?? store.defaultLedgerID ?? ""
        accountID = store.accounts.first(where: { $0.type == "CASH" })?.id ?? store.accounts.first?.id ?? ""
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
                dismiss()
            }
        }
    }
}

private struct TransactionTopBar: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.appThemeColor) private var themeColor
    @Binding var type: EntryType
    @Binding var ledgerID: String
    @Binding var accountID: String

    @ViewBuilder
    var body: some View {
        #if os(iOS)
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                ledgerMenu
                accountMenu
            }
            ThemeSegmentedControl(selection: $type, options: EntryType.allCases, title: \.label)
        }
        #else
        HStack(spacing: 12) {
            ledgerMenu
            ThemeSegmentedControl(selection: $type, options: EntryType.allCases, title: \.label)
                .frame(maxWidth: 220)
            accountMenu
        }
        .frame(maxWidth: .infinity)
        #endif
    }

    private var ledgerMenu: some View {
        Menu {
            ForEach(store.ledgers) { ledger in Button(ledger.name) { ledgerID = ledger.id } }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "books.vertical")
                    .font(.headline)
                    .foregroundStyle(themeColor)
                Text(selectedLedgerName).lineLimit(1)
                Spacer(minLength: 0)
                Image(systemName: "chevron.up.chevron.down").font(.caption2).foregroundStyle(.secondary)
            }
            .padding(.horizontal, 10)
            .frame(maxWidth: .infinity, minHeight: 44)
            .liquidGlassSurface(cornerRadius: 14, interactive: true)
        }
        .accessibilityLabel("账本：\(selectedLedgerName)")
    }

    private var accountMenu: some View {
        Menu {
            ForEach(store.accounts) { account in Button(account.name) { accountID = account.id } }
        } label: {
            HStack(spacing: 8) {
                if let account = selectedAccount {
                    SVGIconView(
                        key: account.iconKey,
                        size: 22,
                        tint: (AppThemeColor(rawValue: store.themeColor) ?? .lavender).cssColor(for: colorScheme)
                    )
                } else {
                    Image(systemName: "wallet.pass").font(.headline)
                }
                Text(selectedAccount?.name ?? "选择账户").lineLimit(1)
                Spacer(minLength: 0)
                Image(systemName: "chevron.up.chevron.down").font(.caption2).foregroundStyle(.secondary)
            }
            .padding(.horizontal, 10)
            .frame(maxWidth: .infinity, minHeight: 44)
            .liquidGlassSurface(cornerRadius: 14, interactive: true)
        }
        .accessibilityLabel("账户：\(selectedAccount?.name ?? "未选择")")
    }

    private var selectedLedgerName: String { store.ledgers.first { $0.id == ledgerID }?.name ?? "选择账本" }
    private var selectedAccount: AccountUI? { store.accounts.first { $0.id == accountID } }
}

private struct TransactionCategoryPicker: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.colorScheme) private var colorScheme
    let categories: [CategoryUI]
    let type: EntryType
    @Binding var selectedID: String
    let onAddSecondary: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if primaryCategories.isEmpty {
                Text("选择账本后加载分类").font(.subheadline).foregroundStyle(.secondary)
            } else {
                #if os(iOS)
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHGrid(rows: rows, spacing: 6) { categoryTiles }
                        .padding(.horizontal, 2)
                }
                .frame(height: 156)
                #else
                LazyVGrid(columns: columns, spacing: 6) { categoryTiles }
                #endif
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
                                    .buttonStyle(SelectablePillButtonStyle(selected: selectedID == category.id))
                            }
                            Button(action: onAddSecondary) { Image(systemName: "plus") }
                                .buttonStyle(SelectablePillButtonStyle(selected: false))
                        }
                    }
                }
                .padding(10)
                .liquidGlassSurface(cornerRadius: 16)
            }
        }
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
        return Array(repeating: GridItem(.flexible(), spacing: 6), count: 4)
        #else
        return [GridItem(.adaptive(minimum: 76), spacing: 6)]
        #endif
    }
    private var rows: [GridItem] { Array(repeating: GridItem(.fixed(75), spacing: 6), count: 2) }

    @ViewBuilder private var categoryTiles: some View {
        ForEach(primaryCategories) { category in
            CategoryTile(
                category: category,
                selected: primaryID == category.id
            ) { selectedID = category.id }
                #if os(iOS)
                .frame(width: 82)
                #endif
        }
    }
}

private struct CategoryTile: View {
    @Environment(\.colorScheme) private var colorScheme
    @EnvironmentObject private var store: AppStore
    @Environment(\.appThemeColor) private var themeColor
    @Environment(\.appThemeSelectionForeground) private var selectedForeground
    let category: CategoryUI
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 5) {
                SVGIconView(
                    key: categoryIconAssetKey(category.iconKey),
                    size: 36,
                    tint: selected
                        ? (AppThemeColor(rawValue: store.themeColor) ?? .lavender).selectionCSSColor(for: colorScheme)
                        : (AppThemeColor(rawValue: store.themeColor) ?? .lavender).cssColor(for: colorScheme)
                )
                Text(category.name).font(.caption.weight(selected ? .bold : .medium)).lineLimit(1)
            }
            .frame(maxWidth: .infinity, minHeight: 72)
            .foregroundStyle(selected ? selectedForeground : Color.primary)
            .overlay { RoundedRectangle(cornerRadius: 14).stroke(selected ? Color.clear : Color.secondary.opacity(0.16), lineWidth: 1) }
        }
        .buttonStyle(.plain)
        .liquidGlassSurface(cornerRadius: 14, interactive: true, tint: selected ? themeColor : nil)
        .accessibilityLabel(category.name)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

private struct TransactionAmountPanel: View {
    @Environment(\.appThemeColor) private var themeColor
    @Environment(\.appThemeSelectionForeground) private var selectedForeground
    @Binding var amount: String
    let saving: Bool
    let message: String?
    let onAgain: () -> Void
    let onDone: () -> Void
    private let rows = [["1", "2", "3", "+"], ["4", "5", "6", "-"], ["7", "8", "9", "再记"], [".", "0", "退格", "完成"]]

    @ViewBuilder
    var body: some View {
        #if os(macOS)
        VStack(alignment: .leading, spacing: 16) {
            Text("金额").font(.headline)
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text("¥").font(.title2.weight(.semibold)).foregroundStyle(.secondary)
                TextField("0.00", text: $amount)
                    .textFieldStyle(.plain)
                    .font(.largeTitle.bold().monospacedDigit())
                    .multilineTextAlignment(.trailing)
            }
            .padding(14)
            .liquidGlassSurface(cornerRadius: 14, interactive: true)
            if let message { Text(message).font(.caption).foregroundStyle(.red) }
            Spacer()
            HStack {
                Button("再记", action: onAgain)
                    .buttonStyle(.bordered)
                Spacer()
                Button(saving ? "保存中" : "完成", action: onDone)
                    .buttonStyle(.borderedProminent)
                    .keyboardShortcut(.defaultAction)
            }
            .disabled(saving)
        }
        .padding(18)
        #else
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
        #endif
    }

    private func keypadButton(_ key: String) -> some View {
        let done = key == "完成"
        let again = key == "再记"
        let operation = key == "+" || key == "-"
        return Button(done && saving ? "保存中" : key) {
            if done { onDone() } else if again { onAgain() } else { press(key) }
        }
        .frame(maxWidth: .infinity, minHeight: 50)
        .foregroundStyle(done ? selectedForeground : operation ? themeColor : Color.primary)
        .font(done || again || key == "退格" ? .headline.weight(.bold) : .title2.weight(.bold))
        .buttonStyle(.plain)
        .liquidGlassSurface(
            cornerRadius: 12,
            interactive: true,
            tint: done ? themeColor : again ? themeColor.opacity(0.16) : nil
        )
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
        HStack(spacing: 8) {
            Image(systemName: "tag").foregroundStyle(.secondary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    if tags.isEmpty {
                        Text("暂无标签").font(.caption).foregroundStyle(.secondary)
                    } else {
                        ForEach(tags) { tag in
                            Button(tag.name) {
                                if !selectedIDs.insert(tag.id).inserted { selectedIDs.remove(tag.id) }
                            }
                            .buttonStyle(SelectablePillButtonStyle(selected: selectedIDs.contains(tag.id)))
                        }
                    }
                }
            }
        }
        .padding(.horizontal, 10)
        .frame(minHeight: 44)
        .liquidGlassSurface(cornerRadius: 13)
    }
}

private extension View {
    @ViewBuilder
    func transactionEditorNavigationChrome(title: String, onCancel: @escaping () -> Void) -> some View {
        #if os(iOS)
        navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消", action: onCancel)
                }
            }
        #else
        self
        #endif
    }
}
