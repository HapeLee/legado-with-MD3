package io.legado.app.feature.mangatranslation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.MangaTranslationEngine
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.R
import io.legado.app.domain.gateway.MangaTranslationStage
import io.legado.app.domain.gateway.MangaLayoutDirection
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem

@Stable
data class MangaTranslationUiState(
    val autoTranslation: Boolean = false,
    val engine: MangaTranslationEngine = MangaTranslationEngine.OFFLINE,
    val aiDescription: String = "",
    val pageKey: String = "",
    val stage: MangaTranslationStage? = null,
    val sourceLanguage: String = "ja",
    val targetLanguage: String = "zh",
    val messageRes: Int? = null,
    val regionDetails: String = "",
    val layoutDirection: MangaLayoutDirection = MangaLayoutDirection.AUTO,
)

/** Translation controls embedded in the reader menu, inheriting its theme and scroll owner. */
@Composable
fun MangaTranslationContent(
    state: MangaTranslationUiState,
    hasPage: Boolean,
    hasTranslation: Boolean,
    showingOriginal: Boolean,
    onLanguages: (String, String) -> Unit,
    onTranslate: () -> Unit,
    onToggleOriginal: () -> Unit,
    onCancel: () -> Unit,
    onClearCache: () -> Unit,
    onAutoTranslation: (Boolean) -> Unit,
    onEngine: (MangaTranslationEngine) -> Unit,
    modifier: Modifier = Modifier,
    onLayoutDirection: (MangaLayoutDirection) -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var modelsVisible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        TinyClickableSettingItem(title = stringResource(R.string.manga_models_title), onClick = { modelsVisible = !modelsVisible })
        if (modelsVisible) MangaModelsRoute()
        TranslationCard {
            Text(stringResource(R.string.manga_translation_layout_direction), style = LegadoTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MangaLayoutDirection.entries.forEach { direction ->
                    LanguageChip(stringResource(when (direction) {
                        MangaLayoutDirection.AUTO -> R.string.manga_translation_layout_auto
                        MangaLayoutDirection.HORIZONTAL -> R.string.manga_translation_layout_horizontal
                        MangaLayoutDirection.VERTICAL -> R.string.manga_translation_layout_vertical
                    }), state.layoutDirection == direction, state.stage == null) { onLayoutDirection(direction) }
                }
            }
        }
        if (state.regionDetails.isNotBlank()) TranslationCard {
            androidx.compose.foundation.text.selection.SelectionContainer { Text(state.regionDetails) }
        }
        TranslationCard {
            Text(
                stringResource(R.string.manga_translation_notice),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
        }
        TinySwitchSettingItem(
            title = stringResource(R.string.manga_translation_auto),
            description = stringResource(R.string.manga_translation_auto_desc),
            checked = state.autoTranslation, onCheckedChange = onAutoTranslation,
        )
        TranslationCard {
            Text(stringResource(R.string.manga_translation_engine), style = LegadoTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MangaTranslationEngine.entries.forEach { engine ->
                    LanguageChip(
                        label = stringResource(if (engine == MangaTranslationEngine.AI) R.string.manga_translation_ai
                            else R.string.manga_translation_offline),
                        selected = state.engine == engine, enabled = true,
                        onClick = { onEngine(engine) },
                    )
                }
            }
            if (state.engine == MangaTranslationEngine.AI) {
                Text(state.aiDescription.ifBlank { stringResource(R.string.manga_translation_ai_configuration) },
                    style = LegadoTheme.typography.bodySmall)
                Text(stringResource(R.string.manga_translation_ai_notice), style = LegadoTheme.typography.bodySmall)
            }
        }
        TranslationCard {
            Text(stringResource(R.string.manga_translation_source),
                style = LegadoTheme.typography.bodyLarge, color = LegadoTheme.colorScheme.onSurface)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "ja" to R.string.manga_translation_japanese,
                    "en" to R.string.manga_translation_english,
                    "ko" to R.string.manga_translation_korean,
                ).forEach { (code, label) ->
                    LanguageChip(
                        label = stringResource(label),
                        selected = state.sourceLanguage == code,
                        enabled = state.stage == null,
                        onClick = {
                            onLanguages(code, if (state.targetLanguage == code) "zh" else state.targetLanguage)
                        },
                    )
                }
            }
        }
        TranslationCard {
            Text(stringResource(R.string.manga_translation_target),
                style = LegadoTheme.typography.bodyLarge, color = LegadoTheme.colorScheme.onSurface)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "zh" to R.string.manga_translation_chinese,
                    "en" to R.string.manga_translation_english,
                    "ja" to R.string.manga_translation_japanese,
                    "ko" to R.string.manga_translation_korean,
                ).forEach { (code, label) ->
                    if (code != state.sourceLanguage) {
                        LanguageChip(
                            label = stringResource(label), selected = state.targetLanguage == code,
                            enabled = state.stage == null,
                            onClick = { onLanguages(state.sourceLanguage, code) },
                        )
                    }
                }
            }
        }
        if (state.stage != null || state.messageRes != null) {
            TranslationCard {
                state.stage?.let { stage ->
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = LegadoTheme.colorScheme.primary,
                        trackColor = LegadoTheme.colorScheme.surfaceContainerHigh,
                    )
                    Text(
                        stringResource(when (stage) {
                            MangaTranslationStage.PREPARING -> R.string.manga_translation_preparing
                            MangaTranslationStage.RECOGNIZING -> R.string.manga_translation_recognizing
                            MangaTranslationStage.DOWNLOADING -> R.string.manga_translation_downloading
                            MangaTranslationStage.TRANSLATING -> R.string.manga_translation_translating
                            MangaTranslationStage.RENDERING -> R.string.manga_translation_rendering
                        }),
                        style = LegadoTheme.typography.bodyMedium,
                        color = LegadoTheme.colorScheme.onSurface,
                    )
                }
                state.messageRes?.let {
                    Text(stringResource(it), style = LegadoTheme.typography.bodyMedium,
                        color = LegadoTheme.colorScheme.onSurface)
                }
            }
        }
        MediumTonalButton(
            onClick = if (state.stage == null) onTranslate else onCancel,
            text = stringResource(if (state.stage == null) R.string.manga_translation_start else R.string.cancel),
            enabled = state.stage != null || hasPage,
            selected = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 4.dp),
        )
        if (hasTranslation) {
            TinyClickableSettingItem(
                title = stringResource(if (showingOriginal) R.string.manga_translation_show_result
                    else R.string.manga_translation_show_original),
                onClick = onToggleOriginal,
            )
        }
        TinyClickableSettingItem(
            title = stringResource(R.string.manga_translation_clear_cache),
            enabled = state.stage == null,
            onClick = onClearCache,
        )
    }
}

@Composable
private fun TranslationCard(content: @Composable ColumnScope.() -> Unit) {
    NormalCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 12.dp,
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
        contentColor = LegadoTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun LanguageChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = LegadoTheme.colorScheme
    FilterChip(
        selected = selected, enabled = enabled, onClick = onClick,
        label = { Text(label, style = LegadoTheme.typography.labelLarge) },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.heightIn(min = 48.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.surfaceContainerLow,
            labelColor = colors.onSurfaceVariant,
            selectedContainerColor = colors.secondaryContainer,
            selectedLabelColor = colors.onSecondaryContainer,
            disabledContainerColor = colors.surfaceContainerLow,
            disabledLabelColor = colors.onSurfaceVariant.copy(alpha = 0.38f),
            disabledSelectedContainerColor = colors.secondaryContainer.copy(alpha = 0.38f),
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled, selected = selected, borderColor = colors.outlineVariant,
            selectedBorderColor = colors.secondaryContainer,
        ),
    )
}
