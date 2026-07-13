import SwiftUI

struct TransactionDetailView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.appThemeColor) private var themeColor
    let transaction: TransactionUI
    @State private var confirmingDelete = false
    @State private var deleting = false
    @State private var error: String?
    @State private var loadedDetail: TransactionRecordDetailUI?
    @State private var showingEditor = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    VStack(spacing: 10) {
                        SVGIconView(
                            key: categoryIconAssetKey(transaction.categoryIconKey ?? "category"),
                            size: 42,
                            tint: (AppThemeColor(rawValue: store.themeColor) ?? .lavender).cssColor(for: colorScheme)
                        )
                        Text(transaction.categoryDisplayName)
                            .font(.headline)
                        Text("\(transaction.type == .expense ? "−" : "+")\(transaction.amountMinor.rmb)")
                            .font(.largeTitle.bold().monospacedDigit())
                            .foregroundStyle(transaction.type == .expense ? Color.expense : Color.income)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(20)
                    .liquidGlassSurface(cornerRadius: 22)

                    VStack(spacing: 0) {
                        Group {
                            detailRow("金额", value: transaction.amountMinor.rmb, systemImage: "banknote")
                            Divider()
                            detailRow("类型", value: transaction.type.label, systemImage: "arrow.left.arrow.right")
                            Divider()
                            detailRow("一级分类", value: primaryCategoryName, systemImage: "square.grid.2x2")
                            Divider()
                            detailRow("二级分类", value: secondaryCategoryName, systemImage: "square.grid.2x2")
                            Divider()
                            detailRow("日期", value: store.transactionDateTimeText(transaction.date), systemImage: "calendar", lineLimit: 1)
                            Divider()
                        }
                        Group {
                            detailRow("账户", value: transaction.accountName, systemImage: "wallet.pass")
                            Divider()
                            detailRow("账本", value: transaction.ledgerName, systemImage: "books.vertical")
                            Divider()
                            detailRow("标签", value: tagText, systemImage: "tag")
                            Divider()
                            detailRow("备注", value: transaction.note.isEmpty ? "未设置" : transaction.note, systemImage: "note.text")
                            Divider()
                            detailRow("统计", value: transaction.excluded ? "不计入统计" : "计入统计", systemImage: "chart.bar")
                        }
                        if let source = transaction.sourceDisplayName, !source.isEmpty {
                            Divider()
                            detailRow("来源", value: source, systemImage: "arrow.triangle.2.circlepath")
                        }
                    }
                    .padding(.horizontal, 14)
                    .liquidGlassSurface(cornerRadius: 18)
                    if let error { Text(error).font(.caption).foregroundStyle(.red) }

                    #if os(iOS)
                    HStack(spacing: 0) {
                        Button(role: .destructive) { confirmingDelete = true } label: {
                            Group {
                                if deleting { ProgressView() } else { Image(systemName: "trash") }
                            }
                            .frame(width: 64, height: 44)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(Color.expense)
                        .disabled(deleting)
                        .accessibilityLabel(deleting ? "删除中" : "删除")
                        Divider().frame(height: 24)
                        Button {
                            store.prepareTransactionEdit(transaction)
                            showingEditor = true
                        } label: {
                            Image(systemName: "pencil")
                                .frame(width: 64, height: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(themeColor)
                        .disabled(deleting)
                        .accessibilityLabel("编辑")
                    }
                    .liquidGlassSurface(cornerRadius: 24, interactive: true)
                    #else
                    HStack(spacing: 10) {
                        Button(role: .destructive) { confirmingDelete = true } label: {
                            Label(deleting ? "删除中" : "删除", systemImage: "trash")
                                .frame(maxWidth: .infinity, minHeight: 34)
                        }
                        .buttonStyle(.bordered)
                        .disabled(deleting)
                        Button { store.editSelectedTransaction() } label: {
                            Label("编辑", systemImage: "pencil")
                                .frame(maxWidth: .infinity, minHeight: 34)
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(deleting)
                    }
                    #endif
                }
                .padding()
            }
            .navigationTitle("明细")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭", action: store.dismissTransactionDetail)
                }
            }
        }
        #if os(macOS)
        .frame(minWidth: 460, minHeight: 620)
        #endif
        .confirmationDialog("删除这条明细？", isPresented: $confirmingDelete) {
            Button("删除", role: .destructive) {
                deleting = true
                store.deleteSelectedTransaction { message in
                    deleting = false
                    error = message
                }
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("删除后将无法恢复。")
        }
        .task(id: transaction.id) {
            store.loadTransactionRecordDetail(transaction.id) { detail, message in
                loadedDetail = detail
                error = message
            }
        }
        #if os(iOS)
        .sheet(isPresented: $showingEditor, onDismiss: {
            if store.editingTransaction?.id == transaction.id { store.editingTransaction = nil }
        }) {
            NavigationStack {
                TransactionEditorView()
                    .environmentObject(store)
            }
            .presentationDragIndicator(.visible)
        }
        .onChange(of: store.editingTransaction) { value in
            guard showingEditor, value == nil else { return }
            showingEditor = false
            store.dismissTransactionDetail()
        }
        #endif
    }

    private var primaryCategoryName: String {
        loadedDetail?.primaryCategoryName ?? transaction.primaryCategoryName
    }

    private var secondaryCategoryName: String {
        guard let loadedDetail else { return error == nil ? "加载中…" : "加载失败" }
        return loadedDetail.secondaryCategoryName ?? "未设置"
    }

    private var tagText: String {
        guard let loadedDetail else {
            if !transaction.tagNames.isEmpty { return transaction.tagNames.joined(separator: " · ") }
            return error == nil ? "加载中…" : "加载失败"
        }
        let names = loadedDetail.tagNames
        return names.isEmpty ? "未设置" : names.joined(separator: " · ")
    }

    private func detailRow(_ label: String, value: String, systemImage: String, lineLimit: Int = 2) -> some View {
        HStack(spacing: 12) {
            Image(systemName: systemImage)
                .frame(width: 24)
                .foregroundStyle(themeColor)
            Text(label).foregroundStyle(.secondary)
            Spacer()
            Text(value)
                .fontWeight(.medium)
                .multilineTextAlignment(.trailing)
                .lineLimit(lineLimit)
                .minimumScaleFactor(lineLimit == 1 ? 0.75 : 1)
        }
        .padding(.vertical, 12)
    }
}
