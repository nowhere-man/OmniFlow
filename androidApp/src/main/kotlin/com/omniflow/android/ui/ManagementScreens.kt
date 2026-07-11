package com.omniflow.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omniflow.shared.domain.model.Account
import com.omniflow.shared.domain.model.AccountType
import com.omniflow.shared.domain.model.Category
import com.omniflow.shared.domain.model.Money
import com.omniflow.shared.domain.model.Reminder
import com.omniflow.shared.domain.model.ReminderSchedule
import com.omniflow.shared.domain.model.ReminderScheduleKind
import com.omniflow.shared.domain.model.ReminderType
import com.omniflow.shared.domain.model.Rule
import com.omniflow.shared.domain.model.RuleActionType
import com.omniflow.shared.domain.model.RuleConditionType
import com.omniflow.shared.domain.model.Tag
import com.omniflow.shared.domain.model.TransactionType

@Composable
internal fun ManagementPage(page: MorePage, state: MoreUiState, viewModel: OmniFlowViewModel, onPage: (MorePage) -> Unit) {
    when (page) {
        MorePage.LEDGERS -> LedgerManagement(state, viewModel)
        MorePage.ACCOUNTS -> AccountManagement(state, viewModel)
        MorePage.ASSETS -> AssetManagement(state) { onPage(MorePage.ACCOUNTS) }
        MorePage.CATEGORIES -> CategoryManagement(state, viewModel)
        MorePage.TAGS -> TagManagement(state, viewModel)
        MorePage.RULES -> RuleManagement(state, viewModel)
        MorePage.REMINDERS -> ReminderManagement(state, viewModel)
        else -> Unit
    }
}

@Composable
private fun LedgerManagement(state: MoreUiState, viewModel: OmniFlowViewModel) {
    var editing by remember { mutableStateOf<com.omniflow.shared.domain.model.Ledger?>(null) }
    var showNew by remember { mutableStateOf(false) }
    ManagementList(
        addLabel = "新建账本",
        onAdd = { showNew = true },
        error = state.error,
    ) {
        items(state.ledgers, key = { it.id }) { ledger ->
            ManagementRow(
                title = ledger.name,
                subtitle = if (state.defaultLedgerId == ledger.id) "默认账本" else "",
                onEdit = { editing = ledger },
                onDelete = { viewModel.deleteLedger(ledger.id) },
            ) {
                Checkbox(
                    checked = state.defaultLedgerId == ledger.id,
                    onCheckedChange = { checked -> viewModel.setDefaultLedger(if (checked) ledger.id else null) },
                )
            }
        }
    }
    if (showNew || editing != null) {
        LedgerDialog(editing, onDismiss = { showNew = false; editing = null }) { name, cover ->
            viewModel.saveLedger(editing?.id, name, cover)
            showNew = false
            editing = null
        }
    }
}

@Composable
private fun LedgerDialog(
    ledger: com.omniflow.shared.domain.model.Ledger?,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit,
) {
    var name by remember(ledger) { mutableStateOf(ledger?.name.orEmpty()) }
    var cover by remember(ledger) { mutableStateOf(ledger?.coverKey.orEmpty()) }
    FormDialog(if (ledger == null) "新建账本" else "编辑账本", onDismiss, { onSave(name, cover.ifBlank { null }) }) {
        OutlinedTextField(name, { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(cover, { cover = it }, label = { Text("封面标识（可选）") }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AccountManagement(state: MoreUiState, viewModel: OmniFlowViewModel) {
    var editing by remember { mutableStateOf<Account?>(null) }
    var showNew by remember { mutableStateOf(false) }
    ManagementList("新建账户", { showNew = true }, state.error) {
        items(state.accounts, key = { it.id }) { account ->
            ManagementRow(
                title = account.name,
                subtitle = "${account.type.label} · ${account.balance.asRmb()}",
                onEdit = { editing = account },
                onDelete = { viewModel.deleteAccount(account.id) },
            )
        }
    }
    if (showNew || editing != null) {
        AccountDialog(editing, onDismiss = { showNew = false; editing = null }) { name, type, icon, card, note, balance, included ->
            viewModel.saveAccount(editing?.id, name, type, icon, card, note, balance, included)
            if (editing != null && balance != editing!!.balance) viewModel.calibrateAccount(editing!!.id, balance)
            showNew = false
            editing = null
        }
    }
}

@Composable
private fun AccountDialog(
    account: Account?,
    onDismiss: () -> Unit,
    onSave: (String, AccountType, String, String?, String?, Money, Boolean) -> Unit,
) {
    var name by remember(account) { mutableStateOf(account?.name.orEmpty()) }
    var type by remember(account) { mutableStateOf(account?.type ?: AccountType.CASH) }
    var icon by remember(account) { mutableStateOf(account?.iconKey ?: "wallet-cards") }
    var card by remember(account) { mutableStateOf(account?.cardNumber.orEmpty()) }
    var note by remember(account) { mutableStateOf(account?.note.orEmpty()) }
    var balance by remember(account) { mutableStateOf(account?.balance?.toDecimal().orEmpty()) }
    var included by remember(account) { mutableStateOf(account?.includeInTotalAssets ?: true) }
    FormDialog(if (account == null) "新建账户" else "编辑账户", onDismiss, {
        onSave(name, type, icon, card.ifBlank { null }, note.ifBlank { null }, balance.toMoney(), included)
    }) {
        OutlinedTextField(name, { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
        ValueMenu(type.label, AccountType.entries, { it.label }) { type = it }
        ValueMenu(icon, BundledIconKeys, { it }) { icon = it }
        OutlinedTextField(card, { card = it }, label = { Text("卡号（可选）") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(balance, { balance = it }, label = { Text("当前余额") }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("计入总资产", modifier = Modifier.weight(1f))
            Switch(included, { included = it })
        }
    }
}

@Composable
private fun AssetManagement(state: MoreUiState, onManageAccounts: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("净资产 ${state.accountSummary.netAssets.asRmb()}", style = MaterialTheme.typography.headlineSmall)
                    Text("资产 ${state.accountSummary.assets.asRmb()}")
                    Text("负债 ${state.accountSummary.liabilities.asRmb()}")
                }
            }
        }
        item { Button(onClick = onManageAccounts, modifier = Modifier.fillMaxWidth()) { Text("管理账户") } }
        items(state.accounts.filter { it.includeInTotalAssets }, key = { it.id }) { account ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(account.name, fontWeight = FontWeight.Medium)
                    Text(account.balance.asRmb())
                }
            }
        }
    }
}

@Composable
private fun CategoryManagement(state: MoreUiState, viewModel: OmniFlowViewModel) {
    var editing by remember { mutableStateOf<Category?>(null) }
    var showNew by remember { mutableStateOf(false) }
    LedgerScopedHeader(state, viewModel)
    ManagementList("新建分类", { showNew = true }, state.error) {
        items(state.categories, key = { it.id }) { category ->
            val parent = state.categories.firstOrNull { it.id == category.parentId }
            ManagementRow(
                title = category.name,
                subtitle = listOfNotNull(parent?.name, category.type.label).joinToString(" · "),
                onEdit = { editing = category },
                onDelete = { viewModel.deleteCategory(category.id) },
            )
        }
    }
    val ledgerId = state.selectedLedgerId
    if ((showNew || editing != null) && ledgerId != null) {
        CategoryDialog(editing, ledgerId, state.categories, onDismiss = { showNew = false; editing = null }) { parent, name, icon, type ->
            viewModel.saveCategory(editing?.id, ledgerId, parent, name, icon, type)
            showNew = false
            editing = null
        }
    }
}

@Composable
private fun CategoryDialog(
    category: Category?,
    ledgerId: String,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (String?, String, String?, TransactionType) -> Unit,
) {
    var type by remember(category) { mutableStateOf(category?.type ?: TransactionType.EXPENSE) }
    var parentId by remember(category) { mutableStateOf(category?.parentId) }
    var name by remember(category) { mutableStateOf(category?.name.orEmpty()) }
    var icon by remember(category) { mutableStateOf(category?.iconKey ?: CategoryIconOptions.first().key) }
    var showIconPicker by remember(category) { mutableStateOf(false) }
    val parents = categories.filter { it.ledgerId == ledgerId && it.parentId == null && it.type == type && it.id != category?.id }
    FormDialog(if (category == null) "新建分类" else "编辑分类", onDismiss, {
        onSave(parentId, name, if (parentId == null) icon else null, type)
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransactionType.entries.forEach { value ->
                FilterChip(selected = type == value, onClick = { type = value; parentId = null }, label = { Text(value.label) })
            }
        }
        NullableValueMenu(
            label = parents.firstOrNull { it.id == parentId }?.name ?: "一级分类",
            allLabel = "创建一级分类",
            values = parents,
            valueLabel = Category::name,
            onAll = { parentId = null },
            onSelected = { parentId = it.id },
        )
        OutlinedTextField(name, { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
        if (parentId == null) {
            CategoryIconButton(icon) { showIconPicker = true }
        }
    }
    if (showIconPicker) {
        CategoryIconPickerDialog(
            selectedKey = icon,
            onDismiss = { showIconPicker = false },
            onSelected = {
                icon = it
                showIconPicker = false
            },
        )
    }
}

@Composable
private fun CategoryIconButton(selectedKey: String, onClick: () -> Unit) {
    val option = CategoryIconOptions.firstOrNull { it.key == selectedKey }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            SvgIcon(
                categoryIconKey(selectedKey),
                Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("分类图标", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(option?.label ?: "默认图标", fontWeight = FontWeight.SemiBold)
            }
            Text("更换", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CategoryIconPickerDialog(
    selectedKey: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择分类图标") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.fillMaxWidth().height(420.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                gridItems(CategoryIconOptions, key = CategoryIconOption::key) { option ->
                    val selected = option.key == selectedKey
                    Surface(
                        onClick = { onSelected(option.key) },
                        modifier = Modifier.height(66.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            SvgIcon(
                                categoryIconKey(option.key),
                                Modifier.size(34.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                option.label,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun TagManagement(state: MoreUiState, viewModel: OmniFlowViewModel) {
    var editing by remember { mutableStateOf<Tag?>(null) }
    var showNew by remember { mutableStateOf(false) }
    LedgerScopedHeader(state, viewModel)
    ManagementList("新建标签", { showNew = true }, state.error) {
        items(state.tags, key = { it.id }) { tag ->
            ManagementRow(tag.name, "", { editing = tag }, { viewModel.deleteTag(tag.id) })
        }
    }
    val ledgerId = state.selectedLedgerId
    if ((showNew || editing != null) && ledgerId != null) {
        NameDialog(if (editing == null) "新建标签" else "编辑标签", editing?.name.orEmpty(), { showNew = false; editing = null }) { name ->
            viewModel.saveTag(editing?.id, ledgerId, name)
            showNew = false
            editing = null
        }
    }
}

@Composable
private fun RuleManagement(state: MoreUiState, viewModel: OmniFlowViewModel) {
    var editing by remember { mutableStateOf<Rule?>(null) }
    var showNew by remember { mutableStateOf(false) }
    LedgerScopedHeader(state, viewModel)
    val orderedRules = state.rules.sortedBy(Rule::priority)
    ManagementList("新建规则", { showNew = true }, state.error) {
        itemsIndexed(orderedRules, key = { _, rule -> rule.id }) { index, rule ->
            ManagementRow(
                title = "${rule.priority}. ${rule.name}",
                subtitle = "${rule.conditionType.label}: ${rule.conditionValue} → ${rule.actionType.label}",
                onEdit = { editing = rule },
                onDelete = { viewModel.deleteRule(rule.id) },
            ) {
                Column {
                    TextButton(onClick = { viewModel.moveRule(rule.id, -1) }, enabled = index > 0) { Text("上移") }
                    TextButton(onClick = { viewModel.moveRule(rule.id, 1) }, enabled = index < orderedRules.lastIndex) { Text("下移") }
                }
            }
        }
    }
    val ledgerId = state.selectedLedgerId
    if ((showNew || editing != null) && ledgerId != null) {
        RuleDialog(editing, state, onDismiss = { showNew = false; editing = null }) { name, condition, conditionValue, action, actionValue, priority ->
            viewModel.saveRule(editing?.id, ledgerId, name, condition, conditionValue, action, actionValue, priority)
            showNew = false
            editing = null
        }
    }
}

@Composable
private fun RuleDialog(
    rule: Rule?,
    state: MoreUiState,
    onDismiss: () -> Unit,
    onSave: (String, RuleConditionType, String, RuleActionType, String, Int) -> Unit,
) {
    var name by remember(rule) { mutableStateOf(rule?.name.orEmpty()) }
    var condition by remember(rule) { mutableStateOf(rule?.conditionType ?: RuleConditionType.NOTE_CONTAINS) }
    var conditionValue by remember(rule) { mutableStateOf(rule?.conditionValue.orEmpty()) }
    var action by remember(rule) { mutableStateOf(rule?.actionType ?: RuleActionType.SET_CATEGORY) }
    var actionValue by remember(rule) { mutableStateOf(rule?.actionValue.orEmpty()) }
    var priority by remember(rule) { mutableStateOf((rule?.priority ?: state.rules.size).toString()) }
    FormDialog(if (rule == null) "新建规则" else "编辑规则", onDismiss, {
        onSave(name, condition, conditionValue, action, actionValue, priority.toIntOrNull() ?: 0)
    }) {
        OutlinedTextField(name, { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
        ValueMenu(condition.label, RuleConditionType.entries, { it.label }) { condition = it }
        if (condition == RuleConditionType.TRANSACTION_TYPE) {
            ValueMenu(
                TransactionType.entries.firstOrNull { it.name == conditionValue }?.label ?: "选择收支类型",
                TransactionType.entries,
                { it.label },
            ) { conditionValue = it.name }
        } else {
            OutlinedTextField(conditionValue, { conditionValue = it }, label = { Text("匹配值") }, modifier = Modifier.fillMaxWidth())
        }
        ValueMenu(action.label, RuleActionType.entries, { it.label }) { selected -> action = selected; actionValue = "" }
        if (action == RuleActionType.SET_CATEGORY) {
            ValueMenu(
                state.categories.firstOrNull { it.id == actionValue }?.name ?: "选择分类",
                state.categories,
                Category::name,
            ) { actionValue = it.id }
        }
        OutlinedTextField(priority, { priority = it.filter(Char::isDigit) }, label = { Text("优先级") }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ReminderManagement(state: MoreUiState, viewModel: OmniFlowViewModel) {
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var showNew by remember { mutableStateOf(false) }
    ManagementList("新建提醒", { showNew = true }, state.error) {
        items(state.reminders, key = { it.id }) { reminder ->
            ManagementRow(
                title = reminder.name,
                subtitle = "${reminder.type.label} · ${reminder.schedule.kind.label}",
                onEdit = { editing = reminder },
                onDelete = { viewModel.deleteReminder(reminder.id) },
            ) {
                Switch(!reminder.paused, { enabled -> viewModel.setReminderPaused(reminder, !enabled) })
            }
        }
    }
    if (showNew || editing != null) {
        ReminderDialog(editing, onDismiss = { showNew = false; editing = null }) { type, name, amount, schedule, paused ->
            viewModel.saveReminder(editing?.id, type, name, amount, schedule, paused)
            showNew = false
            editing = null
        }
    }
}

@Composable
private fun ReminderDialog(
    reminder: Reminder?,
    onDismiss: () -> Unit,
    onSave: (ReminderType, String, Money?, ReminderSchedule, Boolean) -> Unit,
) {
    var type by remember(reminder) { mutableStateOf(reminder?.type ?: ReminderType.REPAYMENT) }
    var name by remember(reminder) { mutableStateOf(reminder?.name.orEmpty()) }
    var amount by remember(reminder) { mutableStateOf(reminder?.amount?.toDecimal().orEmpty()) }
    var kind by remember(reminder) { mutableStateOf(reminder?.schedule?.kind ?: ReminderScheduleKind.FIXED_REPAYMENT_DAY) }
    var day by remember(reminder) { mutableStateOf((reminder?.schedule?.dayOfMonth ?: 1).toString()) }
    var daysAfter by remember(reminder) { mutableStateOf((reminder?.schedule?.daysAfter ?: 0).toString()) }
    var weekday by remember(reminder) { mutableStateOf((reminder?.schedule?.dayOfWeek ?: 1).toString()) }
    var month by remember(reminder) { mutableStateOf((reminder?.schedule?.month ?: 1).toString()) }
    var paused by remember(reminder) { mutableStateOf(reminder?.paused ?: false) }
    val kinds = if (type == ReminderType.REPAYMENT) {
        listOf(ReminderScheduleKind.FIXED_REPAYMENT_DAY, ReminderScheduleKind.DAYS_AFTER_STATEMENT)
    } else {
        listOf(ReminderScheduleKind.DAILY, ReminderScheduleKind.WEEKLY, ReminderScheduleKind.MONTHLY, ReminderScheduleKind.YEARLY)
    }
    FormDialog(if (reminder == null) "新建提醒" else "编辑提醒", onDismiss, {
        onSave(
            type,
            name,
            amount.takeIf(String::isNotBlank)?.toMoney(),
            ReminderSchedule(
                kind = kind,
                dayOfMonth = day.toIntOrNull(),
                daysAfter = daysAfter.toIntOrNull(),
                dayOfWeek = weekday.toIntOrNull(),
                month = month.toIntOrNull(),
            ),
            paused,
        )
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReminderType.entries.forEach { value ->
                FilterChip(
                    selected = type == value,
                    onClick = { type = value; kind = if (value == ReminderType.REPAYMENT) ReminderScheduleKind.FIXED_REPAYMENT_DAY else ReminderScheduleKind.MONTHLY },
                    label = { Text(value.label) },
                )
            }
        }
        OutlinedTextField(name, { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(amount, { amount = it }, label = { Text("金额（可选）") }, modifier = Modifier.fillMaxWidth())
        ValueMenu(kind.label, kinds, { it.label }) { kind = it }
        when (kind) {
            ReminderScheduleKind.FIXED_REPAYMENT_DAY,
            ReminderScheduleKind.MONTHLY -> NumberField("每月几号", day) { day = it }
            ReminderScheduleKind.DAYS_AFTER_STATEMENT -> {
                NumberField("账单日", day) { day = it }
                NumberField("账单日后天数", daysAfter) { daysAfter = it }
            }
            ReminderScheduleKind.WEEKLY -> NumberField("星期（1-7）", weekday) { weekday = it }
            ReminderScheduleKind.YEARLY -> {
                NumberField("月份", month) { month = it }
                NumberField("日期", day) { day = it }
            }
            ReminderScheduleKind.DAILY -> Unit
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("暂停", modifier = Modifier.weight(1f))
            Switch(paused, { paused = it })
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(value, { onValue(it.filter(Char::isDigit)) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun LedgerScopedHeader(state: MoreUiState, viewModel: OmniFlowViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        ValueMenu(
            label = state.ledgers.firstOrNull { it.id == state.selectedLedgerId }?.name ?: "选择账本",
            values = state.ledgers,
            valueLabel = { it.name },
        ) { viewModel.selectMoreLedger(it.id) }
    }
}

@Composable
private fun ManagementList(
    addLabel: String,
    onAdd: () -> Unit,
    error: String?,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text(addLabel) } }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        content()
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ManagementRow(
    title: String,
    subtitle: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            trailing?.invoke()
            TextButton(onClick = onEdit) { Text("编辑") }
            TextButton(onClick = onDelete) { Text("删除") }
        }
    }
}

@Composable
private fun FormDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content) },
        confirmButton = { TextButton(onClick = onSave) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial) }
    FormDialog(title, onDismiss, { onSave(name) }) {
        OutlinedTextField(name, { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
    }
}

private val AccountType.label: String
    get() = when (this) {
        AccountType.CASH -> "现金"
        AccountType.DEBIT_CARD -> "储蓄卡"
        AccountType.CREDIT_CARD -> "信用卡"
        AccountType.E_WALLET -> "电子钱包"
        AccountType.INVESTMENT -> "投资账户"
    }

private val TransactionType.label: String get() = if (this == TransactionType.EXPENSE) "支出" else "收入"
private val ReminderType.label: String get() = if (this == ReminderType.REPAYMENT) "还款提醒" else "订阅提醒"
private val ReminderScheduleKind.label: String
    get() = when (this) {
        ReminderScheduleKind.FIXED_REPAYMENT_DAY -> "固定还款日"
        ReminderScheduleKind.DAYS_AFTER_STATEMENT -> "账单日后 N 天"
        ReminderScheduleKind.DAILY -> "每天"
        ReminderScheduleKind.WEEKLY -> "每周"
        ReminderScheduleKind.MONTHLY -> "每月"
        ReminderScheduleKind.YEARLY -> "每年"
    }

private val RuleConditionType.label: String
    get() = when (this) {
        RuleConditionType.NOTE_CONTAINS -> "备注包含"
        RuleConditionType.TRANSACTION_TYPE -> "收支类型"
        RuleConditionType.TRANSACTION_SOURCE -> "来源平台"
    }

private val RuleActionType.label: String
    get() = when (this) {
        RuleActionType.SET_CATEGORY -> "设置分类"
        RuleActionType.SET_EXCLUDED -> "设为不计入收支"
        RuleActionType.EXCLUDE -> "排除不入账"
    }

private fun Money.toDecimal(): String = "${minor / 100}.${kotlin.math.abs(minor % 100).toString().padStart(2, '0')}"

private fun String.toMoney(): Money {
    val parts = trim().ifBlank { "0" }.split('.', limit = 2)
    val yuan = parts.firstOrNull()?.toLongOrNull() ?: 0L
    val cents = parts.getOrElse(1) { "" }.take(2).padEnd(2, '0').toLongOrNull() ?: 0L
    return Money(yuan * 100 + if (yuan < 0) -cents else cents)
}

private val BundledIconKeys = listOf(
    "banknote", "wallet-cards", "wallet", "landmark", "shopping-bag", "utensils",
    "bus", "wrench", "film", "heart-pulse", "plane", "car", "house", "smartphone",
    "shirt", "chart-line", "briefcase-business", "trophy", "gift", "play", "category",
)
