package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import io.legado.app.ui.book.read.ChapterSummaryUiState
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.toImmutableList

@Composable
fun ChapterSummarySheet(
    show: Boolean,
    state: ChapterSummaryUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = state.chapterTitle.ifBlank {
            stringResource(R.string.ai_chapter_summary)
        },
    ) {
        when {
            state.errorMessage != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppText(
                        text = state.errorMessage,
                        color = LegadoTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(16.dp))
                    SmallTonalButton(
                        onClick = { onIntent(ReadBookIntent.RetryChapterSummary) },
                        text = stringResource(R.string.retry),
                    )
                }
            }

            state.summary.isNotBlank() || state.reasoningText.isNotBlank() -> {
                NormalCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                    containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
                ) {
                    AiGeneratedMessageContent(
                        isUser = false,
                        isAssistant = true,
                        isStreaming = state.isLoading,
                        message = state.toAiChatMessage(),
                        showHeader = false,
                        reasoningAutoExpandWhileStreaming = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }
            }

            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppCircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        AppText(
                            text = stringResource(R.string.ai_chapter_summary_loading),
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun ChapterSummaryUiState.toAiChatMessage(): AiChatMessageUi {
    val parts = buildList {
        if (reasoningText.isNotBlank()) {
            add(AiMessagePart.Reasoning(reasoningText))
        }
        if (summary.isNotBlank()) {
            add(AiMessagePart.Text(summary))
        }
    }.toImmutableList()
    return AiChatMessageUi(
        id = "read_chapter_summary",
        role = AiMessageRole.ASSISTANT,
        parts = parts,
        content = summary,
        reasoning = reasoningText.ifBlank { null },
        toolTrace = null,
        thinkingDuration = thinkingDuration,
        createdAt = System.currentTimeMillis(),
    )
}
