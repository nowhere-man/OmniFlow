import Foundation

#if canImport(OmniFlowShared)
import OmniFlowShared
#endif

@MainActor
final class AppStore: ObservableObject {
    @Published var destination: MainDestination = .home
    @Published var ledgers: [LedgerUI] = []
    @Published var accounts: [AccountUI] = []
    @Published var categories: [CategoryUI] = []
    @Published var tags: [TagUI] = []
    @Published var rules: [RuleUI] = []
    @Published var reminders: [ReminderUI] = []
    @Published var transactions: [TransactionUI] = []
    @Published var selectedLedgerID: String?
    @Published var defaultLedgerID: String?
    @Published var resourceLedgerID: String?
    @Published var selectedMonth = Date()
    @Published var calendarFilter = "ALL"
    @Published var calendarDays: [CalendarDayUI] = []
    @Published var transactionDisplayMode: TransactionDisplayMode = .list
    @Published var selectedDate: Date?
    @Published var dateDetailLedgerID: String?
    @Published var dateDetailTransactions: [TransactionUI] = []
    @Published var dateDetailExpenseMinor: Int64 = 0
    @Published var dateDetailIncomeMinor: Int64 = 0
    @Published var expenseMinor: Int64 = 0
    @Published var incomeMinor: Int64 = 0
    @Published var loading = true
    @Published var error: String?
    @Published var searchText = ""
    @Published var searchResults: [TransactionUI] = []
    @Published var searchExpenseMinor: Int64 = 0
    @Published var searchIncomeMinor: Int64 = 0
    @Published var editingTransaction: TransactionUI?
    @Published var editingTagIDs: Set<String> = []
    @Published var draftTransactionDate: Date?
    @Published var searchLedgerID: String?
    @Published var searchType: EntryType?
    @Published var searchAccountID: String?
    @Published var searchPrimaryCategoryID: String?
    @Published var searchSecondaryCategoryID: String?
    @Published var searchTagID: String?
    @Published var searchCategories: [CategoryUI] = []
    @Published var searchTags: [TagUI] = []
    @Published var searchExact = ""
    @Published var searchMinimum = ""
    @Published var searchMaximum = ""
    @Published var searchDateEnabled = false
    @Published var searchStartDate = Date()
    @Published var searchEndDate = Date()
    @Published var importItems: [ImportItemUI] = []
    @Published var importSessionID: String?
    @Published var importProgress: Double = 0
    @Published var importReady = false
    @Published var selectedImportItemIDs: Set<String> = []
    @Published var appLockEnabled = false
    @Published var appearanceMode = "SYSTEM"
    @Published var analyticsExpenseMinor: Int64 = 0
    @Published var analyticsIncomeMinor: Int64 = 0
    @Published var analyticsPreviousExpenseChange: Int64 = 0
    @Published var analyticsPreviousIncomeChange: Int64 = 0
    @Published var analyticsPreviousNetChange: Int64 = 0
    @Published var analyticsYearExpenseChange: Int64 = 0
    @Published var analyticsYearIncomeChange: Int64 = 0
    @Published var analyticsYearNetChange: Int64 = 0
    @Published var analyticsPoints: [AnalyticsPointUI] = []
    @Published var analyticsRanking: [TransactionUI] = []
    @Published var analyticsCategories: [CategoryShareUI] = []
    @Published var analyticsTags: [TagSummaryUI] = []
    @Published var analyticsAccountAssets: [AccountAssetUI] = []
    @Published var analyticsAccountSummary = (assets: Int64(0), liabilities: Int64(0))
    @Published var analyticsLedgerID: String?
    @Published var analyticsRankingType: EntryType = .expense
    @Published var analyticsCategoryType: EntryType = .expense
    @Published var analyticsCategoryGranularity: AppleCategoryGranularity = .primary
    @Published var analyticsPrimaryCategoryID: String?
    @Published var analyticsStatement: StatementTableUI?
    @Published var backups: [BackupUI] = []
    #if canImport(OmniFlowShared)
    private var analyticsSubscription: AppleFlowSubscription?
    private var homeSubscription: AppleFlowSubscription?
    private var categorySubscription: AppleFlowSubscription?
    private var tagSubscription: AppleFlowSubscription?
    private var ruleSubscription: AppleFlowSubscription?
    private var searchCategorySubscription: AppleFlowSubscription?
    private var searchTagSubscription: AppleFlowSubscription?
    private var dateDetailSubscription: AppleFlowSubscription?
    private var importSubscription: AppleFlowSubscription?
    private var backupObjects: [RemoteBackupMeta] = []
    #endif

    #if canImport(OmniFlowShared)
    private let bridge: AppleAppBridge
    private var subscriptions: [AppleFlowSubscription] = []
    #endif

    init() {
        #if canImport(OmniFlowShared)
        let shared = AppleSharedAppFactory.shared.create(
            databaseName: "omniflow.db",
            iCloudDirectory: FileManager.default.url(forUbiquityContainerIdentifier: "iCloud.com.omniflow")?.appendingPathComponent("Documents").path,
            iCloudAdapter: nil,
            webDavAdapter: nil
        )
        bridge = AppleAppBridge(app: shared)
        AppleSpreadsheetParserBridge.shared.install(parser: PlatformSpreadsheetParser())
        bridge.configureWebDav(
            endpoint: UserDefaults.standard.string(forKey: "webdav.endpoint") ?? "",
            username: UserDefaults.standard.string(forKey: "webdav.username") ?? "",
            password: KeychainPassword.load()
        )
        bridge.initialize { [weak self] message in
            Task { @MainActor in
                self?.error = message
                self?.startObservers()
            }
        }
        #else
        loading = false
        #endif
    }

    func observeAnalytics(start: Date, end: Date, ledgerID: String? = nil) {
        #if canImport(OmniFlowShared)
        analyticsSubscription?.cancel()
        analyticsSubscription = bridge.watchAnalytics(
            ledgerId: ledgerID,
            startMillis: Int64(start.timeIntervalSince1970 * 1000),
            endMillis: Int64(end.timeIntervalSince1970 * 1000),
            rankingTypeName: analyticsRankingType.rawValue,
            categoryTypeName: analyticsCategoryType.rawValue,
            categoryGranularityName: analyticsCategoryGranularity.rawValue,
            primaryCategoryId: analyticsPrimaryCategoryID
        ) { [weak self] value, message in
            Task { @MainActor in
                self?.error = message
                self?.analyticsExpenseMinor = value?.summary.expenseTotal ?? 0
                self?.analyticsIncomeMinor = value?.summary.incomeTotal ?? 0
                self?.analyticsPreviousExpenseChange = value?.previousPeriod.expenseChange ?? 0
                self?.analyticsPreviousIncomeChange = value?.previousPeriod.incomeChange ?? 0
                self?.analyticsPreviousNetChange = value?.previousPeriod.netIncomeChange ?? 0
                self?.analyticsYearExpenseChange = value?.yearOverYear.expenseChange ?? 0
                self?.analyticsYearIncomeChange = value?.yearOverYear.incomeChange ?? 0
                self?.analyticsYearNetChange = value?.yearOverYear.netIncomeChange ?? 0
                self?.analyticsPoints = value?.trend.points.map { AnalyticsPointUI(label: $0.label, expense: $0.expense, income: $0.income) } ?? []
                self?.analyticsRanking = value?.ranking.map { Self.transaction($0.transaction) } ?? []
                self?.analyticsCategories = value?.categoryShares.map { CategoryShareUI(id: $0.categoryId, name: $0.categoryName, amount: $0.amount) } ?? []
                self?.analyticsTags = value?.tagSummary.map { TagSummaryUI(id: $0.tag.id, name: $0.tag.name, expense: $0.expense, income: $0.income) } ?? []
                self?.analyticsAccountAssets = value?.accountAssets.map { AccountAssetUI(id: $0.accountId, name: $0.accountName, balance: $0.balance) } ?? []
                self?.analyticsAccountSummary = (value?.accountSummary.assets ?? 0, value?.accountSummary.liabilities ?? 0)
            }
        }
        #endif
    }

    func selectLedger(_ id: String?) {
        selectedLedgerID = id
        observeHome()
        perform { done in
            #if canImport(OmniFlowShared)
            bridge.setHomeLedgerScope(ledgerId: id, callback: done)
            #else
            done(nil)
            #endif
        }
    }

    func shiftMonth(_ value: Int) {
        selectedMonth = Calendar.current.date(byAdding: .month, value: value, to: selectedMonth) ?? selectedMonth
        observeHome()
    }

    func selectMonth(_ date: Date) {
        selectedMonth = date
        observeHome()
    }

    func setCalendarFilter(_ filter: String) {
        calendarFilter = filter
        observeHome()
    }

    func toggleTransactionDisplayMode() {
        transactionDisplayMode = transactionDisplayMode == .list ? .card : .list
        perform { done in
            #if canImport(OmniFlowShared)
            bridge.setTransactionDetailDisplayMode(modeName: transactionDisplayMode.rawValue, callback: done)
            #else
            done(nil)
            #endif
        }
    }

    func selectResourceLedger(_ id: String?) {
        resourceLedgerID = id
        observeCategories()
    }

    func selectAnalyticsLedger(_ id: String?) {
        analyticsLedgerID = id
        perform { done in
            #if canImport(OmniFlowShared)
            bridge.setAnalyticsLedgerScope(ledgerId: id, callback: done)
            #else
            done(nil)
            #endif
        }
    }

    func loadAnalyticsStatement(year: Int) {
        #if canImport(OmniFlowShared)
        bridge.loadStatementTable(ledgerId: analyticsLedgerID, year: Int32(year)) { [weak self] value, message in
            Task { @MainActor in
                self?.error = message
                if let table = value {
                    self?.analyticsStatement = StatementTableUI(
                        year: Int(table.year),
                        months: table.months.map { StatementMonthUI(month: Int($0.month), expenseMinor: $0.expense, incomeMinor: $0.income) },
                        expenseMinor: table.total.expenseTotal,
                        incomeMinor: table.total.incomeTotal
                    )
                } else {
                    self?.analyticsStatement = nil
                }
            }
        }
        #endif
    }

    func showDate(_ date: Date) {
        selectedDate = date
        dateDetailLedgerID = selectedLedgerID
        observeDateDetails()
    }

    func setDateDetailLedger(_ id: String?) {
        dateDetailLedgerID = id
        observeDateDetails()
    }

    func dismissDateDetail() {
        #if canImport(OmniFlowShared)
        dateDetailSubscription?.cancel()
        #endif
        selectedDate = nil
        dateDetailTransactions = []
    }

    func search() {
        #if canImport(OmniFlowShared)
        bridge.search(
            keyword: searchText,
            ledgerId: searchLedgerID,
            typeName: searchType?.rawValue,
            accountId: searchAccountID,
            primaryCategoryId: searchPrimaryCategoryID,
            secondaryCategoryId: searchSecondaryCategoryID,
            tagId: searchTagID,
            exactMinor: Self.boxedLong(Self.minor(searchExact)),
            minimumMinor: Self.boxedLong(searchExact.isEmpty ? Self.minor(searchMinimum) : nil),
            maximumMinor: Self.boxedLong(searchExact.isEmpty ? Self.minor(searchMaximum) : nil),
            startMillis: Self.boxedLong(searchDateEnabled ? Int64(Calendar.current.startOfDay(for: min(searchStartDate, searchEndDate)).timeIntervalSince1970 * 1000) : nil),
            endMillis: Self.boxedLong(searchDateEnabled ? Int64((Calendar.current.date(byAdding: .day, value: 1, to: Calendar.current.startOfDay(for: max(searchStartDate, searchEndDate))) ?? searchEndDate).timeIntervalSince1970 * 1000) : nil)
        ) { [weak self] result, message in
            Task { @MainActor in
                self?.error = message
                self?.searchResults = result?.items.map { Self.transaction($0.transaction) } ?? []
                self?.searchExpenseMinor = result?.summary.expenseTotal ?? 0
                self?.searchIncomeMinor = result?.summary.incomeTotal ?? 0
            }
        }
        #else
        searchResults = transactions.filter { $0.note.localizedCaseInsensitiveContains(searchText) }
        #endif
    }

    func setSearchLedger(_ id: String?) {
        searchLedgerID = id
        searchPrimaryCategoryID = nil
        searchSecondaryCategoryID = nil
        searchTagID = nil
        guard let id else { searchCategories = []; searchTags = []; search(); return }
        #if canImport(OmniFlowShared)
        searchCategorySubscription?.cancel()
        searchTagSubscription?.cancel()
        searchCategorySubscription = bridge.watchCategories(ledgerId: id) { [weak self] values, message in
            Task { @MainActor in
                self?.error = message
                self?.searchCategories = values?.map {
                    CategoryUI(
                        id: $0.id,
                        name: $0.name,
                        type: String(describing: $0.type).contains("INCOME") ? .income : .expense,
                        parentID: $0.parentId,
                        iconKey: $0.iconKey
                    )
                } ?? []
            }
        }
        searchTagSubscription = bridge.watchTags(ledgerId: id) { [weak self] values, message in
            Task { @MainActor in self?.error = message; self?.searchTags = values?.map { TagUI(id: $0.id, name: $0.name) } ?? [] }
        }
        #endif
        search()
    }

    func saveTransaction(
        id: String?,
        ledgerID: String,
        accountID: String,
        categoryID: String,
        amountMinor: Int64,
        type: EntryType,
        date: Date,
        note: String,
        excluded: Bool,
        tagIDs: Set<String>,
        completion: @escaping (String?) -> Void
    ) {
        #if canImport(OmniFlowShared)
        bridge.saveTransaction(
            id: id,
            ledgerId: ledgerID,
            accountId: accountID,
            categoryId: categoryID,
            amountMinor: amountMinor,
            typeName: type.rawValue,
            occurredAtMillis: Int64(date.timeIntervalSince1970 * 1000),
            note: note.isEmpty ? nil : note,
            excluded: excluded,
            tagIds: tagIDs
        ) { message in
            Task { @MainActor in completion(message) }
        }
        #else
        completion("共享 Framework 尚未构建")
        #endif
    }

    func startNewTransaction(date: Date? = nil, ledgerID: String? = nil) {
        editingTransaction = nil
        editingTagIDs = []
        draftTransactionDate = date
        selectResourceLedger(ledgerID ?? defaultLedgerID ?? selectedLedgerID ?? ledgers.first?.id)
        destination = .transaction
    }

    func editTransaction(_ transaction: TransactionUI) {
        editingTransaction = transaction
        editingTagIDs = []
        draftTransactionDate = nil
        selectResourceLedger(transaction.ledgerID)
        #if canImport(OmniFlowShared)
        bridge.loadTransaction(id: transaction.id) { [weak self] value, message in
            Task { @MainActor in
                self?.error = message
                self?.editingTagIDs = Set(value?.tagIds ?? [])
            }
        }
        #endif
        destination = .transaction
    }

    func deleteTransaction(_ id: String, completion: @escaping (String?) -> Void) {
        #if canImport(OmniFlowShared)
        bridge.deleteTransaction(id: id) { message in Task { @MainActor in completion(message) } }
        #else
        completion("共享 Framework 尚未构建")
        #endif
    }

    func saveLedger(id: String?, name: String, coverKey: String?) { perform { done in
        #if canImport(OmniFlowShared)
        bridge.saveLedger(id: id, name: name, coverKey: coverKey, callback: done)
        #else
        done("共享 Framework 尚未构建")
        #endif
    } }

    func deleteLedger(_ id: String) { perform { done in
        #if canImport(OmniFlowShared)
        bridge.deleteLedger(id: id, callback: done)
        #else
        done("共享 Framework 尚未构建")
        #endif
    } }

    func setDefaultLedger(_ id: String?) { perform { done in
        #if canImport(OmniFlowShared)
        bridge.setDefaultLedger(id: id, callback: done)
        #else
        done(nil)
        #endif
    } }

    func saveAccount(
        id: String?,
        name: String,
        balanceMinor: Int64,
        type: String,
        iconKey: String,
        cardNumber: String?,
        note: String?,
        included: Bool
    ) { perform { done in
        #if canImport(OmniFlowShared)
        bridge.saveAccount(
            id: id,
            name: name,
            typeName: type,
            iconKey: iconKey,
            cardNumber: cardNumber,
            note: note,
            balanceMinor: balanceMinor,
            includeInTotalAssets: included,
            callback: done
        )
        #else
        done("共享 Framework 尚未构建")
        #endif
    } }

    func deleteAccount(_ id: String) { perform { done in
        #if canImport(OmniFlowShared)
        bridge.deleteAccount(id: id, callback: done)
        #else
        done("共享 Framework 尚未构建")
        #endif
    } }

    func saveCategory(id: String?, name: String, type: EntryType, parentID: String? = nil, iconKey: String? = nil) {
        guard let ledgerID = resourceLedgerID else { error = "请先选择账本"; return }
        perform { done in
            #if canImport(OmniFlowShared)
            bridge.saveCategory(id: id, ledgerId: ledgerID, parentId: parentID, name: name, iconKey: parentID == nil ? (iconKey ?? "category") : nil, typeName: type.rawValue, callback: done)
            #else
            done("共享 Framework 尚未构建")
            #endif
        }
    }

    func deleteCategory(_ id: String) { perform { done in
        #if canImport(OmniFlowShared)
        bridge.deleteCategory(id: id, callback: done)
        #else
        done("共享 Framework 尚未构建")
        #endif
    } }

    func saveTag(id: String?, name: String) {
        guard let ledgerID = resourceLedgerID else { error = "请先选择账本"; return }
        perform { done in
            #if canImport(OmniFlowShared)
            bridge.saveTag(id: id, ledgerId: ledgerID, name: name, callback: done)
            #else
            done("共享 Framework 尚未构建")
            #endif
        }
    }

    func deleteTag(_ id: String) { perform { done in
        #if canImport(OmniFlowShared)
        bridge.deleteTag(id: id, callback: done)
        #else
        done("共享 Framework 尚未构建")
        #endif
    } }

    func saveRule(
        id: String?,
        name: String,
        conditionType: String,
        conditionValue: String,
        actionType: String,
        actionValue: String,
        priority: Int
    ) {
        guard let ledgerID = resourceLedgerID else { error = "请先选择账本"; return }
        perform { done in
            #if canImport(OmniFlowShared)
            bridge.saveRule(id: id, ledgerId: ledgerID, name: name, conditionTypeName: conditionType, conditionValue: conditionValue, actionTypeName: actionType, actionValue: actionValue, priority: Int32(priority), callback: done)
            #else
            done("共享 Framework 尚未构建")
            #endif
        }
    }

    func deleteRule(_ id: String) { perform { done in
        #if canImport(OmniFlowShared)
        bridge.deleteRule(id: id, callback: done)
        #else
        done("共享 Framework 尚未构建")
        #endif
    } }

    func moveRule(_ id: String, offset: Int) {
        guard let ledgerID = resourceLedgerID else { error = "请先选择账本"; return }
        var ordered = rules.sorted { $0.priority < $1.priority }
        guard let from = ordered.firstIndex(where: { $0.id == id }) else { return }
        let to = from + offset
        guard ordered.indices.contains(to) else { return }
        let moved = ordered.remove(at: from)
        ordered.insert(moved, at: to)
        perform { done in
            #if canImport(OmniFlowShared)
            bridge.reorderRules(ledgerId: ledgerID, orderedIds: ordered.map(\.id), callback: done)
            #else
            done("共享 Framework 尚未构建")
            #endif
        }
    }

    func saveReminder(
        id: String?,
        name: String,
        type: String,
        amountMinor: Int64?,
        schedule: String,
        dayOfMonth: Int?,
        daysAfter: Int?,
        dayOfWeek: Int?,
        month: Int?,
        paused: Bool
    ) { perform { done in
        #if canImport(OmniFlowShared)
        bridge.saveReminder(
            id: id,
            typeName: type,
            name: name,
            amountMinor: amountMinor ?? 0,
            hasAmount: amountMinor != nil,
            scheduleKindName: schedule,
            dayOfMonth: Int32(dayOfMonth ?? 0),
            hasDayOfMonth: dayOfMonth != nil,
            daysAfter: Int32(daysAfter ?? 0),
            hasDaysAfter: daysAfter != nil,
            dayOfWeek: Int32(dayOfWeek ?? 0),
            hasDayOfWeek: dayOfWeek != nil,
            month: Int32(month ?? 0),
            hasMonth: month != nil,
            paused: paused,
            callback: done
        )
        #else
        done("共享 Framework 尚未构建")
        #endif
    } }

    func deleteReminder(_ id: String) { perform { done in
        #if canImport(OmniFlowShared)
        bridge.deleteReminder(id: id, callback: done)
        #else
        done("共享 Framework 尚未构建")
        #endif
    } }

    func setReminderPaused(_ id: String, paused: Bool) { perform { done in
        #if canImport(OmniFlowShared)
        bridge.setReminderPaused(id: id, paused: paused, callback: done)
        #else
        done("共享 Framework 尚未构建")
        #endif
    } }

    func syncNow(target: String = "ICLOUD", retention: Int = 10) { perform { done in
        #if canImport(OmniFlowShared)
        bridge.configureSync(targetName: target, retention: Int32(retention)) { message in
            if let message { done(message) } else { self.bridge.syncNow(callback: done) }
        }
        #else
        done("共享 Framework 尚未构建")
        #endif
    } }

    func loadBackups() {
        #if canImport(OmniFlowShared)
        bridge.listBackups { [weak self] values, message in
            Task { @MainActor in
                self?.error = message
                self?.backupObjects = values ?? []
                self?.backups = values?.map { BackupUI(id: $0.backupId, createdAt: String(describing: $0.createdAt)) } ?? []
            }
        }
        #endif
    }

    func restoreBackup(id: String) {
        #if canImport(OmniFlowShared)
        guard let backup = backupObjects.first(where: { $0.backupId == id }) else { return }
        perform { done in bridge.restoreBackup(meta: backup, callback: done) }
        #endif
    }

    func configureWebDav(endpoint: String, username: String, password: String) {
        #if canImport(OmniFlowShared)
        bridge.configureWebDav(endpoint: endpoint, username: username, password: password)
        #endif
        KeychainPassword.save(password)
    }

    func setAppLockEnabled(_ enabled: Bool) {
        appLockEnabled = enabled
        perform { done in
            #if canImport(OmniFlowShared)
            bridge.setAppLockEnabled(enabled: enabled, callback: done)
            #else
            done(nil)
            #endif
        }
    }

    func setAppearanceMode(_ mode: String) {
        appearanceMode = mode
        perform { done in
            #if canImport(OmniFlowShared)
            bridge.setAppearanceMode(modeName: mode, callback: done)
            #else
            done(nil)
            #endif
        }
    }

    func exportQingzi(start: Date? = nil, end: Date? = nil, completion: @escaping (String?) -> Void) {
        #if canImport(OmniFlowShared)
        let orderedStart = start.map { Calendar.current.startOfDay(for: $0) }
        let orderedEnd = end.map { Calendar.current.startOfDay(for: $0) }
        let rangeStart = orderedStart.flatMap { first in orderedEnd.map { min(first, $0) } }
        let rangeEnd = orderedStart.flatMap { first in orderedEnd.map { max(first, $0) } }
            .flatMap { Calendar.current.date(byAdding: .day, value: 1, to: $0) }
        bridge.exportQingziRange(
            startMillis: Self.boxedLong(rangeStart.map { Int64($0.timeIntervalSince1970 * 1000) }),
            endMillis: Self.boxedLong(rangeEnd.map { Int64($0.timeIntervalSince1970 * 1000) })
        ) { [weak self] payload, _, message in
            Task { @MainActor in self?.error = message; completion(payload) }
        }
        #else
        completion(nil)
        error = "共享 Framework 尚未构建"
        #endif
    }

    func importFile(_ url: URL, selectedFormat: AppleImportFormat? = nil) {
        guard let ledgerID = resourceLedgerID else { error = "请先选择账本"; return }
        #if canImport(OmniFlowShared)
        do {
            let data = try Data(contentsOf: url)
            let bytes = KotlinByteArray(size: Int32(data.count))
            data.enumerated().forEach { bytes.set(index: Int32($0.offset), value: Int8(bitPattern: $0.element)) }
            importSubscription?.cancel()
            importSubscription = bridge.previewImport(ledgerId: ledgerID, fileName: url.lastPathComponent, bytes: bytes, formatName: selectedFormat?.rawValue) { [weak self] preview, message in
                Task { @MainActor in
                    self?.error = message
                    self?.applyImportPreview(preview)
                }
            }
        } catch { self.error = error.localizedDescription }
        #else
        error = "共享 Framework 尚未构建"
        #endif
    }

    func updateImportItem(_ item: ImportItemUI) {
        guard let sessionID = importSessionID else { return }
        #if canImport(OmniFlowShared)
        bridge.editImportItem(
            sessionId: sessionID,
            itemId: item.id,
            typeName: item.type?.rawValue,
            categoryId: item.categoryID,
            accountId: item.accountID,
            note: item.note,
            tags: item.tags,
            excluded: item.excluded,
            skipped: item.skipped
        ) { [weak self] preview, message in
            Task { @MainActor in self?.error = message; self?.applyImportPreview(preview) }
        }
        #endif
    }

    func toggleImportSelection(_ id: String) {
        if selectedImportItemIDs.contains(id) { selectedImportItemIDs.remove(id) } else { selectedImportItemIDs.insert(id) }
    }

    func selectAllImportItems() {
        selectedImportItemIDs = Set(importItems.map(\.id))
    }

    func invertImportSelection() {
        selectedImportItemIDs = Set(importItems.map(\.id)).subtracting(selectedImportItemIDs)
    }

    func setSelectedImportCategory(_ categoryID: String?) {
        guard let sessionID = importSessionID, !selectedImportItemIDs.isEmpty else { return }
        #if canImport(OmniFlowShared)
        bridge.editImportCategories(sessionId: sessionID, itemIds: selectedImportItemIDs, categoryId: categoryID) { [weak self] preview, message in
            Task { @MainActor in self?.error = message; self?.applyImportPreview(preview) }
        }
        #endif
    }

    func setSelectedImportSkipped(_ skipped: Bool) {
        guard let sessionID = importSessionID, !selectedImportItemIDs.isEmpty else { return }
        #if canImport(OmniFlowShared)
        bridge.editImportSkipped(sessionId: sessionID, itemIds: selectedImportItemIDs, skipped: skipped) { [weak self] preview, message in
            Task { @MainActor in self?.error = message; self?.applyImportPreview(preview) }
        }
        #endif
    }

    func commitImport() {
        guard let sessionID = importSessionID else { return }
        #if canImport(OmniFlowShared)
        bridge.commitImport(sessionId: sessionID) { [weak self] result, message in
            Task { @MainActor in
                self?.error = message ?? result.map { "已导入 \($0.importedCount) 条" }
                if message == nil {
                    self?.importItems = []
                    self?.selectedImportItemIDs = []
                    self?.importSessionID = nil
                    self?.importReady = false
                }
            }
        }
        #endif
    }

    private func perform(_ operation: (@escaping (String?) -> Void) -> Void) {
        operation { [weak self] message in Task { @MainActor in self?.error = message } }
    }

    #if canImport(OmniFlowShared)
    private func applyImportPreview(_ preview: ImportPreviewState?) {
        importSessionID = preview?.sessionId
        importProgress = Double(preview?.progress ?? 0)
        importReady = preview?.isReadyToCommit ?? false
        importItems = preview?.items.map {
            ImportItemUI(
                id: $0.id,
                note: $0.note ?? "",
                amountMinor: $0.raw.amount,
                date: Date(timeIntervalSince1970: TimeInterval($0.raw.occurredAt.epochSeconds)),
                source: String(describing: $0.raw.format).components(separatedBy: ".").last ?? "",
                type: $0.type.map { String(describing: $0).contains("INCOME") ? .income : .expense },
                categoryID: $0.categoryId,
                accountID: $0.accountId,
                tags: $0.tags,
                excluded: $0.isExcluded,
                skipped: $0.isSkipped,
                duplicate: String(describing: $0.duplicateStatus)
            )
        } ?? []
        selectedImportItemIDs = selectedImportItemIDs.intersection(importItems.map(\.id))
    }

    private func startObservers() {
        subscriptions.append(bridge.watchLedgers { [weak self] values, message in
            Task { @MainActor in
                self?.error = message
                self?.ledgers = values?.map { LedgerUI(id: $0.id, name: $0.name, coverKey: $0.coverKey) } ?? []
                if self?.resourceLedgerID == nil {
                    self?.resourceLedgerID = self?.selectedLedgerID ?? self?.ledgers.first?.id
                    self?.observeCategories()
                }
                self?.observeHome()
            }
        })
        subscriptions.append(bridge.watchDefaultLedgerId { [weak self] value, message in
            Task { @MainActor in self?.error = message; self?.defaultLedgerID = value }
        })
        subscriptions.append(bridge.watchAccounts { [weak self] values, message in
            Task { @MainActor in
                self?.error = message
                self?.accounts = values?.map {
                    AccountUI(
                        id: $0.id,
                        name: $0.name,
                        balanceMinor: $0.balance,
                        type: String(describing: $0.type),
                        iconKey: $0.iconKey,
                        cardNumber: $0.cardNumber,
                        note: $0.note,
                        includeInTotalAssets: $0.includeInTotalAssets
                    )
                } ?? []
            }
        })
        subscriptions.append(bridge.watchReminders { [weak self] values, message in
            let reminders: [ReminderUI] = values?.map { value -> ReminderUI in
                return ReminderUI(
                    id: value.id,
                    name: value.name,
                    type: value.typeName,
                    amountMinor: value.hasAmount ? value.amountMinor : nil,
                    scheduleKind: value.scheduleKindName,
                    paused: value.paused,
                    dayOfMonth: value.hasDayOfMonth ? Int(value.dayOfMonth) : nil,
                    daysAfter: value.hasDaysAfter ? Int(value.daysAfter) : nil,
                    dayOfWeek: value.hasDayOfWeek ? Int(value.dayOfWeek) : nil,
                    month: value.hasMonth ? Int(value.month) : nil
                )
            } ?? []
            Task { @MainActor in
                self?.error = message
                self?.reminders = reminders
            }
        })
        subscriptions.append(bridge.watchPreferenceSnapshot { [weak self] value, message in
            Task { @MainActor in
                self?.error = message
                self?.appLockEnabled = value?.appLockEnabled ?? false
                self?.appearanceMode = value?.appearanceMode ?? "SYSTEM"
                self?.selectedLedgerID = value?.homeLedgerId
                self?.analyticsLedgerID = value?.analyticsLedgerId
                self?.transactionDisplayMode = TransactionDisplayMode(rawValue: value?.transactionDetailDisplayMode ?? "LIST") ?? .list
                if self?.resourceLedgerID == nil { self?.resourceLedgerID = value?.homeLedgerId ?? self?.ledgers.first?.id }
                self?.observeHome()
                self?.observeCategories()
            }
        })
        observeHome()
        observeCategories()
    }

    private func observeHome() {
        let interval = Self.monthInterval(selectedMonth)
        homeSubscription?.cancel()
        homeSubscription = bridge.watchHome(
            ledgerId: selectedLedgerID,
            startMillis: Int64(interval.start.timeIntervalSince1970 * 1000),
            endMillis: Int64(interval.end.timeIntervalSince1970 * 1000),
            calendarFilterName: calendarFilter
        ) { [weak self] value, message in
            Task { @MainActor in
                self?.loading = false
                self?.error = message
                self?.expenseMinor = value?.summary.expenseTotal ?? 0
                self?.incomeMinor = value?.summary.incomeTotal ?? 0
                self?.transactions = value?.groups.flatMap { $0.items }.map(Self.transaction) ?? []
                self?.calendarDays = value?.calendar.map {
                    CalendarDayUI(
                        id: "\($0.date.year)-\($0.date.monthNumber)-\($0.date.dayOfMonth)",
                        date: Self.date(year: Int($0.date.year), month: Int($0.date.monthNumber), day: Int($0.date.dayOfMonth)),
                        expenseMinor: $0.expenseTotal,
                        incomeMinor: $0.incomeTotal
                    )
                } ?? []
            }
        }
    }

    private func observeCategories() {
        categorySubscription?.cancel()
        tagSubscription?.cancel()
        ruleSubscription?.cancel()
        guard let ledgerID = resourceLedgerID else { categories = []; tags = []; rules = []; return }
        categorySubscription = bridge.watchCategories(ledgerId: ledgerID) { [weak self] values, message in
            Task { @MainActor in
                self?.error = message
                self?.categories = values?.map {
                    CategoryUI(
                        id: $0.id,
                        name: $0.name,
                        type: String(describing: $0.type).contains("INCOME") ? .income : .expense,
                        parentID: $0.parentId,
                        iconKey: $0.iconKey
                    )
                } ?? []
            }
        }
        tagSubscription = bridge.watchTags(ledgerId: ledgerID) { [weak self] values, message in
            Task { @MainActor in self?.error = message; self?.tags = values?.map { TagUI(id: $0.id, name: $0.name) } ?? [] }
        }
        ruleSubscription = bridge.watchRules(ledgerId: ledgerID) { [weak self] values, message in
            Task { @MainActor in
                self?.error = message
                self?.rules = values?.map {
                    RuleUI(
                        id: $0.id,
                        name: $0.name,
                        conditionType: String(describing: $0.conditionType).components(separatedBy: ".").last?.uppercased() ?? "NOTE_CONTAINS",
                        conditionValue: $0.conditionValue,
                        actionType: String(describing: $0.actionType).components(separatedBy: ".").last?.uppercased() ?? "SET_CATEGORY",
                        actionValue: $0.actionValue,
                        priority: Int($0.priority)
                    )
                } ?? []
            }
        }
    }

    private func observeDateDetails() {
        guard let date = selectedDate else { return }
        let start = Calendar.current.startOfDay(for: date)
        let end = Calendar.current.date(byAdding: .day, value: 1, to: start) ?? start.addingTimeInterval(86_400)
        dateDetailSubscription?.cancel()
        dateDetailSubscription = bridge.watchTransactionDetails(
            ledgerId: dateDetailLedgerID,
            startMillis: Int64(start.timeIntervalSince1970 * 1000),
            endMillis: Int64(end.timeIntervalSince1970 * 1000)
        ) { [weak self] value, message in
            Task { @MainActor in
                self?.error = message
                self?.dateDetailExpenseMinor = value?.summary.expenseTotal ?? 0
                self?.dateDetailIncomeMinor = value?.summary.incomeTotal ?? 0
                self?.dateDetailTransactions = value?.items.map(Self.transaction) ?? []
            }
        }
    }

    private static func transaction(_ value: TransactionListItem) -> TransactionUI {
        TransactionUI(
            id: value.id,
            ledgerID: value.ledgerId,
            ledgerName: value.ledgerName,
            accountID: value.accountId,
            accountName: value.accountName,
            categoryID: value.categoryId,
            categoryName: value.categoryName,
            categoryIconKey: value.categoryIconKey,
            amountMinor: value.amount,
            type: String(describing: value.type).contains("INCOME") ? .income : .expense,
            date: Date(timeIntervalSince1970: TimeInterval(value.occurredAt.epochSeconds)),
            note: value.note ?? "",
            excluded: value.isExcluded
        )
    }

    private static func boxedLong(_ value: Int64?) -> KotlinLong? {
        value.map { KotlinLong(value: $0) }
    }
    #else
    private func observeHome() {}
    private func observeCategories() {}
    private func observeDateDetails() {}
    #endif

    private static func monthInterval(_ date: Date) -> DateInterval {
        Calendar.current.dateInterval(of: .month, for: date) ?? DateInterval(start: date, duration: 30 * 86_400)
    }

    private static func date(year: Int, month: Int, day: Int) -> Date {
        Calendar.current.date(from: DateComponents(year: year, month: month, day: day)) ?? Date()
    }

    private static func minor(_ text: String) -> Int64? {
        guard !text.isEmpty, let value = Decimal(string: text) else { return nil }
        return NSDecimalNumber(decimal: value * 100).int64Value
    }
}
