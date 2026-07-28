package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.ui.ai.chat.AiChatMessageUi
import io.legado.app.ui.ai.chat.AiGeneratedMessageContent
import io.legado.app.ui.book.read.AiTextCleanUiState
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.toImmutableList

@Composable
fun AiTextCleanSheet(
    show: Boolean,
    state: AiTextCleanUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = {
            if (!state.isApplying) onDismissRequest()
        },
        title = stringResource(R.string.ai_text_clean),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                TextPreview(
                    title = stringResource(R.string.ai_text_clean_before),
                    text = state.originalText,
                )
                Spacer(Modifier.height(16.dp))

                AppText(
                    text = stringResource(R.string.ai_text_clean_after),
                    style = LegadoTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))

                when {
                    state.errorMessage != null -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppText(
                                text = state.errorMessage,
                                color = LegadoTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(12.dp))
                            SmallTonalButton(
                                onClick = { onIntent(ReadBookIntent.RetryAiTextClean) },
                                text = stringResource(R.string.retry),
                            )
                        }
                    }

                    state.isLoading &&
                            state.streamingText.isBlank() &&
                            state.reasoningText.isBlank() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppCircularProgressIndicator()
                        }
                    }

                    !state.isLoading && state.replacementText.isEmpty() -> {
                        TextPreview(
                            text = stringResource(R.string.ai_text_clean_delete),
                            showTitle = false,
                            deleted = true,
                        )
                    }

                    else -> {
                        NormalCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            AiGeneratedMessageContent(
                                isUser = false,
                                isAssistant = true,
                                isStreaming = state.isLoading,
                                message = state.toAiChatMessage(),
                                showHeader = false,
                                reasoningAutoExpandWhileStreaming = false,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                AppText(
                    text = stringResource(R.string.ai_text_clean_scope_warning),
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    style = LegadoTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(20.dp))
            }

            ConfirmDismissButtonsRow(
                onDismiss = onDismissRequest,
                onConfirm = { onIntent(ReadBookIntent.ConfirmAiTextClean) },
                dismissText = stringResource(R.string.cancel),
                confirmText = stringResource(R.string.ok),
                dismissEnabled = !state.isApplying,
                confirmEnabled = !state.isLoading &&
                        !state.isApplying &&
                        state.errorMessage == null,
            )
        }
    }
}

private fun AiTextCleanUiState.toAiChatMessage(): AiChatMessageUi {
    val displayText = replacementText.ifBlank { streamingText }
    val parts = buildList {
        if (reasoningText.isNotBlank()) {
            add(AiMessagePart.Reasoning(reasoningText))
        }
        if (displayText.isNotBlank()) {
            add(AiMessagePart.Text(displayText))
        }
    }.toImmutableList()
    return AiChatMessageUi(
        id = "read_ai_clean",
        role = AiMessageRole.ASSISTANT,
        parts = parts,
        content = displayText,
        reasoning = reasoningText.ifBlank { null },
        toolTrace = null,
        thinkingDuration = thinkingDuration,
        createdAt = System.currentTimeMillis(),
    )
}

@Composable
private fun TextPreview(
    text: String,
    title: String? = null,
    showTitle: Boolean = true,
    deleted: Boolean = false,
) {
    if (showTitle && title != null) {
        AppText(
            text = title,
            style = LegadoTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(8.dp))
    }
    NormalCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (deleted) {
            LegadoTheme.colorScheme.errorContainer
        } else {
            LegadoTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (deleted) {
            LegadoTheme.colorScheme.onErrorContainer
        } else {
            LegadoTheme.colorScheme.onSurface
        },
    ) {
        SelectionContainer {
            AppText(
                text = text,
                modifier = Modifier.padding(16.dp),
                color = if (deleted) {
                    LegadoTheme.colorScheme.onErrorContainer
                } else {
                    LegadoTheme.colorScheme.onSurface
                },
            )
        }
    }
}
