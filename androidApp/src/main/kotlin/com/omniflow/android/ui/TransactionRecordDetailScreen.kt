package com.omniflow.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omniflow.shared.domain.model.TransactionSource
import com.omniflow.shared.domain.model.TransactionType
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionRecordDetailSheet(
    state: TransactionRecordDetailUiState,
    onDismiss: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember(state.transaction?.id) { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        when {
            state.isLoading -> Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.transaction != null -> {
                val transaction = state.transaction
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "关闭明细") }
                            Text(
                                "明细详情",
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            IconButton(onClick = { confirmDelete = true }, enabled = !state.isDeleting) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "删除明细", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    item {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Surface(
                                    modifier = Modifier.size(64.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        SvgIcon(
                                            categoryIconKey(state.categoryIconKey),
                                            Modifier.size(34.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                Text(state.categoryDisplayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "${if (transaction.type == TransactionType.EXPENSE) "-" else "+"}${transaction.amount.asRmb()}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (transaction.type == TransactionType.EXPENSE) {
                                        MaterialTheme.colorScheme.error
                                    } else MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    item {
                        DetailSection {
                            DetailRow(Icons.Default.Payments, "金额", transaction.amount.asRmb())
                            DetailRow(Icons.Default.SwapHoriz, "类型", if (transaction.type == TransactionType.EXPENSE) "支出" else "收入")
                            DetailRow(
                                Icons.Default.CalendarMonth,
                                "日期",
                                transaction.occurredAt.toLocalDateTime(ChinaTimeZone).let { dateTime ->
                                    "${dateTime.date} ${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
                                },
                            )
                            DetailRow(Icons.Default.AccountBalanceWallet, "账户", state.accountName)
                            DetailRow(Icons.AutoMirrored.Filled.MenuBook, "账本", state.ledgerName)
                            transaction.source?.let { DetailRow(Icons.Default.SyncAlt, "来源", it.displayName()) }
                            DetailRow(Icons.Default.Block, "统计", if (transaction.isExcluded) "不计入统计" else "计入统计")
                        }
                    }
                    transaction.note?.takeIf(String::isNotBlank)?.let { note ->
                        item {
                            DetailSection {
                                DetailRow(Icons.AutoMirrored.Filled.Notes, "备注", note)
                            }
                        }
                    }
                    if (state.tagNames.isNotEmpty()) {
                        item {
                            DetailSection {
                                DetailRow(Icons.AutoMirrored.Filled.Label, "标签", state.tagNames.joinToString(" · "))
                            }
                        }
                    }
                    state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { confirmDelete = true },
                                modifier = Modifier.weight(1f).height(52.dp),
                                enabled = !state.isDeleting,
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(6.dp))
                                Text(if (state.isDeleting) "删除中" else "删除", color = MaterialTheme.colorScheme.error)
                            }
                            Button(
                                onClick = { onEdit(transaction.id) },
                                modifier = Modifier.weight(2f).height(52.dp),
                                enabled = !state.isDeleting,
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("编辑")
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
            else -> Column(
                Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(state.error ?: "无法读取这条明细", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条明细？") },
            text = { Text("删除后将无法恢复。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun DetailSection(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) { content() }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, modifier = Modifier.width(54.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun TransactionSource.displayName(): String = when (this) {
    TransactionSource.MANUAL -> "手动记账"
    TransactionSource.ALIPAY -> "支付宝"
    TransactionSource.WECHAT -> "微信"
    TransactionSource.JD -> "京东"
    TransactionSource.MEITUAN -> "美团"
    TransactionSource.CCB -> "建设银行"
}
