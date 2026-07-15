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
    @Published var selectedDetailRange: DateInterval?
    @Published var dateDetailLedgerID: String?
    @Published var dateDetailType: EntryType?
    @Published var dateDetailTransactions: [TransactionUI] = []
    @Published var dateDetailExpenseMinor: Int64 = 0
    @Published var dateDetailIncomeMinor: Int64 = 0
    @Published var dateDetailStatus = DateDetailStatus.idle
    @Published var expenseMinor: Int64 = 0
    @Published var incomeMinor: Int64 = 0
    @Published var loading = true
    @Published var error: String?
    @Published var managementError: String?
    @Published var dataManagementError: String?
    @Published var searchText = ""
    @Published var searchPrimaryCategoryText = ""
    @Published var searchSecondaryCategoryText = ""
    @Published var searchTagText = ""
    @Published var searchNoteText = ""
    @Published var searchMinimumAmount = ""
    @Published var searchMaximumAmount = ""
    @Published var searchDateEnabled = false
    @Published var searchStartDate = Date()
    @Published var searchEndDate = Date()
    @Published var searchResults: [TransactionUI] = []
    @Published var searchExpenseMinor: Int64 = 0
    @Published var searchIncomeMinor: Int64 = 0
    @Published var searchStatus = SearchStatus.idle
    @Published var selectedTransactionDetail: TransactionUI?
    @Published var editingTransaction: TransactionUI?
    @Published var editingTagIDs: Set<String> = []
    @Published var draftTransactionDate: Date?
    @Published var draftTransactionLedgerID: String?
    @Published private(set) var transactionDraftRevision = UUID()
    @Published var searchLedgerID: String?
    @Published var searchType: EntryType?
    @Published var searchAccountID: String?
    @Published var importItems: [ImportItemUI] = []
    @Published var importSessionID: String?
    @Published var importProgress: Double = 0
    @Published var importReady = false
    @Published private(set) var importUpdating = false
    @Published private(set) var importSessionLedgerID: String?
    @Published var selectedImportItemIDs: Set<String> = []
    @Published var importError: String?
    @Published var importMessage: String?
    @Published var appLockEnabled = false
    @Published var appearanceMode = "SYSTEM"
    @Published var themeColor = "LAVENDER"
    @Published var analyticsExpenseMinor: Int64 = 0
    @Published var analyticsIncomeMinor: Int64 = 0
    @Published var analyticsRanking: [AnalyticsRankingUI] = []
    @Published var analyticsCategories: [CategoryBreakdownUI] = []
    @Published var analyticsYearStatement: StatementTableUI?
    @Published var analyticsLedgerID: String?
    @Published var analyticsRankingType: EntryType = .expense
    @Published var analyticsCategoryType: EntryType = .expense
    @Published var analyticsStatement: StatementTableUI?
    @Published var backups: [BackupUI] = []
    @Published var syncPhase = "IDLE"
    @Published var syncProgress: Double?
    @Published var syncLastBackupAt: String?
    @Published var syncError: String?
    private var analyticsObservation: (start: Date, end: Date, ledgerID: String?)?
    private var pendingTransactionDetail: TransactionUI?
    private var pendingDateDetailDraft: (date: Date, ledgerID: String?)?
    private var searchDebounceTask: Task<Void, Never>?
    private var searchGeneration = 0
    private var dateDetailGeneration = 0
    private var transactionTagLoadGeneration = 0
    private var importSessionGeneration = 0
    private var pendingImportItemEdits: [String: ImportItemUI] = [:]
    private var pendingImportItemOrder: [String] = []
    private var importItemEditInFlight = false
    #if canImport(OmniFlowShared)
    private var analyticsSubscription: AppleFlowSubscription?
    private var homeSubscription: AppleFlowSubscription?
    private var categorySubscription: AppleFlowSubscription?
    private var tagSubscription: AppleFlowSubscription?
    private var ruleSubscription: AppleFlowSubscription?
    private var dateDetailSubscription: AppleFlowSubscription?
    private var importSubscription: AppleFlowSubscription?
    private var latestImportPreview: ImportPreviewState?
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
                if let message { self?.error = message }
                self?.startObservers()
            }
        }
        #else
        loading = false
        #endif
    }

    func observeAnalytics(start: Date, end: Date, ledgerID: String? = nil) {
        analyticsObservation = (start, end, ledgerID)
        #if canImport(OmniFlowShared)
        analyticsSubscription?.cancel()
        analyticsSubscription = bridge.watchAnalytics(
            ledgerId: ledgerID,
            startMillis: Int64(start.timeIntervalSince1970 * 1000),
            endMillis: Int64(end.timeIntervalSince1970 * 1000),
            rankingTypeName: analyticsRankingType.rawValue,
            categoryTypeName: analyticsCategoryType.rawValue
        ) { [weak self] value, message in
            Task { @MainActor in
                if let message { self?.error = message }
                self?.analyticsExpenseMinor = value?.summary.expenseTotal ?? 0
                self?.analyticsIncomeMinor = value?.summary.incomeTotal ?? 0
                self?.analyticsRanking = value?.ranking.map {
                    AnalyticsRankingUI(
                        id: "\($0.categoryId)-\($0.primaryCategoryName)-\($0.secondaryCategoryName ?? "")",
                        primaryName: $0.primaryCategoryName,
                        secondaryName: $0.secondaryCategoryName,
                        iconKey: $0.iconKey,
                        amount: $0.amount
                    )
                } ?? []
                self?.analyticsCategories = value?.categoryBreakdowns.map {
                    CategoryBreakdownUI(
                        id: $0.primaryCategoryId,
                        name: $0.primaryCategoryName,
                        iconKey: $0.iconKey,
                        amount: $0.amount,
                        secondary: $0.secondaryCategories.map {
                            CategoryShareUI(id: $0.categoryId, name: $0.categoryName, iconKey: $0.iconKey, amount: $0.amount)
                        }
                    )
                } ?? []
                if let statement = value?.yearStatement {
                    self?.analyticsYearStatement = StatementTableUI(
                        year: Int(statement.year),
                        months: statement.months.map { StatementMonthUI(month: Int($0.month), expenseMinor: $0.expense, incomeMinor: $0.income) },
                        expenseMinor: statement.total.expenseTotal,
                        incomeMinor: statement.total.incomeTotal
                    )
                } else {
                    self?.analyticsYearStatement = nil
                }
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

    func calendarAmountText(_ minor: Int64) -> String {
        #if canImport(OmniFlowShared)
        bridge.calendarAmountText(amountMinor: minor)
        #else
        "--"
        #endif
    }

    func yearMonthText(_ date: Date) -> String {
        #if canImport(OmniFlowShared)
        bridge.yearMonthText(epochMillis: Int64(date.timeIntervalSince1970 * 1000))
        #else
        "--"
        #endif
    }

    func hourMinuteText(_ date: Date) -> String {
        #if canImport(OmniFlowShared)
        bridge.hourMinuteText(epochMillis: Int64(date.timeIntervalSince1970 * 1000))
        #else
        "--:--"
        #endif
    }

    func transactionDateTimeText(_ date: Date) -> String {
        #if canImport(OmniFlowShared)
        bridge.transactionDateTimeText(epochMillis: Int64(date.timeIntervalSince1970 * 1000))
        #else
        "---- -- -- --:--"
        #endif
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
                if let message { self?.error = message }
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
        let start = Calendar.current.startOfDay(for: date)
        let end = Calendar.current.date(byAdding: .day, value: 1, to: start) ?? start.addingTimeInterval(86_400)
        showTransactionDetails(range: DateInterval(start: start, end: end), ledgerID: selectedLedgerID, type: nil)
    }

    func showTransactionDetails(range: DateInterval, ledgerID: String?, type: EntryType?) {
        selectedDetailRange = range
        dateDetailLedgerID = ledgerID
        dateDetailType = type
        observeDateDetails()
    }

    func selectDateDetailLedger(_ id: String?) {
        guard dateDetailLedgerID != id else { return }
        dateDetailLedgerID = id
        observeDateDetails()
    }

    func retryDateDetails() {
        observeDateDetails()
    }

    func dismissDateDetail() {
        dateDetailGeneration += 1
        #if canImport(OmniFlowShared)
        dateDetailSubscription?.cancel()
        #endif
        selectedDetailRange = nil
        dateDetailType = nil
        dateDetailTransactions = []
        dateDetailExpenseMinor = 0
        dateDetailIncomeMinor = 0
        dateDetailStatus = .idle
    }

    func transitionFromDateDetail(to transaction: TransactionUI) {
        pendingTransactionDetail = transaction
        dismissDateDetail()
    }

    func transitionFromDateDetailToNewTransaction() {
        guard let range = selectedDetailRange else { return }
        pendingDateDetailDraft = (range.start, dateDetailLedgerID)
        dismissDateDetail()
    }

    func presentPendingDateDetailDestination() {
        if let pendingTransactionDetail {
            self.pendingTransactionDetail = nil
            showTransactionDetail(pendingTransactionDetail)
        } else if let pendingDateDetailDraft {
            self.pendingDateDetailDraft = nil
            startNewTransaction(date: pendingDateDetailDraft.date, ledgerID: pendingDateDetailDraft.ledgerID)
        }
    }

    var hasSearchFilters: Bool {
        !searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            searchLedgerID != nil || searchType != nil || searchAccountID != nil ||
            !searchPrimaryCategoryText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            !searchSecondaryCategoryText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            !searchTagText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            !searchNoteText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            !searchMinimumAmount.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            !searchMaximumAmount.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            searchDateEnabled
    }

    func scheduleSearch() {
        searchDebounceTask?.cancel()
        guard hasSearchFilters else {
            resetSearchState()
            return
        }
        searchDebounceTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard !Task.isCancelled else { return }
            self?.performSearch()
        }
    }

    func search() {
        searchDebounceTask?.cancel()
        performSearch()
    }

    func clearSearch() {
        searchText = ""
        searchPrimaryCategoryText = ""
        searchSecondaryCategoryText = ""
        searchTagText = ""
        searchNoteText = ""
        searchMinimumAmount = ""
        searchMaximumAmount = ""
        searchDateEnabled = false
        searchType = nil
        searchLedgerID = nil
        searchAccountID = nil
        resetSearchState()
    }

    private func performSearch() {
        guard hasSearchFilters else {
            resetSearchState()
            return
        }
        #if canImport(OmniFlowShared)
        let minimumResult = Self.minorUnits(searchMinimumAmount)
        let maximumResult = Self.minorUnits(searchMaximumAmount)
        if let message = minimumResult.message ?? maximumResult.message {
            failSearch(message)
            return
        }
        let minimum = minimumResult.value
        let maximum = maximumResult.value
        if let minimum, let maximum, minimum > maximum {
            failSearch("最低金额不能大于最高金额")
            return
        }
        searchGeneration += 1
        let generation = searchGeneration
        searchStatus = .loading
        bridge.search(
            keyword: searchText,
            primaryCategoryText: searchPrimaryCategoryText,
            secondaryCategoryText: searchSecondaryCategoryText,
            tagText: searchTagText,
            noteText: searchNoteText,
            ledgerId: searchLedgerID,
            typeName: searchType?.rawValue,
            accountId: searchAccountID,
            primaryCategoryId: nil,
            secondaryCategoryId: nil,
            tagId: nil,
            exactMinor: nil,
            minimumMinor: Self.boxedLong(minimum),
            maximumMinor: Self.boxedLong(maximum),
            startMillis: Self.boxedLong(searchDateEnabled ? Int64(Calendar.current.startOfDay(for: min(searchStartDate, searchEndDate)).timeIntervalSince1970 * 1000) : nil),
            endMillis: Self.boxedLong(searchDateEnabled ? Int64((Calendar.current.date(byAdding: .day, value: 1, to: Calendar.current.startOfDay(for: max(searchStartDate, searchEndDate))) ?? max(searchStartDate, searchEndDate)).timeIntervalSince1970 * 1000) : nil)
        ) { [weak self] result, message in
            Task { @MainActor in
                guard let self, generation == self.searchGeneration else { return }
                if let message {
                    self.failSearch(message)
                    return
                }
                self.searchResults = result?.items.map { Self.transaction($0.transaction, tagNames: $0.tags.map { $0.name }) } ?? []
                self.searchExpenseMinor = result?.summary.expenseTotal ?? 0
                self.searchIncomeMinor = result?.summary.incomeTotal ?? 0
                self.searchStatus = .loaded
            }
        }
        #else
        searchResults = transactions.filter { $0.note.localizedCaseInsensitiveContains(searchText) }
        searchStatus = .loaded
        #endif
    }

    private static func minorUnits(_ text: String) -> (value: Int64?, message: String?) {
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return (nil, nil) }
        let pattern = #"^(?:\d+(?:\.\d{0,2})?|\.\d{1,2})$"#
        guard value.range(of: pattern, options: .regularExpression) != nil,
              let decimal = Decimal(string: value.hasPrefix(".") ? "0\(value)" : value, locale: Locale(identifier: "en_US_POSIX")) else {
            return (nil, "金额格式有误，最多输入两位小数")
        }
        return (NSDecimalNumber(decimal: decimal * 100).int64Value, nil)
    }

    private func failSearch(_ message: String) {
        searchGeneration += 1
        searchResults = []
        searchExpenseMinor = 0
        searchIncomeMinor = 0
        searchStatus = .failed(message)
    }

    private func resetSearchState() {
        searchDebounceTask?.cancel()
        searchGeneration += 1
        searchResults = []
        searchExpenseMinor = 0
        searchIncomeMinor = 0
        searchStatus = .idle
    }

    func setSearchLedger(_ id: String?) {
        searchLedgerID = id
        search()
    }

    func prepareSearch() {
        search()
    }

    func showTransactionDetail(_ transaction: TransactionUI) {
        selectedTransactionDetail = transaction
    }

    func dismissTransactionDetail() {
        selectedTransactionDetail = nil
    }

    func editSelectedTransaction() {
        guard let transaction = selectedTransactionDetail else { return }
        selectedTransactionDetail = nil
        DispatchQueue.main.async { [weak self] in self?.editTransaction(transaction) }
    }

    func loadTransactionRecordDetail(_ id: String, completion: @escaping (TransactionRecordDetailUI?, String?) -> Void) {
        #if canImport(OmniFlowShared)
        bridge.loadTransactionRecordDetail(id: id) { detail, message in
            let value = detail.map {
                TransactionRecordDetailUI(
                    primaryCategoryName: $0.primaryCategoryName,
                    secondaryCategoryName: $0.secondaryCategoryName,
                    tagNames: $0.tagNames
                )
            }
            Task { @MainActor in completion(value, message) }
        }
        #else
        completion(nil, "共享 Framework 尚未构建")
        #endif
    }

    func deleteSelectedTransaction(completion: @escaping (String?) -> Void) {
        guard let id = selectedTransactionDetail?.id else { return }
        deleteTransaction(id) { [weak self] message in
            if message == nil { self?.selectedTransactionDetail = nil }
            completion(message)
        }
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
        ) { [weak self] message in
            Task { @MainActor in
                if message == nil { self?.refreshFinancialViews() }
                completion(message)
            }
        }
        #else
        completion("共享 Framework 尚未构建")
        #endif
    }

    func startNewTransaction(date: Date? = nil, ledgerID: String? = nil) {
        editingTransaction = nil
        editingTagIDs = []
        draftTransactionDate = date
        draftTransactionLedgerID = ledgerID ?? defaultLedgerID
        transactionDraftRevision = UUID()
        selectResourceLedger(draftTransactionLedgerID)
        destination = .transaction
    }

    func prepareTransactionEdit(_ transaction: TransactionUI) {
        editingTransaction = transaction
        editingTagIDs = []
        draftTransactionDate = nil
        draftTransactionLedgerID = transaction.ledgerID
        transactionDraftRevision = UUID()
        transactionTagLoadGeneration += 1
        let tagGeneration = transactionTagLoadGeneration
        selectResourceLedger(transaction.ledgerID)
        #if canImport(OmniFlowShared)
        bridge.loadTransaction(id: transaction.id) { [weak self] value, message in
            Task { @MainActor in
                if let message { self?.error = message }
                guard self?.transactionTagLoadGeneration == tagGeneration else { return }
                self?.editingTagIDs = Set(value?.tagIds ?? [])
            }
        }
        #endif
    }

    func editTransaction(_ transaction: TransactionUI) {
        prepareTransactionEdit(transaction)
        destination = .transaction
    }

    func clearTransactionTagsForLedgerChange() {
        transactionTagLoadGeneration += 1
        editingTagIDs = []
    }

    func deleteTransaction(_ id: String, completion: @escaping (String?) -> Void) {
        #if canImport(OmniFlowShared)
        bridge.deleteTransaction(id: id) { [weak self] message in
            Task { @MainActor in
                if message == nil { self?.refreshFinancialViews() }
                completion(message)
            }
        }
        #else
        completion("共享 Framework 尚未构建")
        #endif
    }

    private func refreshFinancialViews() {
        observeHome()
        if let analyticsObservation {
            observeAnalytics(
                start: analyticsObservation.start,
                end: analyticsObservation.end,
                ledgerID: analyticsObservation.ledgerID
            )
        }
    }

    func saveLedger(id: String?, name: String, coverKey: String?, completion: @escaping (String?) -> Void = { _ in }) { performManagement(completion: completion) { done in
        #if canImport(OmniFlowShared)
        bridge.saveLedger(id: id, name: name, coverKey: coverKey, callback: done)
        #else
        done("共享 Framework 尚未构建")
        #endif
    } }

    func deleteLedger(_ id: String) { performManagement { done in
        #if canImport(OmniFlowShared)
        bridge.deleteLedger(id: id, callback: done)
        #else
        done("共享 Framework 尚未构建")
        #endif
    } }

    func setDefaultLedger(_ id: String?) { performManagement { done in
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
        included: Bool,
        completion: @escaping (String?) -> Void = { _ in }
    ) { performManagement(completion: completion) { done in
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

    func deleteAccount(_ id: String) { performManagement { done in
        #if canImport(OmniFlowShared)
        bridge.deleteAccount(id: id, callback: done)
        #else
        done("共享 Framework 尚未构建")
        #endif
    } }

    func saveCategory(id: String?, name: String, type: EntryType, parentID: String? = nil, iconKey: String? = nil, completion: @escaping (String?) -> Void = { _ in }) {
        guard let ledgerID = resourceLedgerID else { completeManagement("请先选择账本", completion: completion); return }
        performManagement(completion: completion) { done in
            #if canImport(OmniFlowShared)
            bridge.saveCategory(id: id, ledgerId: ledgerID, parentId: parentID, name: name, iconKey: parentID == nil ? (iconKey ?? "category") : nil, typeName: type.rawValue, callback: done)
            #else
            done("共享 Framework 尚未构建")
            #endif
        }
    }

    func deleteCategory(_ id: String) { performManagement { done in
        #if canImport(OmniFlowShared)
        bridge.deleteCategory(id: id, callback: done)
        #else
        done("共享 Framework 尚未构建")
        #endif
    } }

    func reorderPrimaryCategories(type: EntryType, orderedIDs: [String]) {
        guard let ledgerID = resourceLedgerID else { managementError = "请先选择账本"; return }
        performManagement { done in
            #if canImport(OmniFlowShared)
            bridge.reorderPrimaryCategories(ledgerId: ledgerID, typeName: type.rawValue, orderedIds: orderedIDs, callback: done)
            #else
            done("共享 Framework 尚未构建")
            #endif
        }
    }

    func saveTag(id: String?, name: String, completion: @escaping (String?) -> Void = { _ in }) {
        guard let ledgerID = resourceLedgerID else { completeManagement("请先选择账本", completion: completion); return }
        performManagement(completion: completion) { done in
            #if canImport(OmniFlowShared)
            bridge.saveTag(id: id, ledgerId: ledgerID, name: name, callback: done)
            #else
            done("共享 Framework 尚未构建")
            #endif
        }
    }

    func deleteTag(_ id: String) { performManagement { done in
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
        priority: Int,
        completion: @escaping (String?) -> Void = { _ in }
    ) {
        guard let ledgerID = resourceLedgerID else { completeManagement("请先选择账本", completion: completion); return }
        performManagement(completion: completion) { done in
            #if canImport(OmniFlowShared)
            bridge.saveRule(id: id, ledgerId: ledgerID, name: name, conditionTypeName: conditionType, conditionValue: conditionValue, actionTypeName: actionType, actionValue: actionValue, priority: Int32(priority), callback: done)
            #else
            done("共享 Framework 尚未构建")
            #endif
        }
    }

    func deleteRule(_ id: String) { performManagement { done in
        #if canImport(OmniFlowShared)
        bridge.deleteRule(id: id, callback: done)
        #else
        done("共享 Framework 尚未构建")
        #endif
    } }

    func moveRule(_ id: String, offset: Int) {
        guard let ledgerID = resourceLedgerID else { managementError = "请先选择账本"; return }
        var ordered = rules.sorted { $0.priority < $1.priority }
        guard let from = ordered.firstIndex(where: { $0.id == id }) else { return }
        let to = from + offset
        guard ordered.indices.contains(to) else { return }
        let moved = ordered.remove(at: from)
        ordered.insert(moved, at: to)
        performManagement { done in
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
        paused: Bool,
        completion: @escaping (String?) -> Void = { _ in }
    ) { performManagement(completion: completion) { done in
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

    func deleteReminder(_ id: String) { performManagement { done in
        #if canImport(OmniFlowShared)
        bridge.deleteReminder(id: id, callback: done)
        #else
        done("共享 Framework 尚未构建")
        #endif
    } }

    func setReminderPaused(_ id: String, paused: Bool) { performManagement { done in
        #if canImport(OmniFlowShared)
        bridge.setReminderPaused(id: id, paused: paused, callback: done)
        #else
        done("共享 Framework 尚未构建")
        #endif
    } }

    func syncNow(target: String = "ICLOUD", retention: Int = 10) { performDataManagement { done in
        #if canImport(OmniFlowShared)
        bridge.configureSync(targetName: target, retention: Int32(retention)) { message in
            if let message { done(message) } else { self.bridge.syncNow(callback: done) }
        }
        #else
        done("共享 Framework 尚未构建")
        #endif
    } }

    func loadBackups() {
        dataManagementError = nil
        #if canImport(OmniFlowShared)
        bridge.listBackups { [weak self] values, message in
            Task { @MainActor in
                self?.dataManagementError = message
                self?.backupObjects = values ?? []
                self?.backups = values?.map { BackupUI(id: $0.backupId, createdAt: String(describing: $0.createdAt)) } ?? []
            }
        }
        #endif
    }

    func restoreBackup(id: String) {
        #if canImport(OmniFlowShared)
        guard let backup = backupObjects.first(where: { $0.backupId == id }) else { return }
        performDataManagement { done in bridge.restoreBackup(meta: backup, callback: done) }
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

    func setThemeColor(_ color: String) {
        themeColor = color
        perform { done in
            #if canImport(OmniFlowShared)
            bridge.setThemeColor(colorName: color, callback: done)
            #else
            done(nil)
            #endif
        }
    }

    func exportQingzi(start: Date? = nil, end: Date? = nil, completion: @escaping (String?) -> Void) {
        dataManagementError = nil
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
            Task { @MainActor in self?.dataManagementError = message; completion(payload) }
        }
        #else
        completion(nil)
        dataManagementError = "共享 Framework 尚未构建"
        #endif
    }

    func importFile(_ url: URL, selectedFormat: AppleImportFormat? = nil) {
        clearImportFeedback()
        guard let ledgerID = resourceLedgerID else { importError = "请先选择账本"; return }
        importSessionGeneration += 1
        clearImportEditQueue()
        importSessionLedgerID = ledgerID
        importUpdating = true
        #if canImport(OmniFlowShared)
        Task {
            do {
                let bytes = try await Task.detached { () throws -> KotlinByteArray in
                    let accessing = url.startAccessingSecurityScopedResource()
                    defer { if accessing { url.stopAccessingSecurityScopedResource() } }
                    let data = try Data(contentsOf: url, options: .mappedIfSafe)
                    let bytes = KotlinByteArray(size: Int32(data.count))
                    data.enumerated().forEach { bytes.set(index: Int32($0.offset), value: Int8(bitPattern: $0.element)) }
                    return bytes
                }.value
                importSubscription?.cancel()
                importSubscription = bridge.previewImport(ledgerId: ledgerID, fileName: url.lastPathComponent, bytes: bytes, formatName: selectedFormat?.rawValue) { [weak self] preview, message in
                    Task { @MainActor in
                        self?.importError = message
                        self?.importUpdating = message == nil && Double(preview?.progress ?? 0) < 1
                        if message != nil, preview == nil { self?.importSessionLedgerID = nil }
                        self?.applyImportPreview(preview)
                    }
                }
            } catch {
                self.importError = error.localizedDescription
                self.importUpdating = false
                self.importSessionLedgerID = nil
            }
        }
        #else
        importUpdating = false
        importSessionLedgerID = nil
        importError = "共享 Framework 尚未构建"
        #endif
    }

    func updateImportItem(_ item: ImportItemUI) {
        guard let sessionID = importSessionID else { return }
        if pendingImportItemEdits.isEmpty, !importItemEditInFlight { importError = nil }
        if pendingImportItemEdits[item.id] == nil { pendingImportItemOrder.append(item.id) }
        pendingImportItemEdits[item.id] = item
        importUpdating = true
        importReady = false
        #if canImport(OmniFlowShared)
        sendNextImportItemEdit(sessionID: sessionID)
        #endif
    }

    #if canImport(OmniFlowShared)
    private func sendNextImportItemEdit(sessionID: String) {
        guard !importItemEditInFlight, let itemID = pendingImportItemOrder.first else { return }
        pendingImportItemOrder.removeFirst()
        guard let item = pendingImportItemEdits.removeValue(forKey: itemID) else {
            sendNextImportItemEdit(sessionID: sessionID)
            return
        }
        let generation = importSessionGeneration
        importItemEditInFlight = true
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
            Task { @MainActor in
                guard let self else { return }
                guard generation == self.importSessionGeneration else { return }
                if let message { self.importError = message }
                if let preview { self.latestImportPreview = preview }
                self.importItemEditInFlight = false
                self.sendNextImportItemEdit(sessionID: sessionID)
                self.finishImportEditsIfNeeded()
            }
        }
    }
    #endif

    func setImportSelection(_ id: String, selected: Bool) {
        if selected { selectedImportItemIDs.insert(id) } else { selectedImportItemIDs.remove(id) }
    }

    func selectAllImportItems() {
        selectedImportItemIDs = Set(importItems.map(\.id))
    }

    func invertImportSelection() {
        selectedImportItemIDs = Set(importItems.map(\.id)).subtracting(selectedImportItemIDs)
    }

    func setSelectedImportCategory(_ categoryID: String?) {
        guard let sessionID = importSessionID, !selectedImportItemIDs.isEmpty, !importUpdating else { return }
        importUpdating = true
        importReady = false
        #if canImport(OmniFlowShared)
        bridge.editImportCategories(sessionId: sessionID, itemIds: selectedImportItemIDs, categoryId: categoryID) { [weak self] preview, message in
            Task { @MainActor in self?.importError = message; self?.importUpdating = false; self?.applyImportPreview(preview) }
        }
        #else
        importUpdating = false
        #endif
    }

    func setSelectedImportSkipped(_ skipped: Bool) {
        guard let sessionID = importSessionID, !selectedImportItemIDs.isEmpty, !importUpdating else { return }
        importUpdating = true
        importReady = false
        #if canImport(OmniFlowShared)
        bridge.editImportSkipped(sessionId: sessionID, itemIds: selectedImportItemIDs, skipped: skipped) { [weak self] preview, message in
            Task { @MainActor in self?.importError = message; self?.importUpdating = false; self?.applyImportPreview(preview) }
        }
        #else
        importUpdating = false
        #endif
    }

    func commitImport() {
        guard let sessionID = importSessionID, !importUpdating else { return }
        #if canImport(OmniFlowShared)
        bridge.commitImport(sessionId: sessionID) { [weak self] result, message in
            Task { @MainActor in
                self?.importError = message
                self?.importMessage = message == nil ? result.map { "已导入 \($0.importedCount) 条" } : nil
                if message == nil, result != nil {
                    self?.clearImportSessionState()
                }
            }
        }
        #endif
    }

    func resetImportSession() {
        #if canImport(OmniFlowShared)
        importSubscription?.cancel()
        guard let sessionID = importSessionID else { clearImportSessionState(); return }
        importSessionGeneration += 1
        clearImportEditQueue()
        importUpdating = true
        bridge.cancelImport(sessionId: sessionID) { [weak self] message in
            Task { @MainActor in
                self?.importError = message
                self?.importUpdating = false
                if message == nil { self?.clearImportSessionState() }
            }
        }
        #else
        clearImportSessionState()
        #endif
    }

    func restoreImportLedgerSelection() {
        guard let importSessionLedgerID, resourceLedgerID != importSessionLedgerID else { return }
        selectResourceLedger(importSessionLedgerID)
    }

    func clearImportFeedback() {
        importError = nil
        importMessage = nil
    }

    private func perform(_ operation: (@escaping (String?) -> Void) -> Void) {
        operation { [weak self] message in Task { @MainActor in self?.error = message } }
    }

    private func performManagement(completion: @escaping (String?) -> Void = { _ in }, _ operation: (@escaping (String?) -> Void) -> Void) {
        managementError = nil
        operation { [weak self] message in
            Task { @MainActor in
                self?.managementError = message
                completion(message)
            }
        }
    }

    private func completeManagement(_ message: String, completion: @escaping (String?) -> Void) {
        managementError = message
        completion(message)
    }

    private func performDataManagement(_ operation: (@escaping (String?) -> Void) -> Void) {
        dataManagementError = nil
        operation { [weak self] message in Task { @MainActor in self?.dataManagementError = message } }
    }

    #if canImport(OmniFlowShared)
    private func applyImportPreview(_ preview: ImportPreviewState?) {
        if let preview, !preview.sessionId.isEmpty {
            importSessionID = preview.sessionId
            importSessionLedgerID = preview.ledgerId
        }
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

    private func finishImportEditsIfNeeded() {
        guard pendingImportItemEdits.isEmpty, pendingImportItemOrder.isEmpty, !importItemEditInFlight else { return }
        importUpdating = false
        if let latestImportPreview {
            self.latestImportPreview = nil
            applyImportPreview(latestImportPreview)
        }
    }

    private func startObservers() {
        subscriptions.append(bridge.watchLedgers { [weak self] values, message in
            Task { @MainActor in
                if let message { self?.error = message }
                self?.ledgers = values?.map { LedgerUI(id: $0.id, name: $0.name, coverKey: $0.coverKey) } ?? []
                if self?.resourceLedgerID == nil {
                    self?.resourceLedgerID = self?.selectedLedgerID ?? self?.ledgers.first?.id
                    self?.observeCategories()
                }
                self?.observeHome()
            }
        })
        subscriptions.append(bridge.watchDefaultLedgerId { [weak self] value, message in
            Task { @MainActor in if let message { self?.error = message }; self?.defaultLedgerID = value }
        })
        subscriptions.append(bridge.watchAccounts { [weak self] values, message in
            Task { @MainActor in
                if let message { self?.error = message }
                self?.accounts = values?.map {
                    AccountUI(
                        id: $0.id,
                        name: $0.name,
                        balanceMinor: $0.balance,
                        type: String(describing: $0.type).components(separatedBy: ".").last ?? "CASH",
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
                if let message { self?.error = message }
                self?.reminders = reminders
            }
        })
        subscriptions.append(bridge.watchPreferenceSnapshot { [weak self] value, message in
            Task { @MainActor in
                if let message { self?.error = message }
                self?.appLockEnabled = value?.appLockEnabled ?? false
                self?.appearanceMode = value?.appearanceMode ?? "SYSTEM"
                self?.themeColor = value?.themeColor ?? "LAVENDER"
                self?.selectedLedgerID = value?.homeLedgerId
                self?.analyticsLedgerID = value?.analyticsLedgerId
                self?.transactionDisplayMode = TransactionDisplayMode(rawValue: value?.transactionDetailDisplayMode ?? "LIST") ?? .list
                if self?.resourceLedgerID == nil { self?.resourceLedgerID = value?.homeLedgerId ?? self?.ledgers.first?.id }
                self?.observeHome()
                self?.observeCategories()
            }
        })
        subscriptions.append(bridge.watchSyncState { [weak self] value, message in
            Task { @MainActor in
                if let message { self?.dataManagementError = message }
                self?.syncPhase = value?.phase ?? "IDLE"
                self?.syncProgress = value?.progress?.doubleValue
                self?.syncLastBackupAt = value?.lastBackupAt
                self?.syncError = value?.errorMessage
                if value?.phase == "SUCCESS" { self?.loadBackups() }
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
                if let message { self?.error = message }
                self?.expenseMinor = value?.summary.expenseTotal ?? 0
                self?.incomeMinor = value?.summary.incomeTotal ?? 0
                self?.transactions = value?.groups.flatMap { $0.items }.map { Self.transaction($0) } ?? []
                let filterName = self?.calendarFilter ?? "ALL"
                self?.calendarDays = value?.calendar.map { summary in
                    let display = self?.bridge.calendarDisplayAmount(summary: summary, filterName: filterName)
                    return CalendarDayUI(
                        id: "\(summary.date.year)-\(summary.date.monthNumber)-\(summary.date.dayOfMonth)",
                        date: Self.date(year: Int(summary.date.year), month: Int(summary.date.monthNumber), day: Int(summary.date.dayOfMonth)),
                        expenseMinor: summary.expenseTotal,
                        incomeMinor: summary.incomeTotal,
                        displayAmountMinor: display?.amount,
                        displayIsIncome: display?.isIncome ?? false
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
                if let message { self?.error = message }
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
            Task { @MainActor in if let message { self?.error = message }; self?.tags = values?.map { TagUI(id: $0.id, name: $0.name) } ?? [] }
        }
        ruleSubscription = bridge.watchRules(ledgerId: ledgerID) { [weak self] values, message in
            Task { @MainActor in
                if let message { self?.error = message }
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
        guard let range = selectedDetailRange else { return }
        dateDetailGeneration += 1
        let generation = dateDetailGeneration
        dateDetailStatus = .loading
        dateDetailTransactions = []
        dateDetailExpenseMinor = 0
        dateDetailIncomeMinor = 0
        dateDetailSubscription?.cancel()
        dateDetailSubscription = bridge.watchTransactionDetails(
            ledgerId: dateDetailLedgerID,
            startMillis: Int64(range.start.timeIntervalSince1970 * 1000),
            endMillis: Int64(range.end.timeIntervalSince1970 * 1000),
            typeName: dateDetailType?.rawValue
        ) { [weak self] value, message in
            Task { @MainActor in
                guard self?.dateDetailGeneration == generation else { return }
                if let message {
                    self?.dateDetailStatus = .failed(message)
                    return
                }
                self?.dateDetailExpenseMinor = value?.summary.expenseTotal ?? 0
                self?.dateDetailIncomeMinor = value?.summary.incomeTotal ?? 0
                self?.dateDetailTransactions = value?.items.map { Self.transaction($0) } ?? []
                self?.dateDetailStatus = .loaded
            }
        }
    }

    private static func transaction(_ value: TransactionListItem, tagNames: [String] = []) -> TransactionUI {
        TransactionUI(
            id: value.id,
            ledgerID: value.ledgerId,
            ledgerName: value.ledgerName,
            accountID: value.accountId,
            accountName: value.accountName,
            categoryID: value.categoryId,
            categoryName: value.categoryName,
            primaryCategoryName: value.primaryCategoryName,
            categoryIconKey: value.categoryIconKey,
            amountMinor: value.amount,
            type: String(describing: value.type).contains("INCOME") ? .income : .expense,
            date: Date(timeIntervalSince1970: TimeInterval(value.occurredAt.epochSeconds)),
            note: value.note ?? "",
            excluded: value.isExcluded,
            source: value.source.map { String(describing: $0).components(separatedBy: ".").last ?? "" },
            tagNames: tagNames,
            categoryDisplayName: value.categoryDisplayName
        )
    }

    private static func boxedLong(_ value: Int64?) -> KotlinLong? {
        value.map { KotlinLong(value: $0) }
    }
    #else
    private func observeHome() {}
    private func observeCategories() {}
    private func observeDateDetails() { dateDetailStatus = .failed("共享 Framework 尚未构建") }
    #endif

    private func clearImportEditQueue() {
        pendingImportItemEdits = [:]
        pendingImportItemOrder = []
        importItemEditInFlight = false
        #if canImport(OmniFlowShared)
        latestImportPreview = nil
        #endif
    }

    private func clearImportSessionState() {
        importSessionGeneration += 1
        clearImportEditQueue()
        importItems = []
        selectedImportItemIDs = []
        importSessionID = nil
        importSessionLedgerID = nil
        importProgress = 0
        importReady = false
        importUpdating = false
    }

    private static func monthInterval(_ date: Date) -> DateInterval {
        Calendar.current.dateInterval(of: .month, for: date) ?? DateInterval(start: date, duration: 30 * 86_400)
    }

    private static func date(year: Int, month: Int, day: Int) -> Date {
        Calendar.current.date(from: DateComponents(year: year, month: month, day: day)) ?? Date()
    }

}
