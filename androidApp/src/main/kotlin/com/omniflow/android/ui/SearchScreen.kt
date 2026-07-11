package com.omniflow.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omniflow.shared.domain.model.Account
import com.omniflow.shared.domain.model.Category
import com.omniflow.shared.domain.model.Ledger
import com.omniflow.shared.domain.model.LedgerScope
import com.omniflow.shared.domain.model.Tag
import com.omniflow.shared.domain.model.TransactionType

@Composable
internal fun SearchScreen(
    state: SearchUiState,
    onKeyword: (String) -> Unit,
    onScope: (LedgerScope) -> Unit,
    onType: (TransactionType?) -> Unit,
    onPrimaryCategory: (String?) -> Unit,
    onSecondaryCategory: (String?) -> Unit,
    onTag: (String?) -> Unit,
    onAccount: (String?) -> Unit,
    onClear: () -> Unit,
    onEditTransaction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryCategories = state.categories.filter { it.parentId == null }
    val secondaryCategories = state.categories.filter { category ->
        category.parentId != null && (state.query.primaryCategoryId == null || category.parentId == state.query.primaryCategoryId)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item {
            OutlinedTextField(
                value = state.query.keyword,
                onValueChange = onKeyword,
                placeholder = { Text("关键词、分类、账户或标签") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (state.query.keyword.isBlank()) null else {
                    { IconButton(onClick = { onKeyword("") }) { Icon(Icons.Default.Close, contentDescription = "清除关键词") } }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                singleLine = true,
            )
        }
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("筛选", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        if (state.query.hasFilters) TextButton(onClick = onClear) { Text("清除") }
                    }
                    SearchTypeSelector(state.query.type, onType)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ValueMenu(
                            label = when (val scope = state.query.scope) {
                                LedgerScope.All -> "所有账本"
                                is LedgerScope.Single -> state.ledgers.firstOrNull { it.id == scope.ledgerId }?.name ?: "账本"
                            },
                            allLabel = "所有账本",
                            values = state.ledgers,
                            valueLabel = Ledger::name,
                            onAll = { onScope(LedgerScope.All) },
                            onSelected = { onScope(LedgerScope.Single(it.id)) },
                            modifier = Modifier.weight(1f),
                        )
                        ValueMenu(
                            label = state.accounts.firstOrNull { it.id == state.query.accountId }?.name ?: "所有账户",
                            allLabel = "所有账户",
                            values = state.accounts,
                            valueLabel = Account::name,
                            onAll = { onAccount(null) },
                            onSelected = { onAccount(it.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ValueMenu(
                            label = primaryCategories.firstOrNull { it.id == state.query.primaryCategoryId }?.name ?: "所有一级分类",
                            allLabel = "所有一级分类",
                            values = primaryCategories,
                            valueLabel = Category::name,
                            onAll = { onPrimaryCategory(null) },
                            onSelected = { onPrimaryCategory(it.id) },
                            modifier = Modifier.weight(1f),
                        )
                        ValueMenu(
                            label = secondaryCategories.firstOrNull { it.id == state.query.secondaryCategoryId }?.name ?: "所有二级分类",
                            allLabel = "所有二级分类",
                            values = secondaryCategories,
                            valueLabel = Category::name,
                            onAll = { onSecondaryCategory(null) },
                            onSelected = { onSecondaryCategory(it.id) },
                            modifier = Modifier.weight(1f),
                        )
                        ValueMenu(
                            label = state.tags.firstOrNull { it.id == state.query.tagId }?.name ?: "所有标签",
                            allLabel = "所有标签",
                            values = state.tags,
                            valueLabel = Tag::name,
                            onAll = { onTag(null) },
                            onSelected = { onTag(it.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        if (state.isLoading) item { Text("搜索中…", Modifier.fillMaxWidth().padding(24.dp)) }
        if (!state.isLoading && state.error == null && state.result == null) {
            item { SearchEmptyState(hasFilters = false, onClear = onClear) }
        }
        state.result?.let { result ->
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("收入 ${result.summary.incomeTotal.asRmb()}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text("支出 ${result.summary.expenseTotal.asRmb()}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (result.items.isEmpty()) item {
                SearchEmptyState(
                    hasFilters = state.query.hasFilters,
                    onClear = onClear,
                )
            }
            items(result.items, key = { it.transaction.id }) { item ->
                Card(
                    Modifier.fillMaxWidth().clickable { onEditTransaction(item.transaction.id) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                SvgIcon(
                                    categoryIconKey(item.transaction.categoryIconKey),
                                    Modifier.size(26.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(item.transaction.categoryDisplayName, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${item.transaction.ledgerName} · ${item.transaction.accountName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            item.transaction.note?.takeIf(String::isNotBlank)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (item.tags.isNotEmpty()) {
                                Text(
                                    item.tags.joinToString(" · ") { it.name },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            item.transaction.amount.asRmb(),
                            fontWeight = FontWeight.Bold,
                            color = if (item.transaction.type == TransactionType.EXPENSE) {
                                MaterialTheme.colorScheme.error
                            } else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SearchEmptyState(
    hasFilters: Boolean,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (hasFilters) "没有符合条件的交易" else "还没有可搜索的交易", fontWeight = FontWeight.SemiBold)
        if (hasFilters) TextButton(onClick = onClear) { Text("清除筛选") }
    }
}

@Composable
private fun SearchTypeSelector(selected: TransactionType?, onSelected: (TransactionType?) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(null, TransactionType.EXPENSE, TransactionType.INCOME).forEach { type ->
                val active = selected == type
                val label = when (type) {
                    null -> "全部"
                    TransactionType.EXPENSE -> "支出"
                    TransactionType.INCOME -> "收入"
                }
                Surface(
                    onClick = { onSelected(type) },
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (active) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            textAlign = TextAlign.Center,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = when {
                                !active -> MaterialTheme.colorScheme.onSurfaceVariant
                                type == TransactionType.EXPENSE -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> ValueMenu(
    label: String,
    allLabel: String,
    values: List<T>,
    valueLabel: (T) -> String,
    onAll: () -> Unit,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(13.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(allLabel) }, onClick = { expanded = false; onAll() })
            values.forEach { value ->
                DropdownMenuItem(text = { Text(valueLabel(value)) }, onClick = { expanded = false; onSelected(value) })
            }
        }
    }
}
