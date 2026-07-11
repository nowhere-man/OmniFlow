package com.omniflow.shared.data.repository

import com.omniflow.shared.db.OmniFlowDatabase
import com.omniflow.shared.domain.model.Category
import com.omniflow.shared.domain.model.CategoryId
import com.omniflow.shared.domain.model.TransactionType
import com.omniflow.shared.domain.repository.CategoryRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class SqlDelightCategoryRepository(
    private val database: OmniFlowDatabase,
    private val now: () -> Instant = { Clock.System.now() },
) : CategoryRepository {
    override suspend fun activeCategories(ledgerId: String): List<Category> = database.categoryQueries
        .activeCategoriesForLedger(ledgerId)
        .executeAsList()
        .map {
            Category(
                id = it.id,
                ledgerId = it.ledger_id,
                parentId = it.parent_id,
                name = it.name,
                iconKey = it.icon_key,
                type = TransactionType.valueOf(it.type),
                deletedAt = it.deleted_at?.let(Instant::fromEpochMilliseconds),
            )
        }

    override suspend fun create(category: Category) {
        validateCategory(category)
        val timestamp = now().toEpochMilliseconds()
        database.categoryQueries.insertCategory(
            id = category.id,
            ledger_id = category.ledgerId,
            parent_id = category.parentId,
            name = category.name.trim(),
            icon_key = category.iconKey?.trim()?.takeIf(String::isNotEmpty),
            type = category.type.name,
            created_at = timestamp,
            updated_at = timestamp,
        )
    }

    override suspend fun update(category: Category) {
        validateCategory(category)
        require(database.categoryQueries.activeCategoryId(category.id).executeAsOneOrNull() != null) {
            "分类不存在或已删除"
        }
        database.categoryQueries.updateCategory(
            name = category.name.trim(),
            icon_key = category.iconKey?.trim()?.takeIf(String::isNotEmpty),
            updated_at = now().toEpochMilliseconds(),
            id = category.id,
            ledger_id = category.ledgerId,
            type = category.type.name,
        )
    }

    override suspend fun reorderPrimary(
        ledgerId: String,
        type: TransactionType,
        categoryIds: List<CategoryId>,
    ) {
        val activeIds = activeCategories(ledgerId)
            .filter { it.parentId == null && it.type == type }
            .map(Category::id)
        require(categoryIds.size == activeIds.size && categoryIds.toSet() == activeIds.toSet()) {
            "分类顺序与当前一级分类不一致"
        }
        val timestamp = now().toEpochMilliseconds()
        database.transaction {
            categoryIds.forEachIndexed { index, id ->
                database.categoryQueries.updatePrimaryCategorySortOrder(
                    sort_order = index.toLong(),
                    updated_at = timestamp,
                    id = id,
                    ledger_id = ledgerId,
                    type = type.name,
                )
            }
        }
    }

    override suspend fun archive(categoryId: CategoryId) {
        require(database.categoryQueries.activeCategoryId(categoryId).executeAsOneOrNull() != null) {
            "分类不存在或已删除"
        }
        require(database.categoryQueries.activeChildCategoryId(categoryId).executeAsOneOrNull() == null) {
            "一级分类下仍有二级分类"
        }
        val timestamp = now().toEpochMilliseconds()
        database.transaction {
            database.ruleQueries.archiveRulesForCategory(timestamp, categoryId)
            database.categoryMemoryQueries.deleteCategoryMemoriesForCategory(categoryId)
            database.categoryQueries.archiveCategory(timestamp, categoryId)
        }
    }

    private fun validateCategory(category: Category) {
        require(category.name.isNotBlank()) { "分类名称不能为空" }
        require(database.ledgerQueries.activeLedgerId(category.ledgerId).executeAsOneOrNull() != null) {
            "账本不存在或已删除"
        }
        if (category.parentId == null) {
            require(!category.iconKey.isNullOrBlank()) { "一级分类必须选择图标" }
            return
        }

        require(database.categoryQueries.activePrimaryCategoryTypeForLedger(
            id = category.parentId,
            ledger_id = category.ledgerId,
        ).executeAsOneOrNull() == category.type.name) {
            "二级分类的一级分类不存在或交易类型不一致"
        }
    }
}
