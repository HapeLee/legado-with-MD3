package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.ui.ai.chat.AiChatMessageUi
import io.legado.app.ui.ai.chat.AiGeneratedMessageContent
import io.legado.app.ui.book.read.AiRewritePresetUi
import io.legado.app.ui.book.read.AiTextRewriteUiState
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppRadioButton
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.button.PrimaryButton
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallOutlinedButton
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.toImmutableList

@Composable
fun AiTextRewriteSheet(
    show: Boolean,
    state: AiTextRewriteUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = {
            if (!state.isApplying) onDismissRequest()
        },
        title = stringResource(R.string.ai_text_rewrite),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                var selectedTabIndex by remember { mutableIntStateOf(0) }
                var originalTextExpanded by remember { mutableStateOf(false) }

                CardTabRow(
                    tabTitles = listOf(
                        stringResource(R.string.ai_text_clean_before),
                        stringResource(R.string.ai_text_clean_after),
                    ),
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = { selectedTabIndex = it },
                )
                Spacer(Modifier.height(8.dp))

                when (selectedTabIndex) {
                    0 -> {
                        CollapsibleTextPreview(
                            text = state.originalText,
                            expanded = originalTextExpanded,
                            onToggleExpand = { originalTextExpanded = !originalTextExpanded },
                        )
                        Spacer(Modifier.height(16.dp))

                        PrimaryButton(
                            onClick = { onIntent(ReadBookIntent.GenerateAiTextRewrite) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.presets.isNotEmpty() &&
                                    !state.isLoading &&
                                    !state.isApplying,
                            text = if (state.rewrittenText.isBlank()) {
                                stringResource(R.string.ai_generate)
                            } else {
                                stringResource(R.string.ai_regenerate)
                            },
                        )

                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppText(
                                text = stringResource(R.string.ai_rewrite_requirements),
                                modifier = Modifier.weight(1f),
                                style = LegadoTheme.typography.titleSmall,
                            )
                            SmallOutlinedButton(
                                onClick = { onIntent(ReadBookIntent.OpenAiRewritePresetConfig) },
                                icon = Icons.Default.Settings,
                                text = stringResource(R.string.ai_rewrite_manage_presets),
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        if (state.presets.isEmpty()) {
                            AppText(
                                text = stringResource(R.string.ai_rewrite_no_preset),
                                color = LegadoTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.presets.forEach { preset ->
                                    RewritePresetOption(
                                        preset = preset,
                                        selected = preset.id == state.selectedPresetId,
                                        enabled = !state.isLoading && !state.isApplying,
                                        onSelect = {
                                            onIntent(ReadBookIntent.SelectAiRewritePreset(preset.id))
                                        },
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        AppTextField(
                            value = state.temporaryInstruction,
                            onValueChange = {
                                onIntent(ReadBookIntent.SetAiRewriteTemporaryInstruction(it))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading && !state.isApplying,
                            label = stringResource(R.string.ai_rewrite_temporary_instruction),
                            placeholder = {
                                AppText(stringResource(R.string.ai_rewrite_temporary_instruction_hint))
                            },
                            minLines = 2,
                            maxLines = 5,
                        )
                    }

                    1 -> {
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
                                        onClick = { onIntent(ReadBookIntent.RetryAiTextRewrite) },
                                        enabled = state.presets.isNotEmpty(),
                                        text = stringResource(R.string.retry),
                                    )
                                }
                            }

                            state.rewrittenText.isNotBlank() || state.reasoningText.isNotBlank() -> {
                                if (state.referenceCount > 0) {
                                    AppText(
                                        text = stringResource(
                                            R.string.ai_rewrite_reference_used,
                                            state.referenceCount,
                                        ),
                                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                                        style = LegadoTheme.typography.bodySmall,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
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

                            state.isLoading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AppCircularProgressIndicator()
                                }
                            }

                            else -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    EmptyMessage(
                                        messageResId = R.string.ai_rewrite_generate_hint,
                                    )
                                }
                            }
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
                onConfirm = { onIntent(ReadBookIntent.ConfirmAiTextRewrite) },
                dismissText = stringResource(R.string.cancel),
                confirmText = stringResource(R.string.ok),
                dismissEnabled = !state.isApplying,
                confirmEnabled = !state.isLoading &&
                        !state.isApplying &&
                        state.errorMessage == null &&
                        state.rewrittenText.isNotBlank(),
            )
        }
    }
}

private fun AiTextRewriteUiState.toAiChatMessage(): AiChatMessageUi {
    val parts = buildList {
        reasoningText.takeIf { it.isNotBlank() }?.let {
            add(AiMessagePart.Reasoning(it))
        }
        rewrittenText.takeIf { it.isNotBlank() }?.let {
            add(AiMessagePart.Text(it))
        }
    }
    return AiChatMessageUi(
        id = "read_ai_rewrite",
        role = AiMessageRole.ASSISTANT,
        parts = parts.toImmutableList(),
        content = rewrittenText,
        reasoning = reasoningText.ifBlank { null },
        toolTrace = null,
        createdAt = System.currentTimeMillis(),
        thinkingDuration = thinkingDuration,
    )
}

@Composable
private fun RewritePresetOption(
    preset: AiRewritePresetUi,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    NormalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (enabled) onSelect else null,
        containerColor = if (selected) {
            LegadoTheme.colorScheme.primaryContainer
        } else {
            LegadoTheme.colorScheme.onSheetContent
        },
        contentColor = if (selected) {
            LegadoTheme.colorScheme.onPrimaryContainer
        } else {
            LegadoTheme.colorScheme.onSurface
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppRadioButton(
                selected = selected,
                onClick = onSelect,
                enabled = enabled,
            )
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = preset.name,
                    style = LegadoTheme.typography.labelLargeEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AppText(
                    text = preset.instruction,
                    style = LegadoTheme.typography.bodySmall,
                    color = if (selected) {
                        LegadoTheme.colorScheme.onPrimaryContainer
                    } else {
                        LegadoTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TextPreview(
    text: String,
    title: String? = null,
    showTitle: Boolean = true,
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
        containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
    ) {
        SelectionContainer {
            AppText(
                text = text,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun CollapsibleTextPreview(
    text: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    NormalCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SelectionContainer {
                AppText(
                    text = text,
                    maxLines = if (expanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            SmallTonalButton(
                onClick = onToggleExpand,
                text = stringResource(
                    if (expanded) R.string.collapse else R.string.expand,
                ),
            )
        }
    }
}
