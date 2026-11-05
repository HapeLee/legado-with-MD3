package io.legado.app.domain.model.manga

/**
 * 产物能做什么：只回答"有没有"，不回答"怎么表现"。
 *
 * 界面据此把本产物跑不了的档位**列出来但置灰**，并在显示名上标注来源，让用户看到漫画增强版多了
 * 什么；这些档位不可选中，值也不会被写回，因此不会出现"选了却被另一套实现执行"。
 */
data class MangaProductCapabilities(
    val localEngine: Boolean = true,
) {
    val onDeviceOcr: Boolean get() = localEngine
    val onDeviceBubble: Boolean get() = localEngine
    val modelDownload: Boolean get() = localEngine
}

/** Settings that a different choice elsewhere can silence. */
enum class MangaSettingField {
    LOCATOR, SOURCE_LANGUAGE, TARGET_LANGUAGE, COMPOSITION,
    OCR_MODEL, LOCAL_OCR_ENGINE, CLOUD_OCR, OCR_OPTIONS,
    TEXT_ENGINE, TEXT_MODEL, REASONING_LEVEL, RETRY_COUNT,
    INPAINT_ENGINE, BUBBLE_SOURCE, IMAGE_MODEL, IMAGE_EDIT_PATH,
    FONT_SCALE, GEOMETRY_DIAGNOSTICS,
}

/** Why a stored value currently has no effect, or cannot be honored by this product. */
enum class MangaSettingReason {
    /** Whole page is redrawn by an image model: recognition and text translation are skipped. */
    PAGE_EDIT_OWNS_PAGE,

    /** `PATCH` keeps the original lettering, so nothing is erased. */
    SOURCE_TEXT_KEPT,

    /** The composite is produced by an image model, so the local layout stage is skipped. */
    AI_IMAGE_OWNS_COMPOSITION,

    /** Local layout never asks an image model for an edit. */
    LOCAL_COMPOSITION,

    RECOGNITION_NOT_AI,
    RECOGNITION_NOT_LOCAL,
    RECOGNITION_NOT_CLOUD,

    /** AI vision reads the source image directly: no OCR preprocessing or thresholds. */
    RECOGNITION_IS_AI,

    TEXT_ENGINE_NOT_AI,

    /** Text engine follows the chapter preset; the per-book provider is not consulted. */
    TEXT_FOLLOWS_GLOBAL,

    /** The product has no local runtime: model inpaint falls back to built-in erasing. */
    PRODUCT_WITHOUT_LOCAL_ENGINE,
}

/**
 * Field-level truth table shared by the settings sheet and the pipeline it configures.
 *
 * [ignored] means "stored, but the current combination never reads it"; [degraded] means
 * "the user picked something this product cannot run". Unavailable built-ins are greyed out in the
 * picker itself, so [degraded] only describes values that are still stored but unreachable.
 */
data class MangaSettingsRelevance(
    val ignored: Map<MangaSettingField, MangaSettingReason> = emptyMap(),
    val degraded: Map<MangaSettingField, MangaSettingReason> = emptyMap(),
) {
    fun isActive(field: MangaSettingField): Boolean = field !in ignored

    /** Inactive fields of one group, bucketed so the sheet can print one line per reason. */
    fun inactiveOf(fields: Collection<MangaSettingField>): Map<MangaSettingReason, List<MangaSettingField>> =
        fields.mapNotNull { field -> ignored[field]?.let { it to field } }
            .groupBy({ it.first }, { it.second })

    fun degradedOf(fields: Collection<MangaSettingField>): Map<MangaSettingReason, List<MangaSettingField>> =
        fields.mapNotNull { field -> degraded[field]?.let { it to field } }
            .groupBy({ it.first }, { it.second })

    companion object {
        /**
         * [stored] 是未经产物降级的存储值；与生效值不同即可说明"保存过的端侧选择被换掉了"。
         * 不传时视为一致，只报告当前组合内的失效项。
         */
        fun of(
            settings: MangaTranslationSettings,
            capabilities: MangaProductCapabilities,
            stored: MangaTranslationSettings = settings,
        ): MangaSettingsRelevance {
            val pipeline = MangaTranslationPipeline.of(settings)
            val composition = pipeline.composition
            val ignored = mutableMapOf<MangaSettingField, MangaSettingReason>()
            val degraded = mutableMapOf<MangaSettingField, MangaSettingReason>()

            fun ignore(fields: List<MangaSettingField>, reason: MangaSettingReason) =
                fields.forEach { ignored[it] = reason }

            if (composition is MangaTranslationPipeline.Composition.AiImage &&
                composition.mode == MangaComposition.AI_PAGE
            ) {
                ignore(
                    listOf(
                        MangaSettingField.LOCATOR, MangaSettingField.OCR_MODEL, MangaSettingField.LOCAL_OCR_ENGINE,
                        MangaSettingField.CLOUD_OCR, MangaSettingField.OCR_OPTIONS, MangaSettingField.TEXT_ENGINE,
                        MangaSettingField.TEXT_MODEL, MangaSettingField.REASONING_LEVEL,
                        MangaSettingField.INPAINT_ENGINE, MangaSettingField.BUBBLE_SOURCE,
                        MangaSettingField.FONT_SCALE, MangaSettingField.GEOMETRY_DIAGNOSTICS,
                    ),
                    MangaSettingReason.PAGE_EDIT_OWNS_PAGE,
                )
            } else {
                when (pipeline.recognition) {
                    is MangaTranslationPipeline.Recognition.AiVision -> {
                        ignore(
                            listOf(MangaSettingField.LOCAL_OCR_ENGINE, MangaSettingField.CLOUD_OCR),
                            MangaSettingReason.RECOGNITION_NOT_LOCAL,
                        )
                        ignore(listOf(MangaSettingField.OCR_OPTIONS), MangaSettingReason.RECOGNITION_IS_AI)
                    }

                    is MangaTranslationPipeline.Recognition.LocalOcr -> {
                        ignore(listOf(MangaSettingField.OCR_MODEL), MangaSettingReason.RECOGNITION_NOT_AI)
                        ignore(listOf(MangaSettingField.CLOUD_OCR), MangaSettingReason.RECOGNITION_NOT_CLOUD)
                    }

                    is MangaTranslationPipeline.Recognition.CloudOcr -> {
                        ignore(listOf(MangaSettingField.OCR_MODEL), MangaSettingReason.RECOGNITION_NOT_AI)
                        ignore(listOf(MangaSettingField.LOCAL_OCR_ENGINE), MangaSettingReason.RECOGNITION_NOT_LOCAL)
                    }

                    MangaTranslationPipeline.Recognition.Unused -> Unit
                }
                // AI 视觉识别的请求由翻译模型发出：合并态下它就是识别模型，思考强度也照常生效。
                // 只有"既不翻译也不识别"的字段才真的失效。
                val aiVision = pipeline.recognition is MangaTranslationPipeline.Recognition.AiVision
                when (pipeline.translation) {
                    MangaTranslationPipeline.Translation.GoogleText -> {
                        if (!settings.fusedRecognition) ignore(
                            listOf(MangaSettingField.TEXT_MODEL), MangaSettingReason.TEXT_ENGINE_NOT_AI,
                        )
                        if (!aiVision) ignore(
                            listOf(MangaSettingField.REASONING_LEVEL), MangaSettingReason.TEXT_ENGINE_NOT_AI,
                        )
                    }

                    MangaTranslationPipeline.Translation.FollowSettings -> {
                        if (!settings.fusedRecognition) ignore(
                            listOf(MangaSettingField.TEXT_MODEL), MangaSettingReason.TEXT_FOLLOWS_GLOBAL,
                        )
                        if (!aiVision) ignore(
                            listOf(MangaSettingField.REASONING_LEVEL), MangaSettingReason.TEXT_FOLLOWS_GLOBAL,
                        )
                    }

                    // 合并请求只是"当前没有独立文字阶段"：引擎仍可切换，识别改由翻译模型跑。
                    MangaTranslationPipeline.Translation.FusedWithRecognition -> Unit

                    else -> Unit
                }
                when (composition) {
                    is MangaTranslationPipeline.Composition.LocalLayout -> ignore(
                        listOf(MangaSettingField.IMAGE_MODEL, MangaSettingField.IMAGE_EDIT_PATH),
                        MangaSettingReason.LOCAL_COMPOSITION,
                    )

                    MangaTranslationPipeline.Composition.LocalOverlay -> {
                        ignore(listOf(MangaSettingField.INPAINT_ENGINE), MangaSettingReason.SOURCE_TEXT_KEPT)
                        ignore(
                            listOf(MangaSettingField.IMAGE_MODEL, MangaSettingField.IMAGE_EDIT_PATH),
                            MangaSettingReason.LOCAL_COMPOSITION,
                        )
                    }

                    is MangaTranslationPipeline.Composition.AiImage -> {
                        ignore(listOf(MangaSettingField.INPAINT_ENGINE), MangaSettingReason.AI_IMAGE_OWNS_COMPOSITION)
                        if (composition.mode != MangaComposition.AI_REPAIR) ignore(
                            listOf(MangaSettingField.FONT_SCALE), MangaSettingReason.AI_IMAGE_OWNS_COMPOSITION,
                        )
                    }
                }
            }
            if (!capabilities.localEngine) {
                // 存储值仍是端侧档位、而当前产物跑不了：把"你选过的东西被换掉了"说清楚。
                if (stored.locator == MangaLocator.LOCAL)
                    degraded[MangaSettingField.LOCATOR] = MangaSettingReason.PRODUCT_WITHOUT_LOCAL_ENGINE
                if (stored.inpaintEngine.usesModel)
                    degraded[MangaSettingField.INPAINT_ENGINE] = MangaSettingReason.PRODUCT_WITHOUT_LOCAL_ENGINE
                if (stored.bubbleSource == MangaBubbleSource.DEVICE)
                    degraded[MangaSettingField.BUBBLE_SOURCE] = MangaSettingReason.PRODUCT_WITHOUT_LOCAL_ENGINE
            }
            return MangaSettingsRelevance(ignored, degraded)
        }
    }
}
