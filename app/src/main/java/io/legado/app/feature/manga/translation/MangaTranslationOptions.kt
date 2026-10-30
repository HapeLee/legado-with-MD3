package io.legado.app.feature.manga.translation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.manga.*
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.SettingItemWithDivider
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText

@Composable
internal fun MangaTranslationOptions(
    state: MangaTranslationUiState,
    onIntent: (MangaTranslationIntent) -> Unit
) {
    val settings = state.settings
    var advanced by rememberSaveable { mutableStateOf(false) }
    var relationHelp by rememberSaveable { mutableStateOf(false) }
    val pageEdit = settings.composition == MangaComposition.AI_PAGE
    val aiEngine = settings.textEngine == MangaTranslateEngine.AI
    // A fused AI request has no separate text stage for another engine to take over.
    val separateTextStage =
        !pageEdit && (settings.locator != MangaLocator.AI || settings.ocrModelId.isNotBlank())
    AppAlertDialog(
        show = relationHelp,
        onDismissRequest = { relationHelp = false },
        title = stringResource(R.string.manga_translation_model_relation),
        confirmText = stringResource(R.string.close),
        onConfirm = { relationHelp = false },
        content = {
            Column(Modifier.fillMaxWidth()) {
                AppText(mangaPipelineSummary(state), style = LegadoTheme.typography.bodySmall)
                AppText(
                    stringResource(R.string.manga_translation_model_relation_roles),
                    style = LegadoTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
                AppText(
                    stringResource(R.string.manga_translation_model_relation_merge),
                    style = LegadoTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        })
    Column(Modifier.fillMaxWidth()) {
        if (!pageEdit) SplicedColumnGroup(title = stringResource(R.string.manga_translation_recognition)) {
            DropdownListSettingItem(
                stringResource(R.string.manga_translation_locator),
                settings.locator.name,
                displayEntries = arrayOf(
                    stringResource(R.string.manga_translation_locator_ai),
                    stringResource(R.string.manga_translation_locator_local),
                    stringResource(R.string.manga_cloud_ocr_title)
                ),
                entryValues = MangaLocator.entries.map { it.name }.toTypedArray()
            ) { value ->
                onIntent(MangaTranslationIntent.ChangeSettings { current ->
                    val locator = MangaLocator.valueOf(value)
                    current.copy(
                        locator = locator,
                        sourceLanguage = if (locator == MangaLocator.LOCAL && current.sourceLanguage !in current.localOcrEngine.sourceLanguages)
                            current.localOcrEngine.sourceLanguages.first() else current.sourceLanguage,
                        // Keeping a non-AI engine here would leave the fused request without a text stage.
                        textEngine = if (locator == MangaLocator.AI && current.textEngine != MangaTranslateEngine.AI &&
                            current.ocrModelId.isBlank()
                        ) MangaTranslateEngine.AI else current.textEngine
                    )
                })
            }
            val sourceLanguages = if (settings.locator == MangaLocator.LOCAL)
                settings.localOcrEngine.sourceLanguages.toList() else listOf(
                "und",
                "ja",
                "en",
                "zh",
                "ko",
                "ru",
                "fr",
                "de",
                "es",
                "it",
                "pt",
                "uk",
                "be",
                "bg",
                "ar",
                "th",
                "vi",
                "id"
            )
            DropdownListSettingItem(
                stringResource(R.string.manga_translation_source),
                settings.sourceLanguage,
                displayEntries = sourceLanguages.map { mangaLanguageName(it) }.toTypedArray(),
                entryValues = sourceLanguages.toTypedArray()
            ) { value ->
                onIntent(MangaTranslationIntent.ChangeSettings { it.copy(sourceLanguage = value) })
            }
            when (settings.locator) {
                MangaLocator.AI -> {
                    val models = state.models.filter { it.text && it.vision }
                    DropdownListSettingItem(
                        stringResource(R.string.manga_ocr_ai_model), settings.ocrModelId,
                        displayEntries = (if (aiEngine) arrayOf(stringResource(R.string.manga_ocr_follow_translation)) else emptyArray()) +
                                models.map { it.name }.toTypedArray(),
                        entryValues = (if (aiEngine) arrayOf("") else emptyArray()) + models.map { it.id }
                            .toTypedArray()
                    ) { value ->
                        onIntent(MangaTranslationIntent.ChangeSettings { it.copy(ocrModelId = value) })
                    }
                }

                MangaLocator.LOCAL -> {
                    DropdownListSettingItem(
                        stringResource(R.string.manga_ocr_local_engine),
                        settings.localOcrEngine.name,
                        displayEntries = MangaLocalOcrEngine.entries.map { mangaOcrEngineName(it) }
                            .toTypedArray(),
                        entryValues = MangaLocalOcrEngine.entries.map { it.name }.toTypedArray()
                    ) { value ->
                        onIntent(MangaTranslationIntent.ChangeSettings { current ->
                            val engine = MangaLocalOcrEngine.valueOf(value)
                            current.copy(
                                localOcrEngine = engine,
                                sourceLanguage = if (current.sourceLanguage in engine.sourceLanguages)
                                    current.sourceLanguage else engine.sourceLanguages.first()
                            )
                        })
                    }
                    if (settings.localOcrEngine.downloadable) SettingItemWithDivider {
                        MangaOcrModelControls(settings.localOcrEngine, state, onIntent)
                    }
                }

                MangaLocator.CLOUD -> MangaCloudOcrControls(state, onIntent)
            }
            if (settings.locator != MangaLocator.AI) MangaOcrOptionsControls(settings, onIntent)
        }
        SplicedColumnGroup(title = stringResource(R.string.manga_translation_basic)) {
            if (separateTextStage) {
                DropdownListSettingItem(
                    stringResource(R.string.manga_translation_engine),
                    settings.textEngine.name,
                    displayEntries = arrayOf(
                        stringResource(R.string.manga_translation_engine_ai),
                        stringResource(R.string.manga_translation_engine_google),
                        stringResource(R.string.manga_translation_engine_follow)
                    ),
                    entryValues = MangaTranslateEngine.entries.map { it.name }.toTypedArray()
                ) { value ->
                    onIntent(MangaTranslationIntent.ChangeSettings {
                        it.copy(
                            textEngine = MangaTranslateEngine.valueOf(
                                value
                            )
                        )
                    })
                }
                val hint = when (settings.textEngine) {
                    MangaTranslateEngine.GOOGLE -> R.string.manga_translation_engine_google_hint
                    MangaTranslateEngine.FOLLOW_SETTINGS -> R.string.manga_translation_engine_follow_hint
                    else -> null
                }
                if (hint != null) AppText(
                    stringResource(hint), style = LegadoTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            val targetLanguages = listOf(
                "zh-CN",
                "zh-TW",
                "en",
                "ja",
                "ko",
                "ru",
                "fr",
                "de",
                "es",
                "it",
                "pt",
                "uk",
                "ar",
                "th",
                "vi",
                "id"
            )
            DropdownListSettingItem(
                stringResource(R.string.manga_translation_target),
                settings.targetLanguage,
                displayEntries = targetLanguages.map { mangaLanguageName(it) }.toTypedArray(),
                entryValues = targetLanguages.toTypedArray()
            ) { value ->
                onIntent(MangaTranslationIntent.ChangeSettings { it.copy(targetLanguage = value) })
            }
            if (!pageEdit && aiEngine) {
                val models = state.models.filter { it.text }
                DropdownListSettingItem(
                    stringResource(R.string.manga_translation_model), settings.modelId,
                    displayEntries = arrayOf(stringResource(R.string.manga_translation_default_model)) + models.map { it.name },
                    entryValues = arrayOf("") + models.map { it.id }
                ) { value ->
                    onIntent(MangaTranslationIntent.ChangeSettings { it.copy(modelId = value) })
                }
            }
            if (!pageEdit) DropdownListSettingItem(
                stringResource(R.string.manga_translation_retry_count),
                settings.retryCount.toString(),
                displayEntries = (0..10).map { it.toString() }.toTypedArray(),
                entryValues = (0..10).map { it.toString() }.toTypedArray()
            ) { value ->
                onIntent(MangaTranslationIntent.ChangeSettings { it.copy(retryCount = value.toInt()) })
            }
            if (state.globalMode && !pageEdit && aiEngine) DropdownListSettingItem(
                stringResource(R.string.ai_thinking_mode), settings.reasoningLevel,
                displayEntries = arrayOf(
                    R.string.ai_thinking_off,
                    R.string.ai_thinking_auto,
                    R.string.ai_reasoning_level_low,
                    R.string.ai_reasoning_level_medium,
                    R.string.ai_reasoning_level_high,
                    R.string.ai_reasoning_level_xhigh,
                    R.string.ai_reasoning_level_max
                ).map { stringResource(it) }.toTypedArray(),
                entryValues = AiReasoningLevel.entries.map { it.storageValue }.toTypedArray()
            ) { value ->
                onIntent(MangaTranslationIntent.ChangeSettings { it.copy(reasoningLevel = value) })
            }
            if (!pageEdit) ClickableSettingItem(
                stringResource(R.string.manga_translation_model_relation),
                onClick = { relationHelp = true })
        }
        SplicedColumnGroup(title = stringResource(R.string.manga_translation_processing)) {
            DropdownListSettingItem(
                stringResource(R.string.manga_translation_composition),
                settings.composition.name,
                displayEntries = arrayOf(
                    stringResource(R.string.manga_translation_smart),
                    stringResource(R.string.manga_translation_patch),
                    stringResource(R.string.manga_translation_ai_repair),
                    stringResource(R.string.manga_translation_ai_region),
                    stringResource(R.string.manga_translation_ai_page)
                ),
                entryValues = MangaComposition.entries.map { it.name }.toTypedArray()
            ) { value ->
                onIntent(MangaTranslationIntent.ChangeSettings { current ->
                    val composition = MangaComposition.valueOf(value)
                    // A whole-page edit owns translation, so a text engine would be left unreachable.
                    current.copy(
                        composition = composition,
                        textEngine = if (composition == MangaComposition.AI_PAGE) MangaTranslateEngine.AI else current.textEngine
                    )
                })
            }
            if (settings.composition == MangaComposition.SMART) {
                DropdownListSettingItem(
                    stringResource(R.string.manga_inpaint_engine),
                    settings.inpaintEngine.name,
                    displayEntries = MangaInpaintEngine.entries.map { mangaInpaintEngineName(it) }
                        .toTypedArray(),
                    entryValues = MangaInpaintEngine.entries.map { it.name }.toTypedArray()
                ) { value ->
                    onIntent(MangaTranslationIntent.ChangeSettings {
                        it.copy(
                            inpaintEngine = MangaInpaintEngine.valueOf(
                                value
                            )
                        )
                    })
                }
                if (settings.inpaintEngine.downloadable) SettingItemWithDivider {
                    MangaInpaintModelControls(settings.inpaintEngine, state, onIntent)
                }
            }
            if (settings.composition in setOf(
                    MangaComposition.AI_PAGE,
                    MangaComposition.AI_REGION,
                    MangaComposition.AI_REPAIR
                )
            ) {
                val models = state.models.filter { it.imageEditing }
                DropdownListSettingItem(
                    stringResource(R.string.manga_translation_image_model), settings.imageModelId,
                    displayEntries = arrayOf(stringResource(R.string.manga_translation_choose_model)) + models.map { it.name },
                    entryValues = arrayOf("") + models.map { it.id }) { value ->
                    onIntent(MangaTranslationIntent.ChangeSettings { it.copy(imageModelId = value) })
                }
            }
            if (settings.composition in setOf(
                    MangaComposition.SMART,
                    MangaComposition.PATCH,
                    MangaComposition.AI_REPAIR
                )
            ) {
                DropdownListSettingItem(
                    stringResource(R.string.manga_translation_font_scale),
                    settings.fontScale.toString(),
                    displayEntries = arrayOf("50%", "75%", "100%", "125%", "150%", "200%"),
                    entryValues = arrayOf("0.5", "0.75", "1.0", "1.25", "1.5", "2.0")
                ) { value ->
                    onIntent(MangaTranslationIntent.ChangeSettings { it.copy(fontScale = value.toFloat()) })
                }
            }
        }
        SplicedColumnGroup {
            MangaSettingsExpansion(
                title = stringResource(R.string.manga_translation_advanced),
                expanded = advanced,
                onExpandChange = { advanced = it },
                expandContent = {
                    if (settings.composition in setOf(
                            MangaComposition.SMART,
                            MangaComposition.PATCH
                        )
                    ) SwitchSettingItem(
                        stringResource(R.string.manga_ai_geometry_diagnostics),
                        checked = settings.geometryDiagnostics
                    ) { value ->
                        onIntent(MangaTranslationIntent.ChangeSettings { it.copy(geometryDiagnostics = value) })
                    }
                    if (settings.composition in setOf(
                            MangaComposition.AI_PAGE,
                            MangaComposition.AI_REGION,
                            MangaComposition.AI_REPAIR
                        )
                    )
                        OutlinedTextField(
                            textStyle = LegadoTheme.typography.bodyLarge,
                            value = settings.imageEditPath,
                            onValueChange = { value ->
                                onIntent(MangaTranslationIntent.ChangeSettings {
                                    it.copy(
                                        imageEditPath = value.trim()
                                    )
                                })
                            },
                            label = {
                                AppText(
                                    stringResource(R.string.manga_translation_edit_path),
                                    style = LegadoTheme.typography.bodySmall,
                                    color = LocalContentColor.current
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                })
        }
    }
}
