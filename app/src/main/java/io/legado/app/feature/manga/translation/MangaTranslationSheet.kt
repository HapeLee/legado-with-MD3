package io.legado.app.feature.manga.translation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Translate
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
import io.legado.app.domain.model.manga.MangaComposition
import io.legado.app.domain.model.manga.MangaLocalOcrEngine
import io.legado.app.domain.model.manga.MangaLocator
import io.legado.app.domain.model.manga.MangaOcrModelPhase
import io.legado.app.domain.model.manga.MangaOcrModelState
import io.legado.app.domain.model.manga.MangaTranslationStage
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.MediumOutlinedButton
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallOutlinedButton
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
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
            if (state.settings.composition in setOf(MangaComposition.SMART, MangaComposition.PATCH))
                MediumTonalButton(
                    onClick = { onIntent(MangaTranslationIntent.Recompose(chapter = true)) },
                    enabled = state.loaded && !state.busy && state.chapterPages > 0,
                    icon = Icons.Default.Layers,
                    contentDescription = stringResource(R.string.manga_translation_recompose_chapter),
                )
        },
        endAction = {
            MediumTonalButton(
                onClick = {
                    onIntent(
                        MangaTranslationIntent.Translate(
                            chapter = true, force = state.chapterTranslated
                        )
                    )
                },
                enabled = state.loaded && !state.busy && state.chapterPages > 0,
                icon = Icons.Default.Translate,
                contentDescription = stringResource(
                    if (state.chapterTranslated) R.string.feature_manga_translation_retry_chapter
                    else R.string.manga_translation_chapter, state.chapterPages
                ),
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
            if (state.loaded) TinySwitchSettingItem(
                stringResource(R.string.manga_translation_automatic_book),
                checked = state.settings.automatic,
                color = LegadoTheme.colorScheme.onSheetContent,
            ) { enabled ->
                onIntent(MangaTranslationIntent.ChangeSettings { it.copy(automatic = enabled) })
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
            // 轻量产品不内置本地运行时，因此不出现权重下载区块。
            if (LocalMangaCapabilities.modelDownload) {
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
                Column(
                    Modifier
                        .fillMaxWidth()
                ) {
                    TinySwitchSettingItem(
                        stringResource(R.string.manga_translation_use_global),
                        checked = state.useGlobalSettings,
                        color = LegadoTheme.colorScheme.onSheetContent,
                    ) { enabled ->
                        onIntent(MangaTranslationIntent.SetUseGlobalSettings(enabled))
                    }
                    // 只有全局生效时这条入口才有意义：本漫单独设置时它改不动眼前这些选项。
                    if (state.useGlobalSettings) TinyClickableSettingItem(
                        stringResource(R.string.manga_translation_global_settings),
                        color = LegadoTheme.colorScheme.onSheetContent,
                        onClick = onOpenGlobalSettings
                    )
                    // 诊断紧跟设置入口：它解释的正是这组设置刚才发出了什么请求。
                    TinyClickableSettingItem(
                        stringResource(R.string.feature_manga_translation_diagnostics),
                        color = LegadoTheme.colorScheme.onSheetContent,
                        onClick = { diagnostics = true }
                    )

                    // 顶栏只剩翻译/重新排版两颗图标按钮，思考强度回到基础设置组内。
                    if (!state.useGlobalSettings) MangaTranslationOptions(
                        state = state,
                        onIntent = onIntent,
                        storedSettings = state.storedSettings,
                        tiny = true,
                    )

                    TinyClickableSettingItem(
                        stringResource(R.string.manga_translation_glossary),
                        color = LegadoTheme.colorScheme.onSheetContent,
                        onClick = { glossary = true }
                    )

                    MediumOutlinedButton(
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
    val downloading = model.phase == MangaOcrModelPhase.DOWNLOADING
    MangaModelSection(
        title = mangaOcrEngineName(engine),
        hint = stringResource(
            when (engine) {
                MangaLocalOcrEngine.PADDLE_V5_KOREAN -> R.string.manga_ocr_v5_korean_hint
                MangaLocalOcrEngine.PADDLE_V5_ESLAV -> R.string.manga_ocr_v5_eslav_hint
                MangaLocalOcrEngine.PADDLE_V6_TINY -> R.string.manga_ocr_v6_tiny_hint
                MangaLocalOcrEngine.PADDLE_V6_SMALL -> R.string.manga_ocr_v6_small_hint
                MangaLocalOcrEngine.PADDLE_V6_MEDIUM -> R.string.manga_ocr_v6_medium_hint
                MangaLocalOcrEngine.PADDLE_V6_MANGA -> R.string.manga_ocr_v6_manga_hint
                else -> R.string.manga_ocr_paddle_hint
            }
        ),
        status = stringResource(
            when (model.phase) {
                MangaOcrModelPhase.CHECKING -> R.string.manga_ocr_checking
                MangaOcrModelPhase.MISSING -> R.string.manga_ocr_missing
                MangaOcrModelPhase.DOWNLOADING -> R.string.manga_ocr_downloading
                MangaOcrModelPhase.READY -> R.string.manga_ocr_ready
                MangaOcrModelPhase.FAILED -> R.string.manga_ocr_failed
            }
        ),
        statusColor = mangaModelStatusColor(ready = model.phase == MangaOcrModelPhase.READY,
            failed = model.phase == MangaOcrModelPhase.FAILED, running = downloading),
        progress = if (downloading) (model.downloadedBytes.toFloat() / model.totalBytes.coerceAtLeast(1)).coerceIn(0f, 1f) else null,
        sizeText = when {
            downloading -> stringResource(R.string.manga_ocr_download_size,
                model.downloadedBytes / 1048576f, model.totalBytes / 1048576f)
            model.phase == MangaOcrModelPhase.READY && model.totalBytes > 0 ->
                stringResource(R.string.manga_model_size, model.totalBytes / 1048576f)
            else -> null
        },
        error = model.error,
    ) {
        if (downloading) SmallOutlinedButton(
            onClick = { onIntent(MangaTranslationIntent.CancelOcrDownload(engine)) },
            text = stringResource(R.string.cancel))
        else {
            if (model.phase != MangaOcrModelPhase.READY) SmallTonalButton(
                enabled = model.phase != MangaOcrModelPhase.CHECKING && state.ocrOperation == null,
                icon = Icons.Default.Download,
                onClick = { onIntent(MangaTranslationIntent.DownloadOcr(engine)) },
                text = stringResource(R.string.manga_ocr_download))
            if (model.phase != MangaOcrModelPhase.CHECKING) SmallOutlinedButton(
                enabled = state.ocrOperation == null,
                icon = Icons.Default.Delete,
                onClick = { onIntent(MangaTranslationIntent.DeleteOcr(engine)) },
                text = stringResource(R.string.manga_ocr_delete))
        }
    }
}
