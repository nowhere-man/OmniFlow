package com.omniflow.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omniflow.shared.SharedApp
import com.omniflow.shared.domain.model.Account
import com.omniflow.shared.domain.model.AccountSummary
import com.omniflow.shared.domain.model.AccountType
import com.omniflow.shared.domain.model.AnalyticsDashboardState
import com.omniflow.shared.domain.model.AnalyticsQuery
import com.omniflow.shared.domain.model.AppPreferences
import com.omniflow.shared.domain.model.AppearanceMode
import com.omniflow.shared.domain.model.CalendarTransactionFilter
import com.omniflow.shared.domain.model.Category
import com.omniflow.shared.domain.model.CategoryShareGranularity
import com.omniflow.shared.domain.model.DateRange
import com.omniflow.shared.domain.model.HomeQuery
import com.omniflow.shared.domain.model.HomeState
import com.omniflow.shared.domain.model.Ledger
import com.omniflow.shared.domain.model.LedgerScope
import com.omniflow.shared.domain.model.Money
import com.omniflow.shared.domain.model.ImportCategoryBatchEdit
import com.omniflow.shared.domain.model.ImportExcludeBatchEdit
import com.omniflow.shared.domain.model.ImportPreviewEdit
import com.omniflow.shared.domain.model.ImportPreviewState
import com.omniflow.shared.domain.model.ImportRequest
import com.omniflow.shared.domain.model.QingziExportRequest
import com.omniflow.shared.domain.model.RemoteBackupMeta
import com.omniflow.shared.domain.model.Reminder
import com.omniflow.shared.domain.model.ReminderSchedule
import com.omniflow.shared.domain.model.ReminderScheduleKind
import com.omniflow.shared.domain.model.ReminderType
import com.omniflow.shared.domain.model.Rule
import com.omniflow.shared.domain.model.RuleActionType
import com.omniflow.shared.domain.model.RuleConditionType
import com.omniflow.shared.domain.model.SearchResult
import com.omniflow.shared.domain.model.StatementTable
import com.omniflow.shared.domain.model.SyncConfig
import com.omniflow.shared.domain.model.SyncState
import com.omniflow.shared.domain.model.SyncTarget
import com.omniflow.shared.domain.model.ThemeColor
import com.omniflow.shared.domain.model.Tag
import com.omniflow.shared.domain.model.Transaction
import com.omniflow.shared.domain.model.TransactionDetailDisplayMode
import com.omniflow.shared.domain.model.TransactionDetailQuery
import com.omniflow.shared.domain.model.TransactionDetailState
import com.omniflow.shared.domain.model.TransactionSearchQuery
import com.omniflow.shared.domain.model.TransactionType
import com.omniflow.shared.domain.usecase.CreateTransactionCommand
import com.omniflow.shared.parser.ImportFormat
import java.time.LocalDate as JavaLocalDate
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

data class HomeUiState(
    val home: HomeState? = null,
    val ledgers: List<Ledger> = emptyList(),
    val isLedgerMenuExpanded: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedDate: TransactionDetailState? = null,
    val selectedDateScope: LedgerScope? = null,
    val isDateLoading: Boolean = false,
    val dateError: String? = null,
    val displayMode: TransactionDetailDisplayMode = TransactionDetailDisplayMode.LIST,
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    val calendarFilter: CalendarTransactionFilter = CalendarTransactionFilter.ALL,
)

enum class AnalyticsRangeMode { WEEK, MONTH, YEAR, CUSTOM }

data class AnalyticsUiState(
    val dashboard: AnalyticsDashboardState? = null,
    val statementTable: StatementTable? = null,
    val ledgers: List<Ledger> = emptyList(),
    val scope: LedgerScope = LedgerScope.All,
    val rangeMode: AnalyticsRangeMode = AnalyticsRangeMode.MONTH,
    val range: DateRange = monthRange(Clock.System.now()),
    val rankingType: TransactionType = TransactionType.EXPENSE,
    val categoryType: TransactionType = TransactionType.EXPENSE,
    val categoryGranularity: CategoryShareGranularity = CategoryShareGranularity.PRIMARY,
    val primaryCategoryId: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

data class SearchUiState(
    val query: TransactionSearchQuery = TransactionSearchQuery(),
    val result: SearchResult? = null,
    val ledgers: List<Ledger> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class TransactionRecordDetailUiState(
    val transaction: Transaction? = null,
    val ledgerName: String = "",
    val accountName: String = "",
    val primaryCategoryName: String = "",
    val secondaryCategoryName: String? = null,
    val categoryIconKey: String? = null,
    val tagNames: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
    val error: String? = null,
) {
    val isVisible: Boolean get() = isLoading || transaction != null || error != null
    val categoryDisplayName: String get() = secondaryCategoryName?.let { "$primaryCategoryName-$it" } ?: primaryCategoryName
}

data class TransactionEditorUiState(
    val editingId: String? = null,
    val ledgers: List<Ledger> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val ledgerId: String? = null,
    val accountId: String? = null,
    val categoryId: String? = null,
    val selectedTagIds: Set<String> = emptySet(),
    val type: TransactionType = TransactionType.EXPENSE,
    val occurredAt: Instant = Clock.System.now(),
    val amountInput: String = "",
    val note: String = "",
    val isExcluded: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val completed: Boolean = false,
)

data class MoreUiState(
    val accountSummary: AccountSummary = AccountSummary(Money.Zero, Money.Zero),
    val ledgers: List<Ledger> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val defaultLedgerId: String? = null,
    val selectedLedgerId: String? = null,
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val rules: List<Rule> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val preferences: AppPreferences = AppPreferences(),
    val syncState: SyncState = SyncState(),
    val backups: List<RemoteBackupMeta> = emptyList(),
    val importPreview: ImportPreviewState? = null,
    val selectedImportItemIds: Set<String> = emptySet(),
    val importFileName: String? = null,
    val importFormat: ImportFormat? = null,
    val importMessage: String? = null,
    val exportPayload: String? = null,
    val exportWarnings: List<String> = emptyList(),
    val isImporting: Boolean = false,
    val isExporting: Boolean = false,
    val isLoadingBackups: Boolean = false,
    val error: String? = null,
)

class OmniFlowViewModel(
    private val sharedApp: SharedApp,
) : ViewModel() {
    private val _homeUiState = MutableStateFlow(HomeUiState())
    val homeUiState: StateFlow<HomeUiState> = _homeUiState.asStateFlow()
    private val _analyticsUiState = MutableStateFlow(AnalyticsUiState())
    val analyticsUiState: StateFlow<AnalyticsUiState> = _analyticsUiState.asStateFlow()
    private val _searchUiState = MutableStateFlow(SearchUiState())
    val searchUiState: StateFlow<SearchUiState> = _searchUiState.asStateFlow()
    private val _transactionRecordDetailUiState = MutableStateFlow(TransactionRecordDetailUiState())
    val transactionRecordDetailUiState: StateFlow<TransactionRecordDetailUiState> =
        _transactionRecordDetailUiState.asStateFlow()
    private val _transactionUiState = MutableStateFlow(TransactionEditorUiState())
    val transactionUiState: StateFlow<TransactionEditorUiState> = _transactionUiState.asStateFlow()
    private val _moreUiState = MutableStateFlow(MoreUiState())
    val moreUiState: StateFlow<MoreUiState> = _moreUiState.asStateFlow()

    private var homeQuery = HomeQuery(LedgerScope.All, monthRange(Clock.System.now()))
    private var analyticsAnchor = Clock.System.now().toLocalDateTime(ChinaTimeZone).date
    private var preferences = AppPreferences()
    private var defaultLedgerId: String? = null
    private var editingTransaction: Transaction? = null
    private var selectedDate: LocalDate? = null

    private var homeJob: Job? = null
    private var detailJob: Job? = null
    private var analyticsJob: Job? = null
    private var searchJob: Job? = null
    private var editorCategoriesJob: Job? = null
    private var editorTagsJob: Job? = null
    private var searchCategoriesJob: Job? = null
    private var searchTagsJob: Job? = null
    private var moreCategoriesJob: Job? = null
    private var moreTagsJob: Job? = null
    private var moreRulesJob: Job? = null
    private var importJob: Job? = null

    init {
        viewModelScope.launch { sharedApp.initialize() }
        collectManagement()
        collectPreferences()
        collectSync()
        observeHome()
        observeAnalytics()
    }

    private fun collectManagement() {
        viewModelScope.launch {
            sharedApp.management.observeLedgers().collect { result ->
                val ledgers = result.getOrDefault(emptyList())
                _homeUiState.value = _homeUiState.value.copy(ledgers = ledgers)
                _analyticsUiState.value = _analyticsUiState.value.copy(ledgers = ledgers)
                _searchUiState.value = _searchUiState.value.copy(ledgers = ledgers)
                if (_searchUiState.value.query.scope == LedgerScope.All) observeSearchFilters(LedgerScope.All)
                _transactionUiState.value = _transactionUiState.value.copy(ledgers = ledgers)
                val previousSelection = _moreUiState.value.selectedLedgerId
                val selected = previousSelection?.takeIf { id -> ledgers.any { it.id == id } }
                    ?: ledgers.firstOrNull()?.id
                _moreUiState.value = _moreUiState.value.copy(ledgers = ledgers, selectedLedgerId = selected)
                if (selected != null && selected != previousSelection) observeMoreLedger(selected)
            }
        }
        viewModelScope.launch {
            sharedApp.management.observeDefaultLedgerId().collect { result ->
                defaultLedgerId = result.getOrNull()
                _moreUiState.value = _moreUiState.value.copy(defaultLedgerId = defaultLedgerId)
            }
        }
        viewModelScope.launch {
            sharedApp.management.observeAccounts().collect { result ->
                val accounts = result.getOrDefault(emptyList())
                _searchUiState.value = _searchUiState.value.copy(accounts = accounts)
                _transactionUiState.value = _transactionUiState.value.copy(accounts = accounts)
                _moreUiState.value = _moreUiState.value.copy(accounts = accounts)
            }
        }
        viewModelScope.launch {
            sharedApp.management.observeAccountSummary().collect { result ->
                result.onSuccess { summary ->
                    _moreUiState.value = _moreUiState.value.copy(accountSummary = summary)
                }
            }
        }
        viewModelScope.launch {
            sharedApp.reminders.observe().collect { result ->
                _moreUiState.value = _moreUiState.value.copy(reminders = result.getOrDefault(emptyList()))
            }
        }
    }

    private fun collectPreferences() {
        viewModelScope.launch {
            sharedApp.preferences.observe().collect { result ->
                result.onSuccess { saved ->
                    val homeScopeChanged = homeQuery.scope != saved.homeLedgerScope
                    val analyticsScopeChanged = _analyticsUiState.value.scope != saved.analyticsLedgerScope
                    preferences = saved
                    homeQuery = homeQuery.copy(scope = saved.homeLedgerScope)
                    _homeUiState.value = _homeUiState.value.copy(
                        displayMode = saved.transactionDetailDisplayMode,
                        appearanceMode = saved.appearanceMode,
                    )
                    _analyticsUiState.value = _analyticsUiState.value.copy(scope = saved.analyticsLedgerScope)
                    _moreUiState.value = _moreUiState.value.copy(preferences = saved)
                    if (homeScopeChanged) observeHome()
                    if (analyticsScopeChanged) observeAnalytics()
                }
            }
        }
    }

    private fun collectSync() {
        viewModelScope.launch {
            sharedApp.sync.observeSyncState().collect { state ->
                _moreUiState.value = _moreUiState.value.copy(syncState = state)
            }
        }
    }

    fun previousMonth() = updateHomeMonth(-1)
    fun nextMonth() = updateHomeMonth(1)
    fun selectHomeMonth(date: LocalDate) {
        homeQuery = homeQuery.copy(month = monthRange(date))
        observeHome()
    }
    fun setCalendarFilter(filter: CalendarTransactionFilter) {
        homeQuery = homeQuery.copy(calendarFilter = filter)
        _homeUiState.value = _homeUiState.value.copy(calendarFilter = filter)
        observeHome()
    }
    fun toggleLedgerMenu() {
        _homeUiState.value = _homeUiState.value.copy(isLedgerMenuExpanded = !_homeUiState.value.isLedgerMenuExpanded)
    }
    fun selectLedger(scope: LedgerScope) {
        homeQuery = homeQuery.copy(scope = scope)
        _homeUiState.value = _homeUiState.value.copy(isLedgerMenuExpanded = false)
        observeHome()
        savePreferences(preferences.copy(homeLedgerScope = scope))
    }
    fun showDate(date: LocalDate) {
        selectedDate = date
        showDate(date, homeQuery.scope)
    }
    fun selectDateScope(scope: LedgerScope) {
        selectedDate?.let { showDate(it, scope) }
    }
    fun dismissDate() {
        detailJob?.cancel()
        selectedDate = null
        _homeUiState.value = _homeUiState.value.copy(
            selectedDate = null,
            selectedDateScope = null,
            isDateLoading = false,
            dateError = null,
        )
    }
    fun toggleDisplayMode() {
        val mode = when (_homeUiState.value.displayMode) {
            TransactionDetailDisplayMode.LIST -> TransactionDetailDisplayMode.CARD
            TransactionDetailDisplayMode.CARD -> TransactionDetailDisplayMode.LIST
        }
        _homeUiState.value = _homeUiState.value.copy(displayMode = mode)
        savePreferences(preferences.copy(transactionDetailDisplayMode = mode))
    }

    private fun showDate(date: LocalDate, scope: LedgerScope) {
        detailJob?.cancel()
        _homeUiState.value = _homeUiState.value.copy(
            selectedDate = null,
            selectedDateScope = scope,
            isDateLoading = true,
            dateError = null,
        )
        detailJob = viewModelScope.launch {
            sharedApp.home.observeTransactionDetails(TransactionDetailQuery(scope, dateRange(date))).collect { result ->
                _homeUiState.value = _homeUiState.value.copy(
                    selectedDate = result.getOrNull(),
                    selectedDateScope = scope,
                    isDateLoading = false,
                    dateError = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    private fun observeHome() {
        homeJob?.cancel()
        _homeUiState.value = _homeUiState.value.copy(isLoading = true, error = null)
        homeJob = viewModelScope.launch {
            sharedApp.home.observeHome(homeQuery).collect { result ->
                _homeUiState.value = _homeUiState.value.copy(
                    home = result.getOrNull(),
                    isLoading = false,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    private fun updateHomeMonth(delta: Long) {
        val start = homeQuery.month.startInclusive.toLocalDateTime(ChinaTimeZone).date
        val month = JavaLocalDate.of(start.year, start.monthNumber, 1).plusMonths(delta)
        homeQuery = homeQuery.copy(month = monthRange(LocalDate(month.year, month.monthValue, 1)))
        observeHome()
    }

    fun setAnalyticsScope(scope: LedgerScope) {
        _analyticsUiState.value = _analyticsUiState.value.copy(scope = scope)
        savePreferences(preferences.copy(analyticsLedgerScope = scope))
        observeAnalytics()
    }
    fun setAnalyticsRangeMode(mode: AnalyticsRangeMode) {
        _analyticsUiState.value = _analyticsUiState.value.copy(rangeMode = mode, range = analyticsRange(mode, analyticsAnchor))
        observeAnalytics()
    }
    fun shiftAnalyticsRange(delta: Long) {
        analyticsAnchor = when (_analyticsUiState.value.rangeMode) {
            AnalyticsRangeMode.WEEK -> analyticsAnchor.minus(-7 * delta.toInt(), DateTimeUnit.DAY)
            AnalyticsRangeMode.MONTH -> javaDate(analyticsAnchor).plusMonths(delta).toKotlinDate()
            AnalyticsRangeMode.YEAR -> javaDate(analyticsAnchor).plusYears(delta).toKotlinDate()
            AnalyticsRangeMode.CUSTOM -> analyticsAnchor
        }
        _analyticsUiState.value = _analyticsUiState.value.copy(
            range = analyticsRange(_analyticsUiState.value.rangeMode, analyticsAnchor),
        )
        observeAnalytics()
    }
    fun resetAnalyticsRange() {
        val mode = _analyticsUiState.value.rangeMode
        if (mode == AnalyticsRangeMode.CUSTOM) return
        analyticsAnchor = Clock.System.now().toLocalDateTime(ChinaTimeZone).date
        _analyticsUiState.value = _analyticsUiState.value.copy(range = analyticsRange(mode, analyticsAnchor))
        observeAnalytics()
    }
    fun setAnalyticsCustomRange(range: DateRange) {
        _analyticsUiState.value = _analyticsUiState.value.copy(rangeMode = AnalyticsRangeMode.CUSTOM, range = range)
        observeAnalytics()
    }
    fun setRankingType(type: TransactionType) {
        _analyticsUiState.value = _analyticsUiState.value.copy(rankingType = type)
        observeAnalytics()
    }
    fun setCategoryAnalysis(type: TransactionType, granularity: CategoryShareGranularity) {
        _analyticsUiState.value = _analyticsUiState.value.copy(
            categoryType = type,
            categoryGranularity = granularity,
            primaryCategoryId = null,
        )
        observeAnalytics()
    }
    fun setCategoryDrillDown(primaryCategoryId: String?) {
        _analyticsUiState.value = _analyticsUiState.value.copy(
            categoryGranularity = if (primaryCategoryId == null) CategoryShareGranularity.PRIMARY else CategoryShareGranularity.SECONDARY,
            primaryCategoryId = primaryCategoryId,
        )
        observeAnalytics()
    }
    fun loadStatementTable(year: Int) {
        viewModelScope.launch {
            sharedApp.analytics.statementTable(_analyticsUiState.value.scope, year).onSuccess { table ->
                _analyticsUiState.value = _analyticsUiState.value.copy(statementTable = table)
            }.onFailure { error ->
                _analyticsUiState.value = _analyticsUiState.value.copy(error = error.message)
            }
        }
    }
    fun dismissStatementTable() {
        _analyticsUiState.value = _analyticsUiState.value.copy(statementTable = null)
    }

    private fun observeAnalytics() {
        analyticsJob?.cancel()
        val current = _analyticsUiState.value
        _analyticsUiState.value = current.copy(isLoading = true, error = null)
        analyticsJob = viewModelScope.launch {
            sharedApp.analytics.observeDashboard(
                AnalyticsQuery(
                    scope = current.scope,
                    range = current.range,
                    rankingType = current.rankingType,
                    categoryShareType = current.categoryType,
                    categoryShareGranularity = current.categoryGranularity,
                    primaryCategoryId = current.primaryCategoryId,
                ),
            ).collect { result ->
                _analyticsUiState.value = _analyticsUiState.value.copy(
                    dashboard = result.getOrNull(),
                    isLoading = false,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun setSearchKeyword(keyword: String) = updateSearch(_searchUiState.value.query.copy(keyword = keyword))
    fun setSearchScope(scope: LedgerScope) {
        updateSearch(_searchUiState.value.query.copy(
            scope = scope,
            primaryCategoryId = null,
            secondaryCategoryId = null,
            tagId = null,
        ))
        observeSearchFilters(scope)
    }
    fun setSearchType(type: TransactionType?) = updateSearch(_searchUiState.value.query.copy(type = type))
    fun setSearchPrimaryCategory(categoryId: String?) = updateSearch(
        _searchUiState.value.query.copy(primaryCategoryId = categoryId, secondaryCategoryId = null),
    )
    fun setSearchSecondaryCategory(categoryId: String?) = updateSearch(
        _searchUiState.value.query.copy(secondaryCategoryId = categoryId),
    )
    fun setSearchTag(tagId: String?) = updateSearch(_searchUiState.value.query.copy(tagId = tagId))
    fun setSearchAccount(accountId: String?) = updateSearch(_searchUiState.value.query.copy(accountId = accountId))
    fun clearSearch() {
        searchCategoriesJob?.cancel()
        searchTagsJob?.cancel()
        _searchUiState.value = _searchUiState.value.copy(
            query = TransactionSearchQuery(),
            result = null,
            categories = emptyList(),
            tags = emptyList(),
            error = null,
        )
        observeSearchFilters(LedgerScope.All)
    }

    private fun updateSearch(query: TransactionSearchQuery) {
        _searchUiState.value = _searchUiState.value.copy(query = query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _searchUiState.value = _searchUiState.value.copy(isLoading = true, error = null)
            sharedApp.search(query).onSuccess { result ->
                _searchUiState.value = _searchUiState.value.copy(result = result, isLoading = false)
            }.onFailure { error ->
                _searchUiState.value = _searchUiState.value.copy(isLoading = false, error = error.message)
            }
        }
    }

    private fun observeSearchFilters(scope: LedgerScope) {
        searchCategoriesJob?.cancel()
        searchTagsJob?.cancel()
        val ledgerIds = when (scope) {
            LedgerScope.All -> _searchUiState.value.ledgers.map { it.id }
            is LedgerScope.Single -> listOf(scope.ledgerId)
        }
        if (ledgerIds.isEmpty()) {
            _searchUiState.value = _searchUiState.value.copy(categories = emptyList(), tags = emptyList())
            return
        }
        searchCategoriesJob = viewModelScope.launch {
            val categoriesByLedger = mutableMapOf<String, List<Category>>()
            ledgerIds.forEach { ledgerId ->
                launch {
                    sharedApp.management.observeCategories(ledgerId).collect { result ->
                        categoriesByLedger[ledgerId] = result.getOrDefault(emptyList())
                        _searchUiState.value = _searchUiState.value.copy(
                            categories = ledgerIds.flatMap { categoriesByLedger[it].orEmpty() },
                        )
                    }
                }
            }
        }
        searchTagsJob = viewModelScope.launch {
            val tagsByLedger = mutableMapOf<String, List<Tag>>()
            ledgerIds.forEach { ledgerId ->
                launch {
                    sharedApp.management.observeTags(ledgerId).collect { result ->
                        tagsByLedger[ledgerId] = result.getOrDefault(emptyList())
                        _searchUiState.value = _searchUiState.value.copy(
                            tags = ledgerIds.flatMap { tagsByLedger[it].orEmpty() },
                        )
                    }
                }
            }
        }
    }

    fun showTransactionRecordDetail(transactionId: String) {
        _transactionRecordDetailUiState.value = TransactionRecordDetailUiState(isLoading = true)
        viewModelScope.launch {
            val result = sharedApp.getTransaction(transactionId)
            val transaction = result.getOrNull()
            if (transaction == null) {
                _transactionRecordDetailUiState.value = TransactionRecordDetailUiState(
                    error = result.exceptionOrNull()?.message ?: "交易不存在或已删除",
                )
                return@launch
            }
            val categories = sharedApp.management.observeCategories(transaction.ledgerId).first().getOrDefault(emptyList())
            val tags = sharedApp.management.observeTags(transaction.ledgerId).first().getOrDefault(emptyList())
            val selectedCategory = categories.firstOrNull { it.id == transaction.categoryId }
            val primaryCategory = selectedCategory?.parentId?.let { parentId -> categories.firstOrNull { it.id == parentId } }
                ?: selectedCategory
            _transactionRecordDetailUiState.value = TransactionRecordDetailUiState(
                transaction = transaction,
                ledgerName = _searchUiState.value.ledgers.firstOrNull { it.id == transaction.ledgerId }?.name ?: "未知账本",
                accountName = _searchUiState.value.accounts.firstOrNull { it.id == transaction.accountId }?.name ?: "未知账户",
                primaryCategoryName = primaryCategory?.name ?: "未分类",
                secondaryCategoryName = selectedCategory?.takeIf { it.parentId != null }?.name,
                categoryIconKey = primaryCategory?.iconKey ?: selectedCategory?.iconKey,
                tagNames = tags.filter { it.id in transaction.tagIds }.map { it.name },
            )
        }
    }

    fun dismissTransactionRecordDetail() {
        _transactionRecordDetailUiState.value = TransactionRecordDetailUiState()
    }

    fun deleteTransactionRecordDetail() {
        val transaction = _transactionRecordDetailUiState.value.transaction ?: return
        _transactionRecordDetailUiState.value = _transactionRecordDetailUiState.value.copy(isDeleting = true, error = null)
        viewModelScope.launch {
            sharedApp.deleteTransaction(transaction.id).onSuccess {
                dismissTransactionRecordDetail()
            }.onFailure { error ->
                _transactionRecordDetailUiState.value = _transactionRecordDetailUiState.value.copy(
                    isDeleting = false,
                    error = error.message,
                )
            }
        }
    }

    fun startNewTransaction(date: LocalDate? = null, ledgerId: String? = null) {
        editingTransaction = null
        val accounts = _transactionUiState.value.accounts
        val selectedLedger = ledgerId ?: defaultLedgerId
        val now = Clock.System.now().toLocalDateTime(ChinaTimeZone)
        _transactionUiState.value = TransactionEditorUiState(
            ledgers = _transactionUiState.value.ledgers,
            accounts = accounts,
            ledgerId = selectedLedger,
            accountId = accounts.firstOrNull { it.name == "现金" }?.id ?: accounts.firstOrNull()?.id,
            occurredAt = LocalDateTime(
                date ?: now.date,
                LocalTime(now.hour, now.minute),
            ).toInstant(ChinaTimeZone),
        )
        observeEditorLedger(selectedLedger)
    }
    fun editTransaction(transactionId: String) {
        viewModelScope.launch {
            sharedApp.getTransaction(transactionId).onSuccess { transaction ->
                if (transaction == null) {
                    _transactionUiState.value = _transactionUiState.value.copy(error = "交易不存在或已删除")
                    return@onSuccess
                }
                editingTransaction = transaction
                _transactionUiState.value = TransactionEditorUiState(
                    editingId = transaction.id,
                    ledgers = _transactionUiState.value.ledgers,
                    accounts = _transactionUiState.value.accounts,
                    ledgerId = transaction.ledgerId,
                    accountId = transaction.accountId,
                    categoryId = transaction.categoryId,
                    selectedTagIds = transaction.tagIds,
                    type = transaction.type,
                    occurredAt = transaction.occurredAt,
                    amountInput = transaction.amount.toInputString(),
                    note = transaction.note.orEmpty(),
                    isExcluded = transaction.isExcluded,
                )
                observeEditorLedger(transaction.ledgerId)
            }.onFailure { error ->
                _transactionUiState.value = _transactionUiState.value.copy(error = error.message)
            }
        }
    }
    fun setTransactionType(type: TransactionType) {
        val category = _transactionUiState.value.categories.firstOrNull { it.id == _transactionUiState.value.categoryId }
        _transactionUiState.value = _transactionUiState.value.copy(
            type = type,
            categoryId = category?.takeIf { it.type == type }?.id,
        )
    }
    fun setTransactionLedger(ledgerId: String?) {
        _transactionUiState.value = _transactionUiState.value.copy(ledgerId = ledgerId, categoryId = null, selectedTagIds = emptySet())
        observeEditorLedger(ledgerId)
    }
    fun setTransactionAccount(accountId: String?) {
        _transactionUiState.value = _transactionUiState.value.copy(accountId = accountId)
    }
    fun setTransactionCategory(categoryId: String?) {
        _transactionUiState.value = _transactionUiState.value.copy(categoryId = categoryId)
    }
    fun reorderTransactionPrimaryCategories(categoryIds: List<String>) {
        val state = _transactionUiState.value
        val ledgerId = state.ledgerId ?: return
        viewModelScope.launch {
            sharedApp.reorderPrimaryCategories(ledgerId, state.type, categoryIds)
                .onFailure { error -> _transactionUiState.value = state.copy(error = error.message) }
        }
    }
    fun createTransactionSecondaryCategory(name: String) {
        val state = _transactionUiState.value
        val ledgerId = state.ledgerId ?: return
        val selected = state.categories.firstOrNull { it.id == state.categoryId } ?: return
        val parentId = selected.parentId ?: selected.id
        val id = UUID.randomUUID().toString()
        viewModelScope.launch {
            sharedApp.createCategory(Category(id, ledgerId, parentId, name, null, state.type))
                .onSuccess { _transactionUiState.value = _transactionUiState.value.copy(categoryId = id) }
                .onFailure { error -> _transactionUiState.value = state.copy(error = error.message) }
        }
    }
    fun toggleTransactionTag(tagId: String) {
        val tags = _transactionUiState.value.selectedTagIds.toMutableSet()
        if (!tags.add(tagId)) tags.remove(tagId)
        _transactionUiState.value = _transactionUiState.value.copy(selectedTagIds = tags)
    }
    fun setTransactionNote(note: String) {
        _transactionUiState.value = _transactionUiState.value.copy(note = note)
    }
    fun setTransactionDate(instant: Instant) {
        _transactionUiState.value = _transactionUiState.value.copy(occurredAt = instant)
    }
    fun setTransactionExcluded(excluded: Boolean) {
        _transactionUiState.value = _transactionUiState.value.copy(isExcluded = excluded)
    }
    fun pressAmountKey(key: String) {
        val current = _transactionUiState.value.amountInput
        val updated = when (key) {
            "退格" -> current.dropLast(1)
            "+", "-" -> if (current.isNotEmpty() && current.last() !in "+-") current + key else current
            "." -> if (current.substringAfterLast('+').substringAfterLast('-').contains('.')) current else current + key
            else -> current + key
        }
        _transactionUiState.value = _transactionUiState.value.copy(amountInput = updated, error = null)
    }
    fun saveTransaction(saveAgain: Boolean) {
        val state = _transactionUiState.value
        val amount = evaluateAmount(state.amountInput)?.let(::Money)
        if (amount == null) {
            _transactionUiState.value = state.copy(error = "请输入有效金额")
            return
        }
        _transactionUiState.value = state.copy(isSaving = true, error = null)
        viewModelScope.launch {
            val existing = editingTransaction
            val result = if (existing == null) {
                sharedApp.createTransaction(
                    CreateTransactionCommand(
                        id = UUID.randomUUID().toString(),
                        ledgerId = state.ledgerId,
                        accountId = state.accountId,
                        categoryId = state.categoryId,
                        amount = amount,
                        type = state.type,
                        occurredAt = state.occurredAt,
                        note = state.note,
                        isExcluded = state.isExcluded,
                        tagIds = state.selectedTagIds,
                    ),
                )
            } else {
                sharedApp.updateTransaction(
                    existing.copy(
                        ledgerId = state.ledgerId ?: existing.ledgerId,
                        accountId = state.accountId ?: existing.accountId,
                        categoryId = state.categoryId ?: existing.categoryId,
                        amount = amount,
                        type = state.type,
                        occurredAt = state.occurredAt,
                        note = state.note,
                        isExcluded = state.isExcluded,
                        tagIds = state.selectedTagIds,
                    ),
                )
            }
            result.onSuccess {
                if (saveAgain) {
                    editingTransaction = null
                    _transactionUiState.value = state.copy(
                        editingId = null,
                        amountInput = "",
                        categoryId = null,
                        selectedTagIds = emptySet(),
                        note = "",
                        isExcluded = false,
                        isSaving = false,
                    )
                } else {
                    _transactionUiState.value = state.copy(isSaving = false, completed = true)
                }
            }.onFailure { error ->
                _transactionUiState.value = state.copy(isSaving = false, error = error.message)
            }
        }
    }
    fun consumeTransactionCompletion() {
        _transactionUiState.value = _transactionUiState.value.copy(completed = false)
    }

    private fun observeEditorLedger(ledgerId: String?) {
        editorCategoriesJob?.cancel()
        editorTagsJob?.cancel()
        if (ledgerId == null) {
            _transactionUiState.value = _transactionUiState.value.copy(categories = emptyList(), tags = emptyList())
            return
        }
        editorCategoriesJob = viewModelScope.launch {
            sharedApp.management.observeCategories(ledgerId).collect { result ->
                _transactionUiState.value = _transactionUiState.value.copy(categories = result.getOrDefault(emptyList()))
            }
        }
        editorTagsJob = viewModelScope.launch {
            sharedApp.management.observeTags(ledgerId).collect { result ->
                _transactionUiState.value = _transactionUiState.value.copy(tags = result.getOrDefault(emptyList()))
            }
        }
    }

    fun setAppearanceMode(mode: AppearanceMode) = savePreferences(preferences.copy(appearanceMode = mode))
    fun setThemeColor(color: ThemeColor) = savePreferences(preferences.copy(themeColor = color))
    fun setAppLockEnabled(enabled: Boolean) = savePreferences(preferences.copy(appLockEnabled = enabled))
    fun configureSync(target: SyncTarget, retention: Int) {
        viewModelScope.launch {
            sharedApp.sync.configure(SyncConfig(target, retention)).onFailure { error ->
                _moreUiState.value = _moreUiState.value.copy(error = error.message)
            }
        }
    }
    fun syncNow() {
        viewModelScope.launch {
            sharedApp.sync.syncNow().onFailure { error ->
                _moreUiState.value = _moreUiState.value.copy(error = error.message)
            }
        }
    }
    fun loadBackups() {
        _moreUiState.value = _moreUiState.value.copy(isLoadingBackups = true, error = null)
        viewModelScope.launch {
            sharedApp.sync.listBackups().onSuccess { backups ->
                _moreUiState.value = _moreUiState.value.copy(backups = backups, isLoadingBackups = false)
            }.onFailure { error ->
                _moreUiState.value = _moreUiState.value.copy(isLoadingBackups = false, error = error.message)
            }
        }
    }
    fun restoreBackup(meta: RemoteBackupMeta) {
        viewModelScope.launch {
            sharedApp.sync.restore(meta).onFailure { error ->
                _moreUiState.value = _moreUiState.value.copy(error = error.message)
            }
        }
    }

    fun selectMoreLedger(ledgerId: String) {
        _moreUiState.value = _moreUiState.value.copy(selectedLedgerId = ledgerId)
        observeMoreLedger(ledgerId)
    }

    private fun observeMoreLedger(ledgerId: String) {
        moreCategoriesJob?.cancel()
        moreTagsJob?.cancel()
        moreRulesJob?.cancel()
        moreCategoriesJob = viewModelScope.launch {
            sharedApp.management.observeCategories(ledgerId).collect { result ->
                _moreUiState.value = _moreUiState.value.copy(categories = result.getOrDefault(emptyList()))
            }
        }
        moreTagsJob = viewModelScope.launch {
            sharedApp.management.observeTags(ledgerId).collect { result ->
                _moreUiState.value = _moreUiState.value.copy(tags = result.getOrDefault(emptyList()))
            }
        }
        moreRulesJob = viewModelScope.launch {
            sharedApp.management.observeRules(ledgerId).collect { result ->
                _moreUiState.value = _moreUiState.value.copy(rules = result.getOrDefault(emptyList()))
            }
        }
    }

    fun saveLedger(id: String?, name: String, coverKey: String?) = mutate {
        val ledger = Ledger(id ?: UUID.randomUUID().toString(), name, coverKey)
        if (id == null) sharedApp.createLedger(ledger) else sharedApp.updateLedger(ledger)
    }

    fun deleteLedger(id: String) = mutate { sharedApp.deleteLedger(id) }

    fun setDefaultLedger(id: String?) = mutate { sharedApp.setDefaultLedger(id) }

    fun saveAccount(
        id: String?,
        name: String,
        type: AccountType,
        iconKey: String,
        cardNumber: String?,
        note: String?,
        balance: Money,
        includeInTotalAssets: Boolean,
    ) = mutate {
        val account = Account(
            id = id ?: UUID.randomUUID().toString(),
            name = name,
            type = type,
            iconKey = iconKey,
            cardNumber = cardNumber,
            note = note,
            balance = balance,
            includeInTotalAssets = includeInTotalAssets,
        )
        if (id == null) sharedApp.createAccount(account) else sharedApp.updateAccount(account)
    }

    fun calibrateAccount(id: String, balance: Money) = mutate { sharedApp.calibrateAccount(id, balance) }

    fun deleteAccount(id: String) = mutate { sharedApp.deleteAccount(id) }

    fun saveCategory(
        id: String?,
        ledgerId: String,
        parentId: String?,
        name: String,
        iconKey: String?,
        type: TransactionType,
    ) = mutate {
        val category = Category(id ?: UUID.randomUUID().toString(), ledgerId, parentId, name, iconKey, type)
        if (id == null) sharedApp.createCategory(category) else sharedApp.updateCategory(category)
    }

    fun deleteCategory(id: String) = mutate { sharedApp.deleteCategory(id) }

    fun saveTag(id: String?, ledgerId: String, name: String) = mutate {
        val tag = Tag(id ?: UUID.randomUUID().toString(), ledgerId, name)
        if (id == null) sharedApp.createTag(tag) else sharedApp.updateTag(tag)
    }

    fun deleteTag(id: String) = mutate { sharedApp.deleteTag(id) }

    fun saveRule(
        id: String?,
        ledgerId: String,
        name: String,
        conditionType: RuleConditionType,
        conditionValue: String,
        actionType: RuleActionType,
        actionValue: String,
        priority: Int,
    ) = mutate {
        val rule = Rule(
            id = id ?: UUID.randomUUID().toString(),
            ledgerId = ledgerId,
            name = name,
            conditionType = conditionType,
            conditionValue = conditionValue,
            actionType = actionType,
            actionValue = actionValue,
            priority = priority,
        )
        if (id == null) sharedApp.createRule(rule) else sharedApp.updateRule(rule)
    }

    fun deleteRule(id: String) = mutate { sharedApp.deleteRule(id) }

    fun moveRule(id: String, offset: Int) = mutate {
        val ordered = _moreUiState.value.rules.sortedBy(Rule::priority).map(Rule::id).toMutableList()
        val from = ordered.indexOf(id)
        val to = from + offset
        if (from !in ordered.indices || to !in ordered.indices) Result.success(Unit) else {
            ordered.add(to, ordered.removeAt(from))
            sharedApp.reorderRules(_moreUiState.value.selectedLedgerId ?: return@mutate Result.failure(IllegalStateException("请先选择账本")), ordered)
        }
    }

    fun saveReminder(
        id: String?,
        type: ReminderType,
        name: String,
        amount: Money?,
        schedule: ReminderSchedule,
        paused: Boolean,
    ) = mutate {
        val reminder = Reminder(id ?: UUID.randomUUID().toString(), type, name, amount, schedule, paused)
        if (id == null) sharedApp.createReminder(reminder) else sharedApp.updateReminder(reminder)
    }

    fun setReminderPaused(reminder: Reminder, paused: Boolean) = mutate {
        sharedApp.setReminderPaused(reminder, paused)
    }

    fun deleteReminder(id: String) = mutate { sharedApp.deleteReminder(id) }

    fun importFile(ledgerId: String, fileName: String, bytes: ByteArray, selectedFormat: ImportFormat? = null) {
        importJob?.cancel()
        _moreUiState.value = _moreUiState.value.copy(
            importPreview = null,
            importFileName = fileName,
            importFormat = selectedFormat,
            importMessage = null,
            isImporting = true,
            error = null,
        )
        importJob = viewModelScope.launch {
            sharedApp.imports.preview(ImportRequest(ledgerId, fileName, bytes, selectedFormat)).collect { result ->
                result.onSuccess { preview ->
                    _moreUiState.value = _moreUiState.value.copy(
                        importPreview = preview,
                        importFormat = preview.format,
                        selectedImportItemIds = preview.items.mapTo(mutableSetOf()) { it.id },
                        isImporting = preview.phase != com.omniflow.shared.domain.model.ImportPreviewPhase.READY,
                    )
                }.onFailure { error ->
                    _moreUiState.value = _moreUiState.value.copy(isImporting = false, error = error.message)
                }
            }
        }
    }

    fun toggleImportItem(id: String) {
        val selected = _moreUiState.value.selectedImportItemIds.toMutableSet()
        if (!selected.add(id)) selected.remove(id)
        _moreUiState.value = _moreUiState.value.copy(selectedImportItemIds = selected)
    }

    fun selectAllImportItems(selected: Boolean) {
        val ids = if (selected) _moreUiState.value.importPreview?.items.orEmpty().mapTo(mutableSetOf()) { it.id } else emptySet()
        _moreUiState.value = _moreUiState.value.copy(selectedImportItemIds = ids)
    }

    fun invertImportSelection() {
        val all = _moreUiState.value.importPreview?.items.orEmpty().mapTo(mutableSetOf()) { it.id }
        _moreUiState.value = _moreUiState.value.copy(selectedImportItemIds = all - _moreUiState.value.selectedImportItemIds)
    }

    fun editImportItem(
        itemId: String,
        type: TransactionType?,
        categoryId: String?,
        accountId: String?,
        isSkipped: Boolean,
        note: String? = null,
        tags: List<String>? = null,
        isExcluded: Boolean? = null,
    ) {
        val preview = _moreUiState.value.importPreview ?: return
        val item = preview.items.firstOrNull { it.id == itemId } ?: return
        viewModelScope.launch {
            sharedApp.imports.editItem(
                ImportPreviewEdit(
                    sessionId = preview.sessionId,
                    itemId = item.id,
                    type = type,
                    categoryId = categoryId,
                    accountId = accountId,
                    note = note ?: item.note,
                    tags = tags ?: item.tags,
                    isExcluded = isExcluded ?: item.isExcluded,
                    isSkipped = isSkipped,
                ),
            ).onSuccess { state -> _moreUiState.value = _moreUiState.value.copy(importPreview = state) }
                .onFailure { error -> _moreUiState.value = _moreUiState.value.copy(error = error.message) }
        }
    }

    fun setSelectedImportCategory(categoryId: String?) {
        val state = _moreUiState.value
        val preview = state.importPreview ?: return
        viewModelScope.launch {
            sharedApp.imports.editCategories(
                preview.sessionId,
                ImportCategoryBatchEdit(state.selectedImportItemIds, categoryId),
            ).onSuccess { updated -> _moreUiState.value = _moreUiState.value.copy(importPreview = updated) }
                .onFailure { error -> _moreUiState.value = _moreUiState.value.copy(error = error.message) }
        }
    }

    fun setSelectedImportSkipped(skipped: Boolean) {
        val state = _moreUiState.value
        val preview = state.importPreview ?: return
        viewModelScope.launch {
            sharedApp.imports.editSkipped(
                preview.sessionId,
                ImportExcludeBatchEdit(state.selectedImportItemIds, skipped),
            ).onSuccess { updated -> _moreUiState.value = _moreUiState.value.copy(importPreview = updated) }
                .onFailure { error -> _moreUiState.value = _moreUiState.value.copy(error = error.message) }
        }
    }

    fun commitImport() {
        val preview = _moreUiState.value.importPreview ?: return
        _moreUiState.value = _moreUiState.value.copy(isImporting = true, error = null)
        viewModelScope.launch {
            sharedApp.imports.commit(preview.sessionId).onSuccess { result ->
                _moreUiState.value = _moreUiState.value.copy(
                    importPreview = null,
                    selectedImportItemIds = emptySet(),
                    isImporting = false,
                    importMessage = "已导入 ${result.importedCount} 条，跳过 ${result.excludedCount} 条",
                )
            }.onFailure { error ->
                _moreUiState.value = _moreUiState.value.copy(isImporting = false, error = error.message)
            }
        }
    }

    fun prepareQingziExport(dateRange: DateRange? = null) {
        _moreUiState.value = _moreUiState.value.copy(isExporting = true, error = null)
        viewModelScope.launch {
            sharedApp.qingzi.export(QingziExportRequest(dateRange = dateRange)).onSuccess { result ->
                _moreUiState.value = _moreUiState.value.copy(
                    exportPayload = result.payload,
                    exportWarnings = result.warnings,
                    isExporting = false,
                )
            }.onFailure { error ->
                _moreUiState.value = _moreUiState.value.copy(isExporting = false, error = error.message)
            }
        }
    }

    fun consumeExportPayload() {
        _moreUiState.value = _moreUiState.value.copy(exportPayload = null)
    }

    fun clearMoreMessage() {
        _moreUiState.value = _moreUiState.value.copy(error = null, importMessage = null)
    }

    private fun mutate(block: suspend () -> Result<Unit>) {
        _moreUiState.value = _moreUiState.value.copy(error = null)
        viewModelScope.launch {
            block().onFailure { error -> _moreUiState.value = _moreUiState.value.copy(error = error.message) }
        }
    }

    private fun savePreferences(updated: AppPreferences) {
        preferences = updated
        _moreUiState.value = _moreUiState.value.copy(preferences = updated)
        viewModelScope.launch { sharedApp.preferences.save(updated) }
    }
}

class OmniFlowViewModelFactory(
    private val sharedApp: SharedApp,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = OmniFlowViewModel(sharedApp) as T
}

internal val ChinaTimeZone = TimeZone.currentSystemDefault()

internal fun monthRange(now: Instant): DateRange = monthRange(now.toLocalDateTime(ChinaTimeZone).date)

internal fun monthRange(date: LocalDate): DateRange {
    val next = javaDate(LocalDate(date.year, date.monthNumber, 1)).plusMonths(1)
    return DateRange(
        LocalDate(date.year, date.monthNumber, 1).atStartOfDayIn(ChinaTimeZone),
        next.toKotlinDate().atStartOfDayIn(ChinaTimeZone),
    )
}

internal fun dateRange(date: LocalDate): DateRange {
    val next = javaDate(date).plusDays(1).toKotlinDate()
    return DateRange(date.atStartOfDayIn(ChinaTimeZone), next.atStartOfDayIn(ChinaTimeZone))
}

private fun analyticsRange(mode: AnalyticsRangeMode, anchor: LocalDate): DateRange = when (mode) {
    AnalyticsRangeMode.WEEK -> {
        val start = javaDate(anchor).minusDays((javaDate(anchor).dayOfWeek.value - 1).toLong()).toKotlinDate()
        DateRange(start.atStartOfDayIn(ChinaTimeZone), javaDate(start).plusDays(7).toKotlinDate().atStartOfDayIn(ChinaTimeZone))
    }
    AnalyticsRangeMode.MONTH -> monthRange(anchor)
    AnalyticsRangeMode.YEAR -> DateRange(
        LocalDate(anchor.year, 1, 1).atStartOfDayIn(ChinaTimeZone),
        LocalDate(anchor.year + 1, 1, 1).atStartOfDayIn(ChinaTimeZone),
    )
    AnalyticsRangeMode.CUSTOM -> monthRange(anchor)
}

private fun javaDate(date: LocalDate) = JavaLocalDate.of(date.year, date.monthNumber, date.dayOfMonth)
private fun JavaLocalDate.toKotlinDate() = LocalDate(year, monthValue, dayOfMonth)

private fun Money.toInputString(): String = "${minor / 100}.${kotlin.math.abs(minor % 100).toString().padStart(2, '0')}"

private fun evaluateAmount(expression: String): Long? {
    if (expression.isBlank()) return null
    var total = 0L
    var operation = '+'
    val token = StringBuilder()
    for (character in expression + "+") {
        if (character == '+' || character == '-') {
            val value = decimalMinor(token.toString()) ?: return null
            total = if (operation == '+') total + value else total - value
            operation = character
            token.clear()
        } else {
            token.append(character)
        }
    }
    return total.takeIf { it > 0 }
}

private fun decimalMinor(value: String): Long? {
    if (value.isBlank()) return null
    val parts = value.split('.', limit = 2)
    val yuan = parts[0].toLongOrNull() ?: return null
    val fraction = parts.getOrElse(1) { "" }
    if (fraction.length > 2 || fraction.any { !it.isDigit() }) return null
    return yuan * 100 + fraction.padEnd(2, '0').toLongOrNull().orZero()
}

private fun Long?.orZero(): Long = this ?: 0L
