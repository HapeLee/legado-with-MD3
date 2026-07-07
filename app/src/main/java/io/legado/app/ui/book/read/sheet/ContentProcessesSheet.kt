package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.BookContentProcess
import io.legado.app.domain.model.TextProcessAction
import io.legado.app.ui.book.read.ContentProcessConfigUiState
import io.legado.app.ui.book.read.ContentProcessItemUi
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.MediumToggleButton
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.text.AppText

@Composable
fun ContentProcessesSheet(
    show: Boolean,
    state: ContentProcessConfigUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.content_processes),
    ) {
        when {
            state.isLoading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppCircularProgressIndicator()
                }
            }

            state.errorMessage != null -> {
                AppText(
                    text = state.errorMessage,
                    color = LegadoTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                )
            }

            state.items.isEmpty() -> {
                AppText(
                    text = stringResource(R.string.content_process_empty),
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth(),
                )
            }

            else -> {
                var viewingItem by remember { mutableStateOf<ContentProcessItemUi?>(null) }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        ContentProcessItem(
                            item = item,
                            onClick = { viewingItem = item },
                            onToggle = {
                                onIntent(ReadBookIntent.ToggleContentProcess(item.id, !item.enabled))
                            },
                            onDelete = {
                                onIntent(ReadBookIntent.RequestDeleteContentProcess(item))
                            },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))

                viewingItem?.let { item ->
                    AppAlertDialog(
                        show = true,
                        onDismissRequest = { viewingItem = null },
                        title = contentProcessTitle(item),
                        content = {
                            Column {
                                AppText(
                                    text = stringResource(R.string.ai_text_clean_before),
                                    style = LegadoTheme.typography.labelSmall,
                                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                                )
                                AppText(
                                    text = item.selectedText,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                                )
                                AppText(
                                    text = stringResource(R.string.ai_text_clean_after),
                                    style = LegadoTheme.typography.labelSmall,
                                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                                )
                                AppText(
                                    text = item.replacementText.ifEmpty {
                                        stringResource(R.string.ai_text_clean_delete)
                                    },
                                    modifier = Modifier.padding(top = 2.dp),
                                    color = if (item.replacementText.isEmpty()) {
                                        LegadoTheme.colorScheme.error
                                    } else {
                                        LegadoTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        },
                        confirmText = stringResource(R.string.ok),
                        onConfirm = { viewingItem = null },
                    )
                }
            }
        }
    }

    val deletingItem = state.deleteItem
    AppAlertDialog(
        show = deletingItem != null,
        onDismissRequest = { onIntent(ReadBookIntent.DismissDeleteContentProcess) },
        title = stringResource(R.string.delete),
        text = stringResource(R.string.content_process_delete_confirm_simple),
        confirmText = stringResource(R.string.ok),
        onConfirm = { onIntent(ReadBookIntent.ConfirmDeleteContentProcess) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(ReadBookIntent.DismissDeleteContentProcess) },
    )
}

@Composable
private fun ContentProcessItem(
    item: ContentProcessItemUi,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    NormalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = LegadoTheme.colorScheme.onSheetContent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = contentProcessTitle(item),
                        style = LegadoTheme.typography.labelLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AppText(
                        text = stringResource(R.string.chapter_index_format, item.chapterIndex + 1),
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MediumToggleButton(
                    checked = item.enabled,
                    onCheckedChange = { onToggle() },
                    icon = Icons.Default.VisibilityOff,
                    iconChecked = Icons.Default.Visibility,
                    contentDescription = stringResource(R.string.enable),
                )
                MediumTonalButton(
                    onClick = onDelete,
                    icon = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                )
            }

            Spacer(Modifier.height(10.dp))
            AppText(
                text = stringResource(R.string.ai_text_clean_before),
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
            AppText(
                text = item.selectedText,
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))
            AppText(
                text = stringResource(R.string.ai_text_clean_after),
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
            AppText(
                text = item.replacementText.ifEmpty {
                    stringResource(R.string.ai_text_clean_delete)
                },
                modifier = Modifier.padding(top = 2.dp),
                color = if (item.replacementText.isEmpty()) {
                    LegadoTheme.colorScheme.error
                } else {
                    LegadoTheme.colorScheme.onSurface
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun contentProcessTitle(item: ContentProcessItemUi): String {
    val kind = when (item.kind) {
        BookContentProcess.KIND_AI_CLEAN -> stringResource(R.string.content_process_ai_clean)
        BookContentProcess.KIND_AI_REWRITE -> stringResource(R.string.content_process_ai_rewrite)
        BookContentProcess.KIND_USER_UNDERLINE -> stringResource(R.string.content_process_user_underline)
        BookContentProcess.KIND_USER_HIGHLIGHT -> stringResource(R.string.content_process_user_highlight)
        else -> item.kind
    }
    val action = when (item.actionType) {
        TextProcessAction.TYPE_DELETE -> stringResource(R.string.content_process_delete_action)
        TextProcessAction.TYPE_INSERT_BEFORE,
        TextProcessAction.TYPE_INSERT_AFTER -> stringResource(R.string.content_process_insert_action)
        else -> stringResource(R.string.content_process_replace_action)
    }
    return "$kind · $action"
}
