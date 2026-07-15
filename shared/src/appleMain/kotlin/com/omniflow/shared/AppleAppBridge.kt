package com.omniflow.shared

import com.omniflow.shared.domain.model.AnalyticsDashboardState
import com.omniflow.shared.domain.model.AnalyticsQuery
import com.omniflow.shared.domain.model.Account
import com.omniflow.shared.domain.model.AccountType
import com.omniflow.shared.domain.model.AmountFilter
import com.omniflow.shared.domain.model.AppPreferences
import com.omniflow.shared.domain.model.Category
import com.omniflow.shared.domain.model.CalendarDaySummary
import com.omniflow.shared.domain.model.CalendarDisplayAmount
import com.omniflow.shared.domain.model.CalendarTransactionFilter
import com.omniflow.shared.domain.model.ImportCommitResult
import com.omniflow.shared.domain.model.ImportCategoryBatchEdit
import com.omniflow.shared.domain.model.ImportExcludeBatchEdit
import com.omniflow.shared.domain.model.ImportPreviewState
import com.omniflow.shared.domain.model.ImportPreviewEdit
import com.omniflow.shared.domain.model.ImportRequest
import com.omniflow.shared.domain.model.HomeQuery
import com.omniflow.shared.domain.model.HomeState
import com.omniflow.shared.domain.model.DateRange
import com.omniflow.shared.domain.model.Ledger
import com.omniflow.shared.domain.model.LedgerScope
import com.omniflow.shared.domain.model.Money
import com.omniflow.shared.domain.model.QingziExportRequest
import com.omniflow.shared.domain.model.Reminder
import com.omniflow.shared.domain.model.ReminderSchedule
import com.omniflow.shared.domain.model.ReminderScheduleKind
import com.omniflow.shared.domain.model.ReminderType
import com.omniflow.shared.domain.model.RemoteBackupMeta
import com.omniflow.shared.domain.model.Rule
import com.omniflow.shared.domain.model.RuleActionType
import com.omniflow.shared.domain.model.RuleConditionType
import com.omniflow.shared.domain.model.SearchResult
import com.omniflow.shared.domain.model.Tag
import com.omniflow.shared.domain.model.SyncConfig
import com.omniflow.shared.domain.model.SyncState
import com.omniflow.shared.domain.model.SyncTarget
import com.omniflow.shared.domain.model.StatementTable
import com.omniflow.shared.domain.model.TimeGranularity
import com.omniflow.shared.domain.model.TransactionDetailQuery
import com.omniflow.shared.domain.model.TransactionDetailState
import com.omniflow.shared.domain.model.TransactionSearchQuery
import com.omniflow.shared.domain.model.Transaction
import com.omniflow.shared.domain.model.TransactionRecordDetail
import com.omniflow.shared.domain.model.TransactionType
import com.omniflow.shared.domain.model.calendarAmountText
import com.omniflow.shared.domain.model.displayAmount
import com.omniflow.shared.domain.model.hourMinuteText
import com.omniflow.shared.domain.model.transactionDateTimeText
import com.omniflow.shared.domain.model.yearMonthText
import com.omniflow.shared.domain.usecase.CreateTransactionCommand
import com.omniflow.shared.domain.util.UuidGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.omniflow.shared.parser.ImportFormat
import com.omniflow.shared.data.sync.AppleWebDavCredentials

class AppleFlowSubscription internal constructor(private val job: Job) {
    fun cancel() = job.cancel()
}

data class ApplePreferenceSnapshot(
    val homeLedgerId: String?,
    val analyticsLedgerId: String?,
    val transactionDetailDisplayMode: String,
    val appearanceMode: String,
    val themeColor: String,
    val appLockEnabled: Boolean,
    val syncTarget: String?,
    val backupRetention: Int,
)

data class AppleReminderSnapshot(
    val id: String,
    val name: String,
    val typeName: String,
    val amountMinor: Long,
    val hasAmount: Boolean,
    val scheduleKindName: String,
    val dayOfMonth: Int,
    val hasDayOfMonth: Boolean,
    val daysAfter: Int,
    val hasDaysAfter: Boolean,
    val dayOfWeek: Int,
    val hasDayOfWeek: Boolean,
    val month: Int,
    val hasMonth: Boolean,
    val paused: Boolean,
)

data class AppleSyncSnapshot(
    val phase: String,
    val progress: Float?,
    val lastBackupAt: String?,
    val errorMessage: String?,
)

class AppleAppBridge(val app: SharedApp) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ids = UuidGenerator()

    fun initialize(callback: (String?) -> Unit) {
        scope.launch { callback(app.initialize().exceptionOrNull()?.message) }
    }

    fun watchHome(query: HomeQuery, callback: (HomeState?, String?) -> Unit) =
        watch(app.home.observeHome(query), callback)

    fun watchHome(
        ledgerId: String?,
        startMillis: Long,
        endMillis: Long,
        calendarFilterName: String = CalendarTransactionFilter.ALL.name,
        callback: (HomeState?, String?) -> Unit,
    ) = watchHome(
        HomeQuery(
            scope = ledgerId?.let(LedgerScope::Single) ?: LedgerScope.All,
            month = DateRange(Instant.fromEpochMilliseconds(startMillis), Instant.fromEpochMilliseconds(endMillis)),
            calendarFilter = CalendarTransactionFilter.valueOf(calendarFilterName),
        ),
        callback,
    )

    fun calendarDisplayAmount(summary: CalendarDaySummary, filterName: String): CalendarDisplayAmount? =
        summary.displayAmount(CalendarTransactionFilter.valueOf(filterName))

    fun calendarAmountText(amountMinor: Long): String = Money(amountMinor).calendarAmountText()

    fun yearMonthText(epochMillis: Long): String = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date.yearMonthText()

    fun hourMinuteText(epochMillis: Long): String = Instant.fromEpochMilliseconds(epochMillis).hourMinuteText()

    fun transactionDateTimeText(epochMillis: Long): String =
        Instant.fromEpochMilliseconds(epochMillis).transactionDateTimeText()

    fun watchTransactionDetails(query: TransactionDetailQuery, callback: (TransactionDetailState?, String?) -> Unit) =
        watch(app.home.observeTransactionDetails(query), callback)

    fun watchTransactionDetails(
        ledgerId: String?,
        startMillis: Long,
        endMillis: Long,
        typeName: String? = null,
        callback: (TransactionDetailState?, String?) -> Unit,
    ) = watchTransactionDetails(
        TransactionDetailQuery(
            scope = ledgerId?.let(LedgerScope::Single) ?: LedgerScope.All,
            date = DateRange(Instant.fromEpochMilliseconds(startMillis), Instant.fromEpochMilliseconds(endMillis)),
            type = typeName?.let(TransactionType::valueOf),
        ),
        callback,
    )

    fun watchAnalytics(query: AnalyticsQuery, callback: (AnalyticsDashboardState?, String?) -> Unit) =
        watch(app.analytics.observeDashboard(query), callback)

    fun watchAnalytics(
        ledgerId: String?,
        startMillis: Long,
        endMillis: Long,
        rankingTypeName: String = TransactionType.EXPENSE.name,
        categoryTypeName: String = TransactionType.EXPENSE.name,
        tagTypeName: String = TransactionType.EXPENSE.name,
        trendGranularityName: String = TimeGranularity.DAY.name,
        callback: (AnalyticsDashboardState?, String?) -> Unit,
    ) = watchAnalytics(
        AnalyticsQuery(
            scope = ledgerId?.let(LedgerScope::Single) ?: LedgerScope.All,
            range = DateRange(Instant.fromEpochMilliseconds(startMillis), Instant.fromEpochMilliseconds(endMillis)),
            rankingType = TransactionType.valueOf(rankingTypeName),
            categoryShareType = TransactionType.valueOf(categoryTypeName),
            tagAnalysisType = TransactionType.valueOf(tagTypeName),
            trendGranularity = TimeGranularity.valueOf(trendGranularityName),
        ),
        callback,
    )

    fun loadStatementTable(
        ledgerId: String?,
        year: Int,
        callback: (StatementTable?, String?) -> Unit,
    ) {
        scope.launch {
            app.analytics.statementTable(ledgerId?.let(LedgerScope::Single) ?: LedgerScope.All, year).fold(
                onSuccess = { callback(it, null) },
                onFailure = { callback(null, it.message) },
            )
        }
    }

    fun watchLedgers(callback: (List<Ledger>?, String?) -> Unit) =
        watch(app.management.observeLedgers(), callback)

    fun watchDefaultLedgerId(callback: (String?, String?) -> Unit) =
        watch(app.management.observeDefaultLedgerId(), callback)

    fun watchAccounts(callback: (List<Account>?, String?) -> Unit) =
        watch(app.management.observeAccounts(), callback)

    fun watchCategories(ledgerId: String, callback: (List<Category>?, String?) -> Unit) =
        watch(app.management.observeCategories(ledgerId), callback)

    fun watchTags(ledgerId: String, callback: (List<Tag>?, String?) -> Unit) =
        watch(app.management.observeTags(ledgerId), callback)

    fun watchRules(ledgerId: String, callback: (List<Rule>?, String?) -> Unit) =
        watch(app.management.observeRules(ledgerId), callback)

    fun watchReminders(callback: (List<AppleReminderSnapshot>?, String?) -> Unit) = watch(
        app.reminders.observe().map { result ->
            result.map { reminders ->
                reminders.map { reminder ->
                    AppleReminderSnapshot(
                        id = reminder.id,
                        name = reminder.name,
                        typeName = reminder.type.name,
                        amountMinor = reminder.amount?.minor ?: 0,
                        hasAmount = reminder.amount != null,
                        scheduleKindName = reminder.schedule.kind.name,
                        dayOfMonth = reminder.schedule.dayOfMonth ?: 0,
                        hasDayOfMonth = reminder.schedule.dayOfMonth != null,
                        daysAfter = reminder.schedule.daysAfter ?: 0,
                        hasDaysAfter = reminder.schedule.daysAfter != null,
                        dayOfWeek = reminder.schedule.dayOfWeek ?: 0,
                        hasDayOfWeek = reminder.schedule.dayOfWeek != null,
                        month = reminder.schedule.month ?: 0,
                        hasMonth = reminder.schedule.month != null,
                        paused = reminder.paused,
                    )
                }
            }
        },
        callback,
    )

    fun watchPreferences(callback: (AppPreferences?, String?) -> Unit) =
        watch(app.preferences.observe(), callback)

    fun watchPreferenceSnapshot(callback: (ApplePreferenceSnapshot?, String?) -> Unit) = watch(
        app.preferences.observe().map { result ->
            result.map { preferences ->
                ApplePreferenceSnapshot(
                    homeLedgerId = (preferences.homeLedgerScope as? LedgerScope.Single)?.ledgerId,
                    analyticsLedgerId = (preferences.analyticsLedgerScope as? LedgerScope.Single)?.ledgerId,
                    transactionDetailDisplayMode = preferences.transactionDetailDisplayMode.name,
                    appearanceMode = preferences.appearanceMode.name,
                    themeColor = preferences.themeColor.name,
                    appLockEnabled = preferences.appLockEnabled,
                    syncTarget = preferences.syncTarget?.name,
                    backupRetention = preferences.backupRetention,
                )
            }
        },
        callback,
    )

    fun watchSyncState(callback: (AppleSyncSnapshot?, String?) -> Unit) = watch(
        app.sync.observeSyncState().map { state: SyncState ->
            Result.success(
                AppleSyncSnapshot(
                    phase = state.phase.name,
                    progress = state.progress,
                    lastBackupAt = state.lastBackupAt?.toString(),
                    errorMessage = state.errorMessage,
                ),
            )
        },
        callback,
    )

    fun search(query: TransactionSearchQuery, callback: (SearchResult?, String?) -> Unit) {
        scope.launch {
            app.search(query).fold(
                onSuccess = { callback(it, null) },
                onFailure = { callback(null, it.message) },
            )
        }
    }

    fun search(
        keyword: String,
        primaryCategoryText: String,
        secondaryCategoryText: String,
        tagText: String,
        noteText: String,
        ledgerId: String?,
        typeName: String?,
        accountId: String?,
        primaryCategoryId: String?,
        secondaryCategoryId: String?,
        tagId: String?,
        exactMinor: Long?,
        minimumMinor: Long?,
        maximumMinor: Long?,
        startMillis: Long?,
        endMillis: Long?,
        callback: (SearchResult?, String?) -> Unit,
    ) = search(
        TransactionSearchQuery(
            scope = ledgerId?.let(LedgerScope::Single) ?: LedgerScope.All,
            keyword = keyword,
            primaryCategoryText = primaryCategoryText,
            secondaryCategoryText = secondaryCategoryText,
            tagText = tagText,
            noteText = noteText,
            type = typeName?.let(TransactionType::valueOf),
            accountId = accountId,
            primaryCategoryId = primaryCategoryId,
            secondaryCategoryId = secondaryCategoryId,
            tagId = tagId,
            amount = AmountFilter(exactMinor?.let(::Money), minimumMinor?.let(::Money), maximumMinor?.let(::Money)),
            dateRange = if (startMillis != null && endMillis != null) {
                DateRange(Instant.fromEpochMilliseconds(startMillis), Instant.fromEpochMilliseconds(endMillis))
            } else {
                null
            },
        ),
        callback,
    )

    fun loadTransaction(id: String, callback: (Transaction?, String?) -> Unit) {
        scope.launch {
            app.getTransaction(id).fold(
                onSuccess = { callback(it, null) },
                onFailure = { callback(null, it.message) },
            )
        }
    }

    fun loadTransactionRecordDetail(id: String, callback: (TransactionRecordDetail?, String?) -> Unit) {
        scope.launch {
            app.getTransactionRecordDetail(id).fold(
                onSuccess = { callback(it, null) },
                onFailure = { callback(null, it.message) },
            )
        }
    }

    fun saveTransaction(
        id: String?,
        ledgerId: String,
        accountId: String,
        categoryId: String,
        amountMinor: Long,
        typeName: String,
        occurredAtMillis: Long,
        note: String?,
        excluded: Boolean,
        tagIds: Set<String>,
        callback: (String?) -> Unit,
    ) {
        scope.launch {
            val transactionId = id ?: ids.next()
            val type = TransactionType.valueOf(typeName)
            val result = if (id == null) {
                app.createTransaction(
                    CreateTransactionCommand(
                        id = transactionId,
                        ledgerId = ledgerId,
                        accountId = accountId,
                        categoryId = categoryId,
                        amount = Money(amountMinor),
                        type = type,
                        occurredAt = Instant.fromEpochMilliseconds(occurredAtMillis),
                        note = note,
                        isExcluded = excluded,
                        tagIds = tagIds,
                    ),
                )
            } else {
                val existing = app.getTransaction(transactionId).getOrThrow()
                    ?: error("交易不存在或已删除")
                app.updateTransaction(
                    existing.copy(
                        ledgerId = ledgerId,
                        accountId = accountId,
                        categoryId = categoryId,
                        amount = Money(amountMinor),
                        type = type,
                        occurredAt = Instant.fromEpochMilliseconds(occurredAtMillis),
                        note = note,
                        isExcluded = excluded,
                        tagIds = tagIds,
                    ),
                )
            }
            callback(result.exceptionOrNull()?.message)
        }
    }

    fun deleteTransaction(id: String, callback: (String?) -> Unit) {
        scope.launch { callback(app.deleteTransaction(id).exceptionOrNull()?.message) }
    }

    fun saveLedger(id: String?, name: String, coverKey: String?, callback: (String?) -> Unit) {
        scope.launch {
            val ledger = Ledger(id ?: ids.next(), name, coverKey)
            val result = if (id == null) app.createLedger(ledger) else app.updateLedger(ledger)
            callback(result.exceptionOrNull()?.message)
        }
    }

    fun deleteLedger(id: String, callback: (String?) -> Unit) {
        scope.launch { callback(app.deleteLedger(id).exceptionOrNull()?.message) }
    }

    fun setDefaultLedger(id: String?, callback: (String?) -> Unit) {
        scope.launch { callback(app.setDefaultLedger(id).exceptionOrNull()?.message) }
    }

    fun saveAccount(
        id: String?,
        name: String,
        typeName: String,
        iconKey: String,
        cardNumber: String?,
        note: String?,
        balanceMinor: Long,
        includeInTotalAssets: Boolean,
        callback: (String?) -> Unit,
    ) {
        scope.launch {
            val account = Account(
                id = id ?: ids.next(),
                name = name,
                type = AccountType.valueOf(typeName),
                iconKey = iconKey,
                cardNumber = cardNumber,
                note = note,
                balance = Money(balanceMinor),
                includeInTotalAssets = includeInTotalAssets,
            )
            val result = if (id == null) app.createAccount(account) else app.updateAccount(account)
            callback(result.exceptionOrNull()?.message)
        }
    }

    fun deleteAccount(id: String, callback: (String?) -> Unit) {
        scope.launch { callback(app.deleteAccount(id).exceptionOrNull()?.message) }
    }

    fun saveCategory(
        id: String?,
        ledgerId: String,
        parentId: String?,
        name: String,
        iconKey: String?,
        typeName: String,
        callback: (String?) -> Unit,
    ) {
        scope.launch {
            val category = Category(id ?: ids.next(), ledgerId, parentId, name, iconKey, TransactionType.valueOf(typeName))
            val result = if (id == null) app.createCategory(category) else app.updateCategory(category)
            callback(result.exceptionOrNull()?.message)
        }
    }

    fun deleteCategory(id: String, callback: (String?) -> Unit) {
        scope.launch { callback(app.deleteCategory(id).exceptionOrNull()?.message) }
    }

    fun reorderPrimaryCategories(
        ledgerId: String,
        typeName: String,
        orderedIds: List<String>,
        callback: (String?) -> Unit,
    ) {
        scope.launch {
            callback(
                app.reorderPrimaryCategories(ledgerId, TransactionType.valueOf(typeName), orderedIds)
                    .exceptionOrNull()?.message,
            )
        }
    }

    fun saveTag(id: String?, ledgerId: String, name: String, callback: (String?) -> Unit) {
        scope.launch {
            val tag = Tag(id ?: ids.next(), ledgerId, name)
            val result = if (id == null) app.createTag(tag) else app.updateTag(tag)
            callback(result.exceptionOrNull()?.message)
        }
    }

    fun deleteTag(id: String, callback: (String?) -> Unit) {
        scope.launch { callback(app.deleteTag(id).exceptionOrNull()?.message) }
    }

    fun saveRule(
        id: String?,
        ledgerId: String,
        name: String,
        conditionTypeName: String,
        conditionValue: String,
        actionTypeName: String,
        actionValue: String,
        priority: Int,
        callback: (String?) -> Unit,
    ) {
        scope.launch {
            val rule = Rule(
                id ?: ids.next(), ledgerId, name,
                RuleConditionType.valueOf(conditionTypeName), conditionValue,
                RuleActionType.valueOf(actionTypeName), actionValue, priority,
            )
            val result = if (id == null) app.createRule(rule) else app.updateRule(rule)
            callback(result.exceptionOrNull()?.message)
        }
    }

    fun deleteRule(id: String, callback: (String?) -> Unit) {
        scope.launch { callback(app.deleteRule(id).exceptionOrNull()?.message) }
    }

    fun reorderRules(ledgerId: String, orderedIds: List<String>, callback: (String?) -> Unit) {
        scope.launch { callback(app.reorderRules(ledgerId, orderedIds).exceptionOrNull()?.message) }
    }

    fun saveReminder(
        id: String?,
        typeName: String,
        name: String,
        amountMinor: Long,
        hasAmount: Boolean,
        scheduleKindName: String,
        dayOfMonth: Int,
        hasDayOfMonth: Boolean,
        daysAfter: Int,
        hasDaysAfter: Boolean,
        dayOfWeek: Int,
        hasDayOfWeek: Boolean,
        month: Int,
        hasMonth: Boolean,
        paused: Boolean,
        callback: (String?) -> Unit,
    ) {
        scope.launch {
            val reminder = Reminder(
                id = id ?: ids.next(),
                type = ReminderType.valueOf(typeName),
                name = name,
                amount = if (hasAmount) Money(amountMinor) else null,
                schedule = ReminderSchedule(
                    ReminderScheduleKind.valueOf(scheduleKindName),
                    dayOfMonth.takeIf { hasDayOfMonth },
                    daysAfter.takeIf { hasDaysAfter },
                    dayOfWeek.takeIf { hasDayOfWeek },
                    month.takeIf { hasMonth },
                ),
                paused = paused,
            )
            val result = if (id == null) app.createReminder(reminder) else app.updateReminder(reminder)
            callback(result.exceptionOrNull()?.message)
        }
    }

    fun deleteReminder(id: String, callback: (String?) -> Unit) {
        scope.launch { callback(app.deleteReminder(id).exceptionOrNull()?.message) }
    }

    fun setReminderPaused(reminder: Reminder, paused: Boolean, callback: (String?) -> Unit) {
        scope.launch { callback(app.setReminderPaused(reminder, paused).exceptionOrNull()?.message) }
    }

    fun setReminderPaused(id: String, paused: Boolean, callback: (String?) -> Unit) {
        scope.launch {
            val reminder = app.reminders.observe().first().getOrThrow().firstOrNull { it.id == id }
                ?: error("提醒不存在或已删除")
            callback(app.setReminderPaused(reminder, paused).exceptionOrNull()?.message)
        }
    }

    fun previewImport(
        ledgerId: String,
        fileName: String,
        bytes: ByteArray,
        formatName: String?,
        callback: (ImportPreviewState?, String?) -> Unit,
    ): AppleFlowSubscription = watch(
        app.imports.preview(ImportRequest(ledgerId, fileName, bytes, formatName?.let(ImportFormat::valueOf))),
        callback,
    )

    fun commitImport(sessionId: String, callback: (ImportCommitResult?, String?) -> Unit) {
        scope.launch {
            app.imports.commit(sessionId).fold(
                onSuccess = { callback(it, null) },
                onFailure = { callback(null, it.message) },
            )
        }
    }

    fun cancelImport(sessionId: String, callback: (String?) -> Unit) {
        scope.launch { callback(app.imports.cancel(sessionId).exceptionOrNull()?.message) }
    }

    fun editImportItem(
        sessionId: String,
        itemId: String,
        typeName: String?,
        categoryId: String?,
        accountId: String?,
        note: String?,
        tags: List<String>,
        excluded: Boolean,
        skipped: Boolean,
        callback: (ImportPreviewState?, String?) -> Unit,
    ) {
        scope.launch {
            app.imports.editItem(
                ImportPreviewEdit(
                    sessionId, itemId, typeName?.let(TransactionType::valueOf), categoryId, accountId,
                    note, tags, excluded, skipped,
                ),
            ).fold(
                onSuccess = { callback(it, null) },
                onFailure = { callback(null, it.message) },
            )
        }
    }

    fun editImportCategories(
        sessionId: String,
        itemIds: Set<String>,
        categoryId: String?,
        callback: (ImportPreviewState?, String?) -> Unit,
    ) {
        scope.launch {
            app.imports.editCategories(sessionId, ImportCategoryBatchEdit(itemIds, categoryId)).fold(
                onSuccess = { callback(it, null) },
                onFailure = { callback(null, it.message) },
            )
        }
    }

    fun editImportSkipped(
        sessionId: String,
        itemIds: Set<String>,
        skipped: Boolean,
        callback: (ImportPreviewState?, String?) -> Unit,
    ) {
        scope.launch {
            app.imports.editSkipped(sessionId, ImportExcludeBatchEdit(itemIds, skipped)).fold(
                onSuccess = { callback(it, null) },
                onFailure = { callback(null, it.message) },
            )
        }
    }

    fun exportQingzi(callback: (String?, List<String>, String?) -> Unit) {
        scope.launch {
            app.qingzi.export().fold(
                onSuccess = { callback(it.payload, it.warnings, null) },
                onFailure = { callback(null, emptyList(), it.message) },
            )
        }
    }

    fun exportQingziRange(
        startMillis: Long?,
        endMillis: Long?,
        callback: (String?, List<String>, String?) -> Unit,
    ) {
        scope.launch {
            val range = if (startMillis != null && endMillis != null) {
                DateRange(Instant.fromEpochMilliseconds(startMillis), Instant.fromEpochMilliseconds(endMillis))
            } else {
                null
            }
            app.qingzi.export(QingziExportRequest(dateRange = range)).fold(
                onSuccess = { callback(it.payload, it.warnings, null) },
                onFailure = { callback(null, emptyList(), it.message) },
            )
        }
    }

    fun configureSync(targetName: String, retention: Int, callback: (String?) -> Unit) {
        scope.launch {
            callback(app.sync.configure(SyncConfig(SyncTarget.valueOf(targetName), retention)).exceptionOrNull()?.message)
        }
    }

    fun syncNow(callback: (String?) -> Unit) {
        scope.launch { callback(app.sync.syncNow().exceptionOrNull()?.message) }
    }

    fun listBackups(callback: (List<RemoteBackupMeta>?, String?) -> Unit) {
        scope.launch {
            app.sync.listBackups().fold(
                onSuccess = { callback(it, null) },
                onFailure = { callback(null, it.message) },
            )
        }
    }

    fun restoreBackup(meta: RemoteBackupMeta, callback: (String?) -> Unit) {
        scope.launch { callback(app.sync.restore(meta).exceptionOrNull()?.message) }
    }

    fun setAppearanceMode(modeName: String, callback: (String?) -> Unit) {
        scope.launch {
            val current = app.preferences.observe().first().getOrThrow()
            callback(
                app.preferences.save(
                    current.copy(appearanceMode = com.omniflow.shared.domain.model.AppearanceMode.valueOf(modeName)),
                ).exceptionOrNull()?.message,
            )
        }
    }

    fun setThemeColor(colorName: String, callback: (String?) -> Unit) {
        scope.launch {
            val current = app.preferences.observe().first().getOrThrow()
            callback(
                app.preferences.save(
                    current.copy(themeColor = com.omniflow.shared.domain.model.ThemeColor.valueOf(colorName)),
                ).exceptionOrNull()?.message,
            )
        }
    }

    fun setAppLockEnabled(enabled: Boolean, callback: (String?) -> Unit) {
        scope.launch {
            val current = app.preferences.observe().first().getOrThrow()
            callback(app.preferences.save(current.copy(appLockEnabled = enabled)).exceptionOrNull()?.message)
        }
    }

    fun setHomeLedgerScope(ledgerId: String?, callback: (String?) -> Unit) {
        scope.launch {
            val current = app.preferences.observe().first().getOrThrow()
            callback(
                app.preferences.save(
                    current.copy(homeLedgerScope = ledgerId?.let(LedgerScope::Single) ?: LedgerScope.All),
                ).exceptionOrNull()?.message,
            )
        }
    }

    fun setAnalyticsLedgerScope(ledgerId: String?, callback: (String?) -> Unit) {
        scope.launch {
            val current = app.preferences.observe().first().getOrThrow()
            callback(
                app.preferences.save(
                    current.copy(analyticsLedgerScope = ledgerId?.let(LedgerScope::Single) ?: LedgerScope.All),
                ).exceptionOrNull()?.message,
            )
        }
    }

    fun setTransactionDetailDisplayMode(modeName: String, callback: (String?) -> Unit) {
        scope.launch {
            val current = app.preferences.observe().first().getOrThrow()
            callback(
                app.preferences.save(
                    current.copy(
                        transactionDetailDisplayMode = com.omniflow.shared.domain.model.TransactionDetailDisplayMode.valueOf(modeName),
                    ),
                ).exceptionOrNull()?.message,
            )
        }
    }

    fun configureWebDav(endpoint: String, username: String, password: String) {
        AppleWebDavCredentials.configure(endpoint, username, password)
    }

    private fun <T> watch(flow: Flow<Result<T>>, callback: (T?, String?) -> Unit): AppleFlowSubscription =
        AppleFlowSubscription(scope.launch {
            flow.collect { result ->
                result.fold(
                    onSuccess = { callback(it, null) },
                    onFailure = { callback(null, it.message) },
                )
            }
        })
}
