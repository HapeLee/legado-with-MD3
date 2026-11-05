package io.legado.app.feature.manga.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.legado.app.R
import io.legado.app.domain.model.manga.MangaComposition
import io.legado.app.domain.model.manga.MangaTranslationStage
import io.legado.app.ui.ai.chat.AiThinkingCard
import io.legado.app.ui.ai.chat.AiThinkingStep
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun MangaTranslationDiagnosticsSheet(
    state: MangaTranslationUiState,
    show: Boolean,
    onDismiss: () -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        title = stringResource(R.string.feature_manga_translation_diagnostics),
        onDismissRequest = onDismiss,
        startAction = { MediumTonalButton(onClick = onDismiss, icon = AppIcons.Back,
            contentDescription = stringResource(R.string.back)) },
    ) {
        val empty = state.ai == null && state.diagnosticPath == null && !state.busy && state.error == null
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (empty) EmptyMessage(
                messageResId = R.string.feature_manga_translation_diagnostics_empty,
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { message ->
                DiagnosticCard {
                    AppText(
                        message,
                        style = LegadoTheme.typography.bodyMedium,
                        color = LegadoTheme.colorScheme.error,
                    )
                }
            }
            if (state.settings.composition == MangaComposition.AI_PAGE ||
                (state.busy && state.progress.stage == MangaTranslationStage.EDITING)
            ) AppText(
                stringResource(R.string.manga_translation_image_thinking_unavailable),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
            if (state.busy || state.elapsedSeconds > 0) DiagnosticCard {
                AppText(
                    stringResource(
                        R.string.manga_translation_elapsed,
                        state.elapsedSeconds,
                        state.elapsedSeconds - state.phaseStartedAtSeconds
                    ),
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 定位图是诊断的主体：有就直接摊开，不再藏在一颗按钮后面。
            state.diagnosticPath?.let { path ->
                DiagnosticCard {
                    AppText(
                        stringResource(R.string.manga_ai_geometry_preview),
                        style = LegadoTheme.typography.titleSmall,
                    )
                    AsyncImage(
                        model = path,
                        contentDescription = stringResource(R.string.manga_ai_geometry_preview),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    AppText(
                        stringResource(R.string.manga_ai_geometry_legend),
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.ai?.let { trace ->
                key(trace.requestId) {
                    DiagnosticCard {
                        AppText(
                            stringResource(
                                R.string.manga_translation_ai_request,
                                trace.modelName,
                                trace.outputCharacters
                            ),
                            style = LegadoTheme.typography.bodyMedium,
                        )
                        trace.reasoningParameters?.let { parameters ->
                            AppText(
                                stringResource(R.string.manga_translation_reasoning_parameters, parameters),
                                style = LegadoTheme.typography.bodySmall,
                                color = LegadoTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (trace.reasoningDisabledRequested && trace.reasoning.isNotBlank()) AppText(
                            stringResource(R.string.manga_translation_reasoning_disable_ignored),
                            color = LegadoTheme.colorScheme.error,
                            style = LegadoTheme.typography.bodySmall,
                        )
                        if (!trace.reasoningCapabilityDeclared && trace.reasoningParameters == null) AppText(
                            stringResource(R.string.manga_translation_reasoning_not_declared),
                            style = LegadoTheme.typography.bodySmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (trace.reasoning.isNotBlank()) AiThinkingCard(
                        steps = persistentListOf(AiThinkingStep.ReasoningStep(trace.reasoning)),
                        isStreaming = trace.running && trace.thinking,
                        durationSeconds = (trace.thinkingMillis / 1000).coerceAtMost(Int.MAX_VALUE.toLong())
                            .toInt(),
                        autoExpandWhileStreaming = false,
                    ) else DiagnosticCard {
                        AppText(
                            stringResource(
                                if (trace.running) R.string.manga_translation_reasoning_pending
                                else R.string.manga_translation_reasoning_absent
                            ),
                            style = LegadoTheme.typography.bodySmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (trace.reasoningTruncated) AppText(
                        stringResource(R.string.manga_translation_reasoning_truncated),
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 诊断块：与 sheet 内其它卡片同色同圆角，内部统一 12dp 边距与 6dp 行距。 */
@Composable
private fun DiagnosticCard(content: @Composable ColumnScope.() -> Unit) {
    NormalCard(
        cornerRadius = 12.dp,
        containerColor = LegadoTheme.colorScheme.onSheetContent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}
