package com.omniflow.android.ui

import android.content.Context
import android.app.DatePickerDialog
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.omniflow.shared.domain.model.AppearanceMode
import com.omniflow.shared.domain.model.DateRange
import com.omniflow.shared.domain.model.ImportDuplicateStatus
import com.omniflow.shared.domain.model.ImportPreviewItem
import com.omniflow.shared.domain.model.ImportPreviewPhase
import com.omniflow.shared.domain.model.Ledger
import com.omniflow.shared.domain.model.Money
import com.omniflow.shared.domain.model.SyncPhase
import com.omniflow.shared.domain.model.SyncTarget
import com.omniflow.shared.domain.model.ThemeColor
import com.omniflow.shared.domain.model.TransactionType
import com.omniflow.shared.parser.ImportFormat
import com.omniflow.android.WebDavCredentials
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

internal enum class MorePage(val label: String, val icon: ImageVector) {
    HOME("更多", Icons.Default.Settings),
    SETTINGS("设置", Icons.Default.Settings),
    DATA("数据管理", Icons.Default.CloudSync),
    IMPORT("导入", Icons.Default.FileUpload),
    EXPORT("导出", Icons.Default.FileDownload),
    RULES("规则", Icons.AutoMirrored.Filled.Rule),
    REMINDERS("提醒", Icons.Default.Notifications),
    LEDGERS("账本", Icons.Default.AccountBalance),
    ACCOUNTS("账户", Icons.Default.Wallet),
    ASSETS("资产", Icons.Default.Savings),
    CATEGORIES("分类管理", Icons.Default.Category),
    TAGS("标签管理", Icons.AutoMirrored.Filled.Label),
}

@Composable
internal fun MoreScreen(
    state: MoreUiState,
    viewModel: OmniFlowViewModel,
    initialPage: MorePage = MorePage.HOME,
    modifier: Modifier = Modifier,
) {
    var pageStack by remember(initialPage) {
        mutableStateOf(if (initialPage == MorePage.HOME) listOf(MorePage.HOME) else listOf(MorePage.HOME, initialPage))
    }
    val page = pageStack.last()
    fun navigate(next: MorePage) {
        if (next != page) pageStack = pageStack + next
    }
    fun navigateBack() {
        if (pageStack.size > 1) pageStack = pageStack.dropLast(1)
    }
    BackHandler(enabled = page != MorePage.HOME, onBack = ::navigateBack)
    Column(modifier.fillMaxSize()) {
        if (page != MorePage.HOME) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = ::navigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(page.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        when (page) {
            MorePage.HOME -> MoreHome(state, onPage = ::navigate)
            MorePage.SETTINGS -> SettingsPage(state, viewModel) { navigate(MorePage.DATA) }
            MorePage.DATA -> DataManagementPage(state, viewModel, onPage = ::navigate)
            MorePage.IMPORT -> ImportPage(state, viewModel)
            MorePage.EXPORT -> ExportPage(state, viewModel)
            MorePage.RULES,
            MorePage.REMINDERS,
            MorePage.LEDGERS,
            MorePage.ACCOUNTS,
            MorePage.ASSETS,
            MorePage.CATEGORIES,
            MorePage.TAGS -> ManagementPage(page, state, viewModel, onPage = ::navigate)
        }
    }
}

@Composable
private fun MoreHome(state: MoreUiState, onPage: (MorePage) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("净资产", style = MaterialTheme.typography.labelLarge)
                    Text(state.accountSummary.netAssets.asRmb(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text("资产 ${state.accountSummary.assets.asRmb()} · 负债 ${state.accountSummary.liabilities.asRmb()}")
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(syncLabel(state), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { MoreSection("数据", listOf(MorePage.DATA, MorePage.IMPORT, MorePage.EXPORT, MorePage.SETTINGS), state, onPage) }
        item { MoreSection("账本与账户", listOf(MorePage.LEDGERS, MorePage.ACCOUNTS, MorePage.ASSETS, MorePage.CATEGORIES, MorePage.TAGS), state, onPage) }
        item { MoreSection("自动化", listOf(MorePage.RULES, MorePage.REMINDERS), state, onPage) }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MoreSection(
    title: String,
    pages: List<MorePage>,
    state: MoreUiState,
    onPage: (MorePage) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            modifier = Modifier.padding(start = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            pages.forEachIndexed { index, page ->
                GroupedOptionRow(
                    title = page.label,
                    subtitle = page.description(state),
                    icon = page.icon,
                    shape = groupedOptionShape(index, pages.size),
                    onClick = { onPage(page) },
                    trailing = { Icon(Icons.Default.ChevronRight, contentDescription = page.label) },
                )
            }
        }
    }
}

@Composable
private fun GroupedOptionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    shape: Shape,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
) {
    val modifier = if (onClick == null) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.fillMaxWidth().clickable(onClick = onClick)
    }
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = shape) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                Modifier.weight(1f).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                content?.invoke()
            }
            trailing?.invoke()
        }
    }
}

private fun groupedOptionShape(index: Int, size: Int): Shape = when {
    size == 1 -> RoundedCornerShape(24.dp)
    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    index == size - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    else -> RoundedCornerShape(4.dp)
}

private fun MorePage.description(state: MoreUiState): String = when (this) {
    MorePage.HOME -> ""
    MorePage.DATA -> syncLabel(state)
    MorePage.IMPORT -> "从账单文件批量导入交易"
    MorePage.EXPORT -> "导出青子记账兼容数据"
    MorePage.SETTINGS -> "应用锁、显示模式与主题色"
    MorePage.LEDGERS -> "管理账本与默认账本"
    MorePage.ACCOUNTS -> "管理账户、余额与卡片信息"
    MorePage.ASSETS -> "查看净资产和账户分布"
    MorePage.CATEGORIES -> "维护收支分类与图标"
    MorePage.TAGS -> "维护账本标签"
    MorePage.RULES -> "自动分类和排除规则"
    MorePage.REMINDERS -> "还款与订阅提醒"
}

@Composable
private fun SettingsPage(state: MoreUiState, viewModel: OmniFlowViewModel, onData: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GroupedOptionRow(
                    title = "应用锁",
                    subtitle = "启动或回到前台时验证设备凭据",
                    icon = Icons.Default.Lock,
                    shape = groupedOptionShape(0, 4),
                    trailing = { Switch(state.preferences.appLockEnabled, viewModel::setAppLockEnabled) },
                )
                GroupedOptionRow(
                    title = "界面外观",
                    subtitle = "跟随系统、浅色或深色",
                    icon = Icons.Default.DarkMode,
                    shape = groupedOptionShape(1, 4),
                    content = {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppearanceMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = state.preferences.appearanceMode == mode,
                                    onClick = { viewModel.setAppearanceMode(mode) },
                                    modifier = Modifier.weight(1f),
                                    label = { Text(when (mode) { AppearanceMode.SYSTEM -> "系统"; AppearanceMode.LIGHT -> "浅色"; AppearanceMode.DARK -> "深色" }) },
                                )
                            }
                        }
                    },
                )
                GroupedOptionRow(
                    title = "主题色",
                    subtitle = "选择按钮和导航的全局强调色",
                    icon = Icons.Default.Palette,
                    shape = groupedOptionShape(2, 4),
                    content = {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(ThemeColor.entries, key = { it.name }) { color ->
                                val selected = state.preferences.themeColor == color
                                Column(
                                    modifier = Modifier
                                        .width(64.dp)
                                        .selectable(
                                            selected = selected,
                                            onClick = { viewModel.setThemeColor(color) },
                                            role = Role.RadioButton,
                                        ),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .border(
                                                width = if (selected) 3.dp else 0.dp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                shape = CircleShape,
                                            ),
                                        shape = CircleShape,
                                        color = themePrimaryColor(color, false),
                                    ) {}
                                    Text(themeColorLabel(color), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                            }
                        }
                    },
                )
                GroupedOptionRow(
                    title = "数据管理",
                    subtitle = "备份、恢复、导入与导出",
                    icon = Icons.Default.Storage,
                    shape = groupedOptionShape(3, 4),
                    onClick = onData,
                    trailing = { Icon(Icons.Default.ChevronRight, contentDescription = "数据管理") },
                )
            }
        }
    }
}

private fun themeColorLabel(color: ThemeColor): String = when (color) {
    ThemeColor.MIST_BLUE -> "雾蓝"
    ThemeColor.SAGE -> "鼠尾草"
    ThemeColor.LAVENDER -> "薰衣草"
    ThemeColor.SOFT_CORAL -> "柔珊瑚"
    ThemeColor.WARM_AMBER -> "暖琥珀"
    ThemeColor.GRAPHITE -> "石墨灰"
}

@Composable
private fun DataManagementPage(
    state: MoreUiState,
    viewModel: OmniFlowViewModel,
    onPage: (MorePage) -> Unit,
) {
    val context = LocalContext.current
    val secure = remember { context.getSharedPreferences("webdav", Context.MODE_PRIVATE) }
    var endpoint by remember { mutableStateOf(secure.getString("endpoint", "").orEmpty()) }
    var username by remember { mutableStateOf(secure.getString("username", "").orEmpty()) }
    var password by remember { mutableStateOf(WebDavCredentials.password(context)) }
    var retention by remember(state.preferences.backupRetention) { mutableStateOf(state.preferences.backupRetention.toString()) }
    var pendingRestore by remember { mutableStateOf<com.omniflow.shared.domain.model.RemoteBackupMeta?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("WebDAV 全量备份", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(endpoint, { endpoint = it }, label = { Text("服务器目录 URL") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(username, { username = it }, label = { Text("用户名") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, { password = it }, label = { Text("密码") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(retention, { retention = it.filter(Char::isDigit) }, label = { Text("最大备份数") })
                    Button(onClick = {
                        WebDavCredentials.save(context, endpoint, username, password)
                        viewModel.configureSync(SyncTarget.WEBDAV, retention.toIntOrNull() ?: 10)
                    }) { Text("保存配置") }
                    LinearProgressIndicator(
                        progress = { state.syncState.progress ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(syncLabel(state), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::syncNow, enabled = state.syncState.phase != SyncPhase.RUNNING) { Text("立即备份") }
                        OutlinedButton(onClick = viewModel::loadBackups) { Text("刷新备份") }
                    }
                }
            }
        }
        items(state.backups, key = { "${it.deviceId}-${it.backupId}" }) { backup ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(backup.createdAt.toString(), fontWeight = FontWeight.Medium)
                        Text(backup.backupId, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { pendingRestore = backup }) { Text("恢复") }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GroupedOptionRow(
                    title = "导入账单",
                    subtitle = "从支付宝、微信等账单文件导入",
                    icon = Icons.Default.FileUpload,
                    shape = groupedOptionShape(0, 2),
                    onClick = { onPage(MorePage.IMPORT) },
                    trailing = { Icon(Icons.Default.ChevronRight, contentDescription = "导入账单") },
                )
                GroupedOptionRow(
                    title = "导出数据",
                    subtitle = "生成兼容青子记账的数据文件",
                    icon = Icons.Default.FileDownload,
                    shape = groupedOptionShape(1, 2),
                    onClick = { onPage(MorePage.EXPORT) },
                    trailing = { Icon(Icons.Default.ChevronRight, contentDescription = "导出数据") },
                )
            }
        }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    }
    pendingRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("完整恢复备份？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("恢复会完整替换本机全部可恢复数据，不与当前数据合并。")
                    OutlinedButton(onClick = viewModel::syncNow) { Text("先备份当前数据") }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.restoreBackup(backup); pendingRestore = null }) { Text("确认恢复") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ImportPage(state: MoreUiState, viewModel: OmniFlowViewModel) {
    val context = LocalContext.current
    var ledgerId by remember(state.selectedLedgerId) { mutableStateOf(state.selectedLedgerId) }
    var selectedFormat by remember { mutableStateOf<ImportFormat?>(null) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && ledgerId != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) viewModel.importFile(ledgerId!!, context.fileName(uri), bytes, selectedFormat)
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ValueMenu(
                label = state.ledgers.firstOrNull { it.id == ledgerId }?.name ?: "先选择账本",
                values = state.ledgers,
                valueLabel = Ledger::name,
            ) { ledgerId = it.id; viewModel.selectMoreLedger(it.id) }
        }
        item {
            NullableValueMenu(
                label = selectedFormat?.label ?: "自动识别来源",
                allLabel = "自动识别来源",
                values = ImportFormat.entries,
                valueLabel = { it.label },
                onAll = { selectedFormat = null },
                onSelected = { selectedFormat = it },
            )
        }
        item {
            Button(
                onClick = { fileLauncher.launch("*/*") },
                enabled = ledgerId != null && !state.isImporting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("选择账单文件") }
        }
        if (state.isImporting) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        state.importPreview?.let { preview ->
            item {
                Text(
                    when (preview.phase) {
                        ImportPreviewPhase.DETECTING -> "正在识别格式"
                        ImportPreviewPhase.PARSING -> "正在解析账单"
                        ImportPreviewPhase.ENRICHING -> "正在应用规则、记忆和去重"
                        ImportPreviewPhase.READY -> "预览 ${preview.items.size} 条"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (preview.phase == ImportPreviewPhase.READY) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("将导入 ${preview.importableItems.size} 条")
                            Text("收入 ${preview.incomeTotal.asRmb()} · 支出 ${preview.expenseTotal.asRmb()}")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { viewModel.selectAllImportItems(true) }) { Text("全选") }
                                TextButton(onClick = viewModel::invertImportSelection) { Text("反选") }
                                TextButton(onClick = { viewModel.setSelectedImportSkipped(true) }) { Text("批量排除") }
                            }
                            NullableValueMenu(
                                label = "批量设置分类",
                                allLabel = "清空分类",
                                values = state.categories,
                                valueLabel = { it.name },
                                onAll = { viewModel.setSelectedImportCategory(null) },
                                onSelected = { viewModel.setSelectedImportCategory(it.id) },
                            )
                        }
                    }
                }
                items(preview.items, key = { it.id }) { item -> ImportPreviewCard(item, state, viewModel) }
                item {
                    Button(
                        onClick = viewModel::commitImport,
                        enabled = preview.isReadyToCommit && !state.isImporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("确认入账") }
                }
            }
        }
        state.importMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ImportPreviewCard(item: ImportPreviewItem, state: MoreUiState, viewModel: OmniFlowViewModel) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(item.id in state.selectedImportItemIds, { viewModel.toggleImportItem(item.id) })
                Column(Modifier.weight(1f)) {
                    Text(item.note ?: "无备注", fontWeight = FontWeight.Medium)
                    Text("${item.raw.occurredAt} · ${item.raw.amount.asRmb()}", style = MaterialTheme.typography.bodySmall)
                }
                Switch(item.isSkipped, { skipped ->
                    viewModel.editImportItem(item.id, item.type, item.categoryId, item.accountId, skipped)
                }, enabled = item.duplicateStatus != ImportDuplicateStatus.CONFIRMED)
            }
            if (item.duplicateStatus != ImportDuplicateStatus.NONE) {
                Text(if (item.duplicateStatus == ImportDuplicateStatus.CONFIRMED) "重复交易" else "疑似重复", color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = item.type == TransactionType.EXPENSE,
                    onClick = { viewModel.editImportItem(item.id, TransactionType.EXPENSE, item.categoryId, item.accountId, item.isSkipped) },
                    label = { Text("支出") },
                )
                FilterChip(
                    selected = item.type == TransactionType.INCOME,
                    onClick = { viewModel.editImportItem(item.id, TransactionType.INCOME, item.categoryId, item.accountId, item.isSkipped) },
                    label = { Text("收入") },
                )
            }
            ValueMenu(
                label = state.categories.firstOrNull { it.id == item.categoryId }?.name ?: "选择分类",
                values = state.categories.filter { item.type == null || it.type == item.type },
                valueLabel = { it.name },
            ) { category -> viewModel.editImportItem(item.id, item.type, category.id, item.accountId, item.isSkipped) }
            ValueMenu(
                label = state.accounts.firstOrNull { it.id == item.accountId }?.name ?: "选择账户",
                values = state.accounts,
                valueLabel = { it.name },
            ) { account -> viewModel.editImportItem(item.id, item.type, item.categoryId, account.id, item.isSkipped) }
            OutlinedTextField(
                value = item.note.orEmpty(),
                onValueChange = { note ->
                    viewModel.editImportItem(item.id, item.type, item.categoryId, item.accountId, item.isSkipped, note = note)
                },
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = item.tags.joinToString(","),
                onValueChange = { tags ->
                    viewModel.editImportItem(
                        item.id,
                        item.type,
                        item.categoryId,
                        item.accountId,
                        item.isSkipped,
                        tags = tags.split(',').map(String::trim).filter(String::isNotEmpty),
                    )
                },
                label = { Text("标签（逗号分隔）") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("不计入收支", modifier = Modifier.weight(1f))
                Switch(
                    checked = item.isExcluded,
                    onCheckedChange = { excluded ->
                        viewModel.editImportItem(
                            item.id,
                            item.type,
                            item.categoryId,
                            item.accountId,
                            item.isSkipped,
                            isExcluded = excluded,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ExportPage(state: MoreUiState, viewModel: OmniFlowViewModel) {
    val context = LocalContext.current
    var payload by remember { mutableStateOf<String?>(null) }
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    var incremental by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf(today) }
    var endDate by remember { mutableStateOf(today) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) context.contentResolver.openOutputStream(uri)?.use { it.write(payload.orEmpty().encodeToByteArray()) }
        payload = null
        viewModel.consumeExportPayload()
    }
    LaunchedEffect(state.exportPayload) {
        state.exportPayload?.let {
            payload = it
            launcher.launch("OmniFlow-Qingzi.json")
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("青子记账兼容 JSON", fontWeight = FontWeight.SemiBold)
                Text("默认导出全部有效交易，也可按日期范围增量导出。")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("仅导出日期范围", modifier = Modifier.weight(1f))
                    Switch(incremental, { incremental = it })
                }
                if (incremental) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportDateButton("开始", startDate, Modifier.weight(1f)) { startDate = it }
                        ExportDateButton("结束", endDate, Modifier.weight(1f)) { endDate = it }
                    }
                }
                Button(
                    onClick = { viewModel.prepareQingziExport(if (incremental) exportRange(startDate, endDate) else null) },
                    enabled = !state.isExporting,
                ) {
                    Text(if (state.isExporting) "生成中…" else "生成并保存")
                }
            }
        }
        state.exportWarnings.forEach { Text(it, color = MaterialTheme.colorScheme.tertiary) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ExportDateButton(label: String, date: LocalDate, modifier: Modifier, onDate: (LocalDate) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, day -> onDate(LocalDate(year, month + 1, day)) },
                date.year,
                date.monthNumber - 1,
                date.dayOfMonth,
            ).show()
        },
        modifier = modifier,
    ) { Text("$label ${date.monthNumber}/${date.dayOfMonth}") }
}

private fun exportRange(first: LocalDate, second: LocalDate): DateRange {
    val start = minOf(first, second)
    val end = maxOf(first, second)
    val exclusive = java.time.LocalDate.of(end.year, end.monthNumber, end.dayOfMonth).plusDays(1)
    val zone = TimeZone.currentSystemDefault()
    return DateRange(
        start.atStartOfDayIn(zone),
        LocalDate(exclusive.year, exclusive.monthValue, exclusive.dayOfMonth).atStartOfDayIn(zone),
    )
}

private fun syncLabel(state: MoreUiState): String = when (state.syncState.phase) {
    SyncPhase.IDLE -> if (state.preferences.syncTarget == null) "未配置同步" else "等待备份"
    SyncPhase.RUNNING -> "备份进行中 ${((state.syncState.progress ?: 0f) * 100).toInt()}%"
    SyncPhase.SUCCESS -> "最近备份 ${state.syncState.lastBackupAt ?: "刚刚"}"
    SyncPhase.ERROR -> state.syncState.errorMessage ?: "备份失败"
}

private val ImportFormat.label: String
    get() = when (this) {
        ImportFormat.ALIPAY -> "支付宝"
        ImportFormat.WECHAT -> "微信"
        ImportFormat.JD -> "京东"
        ImportFormat.MEITUAN -> "美团"
        ImportFormat.CCB -> "建设银行"
        ImportFormat.QINGZI -> "青子记账"
    }

private fun Context.fileName(uri: Uri): String {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) return cursor.getString(0)
    }
    return uri.lastPathSegment ?: "import"
}

@Composable
internal fun <T> ValueMenu(label: String, values: List<T>, valueLabel: (T) -> String, onSelected: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            values.forEach { value ->
                DropdownMenuItem(text = { Text(valueLabel(value)) }, onClick = { expanded = false; onSelected(value) })
            }
        }
    }
}

@Composable
internal fun <T> NullableValueMenu(
    label: String,
    allLabel: String,
    values: List<T>,
    valueLabel: (T) -> String,
    onAll: () -> Unit,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(allLabel) }, onClick = { expanded = false; onAll() })
            values.forEach { value ->
                DropdownMenuItem(text = { Text(valueLabel(value)) }, onClick = { expanded = false; onSelected(value) })
            }
        }
    }
}
