package io.legado.app.feature.manga.translation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.manga.MangaComposition
import io.legado.app.domain.model.manga.MangaLocalOcrEngine
import io.legado.app.domain.model.manga.MangaLocator
import io.legado.app.domain.model.manga.MangaOcrModelPhase
import io.legado.app.domain.model.manga.MangaOcrModelState
import io.legado.app.domain.model.manga.MangaTranslationStage
import io.legado.app.ui.ai.AiReasoningModeButton
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppLinearProgressIndicator
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import java.util.Locale

@Composable
fun MangaTranslationSheet(
    state: MangaTranslationUiState,
    onOpenGlobalSettings: () -> Unit = {},
    onIntent: (MangaTranslationIntent) -> Unit
) {
    var diagnostics by rememberSaveable { mutableStateOf(false) }
    MangaTranslationDiagnosticsSheet(state, diagnostics && state.open) { diagnostics = false }
    var clearConfirmation by rememberSaveable { mutableStateOf(false) }
    var glossary by rememberSaveable { mutableStateOf(false) }
    AppAlertDialog(
        show = clearConfirmation,
        onDismissRequest = { clearConfirmation = false },
        title = stringResource(R.string.manga_translation_clear),
        text = stringResource(R.string.manga_translation_clear_confirm),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = { clearConfirmation = false; onIntent(MangaTranslationIntent.Clear) },
        onDismiss = { clearConfirmation = false })
    MangaGlossarySheet(
        show = glossary && state.open,
        draft = state.glossaryDraft,
        enabled = !state.busy,
        onDismiss = { glossary = false },
        onDraftChange = { onIntent(MangaTranslationIntent.EditGlossary(it)) },
        onSave = { onIntent(MangaTranslationIntent.SaveGlossary) }
    )
    AppModalBottomSheet(
        show = state.open && !diagnostics,
        title = stringResource(R.string.manga_translation_title),
        startAction = {
            MediumTonalButton(onClick = { diagnostics = true }, icon = Icons.Default.BugReport,
                contentDescription = stringResource(R.string.feature_manga_translation_diagnostics))
        },
        endAction = {
            if (state.settings.composition != MangaComposition.AI_PAGE) AiReasoningModeButton(
                level = AiReasoningLevel.fromStorage(state.settings.reasoningLevel),
                enabled = state.loaded && !state.busy,
                onLevelChange = { level ->
                    onIntent(MangaTranslationIntent.ChangeSettings {
                        it.copy(
                            reasoningLevel = level.storageValue
                        )
                    })
                },
            )
        },
        onDismissRequest = { onIntent(MangaTranslationIntent.Close) }) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {
            if (state.loaded) SplicedColumnGroup(
                Modifier
                    .fillMaxWidth()
            ) {
                TinySwitchSettingItem(
                    stringResource(R.string.manga_translation_automatic_book),
                    checked = state.settings.automatic
                ) { enabled ->
                    onIntent(MangaTranslationIntent.ChangeSettings { it.copy(automatic = enabled) })
                }
            }
            if (state.busy) {
                AppLinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                )
                AppText(
                    stringResource(
                        R.string.manga_translation_progress, state.done, state.total
                    ), modifier = Modifier.padding(16.dp), style = LegadoTheme.typography.bodyMedium
                )
                val phase = state.progress
                val phaseText = when (phase.stage) {
                    MangaTranslationStage.PREPARING -> stringResource(R.string.manga_translation_preparing)
                    MangaTranslationStage.ANALYZING -> if (phase.total == 1) stringResource(R.string.manga_translation_analyzing_page)
                    else stringResource(
                        R.string.manga_translation_analyzing_parts,
                        (phase.completed + 1).coerceAtMost(phase.total),
                        phase.total
                    )

                    MangaTranslationStage.PLANNING -> stringResource(R.string.manga_ai_layout_planning)
                    MangaTranslationStage.EDITING -> if (phase.total == 1) stringResource(R.string.manga_translation_editing_page)
                    else stringResource(
                        R.string.manga_translation_editing_parts,
                        (phase.completed + 1).coerceAtMost(phase.total),
                        phase.total
                    )

                    MangaTranslationStage.COMPOSING -> stringResource(R.string.manga_translation_composing)
                }
                AppText(
                    phaseText,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = LegadoTheme.typography.bodyMedium
                )
                SmallPlainButton(
                    onClick = { onIntent(MangaTranslationIntent.Cancel) },
                    text = stringResource(R.string.cancel)
                )
            }
            state.ocrOperation?.let { engine ->
                if (state.busy || state.settings.locator != MangaLocator.LOCAL || state.settings.localOcrEngine != engine || state.settings.composition == MangaComposition.AI_PAGE) {
                    MangaOcrModelControls(engine, state, onIntent)
                }
            }
            state.inpaintOperation?.let { engine ->
                if (state.busy || state.settings.composition != MangaComposition.SMART || state.settings.inpaintEngine != engine) MangaInpaintModelControls(
                    engine, state, onIntent
                )
            }
            state.error?.let {
                AppText(
                    it,
                    color = LegadoTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                    style = LegadoTheme.typography.bodyMedium
                )
            }
            if (state.loaded && !state.busy) {
                val settings = state.settings
                Column(
                    Modifier
                        .fillMaxWidth()
                ) {
                    TinySwitchSettingItem(
                        stringResource(R.string.manga_translation_use_global),
                        checked = state.useGlobalSettings
                    ) { enabled ->
                        onIntent(MangaTranslationIntent.SetUseGlobalSettings(enabled))
                    }
                    TinyClickableSettingItem(
                        stringResource(R.string.manga_translation_global_settings),
                        onClick = onOpenGlobalSettings
                    )

                    if (!state.useGlobalSettings) MangaTranslationOptions(state, onIntent)

                    TinyClickableSettingItem(
                        stringResource(R.string.manga_translation_glossary),
                        onClick = { glossary = true }
                    )


                    MediumTonalButton(
                        onClick = { onIntent(MangaTranslationIntent.Translate(chapter = true, force = state.chapterTranslated)) },
                        enabled = state.chapterPages > 0,
                        icon = Icons.Default.Translate,
                        modifier = Modifier.fillMaxWidth(), text = stringResource(
                            if (state.chapterTranslated) R.string.feature_manga_translation_retry_chapter else R.string.manga_translation_chapter, state.chapterPages
                        )
                    )
                    if (settings.composition in setOf(
                            MangaComposition.SMART, MangaComposition.PATCH
                        )
                    ) MediumTonalButton(
                        onClick = { onIntent(MangaTranslationIntent.Recompose(chapter = true)) },
                        enabled = state.chapterPages > 0,
                        icon = Icons.Default.Layers,
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.manga_translation_recompose_chapter)
                    )
                    MediumTonalButton(
                        onClick = { clearConfirmation = true },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.Delete,
                        text = stringResource(R.string.manga_translation_clear)
                    )

                }
            }
        }
    }
}

@Composable
internal fun mangaLanguageName(code: String): String = when (code) {
    "und" -> stringResource(R.string.manga_translation_language_auto)
    "zh-CN" -> stringResource(R.string.manga_translation_language_simplified)
    "zh-TW" -> stringResource(R.string.manga_translation_language_traditional)
    else -> Locale.forLanguageTag(code).getDisplayName(LocalLocale.current.platformLocale)
        .ifBlank { code }
}

@Composable
internal fun mangaOcrEngineName(engine: MangaLocalOcrEngine): String = when (engine) {
    MangaLocalOcrEngine.ML_KIT -> stringResource(R.string.manga_ocr_mlkit)
    MangaLocalOcrEngine.PADDLE_V5 -> "PP-OCRv5 mobile"
    MangaLocalOcrEngine.PADDLE_V5_KOREAN -> stringResource(R.string.manga_ocr_v5_korean_name)
    MangaLocalOcrEngine.PADDLE_V5_ESLAV -> stringResource(R.string.manga_ocr_v5_eslav_name)
    MangaLocalOcrEngine.PADDLE_V6_TINY -> "PP-OCRv6 Tiny"
    MangaLocalOcrEngine.PADDLE_V6_SMALL -> "PP-OCRv6 Small"
    MangaLocalOcrEngine.PADDLE_V6_MEDIUM -> "PP-OCRv6 Medium"
    MangaLocalOcrEngine.PADDLE_V6_MANGA -> stringResource(R.string.manga_ocr_v6_manga_name)
}

@Composable
internal fun MangaOcrModelControls(
    engine: MangaLocalOcrEngine,
    state: MangaTranslationUiState,
    onIntent: (MangaTranslationIntent) -> Unit
) {
    val model = state.ocrModels[engine] ?: MangaOcrModelState()
    Column(
        Modifier
            .fillMaxWidth()
    ) {
        AppText(mangaOcrEngineName(engine), style = LegadoTheme.typography.titleSmall)
        AppText(
            stringResource(
                when (engine) {
                    MangaLocalOcrEngine.PADDLE_V5_KOREAN -> R.string.manga_ocr_v5_korean_hint
                    MangaLocalOcrEngine.PADDLE_V5_ESLAV -> R.string.manga_ocr_v5_eslav_hint
                    MangaLocalOcrEngine.PADDLE_V6_TINY -> R.string.manga_ocr_v6_tiny_hint
                    MangaLocalOcrEngine.PADDLE_V6_SMALL -> R.string.manga_ocr_v6_small_hint
                    MangaLocalOcrEngine.PADDLE_V6_MEDIUM -> R.string.manga_ocr_v6_medium_hint
                    MangaLocalOcrEngine.PADDLE_V6_MANGA -> R.string.manga_ocr_v6_manga_hint
                    else -> R.string.manga_ocr_paddle_hint
                }
            ), style = LegadoTheme.typography.bodySmall
        )
        AppText(
            stringResource(
                when (model.phase) {
                    MangaOcrModelPhase.CHECKING -> R.string.manga_ocr_checking
                    MangaOcrModelPhase.MISSING -> R.string.manga_ocr_missing
                    MangaOcrModelPhase.DOWNLOADING -> R.string.manga_ocr_downloading
                    MangaOcrModelPhase.READY -> R.string.manga_ocr_ready
                    MangaOcrModelPhase.FAILED -> R.string.manga_ocr_failed
                }
            ), style = LegadoTheme.typography.bodyMedium
        )
        if (model.totalBytes > 0) AppText(
            stringResource(
                R.string.manga_ocr_download_size,
                model.downloadedBytes / 1048576f,
                model.totalBytes / 1048576f
            ), style = LegadoTheme.typography.bodySmall
        )
        if (model.phase == MangaOcrModelPhase.DOWNLOADING) {
            LinearProgressIndicator(
                progress = {
                    if (model.totalBytes > 0) (model.downloadedBytes.toFloat() / model.totalBytes).coerceIn(
                        0f, 1f
                    ) else 0f
                }, modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
            SmallPlainButton(
                onClick = { onIntent(MangaTranslationIntent.CancelOcrDownload(engine)) },
                text = stringResource(R.string.cancel)
            )
        } else {
            model.error?.let {
                AppText(
                    it,
                    color = LegadoTheme.colorScheme.error,
                    style = LegadoTheme.typography.bodyMedium
                )
            }
            if (model.phase != MangaOcrModelPhase.READY) SmallPlainButton(
                enabled = model.phase != MangaOcrModelPhase.CHECKING && state.ocrOperation == null,
                onClick = { onIntent(MangaTranslationIntent.DownloadOcr(engine)) },
                text = stringResource(R.string.manga_ocr_download)
            )
            if (model.phase != MangaOcrModelPhase.CHECKING) SmallPlainButton(
                enabled = state.ocrOperation == null,
                onClick = { onIntent(MangaTranslationIntent.DeleteOcr(engine)) },
                text = stringResource(R.string.manga_ocr_delete)
            )
        }
    }
}
