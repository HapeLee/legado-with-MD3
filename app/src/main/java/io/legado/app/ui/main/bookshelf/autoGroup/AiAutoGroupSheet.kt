package io.legado.app.ui.main.bookshelf.autoGroup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.utils.toastOnUi
import org.koin.androidx.compose.koinViewModel

@Composable
fun AiAutoGroupSheet(
    show: Boolean,
    sessionKey: Long,
    onDismissRequest: () -> Unit,
    viewModel: AiAutoGroupViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val closeSheet = {
        if (state.phase == AiAutoGroupPhase.Applying) {
            context.toastOnUi("正在应用分组，请稍候")
        } else {
            viewModel.onIntent(AiAutoGroupIntent.CloseSession)
            onDismissRequest()
        }
    }

    LaunchedEffect(show, sessionKey) {
        if (show) {
            viewModel.onIntent(AiAutoGroupIntent.StartSession(sessionKey))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AiAutoGroupEffect.ShowMessage -> context.toastOnUi(effect.message)
                AiAutoGroupEffect.Applied -> context.toastOnUi("AI 自动分组已完成")
            }
        }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = closeSheet,
        title = "AI 自动分组",
        endAction = {
            when (state.phase) {
                AiAutoGroupPhase.Reviewing -> MediumTonalButton(
                    onClick = { viewModel.onIntent(AiAutoGroupIntent.RequestApply) },
                    icon = Icons.Default.Check,
                    contentDescription = "确认执行",
                    enabled = state.assignedBookCount > 0,
                )

                AiAutoGroupPhase.Result -> MediumPlainButton(
                    onClick = closeSheet,
                    icon = Icons.Default.Close,
                    contentDescription = "关闭",
                )

                else -> Unit
            }
        }
    ) {
        when (state.phase) {
            AiAutoGroupPhase.LoadingSource -> AutoGroupProgressContent("正在读取书架")
            AiAutoGroupPhase.Preflight -> AutoGroupPreflightContent(
                state = state,
                onDismiss = closeSheet,
                onAnalyze = { viewModel.onIntent(AiAutoGroupIntent.Analyze) },
                onGroupingInstructionChange = {
                    viewModel.onIntent(AiAutoGroupIntent.UpdateGroupingInstruction(it))
                },
            )

            AiAutoGroupPhase.Analyzing -> AutoGroupProgressContent(
                text = "正在分析书架",
                onCancel = { viewModel.onIntent(AiAutoGroupIntent.CancelRunning) },
            )
            AiAutoGroupPhase.Revising -> AutoGroupProgressContent(
                text = "正在调整方案",
                onCancel = { viewModel.onIntent(AiAutoGroupIntent.CancelRunning) },
            )
            AiAutoGroupPhase.Applying -> AutoGroupProgressContent("正在应用分组")
            AiAutoGroupPhase.Reviewing -> AutoGroupReviewContent(
                state = state,
                onIntent = viewModel::onIntent,
            )

            AiAutoGroupPhase.Result -> AutoGroupResultContent(
                resultText = state.resultText.orEmpty(),
                onDone = closeSheet,
                onReset = { viewModel.onIntent(AiAutoGroupIntent.Restart) },
            )

            AiAutoGroupPhase.Error -> AutoGroupErrorContent(
                message = state.errorMessage.orEmpty(),
                onRetry = { viewModel.onIntent(AiAutoGroupIntent.Restart) },
                onDismiss = closeSheet,
            )
        }
    }

    AppAlertDialog(
        show = state.showApplyConfirm,
        onDismissRequest = { viewModel.onIntent(AiAutoGroupIntent.DismissApplyConfirm) },
        title = "确认执行分组方案",
        text = "将创建 ${state.newGroupCount} 个分组，调整 ${state.assignedBookCount} 本书，跳过 ${state.ignoredBooks.size} 本书。执行后会直接修改书架分组。",
        confirmText = "执行",
        onConfirm = { viewModel.onIntent(AiAutoGroupIntent.ConfirmApply) },
        dismissText = "取消",
        onDismiss = { viewModel.onIntent(AiAutoGroupIntent.DismissApplyConfirm) },
    )
}

@Composable
private fun AutoGroupPreflightContent(
    state: AiAutoGroupUiState,
    onDismiss: () -> Unit,
    onAnalyze: () -> Unit,
    onGroupingInstructionChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(containerColor = LegadoTheme.colorScheme.onSheetContent) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "将分析 ${state.bookCount} 本书",
                    style = LegadoTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "会使用书名、作者、分类、简介和当前分组生成建议。AI 只生成方案，不会直接修改书架。",
                    style = LegadoTheme.typography.bodyMedium,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "已有 ${state.existingGroupCount} 个用户分组，${state.groupedBookCount} 本书已有分组。",
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AppTextField(
            value = state.groupingInstruction,
            onValueChange = onGroupingInstructionChange,
            label = "补充分组要求",
            minLines = 3,
            maxLines = 5,
            backgroundColor = LegadoTheme.colorScheme.onSheetContent,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "例如：按题材分组；玄幻和仙侠合并；不要处理本地书；分组数量控制在 8 个以内",
                    style = LegadoTheme.typography.bodyMedium,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        ConfirmDismissButtonsRow(
            onDismiss = onDismiss,
            onConfirm = onAnalyze,
            dismissText = "取消",
            confirmText = "开始分析",
            confirmEnabled = state.bookCount > 0,
        )
    }
}

@Composable
private fun AutoGroupProgressContent(
    text: String,
    onCancel: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = text, style = LegadoTheme.typography.bodyMedium)
        if (onCancel != null) {
            Spacer(modifier = Modifier.height(16.dp))
            MediumPlainButton(
                onClick = onCancel,
                icon = Icons.Default.Close,
                text = "取消",
            )
        }
    }
}

@Composable
private fun AutoGroupReviewContent(
    state: AiAutoGroupUiState,
    onIntent: (AiAutoGroupIntent) -> Unit,
) {
    var newGroupName by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AutoGroupSummaryCard(state = state)

        GlassCard(containerColor = LegadoTheme.colorScheme.onSheetContent) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppTextField(
                    value = state.revisionInstruction,
                    onValueChange = { onIntent(AiAutoGroupIntent.UpdateRevisionInstruction(it)) },
                    label = "让 AI 调整方案",
                    minLines = 2,
                    backgroundColor = LegadoTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MediumTonalButton(
                        onClick = { onIntent(AiAutoGroupIntent.Revise) },
                        icon = Icons.Default.Refresh,
                        text = "重新调整",
                        enabled = state.revisionInstruction.isNotBlank(),
                    )
                    MediumPlainButton(
                        onClick = { onIntent(AiAutoGroupIntent.RequestApply) },
                        icon = Icons.Default.PlayArrow,
                        text = "确认执行",
                        enabled = state.assignedBookCount > 0,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTextField(
                value = newGroupName,
                onValueChange = { newGroupName = it },
                label = "新增分组",
                singleLine = true,
                backgroundColor = LegadoTheme.colorScheme.onSheetContent,
                modifier = Modifier.weight(1f),
            )
            MediumTonalButton(
                onClick = {
                    onIntent(AiAutoGroupIntent.AddGroup(newGroupName))
                    newGroupName = ""
                },
                icon = Icons.Default.Add,
                contentDescription = "新增分组",
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.groups, key = { it.key }) { group ->
                AutoGroupCard(
                    group = group,
                    allGroups = state.groups,
                    onIntent = onIntent,
                )
            }

            if (state.ignoredBooks.isNotEmpty()) {
                item(key = "ignored") {
                    IgnoredBooksCard(books = state.ignoredBooks)
                }
            }
        }
    }
}

@Composable
private fun AutoGroupSummaryCard(state: AiAutoGroupUiState) {
    GlassCard(containerColor = LegadoTheme.colorScheme.onSheetContent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SummaryMetric(label = "分组", value = state.groups.size.toString())
            SummaryMetric(label = "新建", value = state.newGroupCount.toString())
            SummaryMetric(label = "书籍", value = state.assignedBookCount.toString())
            SummaryMetric(label = "跳过", value = state.ignoredBooks.size.toString())
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = LegadoTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = LegadoTheme.typography.bodySmall,
            color = LegadoTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AutoGroupCard(
    group: AiAutoGroupGroupUi,
    allGroups: List<AiAutoGroupGroupUi>,
    onIntent: (AiAutoGroupIntent) -> Unit,
) {
    GlassCard(containerColor = LegadoTheme.colorScheme.onSheetContent) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppTextField(
                    value = group.name,
                    onValueChange = { onIntent(AiAutoGroupIntent.RenameGroup(group.key, it)) },
                    singleLine = true,
                    label = if (group.reuseExisting) "复用分组" else "新建分组",
                    backgroundColor = LegadoTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.weight(1f),
                )
                MediumPlainButton(
                    onClick = { onIntent(AiAutoGroupIntent.RemoveGroup(group.key)) },
                    icon = Icons.Default.Delete,
                    contentDescription = "删除分组",
                )
            }

            if (group.description.isNotBlank()) {
                Text(
                    text = group.description,
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            group.books.forEach { book ->
                AutoGroupBookItem(
                    book = book,
                    currentGroupKey = group.key,
                    allGroups = allGroups,
                    onIntent = onIntent,
                )
            }
        }
    }
}

@Composable
private fun AutoGroupBookItem(
    book: AiAutoGroupBookUi,
    currentGroupKey: String,
    allGroups: List<AiAutoGroupGroupUi>,
    onIntent: (AiAutoGroupIntent) -> Unit,
) {
    SelectionItemCard(
        title = book.name,
        subtitle = buildString {
            if (book.author.isNotBlank()) append(book.author)
            if (book.currentGroupNames.isNotEmpty()) {
                if (isNotBlank()) append(" · ")
                append("原分组：")
                append(book.currentGroupNames.joinToString("、"))
            }
        }.ifBlank { null },
        supportingContent = {
            book.reason.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        onToggleSelection = {},
        trailingAction = {
            SmallPlainButton(
                onClick = { onIntent(AiAutoGroupIntent.IgnoreBook(book.bookUrl)) },
                icon = Icons.Default.Close,
                contentDescription = "不处理",
            )
        },
        dropdownContent = { onDismiss ->
            allGroups.filterNot { it.key == currentGroupKey }.forEach { target ->
                RoundDropdownMenuItem(
                    text = "移到 ${target.name}",
                    onClick = {
                        onIntent(AiAutoGroupIntent.MoveBook(book.bookUrl, target.key))
                        onDismiss()
                    },
                )
            }
        },
        containerColor = LegadoTheme.colorScheme.surfaceContainer,
    )
}

@Composable
private fun IgnoredBooksCard(books: List<AiAutoGroupIgnoredBookUi>) {
    GlassCard(containerColor = LegadoTheme.colorScheme.onSheetContent) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "不处理 ${books.size} 本",
                style = LegadoTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            books.take(12).forEach { book ->
                Text(
                    text = buildString {
                        append(book.name)
                        if (book.reason.isNotBlank()) {
                            append(" · ")
                            append(book.reason)
                        }
                    },
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (books.size > 12) {
                Text(
                    text = "还有 ${books.size - 12} 本未显示",
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AutoGroupResultContent(
    resultText: String,
    onDone: () -> Unit,
    onReset: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(containerColor = LegadoTheme.colorScheme.onSheetContent) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = resultText, style = LegadoTheme.typography.bodyMedium)
            }
        }
        ConfirmDismissButtonsRow(
            onDismiss = onReset,
            onConfirm = onDone,
            dismissText = "重新分析",
            confirmText = "完成",
        )
    }
}

@Composable
private fun AutoGroupErrorContent(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(containerColor = LegadoTheme.colorScheme.onSheetContent) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "自动分组失败",
                    style = LegadoTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = message.ifBlank { "未知错误" },
                    style = LegadoTheme.typography.bodyMedium,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ConfirmDismissButtonsRow(
            onDismiss = onDismiss,
            onConfirm = onRetry,
            dismissText = "关闭",
            confirmText = "返回",
        )
    }
}
