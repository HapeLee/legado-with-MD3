package io.legado.app.feature.manga.translation

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.manga.*
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.SettingItemWithDivider
import io.legado.app.ui.widget.components.alert.MarkdownDialog
import io.legado.app.ui.widget.components.card.InfoCard
import io.legado.app.ui.widget.components.card.InfoNote
import io.legado.app.ui.widget.components.icon.AppIcons

@Composable
internal fun MangaTranslationOptions(
    state: MangaTranslationUiState,
    onIntent: (MangaTranslationIntent) -> Unit,
    /**
     * 存储值。单书面板传 `state.storedSettings` 以说明"保存过的端侧选择被换掉了"；
     * 全局设置页不传，避免把某本书的存储差异带到全局面板。
     */
    storedSettings: MangaTranslationSettings = state.settings,
    /**
     * 单书面板放在底部 sheet 里，整行沿用 Tiny 系列；全局设置页是独立页面，仍用完整设置项。
     */
    tiny: Boolean = false,
) {
    val settings = state.settings
    var advanced by rememberSaveable { mutableStateOf(false) }
    var relationHelp by rememberSaveable { mutableStateOf(false) }
    val capabilities = LocalMangaCapabilities
    // 哪一项生效、为什么没生效，只在这里判定一次；运行时链路和界面共用同一张表。
    val relevance = remember(settings, capabilities, storedSettings) {
        MangaSettingsRelevance.of(settings, capabilities, storedSettings)
    }
    val pageEdit = settings.composition == MangaComposition.AI_PAGE
    // 单书面板挂在 sheet 上，卡片底色跟随 sheet 的内容色；独立设置页沿用内容卡片的浅底。
    val cardContainer = if (tiny) LegadoTheme.colorScheme.onSheetContent
    else LegadoTheme.colorScheme.surfaceContainerLow
    // tiny 系列是 12dp 圆角 + 12dp 内边距，说明卡跟着走；完整密度仍是窄块说明。
    val noteRadius = if (tiny) 12.dp else 2.dp
    val notePadding = if (tiny) PaddingValues(horizontal = 12.dp, vertical = 12.dp)
    else PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    // 可选作识别模型的视觉模型：文本类且带视觉能力，顺序与识别模型下拉一致。
    val visionModels = remember(state.models) { state.models.filter { it.text && it.vision } }
    // 产物不提供的档位仍然列出并标注"仅漫画增强版"：让用户看到能力差异，但不允许选中。
    val unavailableLocators = if (capabilities.localEngine) emptySet() else setOf(MangaLocator.LOCAL)
    val locators = MangaLocator.entries
    // miuix 引擎的 spinner 无法把单项压灰，至少要点出"这一档需要漫画增强版"。
    val context = LocalContext.current
    val comicOnlyHint = stringResource(R.string.manga_comic_only_hint)
    val notifyComicOnly: (String) -> Unit = remember(context, comicOnlyHint) {
        { Toast.makeText(context, comicOnlyHint, Toast.LENGTH_SHORT).show() }
    }
    MarkdownDialog(
        show = relationHelp,
        title = stringResource(R.string.manga_translation_model_relation),
        content = stringResource(R.string.manga_translation_model_relation_roles) + "\n\n" +
                stringResource(R.string.manga_translation_model_relation_merge),
        onDismissRequest = { relationHelp = false }
    )
    Column(Modifier.fillMaxWidth()) {
        MangaOptionGroup(title = stringResource(R.string.manga_translation_recognition), tiny = tiny) {
            InfoNote(
                text = stringResource(R.string.manga_translation_recognition_hint),
                detail = mangaGroupDetail(
                    stringResource(R.string.manga_translation_recognition_detail),
                    relevance,
                    listOf(
                        MangaSettingField.LOCATOR,
                        MangaSettingField.OCR_MODEL,
                        MangaSettingField.LOCAL_OCR_ENGINE,
                        MangaSettingField.CLOUD_OCR,
                        MangaSettingField.OCR_OPTIONS,
                    )
                ),
                detailTitle = stringResource(R.string.manga_translation_recognition),
                cornerRadius = noteRadius,
                contentPadding = notePadding,
                containerColor = cardContainer,
                modifier = if (tiny) Modifier.padding(bottom = 4.dp) else Modifier
            )
            MangaOptionDropdown(
                title = stringResource(R.string.manga_translation_locator),
                selectedValue = settings.locator.name,
                displayEntries = locators.map { locator ->
                    if (locator in unavailableLocators) stringResource(R.string.manga_comic_only, mangaLocatorName(locator))
                    else mangaLocatorName(locator)
                }.toTypedArray(),
                entryValues = locators.map { it.name }.toTypedArray(),
                enabled = relevance.isActive(MangaSettingField.LOCATOR),
                unavailableValues = unavailableLocators.map { it.name }.toSet(),
                onUnavailableSelected = notifyComicOnly
            ) { value ->
                onIntent(MangaTranslationIntent.ChangeSettings { current ->
                    val locator = MangaLocator.valueOf(value)
                    current.copy(
                        locator = locator,
                        sourceLanguage = if (locator == MangaLocator.LOCAL && current.sourceLanguage !in current.localOcrEngine.sourceLanguages)
                            current.localOcrEngine.sourceLanguages.first() else current.sourceLanguage,
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
            MangaOptionDropdown(
                stringResource(R.string.manga_translation_source),
                settings.sourceLanguage,
                displayEntries = sourceLanguages.map { mangaLanguageName(it) }.toTypedArray(),
                entryValues = sourceLanguages.toTypedArray()
            ) { value ->
                onIntent(MangaTranslationIntent.ChangeSettings { it.copy(sourceLanguage = value) })
            }
            // 识别方式的分支配置互相排斥，只渲染选中的那一支。
            if (relevance.isActive(MangaSettingField.LOCATOR)) when (settings.locator) {
                MangaLocator.AI -> {
                    // "识别跟随翻译模型"对任何引擎都合法：非 AI 引擎下由该模型单独跑识别阶段。
                    MangaOptionDropdown(
                        stringResource(R.string.manga_ocr_ai_model), settings.ocrModelId,
                        displayEntries = arrayOf(stringResource(R.string.manga_ocr_follow_translation)) +
                                visionModels.map { it.name }.toTypedArray(),
                        entryValues = arrayOf("") + visionModels.map { it.id }.toTypedArray()
                    ) { value ->
                        onIntent(MangaTranslationIntent.ChangeSettings { it.copy(ocrModelId = value) })
                    }
                }

                MangaLocator.LOCAL -> {
                    // 轻量产物不会走到这里：本地档位置灰且读取设置时已回落 AI。
                    if (capabilities.localEngine) {
                        MangaOptionDropdown(
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
                        if (settings.localOcrEngine.downloadable) {
                            if (tiny) MangaOcrModelControls(settings.localOcrEngine, state, onIntent)
                            else SettingItemWithDivider {
                                MangaOcrModelControls(settings.localOcrEngine, state, onIntent)
                            }
                        }
                    }
                }

                MangaLocator.CLOUD -> MangaCloudOcrControls(state, onIntent)
            }
            // 预处理/阈值是一整块子面板，无法只置灰其中一项；不生效时整块不渲染，
            // 原因写在分组说明的详情里。
            if (relevance.isActive(MangaSettingField.OCR_OPTIONS)) MangaOcrOptionsControls(settings, onIntent)
        }
        MangaOptionGroup(title = stringResource(R.string.manga_translation_basic), tiny = tiny) {
            InfoNote(
                text = stringResource(R.string.manga_translation_basic_hint),
                detail = mangaGroupDetail(
                    stringResource(R.string.manga_translation_basic_detail),
                    relevance,
                    listOf(
                        MangaSettingField.TEXT_ENGINE,
                        MangaSettingField.TEXT_MODEL,
                        MangaSettingField.REASONING_LEVEL,
                    )
                ),
                detailTitle = stringResource(R.string.manga_translation_basic),
                cornerRadius = noteRadius,
                contentPadding = notePadding,
                containerColor = cardContainer,
                modifier = if (tiny) Modifier.padding(bottom = 4.dp) else Modifier
            )
            MangaOptionDropdown(
                stringResource(R.string.manga_translation_engine),
                settings.textEngine.name,
                displayEntries = arrayOf(
                    stringResource(R.string.manga_translation_engine_ai),
                    stringResource(R.string.manga_translation_engine_google),
                    stringResource(R.string.manga_translation_engine_follow)
                ),
                entryValues = MangaTranslateEngine.entries.map { it.name }.toTypedArray(),
                enabled = relevance.isActive(MangaSettingField.TEXT_ENGINE)
            ) { value ->
                onIntent(MangaTranslationIntent.ChangeSettings {
                    it.copy(textEngine = MangaTranslateEngine.valueOf(value))
                })
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
            MangaOptionDropdown(
                stringResource(R.string.manga_translation_target),
                settings.targetLanguage,
                displayEntries = targetLanguages.map { mangaLanguageName(it) }.toTypedArray(),
                entryValues = targetLanguages.toTypedArray()
            ) { value ->
                onIntent(MangaTranslationIntent.ChangeSettings { it.copy(targetLanguage = value) })
            }
            val models = state.models.filter { it.text }
            MangaOptionDropdown(
                stringResource(R.string.manga_translation_model), settings.modelId,
                displayEntries = arrayOf(stringResource(R.string.manga_translation_default_model)) + models.map { it.name },
                entryValues = arrayOf("") + models.map { it.id },
                enabled = relevance.isActive(MangaSettingField.TEXT_MODEL)
            ) { value ->
                onIntent(MangaTranslationIntent.ChangeSettings { it.copy(modelId = value) })
            }
            // AI 视觉识别本身就发 AI 请求（合并态即用翻译模型），思考强度与引擎无关。
            if (settings.textEngine == MangaTranslateEngine.AI || settings.usesAiTypesetting) MangaOptionDropdown(
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
                entryValues = AiReasoningLevel.entries.map { it.storageValue }.toTypedArray(),
                enabled = relevance.isActive(MangaSettingField.REASONING_LEVEL)
            ) { value ->
                onIntent(MangaTranslationIntent.ChangeSettings { it.copy(reasoningLevel = value) })
            }
            MangaOptionDropdown(
                stringResource(R.string.manga_translation_retry_count),
                settings.retryCount.toString(),
                displayEntries = (0..10).map { it.toString() }.toTypedArray(),
                entryValues = (0..10).map { it.toString() }.toTypedArray()
            ) { value ->
                onIntent(MangaTranslationIntent.ChangeSettings { it.copy(retryCount = value.toInt()) })
            }
        }
        MangaOptionGroup(title = stringResource(R.string.manga_translation_processing), tiny = tiny) {
            InfoNote(
                text = stringResource(R.string.manga_translation_processing_hint),
                detail = mangaGroupDetail(
                    stringResource(R.string.manga_translation_processing_detail),
                    relevance,
                    listOf(
                        MangaSettingField.INPAINT_ENGINE,
                        MangaSettingField.BUBBLE_SOURCE,
                        MangaSettingField.IMAGE_MODEL,
                        MangaSettingField.IMAGE_EDIT_PATH,
                        MangaSettingField.FONT_SCALE,
                        MangaSettingField.GEOMETRY_DIAGNOSTICS,
                    )
                ),
                detailTitle = stringResource(R.string.manga_translation_processing),
                cornerRadius = noteRadius,
                contentPadding = notePadding,
                containerColor = cardContainer,
                modifier = if (tiny) Modifier.padding(bottom = 4.dp) else Modifier
            )
            MangaOptionDropdown(
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
            // 模型擦字只有漫画增强版能跑：core 里列出但置灰，权重下载也不出现。
            val unavailableInpaint = if (capabilities.localEngine) emptySet()
            else MangaInpaintEngine.entries.filter { it.usesModel }.toSet()
            val inpaintEngines = MangaInpaintEngine.entries
            MangaOptionDropdown(
                stringResource(R.string.manga_inpaint_engine),
                settings.inpaintEngine.name,
                displayEntries = inpaintEngines.map { engine ->
                    if (engine in unavailableInpaint) stringResource(R.string.manga_comic_only, mangaInpaintEngineName(engine))
                    else mangaInpaintEngineName(engine)
                }.toTypedArray(),
                entryValues = inpaintEngines.map { it.name }.toTypedArray(),
                enabled = relevance.isActive(MangaSettingField.INPAINT_ENGINE),
                unavailableValues = unavailableInpaint.map { it.name }.toSet(),
                onUnavailableSelected = notifyComicOnly
            ) { value ->
                onIntent(MangaTranslationIntent.ChangeSettings {
                    it.copy(inpaintEngine = MangaInpaintEngine.valueOf(value))
                })
            }
            if (relevance.isActive(MangaSettingField.INPAINT_ENGINE) && settings.inpaintEngine.downloadable) {
                if (tiny) MangaInpaintModelControls(settings.inpaintEngine, state, onIntent)
                else SettingItemWithDivider {
                    MangaInpaintModelControls(settings.inpaintEngine, state, onIntent)
                }
            }
            // 气泡几何影响擦除遮罩与排版约束。停用的 AUTO 不再出现；端侧检测只在漫画增强版能跑。
            val unavailableBubbles = if (capabilities.onDeviceBubble) emptySet() else setOf(MangaBubbleSource.DEVICE)
            val bubbleSources = MangaBubbleSource.entries.filter { it != MangaBubbleSource.AUTO }
            MangaOptionDropdown(
                stringResource(R.string.manga_bubble_source),
                settings.bubbleSource.name,
                displayEntries = bubbleSources.map { source ->
                    if (source in unavailableBubbles) stringResource(R.string.manga_comic_only, mangaBubbleSourceName(source))
                    else mangaBubbleSourceName(source)
                }.toTypedArray(),
                entryValues = bubbleSources.map { it.name }.toTypedArray(),
                enabled = relevance.isActive(MangaSettingField.BUBBLE_SOURCE),
                unavailableValues = unavailableBubbles.map { it.name }.toSet(),
                onUnavailableSelected = notifyComicOnly
            ) { value ->
                onIntent(MangaTranslationIntent.ChangeSettings {
                    it.copy(bubbleSource = MangaBubbleSource.valueOf(value))
                })
            }
            val imageModels = state.models.filter { it.imageEditing }
            MangaOptionDropdown(
                stringResource(R.string.manga_translation_image_model), settings.imageModelId,
                displayEntries = arrayOf(stringResource(R.string.manga_translation_choose_model)) + imageModels.map { it.name },
                entryValues = arrayOf("") + imageModels.map { it.id },
                enabled = relevance.isActive(MangaSettingField.IMAGE_MODEL)
            ) { value ->
                onIntent(MangaTranslationIntent.ChangeSettings { it.copy(imageModelId = value) })
            }
            MangaOptionDropdown(
                stringResource(R.string.manga_translation_font_scale),
                settings.fontScale.toString(),
                displayEntries = arrayOf("50%", "75%", "100%", "125%", "150%", "200%"),
                entryValues = arrayOf("0.5", "0.75", "1.0", "1.25", "1.5", "2.0"),
                enabled = relevance.isActive(MangaSettingField.FONT_SCALE)
            ) { value ->
                onIntent(MangaTranslationIntent.ChangeSettings { it.copy(fontScale = value.toFloat()) })
            }
        }
        // 三段关系横跨所有分组，不归属某一组；整页重绘没有识别与翻译，不显示。
        if (!pageEdit) InfoCard(
            text = stringResource(R.string.manga_translation_model_relation_hint),
            modifier = if (tiny) Modifier.padding(bottom = 4.dp) else Modifier.padding(vertical = 8.dp),
            cornerRadius = if (tiny) 12.dp else 16.dp,
            contentPadding = if (tiny) PaddingValues(horizontal = 12.dp, vertical = 12.dp)
            else PaddingValues(16.dp),
            containerColor = cardContainer,
            textStyle = LegadoTheme.typography.bodySmall,
            textColor = LegadoTheme.colorScheme.onSurfaceVariant,
            actionIcon = AppIcons.Info,
            actionDescription = stringResource(R.string.manga_translation_model_relation),
            onAction = { relationHelp = true }
        )
        MangaOptionGroup(title = "", tiny = tiny) {
            MangaOptionExpansion(
                title = stringResource(R.string.manga_translation_advanced),
                expanded = advanced,
                onExpandChange = { advanced = it },
                expandContent = {
                    MangaOptionSwitch(
                        stringResource(R.string.manga_ai_geometry_diagnostics),
                        checked = settings.geometryDiagnostics,
                        enabled = relevance.isActive(MangaSettingField.GEOMETRY_DIAGNOSTICS)
                    ) { value ->
                        onIntent(MangaTranslationIntent.ChangeSettings { it.copy(geometryDiagnostics = value) })
                    }
                })
        }
    }
}

/**
 * 分组详情：静态说明 + 此刻被其它选项关掉的字段。
 *
 * 逐项提示会把每个下拉顶得又长又碎，原因只在用户主动展开详情时出现；控件本身仍在原位置灰，
 * 所以"这里为什么不能选"依然看得见，只是不再常驻占行。
 */
@Composable
private fun mangaGroupDetail(
    description: String,
    relevance: MangaSettingsRelevance,
    fields: List<MangaSettingField>,
): String {
    val notes = mutableListOf<String>()
    relevance.degradedOf(fields).values.forEach { affected ->
        notes += stringResource(R.string.manga_settings_degraded_local, mangaFieldNames(affected))
    }
    relevance.inactiveOf(fields).forEach { (reason, affected) ->
        notes += stringResource(
            R.string.manga_settings_inactive_group,
            mangaFieldNames(affected),
            mangaSettingReasonText(reason)
        )
    }
    return (listOf(description) + notes).joinToString("\n\n")
}

@Composable
private fun mangaFieldNames(fields: List<MangaSettingField>): String {
    val separator = stringResource(R.string.manga_settings_field_separator)
    val names = mutableListOf<String>()
    fields.forEach { names += mangaSettingFieldName(it) }
    return names.joinToString(separator)
}

@Composable
internal fun mangaLocatorName(locator: MangaLocator): String = when (locator) {
    MangaLocator.AI -> stringResource(R.string.manga_translation_locator_ai)
    MangaLocator.LOCAL -> stringResource(R.string.manga_translation_locator_local)
    MangaLocator.CLOUD -> stringResource(R.string.manga_cloud_ocr_title)
}

/** 选项名只描述实际会发生的事。停用的 [MangaBubbleSource.AUTO] 按 [MangaBubbleSource.DEVICE] 显示，且不进列表。 */
@Composable
internal fun mangaBubbleSourceName(source: MangaBubbleSource): String = when (source) {
    MangaBubbleSource.DEVICE, MangaBubbleSource.AUTO -> stringResource(R.string.manga_bubble_source_device)
    MangaBubbleSource.AI -> stringResource(R.string.manga_bubble_source_ai)
    MangaBubbleSource.OUTLINE -> stringResource(R.string.manga_bubble_source_outline)
    MangaBubbleSource.OFF -> stringResource(R.string.manga_bubble_source_off)
}

@Composable
internal fun mangaSettingFieldName(field: MangaSettingField): String = when (field) {
    MangaSettingField.LOCATOR -> stringResource(R.string.manga_translation_locator)
    MangaSettingField.SOURCE_LANGUAGE -> stringResource(R.string.manga_translation_source)
    MangaSettingField.TARGET_LANGUAGE -> stringResource(R.string.manga_translation_target)
    MangaSettingField.COMPOSITION -> stringResource(R.string.manga_translation_composition)
    MangaSettingField.OCR_MODEL -> stringResource(R.string.manga_ocr_ai_model)
    MangaSettingField.LOCAL_OCR_ENGINE -> stringResource(R.string.manga_ocr_local_engine)
    MangaSettingField.CLOUD_OCR -> stringResource(R.string.manga_cloud_ocr_title)
    MangaSettingField.OCR_OPTIONS -> stringResource(R.string.manga_ocr_preprocess)
    MangaSettingField.TEXT_ENGINE -> stringResource(R.string.manga_translation_engine)
    MangaSettingField.TEXT_MODEL -> stringResource(R.string.manga_translation_model)
    MangaSettingField.REASONING_LEVEL -> stringResource(R.string.ai_thinking_mode)
    MangaSettingField.RETRY_COUNT -> stringResource(R.string.manga_translation_retry_count)
    MangaSettingField.INPAINT_ENGINE -> stringResource(R.string.manga_inpaint_engine)
    MangaSettingField.BUBBLE_SOURCE -> stringResource(R.string.manga_bubble_source)
    MangaSettingField.IMAGE_MODEL -> stringResource(R.string.manga_translation_image_model)
    MangaSettingField.IMAGE_EDIT_PATH -> stringResource(R.string.manga_translation_edit_path)
    MangaSettingField.FONT_SCALE -> stringResource(R.string.manga_translation_font_scale)
    MangaSettingField.GEOMETRY_DIAGNOSTICS -> stringResource(R.string.manga_ai_geometry_diagnostics)
}

@Composable
private fun mangaSettingReasonText(reason: MangaSettingReason): String = when (reason) {
    MangaSettingReason.PAGE_EDIT_OWNS_PAGE -> stringResource(R.string.manga_settings_reason_page_edit)
    MangaSettingReason.SOURCE_TEXT_KEPT -> stringResource(R.string.manga_settings_reason_source_kept)
    MangaSettingReason.AI_IMAGE_OWNS_COMPOSITION -> stringResource(R.string.manga_settings_reason_ai_composition)
    MangaSettingReason.LOCAL_COMPOSITION -> stringResource(R.string.manga_settings_reason_local_composition)
    MangaSettingReason.RECOGNITION_NOT_AI -> stringResource(R.string.manga_settings_reason_not_ai)
    MangaSettingReason.RECOGNITION_NOT_LOCAL -> stringResource(R.string.manga_settings_reason_not_local)
    MangaSettingReason.RECOGNITION_NOT_CLOUD -> stringResource(R.string.manga_settings_reason_not_cloud)
    MangaSettingReason.RECOGNITION_IS_AI -> stringResource(R.string.manga_settings_reason_recognition_ai)
    MangaSettingReason.TEXT_ENGINE_NOT_AI -> stringResource(R.string.manga_settings_reason_text_not_ai)
    MangaSettingReason.TEXT_FOLLOWS_GLOBAL -> stringResource(R.string.manga_settings_reason_text_follows)
    MangaSettingReason.PRODUCT_WITHOUT_LOCAL_ENGINE -> stringResource(R.string.manga_settings_reason_product)
}
