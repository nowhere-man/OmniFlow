package com.omniflow.shared.data.sync

import com.omniflow.shared.db.OmniFlowDatabase
import com.omniflow.shared.domain.model.BackupRecord
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class SqlDelightBackupStore(
    private val database: OmniFlowDatabase,
    private val json: Json = Json,
) : BackupStore {
    override suspend fun create(deviceId: String, backupId: String, createdAtMillis: Long): BackupRecord {
        val queries = database.backupQueries
        val payload = JsonObject(
            mapOf(
                "version" to JsonPrimitive(1),
                "ledgers" to JsonArray(queries.allLedgersForBackup().executeAsList().map { row(
                    it.id, it.name, it.cover_key, it.created_at, it.updated_at, it.deleted_at,
                ) }),
                "accounts" to JsonArray(queries.allAccountsForBackup().executeAsList().map { row(
                    it.id, it.name, it.type, it.icon_key, it.card_number, it.note, it.balance_minor,
                    it.include_in_total_assets, it.created_at, it.updated_at, it.deleted_at,
                ) }),
                "accountBalanceRecords" to JsonArray(queries.allAccountBalanceRecordsForBackup().executeAsList().map { row(
                    it.id, it.account_id, it.date, it.balance_minor, it.delta_minor, it.created_at,
                ) }),
                "categories" to JsonArray(queries.allCategoriesForBackup().executeAsList().map { row(
                    it.id, it.ledger_id, it.parent_id, it.name, it.icon_key, it.type,
                    it.created_at, it.updated_at, it.deleted_at, it.sort_order,
                ) }),
                "tags" to JsonArray(queries.allTagsForBackup().executeAsList().map { row(
                    it.id, it.ledger_id, it.name, it.created_at, it.updated_at, it.deleted_at,
                ) }),
                "transactionTags" to JsonArray(queries.allTransactionTagsForBackup().executeAsList().map { row(
                    it.transaction_id, it.tag_id,
                ) }),
                "transactions" to JsonArray(queries.allTransactionsForBackup().executeAsList().map { row(
                    it.id, it.ledger_id, it.account_id, it.category_id, it.amount_minor, it.type,
                    it.occurred_at, it.note, it.is_excluded, it.external_source, it.external_id,
                    it.created_at, it.updated_at, it.deleted_at,
                ) }),
                "rules" to JsonArray(queries.allRulesForBackup().executeAsList().map { row(
                    it.id, it.ledger_id, it.name, it.condition_type, it.condition_value, it.action_type,
                    it.action_value, it.priority, it.created_at, it.updated_at, it.deleted_at,
                ) }),
                "categoryMemories" to JsonArray(queries.allCategoryMemoriesForBackup().executeAsList().map { row(
                    it.ledger_id, it.memory_key, it.category_id, it.updated_at,
                ) }),
                "reminders" to JsonArray(queries.allRemindersForBackup().executeAsList().map { row(
                    it.id, it.type, it.name, it.amount_minor, it.schedule_kind, it.schedule_value,
                    it.paused, it.created_at, it.updated_at, it.deleted_at,
                ) }),
                "appPreferences" to JsonArray(queries.allAppPreferencesForBackup().executeAsList().map { row(
                    it.key, it.value_, it.updated_at,
                ) }),
            ),
        ).toString()
        return BackupRecord(deviceId, backupId, Instant.fromEpochMilliseconds(createdAtMillis), payload)
    }

    override suspend fun restore(backup: BackupRecord) {
        val root = json.parseToJsonElement(backup.payload).jsonObject
        require(root["version"]?.jsonPrimitive?.content == "1") { "不支持的备份版本" }
        REQUIRED_SECTIONS.forEach { name ->
            require(root[name] is JsonArray) { "备份缺少数据段：$name" }
        }
        val queries = database.backupQueries
        database.transaction {
            queries.clearTransactionTags()
            queries.clearTransactions()
            queries.clearCategoryMemories()
            queries.clearImportPreviewItems()
            queries.clearImportSessions()
            queries.clearRules()
            queries.clearTags()
            queries.clearCategories()
            queries.clearAccountBalanceRecords()
            queries.clearAccounts()
            queries.clearLedgers()
            queries.clearReminders()
            queries.clearAppPreferences()

            root.rows("ledgers").forEach { element -> element.jsonArray.let { values ->
                queries.restoreLedger(values.text(0), values.text(1), values.nullableText(2), values.long(3), values.long(4), values.nullableLong(5))
            } }
            root.rows("accounts").forEach { element -> element.jsonArray.let { values ->
                queries.restoreAccount(
                    values.text(0), values.text(1), values.text(2), values.text(3), values.nullableText(4),
                    values.nullableText(5), values.long(6), values.long(7), values.long(8), values.long(9), values.nullableLong(10),
                )
            } }
            root.rows("accountBalanceRecords").forEach { element -> element.jsonArray.let { values ->
                queries.restoreAccountBalanceRecord(
                    values.text(0), values.text(1), values.long(2), values.long(3), values.long(4), values.long(5),
                )
            } }
            root.rows("categories").forEach { element -> element.jsonArray.let { values ->
                queries.restoreCategory(
                    values.text(0), values.text(1), values.nullableText(2), values.text(3), values.nullableText(4),
                    values.text(5), values.getOrNull(9)?.jsonPrimitive?.longOrNull ?: 0L,
                    values.long(6), values.long(7), values.nullableLong(8),
                )
            } }
            root.rows("tags").forEach { element -> element.jsonArray.let { values ->
                queries.restoreTag(
                    values.text(0), values.text(1), values.text(2), values.long(3), values.long(4), values.nullableLong(5),
                )
            } }
            root.rows("rules").forEach { element -> element.jsonArray.let { values ->
                queries.restoreRule(
                    values.text(0), values.text(1), values.text(2), values.text(3), values.text(4), values.text(5),
                    values.text(6), values.long(7), values.long(8), values.long(9), values.nullableLong(10),
                )
            } }
            root.rows("transactions").forEach { element -> element.jsonArray.let { values ->
                queries.restoreTransaction(
                    values.text(0), values.text(1), values.text(2), values.text(3), values.long(4), values.text(5),
                    values.long(6), values.nullableText(7), values.long(8), values.nullableText(9), values.nullableText(10),
                    values.long(11), values.long(12), values.nullableLong(13),
                )
            } }
            root.rows("transactionTags").forEach { element -> element.jsonArray.let { values ->
                queries.restoreTransactionTag(values.text(0), values.text(1))
            } }
            root.rows("categoryMemories").forEach { element -> element.jsonArray.let { values ->
                queries.restoreCategoryMemory(values.text(0), values.text(1), values.text(2), values.long(3))
            } }
            root.rows("reminders").forEach { element -> element.jsonArray.let { values ->
                queries.restoreReminder(
                    values.text(0), values.text(1), values.text(2), values.nullableLong(3), values.text(4), values.text(5),
                    values.long(6), values.long(7), values.long(8), values.nullableLong(9),
                )
            } }
            root.rows("appPreferences").forEach { element -> element.jsonArray.let { values ->
                queries.restoreAppPreference(values.text(0), values.text(1), values.long(2))
            } }
        }
    }
}

private fun row(vararg values: Any?): JsonArray = JsonArray(values.map { value -> when (value) {
    null -> JsonNull
    is String -> JsonPrimitive(value)
    is Long -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    else -> error("不支持的备份字段类型")
} })

private fun JsonObject.rows(name: String): JsonArray = this[name]?.jsonArray ?: JsonArray(emptyList())
private fun JsonArray.text(index: Int): String = get(index).jsonPrimitive.content
private fun JsonArray.long(index: Int): Long = text(index).toLong()
private fun JsonArray.nullableText(index: Int): String? = get(index).takeUnless { it is JsonNull }?.jsonPrimitive?.content
private fun JsonArray.nullableLong(index: Int): Long? = nullableText(index)?.toLong()

private val REQUIRED_SECTIONS = listOf(
    "ledgers",
    "accounts",
    "accountBalanceRecords",
    "categories",
    "tags",
    "transactionTags",
    "transactions",
    "rules",
    "categoryMemories",
    "reminders",
    "appPreferences",
)
