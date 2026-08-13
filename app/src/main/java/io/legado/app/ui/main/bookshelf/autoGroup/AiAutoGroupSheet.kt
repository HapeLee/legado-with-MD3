package io.legado.app.ui.main.bookshelf.autoGroup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.utils.toastOnUi
import org.koin.androidx.compose.koinViewModel

@Composable
fun AiAutoGroupSheet(
    show: Boolean,
    sessionKey: Long,
    onDismissRequest: () -> Unit,
    viewModel: AiAutoGroupViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val applyingWaitMessage = stringResource(R.string.ai_auto_group_applying_wait)
    val effectMessages = AiAutoGroupMessage.entries.associateWith { stringResource(it.stringRes) }
    val completedMessage = stringResource(R.string.ai_auto_group_completed)
    val closeSheet = {
        if (state.phase == AiAutoGroupPhase.Applying) {
            context.toastOnUi(applyingWaitMessage)
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
                is AiAutoGroupEffect.ShowMessage -> context.toastOnUi(effectMessages.getValue(effect.message))
                is AiAutoGroupEffect.ShowError -> context.toastOnUi(effect.error.localizedText(context))
                AiAutoGroupEffect.Applied -> context.toastOnUi(completedMessage)
            }
        }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = closeSheet,
        title = stringResource(R.string.ai_auto_group),
        endAction = {
            when (state.phase) {
                AiAutoGroupPhase.Reviewing -> MediumTonalButton(
                    onClick = { viewModel.onIntent(AiAutoGroupIntent.RequestApply) },
                    icon = Icons.Default.Check,
                    contentDescription = stringResource(R.string.ai_auto_group_confirm_execute),
                    enabled = state.assignedBookCount > 0,
                )

                AiAutoGroupPhase.Result -> MediumPlainButton(
                    onClick = closeSheet,
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                )

                else -> Unit
            }
        }
    ) {
        when (state.phase) {
            AiAutoGroupPhase.LoadingSource -> AutoGroupProgressContent(
                text = stringResource(R.string.ai_auto_group_loading_shelf),
            )
            AiAutoGroupPhase.Preflight -> AutoGroupPreflightContent(
                state = state,
                onDismiss = closeSheet,
                onAnalyze = { viewModel.onIntent(AiAutoGroupIntent.Analyze) },
                onGroupingInstructionChange = {
                    viewModel.onIntent(AiAutoGroupIntent.UpdateGroupingInstruction(it))
                },
                onIncrementalOnlyChange = {
                    viewModel.onIntent(AiAutoGroupIntent.SetIncrementalOnly(it))
                },
                onIncludeBookIntroChange = {
                    viewModel.onIntent(AiAutoGroupIntent.SetIncludeBookIntro(it))
                },
                onDeepThinkingChange = {
                    viewModel.onIntent(AiAutoGroupIntent.SetDeepThinkingEnabled(it))
                },
            )

            AiAutoGroupPhase.Analyzing -> AutoGroupProgressContent(
                text = stringResource(R.string.ai_auto_group_analyzing),
                currentBatch = state.currentBatch,
                totalBatches = state.totalBatches,
                onCancel = { viewModel.onIntent(AiAutoGroupIntent.CancelRunning) },
            )
            AiAutoGroupPhase.Revising -> AutoGroupProgressContent(
                text = stringResource(R.string.ai_auto_group_revising),
                currentBatch = state.currentBatch,
                totalBatches = state.totalBatches,
                onCancel = { viewModel.onIntent(AiAutoGroupIntent.CancelRunning) },
            )
            AiAutoGroupPhase.Applying -> AutoGroupProgressContent(
                text = stringResource(R.string.ai_auto_group_applying),
            )
            AiAutoGroupPhase.Reviewing -> AutoGroupReviewContent(
                state = state,
                onIntent = viewModel::onIntent,
            )

            AiAutoGroupPhase.Result -> AutoGroupResultContent(
                result = state.applyResult,
                onDone = closeSheet,
                onReset = { viewModel.onIntent(AiAutoGroupIntent.Restart) },
            )

            AiAutoGroupPhase.Error -> AutoGroupErrorContent(
                message = state.error?.localizedText(context)
                    ?: stringResource(R.string.ai_auto_group_unknown_error),
                onRetry = { viewModel.onIntent(AiAutoGroupIntent.Restart) },
                onDismiss = closeSheet,
            )
        }
    }

    AppAlertDialog(
        show = state.showApplyConfirm,
        onDismissRequest = { viewModel.onIntent(AiAutoGroupIntent.DismissApplyConfirm) },
        title = stringResource(R.string.ai_auto_group_confirm_title),
        text = stringResource(
            if (state.incrementalOnly) {
                R.string.ai_auto_group_confirm_message_incremental
            } else {
                R.string.ai_auto_group_confirm_message
            },
            state.newGroupCount,
            state.assignedBookCount,
            state.ignoredBooks.size,
        ),
        confirmText = stringResource(R.string.ai_auto_group_execute),
        onConfirm = { viewModel.onIntent(AiAutoGroupIntent.ConfirmApply) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { viewModel.onIntent(AiAutoGroupIntent.DismissApplyConfirm) },
    )
}

@Composable
private fun AutoGroupPreflightContent(
    state: AiAutoGroupUiState,
    onDismiss: () -> Unit,
    onAnalyze: () -> Unit,
    onGroupingInstructionChange: (String) -> Unit,
    onIncrementalOnlyChange: (Boolean) -> Unit,
    onIncludeBookIntroChange: (Boolean) -> Unit,
    onDeepThinkingChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassCard(containerColor = LegadoTheme.colorScheme.onSheetContent) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.ai_auto_group_will_analyze, state.bookCount),
                    style = LegadoTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        if (state.incrementalOnly) {
                            R.string.ai_auto_group_preflight_description_incremental
                        } else {
                            R.string.ai_auto_group_preflight_description
                        }
                    ),
                    style = LegadoTheme.typography.bodyMedium,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.ai_auto_group_existing_summary,
                        state.existingGroupCount,
                        state.groupedBookCount,
                    ),
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
                if (state.estimatedRequestCount > 1) {
                    Text(
                        text = stringResource(
                            R.string.ai_auto_group_batch_estimate,
                            state.estimatedRequestCount,
                        ),
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.primary,
                    )
                }
            }
        }

        SplicedColumnGroup(modifier = Modifier.fillMaxWidth()) {
            SwitchSettingItem(
                title = stringResource(R.string.ai_auto_group_incremental),
                description = stringResource(R.string.ai_auto_group_incremental_desc),
                checked = state.incrementalOnly,
                onCheckedChange = onIncrementalOnlyChange,
            )
            SwitchSettingItem(
                title = stringResource(R.string.ai_auto_group_include_intro),
                description = stringResource(R.string.ai_auto_group_include_intro_desc),
                checked = state.includeBookIntro,
                onCheckedChange = onIncludeBookIntroChange,
            )
            SwitchSettingItem(
                title = stringResource(R.string.ai_auto_group_deep_thinking),
                description = stringResource(R.string.ai_auto_group_deep_thinking_desc),
                checked = state.enableDeepThinking,
                onCheckedChange = onDeepThinkingChange,
            )
        }

        AppTextField(
            value = state.groupingInstruction,
            onValueChange = onGroupingInstructionChange,
            label = stringResource(R.string.ai_auto_group_instruction),
            minLines = 3,
            maxLines = 5,
            backgroundColor = LegadoTheme.colorScheme.onSheetContent,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = stringResource(R.string.ai_auto_group_instruction_hint),
                    style = LegadoTheme.typography.bodyMedium,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        state.error?.let { error ->
            Text(
                text = error.localizedText(LocalContext.current),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.error,
            )
        }

        if (state.incrementalOnly && state.bookCount == 0 && state.error == null) {
            Text(
                text = stringResource(R.string.ai_auto_group_no_ungrouped_books),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.primary,
            )
        }

        ConfirmDismissButtonsRow(
            onDismiss = onDismiss,
            onConfirm = onAnalyze,
            dismissText = stringResource(R.string.cancel),
            confirmText = stringResource(R.string.ai_auto_group_start),
            confirmEnabled = state.bookCount > 0 && state.error == null,
        )
    }
}

@Composable
private fun AutoGroupProgressContent(
    text: String,
    currentBatch: Int = 0,
    totalBatches: Int = 0,
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
        if (totalBatches > 1 && currentBatch > 0) {
            Text(
                text = stringResource(
                    R.string.ai_auto_group_batch_progress,
                    currentBatch,
                    totalBatches,
                ),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onCancel != null) {
            Spacer(modifier = Modifier.height(16.dp))
            MediumPlainButton(
                onClick = onCancel,
                icon = Icons.Default.Close,
                text = stringResource(R.string.cancel),
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
                    label = stringResource(R.string.ai_auto_group_adjust),
                    minLines = 2,
                    backgroundColor = LegadoTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MediumTonalButton(
                        onClick = { onIntent(AiAutoGroupIntent.Revise) },
                        icon = Icons.Default.Refresh,
                        text = stringResource(R.string.ai_auto_group_readjust),
                        enabled = state.revisionInstruction.isNotBlank(),
                    )
                    MediumPlainButton(
                        onClick = { onIntent(AiAutoGroupIntent.RequestApply) },
                        icon = Icons.Default.PlayArrow,
                        text = stringResource(R.string.ai_auto_group_confirm_execute),
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
                label = stringResource(R.string.ai_auto_group_add_group),
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
                contentDescription = stringResource(R.string.ai_auto_group_add_group),
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
            SummaryMetric(
                label = stringResource(R.string.ai_auto_group_metric_groups),
                value = state.groups.size.toString(),
            )
            SummaryMetric(
                label = stringResource(R.string.ai_auto_group_metric_new),
                value = state.newGroupCount.toString(),
            )
            SummaryMetric(
                label = stringResource(R.string.ai_auto_group_metric_books),
                value = state.assignedBookCount.toString(),
            )
            SummaryMetric(
                label = stringResource(R.string.ai_auto_group_metric_skipped),
                value = state.ignoredBooks.size.toString(),
            )
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
                    label = stringResource(
                        if (group.reuseExisting) R.string.ai_auto_group_reuse_group
                        else R.string.ai_auto_group_new_group
                    ),
                    backgroundColor = LegadoTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.weight(1f),
                )
                MediumPlainButton(
                    onClick = { onIntent(AiAutoGroupIntent.RemoveGroup(group.key)) },
                    icon = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.ai_auto_group_delete_group),
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
    val originalGroups = book.currentGroupNames.takeIf { it.isNotEmpty() }?.let {
        stringResource(R.string.ai_auto_group_original_groups, it.joinToString(", "))
    }
    SelectionItemCard(
        title = book.name,
        subtitle = buildString {
            if (book.author.isNotBlank()) append(book.author)
            if (originalGroups != null) {
                if (isNotBlank()) append(" · ")
                append(originalGroups)
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
                contentDescription = stringResource(R.string.ai_auto_group_ignore),
            )
        },
        dropdownContent = { onDismiss ->
            allGroups.filterNot { it.key == currentGroupKey }.forEach { target ->
                RoundDropdownMenuItem(
                    text = stringResource(R.string.ai_auto_group_move_to, target.name),
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
                text = stringResource(R.string.ai_auto_group_ignored_count, books.size),
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
                    text = stringResource(R.string.ai_auto_group_more_hidden, books.size - 12),
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AutoGroupResultContent(
    result: AiAutoGroupApplyResultUi?,
    onDone: () -> Unit,
    onReset: () -> Unit,
) {
    val resultText = if (result == null) {
        ""
    } else {
        stringResource(
            R.string.ai_auto_group_result,
            result.createdGroupCount,
            result.reusedGroupCount,
            result.updatedBookCount,
            result.ignoredBookCount,
        )
    }
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
                Text(
                    text = resultText,
                    style = LegadoTheme.typography.bodyMedium,
                )
            }
        }
        ConfirmDismissButtonsRow(
            onDismiss = onReset,
            onConfirm = onDone,
            dismissText = stringResource(R.string.ai_auto_group_reanalyze),
            confirmText = stringResource(R.string.complete),
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
                    text = stringResource(R.string.ai_auto_group_failure),
                    style = LegadoTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = message.ifBlank { stringResource(R.string.ai_auto_group_unknown_error) },
                    style = LegadoTheme.typography.bodyMedium,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ConfirmDismissButtonsRow(
            onDismiss = onDismiss,
            onConfirm = onRetry,
            dismissText = stringResource(R.string.close),
            confirmText = stringResource(R.string.ai_auto_group_reanalyze),
        )
    }
}

private val AiAutoGroupMessage.stringRes: Int
    get() = when (this) {
        AiAutoGroupMessage.EnterRevisionInstruction -> R.string.ai_auto_group_message_enter_revision
        AiAutoGroupMessage.NoApplicablePlan -> R.string.ai_auto_group_message_no_plan
        AiAutoGroupMessage.Cancelled -> R.string.ai_auto_group_message_cancelled
        AiAutoGroupMessage.GroupNameRequired -> R.string.ai_auto_group_message_group_name_required
    }

private fun AiAutoGroupErrorUi.localizedText(context: android.content.Context): String {
    val stringRes = when (this) {
        AiAutoGroupErrorUi.EmptyBookshelf -> R.string.ai_auto_group_error_empty_shelf
        AiAutoGroupErrorUi.MissingModel -> R.string.ai_auto_group_error_missing_model
        AiAutoGroupErrorUi.CapacityTooSmall -> R.string.ai_auto_group_error_capacity
        AiAutoGroupErrorUi.GroupCapacityExceeded ->
            R.string.ai_auto_group_error_group_capacity
        AiAutoGroupErrorUi.InvalidResponse -> R.string.ai_auto_group_error_invalid_response
        is AiAutoGroupErrorUi.Unexpected -> R.string.ai_auto_group_error_unexpected
    }
    val base = context.getString(stringRes)
    val detail = (this as? AiAutoGroupErrorUi.Unexpected)?.detail?.takeIf(String::isNotBlank)
    return if (detail == null || detail == base) base else "$base\n$detail"
}
