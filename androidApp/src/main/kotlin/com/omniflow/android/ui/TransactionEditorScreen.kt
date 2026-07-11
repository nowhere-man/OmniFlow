package com.omniflow.android.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.omniflow.shared.domain.model.Account
import com.omniflow.shared.domain.model.Category
import com.omniflow.shared.domain.model.Ledger
import com.omniflow.shared.domain.model.TransactionType
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

@Composable
internal fun TransactionEditorScreen(
    state: TransactionEditorUiState,
    onType: (TransactionType) -> Unit,
    onLedger: (String?) -> Unit,
    onAccount: (String?) -> Unit,
    onCategory: (String?) -> Unit,
    onReorderPrimary: (List<String>) -> Unit,
    onCreateSecondary: (String) -> Unit,
    onTag: (String) -> Unit,
    onNote: (String) -> Unit,
    onDate: (kotlinx.datetime.Instant) -> Unit,
    onExcluded: (Boolean) -> Unit,
    onAmountKey: (String) -> Unit,
    onSaveAgain: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedCategory = state.categories.firstOrNull { it.id == state.categoryId }
    val primaryId = selectedCategory?.parentId ?: selectedCategory?.id
    val primaryCategories = state.categories.filter { it.parentId == null && it.type == state.type }
    val secondaryCategories = state.categories.filter { it.parentId == primaryId && it.type == state.type }
    val selectedPrimary = state.categories.firstOrNull { it.id == primaryId }
    val selectedSecondary = selectedCategory?.takeIf { it.parentId != null }
    var showSecondaryDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            TransactionFooter(state, onAmountKey, onSaveAgain, onDone)
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TransactionTopBar(
                state = state,
                selectedType = state.type,
                ledgers = state.ledgers,
                accounts = state.accounts,
                onType = onType,
                onLedger = { onLedger(it.id) },
                onAccount = { onAccount(it.id) },
            )
            if (primaryCategories.isEmpty()) {
                Text("选择账本后加载分类", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                ReorderableCategoryGrid(
                    primaryCategories,
                    primaryId,
                    onCategory,
                    onReorderPrimary,
                )
            }
            Spacer(Modifier.weight(1f))
            if (state.tags.isNotEmpty()) {
                CompactTagRow(state.tags, state.selectedTagIds, onTag)
            }
            if (selectedPrimary != null) {
                TransactionEntryPanel(
                    state = state,
                    primary = selectedPrimary,
                    selectedSecondary = selectedSecondary,
                    secondaryCategories = secondaryCategories,
                    onSecondary = onCategory,
                    onAddSecondary = { showSecondaryDialog = true },
                    onNote = onNote,
                    onDate = onDate,
                    onExcluded = onExcluded,
                )
            }
        }
    }

    if (showSecondaryDialog) {
        NewSecondaryCategoryDialog(
            primaryName = state.categories.firstOrNull { it.id == primaryId }?.name.orEmpty(),
            onDismiss = { showSecondaryDialog = false },
            onCreate = { name ->
                onCreateSecondary(name)
                showSecondaryDialog = false
            },
        )
    }
}

@Composable
private fun TransactionTopBar(
    state: TransactionEditorUiState,
    selectedType: TransactionType,
    ledgers: List<Ledger>,
    accounts: List<Account>,
    onType: (TransactionType) -> Unit,
    onLedger: (Ledger) -> Unit,
    onAccount: (Account) -> Unit,
) {
    val ledgerName = ledgers.firstOrNull { it.id == state.ledgerId }?.name ?: "选择账本"
    val selectedAccount = accounts.firstOrNull { it.id == state.accountId }
    Box(Modifier.fillMaxWidth().height(56.dp)) {
        EditorMenuButton(
            values = ledgers,
            valueLabel = Ledger::name,
            onSelected = onLedger,
            contentDescription = ledgerName,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(25.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        TransactionModeSwitch(
            selected = selectedType,
            onSelected = onType,
            modifier = Modifier.align(Alignment.Center).width(190.dp),
        )
        EditorMenuButton(
            values = accounts,
            valueLabel = Account::name,
            onSelected = onAccount,
            contentDescription = selectedAccount?.name ?: "选择账户",
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            if (selectedAccount == null) {
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    modifier = Modifier.size(25.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                SvgIcon(selectedAccount.iconKey, Modifier.size(25.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun <T> EditorMenuButton(
    values: List<T>,
    valueLabel: (T) -> String,
    onSelected: (T) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.size(48.dp).semantics { this.contentDescription = contentDescription },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        ) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { item ->
                DropdownMenuItem(
                    text = { Text(valueLabel(item)) },
                    onClick = { expanded = false; onSelected(item) },
                )
            }
        }
    }
}

@Composable
private fun TransactionModeSwitch(
    selected: TransactionType,
    onSelected: (TransactionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(3.dp)) {
            TransactionType.entries.forEach { type ->
                val isSelected = type == selected
                Text(
                    if (type == TransactionType.EXPENSE) "支出" else "收入",
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { onSelected(type) }
                        .padding(vertical = 10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ReorderableCategoryGrid(
    categories: List<Category>,
    selectedId: String?,
    onSelected: (String?) -> Unit,
    onReordered: (List<String>) -> Unit,
) {
    val columns = 5
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val cellHeight = 86.dp
    val cellHeightPx = with(density) { cellHeight.toPx() }
    var width by remember { mutableIntStateOf(0) }
    var ordered by remember(categories) { mutableStateOf(categories) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var changed by remember { mutableStateOf(false) }
    val rows = ceil(ordered.size / columns.toFloat()).toInt().coerceAtLeast(1)
    Box(
        modifier = Modifier.fillMaxWidth().height(cellHeight * rows.toFloat()).onSizeChanged { width = it.width },
    ) {
        if (width > 0) {
            val cellWidthPx = width.toFloat() / columns
            val cellWidth = with(density) { cellWidthPx.toDp() }
            fun position(index: Int) = IntOffset(
                x = ((index % columns) * cellWidthPx).roundToInt(),
                y = ((index / columns) * cellHeightPx).roundToInt(),
            )
            ordered.forEachIndexed { index, category ->
                key(category.id) {
                    val target = position(index)
                    val animatedTarget by animateIntOffsetAsState(target, spring(), label = "category-position")
                    val dragging = draggedId == category.id
                    val scale by animateFloatAsState(if (dragging) 1.08f else 1f, spring(), label = "category-scale")
                    CategoryTile(
                        category = category,
                        selected = selectedId == category.id,
                        dragging = dragging,
                        onClick = { onSelected(category.id) },
                        modifier = Modifier
                            .offset {
                                if (dragging) {
                                    IntOffset(target.x + dragOffset.x.roundToInt(), target.y + dragOffset.y.roundToInt())
                                } else animatedTarget
                            }
                            .width(cellWidth)
                            .height(cellHeight)
                            .padding(3.dp)
                            .zIndex(if (dragging) 2f else 0f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                shadowElevation = if (dragging) 14.dp.toPx() else 0f
                            }
                            .pointerInput(category.id, width) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggedId = category.id
                                        dragOffset = Offset.Zero
                                        changed = false
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragCancel = {
                                        ordered = categories
                                        draggedId = null
                                        dragOffset = Offset.Zero
                                        changed = false
                                    },
                                    onDragEnd = {
                                        if (changed) onReordered(ordered.map(Category::id))
                                        draggedId = null
                                        dragOffset = Offset.Zero
                                        changed = false
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        val currentIndex = ordered.indexOfFirst { it.id == category.id }
                                        if (currentIndex < 0) return@detectDragGesturesAfterLongPress
                                        dragOffset += amount
                                        val oldBase = position(currentIndex)
                                        val centerX = oldBase.x + dragOffset.x + cellWidthPx / 2f
                                        val centerY = oldBase.y + dragOffset.y + cellHeightPx / 2f
                                        val column = (centerX / cellWidthPx).toInt().coerceIn(0, columns - 1)
                                        val row = (centerY / cellHeightPx).toInt().coerceIn(0, rows - 1)
                                        val targetIndex = (row * columns + column).coerceIn(0, ordered.lastIndex)
                                        if (targetIndex != currentIndex) {
                                            ordered = ordered.toMutableList().apply { add(targetIndex, removeAt(currentIndex)) }
                                            val newBase = position(targetIndex)
                                            dragOffset += Offset((oldBase.x - newBase.x).toFloat(), (oldBase.y - newBase.y).toFloat())
                                            changed = true
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                )
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    category: Category,
    selected: Boolean,
    dragging: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f) else Color.Transparent,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        tonalElevation = if (dragging) 8.dp else 0.dp,
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
            ) {
                SvgIcon(
                    categoryIconKey(category.iconKey),
                    Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    category.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun TransactionEntryPanel(
    state: TransactionEditorUiState,
    primary: Category,
    selectedSecondary: Category?,
    secondaryCategories: List<Category>,
    onSecondary: (String?) -> Unit,
    onAddSecondary: () -> Unit,
    onNote: (String) -> Unit,
    onDate: (kotlinx.datetime.Instant) -> Unit,
    onExcluded: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(11.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        SvgIcon(categoryIconKey(primary.iconKey), Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(primary.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                selectedSecondary?.let {
                    Text(" - ${it.name}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "¥${state.amountInput.ifBlank { "0" }}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                secondaryCategories.forEach { category ->
                    FilterChip(
                        selected = category.id == selectedSecondary?.id,
                        onClick = { onSecondary(category.id) },
                        label = { Text(category.name, maxLines = 1) },
                    )
                }
                Surface(onClick = onAddSecondary, shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(Icons.Default.Add, contentDescription = "新建二级分类", modifier = Modifier.padding(10.dp).size(18.dp))
                }
            }
            CompactEntryDetails(state, onNote, onDate, onExcluded)
        }
    }
}

@Composable
private fun CompactTagRow(
    tags: List<com.omniflow.shared.domain.model.Tag>,
    selectedIds: Set<String>,
    onSelected: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("标签", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        tags.forEach { tag ->
            FilterChip(
                selected = tag.id in selectedIds,
                onClick = { onSelected(tag.id) },
                label = { Text(tag.name, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun CompactEntryDetails(
    state: TransactionEditorUiState,
    onNote: (String) -> Unit,
    onDate: (kotlinx.datetime.Instant) -> Unit,
    onExcluded: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val dateTime = state.occurredAt.toLocalDateTime(ChinaTimeZone)
    val today = Clock.System.now().toLocalDateTime(ChinaTimeZone).date
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Surface(
                modifier = Modifier.weight(1f).height(38.dp),
                color = Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.weight(1f)) {
                        if (state.note.isBlank()) {
                            Text("备注", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        BasicTextField(
                            value = state.note,
                            onValueChange = onNote,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodySmall.fontSize),
                        )
                    }
                }
            }
            Surface(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            onDate(LocalDateTime(dateTime.date, LocalTime(hour, minute)).toInstant(ChinaTimeZone))
                        },
                        dateTime.hour,
                        dateTime.minute,
                        true,
                    ).show()
                },
                modifier = Modifier.height(38.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
            ) {
                Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text("${dateTime.hour.twoDigits()}:${dateTime.minute.twoDigits()}", fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        onDate(LocalDateTime(LocalDate(year, month + 1, day), dateTime.time).toInstant(ChinaTimeZone))
                    },
                    dateTime.year,
                    dateTime.monthNumber - 1,
                    dateTime.dayOfMonth,
                ).show()
            }) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "选择日期", modifier = Modifier.size(18.dp))
                if (dateTime.date != today) {
                    Spacer(Modifier.width(5.dp))
                    Text("${dateTime.monthNumber.twoDigits()}-${dateTime.dayOfMonth.twoDigits()}")
                }
            }
            Spacer(Modifier.weight(1f))
            Text("不计入统计", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Switch(checked = state.isExcluded, onCheckedChange = onExcluded)
        }
    }
}

@Composable
private fun NewSecondaryCategoryDialog(
    primaryName: String,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$primaryName · 新建二级分类") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("分类名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun TransactionFooter(
    state: TransactionEditorUiState,
    onAmountKey: (String) -> Unit,
    onSaveAgain: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Keypad(
                state.isSaving,
                onAmountKey,
                onSaveAgain,
                onDone,
            )
        }
    }
}

@Composable
private fun Keypad(
    isSaving: Boolean,
    onKey: (String) -> Unit,
    onAction: () -> Unit,
    onDone: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3", "+"),
        listOf("4", "5", "6", "-"),
        listOf("7", "8", "9", "再记"),
        listOf(".", "0", "退格", "完成"),
    )
    rows.forEachIndexed { rowIndex, row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            row.forEach { key ->
                val isDone = key == "完成"
                val isAction = rowIndex == 2 && key == "再记"
                val isOperator = key == "+" || key == "-"
                val color = when {
                    isDone -> MaterialTheme.colorScheme.primary
                    isAction || isOperator -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val contentColor = if (isDone) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Surface(
                    onClick = when {
                        isDone -> onDone
                        isAction -> onAction
                        else -> { { onKey(key) } }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(15.dp),
                    color = color,
                    contentColor = contentColor,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            if (isDone && isSaving) "保存中" else key,
                            style = if (isAction || key == "退格") MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        if (rowIndex != rows.lastIndex) Spacer(Modifier.height(3.dp))
    }
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')
