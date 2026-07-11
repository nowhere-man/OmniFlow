import SwiftUI

struct TransactionDetailView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.appThemeColor) private var themeColor
    let transaction: TransactionUI
    @State private var confirmingDelete = false
    @State private var deleting = false
    @State private var error: String?

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
                            .foregroundStyle(transaction.type == .expense ? Color.red : themeColor)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(20)
                    .liquidGlassSurface(cornerRadius: 22)

                    VStack(spacing: 0) {
                        detailRow("金额", value: transaction.amountMinor.rmb, systemImage: "banknote")
                        Divider()
                        detailRow("类型", value: transaction.type.label, systemImage: "arrow.left.arrow.right")
                        Divider()
                        detailRow("日期", value: transaction.date.formatted(date: .numeric, time: .shortened), systemImage: "calendar")
                        Divider()
                        detailRow("账户", value: transaction.accountName, systemImage: "wallet.pass")
                        Divider()
                        detailRow("账本", value: transaction.ledgerName, systemImage: "books.vertical")
                        Divider()
                        detailRow("统计", value: transaction.excluded ? "不计入统计" : "计入统计", systemImage: "chart.bar")
                        if let source = transaction.sourceDisplayName, !source.isEmpty {
                            Divider()
                            detailRow("来源", value: source, systemImage: "arrow.triangle.2.circlepath")
                        }
                    }
                    .padding(.horizontal, 14)
                    .liquidGlassSurface(cornerRadius: 18)

                    if !transaction.note.isEmpty {
                        detailRow("备注", value: transaction.note, systemImage: "note.text")
                            .padding(14)
                            .liquidGlassSurface(cornerRadius: 18)
                    }
                    if !transaction.tagNames.isEmpty {
                        detailRow("标签", value: transaction.tagNames.joined(separator: " · "), systemImage: "tag")
                            .padding(14)
                            .liquidGlassSurface(cornerRadius: 18)
                    }
                    if let error { Text(error).font(.caption).foregroundStyle(.red) }

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
    }

    private func detailRow(_ label: String, value: String, systemImage: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: systemImage)
                .frame(width: 24)
                .foregroundStyle(.tint)
            Text(label).foregroundStyle(.secondary)
            Spacer()
            Text(value).fontWeight(.medium).multilineTextAlignment(.trailing)
        }
        .padding(.vertical, 12)
    }
}
