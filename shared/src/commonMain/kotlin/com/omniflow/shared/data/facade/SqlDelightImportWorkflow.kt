package com.omniflow.shared.data.facade

import com.omniflow.shared.domain.facade.ImportWorkflow
import com.omniflow.shared.domain.model.Account
import com.omniflow.shared.domain.model.ImportCategoryBatchEdit
import com.omniflow.shared.domain.model.ImportCategoryOrigin
import com.omniflow.shared.domain.model.ImportCommitResult
import com.omniflow.shared.domain.model.ImportDuplicateStatus
import com.omniflow.shared.domain.model.ImportExcludeBatchEdit
import com.omniflow.shared.domain.model.ImportPreviewEdit
import com.omniflow.shared.domain.model.ImportPreviewItem
import com.omniflow.shared.domain.model.ImportPreviewState
import com.omniflow.shared.domain.model.ImportPreviewPhase
import com.omniflow.shared.domain.model.ImportRequest
import com.omniflow.shared.domain.model.ImportSessionId
import com.omniflow.shared.domain.model.LedgerId
import com.omniflow.shared.domain.model.Rule
import com.omniflow.shared.domain.model.RuleActionType
import com.omniflow.shared.domain.model.Transaction
import com.omniflow.shared.domain.repository.AccountRepository
import com.omniflow.shared.domain.repository.CategoryMemoryEntry
import com.omniflow.shared.domain.repository.CategoryMemoryRepository
import com.omniflow.shared.domain.repository.CategoryRepository
import com.omniflow.shared.domain.repository.ImportCommitRepository
import com.omniflow.shared.domain.repository.ImportCommitTransaction
import com.omniflow.shared.domain.repository.ImportPreviewSession
import com.omniflow.shared.domain.repository.ImportSessionRepository
import com.omniflow.shared.domain.repository.RuleRepository
import com.omniflow.shared.domain.repository.TransactionDedupeRepository
import com.omniflow.shared.domain.util.UuidGenerator
import com.omniflow.shared.domain.usecase.CreateImportPreviewUseCase
import com.omniflow.shared.parser.BillFormatDetector
import com.omniflow.shared.parser.ImportFormat
import com.omniflow.shared.parser.RawTransaction
import com.omniflow.shared.parser.csv.CsvCharset
import com.omniflow.shared.parser.csv.CsvDecoder
import com.omniflow.shared.parser.csv.CsvBillParser
import com.omniflow.shared.parser.qingzi.QingziBillParser
import com.omniflow.shared.parser.spreadsheet.SpreadsheetBillParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.math.max

class SqlDelightImportWorkflow(
    private val sessions: ImportSessionRepository,
    private val commits: ImportCommitRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val rules: RuleRepository,
    private val categoryMemory: CategoryMemoryRepository,
    private val dedupe: TransactionDedupeRepository,
    private val previewFactory: CreateImportPreviewUseCase = CreateImportPreviewUseCase(),
    private val formatDetector: BillFormatDetector = BillFormatDetector(),
    private val csvParser: CsvBillParser = CsvBillParser(),
    private val qingziParser: QingziBillParser = QingziBillParser(),
    private val ids: UuidGenerator = UuidGenerator(),
) : ImportWorkflow {
    override fun preview(request: ImportRequest): Flow<Result<ImportPreviewState>> = flow {
        emit(Result.success(progressState(request, ImportPreviewPhase.DETECTING, 0.1f)))
        emit(runCatching {
            val format = detectFormat(request)
            emit(Result.success(progressState(request, ImportPreviewPhase.PARSING, 0.3f, format)))
            val rawItems = parse(format, request.bytes)
            val sessionId = ids.next()
            emit(Result.success(progressState(request, ImportPreviewPhase.ENRICHING, 0.7f, format)))
            val state = enrichPreview(
                previewFactory.fromRaw(sessionId, request.ledgerId, format, rawItems),
            )
            sessions.create(sessionId, request.ledgerId, format, state.items)
            state
        })
    }.flowOn(Dispatchers.Default)

    override fun observe(sessionId: ImportSessionId): Flow<Result<ImportPreviewState>> = flow {
        emit(runCatching { toState(sessions.state(sessionId) ?: error("导入会话不存在或已结束")) })
    }

    override suspend fun editItem(edit: ImportPreviewEdit): Result<ImportPreviewState> = withContext(Dispatchers.Default) {
        runCatching {
            sessions.updateItem(edit.sessionId, edit)
            toState(sessions.state(edit.sessionId) ?: error("导入会话不存在或已结束"))
        }
    }

    override suspend fun editCategories(
        sessionId: ImportSessionId,
        edit: ImportCategoryBatchEdit,
    ): Result<ImportPreviewState> = withContext(Dispatchers.Default) {
        runCatching {
            val session = sessions.state(sessionId) ?: error("导入会话不存在或已结束")
            session.items.filter { it.id in edit.itemIds }.forEach { item ->
                sessions.updateCategory(sessionId, item.id, edit.categoryId)
            }
            toState(sessions.state(sessionId) ?: error("导入会话不存在或已结束"))
        }
    }

    override suspend fun editSkipped(
        sessionId: ImportSessionId,
        edit: ImportExcludeBatchEdit,
    ): Result<ImportPreviewState> = withContext(Dispatchers.Default) {
        runCatching {
            val session = sessions.state(sessionId) ?: error("导入会话不存在或已结束")
            session.items.filter { it.id in edit.itemIds }.forEach { item ->
                sessions.updateSkipped(sessionId, item.id, edit.isSkipped)
            }
            toState(sessions.state(sessionId) ?: error("导入会话不存在或已结束"))
        }
    }

    override suspend fun commit(sessionId: ImportSessionId): Result<ImportCommitResult> = withContext(Dispatchers.Default) {
        runCatching {
            val session = sessions.state(sessionId) ?: error("导入会话不存在或已结束")
            val state = toState(session)
            require(state.isReadyToCommit) { "仍有未完成的导入明细" }
            val transactionsToCreate = state.importableItems.map { item ->
            ImportCommitTransaction(
                transaction = Transaction(
                    id = ids.next(),
                    ledgerId = session.ledgerId,
                    accountId = item.accountId ?: error("导入明细缺少账户"),
                    categoryId = item.categoryId ?: error("导入明细缺少分类"),
                    amount = item.raw.amount,
                    type = item.type ?: error("导入明细缺少收支类型"),
                    occurredAt = item.raw.occurredAt,
                    note = item.note,
                    isExcluded = item.isExcluded,
                    source = session.format.transactionSource,
                    externalId = item.raw.externalId,
                ),
                tagNames = item.tags,
            )
        }
            val categoryMemories = state.importableItems
            .filter { it.categoryOrigin == ImportCategoryOrigin.USER && it.categoryId != null }
            .map { item ->
                CategoryMemoryEntry(
                    ledgerId = session.ledgerId,
                    memoryKey = memoryKey(item.raw),
                    categoryId = item.categoryId!!,
                )
            }
            commits.commit(sessionId, transactionsToCreate, categoryMemories)
            ImportCommitResult(
                importedCount = transactionsToCreate.size,
                excludedCount = session.items.count(ImportPreviewItem::isSkipped),
            )
        }
    }

    private fun progressState(
        request: ImportRequest,
        phase: ImportPreviewPhase,
        progress: Float,
        format: ImportFormat? = request.selectedFormat,
    ) = ImportPreviewState(
        sessionId = "",
        ledgerId = request.ledgerId,
        format = format,
        items = emptyList(),
        phase = phase,
        progress = progress,
    )

    private suspend fun enrichPreview(initial: ImportPreviewState): ImportPreviewState {
        val activeAccounts = accounts.activeAccounts()
        val activeCategories = categories.activeCategories(initial.ledgerId)
        val activeRules = rules.activeRules(initial.ledgerId)
        val enrichedItems = mutableListOf<ImportPreviewItem>()
        for (item in initial.items) {
            val duplicateStatus = duplicateStatus(initial.ledgerId, item.raw)
            val matchedRule = activeRules.firstOrNull { rule -> ruleMatches(rule, item.raw) }
            val ruleCategory = matchedRule?.takeIf { it.actionType == RuleActionType.SET_CATEGORY }?.actionValue
            val memoryCategory = if (matchedRule == null) {
                categoryMemory.categoryId(initial.ledgerId, memoryKey(item.raw))
            } else {
                null
            }
            val selectedCategory = (ruleCategory ?: memoryCategory)
                ?.takeIf { candidate -> activeCategories.any { it.id == candidate && it.type == item.type } }
            enrichedItems += item.copy(
                accountId = resolveAccount(item.raw, activeAccounts),
                categoryId = selectedCategory,
                categoryOrigin = when {
                    ruleCategory != null && selectedCategory != null -> ImportCategoryOrigin.RULE
                    memoryCategory != null && selectedCategory != null -> ImportCategoryOrigin.MEMORY
                    else -> ImportCategoryOrigin.NONE
                },
                isExcluded = item.isExcluded || matchedRule?.actionType == RuleActionType.SET_EXCLUDED,
                isSkipped = matchedRule?.actionType == RuleActionType.EXCLUDE ||
                    duplicateStatus != ImportDuplicateStatus.NONE,
                duplicateStatus = duplicateStatus,
            )
        }
        return initial.copy(items = enrichedItems)
    }

    private suspend fun duplicateStatus(ledgerId: LedgerId, raw: RawTransaction): ImportDuplicateStatus {
        val source = raw.format.transactionSource?.name
        if (source != null && raw.externalId != null && dedupe.hasExternalId(source, raw.externalId)) {
            return ImportDuplicateStatus.CONFIRMED
        }
        val instant = raw.occurredAt.toEpochMilliseconds()
        val (start, end) = if (raw.format == ImportFormat.CCB) {
            val startOfDay = raw.occurredAt.toLocalDateTime(CHINA_TIME_ZONE).date
                .atStartOfDayIn(CHINA_TIME_ZONE)
                .toEpochMilliseconds()
            startOfDay to startOfDay + DAY_MILLISECONDS
        } else {
            max(0L, instant - TWO_HOURS_MILLISECONDS) to instant + TWO_HOURS_MILLISECONDS + 1
        }
        return if (dedupe.likelyDuplicate(
                ledgerId = ledgerId,
                amount = raw.amount,
                occurredAtStart = start,
                occurredAtEnd = end,
                note = raw.note,
            )
        ) {
            ImportDuplicateStatus.SUSPECTED
        } else {
            ImportDuplicateStatus.NONE
        }
    }

    private fun resolveAccount(raw: RawTransaction, activeAccounts: List<Account>): String? = when {
        raw.format == ImportFormat.QINGZI -> activeAccounts.firstOrNull { it.name == "现金" }?.id
        else -> activeAccounts.firstOrNull { it.name == raw.accountName?.trim() }?.id
            ?: activeAccounts.firstOrNull { it.name == "现金" }?.id
    }

    private fun detectFormat(request: ImportRequest): ImportFormat {
        request.selectedFormat?.let { return it }
        val fileName = request.fileName.lowercase()
        if (fileName.endsWith(".xlsx")) return ImportFormat.WECHAT
        if (fileName.endsWith(".xls")) return ImportFormat.CCB

        val candidates = listOf(CsvCharset.UTF8, CsvCharset.GB18030)
            .flatMap { charset -> formatDetector.detect(request.fileName, CsvDecoder.decode(request.bytes, charset)) }
            .distinct()
        return candidates.singleOrNull()
            ?: error("无法唯一识别账单来源，请选择导入来源")
    }

    private fun parse(format: ImportFormat, bytes: ByteArray): List<RawTransaction> = when (format) {
        ImportFormat.ALIPAY -> csvParser.parse(format, CsvDecoder.decode(bytes, CsvCharset.GB18030)).getOrThrow()
        ImportFormat.JD, ImportFormat.MEITUAN -> csvParser.parse(format, CsvDecoder.decode(bytes, CsvCharset.UTF8)).getOrThrow()
        ImportFormat.QINGZI -> qingziParser.parse(CsvDecoder.decode(bytes, CsvCharset.UTF8)).getOrThrow().transactions
        ImportFormat.WECHAT, ImportFormat.CCB -> SpreadsheetBillParser.parse(format, bytes).getOrThrow()
    }

    private fun toState(session: ImportPreviewSession): ImportPreviewState = ImportPreviewState(
        sessionId = session.id,
        ledgerId = session.ledgerId,
        format = session.format,
        items = session.items,
    )

    private fun ruleMatches(rule: Rule, raw: RawTransaction): Boolean = when (rule.conditionType.name) {
        "NOTE_CONTAINS" -> raw.note?.contains(rule.conditionValue, ignoreCase = true) == true
        "TRANSACTION_TYPE" -> raw.type?.name == rule.conditionValue
        "TRANSACTION_SOURCE" -> raw.format.transactionSource?.name == rule.conditionValue
        else -> false
    }

    private fun memoryKey(raw: RawTransaction): String = "${raw.format.transactionSource?.name.orEmpty()}:${normalize(raw.note)}"

    private fun normalize(value: String?): String = value.orEmpty().lowercase().filterNot(Char::isWhitespace)

    private companion object {
        const val TWO_HOURS_MILLISECONDS = 2 * 60 * 60 * 1000L
        const val DAY_MILLISECONDS = 24 * 60 * 60 * 1000L
        val CHINA_TIME_ZONE: TimeZone = TimeZone.of("Asia/Shanghai")
    }
}
