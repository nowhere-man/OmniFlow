import SwiftUI
import UniformTypeIdentifiers

struct MoreView: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("净资产").font(.caption.weight(.medium))
                    Text(store.accounts.filter(\.includeInTotalAssets).map(\.balanceMinor).reduce(0, +).rmb).font(.largeTitle.bold())
                    Label(syncStatus, systemImage: "arrow.triangle.2.circlepath")
                        .font(.caption)
                }
                .foregroundStyle(.primary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
                .liquidGlassSurface(cornerRadius: 18)
                ModuleSection(title: "数据", modules: [("数据管理", "arrow.triangle.2.circlepath"), ("导入", "square.and.arrow.down"), ("导出", "square.and.arrow.up"), ("设置", "gearshape")])
                ModuleSection(title: "账本与账户", modules: [("账本", "books.vertical"), ("账户", "wallet.pass"), ("资产", "chart.pie"), ("分类管理", "square.grid.2x2"), ("标签管理", "tag")])
                ModuleSection(title: "自动化", modules: [("规则", "list.bullet.rectangle"), ("提醒", "bell")])
            }
            .padding()
        }
        .navigationTitle("更多")
        .onAppear(perform: store.loadBackups)
    }

    private var syncStatus: String {
        switch store.syncPhase {
        case "RUNNING": return "正在同步 \(Int((store.syncProgress ?? 0) * 100))%"
        case "SUCCESS": return "最近同步 \(store.syncLastBackupAt ?? "刚刚")"
        case "ERROR": return store.syncError ?? "同步失败"
        default:
            if let last = store.syncLastBackupAt { return "最近同步 \(last)" }
            if let last = store.backups.first?.createdAt { return "最近同步 \(last)" }
            return "尚未同步"
        }
    }
}

private struct ModuleSection: View {
    @Environment(\.appThemeColor) private var themeColor
    let title: String
    let modules: [(String, String)]

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.headline)
            VStack(spacing: 0) {
                ForEach(modules, id: \.0) { module in
                    NavigationLink { ModuleView(title: module.0) } label: {
                        HStack(spacing: 12) {
                            Image(systemName: module.1)
                                .frame(width: 24)
                                .foregroundStyle(themeColor)
                            Text(module.0).foregroundStyle(.primary)
                            Spacer()
                            Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
                        }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 16)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    if module.0 != modules.last?.0 { Divider() }
                }
            }
            .liquidGlassSurface(cornerRadius: 16)
        }
    }
}

struct DataManagementView: View {
    @EnvironmentObject private var store: AppStore
    @AppStorage("syncTarget") private var syncTarget = "ICLOUD"
    @AppStorage("webdav.endpoint") private var endpoint = ""
    @AppStorage("webdav.username") private var username = ""
    @AppStorage("backupRetention") private var retention = 10
    @State private var password = ""
    @State private var restoreID: String?
    var body: some View {
        Form {
            Section("全量备份") {
                if store.syncPhase == "RUNNING" {
                    ProgressView(value: store.syncProgress ?? 0) {
                        Text("正在同步")
                    }
                } else if store.syncPhase == "ERROR" {
                    Label(store.syncError ?? "同步失败", systemImage: "exclamationmark.triangle")
                        .foregroundStyle(.red)
                } else if let last = store.syncLastBackupAt {
                    Label("最近备份 \(last)", systemImage: "checkmark.icloud")
                        .foregroundStyle(.secondary)
                }
                Picker("同步目标", selection: $syncTarget) {
                    Text("iCloud").tag("ICLOUD")
                    Text("WebDAV").tag("WEBDAV")
                }
                Stepper("最多保留 \(retention) 个备份", value: $retention, in: 1...100)
                if syncTarget == "WEBDAV" {
                    TextField("服务器目录 URL", text: $endpoint)
                    TextField("用户名", text: $username)
                    SecureField("密码", text: $password)
                    Button("保存 WebDAV 凭据") { store.configureWebDav(endpoint: endpoint, username: username, password: password) }
                }
                Button("立即创建备份") {
                    if syncTarget == "WEBDAV" { store.configureWebDav(endpoint: endpoint, username: username, password: password) }
                    store.syncNow(target: syncTarget, retention: retention)
                }
                .disabled(store.syncPhase == "RUNNING")
                Button("刷新备份列表", action: store.loadBackups)
                ForEach(store.backups) { backup in
                    HStack {
                        Text(backup.createdAt)
                        Spacer()
                        Button("恢复") { restoreID = backup.id }
                    }
                }
            }
            Section("数据互通") {
                NavigationLink("导入账单") { ImportView() }
                NavigationLink("导出青子记账 JSON") { ExportView() }
            }
            if let error = store.dataManagementError { Text(error).foregroundStyle(.red) }
        }
        .formStyle(.grouped)
        .navigationTitle("数据管理")
        .onAppear {
            password = KeychainPassword.load()
            store.dataManagementError = nil
            store.loadBackups()
        }
        .confirmationDialog("完整恢复会替换当前全部可恢复数据", isPresented: Binding(get: { restoreID != nil }, set: { if !$0 { restoreID = nil } })) {
            Button("先备份当前数据") { store.syncNow(target: syncTarget, retention: retention) }
            Button("确认恢复", role: .destructive) { if let restoreID { store.restoreBackup(id: restoreID) }; restoreID = nil }
            Button("取消", role: .cancel) { restoreID = nil }
        }
    }
}

struct ModuleView: View {
    let title: String

    @ViewBuilder
    var body: some View {
        switch title {
        case "数据管理": DataManagementView()
        case "设置": SettingsView().navigationTitle(title)
        case "导入": ImportView()
        case "导出": ExportView()
        case "账本": LedgerManagementView()
        case "账户": AccountManagementView()
        case "资产": AssetView()
        case "分类管理": CategoryManagementView()
        case "标签管理": TagManagementView()
        case "规则": RuleManagementView()
        case "提醒": ReminderManagementView()
        default: EmptyStateView(title: title, systemImage: "square.grid.2x2", detail: "暂无内容")
        }
    }
}

private struct LedgerManagementView: View {
    @EnvironmentObject private var store: AppStore
    @State private var editing: LedgerUI?
    @State private var showingEditor = false
    var body: some View {
        ManagementContainer(title: "账本", add: { showingEditor = true }) {
            ForEach(store.ledgers) { ledger in
                ManagementRow(
                    title: ledger.name,
                    subtitle: store.defaultLedgerID == ledger.id ? "默认账本" : "",
                    edit: { editing = ledger; showingEditor = true },
                    delete: { store.deleteLedger(ledger.id) },
                    auxiliaryTitle: store.defaultLedgerID == ledger.id ? "取消默认" : "设为默认",
                    auxiliary: { store.setDefaultLedger(store.defaultLedgerID == ledger.id ? nil : ledger.id) }
                )
            }
        }
        .sheet(isPresented: $showingEditor, onDismiss: { editing = nil }) {
            LedgerEditor(ledger: editing) { name, coverKey in
                store.saveLedger(id: editing?.id, name: name, coverKey: coverKey)
                showingEditor = false
            }
            .managementEditorSheet(title: editing == nil ? "新建账本" : "编辑账本") { showingEditor = false }
        }
    }
}

private struct AccountManagementView: View {
    @EnvironmentObject private var store: AppStore
    @State private var editing: AccountUI?
    @State private var showingEditor = false
    var body: some View {
        ManagementContainer(title: "账户", add: { showingEditor = true }) {
            ForEach(store.accounts) { account in
                ManagementRow(title: account.name, subtitle: account.balanceMinor.rmb, edit: { editing = account; showingEditor = true }, delete: { store.deleteAccount(account.id) })
            }
        }
        .sheet(isPresented: $showingEditor, onDismiss: { editing = nil }) {
            AccountEditor(account: editing) { name, balance, type, iconKey, cardNumber, note, included in
                store.saveAccount(
                    id: editing?.id,
                    name: name,
                    balanceMinor: balance,
                    type: type,
                    iconKey: iconKey,
                    cardNumber: cardNumber,
                    note: note,
                    included: included
                )
                showingEditor = false
            }
            .managementEditorSheet(title: editing == nil ? "新建账户" : "编辑账户") { showingEditor = false }
        }
    }
}

private struct AssetView: View {
    @EnvironmentObject private var store: AppStore
    var body: some View {
        List {
            let included = store.accounts.filter(\.includeInTotalAssets)
            let assets = included.map(\.balanceMinor).filter { $0 > 0 }.reduce(0, +)
            let liabilities = included.map(\.balanceMinor).filter { $0 < 0 }.reduce(0) { $0 - $1 }
            Section("资产概览") {
                HStack { Text("资产"); Spacer(); Text(assets.rmb) }
                HStack { Text("负债"); Spacer(); Text(liabilities.rmb) }
                HStack { Text("净资产").fontWeight(.semibold); Spacer(); Text((assets - liabilities).rmb).font(.title3.bold()) }
            }
            Section { NavigationLink("管理账户") { AccountManagementView() } }
            Section("账户") {
                ForEach(store.accounts.filter(\.includeInTotalAssets)) { account in
                    HStack { Text(account.name); Spacer(); Text(account.balanceMinor.rmb) }
                }
            }
        }.navigationTitle("资产")
    }
}

private struct CategoryManagementView: View {
    @EnvironmentObject private var store: AppStore
    @State private var editing: CategoryUI?
    @State private var showingEditor = false
    var body: some View {
        ManagementContainer(title: "分类管理", add: { showingEditor = true }) {
            LedgerResourcePicker()
            ForEach(EntryType.allCases) { type in
                Section("\(type.label)一级分类") {
                    ForEach(primaryCategories(type)) { category in
                        ManagementRow(
                            title: category.name,
                            subtitle: "一级分类",
                            edit: { editing = category; showingEditor = true },
                            delete: { store.deleteCategory(category.id) }
                        )
                    }
                    .onMove { offsets, destination in move(type, offsets, destination) }
                }
            }
            if !secondaryCategories.isEmpty {
                Section("二级分类") {
                    ForEach(secondaryCategories) { category in
                        let parent = store.categories.first { $0.id == category.parentID }
                        ManagementRow(
                            title: category.name,
                            subtitle: [parent?.name, category.type.label].compactMap { $0 }.joined(separator: " · "),
                            edit: { editing = category; showingEditor = true },
                            delete: { store.deleteCategory(category.id) }
                        )
                    }
                }
            }
        }
        .toolbar {
            #if os(iOS)
            EditButton()
            #endif
        }
        .sheet(isPresented: $showingEditor, onDismiss: { editing = nil }) {
            CategoryEditor(category: editing) { name, type, parentID, iconKey in
                store.saveCategory(id: editing?.id, name: name, type: type, parentID: parentID, iconKey: iconKey)
                showingEditor = false
            }
            .managementEditorSheet(title: editing == nil ? "新建分类" : "编辑分类") { showingEditor = false }
        }
    }

    private func primaryCategories(_ type: EntryType) -> [CategoryUI] {
        store.categories.filter { $0.parentID == nil && $0.type == type }
    }

    private var secondaryCategories: [CategoryUI] { store.categories.filter { $0.parentID != nil } }

    private func move(_ type: EntryType, _ offsets: IndexSet, _ destination: Int) {
        var ordered = primaryCategories(type)
        ordered.move(fromOffsets: offsets, toOffset: destination)
        store.reorderPrimaryCategories(type: type, orderedIDs: ordered.map(\.id))
    }
}

private struct TagManagementView: View {
    @EnvironmentObject private var store: AppStore
    @State private var editing: TagUI?
    @State private var showingEditor = false
    var body: some View {
        ManagementContainer(title: "标签管理", add: { showingEditor = true }) {
            LedgerResourcePicker()
            ForEach(store.tags) { tag in ManagementRow(title: tag.name, edit: { editing = tag; showingEditor = true }, delete: { store.deleteTag(tag.id) }) }
        }
        .sheet(isPresented: $showingEditor, onDismiss: { editing = nil }) {
            NameEditor(title: editing == nil ? "新建标签" : "编辑标签", initial: editing?.name ?? "") {
                store.saveTag(id: editing?.id, name: $0); showingEditor = false
            }
            .managementEditorSheet(title: editing == nil ? "新建标签" : "编辑标签") { showingEditor = false }
        }
    }
}

private struct RuleManagementView: View {
    @EnvironmentObject private var store: AppStore
    @State private var showingEditor = false
    @State private var editing: RuleUI?
    @State private var deleting: RuleUI?
    var body: some View {
        ManagementContainer(title: "规则", add: { showingEditor = true }) {
            LedgerResourcePicker()
            let ordered = store.rules.sorted { $0.priority < $1.priority }
            ForEach(Array(ordered.enumerated()), id: \.element.id) { index, rule in
                HStack {
                    VStack(alignment: .leading) {
                        Text(rule.name)
                        Text("\(rule.conditionValue) → 分类").font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Menu {
                        Button("上移") { store.moveRule(rule.id, offset: -1) }.disabled(index == 0)
                        Button("下移") { store.moveRule(rule.id, offset: 1) }.disabled(index == ordered.count - 1)
                        Divider()
                        Button("编辑") { editing = rule; showingEditor = true }
                        Button("删除", role: .destructive) { deleting = rule }
                    } label: {
                        Image(systemName: "ellipsis.circle").font(.title3)
                    }
                }
                .padding(.vertical, 4)
            }
        }
        .sheet(isPresented: $showingEditor, onDismiss: { editing = nil }) {
            RuleEditor(rule: editing) { name, conditionType, conditionValue, actionType, actionValue, priority in
                store.saveRule(id: editing?.id, name: name, conditionType: conditionType, conditionValue: conditionValue, actionType: actionType, actionValue: actionValue, priority: priority)
                showingEditor = false
            }
            .managementEditorSheet(title: editing == nil ? "新建规则" : "编辑规则") { showingEditor = false }
        }
        .confirmationDialog(
            "确认删除“\(deleting?.name ?? "")”？",
            isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }),
            titleVisibility: .visible
        ) {
            Button("删除", role: .destructive) {
                if let deleting { store.deleteRule(deleting.id) }
                deleting = nil
            }
            Button("取消", role: .cancel) { deleting = nil }
        } message: {
            Text("此操作无法撤销。")
        }
    }
}

private struct LedgerResourcePicker: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        Picker("账本", selection: Binding(get: { store.resourceLedgerID }, set: store.selectResourceLedger)) {
            Text("请选择").tag(String?.none)
            ForEach(store.ledgers) { Text($0.name).tag(Optional($0.id)) }
        }
    }
}

private struct ReminderManagementView: View {
    @EnvironmentObject private var store: AppStore
    @State private var showingEditor = false
    @State private var editing: ReminderUI?
    var body: some View {
        ManagementContainer(title: "提醒", add: { showingEditor = true }) {
            ForEach(store.reminders) { reminder in
                HStack {
                    Toggle(isOn: Binding(get: { !reminder.paused }, set: { store.setReminderPaused(reminder.id, paused: !$0) })) { EmptyView() }
                        .labelsHidden()
                        .accessibilityLabel("\(reminder.name)提醒")
                        .accessibilityValue(reminder.paused ? "已停用" : "已启用")
                    ManagementRow(title: reminder.name, subtitle: reminder.scheduleKind, edit: { editing = reminder; showingEditor = true }, delete: { store.deleteReminder(reminder.id) })
                }
            }
        }
        .sheet(isPresented: $showingEditor, onDismiss: { editing = nil }) {
            ReminderEditor(reminder: editing) { name, type, amount, schedule, day, daysAfter, weekday, month, paused in
                store.saveReminder(
                    id: editing?.id,
                    name: name,
                    type: type,
                    amountMinor: amount,
                    schedule: schedule,
                    dayOfMonth: day,
                    daysAfter: daysAfter,
                    dayOfWeek: weekday,
                    month: month,
                    paused: paused
                )
                showingEditor = false
            }
            .managementEditorSheet(title: editing == nil ? "新建提醒" : "编辑提醒") { showingEditor = false }
        }
    }
}

private struct ImportView: View {
    @EnvironmentObject private var store: AppStore
    @State private var importing = false
    @State private var batchCategoryID = ""
    @State private var selectedFormat: AppleImportFormat?
    var body: some View {
        List {
            Section {
                Picker("目标账本", selection: Binding(get: { store.resourceLedgerID }, set: { store.selectResourceLedger($0) })) {
                    Text("请选择").tag(String?.none)
                    ForEach(store.ledgers) { Text($0.name).tag(Optional($0.id)) }
                }
                Picker("账单来源", selection: $selectedFormat) {
                    Text("自动识别").tag(AppleImportFormat?.none)
                    ForEach(AppleImportFormat.allCases) { Text($0.label).tag(Optional($0)) }
                }
                Button("选择账单文件") { importing = true }
                ProgressView(value: store.importProgress)
            }
            if !store.importItems.isEmpty {
                Section("批量操作") {
                    HStack {
                        Button("全选", action: store.selectAllImportItems)
                        Button("反选", action: store.invertImportSelection)
                        Spacer()
                        Text("已选 \(store.selectedImportItemIDs.count) 条").foregroundStyle(.secondary)
                    }
                    Picker("批量分类", selection: $batchCategoryID) {
                        Text("请选择").tag("")
                        ForEach(store.categories) { Text($0.name).tag($0.id) }
                    }
                    .onChange(of: batchCategoryID) { value in
                        guard !value.isEmpty else { return }
                        store.setSelectedImportCategory(value)
                        batchCategoryID = ""
                    }
                    HStack {
                        Button("批量排除") { store.setSelectedImportSkipped(true) }
                        Button("恢复入账") { store.setSelectedImportSkipped(false) }
                    }
                    .disabled(store.selectedImportItemIDs.isEmpty)
                }
            }
            Section("预览") {
                ForEach($store.importItems) { $item in
                    VStack(alignment: .leading) {
                        Toggle(
                            isOn: Binding(
                                get: { store.selectedImportItemIDs.contains(item.id) },
                                set: { _ in store.toggleImportSelection(item.id) }
                            )
                        ) {
                            HStack { Text(item.note.isEmpty ? "无备注" : item.note); Spacer(); Text(item.amountMinor.rmb) }
                        }
                        Text("\(item.date.formatted(date: .abbreviated, time: .shortened)) · \(item.source)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        if item.duplicate != "NONE" { Text(item.duplicate).foregroundStyle(.red) }
                        Picker("类型", selection: Binding(get: { item.type ?? .expense }, set: { item.type = $0; store.updateImportItem(item) })) {
                            ForEach(EntryType.allCases) { Text($0.label).tag($0) }
                        }
                        Picker("分类", selection: Binding(get: { item.categoryID ?? "" }, set: { item.categoryID = $0; store.updateImportItem(item) })) {
                            Text("请选择").tag("")
                            ForEach(store.categories.filter { item.type == nil || $0.type == item.type }) { Text($0.name).tag($0.id) }
                        }
                        Picker("账户", selection: Binding(get: { item.accountID ?? "" }, set: { item.accountID = $0; store.updateImportItem(item) })) {
                            Text("请选择").tag("")
                            ForEach(store.accounts) { Text($0.name).tag($0.id) }
                        }
                        TextField("备注", text: Binding(get: { item.note }, set: { item.note = $0; store.updateImportItem(item) }))
                        TextField(
                            "标签（逗号分隔）",
                            text: Binding(
                                get: { item.tags.joined(separator: ",") },
                                set: {
                                    item.tags = $0.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
                                    store.updateImportItem(item)
                                }
                            )
                        )
                        Toggle("不计入收支", isOn: Binding(get: { item.excluded }, set: { item.excluded = $0; store.updateImportItem(item) }))
                        Toggle("排除不入账", isOn: Binding(get: { item.skipped }, set: { item.skipped = $0; store.updateImportItem(item) }))
                    }
                }
                Button("确认入账", action: store.commitImport).disabled(!store.importReady)
            }
            if let message = store.importMessage {
                Section { Label(message, systemImage: "checkmark.circle").foregroundStyle(Color.income) }
            }
            if let error = store.importError {
                Section { Text(error).foregroundStyle(.red) }
            }
        }
        .navigationTitle("导入")
        .onAppear(perform: store.clearImportFeedback)
        // fileImporter 挂在 List 上在 iOS 中无法可靠弹出（无法找到 presentationAnchor）
        // 挂在 background EmptyView 上提供稳定的呈现锚点
        .background(
            EmptyView().fileImporter(isPresented: $importing, allowedContentTypes: [.data]) { result in
                if case let .success(url) = result { store.importFile(url, selectedFormat: selectedFormat) }
            }
        )
    }
}

private struct ExportView: View {
    @EnvironmentObject private var store: AppStore
    @State private var document: QingziDocument?
    @State private var exporting = false
    @State private var incremental = false
    @State private var startDate = Date()
    @State private var endDate = Date()
    var body: some View {
        VStack(spacing: 16) {
            Text("导出青子记账兼容 JSON").font(.title2.bold())
            Toggle("仅导出日期范围", isOn: $incremental)
            if incremental {
                DatePicker("开始", selection: $startDate, displayedComponents: .date)
                DatePicker("结束", selection: $endDate, displayedComponents: .date)
            }
            Button("生成并保存") {
                store.exportQingzi(start: incremental ? startDate : nil, end: incremental ? endDate : nil) { payload in
                    document = payload.map(QingziDocument.init)
                    exporting = document != nil
                }
            }
            if let error = store.dataManagementError {
                Text(error).foregroundStyle(.red)
            }
        }
        .padding()
        .onAppear { store.dataManagementError = nil }
        .navigationTitle("导出")
        .fileExporter(isPresented: $exporting, document: document, contentType: .json, defaultFilename: "OmniFlow-Qingzi") { _ in document = nil }
    }
}

private struct QingziDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    let payload: String
    init(_ payload: String) { self.payload = payload }
    init(configuration: ReadConfiguration) throws { payload = String(data: configuration.file.regularFileContents ?? Data(), encoding: .utf8) ?? "" }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: Data(payload.utf8)) }
}

private struct ManagementContainer<Content: View>: View {
    @EnvironmentObject private var store: AppStore
    let title: String
    let add: () -> Void
    @ViewBuilder let content: Content
    var body: some View {
        List {
            content
            if let error = store.managementError {
                Section { Text(error).foregroundStyle(.red) }
            }
        }
            .navigationTitle(title)
            .toolbar { Button(action: add) { Label("新增", systemImage: "plus") } }
            .onAppear { store.managementError = nil }
    }
}

private struct ManagementRow: View {
    let title: String
    var subtitle = ""
    let edit: () -> Void
    let delete: () -> Void
    var auxiliaryTitle: String? = nil
    var auxiliary: (() -> Void)? = nil
    @State private var confirmingDelete = false
    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(title).fontWeight(.medium)
                if !subtitle.isEmpty { Text(subtitle).font(.caption).foregroundStyle(.secondary) }
            }
            Spacer()
            Menu {
                if let auxiliaryTitle, let auxiliary { Button(auxiliaryTitle, action: auxiliary) }
                Button("编辑", action: edit)
                Button("删除", role: .destructive) { confirmingDelete = true }
            } label: {
                Image(systemName: "ellipsis.circle").font(.title3)
            }
        }
        .padding(.vertical, 5)
        .contentShape(Rectangle())
        .confirmationDialog("确认删除“\(title)”？", isPresented: $confirmingDelete, titleVisibility: .visible) {
            Button("删除", role: .destructive, action: delete)
            Button("取消", role: .cancel) {}
        } message: {
            Text("此操作无法撤销。")
        }
    }
}

private extension View {
    func managementEditorSheet(title: String, cancel: @escaping () -> Void) -> some View {
        NavigationStack {
            self
                .navigationTitle(title)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("取消", action: cancel)
                    }
                }
        }
    }
}

private struct NameEditor: View {
    let title: String
    @State private var name: String
    let save: (String) -> Void
    init(title: String, initial: String, save: @escaping (String) -> Void) { self.title = title; _name = State(initialValue: initial); self.save = save }
    var body: some View { Form { TextField("名称", text: $name); Button("保存") { save(name) } }.padding().frame(minWidth: 320) }
}

private struct LedgerEditor: View {
    @State private var name: String
    @State private var coverKey: String
    let save: (String, String?) -> Void

    init(ledger: LedgerUI?, save: @escaping (String, String?) -> Void) {
        _name = State(initialValue: ledger?.name ?? "")
        _coverKey = State(initialValue: ledger?.coverKey ?? "")
        self.save = save
    }

    var body: some View {
        Form {
            TextField("名称", text: $name)
            TextField("封面标识（可选）", text: $coverKey)
            Button("保存") { save(name, coverKey.isEmpty ? nil : coverKey) }
        }
        .padding()
        .frame(minWidth: 340)
    }
}

private struct AccountEditor: View {
    @State private var name: String
    @State private var balance: String
    @State private var type: String
    @State private var iconKey: String
    @State private var cardNumber: String
    @State private var note: String
    @State private var included: Bool
    @State private var balanceError: String?
    let save: (String, Int64, String, String, String?, String?, Bool) -> Void
    init(account: AccountUI?, save: @escaping (String, Int64, String, String, String?, String?, Bool) -> Void) {
        _name = State(initialValue: account?.name ?? "")
        _balance = State(initialValue: account.map { NSDecimalNumber(decimal: Decimal($0.balanceMinor) / 100).stringValue } ?? "0")
        _type = State(initialValue: account?.type ?? "CASH")
        _iconKey = State(initialValue: account?.iconKey ?? "wallet-cards")
        _cardNumber = State(initialValue: account?.cardNumber ?? "")
        _note = State(initialValue: account?.note ?? "")
        _included = State(initialValue: account?.includeInTotalAssets ?? true)
        _balanceError = State(initialValue: nil)
        self.save = save
    }
    var body: some View {
        Form {
            TextField("名称", text: $name)
            Picker("类型", selection: $type) {
                Text("现金").tag("CASH")
                Text("储蓄卡").tag("DEBIT_CARD")
                Text("信用卡").tag("CREDIT_CARD")
                Text("电子钱包").tag("E_WALLET")
                Text("投资账户").tag("INVESTMENT")
            }
            BundledIconPicker(selection: $iconKey)
            TextField("卡号（可选）", text: $cardNumber)
            TextField("备注（可选）", text: $note)
            TextField("余额", text: $balance)
            if let balanceError {
                Text(balanceError).foregroundStyle(Color.expense)
            }
            Toggle("计入总资产", isOn: $included)
            Button("保存") {
                guard let balanceMinor = balance.signedMoneyMinor else {
                    balanceError = "请输入有效余额，最多两位小数"
                    return
                }
                balanceError = nil
                save(
                    name,
                    balanceMinor,
                    type,
                    iconKey,
                    cardNumber.isEmpty ? nil : cardNumber,
                    note.isEmpty ? nil : note,
                    included
                )
            }
        }
        .padding()
        .frame(minWidth: 320)
    }
}

private struct CategoryEditor: View {
    @EnvironmentObject private var store: AppStore
    @State private var name: String
    @State private var type: EntryType
    @State private var parentID: String?
    @State private var iconKey: String
    let save: (String, EntryType, String?, String?) -> Void
    init(category: CategoryUI?, save: @escaping (String, EntryType, String?, String?) -> Void) {
        _name = State(initialValue: category?.name ?? "")
        _type = State(initialValue: category?.type ?? .expense)
        _parentID = State(initialValue: category?.parentID)
        _iconKey = State(initialValue: category?.iconKey ?? categoryIconOptions[0].key)
        self.save = save
    }
    var body: some View {
        Form {
            TextField("名称", text: $name)
            Picker("类型", selection: $type) { ForEach(EntryType.allCases) { Text($0.label).tag($0) } }
            Picker("层级", selection: $parentID) {
                Text("一级分类").tag(String?.none)
                ForEach(store.categories.filter { $0.parentID == nil && $0.type == type }) { Text($0.name).tag(Optional($0.id)) }
            }
            if parentID == nil { CategoryIconPicker(selection: $iconKey) }
            Button("保存") { save(name, type, parentID, parentID == nil ? iconKey : nil) }
        }.padding().frame(minWidth: 360)
    }
}

private struct CategoryIconPicker: View {
    @Binding var selection: String
    private let columns = [GridItem(.adaptive(minimum: 62), spacing: 8)]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("分类图标").font(.headline)
            ScrollView {
                LazyVGrid(columns: columns, spacing: 8) {
                    ForEach(categoryIconOptions) { option in
                        CategoryIconOptionButton(option: option, selected: selection == option.key) {
                            selection = option.key
                        }
                    }
                }
                .padding(.vertical, 2)
            }
            .frame(height: 300)
        }
    }
}

private struct CategoryIconOptionButton: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.appThemeColor) private var themeColor
    @Environment(\.appThemeSelectionForeground) private var selectedForeground
    @EnvironmentObject private var store: AppStore
    let option: CategoryIconOptionUI
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 3) {
                SVGIconView(
                    key: categoryIconAssetKey(option.key),
                    size: 34,
                    tint: selected
                        ? (AppThemeColor(rawValue: store.themeColor) ?? .lavender).selectionCSSColor(for: colorScheme)
                        : (AppThemeColor(rawValue: store.themeColor) ?? .lavender).cssColor(for: colorScheme)
                )
                Text(option.label).font(.caption2).lineLimit(1)
            }
            .frame(maxWidth: .infinity, minHeight: 58)
            .foregroundStyle(selected ? selectedForeground : Color.primary)
            .overlay { RoundedRectangle(cornerRadius: 10).stroke(selected ? Color.clear : Color.secondary.opacity(0.15), lineWidth: 1) }
        }
        .buttonStyle(.plain)
        .liquidGlassSurface(cornerRadius: 10, interactive: true, tint: selected ? themeColor : nil)
        .accessibilityLabel(option.label)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

private struct BundledIconPicker: View {
    @Binding var selection: String

    var body: some View {
        Picker("图标", selection: $selection) {
            ForEach(bundledIconKeys, id: \.self) { key in
                HStack { SVGIconView(key: key, size: 18); Text(key) }.tag(key)
            }
        }
    }
}

private struct RuleEditor: View {
    @EnvironmentObject private var store: AppStore
    @State private var name: String
    @State private var conditionType: String
    @State private var conditionValue: String
    @State private var actionType: String
    @State private var actionValue: String
    @State private var priority: Int
    @State private var validationError: String?
    let save: (String, String, String, String, String, Int) -> Void
    init(rule: RuleUI?, save: @escaping (String, String, String, String, String, Int) -> Void) {
        _name = State(initialValue: rule?.name ?? "")
        _conditionType = State(initialValue: rule?.conditionType ?? "NOTE_CONTAINS")
        _conditionValue = State(initialValue: rule?.conditionValue ?? "")
        _actionType = State(initialValue: rule?.actionType ?? "SET_CATEGORY")
        _actionValue = State(initialValue: rule?.actionValue ?? "")
        _priority = State(initialValue: rule?.priority ?? 0)
        _validationError = State(initialValue: nil)
        self.save = save
    }
    var body: some View {
        Form {
            TextField("名称", text: $name)
            Picker("条件", selection: $conditionType) {
                Text("备注包含").tag("NOTE_CONTAINS")
                Text("收支类型").tag("TRANSACTION_TYPE")
                Text("来源平台").tag("TRANSACTION_SOURCE")
            }
            if conditionType == "TRANSACTION_TYPE" {
                Picker("匹配值", selection: $conditionValue) { Text("支出").tag("EXPENSE"); Text("收入").tag("INCOME") }
            } else {
                TextField("匹配值", text: $conditionValue)
            }
            Picker("结果", selection: $actionType) {
                Text("设置分类").tag("SET_CATEGORY")
                Text("不计入收支").tag("SET_EXCLUDED")
                Text("排除不入账").tag("EXCLUDE")
            }
            if actionType == "SET_CATEGORY" {
                Picker("分类", selection: $actionValue) {
                    Text("请选择").tag("")
                    ForEach(store.categories) { Text($0.name).tag($0.id) }
                }
            }
            Stepper("优先级 \(priority)", value: $priority, in: 0...999)
            if let validationError {
                Text(validationError).foregroundStyle(Color.expense)
            }
            Button("保存", action: saveIfValid)
        }
        .onAppear {
            if actionType == "SET_CATEGORY", !store.categories.contains(where: { $0.id == actionValue }) {
                actionValue = ""
            }
        }
        .onChange(of: conditionType) {
            conditionValue = $0 == "TRANSACTION_TYPE" ? "EXPENSE" : ""
            validationError = nil
        }
        .onChange(of: actionType) { _ in
            actionValue = ""
            validationError = nil
        }
        .padding()
        .frame(minWidth: 360)
    }

    private func saveIfValid() {
        guard !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            validationError = "请输入规则名称"
            return
        }
        guard !conditionValue.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            validationError = "请输入匹配值"
            return
        }
        guard actionType != "SET_CATEGORY" || store.categories.contains(where: { $0.id == actionValue }) else {
            validationError = "请选择有效分类"
            return
        }
        validationError = nil
        save(name, conditionType, conditionValue, actionType, actionValue, priority)
    }
}

private struct ReminderEditor: View {
    @State private var name: String
    @State private var type: String
    @State private var amount: String
    @State private var schedule: String
    @State private var day: Int
    @State private var daysAfter: Int
    @State private var weekday: Int
    @State private var month: Int
    @State private var paused: Bool
    let save: (String, String, Int64?, String, Int?, Int?, Int?, Int?, Bool) -> Void
    init(reminder: ReminderUI?, save: @escaping (String, String, Int64?, String, Int?, Int?, Int?, Int?, Bool) -> Void) {
        _name = State(initialValue: reminder?.name ?? "")
        _type = State(initialValue: reminder?.type.contains("SUBSCRIPTION") == true ? "SUBSCRIPTION" : "REPAYMENT")
        _amount = State(initialValue: reminder?.amountMinor.map { NSDecimalNumber(decimal: Decimal($0) / 100).stringValue } ?? "")
        _schedule = State(initialValue: reminder?.scheduleKind.components(separatedBy: ".").last ?? "FIXED_REPAYMENT_DAY")
        _day = State(initialValue: reminder?.dayOfMonth ?? 1)
        _daysAfter = State(initialValue: reminder?.daysAfter ?? 7)
        _weekday = State(initialValue: reminder?.dayOfWeek ?? 1)
        _month = State(initialValue: reminder?.month ?? 1)
        _paused = State(initialValue: reminder?.paused ?? false)
        self.save = save
    }
    var body: some View {
        Form {
            TextField("名称", text: $name)
            TextField("金额（可选）", text: $amount)
            Picker("类型", selection: $type) { Text("还款提醒").tag("REPAYMENT"); Text("订阅提醒").tag("SUBSCRIPTION") }
            Picker("周期", selection: $schedule) {
                if type == "REPAYMENT" {
                    Text("固定日期").tag("FIXED_REPAYMENT_DAY")
                    Text("账单日后 N 天").tag("DAYS_AFTER_STATEMENT")
                } else {
                    Text("每天").tag("DAILY")
                    Text("每周").tag("WEEKLY")
                    Text("每月").tag("MONTHLY")
                    Text("每年").tag("YEARLY")
                }
            }
            switch schedule {
            case "DAILY": EmptyView()
            case "WEEKLY": Stepper("星期 \(weekday)", value: $weekday, in: 1...7)
            case "YEARLY":
                Stepper("月份 \(month)", value: $month, in: 1...12)
                Stepper("日期 \(day)", value: $day, in: 1...31)
            case "DAYS_AFTER_STATEMENT":
                Stepper("账单日 \(day)", value: $day, in: 1...31)
                Stepper("账单日后 \(daysAfter) 天", value: $daysAfter, in: 0...60)
            default: Stepper("日期 \(day)", value: $day, in: 1...31)
            }
            Toggle("暂停", isOn: $paused)
            Button("保存") {
                let amountMinor = amount.isEmpty ? nil : amount.moneyMinor
                save(
                    name,
                    type,
                    amountMinor,
                    schedule,
                    ["FIXED_REPAYMENT_DAY", "DAYS_AFTER_STATEMENT", "MONTHLY", "YEARLY"].contains(schedule) ? day : nil,
                    schedule == "DAYS_AFTER_STATEMENT" ? daysAfter : nil,
                    schedule == "WEEKLY" ? weekday : nil,
                    schedule == "YEARLY" ? month : nil,
                    paused
                )
            }
        }
        .onChange(of: type) { schedule = $0 == "REPAYMENT" ? "FIXED_REPAYMENT_DAY" : "MONTHLY" }
        .padding()
        .frame(minWidth: 360)
    }
}
