package com.omniflow.shared.domain.model

data class AmountFilter(
    val exact: Money? = null,
    val minimum: Money? = null,
    val maximum: Money? = null,
) {
    init {
        require(exact == null || (minimum == null && maximum == null)) {
            "精确金额不能与金额范围同时使用"
        }
        require(minimum == null || maximum == null || minimum <= maximum) {
            "最小金额不能大于最大金额"
        }
    }
}

data class TransactionSearchQuery(
    val scope: LedgerScope = LedgerScope.All,
    val keyword: String = "",
    val primaryCategoryText: String = "",
    val secondaryCategoryText: String = "",
    val tagText: String = "",
    val noteText: String = "",
    val type: TransactionType? = null,
    val primaryCategoryId: CategoryId? = null,
    val secondaryCategoryId: CategoryId? = null,
    val tagId: TagId? = null,
    val accountId: AccountId? = null,
    val amount: AmountFilter = AmountFilter(),
    val dateRange: DateRange? = null,
) {
    val hasFilters: Boolean
        get() = keyword.isNotBlank() || primaryCategoryText.isNotBlank() ||
            secondaryCategoryText.isNotBlank() || tagText.isNotBlank() || noteText.isNotBlank() ||
            type != null || primaryCategoryId != null ||
            secondaryCategoryId != null || tagId != null || accountId != null ||
            amount.exact != null || amount.minimum != null || amount.maximum != null || dateRange != null
}

data class TransactionTag(
    val id: TagId,
    val name: String,
)

data class SearchTransactionItem(
    val transaction: TransactionListItem,
    val primaryCategoryId: CategoryId,
    val primaryCategoryName: String,
    val tags: List<TransactionTag>,
)

data class SearchResult(
    val items: List<SearchTransactionItem>,
    val summary: TransactionSummary,
)
